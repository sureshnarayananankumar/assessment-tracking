#!/usr/bin/env node
/**
 * End-to-end smoke test against a running AssessHub stack (Docker Compose or local).
 *
 *   node scripts/smoke-test.mjs
 *
 * Reads credentials from the environment or from ../.env (never hardcoded):
 *   BASE_URL                  default http://localhost:8090
 *   MAILHOG_URL               default http://localhost:8026  (used to read the invitation e-mail)
 *   BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD
 */
import { readFileSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const env = { ...process.env };
const envFile = join(root, '.env');
if (existsSync(envFile)) {
  for (const line of readFileSync(envFile, 'utf8').split(/\r?\n/)) {
    const m = /^([A-Z0-9_]+)=(.*)$/.exec(line.trim());
    if (m && env[m[1]] === undefined) env[m[1]] = m[2];
  }
}
const BASE = env.BASE_URL ?? 'http://localhost:8090';
const MAILHOG = env.MAILHOG_URL ?? 'http://localhost:8026';
const ADMIN_EMAIL = env.BOOTSTRAP_ADMIN_EMAIL;
const ADMIN_PASSWORD = env.BOOTSTRAP_ADMIN_PASSWORD;

let failures = 0;
const ok = (msg) => console.log(`  ✔ ${msg}`);
const fail = (msg) => { failures++; console.log(`  ✘ ${msg}`); };
const check = (cond, msg) => (cond ? ok(msg) : fail(msg));

async function call(method, path, { token, body, raw } = {}) {
  const headers = { 'X-Requested-With': 'XMLHttpRequest' };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const res = await fetch(`${BASE}${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  const text = await res.text();
  if (raw) return { status: res.status, text, headers: res.headers };
  let json = null;
  try { json = JSON.parse(text); } catch { /* not json */ }
  return { status: res.status, json, data: json?.data };
}

async function invitationToken(email) {
  for (let i = 0; i < 20; i++) {
    const res = await fetch(`${MAILHOG}/api/v2/search?kind=to&query=${encodeURIComponent(email)}`).catch(() => null);
    if (res?.ok) {
      const body = await res.json();
      for (const item of body.items ?? []) {
        const content = (item.Content?.Body ?? '').replace(/=\r?\n/g, '').replace(/=3D/g, '=');
        const m = /\/invite\/([A-Za-z0-9_-]{43})/.exec(content);
        if (m) return m[1];
      }
    }
    await new Promise((r) => setTimeout(r, 500));
  }
  return null;
}

async function main() {
  console.log(`AssessHub smoke test -> ${BASE}`);
  if (!ADMIN_EMAIL || !ADMIN_PASSWORD) throw new Error('Set BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD');

  console.log('Health');
  check((await call('GET', '/healthz', { raw: true })).status === 200, 'frontend /healthz');
  check((await call('GET', '/actuator/health')).json?.status === 'UP', 'backend health UP');

  console.log('Authentication & authorisation');
  const bad = await call('POST', '/api/auth/login', { body: { email: ADMIN_EMAIL, password: 'wrong-password-1' } });
  check(bad.status === 401, 'wrong password rejected (401)');
  const login = await call('POST', '/api/auth/login', { body: { email: ADMIN_EMAIL, password: ADMIN_PASSWORD } });
  check(login.status === 200 && login.data?.accessToken, 'super admin signs in');
  const admin = login.data.accessToken;
  check((await call('GET', '/api/users')).status === 401, 'API requires a token');

  console.log('Question bank & assessment');
  const q1 = await call('POST', '/api/questions', { token: admin, body: {
    type: 'MCQ_SINGLE', title: 'Smoke: idempotent verb', body: 'Which HTTP verb is idempotent?', difficulty: 'BEGINNER', defaultMarks: 2,
    options: [{ text: 'POST', correct: false }, { text: 'PUT', correct: true }] } });
  const q2 = await call('POST', '/api/questions', { token: admin, body: {
    type: 'SHORT_ANSWER', title: 'Smoke: status code', body: 'Not found status code?', difficulty: 'BEGINNER', defaultMarks: 1, acceptedAnswers: ['404'] } });
  const q3 = await call('POST', '/api/questions', { token: admin, body: {
    type: 'LONG_ANSWER', title: 'Smoke: explain caching', body: 'Explain HTTP caching.', difficulty: 'INTERMEDIATE', defaultMarks: 5 } });
  check([q1, q2, q3].every((r) => r.status === 201), 'questions created (MCQ, short, long)');
  const a = await call('POST', '/api/assignments', { token: admin, body: {
    title: `Smoke test ${new Date().toISOString()}`, difficulty: 'BEGINNER', assessmentType: 'MIXED', passingMarks: 4,
    durationMinutes: 10, maxAttempts: 1, gracePeriodSeconds: 30, endAt: new Date(Date.now() + 86400000).toISOString(),
    questions: [q1, q2, q3].map((q) => ({ questionId: q.data.id })) } });
  check(a.status === 201 && a.data.totalMarks === 8, 'assessment created with 8 marks');
  const pub = await call('POST', `/api/assignments/${a.data.id}/publish`, { token: admin });
  check(pub.data?.status === 'ACTIVE', 'assessment published (ACTIVE)');

  console.log('Invitation e-mail');
  const email = `smoke-${Date.now()}@example.test`;
  const inv = await call('POST', `/api/assignments/${a.data.id}/invite`, { token: admin, body: { invitees: [{ email, fullName: 'Smoke Candidate' }] } });
  check(inv.data?.invited === 1, 'candidate invited');
  const token = await invitationToken(email);
  check(Boolean(token), 'invitation e-mail delivered to MailHog with a secure link');
  if (!token) throw new Error('No invitation e-mail found - is MailHog running (COMPOSE_PROFILES=local-mail)?');
  const info = await call('GET', `/api/invitations/${token}`);
  check(info.data?.assignmentId === a.data.id, 'invitation link resolves');
  const acc = await call('POST', `/api/invitations/${token}/accept`, { body: {} });
  const cand = acc.data?.auth?.accessToken;
  check(Boolean(cand), 'candidate signed in through the link');
  check((await call('GET', '/api/assignments', { token: cand })).status === 403, 'candidate blocked from staff API (403)');

  console.log('Timed attempt');
  const start = await call('POST', `/api/assessments/${a.data.id}/start`, { token: cand });
  check(start.status === 200 && start.data.remainingSeconds > 590, 'attempt started with server deadline');
  check(!JSON.stringify(start.data).includes('"correct"'), 'answer key not exposed to candidate');
  const qs = start.data.questions;
  const mcq = qs.find((q) => q.type === 'MCQ_SINGLE');
  const answers = [
    { questionId: mcq.questionId, selectedOptionIds: [mcq.options.find((o) => o.text === 'PUT').id] },
    { questionId: qs.find((q) => q.type === 'SHORT_ANSWER').questionId, textAnswer: '404' },
    { questionId: qs.find((q) => q.type === 'LONG_ANSWER').questionId, textAnswer: 'Cache-Control, ETag and validation.' },
  ];
  const save = await call('PUT', `/api/attempts/${start.data.attemptId}/answers`, { token: cand, body: { answers } });
  check(save.data?.savedCount === 3, 'answers autosaved');
  const resumed = await call('GET', `/api/attempts/${start.data.attemptId}`, { token: cand });
  check(resumed.data?.answers.filter((x) => x.savedAt).length === 3, 'answers restored after reload');
  const now = new Date().toISOString();
  const act = await call('POST', `/api/attempts/${start.data.attemptId}/activity`, { token: cand, body: { events: [
    { type: 'TAB_HIDDEN', occurredAt: now }, { type: 'WINDOW_BLUR', occurredAt: now }, { type: 'TAB_VISIBLE', occurredAt: now, durationMs: 3000 }] } });
  check(act.data?.tabSwitchCount === 1 && act.data?.violationCount === 1, 'tab switch logged and de-duplicated');
  const sub = await call('POST', `/api/attempts/${start.data.attemptId}/submit`, { token: cand });
  check(sub.data?.status === 'SUBMITTED', 'attempt submitted');
  check((await call('POST', `/api/attempts/${start.data.attemptId}/submit`, { token: cand })).status === 409, 'second submit rejected (409)');
  check((await call('POST', `/api/assessments/${a.data.id}/start`, { token: cand })).json?.code === 'ATTEMPT_LIMIT_REACHED', 'attempt limit enforced');

  console.log('Grading & results');
  const review = await call('GET', `/api/attempts/${start.data.attemptId}/review`, { token: admin });
  check(review.data?.result?.autoScore === 3 && review.data.result.gradingStatus === 'PENDING_REVIEW', 'objective answers auto-graded (3/3), essay pending');
  const essay = review.data.answers.find((x) => x.type === 'LONG_ANSWER');
  const graded = await call('POST', `/api/attempts/${start.data.attemptId}/grade`, { token: admin, body: { grades: [{ answerId: essay.answerId, marks: 4, comment: 'Good' }], feedback: 'Well done' } });
  check(graded.data?.result?.totalScore === 7 && graded.data.result.passed === true && graded.data.result.released === true, 'manual grade -> 7/8 passed & released');
  const res = await call('GET', `/api/attempts/${start.data.attemptId}/results`, { token: cand });
  check(res.data?.released === true && res.data.totalScore === 7, 'candidate sees released result');
  const csv = await call('GET', `/api/assignments/${a.data.id}/results/export`, { token: admin, raw: true });
  check(csv.status === 200 && csv.text.includes(email), 'CSV export contains the candidate');
  const dash = await call('GET', '/api/dashboard/staff', { token: admin });
  check(dash.status === 200 && dash.data.totalAssignments >= 1, 'dashboard statistics');
  const audit = await call('GET', '/api/audit-logs?action=ATTEMPT_GRADED', { token: admin });
  check(audit.data?.totalElements >= 1, 'audit log recorded grading');

  console.log(failures === 0 ? '\nALL CHECKS PASSED' : `\n${failures} CHECK(S) FAILED`);
  process.exit(failures === 0 ? 0 : 1);
}

main().catch((e) => {
  console.error(`\nSmoke test aborted: ${e.message}`);
  process.exit(1);
});
