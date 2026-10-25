# Database (MySQL 8.x) & MySQL Workbench

MySQL **Workbench is a client**, not a database server. AssessHub connects to a **MySQL Server 8.x** instance —
either the one started by Docker Compose or an existing MySQL Server that you manage with Workbench.

| File | Purpose |
|---|---|
| `01_create_database_and_user.sql` | Creates the `assesshub` database and the `assesshub_app` user (edit the password first). |
| `02_schema.sql` | Full schema (17 tables, FKs, indexes, unique constraints). Identical to Flyway `V1__init_schema.sql`. |
| `03_seed_reference_data.sql` | Roles and default settings. Identical to Flyway `V2__seed_reference_data.sql`. |
| `04_sample_data.sql` | Optional sample question bank + a draft assessment (development only). |

The backend applies the schema automatically with **Flyway** on start-up (`backend/src/main/resources/db/migration`),
so normally you only need step 1.

## Tables

`roles`, `users`, `user_roles`, `refresh_tokens`, `password_reset_tokens`, `questions`, `question_options`,
`assignments`, `assignment_questions`, `assignment_attachments`, `assessment_assignments` (assignees),
`assessment_invitations`, `assessment_attempts`, `assessment_answers`, `assessment_activity_logs`,
`assessment_results`, `email_notifications`, `audit_logs`, `app_settings`.

Design notes:
* All timestamps are `DATETIME(6)` holding **UTC**; the UI converts to the viewer's time zone.
* Passwords are BCrypt hashes; invitation, reset and refresh tokens are stored only as SHA-256 hashes.
* `uk_assessment_assignments (assignment_id, user_id)` prevents duplicate invitations;
  `uk_assessment_attempts_number (assessment_assignment_id, attempt_number)` plus a row lock prevents duplicate attempts.
* `assessment_answers` rows are created when an attempt starts, snapshotting the marks available per question.
* `email_notifications` never stores message bodies or links (they contain secrets).

## Option A — use the Docker MySQL (default) and inspect it with Workbench

1. `docker compose up -d` (with `COMPOSE_PROFILES=local-db,...` in `.env`).
2. Workbench → **+** new connection:
   * Hostname `127.0.0.1`, Port `3308` (`MYSQL_HOST_PORT`), Username `assesshub_app` (or `root`).
   * Password: `DB_PASSWORD` (or `MYSQL_ROOT_PASSWORD`) from `.env` → *Store in Vault…*.
3. Open the `assesshub` schema. Tables appear after the backend has started once.

## Option B — use an existing MySQL Server managed in Workbench

1. Connect to your server in Workbench as an administrative user.
2. Open `01_create_database_and_user.sql`, replace `CHANGE_ME_STRONG_PASSWORD`, run it (⚡).
3. Pick one:
   * **Recommended:** let Flyway create the tables — just start the backend.
   * **Manual:** run `02_schema.sql` then `03_seed_reference_data.sql`, and set
     `FLYWAY_BASELINE_ON_MIGRATE=true` and `FLYWAY_BASELINE_VERSION=2` in `.env` so Flyway adopts the existing schema.
4. In `.env`:
   ```
   COMPOSE_PROFILES=local-mail          # no local-db
   DB_HOST=host.docker.internal         # MySQL on this machine; or the server's host name / IP
   DB_PORT=3306
   DB_NAME=assesshub
   DB_USERNAME=assesshub_app
   DB_PASSWORD=<the password you set>
   ```
   When running the backend outside Docker use `DB_HOST=localhost`.
5. Make sure the server listens on a reachable interface (`bind-address`) and the user's host part (`'%'`)
   allows connections from Docker. Restrict it to the backend's IP/subnet in production.
6. (Optional) After the Super Admin exists, run `04_sample_data.sql` after editing `@owner_email`.

## Bulk demo data (lots of realistic records)

`scripts/generate-demo-data.mjs` builds `database/generated/05_bulk_demo_data.sql` (git-ignored): 12 staff
(`@bgi.test`), ~420 candidates (`@candidates.test`), ~110 questions across 12 departments/stacks, 44 assessments in every
state (draft → archived), ~1,280 assignees with invitations, ~880 attempts (a few live), ~7,300 answers, focus-activity
logs, ~880 results (graded, pending review, released/unreleased), e-mail log and audit trail. Dates are relative to the
moment the script is loaded; scores, statuses and focus counters are consistent with the application's rules.

```bash
node scripts/generate-demo-data.mjs                                   # writes database/generated/05_bulk_demo_data.sql
docker compose cp database/generated/05_bulk_demo_data.sql mysql:/tmp/bulk.sql
docker compose exec mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" assesshub < /tmp/bulk.sql'
```
Or open the generated file in Workbench and run it (⚡). Re-running replaces the previous bulk data only; your own
records are not touched. Seeded accounts sign in with `DEMO_USER_PASSWORD` (they reuse the password hash of
`candidate@demo.local`), e.g. `hessa.al.mansouri@bgi.test` (recruiter) or `noura.al.kuwari@bgi.test` (admin).

## Backups

```bash
# Docker MySQL
docker compose exec -T mysql sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers assesshub' > backups/assesshub-$(date +%F).sql
# Restore
docker compose exec -T mysql sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" assesshub' < backups/assesshub-YYYY-MM-DD.sql
```
In Workbench: **Server → Data Export** (choose *Dump Structure and Data*, *Export to Self-Contained File*).
Also back up the `uploads` Docker volume (attachments and file answers).
