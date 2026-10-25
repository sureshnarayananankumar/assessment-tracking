# AssessHub REST API

Base URL: `/api` (same origin as the web app; nginx proxies to Spring Boot).
Interactive docs: **Swagger UI** at `http://localhost:8090/swagger-ui.html` (via the frontend proxy) or
`http://localhost:8080/swagger-ui.html` (backend directly). OpenAPI JSON: `/v3/api-docs`.

## Conventions

| Topic | Convention |
|---|---|
| Auth | `Authorization: Bearer <accessToken>` (JWT, 15 min). Refresh token = `ah_refresh` HttpOnly, `SameSite=Strict` cookie on `/api/auth`. |
| CSRF | Cookie endpoints (`/auth/refresh`, `/auth/logout`) require header `X-Requested-With: XMLHttpRequest`. |
| Success body | `{ "success": true, "data": …, "message": "…", "timestamp": "…" }` |
| Error body | `{ "success": false, "status": 409, "error": "Conflict", "code": "ATTEMPT_EXPIRED", "message": "…", "path": "…", "fieldErrors": {…} }` |
| Paging | `?page=0&size=20&sort=field,desc` → `data = { content[], page, size, totalElements, totalPages, first, last }` (max size 100) |
| Time | All instants are ISO-8601 **UTC** (`2026-10-19T09:30:00Z`). The UI converts to local time. |
| IDOR | Records you may not access return **404** (not 403) so ids cannot be probed. |
| Status codes | 200 OK · 201 Created · 400 validation · 401 unauthenticated · 403 forbidden · 404 not found · 409 conflict/state · 410 invitation gone · 413 file too large · 423 account locked · 429 rate limited · 500 |

Roles: **SA** = SUPER_ADMIN, **AD** = ADMIN, **RE** = RECRUITER, **CA** = CANDIDATE, **Any** = any signed-in user.
Recruiters only see assessments/results they own; admins see everything.

---

## Authentication — `/api/auth`

| Method | Path | Who | Description |
|---|---|---|---|
| POST | `/auth/login` | Public (rate limited) | `{email, password}` → `{accessToken, tokenType, expiresIn, user}` + refresh cookie. 401 `INVALID_CREDENTIALS`, 423 `ACCOUNT_LOCKED` (5 failures → 15 min), 403 `ACCOUNT_INACTIVE`. |
| POST | `/auth/refresh` | Cookie | Rotates the refresh token, returns a new access token. Reuse of a rotated token revokes all sessions. |
| POST | `/auth/logout` | Cookie | Revokes the refresh token and clears the cookie. |
| POST | `/auth/forgot-password` | Public (rate limited) | `{email}` → always 200 (no account enumeration). E-mails a 60-min single-use link. |
| GET | `/auth/reset-password/validate?token=` | Public | `{valid, purpose: PASSWORD_RESET\|ACCOUNT_SETUP, email(masked)}` |
| POST | `/auth/reset-password` | Public (rate limited) | `{token, newPassword}` (8–128 chars, letters + digits). Also completes account set-up. |
| GET | `/auth/me` | Any | Current user. |
| PUT | `/auth/me/password` | Any | `{currentPassword, newPassword}` → new tokens; other sessions revoked. |

## Invitations (public) — `/api/invitations`

| Method | Path | Description |
|---|---|---|
| GET | `/invitations/{token}` | Assessment summary for the e-mailed link: title, duration, schedule, deadline, candidate name/e-mail, `requiresLogin`, `hasPassword`. 404 invalid, 410 `INVITATION_EXPIRED` / `INVITATION_REVOKED`. |
| POST | `/invitations/{token}/accept` | Body `{password?}`. Candidates are signed in (refresh cookie set) → `{requiresLogin:false, auth, assignmentId}`. Staff accounts get `{requiresLogin:true}` and must sign in with their password. |

## Users — `/api/users` (SA, AD)

| Method | Path | Description |
|---|---|---|
| GET | `/users?search=&role=&status=&page=&size=&sort=` | Paginated search. |
| POST | `/users` | `{email, fullName, roles[], department?, jobTitle?, sendSetupEmail?}` → 201. Only SA can grant ADMIN/SUPER_ADMIN. User starts `INVITED` and receives a set-up link. |
| GET | `/users/{id}` | Details. |
| PUT | `/users/{id}` | `{fullName, roles[], status, department?, jobTitle?}`. Cannot deactivate/demote yourself or remove the last active SA. |
| DELETE | `/users/{id}` | Soft delete (status `DISABLED`, sessions revoked, history kept). |
| POST | `/users/{id}/send-setup` | Re-send set-up / reset e-mail. |

## Question bank — `/api/questions` (SA, AD, RE)

| Method | Path | Description |
|---|---|---|
| GET | `/questions?search=&type=&difficulty=&skill=` | Own + shared questions (admins: all). Staff view includes answer keys. |
| GET | `/questions/skills` | Distinct skill categories. |
| GET | `/questions/{id}` | Details. |
| POST | `/questions` | Create (see payload below). |
| PUT | `/questions/{id}` | Update. 409 `QUESTION_IN_USE` once a candidate answered it (duplicate instead). |
| DELETE | `/questions/{id}` | Deletes, or archives if referenced by an assessment/attempt. |
| POST | `/questions/{id}/duplicate` | Private copy owned by the caller. |

Question payload:
```json
{
  "type": "MCQ_SINGLE | MCQ_MULTI | TRUE_FALSE | SHORT_ANSWER | LONG_ANSWER | CODING | FILE_UPLOAD",
  "title": "…", "body": "…", "skillCategory": "Frontend", "difficulty": "BEGINNER|INTERMEDIATE|ADVANCED|EXPERT",
  "defaultMarks": 2, "visibility": "PRIVATE|SHARED", "tags": "react,hooks",
  "options": [{"text": "useEffect", "correct": true}, {"text": "useState", "correct": false}],
  "acceptedAnswers": ["404"], "caseSensitive": false,
  "codingLanguage": "javascript", "starterCode": "…", "referenceAnswer": "…", "evaluationGuidelines": "…",
  "allowedFileTypes": "pdf,docx", "maxFileSizeMb": 10
}
```
Rules: MCQ_SINGLE 2–12 options with exactly one correct; MCQ_MULTI ≥1 correct (exact-set match to score);
TRUE_FALSE exactly two options. SHORT_ANSWER with `acceptedAnswers` is auto-graded, otherwise manual.

## Assessments — `/api/assignments` (SA, AD, RE — owner or admin)

| Method | Path | Description |
|---|---|---|
| GET | `/assignments?search=&status=&skill=` | Paginated list with question/assignee/completion counts. |
| POST | `/assignments` | Create a DRAFT (payload below). |
| GET | `/assignments/{id}` | Full detail incl. questions (with answer keys) and attachments. |
| PUT | `/assignments/{id}` | Update. Questions/marks are locked once attempts exist (409 `ASSESSMENT_HAS_ATTEMPTS`). |
| DELETE | `/assignments/{id}` | Only without attempts (else 409 — archive instead). |
| POST | `/assignments/{id}/publish` | DRAFT → ACTIVE (or PUBLISHED if `startAt` is in the future). Validates questions, pass mark ≤ total, deadline in future. |
| POST | `/assignments/{id}/status` | `{status}`: `COMPLETED` (close), `ACTIVE` (reopen), `ARCHIVED`, `DRAFT` (unpublish without assignees / restore archived without attempts). |
| POST | `/assignments/{id}/duplicate` | Copy as DRAFT. |
| POST | `/assignments/{id}/invite` | `{invitees:[{email, fullName?}], dueAt?, message?}` → `{invited, reinvited, skipped, messages[]}`. Creates candidate accounts as needed, one assignee per (assessment,user), new secure link per invite (older links revoked). |
| GET | `/assignments/{id}/assignees?status=&search=` | People with status, invitation state, attempts, latest score, tab switches, flag. |
| POST | `/assignments/{id}/assignees/{assigneeId}/remind` | Re-sends with a fresh link. |
| POST | `/assignments/{id}/assignees/{assigneeId}/revoke` | Revokes access and ends a running attempt. |
| GET | `/assignments/{id}/attempts?status=&flagged=&search=` | Attempts with timing & focus counters. |
| GET | `/assignments/{id}/results?passed=&gradingStatus=&flagged=&search=` | Results. |
| GET | `/assignments/{id}/results/export` | CSV (UTF-8 with BOM, formula-injection safe). |
| POST | `/assignments/{id}/release-results` | Releases all graded results; future gradings release automatically. |
| POST | `/assignments/{id}/attachments` | `multipart/form-data` `file` (≤20 MB, allow-listed extensions). |
| DELETE | `/assignments/{id}/attachments/{attachmentId}` | Remove attachment. |

Assessment payload:
```json
{
  "title": "Full-Stack Screening", "description": "…", "instructions": "…", "evaluationCriteria": "…",
  "skillCategory": "Full Stack", "difficulty": "INTERMEDIATE", "assessmentType": "QUIZ|CODING|DESCRIPTIVE|MIXED|TAKE_HOME",
  "passingMarks": 15, "startAt": "2026-11-01T08:00:00Z", "endAt": "2026-11-08T18:00:00Z",
  "durationMinutes": 45, "maxAttempts": 1, "gracePeriodSeconds": 30,
  "shuffleQuestions": true, "shuffleOptions": true,
  "resultsReleasePolicy": "AFTER_GRADING|MANUAL", "showCorrectAnswers": false,
  "monitoringEnabled": true, "warningThreshold": 3, "maxViolations": 5,
  "violationAction": "WARN_ONLY|FLAG_FOR_REVIEW|AUTO_SUBMIT",
  "questions": [{"questionId": 12, "marks": 2}, {"questionId": 15}]
}
```

## Candidate — `/api/assessments` (Any assigned user)

| Method | Path | Description |
|---|---|---|
| GET | `/assessments/my` | Assigned assessments with server-computed `state`: UPCOMING, AVAILABLE, IN_PROGRESS, COMPLETED, EXPIRED. |
| GET | `/assessments/{assignmentId}` | Instructions, policies, attachments, `canStart`, `unavailableReason`, `serverTime`. |
| POST | `/assessments/{assignmentId}/start` | Starts (or resumes) an attempt. Server sets `startedAt` and `deadlineAt = min(now + duration, deadline)`. 409 `NOT_STARTED`, `DEADLINE_PASSED`, `ATTEMPT_LIMIT_REACHED`, `NOT_AVAILABLE`; 403 if revoked. |
| GET | `/assessments/{assignmentId}/attachments/{attachmentId}` | Download reference material. |

## Attempts — `/api/attempts`

| Method | Path | Who | Description |
|---|---|---|---|
| GET | `/attempts/{id}` | Owner | Questions (no answer keys), saved answers, `deadlineAt`, `serverTime`, `remainingSeconds`, counters. Auto-submits if expired. |
| PUT | `/attempts/{id}/answers` | Owner | Autosave `{answers:[{questionId, selectedOptionIds?, textAnswer?, codeAnswer?, codeLanguage?}]}`. 409 `ATTEMPT_EXPIRED` after deadline + grace (attempt auto-submitted), `ATTEMPT_CLOSED`. |
| POST | `/attempts/{id}/answers/{questionId}/file` | Owner | Multipart file answer (type/size per question). |
| GET | `/attempts/{id}/answers/{questionId}/file` | Owner / staff | Download (always `Content-Disposition: attachment`). |
| POST | `/attempts/{id}/activity` | Owner | `{events:[{type: TAB_HIDDEN\|TAB_VISIBLE\|WINDOW_BLUR\|WINDOW_FOCUS\|COPY_ATTEMPT\|PASTE_ATTEMPT\|WARNING_SHOWN, occurredAt, durationMs?, details?}]}` → counters, `newViolation`, `flagged`, `status`, `message`. |
| POST | `/attempts/{id}/submit` | Owner | Final submission. 409 `SUBMISSION_LATE` after deadline + grace (saved answers auto-submitted). |
| GET | `/attempts/{id}/results` | Owner | Result after release (scores, feedback, per-question marks; correct answers only if enabled). |
| GET | `/attempts/{id}/review` | Staff | Full review with answer keys, activity counters and result. |
| GET | `/attempts/{id}/activity` | Staff | Activity timeline. |
| POST | `/attempts/{id}/grade` | Staff | `{grades:[{answerId, marks, comment?}], feedback?, release?}`; marks validated ≤ max. |
| POST | `/attempts/{id}/flag` | Staff | `{flagged, reason?}` |
| POST | `/attempts/{id}/release` | Staff | Release one graded result. |

## Monitoring, reports, dashboard

| Method | Path | Who | Description |
|---|---|---|---|
| GET | `/monitoring/attempts?assignmentId=&status=&flagged=&search=` | Staff | Live attempts (remaining time, tab switches, focus losses, time away, flags). |
| GET | `/reports/results?assignmentId=&passed=&gradingStatus=&flagged=&search=` | Staff | Results across assessments. |
| GET | `/reports/results/export?…` | Staff | CSV export with the same filters. |
| GET | `/dashboard/staff` | Staff | Totals, statuses, invited, pending/in-progress/completed/expired, average score, pass/fail, pending review, flagged, tab-switch totals, score distribution, recent & flagged attempts. |

## Administration

| Method | Path | Who | Description |
|---|---|---|---|
| GET | `/audit-logs?action=&entityType=&search=&from=&to=` | SA, AD | Audit trail (logins, failures, lockouts, user/assessment/question changes, invitations, grading, releases, exports, settings). |
| GET | `/email-notifications` | SA, AD | E-mail delivery log (status/errors; bodies and links are never stored). |
| GET | `/settings/public` | Public | `{orgName}` |
| GET | `/settings` | Staff | Platform defaults. |
| PUT | `/settings` | SA | `{values: {"invitation.expiry.hours": "168", …}}` — only known keys with valid values. |

## Health

| Path | Description |
|---|---|
| `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` | Container health checks. |
| `/actuator/info` | Build info. |
