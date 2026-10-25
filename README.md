# AssessHub — Assignment & Assessment Management System

A secure, cloud-deployable platform for creating assignments and **timed assessments** for any role or tech stack
(frontend, backend, full-stack, QA, data, …), inviting candidates or employees by e-mail, monitoring attempts, grading
and reporting. Dark-first **liquid-glass** UI in the **Business Gateways International** brand (logo blue `#006DB7`
and orange `#F48120`), with light mode.

| Layer | Technology |
|---|---|
| Frontend | React 18, TypeScript, Vite 5, Material UI 6, React Router 6, Axios, React Hook Form + Zod, Recharts |
| Backend | Java 17+ (built on JDK 21), Spring Boot 3.5, Spring Security + JWT, Spring Data JPA/Hibernate, Bean Validation, Spring Mail, Flyway, springdoc-openapi |
| Database | MySQL 8.x (Docker `mysql:8.4` or your own server managed with MySQL Workbench) |
| Ops | Docker multi-stage builds, Docker Compose, nginx (SPA + `/api` reverse proxy), health checks |

```
assignment-assessment-system  (D:\Assignment_tracker)
├── backend/            Spring Boot API (+ Dockerfile, Maven wrapper, tests)
├── frontend/           React app (+ Dockerfile, nginx config, tests)
├── database/           SQL scripts for MySQL Workbench + database guide
├── docker/             MySQL server config and init script for Compose
├── docs/               API.md, DEPLOYMENT.md
├── docker-compose.yml
├── .env.example        copy to .env (never commit .env)
└── README.md
```

---

## Features

**Roles & access** — Super Admin, Admin, Recruiter, Candidate. Every rule is enforced on the server
(Spring Security URL rules + `@PreAuthorize` + record-level ownership checks that return 404 to prevent ID probing).
Recruiters only see their own assessments/results; admins see everything; only Super Admins grant admin roles or change platform settings.

**Authentication** — BCrypt passwords, 15-minute JWT access tokens kept in memory, rotating refresh token in an
HttpOnly `SameSite=Strict` cookie with reuse detection, logout, forgot/reset password, account set-up e-mails
(acts as e-mail verification), login rate limiting and account lockout, full audit trail.

**Assessments** — title, description, instructions, evaluation criteria, skill category, difficulty, type, question bank,
total/passing marks, start time, deadline, duration, attempts, grace period, randomised questions/options,
Draft → Published/Active → Completed → Archived lifecycle, reference attachments, duplication.

**Question types** — multiple choice, multiple select, true/false, short answer (optional auto-grading against
accepted answers), long answer, coding (submission + manual review; code is never executed), file upload.

**Invitations** — assign to one or many people by e-mail (new users get candidate accounts), personal deadline overrides,
secure 256-bit links stored only as hashes, expiry and revocation, re-invite without duplicates, reminders
(manual + automatic before the deadline). E-mails include title, name, instructions, start time, duration, deadline and the link.

**Timed engine (server is the source of truth)** — start time and deadline are computed and stored server-side;
autosave every change (debounced) + periodic saves + local backup restored after refresh/crash/offline;
5-minute and 1-minute warnings; automatic submission at zero; writes after deadline + grace are rejected (409) and the
attempt is auto-submitted with answers saved before the deadline; a scheduler auto-submits abandoned attempts;
attempt limits, schedule windows and restarts are enforced with row locks + unique constraints.

**Focus monitoring** — Page Visibility API + window blur/focus, warning dialog with count, every event logged with
client & server timestamps and time away, de-duplication (blur + hidden = one event), configurable warning threshold,
violation limit and action (warn only / flag for review / auto-submit), activity timeline and summary stored with the result.

**Grading & results** — automatic grading of objective questions, manual grading with per-answer comments and overall feedback,
marks validated on the server, automatic or manual result release, optional correct-answer reveal, CSV export
(Excel-compatible, formula-injection safe).

**Dashboards** — staff dashboard (totals, invited, pending, in-progress, completed, expired, average score, pass/fail,
pending review, flagged, tab-switch activity, score distribution), live monitoring with server-synchronised countdowns,
candidate dashboard (assigned, upcoming, available, in-progress, completed, expired, results), searchable/sortable/paginated tables.

**Branding** — BGI logo (`frontend/public/brand/`: original, light, dark-mode variant with light lettering, square mark
used as favicon), brand palette in `frontend/src/theme/tokens.ts`, BGI-coloured e-mail templates, organisation name
"Business Gateways International" (editable by the Super Admin under Settings).

**UI** — dark mode by default, light & system modes, brand accents "BGI Blue" (default) / "BGI Sunrise" / "BGI Midnight", adjustable glass intensity
(Frosted → Liquid), animated liquid background (respects reduced-motion), responsive from phones to desktops,
distraction-free assessment screen.

> **Browser monitoring limitations.** A web page can only observe its *own* tab and window. It **cannot** detect other
> applications, other browsers or profiles, incognito windows, virtual machines, second monitors, phones or other people,
> and OS notifications or password managers can trigger false positives. AssessHub therefore records focus events as
> **indicators for human review** and never labels anyone as cheating automatically. It does **not** provide browser lockdown.

---

## Quick start (fully containerised)

Prerequisites: Docker Desktop (or Docker Engine + Compose v2).

```bash
cd D:\Assignment_tracker
cp .env.example .env        # Windows PowerShell: Copy-Item .env.example .env
# edit .env: DB_PASSWORD, MYSQL_ROOT_PASSWORD, JWT_SECRET, BOOTSTRAP_ADMIN_EMAIL / _PASSWORD
docker compose up -d --build
```

| URL | What |
|---|---|
| http://localhost:8090 | AssessHub web app |
| http://localhost:8090/swagger-ui.html | API docs (Swagger UI) |
| http://localhost:8026 | MailHog — every e-mail sent in development (invitations, resets) |
| 127.0.0.1:3308 | MySQL (connect with MySQL Workbench) |
| http://localhost:8080/actuator/health | Backend health (localhost only) |

Sign in with `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD`. With `DEMO_DATA_ENABLED=true` you also get
`recruiter@demo.local` and `candidate@demo.local` (password = `DEMO_USER_PASSWORD`) and a ready-to-take demo assessment.

Typical flow: **Question bank → New question** → **Assessments → New assessment** (add questions) → **Publish** →
**Invite people** → open the link from MailHog → candidate takes the timed assessment → **Results** → **Evaluate** → **Release**.

```bash
docker compose ps                 # health status
docker compose logs -f backend    # logs
docker compose down               # stop (data kept in volumes)
docker compose down -v            # stop and DELETE database + uploads
```

## Using an existing MySQL Server (managed in MySQL Workbench)

See [database/README.md](database/README.md). In short: run `database/01_create_database_and_user.sql` in Workbench, then in `.env`:

```
COMPOSE_PROFILES=local-mail
DB_HOST=host.docker.internal      # MySQL on this PC (or your DB server's hostname)
DB_PORT=3306
DB_USERNAME=assesshub_app
DB_PASSWORD=<password you set>
```

`docker compose up -d --build` — Flyway creates the tables on first start.

## Lots of demo data

```bash
node scripts/generate-demo-data.mjs
docker compose cp database/generated/05_bulk_demo_data.sql mysql:/tmp/bulk.sql
docker compose exec mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" assesshub < /tmp/bulk.sql'
```
Adds ~430 users, ~110 questions, 44 assessments, ~880 attempts with answers, results, focus logs, e-mails and audit
entries. Details in [database/README.md](database/README.md#bulk-demo-data-lots-of-realistic-records).

## Local development (without Docker for the apps)

Requirements: **JDK 17+** (the machine currently has Java 8 on PATH — install Temurin 21 from https://adoptium.net and set
`JAVA_HOME`), Node.js 20+ (Node 24 is installed). Maven is **not** required: use the bundled wrapper.

```bash
# 1) database only
docker compose --profile local-db --profile local-mail up -d mysql mailhog

# 2) backend (PowerShell: set variables with $env:NAME="value")
cd backend
export DB_HOST=localhost DB_PORT=3308 DB_USERNAME=assesshub_app DB_PASSWORD=... JWT_SECRET=... \
       MAIL_HOST=localhost MAIL_PORT=1025 FRONTEND_URL=http://localhost:5174 \
       BOOTSTRAP_ADMIN_EMAIL=admin@example.com BOOTSTRAP_ADMIN_PASSWORD=...
./mvnw spring-boot:run            # Windows: mvnw.cmd spring-boot:run   -> http://localhost:8080

# 3) frontend (proxies /api to :8080)
cd frontend
npm install
npm run dev                       # -> http://localhost:5174
```

MailHog in Docker exposes SMTP only inside the Docker network by default; for a backend running on the host either
add `"127.0.0.1:1025:1025"` to the mailhog ports or set `MAIL_ENABLED=false` and `MAIL_LOG_LINKS=true` to print links in the log.

Build without a local JDK (uses Docker):

```bash
docker run --rm -v "${PWD}/backend:/workspace" -w /workspace maven:3.9-eclipse-temurin-21 mvn -B package
```

## Tests

```bash
# Backend: 55 tests — JUnit 5 + Mockito unit tests and Spring Boot/MockMvc integration tests on H2 (MySQL mode)
cd backend && ./mvnw test
# or without a local JDK:
docker run --rm -v "${PWD}/backend:/workspace" -w /workspace maven:3.9-eclipse-temurin-21 mvn -B test

# Frontend: 19 tests — Vitest + Testing Library
cd frontend && npm test

# End-to-end smoke test against the running Docker stack (27 checks, reads credentials from .env,
# follows the real invitation e-mail through MailHog)
node scripts/smoke-test.mjs
```

Coverage highlights: login/lockout/refresh-rotation/reuse detection, password reset, role & ownership authorisation
(IDOR), full lifecycle (questions → publish → invite → accept link → start → autosave → resume → monitor → submit →
grade → release → export), deadline + grace enforcement, late submission rejection, scheduler auto-submit,
focus-event de-duplication / flagging / auto-submit policy, attempt limits, duplicate invitations and link revocation,
countdown timer, activity monitor hook, route guards and form validation.

Docker start-up verification:

```bash
docker compose up -d --build
docker compose ps                                   # mysql, backend, frontend = healthy
curl http://localhost:8090/healthz                  # ok
curl http://localhost:8090/actuator/health          # {"status":"UP"}
curl http://localhost:8090/api/settings/public      # {"success":true,"data":{"orgName":"AssessHub"}...}
```

## Configuration reference

All configuration is via environment variables — see [.env.example](.env.example) (fully commented).
Nothing sensitive is hardcoded. Key variables: `DB_*`, `JWT_SECRET`, `FRONTEND_URL`, `CORS_ALLOWED_ORIGINS`,
`COOKIE_SECURE`, `MAIL_*`, `BOOTSTRAP_ADMIN_*`, `DEMO_DATA_ENABLED`, `SWAGGER_ENABLED`.
Platform defaults (invitation expiry, grace period, monitoring thresholds, release policy, reminders) are editable by the
Super Admin under **Settings**.

**External configuration you must supply:** SMTP credentials for real e-mail (MailHog is for development only),
a strong `JWT_SECRET` and database passwords, your public URL (`FRONTEND_URL`), and — for cloud hosting — a domain,
TLS certificate and hosting account. See [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).

## Documentation

* [docs/API.md](docs/API.md) — every endpoint, payloads, status/error codes (plus live Swagger UI).
* [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) — cloud deployment, HTTPS, secrets, e-mail providers, backups, hardening, troubleshooting.
* [database/README.md](database/README.md) — schema, MySQL Workbench usage, backups.

## Security summary

BCrypt(12) password hashing · short-lived JWT + rotating HttpOnly refresh cookie · server-side RBAC and ownership checks ·
Bean Validation on every request DTO (no entity binding → no mass assignment) · parameterised JPA queries (no SQL injection) ·
rate limiting and lockout on auth endpoints · CORS restricted to configured origins · CSP and security headers in nginx ·
hashed single-use tokens · uploads stored outside the web root under random names with extension allow-lists and served as attachments ·
correct answers never sent to candidates before release · marks, timing, roles and attempt status never trusted from the client ·
audit log of administrative actions · secrets only in environment variables.
