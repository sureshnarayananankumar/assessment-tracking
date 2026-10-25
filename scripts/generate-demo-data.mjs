#!/usr/bin/env node
/**
 * Generates a large, internally consistent demo dataset for AssessHub as a MySQL script.
 *
 *   node scripts/generate-demo-data.mjs [output.sql]
 *   (default output: database/generated/05_bulk_demo_data.sql)
 *
 * What it creates (all timestamps relative to the moment the script is loaded):
 *   - 12 staff (2 admins, 10 recruiters) and ~420 candidates
 *   - ~110 questions across 12 departments / tech stacks
 *   - ~44 assessments in every lifecycle state (draft, published, active, completed, archived)
 *   - assignees, invitations, attempts, answers, focus-activity logs, results, e-mail log, audit trail
 *
 * Data rules follow the application: auto-graded marks match the selected options, totals and
 * percentages match the answers, focus counters match the activity log, released results follow
 * the release policy, and timing respects each assessment's window and duration.
 *
 * No passwords are written. Seeded accounts reuse the BCrypt hash of the demo account
 * candidate@demo.local (DEMO_USER_PASSWORD) when it exists; otherwise they cannot sign in.
 * Re-running the SQL first removes the previous bulk demo data (@bgi.test / @candidates.test accounts).
 */
import { createHash } from 'node:crypto';
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { BANK } from './demo-data/question-bank.mjs';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const OUT = process.argv[2] ?? join(root, 'database', 'generated', '05_bulk_demo_data.sql');

// ------------------------------------------------------------------ deterministic random
let seed = 20261020;
function rnd() {
  seed |= 0; seed = (seed + 0x6d2b79f5) | 0;
  let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
}
const int = (a, b) => a + Math.floor(rnd() * (b - a + 1));
const chance = (p) => rnd() < p;
const pick = (arr) => arr[Math.floor(rnd() * arr.length)];
const shuffle = (arr) => { const a = [...arr]; for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(rnd() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; } return a; };
const sample = (arr, n) => shuffle(arr).slice(0, Math.min(n, arr.length));
const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
const round2 = (v) => Math.round(v * 100) / 100;
const halfStep = (v) => Math.round(v * 2) / 2;

// ------------------------------------------------------------------ SQL helpers
const ascii = (s) => s.replace(/[‘’]/g, "'").replace(/[“”]/g, '"').replace(/…/g, '...').replace(/[–—]/g, '-').replace(/[^\x00-\x7F]/g, '');
const q = (s) => (s === null || s === undefined ? 'NULL' : `'${ascii(String(s)).replace(/\\/g, '\\\\').replace(/'/g, "''")}'`);
const b = (v) => (v === null || v === undefined ? 'NULL' : v ? 'TRUE' : 'FALSE');
const n = (v) => (v === null || v === undefined ? 'NULL' : String(round2(v)));
const MIN = 60;
const HOUR = 3600;
const DAY = 86400;
/** Instant expressed as seconds relative to @now (negative = past). */
const t = (s) => (s === null || s === undefined ? 'NULL' : `@now + INTERVAL ${Math.round(s)} SECOND`);
const id = (v, k) => `@${v}+${k}`;
const hash = (s) => createHash('sha256').update(`assesshub-demo-${s}`).digest('hex');

const out = [];
const tables = {};
function row(table, cols, values) {
  (tables[table] ??= { cols, rows: [] }).rows.push(`(${values.join(', ')})`);
}
function flush(table) {
  const tb = tables[table];
  if (!tb || !tb.rows.length) return;
  for (let i = 0; i < tb.rows.length; i += 400) {
    out.push(`INSERT INTO ${table} (${tb.cols.join(', ')}) VALUES\n${tb.rows.slice(i, i + 400).join(',\n')};`);
  }
  out.push('');
  tb.rows = [];
}

// ------------------------------------------------------------------ people
const FIRST = ['Aisha', 'Omar', 'Fatima', 'Yousef', 'Mariam', 'Khalid', 'Noor', 'Hamad', 'Layla', 'Ahmed', 'Sara', 'Ali', 'Huda', 'Rashid', 'Amal',
  'Priya', 'Arjun', 'Kavya', 'Rahul', 'Ananya', 'Vikram', 'Deepa', 'Suresh', 'Lakshmi', 'Karthik', 'Divya', 'Ravi', 'Meera', 'Sanjay', 'Nisha',
  'James', 'Emily', 'Daniel', 'Sophie', 'Michael', 'Olivia', 'David', 'Hannah', 'Thomas', 'Grace', 'Lucas', 'Chloe', 'Ethan', 'Zoe', 'Ryan',
  'Maria', 'Carlos', 'Lucia', 'Diego', 'Elena', 'Jose', 'Ana', 'Wei', 'Mei', 'Jun', 'Li', 'Hiro', 'Yuki', 'Kofi', 'Amara', 'Tunde', 'Zainab',
  'Ivan', 'Olga', 'Mateo', 'Isabel', 'Joseph', 'Rania', 'Tariq', 'Salma', 'Imran', 'Ayesha', 'Bilal', 'Hina', 'Nikhil', 'Pooja', 'Arun', 'Sneha'];
const LAST = ['Al-Thani', 'Al-Kuwari', 'Al-Mansouri', 'Haddad', 'Khan', 'Rahman', 'Hassan', 'Nasser', 'Saleh', 'Farouk', 'Sharma', 'Patel', 'Iyer',
  'Nair', 'Reddy', 'Menon', 'Kumar', 'Krishnan', 'Pillai', 'Rao', 'Smith', 'Johnson', 'Brown', 'Taylor', 'Wilson', 'Clarke', 'Walker', 'Hughes',
  'Garcia', 'Lopez', 'Fernandes', 'DSouza', 'Chen', 'Wang', 'Tanaka', 'Sato', 'Okafor', 'Mensah', 'Adeyemi', 'Petrov', 'Novak', 'Rossi',
  'Silva', 'Costa', 'Ibrahim', 'Mahmoud', 'Qureshi', 'Siddiqui', 'Joshi', 'Verma'];
const DEPARTMENTS = ['Engineering', 'Quality Assurance', 'Information Security', 'Data & Analytics', 'Business Solutions', 'PMO', 'Mobile', 'Infrastructure'];

const staff = [
  { name: 'Noura Al-Kuwari', role: 'ADMIN', dept: 'Human Resources', title: 'HR Director' },
  { name: 'Rajesh Menon', role: 'ADMIN', dept: 'Engineering', title: 'Head of Engineering' },
  { name: 'Hessa Al-Mansouri', role: 'RECRUITER', dept: 'Talent Acquisition', title: 'Senior Recruiter', cats: ['Frontend', 'Backend', 'Node.js'] },
  { name: 'Daniel Clarke', role: 'RECRUITER', dept: 'Talent Acquisition', title: 'Technical Recruiter', cats: ['Backend', 'Databases & SQL', 'Python'] },
  { name: 'Priya Sharma', role: 'RECRUITER', dept: 'Quality Assurance', title: 'QA Lead', cats: ['QA & Testing'] },
  { name: 'Omar Haddad', role: 'RECRUITER', dept: 'Infrastructure', title: 'DevOps Manager', cats: ['DevOps & Cloud', 'Cyber Security'] },
  { name: 'Lakshmi Iyer', role: 'RECRUITER', dept: 'Data & Analytics', title: 'Analytics Manager', cats: ['Data & Analytics', 'Databases & SQL', 'Python'] },
  { name: 'Khalid Saleh', role: 'RECRUITER', dept: 'Information Security', title: 'Security Officer', cats: ['Cyber Security'] },
  { name: 'Sophie Walker', role: 'RECRUITER', dept: 'Business Solutions', title: 'Lead Business Analyst', cats: ['Business Analysis', 'Project Management'] },
  { name: 'Arjun Nair', role: 'RECRUITER', dept: 'Mobile', title: 'Mobile Engineering Lead', cats: ['Mobile', 'Frontend'] },
  { name: 'Mariam Nasser', role: 'RECRUITER', dept: 'PMO', title: 'PMO Manager', cats: ['Project Management', 'Business Analysis'] },
  { name: 'Wei Chen', role: 'RECRUITER', dept: 'Engineering', title: 'Engineering Manager', cats: ['Frontend', 'Backend', 'Node.js', 'Databases & SQL'] },
].map((s, i) => ({ ...s, k: i + 1, email: `${s.name.toLowerCase().replace(/[^a-z]+/g, '.').replace(/^\.|\.$/g, '')}@bgi.test` }));

const CANDIDATE_COUNT = 420;
const candidates = [];
const usedEmails = new Set();
for (let i = 0; i < CANDIDATE_COUNT; i++) {
  const first = pick(FIRST);
  const last = pick(LAST);
  let email = `${first}.${last}`.toLowerCase().replace(/[^a-z.]/g, '');
  let suffix = 1;
  while (usedEmails.has(email)) email = `${first}.${last}${++suffix}`.toLowerCase().replace(/[^a-z0-9.]/g, '');
  usedEmails.add(email);
  candidates.push({
    k: staff.length + i + 1,
    name: `${first} ${last.replace('DSouza', "D'Souza")}`,
    email: `${email}@candidates.test`,
    ability: clamp(0.25 + rnd() * 0.65 + (rnd() - 0.5) * 0.1, 0.15, 0.97),
    focusRisk: rnd(),
    createdAt: -int(5, 220) * DAY - int(0, 86399),
    everAccepted: false,
    lastLogin: null,
  });
}

// ------------------------------------------------------------------ questions
const questions = [];
let optionK = 0;
for (const [category, list] of Object.entries(BANK)) {
  const owner = staff.filter((s) => s.role === 'RECRUITER' && s.cats.includes(category))[0] ?? staff[1];
  for (const item of list) {
    const k = questions.length + 1;
    const options = (item.options ?? []).map(([text, correct], i) => ({ k: ++optionK, text, correct, order: i }));
    questions.push({ ...item, k, category, owner, options, createdAt: -int(265, 290) * DAY + int(0, 86399), shared: chance(0.7) });
  }
}

// ------------------------------------------------------------------ assessments
const TEMPLATES = [
  ['React Frontend Developer - Screening', ['Frontend'], 'QUIZ', 'INTERMEDIATE', 30],
  ['Senior Frontend Engineer - Technical Assessment', ['Frontend', 'Node.js'], 'MIXED', 'ADVANCED', 60],
  ['Java Spring Boot Developer - Technical Test', ['Backend', 'Databases & SQL'], 'MIXED', 'INTERMEDIATE', 60],
  ['Backend Engineer - API & Data Design', ['Backend', 'Databases & SQL'], 'DESCRIPTIVE', 'ADVANCED', 75],
  ['Node.js API Developer - Round 1', ['Node.js', 'Databases & SQL'], 'MIXED', 'INTERMEDIATE', 45],
  ['Full-Stack Engineer Assessment', ['Frontend', 'Backend', 'Databases & SQL'], 'MIXED', 'ADVANCED', 90],
  ['Python Developer - Coding & Concepts', ['Python', 'Databases & SQL'], 'CODING', 'INTERMEDIATE', 60],
  ['QA Automation Engineer - Round 1', ['QA & Testing'], 'MIXED', 'INTERMEDIATE', 45],
  ['Manual QA Analyst - Fundamentals', ['QA & Testing'], 'QUIZ', 'BEGINNER', 30],
  ['DevOps Engineer - Cloud & CI/CD', ['DevOps & Cloud', 'Cyber Security'], 'MIXED', 'ADVANCED', 60],
  ['Site Reliability Engineer - Incident Handling', ['DevOps & Cloud'], 'DESCRIPTIVE', 'ADVANCED', 45],
  ['Cyber Security Awareness 2026 (All Staff)', ['Cyber Security'], 'QUIZ', 'BEGINNER', 20],
  ['Secure Coding Refresher - Developers', ['Cyber Security', 'Backend'], 'QUIZ', 'INTERMEDIATE', 30],
  ['Data Analyst - SQL & Reporting', ['Data & Analytics', 'Databases & SQL'], 'MIXED', 'INTERMEDIATE', 60],
  ['BI Developer - Dashboards & KPIs', ['Data & Analytics'], 'MIXED', 'INTERMEDIATE', 45],
  ['Database Administrator - MySQL', ['Databases & SQL'], 'MIXED', 'ADVANCED', 60],
  ['Mobile Developer (Flutter / React Native)', ['Mobile', 'Frontend'], 'MIXED', 'INTERMEDIATE', 45],
  ['Business Analyst - Aptitude & Scenarios', ['Business Analysis'], 'MIXED', 'INTERMEDIATE', 45],
  ['Project Coordinator Assessment', ['Project Management'], 'QUIZ', 'BEGINNER', 30],
  ['Senior Project Manager - Case Study', ['Project Management', 'Business Analysis'], 'DESCRIPTIVE', 'ADVANCED', 60],
  ['Graduate Trainee Programme - Technical Aptitude', ['Frontend', 'Backend', 'Databases & SQL', 'QA & Testing'], 'QUIZ', 'BEGINNER', 40],
  ['Internship 2026 - Software Engineering', ['Python', 'Databases & SQL', 'Frontend'], 'QUIZ', 'BEGINNER', 30],
];

const STATUS_PLAN = [
  ...Array(16).fill('ACTIVE'), ...Array(12).fill('COMPLETED'), ...Array(5).fill('ARCHIVED'),
  ...Array(5).fill('PUBLISHED'), ...Array(6).fill('DRAFT'),
];
const assignments = STATUS_PLAN.map((status, i) => {
  const [baseTitle, cats, type, difficulty, duration] = TEMPLATES[i % TEMPLATES.length];
  const cohort = i >= TEMPLATES.length ? ` (${pick(['Q1', 'Q2', 'Q3', 'Q4'])} ${status === 'ARCHIVED' ? '2025' : '2026'} intake)` : '';
  const owner = staff.find((s) => s.role === 'RECRUITER' && s.cats?.some((c) => cats.includes(c))) ?? pick(staff.filter((s) => s.role === 'RECRUITER'));
  const pool = questions.filter((qq) => cats.includes(qq.category) && qq.type !== 'FILE_UPLOAD');
  const objective = pool.filter((qq) => ['MCQ_SINGLE', 'MCQ_MULTI', 'TRUE_FALSE', 'SHORT_ANSWER'].includes(qq.type));
  const manual = pool.filter((qq) => ['LONG_ANSWER', 'CODING'].includes(qq.type));
  const manualCount = type === 'QUIZ' ? 0 : type === 'DESCRIPTIVE' || type === 'CODING' ? 2 : 1;
  const chosen = shuffle([...sample(objective, int(6, 9)), ...sample(manual, manualCount)]);
  const total = chosen.reduce((s, qq) => s + qq.marks, 0);
  let createdAt, startAt = null, endAt = null, publishedAt = null;
  switch (status) {
    case 'ARCHIVED': createdAt = -int(200, 260) * DAY; publishedAt = createdAt + int(1, 3) * DAY; startAt = publishedAt; endAt = startAt + int(10, 20) * DAY; break;
    case 'COMPLETED': createdAt = -int(45, 140) * DAY; publishedAt = createdAt + int(1, 3) * DAY; startAt = publishedAt + int(0, 2) * DAY; endAt = startAt + int(10, 21) * DAY; break;
    case 'ACTIVE': createdAt = -int(8, 35) * DAY; publishedAt = createdAt + int(1, 3) * DAY; startAt = chance(0.3) ? null : publishedAt; endAt = int(3, 28) * DAY + int(0, 23) * HOUR; break;
    case 'PUBLISHED': createdAt = -int(1, 6) * DAY; publishedAt = createdAt + int(1, 20) * HOUR; startAt = int(1, 8) * DAY; endAt = startAt + int(10, 20) * DAY; break;
    default: createdAt = -int(0, 12) * DAY - int(1, 20) * HOUR; startAt = chance(0.5) ? int(5, 20) * DAY : null; endAt = startAt ? startAt + 14 * DAY : null;
  }
  const policy = chance(0.7) ? 'AFTER_GRADING' : 'MANUAL';
  const action = pick(['FLAG_FOR_REVIEW', 'FLAG_FOR_REVIEW', 'FLAG_FOR_REVIEW', 'WARN_ONLY', 'AUTO_SUBMIT']);
  return {
    k: i + 1, status, title: `${baseTitle}${cohort}`, cats, type, difficulty, duration, owner, questions: chosen,
    total, passing: Math.round(total * pick([0.5, 0.6, 0.6, 0.7])), createdAt, publishedAt: status === 'DRAFT' ? null : publishedAt,
    startAt, endAt, maxAttempts: chance(0.25) ? 2 : 1, grace: pick([0, 30, 30, 60]), shuffleQ: chance(0.6), shuffleO: chance(0.5),
    policy, showCorrect: chance(0.35), released: policy === 'MANUAL' && (status === 'COMPLETED' || status === 'ARCHIVED'),
    releasedAt: endAt === null ? null : Math.min(endAt + 2 * DAY, -HOUR),
    monitoring: chance(0.9), warn: pick([2, 3, 3]), maxV: pick([4, 5, 5, 6]), action,
  };
});

const UAS = ['Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Safari/537.36',
  'Mozilla/5.0 (Macintosh; Intel Mac OS X 14_6) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Safari/605.1.15',
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:132.0) Gecko/20100101 Firefox/132.0',
  'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Safari/537.36',
  'Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148'];

// ------------------------------------------------------------------ answers & grading helpers
const COMMENTS = ['Clear and well structured answer.', 'Good understanding, but misses edge cases.', 'Covers the basics; needs more depth on trade-offs.',
  'Excellent - concise and correct.', 'Partially correct. Review the core concept.', 'Solid reasoning, minor inaccuracies.', 'Answer lacks concrete examples.',
  'Well explained with practical experience.', 'Incomplete solution; logic is on the right track.', 'Correct approach, code could be cleaner.'];
const FEEDBACK = ['Strong performance overall. Recommended for the next round.', 'Good fundamentals; practise system design topics.',
  'Meets expectations for the role.', 'Below the bar for this level - consider a junior position.', 'Very good result, especially on practical questions.',
  'Please review the evaluation notes for each question.', null, null];
const LONG_ANSWERS = [
  (title) => `For "${title}" I would start by clarifying the goals and constraints, then measure the current state before changing anything. Next I would list options, compare trade-offs (cost, risk, time) and agree a plan with stakeholders. Finally I would implement in small steps, monitor the impact and document the lessons learned.`,
  (title) => `Approach to ${title}: 1) Gather facts and data. 2) Identify the root cause rather than symptoms. 3) Prioritise actions by impact and effort. 4) Communicate clearly and set expectations. 5) Review results and adjust.`,
  (title) => `I would handle ${title.toLowerCase()} by defining success criteria first, involving the right people early, using proven practices from previous projects and keeping a written record of decisions.`,
];
const WRONG_SHORT = ['200', '500', 'not sure', 'Ctrl+F', 'index', 'unique', 'main', '1'];

function gradeAnswer(question, ability, mode) {
  // mode: 'graded' | 'pending' | 'progress'
  const r = { selected: null, text: null, code: null, lang: null, marks: null, correct: null, status: 'PENDING' };
  const answered = mode === 'progress' ? chance(0.6) : chance(0.96);
  const good = chance(ability);
  switch (question.type) {
    case 'MCQ_SINGLE':
    case 'TRUE_FALSE': {
      if (answered) {
        const opt = good ? question.options.find((o) => o.correct) : pick(question.options.filter((o) => !o.correct));
        r.selected = [opt.k];
      }
      break;
    }
    case 'MCQ_MULTI': {
      if (answered) {
        const right = question.options.filter((o) => o.correct);
        const exact = chance(ability * 0.85);
        r.selected = exact ? right.map((o) => o.k)
          : sample(question.options, int(1, Math.max(1, question.options.length - 1))).map((o) => o.k).sort((x, y) => x - y);
        if (!exact && r.selected.join() === right.map((o) => o.k).join()) r.selected = r.selected.slice(0, -1).length ? r.selected.slice(0, -1) : [question.options.find((o) => !o.correct).k];
      }
      break;
    }
    case 'SHORT_ANSWER':
      if (answered) r.text = good ? question.accepted[0] : pick(WRONG_SHORT);
      break;
    case 'LONG_ANSWER':
      if (answered) r.text = pick(LONG_ANSWERS)(question.title);
      break;
    case 'CODING':
      if (answered) {
        r.lang = question.lang;
        r.code = good ? question.ref : `${question.starter.replace('// your code here', '// partial attempt').replace('# your code here', '# partial attempt')}`;
      }
      break;
  }
  if (mode === 'progress') return r;

  if (['MCQ_SINGLE', 'TRUE_FALSE', 'MCQ_MULTI'].includes(question.type)) {
    const right = question.options.filter((o) => o.correct).map((o) => o.k).join();
    r.correct = Boolean(r.selected && r.selected.join() === right);
    r.marks = r.correct ? question.marks : 0;
    r.status = 'AUTO_GRADED';
  } else if (question.type === 'SHORT_ANSWER') {
    r.correct = Boolean(r.text && question.accepted.some((a) => a.toLowerCase() === r.text.toLowerCase()));
    r.marks = r.correct ? question.marks : 0;
    r.status = 'AUTO_GRADED';
  } else if (!answered) {
    r.correct = false; r.marks = 0; r.status = 'AUTO_GRADED';
  } else if (mode === 'graded') {
    r.marks = clamp(halfStep(question.marks * clamp(ability + (rnd() - 0.5) * 0.35, 0, 1)), 0, question.marks);
    r.correct = r.marks === question.marks ? true : r.marks === 0 ? false : null;
    r.status = 'MANUALLY_GRADED';
  }
  return r;
}

// ------------------------------------------------------------------ build rows
const C_USERS = ['id', 'email', 'password_hash', 'full_name', 'department', 'job_title', 'status', 'email_verified', 'failed_login_attempts', 'last_login_at', 'created_by', 'created_at', 'updated_at', 'version'];
const assignees = [];
const attempts = [];
let aaK = 0, invK = 0, attK = 0, ansK = 0, resK = 0;
const liveTarget = 7;
let liveCount = 0;

for (const a of assignments) {
  if (a.status === 'DRAFT') continue;
  const size = a.status === 'PUBLISHED' ? int(8, 20) : int(18, 55);
  const people = sample(candidates, size);
  const windowStart = a.startAt ?? a.publishedAt;
  for (const c of people) {
    const assignedAt = a.publishedAt + int(1, 48) * HOUR;
    c.firstAssigned = Math.min(c.firstAssigned ?? 0, assignedAt);
    const aa = { k: ++aaK, a, c, assignedAt, status: 'ASSIGNED', attemptsUsed: 0, completedAt: null, reminders: 0, lastReminder: null, dueAt: null, invitation: null, attempts: [] };
    const windowEnd = a.endAt;
    const past = windowEnd !== null && windowEnd < 0;

    let plan; // 'none' | 'done' | 'live' | 'revoked'
    if (a.status === 'PUBLISHED') plan = 'none';
    else if (chance(0.03)) plan = 'revoked';
    else if (a.status === 'ACTIVE' && liveCount < liveTarget && chance(0.08)) { plan = 'live'; liveCount++; }
    else plan = chance(past ? 0.78 : 0.58) ? 'done' : 'none';

    if (plan === 'done' || plan === 'live') {
      let count = plan === 'done' && a.maxAttempts > 1 && chance(0.3) ? 2 : 1;
      const earliest = Math.max(assignedAt + HOUR, windowStart ?? assignedAt);
      const latest = Math.min((windowEnd ?? 0) - a.duration * MIN, -a.duration * MIN - 2 * HOUR);
      if (plan === 'done' && latest <= earliest) plan = 'none';
      else {
        const range = latest - earliest;
        if (range < 4 * a.duration * MIN) count = 1;
        for (let n2 = 1; n2 <= count; n2++) {
          // live attempts started a few minutes ago and still have time left; repeat attempts use separate halves of the window
          const startedAt = plan === 'live' ? -int(3, Math.max(4, Math.min(a.duration - 6, 25))) * MIN
            : count === 1 ? earliest + rnd() * range : n2 === 1 ? earliest + rnd() * range * 0.45 : earliest + range * 0.55 + rnd() * range * 0.45;
          aa.attempts.push(buildAttempt(a, c, aa, n2, startedAt, plan === 'live'));
        }
        aa.attemptsUsed = aa.attempts.length;
        if (plan === 'live') aa.status = 'IN_PROGRESS';
        else { aa.status = 'COMPLETED'; aa.completedAt = aa.attempts[aa.attempts.length - 1].submittedAt; }
        c.everAccepted = true;
        c.lastLogin = Math.max(c.lastLogin ?? -1e12, aa.attempts[aa.attempts.length - 1].startedAt - 2 * MIN);
      }
    }
    if (plan === 'revoked') aa.status = 'REVOKED';
    else if (aa.status === 'ASSIGNED' && past) aa.status = 'EXPIRED';
    if (aa.status === 'ASSIGNED' && a.status === 'ACTIVE' && windowEnd !== null && windowEnd < 2 * DAY && chance(0.6)) {
      aa.reminders = 1; aa.lastReminder = windowEnd - 24 * HOUR;
    }
    if (aa.status === 'ASSIGNED' && chance(0.08)) aa.dueAt = (windowEnd ?? 10 * DAY) + int(2, 7) * DAY; // personal extension
    const deadline = aa.dueAt ?? windowEnd;
    const invSent = aa.lastReminder ?? assignedAt;
    const invStatus = aa.status === 'REVOKED' ? 'REVOKED' : aa.status === 'EXPIRED' ? 'EXPIRED'
      : aa.attemptsUsed > 0 || (aa.status === 'ASSIGNED' && chance(0.35)) ? 'ACCEPTED' : 'PENDING';
    if (invStatus === 'ACCEPTED') c.everAccepted = true;
    aa.invitation = { k: ++invK, status: invStatus, sentAt: invSent, expiresAt: Math.max(invSent + 168 * HOUR, deadline ?? 0),
      acceptedAt: invStatus === 'ACCEPTED' ? (aa.attempts[0]?.startedAt ?? invSent + int(1, 30) * HOUR) - int(1, 20) * MIN : null };
    assignees.push(aa);
  }
}

function buildAttempt(a, c, aa, number, startedAt, live) {
  const hardEnd = a.endAt ?? Infinity;
  const deadlineAt = Math.min(startedAt + a.duration * MIN, aa.dueAt ?? hardEnd);
  const order = a.shuffleQ ? shuffle(a.questions) : a.questions;
  const ability = clamp(c.ability + (number === 2 ? 0.08 : 0) + (rnd() - 0.5) * 0.12, 0.05, 0.99);
  // focus activity
  const risky = c.focusRisk > 0.88;
  const episodes = live ? int(0, 2) : risky ? int(3, 9) : c.focusRisk > 0.55 ? int(1, 3) : chance(0.3) ? 1 : 0;
  let status = 'SUBMITTED', reason = 'Submitted by candidate';
  const violationsCounted = a.monitoring ? episodes : 0;
  let flagged = false, flagReason = null;
  if (!live && a.monitoring && violationsCounted >= a.maxV) {
    const r2 = `Left the assessment window ${violationsCounted} times (limit ${a.maxV})`;
    if (a.action === 'FLAG_FOR_REVIEW') { flagged = true; flagReason = r2; }
    if (a.action === 'AUTO_SUBMIT') { flagged = true; flagReason = r2; status = 'TERMINATED'; reason = `Submitted automatically: ${r2}`; }
  }
  if (!live && status === 'SUBMITTED' && chance(0.11)) { status = 'AUTO_SUBMITTED'; reason = 'Time limit reached - submitted automatically by the server'; }
  const used = status === 'AUTO_SUBMITTED' ? deadlineAt - startedAt : (deadlineAt - startedAt) * (0.35 + rnd() * 0.6);
  const submittedAt = live ? null : Math.min(startedAt + used, deadlineAt);
  const recent = !live && submittedAt > -3 * DAY;
  const pendingReview = recent && chance(0.6);
  const mode = live ? 'progress' : pendingReview ? 'pending' : 'graded';
  const answers = order.map((qq, i) => ({ k: ++ansK, q: qq, order: i, ...gradeAnswer(qq, ability, mode) }));
  const lastSaved = live ? -int(10, 90) : submittedAt - int(5, 120);
  const att = { k: ++attK, a, c, aa, number, startedAt, deadlineAt, submittedAt, status: live ? 'IN_PROGRESS' : status, reason: live ? null : reason,
    order, answers, episodes, violations: violationsCounted, flagged, flagReason, lastSaved, live, pendingReview,
    awayMs: 0, tab: 0, focus: 0, ip: `10.${int(10, 250)}.${int(0, 255)}.${int(1, 254)}`, ua: pick(UAS) };
  return att;
}

// ------------------------------------------------------------------ emit
const head = () => `-- =====================================================================
-- AssessHub - BULK DEMO DATA (generated by scripts/generate-demo-data.mjs)
-- ${staff.length} staff, ${candidates.length} candidates, ${questions.length} questions, ${assignments.length} assessments,
-- ${assignees.length} assignees, ${attempts.length} attempts and related answers, activity, results, e-mails and audit logs.
-- Development / demo use only. Re-running replaces the previous bulk demo data.
-- Seeded accounts use the password of candidate@demo.local (DEMO_USER_PASSWORD) if that account exists.
-- =====================================================================
SET NAMES utf8mb4;
SET @now := UTC_TIMESTAMP(6);
SET @pw := (SELECT password_hash FROM users WHERE email = 'candidate@demo.local' LIMIT 1);
SET @r_admin := (SELECT id FROM roles WHERE name = 'ADMIN');
SET @r_recruiter := (SELECT id FROM roles WHERE name = 'RECRUITER');
SET @r_candidate := (SELECT id FROM roles WHERE name = 'CANDIDATE');

START TRANSACTION;

-- ---- remove a previous bulk-demo run (cascades remove assignees, invitations, attempts, answers, logs, results)
DELETE a FROM assignments a JOIN users u ON u.id = a.owner_id WHERE u.email LIKE '%@bgi.test';
DELETE q FROM questions q JOIN users u ON u.id = q.owner_id WHERE u.email LIKE '%@bgi.test';
DELETE FROM audit_logs WHERE actor_email LIKE '%@bgi.test' OR actor_email LIKE '%@candidates.test';
DELETE FROM email_notifications WHERE recipient_email LIKE '%@bgi.test' OR recipient_email LIKE '%@candidates.test';
DELETE FROM users WHERE email LIKE '%@bgi.test' OR email LIKE '%@candidates.test';

SET @u := (SELECT COALESCE(MAX(id), 0) FROM users);
SET @q := (SELECT COALESCE(MAX(id), 0) FROM questions);
SET @o := (SELECT COALESCE(MAX(id), 0) FROM question_options);
SET @a := (SELECT COALESCE(MAX(id), 0) FROM assignments);
SET @aq := (SELECT COALESCE(MAX(id), 0) FROM assignment_questions);
SET @aa := (SELECT COALESCE(MAX(id), 0) FROM assessment_assignments);
SET @inv := (SELECT COALESCE(MAX(id), 0) FROM assessment_invitations);
SET @t := (SELECT COALESCE(MAX(id), 0) FROM assessment_attempts);
SET @ans := (SELECT COALESCE(MAX(id), 0) FROM assessment_answers);
SET @res := (SELECT COALESCE(MAX(id), 0) FROM assessment_results);
`;

// collect attempts
for (const aa of assignees) attempts.push(...aa.attempts);

// users
for (const s of staff) {
  const created = -int(300, 400) * DAY;
  row('users', C_USERS, [id('u', s.k), q(s.email), '@pw', q(s.name), q(s.dept), q(s.title), "IF(@pw IS NULL, 'INVITED', 'ACTIVE')", b(true), '0',
    t(-int(1, 30) * HOUR), 'NULL', t(created), t(created), '0']);
}
for (const c of candidates) {
  const active = c.everAccepted;
  row('users', C_USERS, [id('u', c.k), q(c.email), active ? '@pw' : 'NULL', q(c.name), 'NULL', q(pick(['Applicant', 'Applicant', 'Graduate', 'Intern Applicant', 'Employee'])),
    active ? "IF(@pw IS NULL, 'INVITED', 'ACTIVE')" : "'INVITED'", b(active), '0', active && c.lastLogin ? t(c.lastLogin) : 'NULL', id('u', pick(staff).k), t(Math.min(c.createdAt, (c.firstAssigned ?? 0) - HOUR)), t(Math.min(c.createdAt, (c.firstAssigned ?? 0) - HOUR)), '0']);
}
flush('users');
for (const s of staff) row('user_roles', ['user_id', 'role_id'], [id('u', s.k), s.role === 'ADMIN' ? '@r_admin' : '@r_recruiter']);
for (const c of candidates) row('user_roles', ['user_id', 'role_id'], [id('u', c.k), '@r_candidate']);
flush('user_roles');

// questions + options
for (const qq of questions) {
  row('questions', ['id', 'owner_id', 'type', 'title', 'body', 'skill_category', 'difficulty', 'default_marks', 'visibility', 'coding_language', 'starter_code',
    'reference_answer', 'evaluation_guidelines', 'accepted_answers', 'case_sensitive', 'allowed_file_types', 'max_file_size_mb', 'tags', 'archived', 'created_at', 'updated_at', 'version'],
  [id('q', qq.k), id('u', qq.owner.k), q(qq.type), q(qq.title), q(qq.body), q(qq.category), q(qq.difficulty), n(qq.marks), q(qq.shared ? 'SHARED' : 'PRIVATE'),
    q(qq.lang ?? null), q(qq.starter ?? null), q(qq.ref ?? null), q(qq.guide ?? null), q(qq.accepted ? qq.accepted.join('\n') : null), b(false), 'NULL', 'NULL',
    q(qq.category.toLowerCase().replace(/[^a-z]+/g, '-')), b(false), t(qq.createdAt), t(qq.createdAt + int(0, 30) * DAY), '0']);
  for (const o of qq.options) row('question_options', ['id', 'question_id', 'option_text', 'is_correct', 'display_order'], [id('o', o.k), id('q', qq.k), q(o.text), b(o.correct), String(o.order)]);
}
flush('questions');
flush('question_options');

// assignments + questions
let aqK = 0;
for (const a of assignments) {
  const instructions = `You have ${a.duration} minutes. Answers are saved automatically - if you lose connection, simply reopen the link.\nWork independently; leaving this tab is recorded for review.\nRead every question carefully before answering.`;
  row('assignments', ['id', 'owner_id', 'title', 'description', 'instructions', 'evaluation_criteria', 'skill_category', 'difficulty', 'assessment_type', 'total_marks',
    'passing_marks', 'start_at', 'end_at', 'duration_minutes', 'max_attempts', 'grace_period_seconds', 'shuffle_questions', 'shuffle_options', 'status',
    'results_release_policy', 'show_correct_answers', 'results_released', 'results_released_at', 'monitoring_enabled', 'warning_threshold', 'max_violations',
    'violation_action', 'published_at', 'created_at', 'updated_at', 'version'],
  [id('a', a.k), id('u', a.owner.k), q(a.title), q(`Assessment for ${a.cats.join(', ')} candidates at Business Gateways International.`), q(instructions),
    q('Objective questions are auto-graded. Descriptive and coding answers are reviewed by the hiring panel.'), q(a.cats[0]), q(a.difficulty), q(a.type),
    n(a.total), n(a.passing), t(a.startAt), t(a.endAt), String(a.duration), String(a.maxAttempts), String(a.grace), b(a.shuffleQ), b(a.shuffleO), q(a.status),
    q(a.policy), b(a.showCorrect), b(a.released), a.released ? t(a.releasedAt) : 'NULL', b(a.monitoring), String(a.warn), String(a.maxV), q(a.action),
    t(a.publishedAt), t(a.createdAt), t(a.publishedAt ?? a.createdAt), '0']);
  a.questions.forEach((qq, i) => row('assignment_questions', ['id', 'assignment_id', 'question_id', 'display_order', 'marks'], [id('aq', ++aqK), id('a', a.k), id('q', qq.k), String(i), n(qq.marks)]));
}
flush('assignments');
flush('assignment_questions');

// assignees + invitations
for (const aa of assignees) {
  row('assessment_assignments', ['id', 'assignment_id', 'user_id', 'assigned_by', 'status', 'due_at', 'attempts_used', 'reminder_count', 'last_reminder_at', 'assigned_at',
    'completed_at', 'created_at', 'updated_at', 'version'],
  [id('aa', aa.k), id('a', aa.a.k), id('u', aa.c.k), id('u', aa.a.owner.k), q(aa.status), t(aa.dueAt), String(aa.attemptsUsed), String(aa.reminders), t(aa.lastReminder),
    t(aa.assignedAt), t(aa.completedAt), t(aa.assignedAt), t(aa.completedAt ?? aa.assignedAt), '0']);
  const inv = aa.invitation;
  row('assessment_invitations', ['id', 'assessment_assignment_id', 'assignment_id', 'user_id', 'email', 'token_hash', 'status', 'expires_at', 'sent_at', 'accepted_at',
    'last_used_at', 'revoked_at', 'invited_by', 'created_at'],
  [id('inv', inv.k), id('aa', aa.k), id('a', aa.a.k), id('u', aa.c.k), q(aa.c.email), q(hash(`inv-${inv.k}`)), q(inv.status), t(inv.expiresAt), t(inv.sentAt),
    t(inv.acceptedAt), t(inv.acceptedAt), inv.status === 'REVOKED' ? t(inv.sentAt + int(2, 72) * HOUR) : 'NULL', id('u', aa.a.owner.k), t(inv.sentAt)]);
}
flush('assessment_assignments');
flush('assessment_invitations');

// attempts, answers, activity, results
const C_ATT = ['id', 'assessment_assignment_id', 'assignment_id', 'user_id', 'attempt_number', 'status', 'started_at', 'deadline_at', 'submitted_at', 'submission_reason',
  'question_order', 'option_order', 'tab_switch_count', 'focus_loss_count', 'violation_count', 'total_away_ms', 'last_violation_at', 'flagged', 'flag_reason',
  'ip_address', 'user_agent', 'last_saved_at', 'created_at', 'updated_at', 'version'];
const C_LOG = ['attempt_id', 'event_type', 'client_timestamp', 'server_timestamp', 'duration_ms', 'details', 'counted_as_violation'];
const resultsOut = [];
for (const at of attempts) {
  const a = at.a;
  // activity log (consistent with the counters stored on the attempt)
  const logs = [[at.startedAt, 'STARTED', null, `Attempt ${at.number}`, false]];
  const span = (at.submittedAt ?? 0) - at.startedAt;
  let lastViolation = null;
  const moments = Array.from({ length: at.episodes }, () => at.startedAt + 60 + rnd() * Math.max(120, span - 120)).sort((x, y) => x - y);
  for (const m of moments) {
    const end = at.live ? 0 : at.submittedAt;
    const away = Math.max(2, Math.min(int(2, 95), Math.floor(end - m - 5))) * 1000;
    const hidden = chance(0.7);
    if (hidden) { logs.push([m, 'TAB_HIDDEN', null, null, a.monitoring]); at.tab++; }
    logs.push([m + 0.08, 'WINDOW_BLUR', null, null, hidden ? false : a.monitoring]);
    at.focus++;
    if (hidden) logs.push([m + away / 1000, 'TAB_VISIBLE', away, null, false]);
    logs.push([m + away / 1000 + 0.05, 'WINDOW_FOCUS', away, null, false]);
    if (a.monitoring) logs.push([m + away / 1000 + 0.3, 'WARNING_SHOWN', null, 'violations=' + (logs.filter((l) => l[4]).length), false]);
    at.awayMs += away;
    lastViolation = m;
  }
  if (at.flagged && at.status !== 'TERMINATED') logs.push([moments[moments.length - 1] + 2, 'FLAGGED', null, at.flagReason, false]);
  if (!at.live) logs.push([at.submittedAt, at.status === 'SUBMITTED' ? 'SUBMITTED' : at.status === 'AUTO_SUBMITTED' ? 'AUTO_SUBMITTED' : 'TERMINATED', null, at.reason, false]);
  if (at.number === 2 || chance(0.06)) logs.splice(1, 0, [at.startedAt + int(120, 600), 'RESUMED', null, null, false]);
  logs.sort((x, y) => x[0] - y[0]);

  row('assessment_attempts', C_ATT, [id('t', at.k), id('aa', at.aa.k), id('a', a.k), id('u', at.c.k), String(at.number), q(at.status), t(at.startedAt), t(at.deadlineAt),
    t(at.submittedAt), q(at.reason), `CONCAT_WS(',', ${at.order.map((qq) => id('q', qq.k)).join(', ')})`, 'NULL', String(at.tab), String(at.focus),
    String(at.violations), String(at.awayMs), t(lastViolation), b(at.flagged), q(at.flagReason), q(at.ip), q(at.ua), t(at.lastSaved), t(at.startedAt),
    t(at.submittedAt ?? at.lastSaved), '0']);
  for (const l of logs) row('assessment_activity_logs', C_LOG, [id('t', at.k), q(l[1]), t(l[0]), t(l[0] + 0.2), l[2] === null ? 'NULL' : String(l[2]), q(l[3]), b(l[4])]);

  // graded some time after submission, never in the future
  const gradedAt = at.live ? null : at.submittedAt + int(60, Math.max(60, Math.min(40 * HOUR, -at.submittedAt - 60)));
  for (const ans of at.answers) {
    const sel = ans.selected ? `CONCAT_WS(',', ${ans.selected.map((k) => id('o', k)).join(', ')})` : 'NULL';
    const manual = ans.status === 'MANUALLY_GRADED';
    row('assessment_answers', ['id', 'attempt_id', 'question_id', 'display_order', 'selected_option_ids', 'text_answer', 'code_answer', 'code_language', 'max_marks',
      'marks_awarded', 'is_correct', 'grading_status', 'evaluator_comment', 'graded_by', 'graded_at', 'saved_at', 'created_at', 'updated_at', 'version'],
    [id('ans', ans.k), id('t', at.k), id('q', ans.q.k), String(ans.order), sel, q(ans.text), q(ans.code), q(ans.lang), n(ans.q.marks), n(ans.marks), b(ans.correct),
      q(ans.status), manual ? q(pick(COMMENTS)) : 'NULL', manual ? id('u', a.owner.k) : 'NULL', ans.status === 'PENDING' ? 'NULL' : t(manual ? gradedAt : at.submittedAt),
      (ans.selected || ans.text || ans.code) ? t(Math.min(at.lastSaved, at.startedAt + (ans.order + 1) * 90)) : 'NULL', t(at.startedAt), t(at.submittedAt ?? at.lastSaved), '0']);
  }

  if (!at.live) {
    const auto = at.answers.filter((x) => x.status === 'AUTO_GRADED').reduce((s, x) => s + x.marks, 0);
    const manual = at.answers.filter((x) => x.status === 'MANUALLY_GRADED').reduce((s, x) => s + x.marks, 0);
    const pending = at.answers.some((x) => x.status === 'PENDING');
    const max = at.answers.reduce((s, x) => s + x.q.marks, 0);
    const total = auto + manual;
    const graded = !pending;
    const hasManual = at.answers.some((x) => x.status === 'MANUALLY_GRADED');
    const releaseAt = graded ? (a.policy === 'AFTER_GRADING' ? (hasManual ? gradedAt : at.submittedAt) : a.released ? Math.max(a.releasedAt, hasManual ? gradedAt : at.submittedAt) : null) : null;
    const released = releaseAt !== null && releaseAt < 0;
    const summary = JSON.stringify({ tabSwitches: at.tab, focusLosses: at.focus, violations: at.violations, totalAwayMs: at.awayMs, flagged: at.flagged,
      flagReason: at.flagReason, answered: at.answers.filter((x) => x.selected || x.text || x.code).length, questions: at.answers.length, status: at.status,
      note: 'Focus events are indicators for human review, not proof of misconduct.' });
    const feedback = graded && hasManual ? pick(FEEDBACK) : null;
    row('assessment_results', ['id', 'attempt_id', 'assignment_id', 'user_id', 'auto_score', 'manual_score', 'total_score', 'max_score', 'percentage', 'passed',
      'grading_status', 'released', 'released_at', 'feedback', 'graded_by', 'graded_at', 'tab_switch_count', 'focus_loss_count', 'violation_count', 'flagged',
      'activity_summary', 'created_at', 'updated_at', 'version'],
    [id('res', ++resK), id('t', at.k), id('a', a.k), id('u', at.c.k), n(auto), n(manual), n(total), n(max), n(max ? Math.min(100, (total * 100) / max) : 0),
      graded ? b(total >= a.passing) : 'NULL', q(graded ? 'GRADED' : 'PENDING_REVIEW'), b(released), released ? t(releaseAt) : 'NULL', q(feedback),
      hasManual && graded ? id('u', a.owner.k) : 'NULL', hasManual && graded ? t(gradedAt) : 'NULL', String(at.tab), String(at.focus), String(at.violations),
      b(at.flagged), q(summary), t(at.submittedAt), t(released ? releaseAt : at.submittedAt), '0']);
    resultsOut.push({ at, released, releaseAt, graded, hasManual, gradedAt });
  }
}
flush('assessment_attempts');
flush('assessment_activity_logs');
flush('assessment_answers');
flush('assessment_results');

// e-mail delivery log
const C_MAIL = ['recipient_email', 'recipient_user_id', 'template', 'subject', 'status', 'error_message', 'related_type', 'related_id', 'sent_at', 'created_at'];
const mail = (to, userK, template, subject, when, relType, relK) => {
  const failed = chance(0.015);
  row('email_notifications', C_MAIL, [q(to), id('u', userK), q(template), q(subject), q(failed ? 'FAILED' : 'SENT'),
    failed ? q('MailConnectException: Couldn\'t connect to host, port: smtp.office365.com, 587; timeout 10000') : 'NULL', q(relType), relK, failed ? 'NULL' : t(when + 3), t(when)]);
};
for (const aa of assignees) {
  mail(aa.c.email, aa.c.k, 'INVITATION', `Assessment invitation - ${aa.a.title}`, aa.assignedAt, 'ASSIGNMENT', id('a', aa.a.k));
  if (aa.reminders) mail(aa.c.email, aa.c.k, 'REMINDER', `Reminder: Assessment invitation - ${aa.a.title}`, aa.lastReminder, 'ASSIGNMENT', id('a', aa.a.k));
}
for (const r of resultsOut) if (r.released) mail(r.at.c.email, r.at.c.k, 'RESULT_RELEASED', `Your result is available - ${r.at.a.title}`, r.releaseAt, 'ATTEMPT', id('t', r.at.k));
for (const s of staff) mail(s.email, s.k, 'ACCOUNT_SETUP', 'Activate your Business Gateways International account', -int(300, 400) * DAY, 'USER', id('u', s.k));
flush('email_notifications');

// audit trail
const C_AUDIT = ['actor_id', 'actor_email', 'action', 'entity_type', 'entity_id', 'details', 'ip_address', 'created_at'];
const audit = (actor, action, type, entity, details, when) => row('audit_logs', C_AUDIT,
  [actor ? id('u', actor.k) : 'NULL', q(actor?.email ?? null), q(action), q(type), entity === null ? 'NULL' : `CAST(${entity} AS CHAR)`, q(details), q(`10.20.${int(0, 40)}.${int(2, 250)}`), t(when)]);
const admin = staff[0];
for (const s of staff.slice(1)) audit(admin, 'USER_CREATED', 'USER', id('u', s.k), `Roles: [${s.role}]`, -int(300, 400) * DAY);
for (const qq of questions) audit(qq.owner, 'QUESTION_CREATED', 'QUESTION', id('q', qq.k), `${qq.type}: ${qq.title}`, qq.createdAt);
for (const a of assignments) {
  audit(a.owner, 'ASSIGNMENT_CREATED', 'ASSIGNMENT', id('a', a.k), a.title, a.createdAt);
  if (a.publishedAt !== null) audit(a.owner, 'ASSIGNMENT_PUBLISHED', 'ASSIGNMENT', id('a', a.k), `${a.title} -> ${a.startAt && a.startAt > a.publishedAt ? 'PUBLISHED' : 'ACTIVE'}`, a.publishedAt);
  const invited = assignees.filter((x) => x.a === a);
  if (invited.length) audit(a.owner, 'CANDIDATES_INVITED', 'ASSIGNMENT', id('a', a.k), `invited=${invited.length}, reinvited=0, skipped=0`, a.publishedAt + HOUR);
  if (a.status === 'COMPLETED' || a.status === 'ARCHIVED') audit(null, 'ASSIGNMENT_STATUS_CHANGED', 'ASSIGNMENT', id('a', a.k), 'ACTIVE -> COMPLETED (window closed)', a.endAt + 60);
  if (a.status === 'ARCHIVED') audit(a.owner, 'ASSIGNMENT_STATUS_CHANGED', 'ASSIGNMENT', id('a', a.k), 'COMPLETED -> ARCHIVED', a.endAt + int(20, 40) * DAY);
  if (a.released) audit(a.owner, 'RESULTS_RELEASED', 'ASSIGNMENT', id('a', a.k), 'All graded results released', a.releasedAt);
}
for (const aa of assignees.filter((x) => x.status === 'REVOKED')) audit(aa.a.owner, 'ASSIGNEE_REVOKED', 'ASSIGNMENT', id('a', aa.a.k), aa.c.email, aa.assignedAt + int(2, 72) * HOUR);
for (const r of resultsOut.filter((x) => x.hasManual && x.graded)) {
  audit(r.at.a.owner, 'ATTEMPT_GRADED', 'ATTEMPT', id('t', r.at.k), 'Manual grading completed', r.gradedAt);
}
for (const aa of assignees.filter((x) => x.attemptsUsed > 0)) audit(aa.c, 'INVITATION_ACCEPTED', 'ASSIGNMENT', id('a', aa.a.k), null, aa.invitation.acceptedAt);
for (let i = 0; i < 420; i++) { const s = pick(staff); audit(s, 'LOGIN_SUCCESS', 'USER', id('u', s.k), null, -int(1, 90 * 24) * HOUR - int(0, 3599)); }
for (let i = 0; i < 60; i++) { const s = pick([...staff, ...sample(candidates, 3)]); audit(s, 'LOGIN_FAILED', 'USER', id('u', s.k), 'Wrong password', -int(1, 60 * 24) * HOUR); }
for (let i = 0; i < 4; i++) { const s = pick(staff); audit(s, 'ACCOUNT_LOCKED', 'USER', id('u', s.k), 'Locked after 5 failed logins', -int(5, 60) * DAY); }
for (let i = 0; i < 12; i++) audit(pick(staff), 'RESULTS_EXPORTED', 'ASSIGNMENT', id('a', pick(assignments).k), `${int(10, 60)} row(s)`, -int(1, 60) * DAY);
flush('audit_logs');

out.push('COMMIT;', '');
out.push(`SELECT 'Bulk demo data loaded' AS result,
  (SELECT COUNT(*) FROM users WHERE email LIKE '%@bgi.test' OR email LIKE '%@candidates.test') AS users,
  (SELECT COUNT(*) FROM assignments a JOIN users u ON u.id = a.owner_id WHERE u.email LIKE '%@bgi.test') AS assessments,
  (SELECT COUNT(*) FROM assessment_attempts t JOIN users u ON u.id = t.user_id WHERE u.email LIKE '%@candidates.test') AS attempts,
  IF(@pw IS NULL, 'Seeded accounts cannot sign in (candidate@demo.local not found)', 'Seeded accounts use DEMO_USER_PASSWORD') AS logins;`);

mkdirSync(dirname(OUT), { recursive: true });
writeFileSync(OUT, `${head()}\n${out.join('\n')}\n`, 'utf8');
const stats = {
  staff: staff.length, candidates: candidates.length, questions: questions.length, options: optionK, assessments: assignments.length,
  assignees: assignees.length, attempts: attempts.length, live: attempts.filter((x) => x.live).length,
  answers: attempts.reduce((s, x) => s + x.answers.length, 0), results: resultsOut.length,
  pendingReview: resultsOut.filter((x) => !x.graded).length, flagged: attempts.filter((x) => x.flagged).length,
};
console.log(`Wrote ${OUT}`);
console.log(stats);
