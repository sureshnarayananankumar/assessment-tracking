# Deployment guide

AssessHub ships as two containers plus MySQL:

```
 browser ──HTTPS──► [TLS proxy / load balancer] ──► frontend (nginx :80) ──/api──► backend (Spring Boot :8080) ──► MySQL 8
                                                     static React build                Flyway migrations, SMTP
```

The frontend nginx serves the SPA and proxies `/api`, so the browser talks to a single origin (required for
the `SameSite=Strict` refresh cookie). The backend should **not** be exposed publicly.

## 1. Single cloud VM (Docker Compose)

Works on any Linux VM (AWS EC2, Azure VM, GCP Compute Engine, DigitalOcean, Hetzner…).

```bash
# on the server (Docker Engine + Compose plugin installed)
git clone <your repo> assesshub && cd assesshub
cp .env.example .env && nano .env      # fill in real secrets (see checklist below)
docker compose up -d --build
docker compose ps                      # all services "healthy"
```

Production `.env` checklist:

| Variable | Production value |
|---|---|
| `COMPOSE_PROFILES` | `local-db` if MySQL runs on the VM; empty when using a managed database. Never `local-mail`. |
| `JWT_SECRET` | `openssl rand -base64 48` (unique per environment) |
| `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD` | long random values |
| `FRONTEND_URL` | `https://assess.example.com` |
| `CORS_ALLOWED_ORIGINS` | `https://assess.example.com` |
| `COOKIE_SECURE` | `true` |
| `MAIL_*` | your SMTP provider (see below) |
| `MAIL_LOG_LINKS`, `DEMO_DATA_ENABLED` | `false` |
| `SWAGGER_ENABLED` | `false` (or protect it) |
| `BOOTSTRAP_ADMIN_*` | set for the first start, then remove the password and restart |
| `FRONTEND_HOST_PORT` | e.g. `127.0.0.1:8090` behind the TLS proxy |

### HTTPS

Terminate TLS in front of the frontend container, e.g. with Caddy (automatic Let's Encrypt):

```
# /etc/caddy/Caddyfile
assess.example.com {
    encode gzip
    reverse_proxy 127.0.0.1:8090
    header Strict-Transport-Security "max-age=31536000; includeSubDomains"
}
```

or nginx + certbot (`proxy_pass http://127.0.0.1:8090;`). Because another proxy now sits in front, change
`proxy_set_header X-Forwarded-For $remote_addr;` in `frontend/nginx/default.conf.template` to
`$proxy_add_x_forwarded_for` so audit logs and rate limits see the real client IP.

## 2. Managed platforms

| Component | AWS | Azure | Google Cloud |
|---|---|---|---|
| Containers | ECS Fargate / App Runner | Container Apps / App Service | Cloud Run |
| MySQL 8 | RDS for MySQL / Aurora MySQL | Azure Database for MySQL – Flexible | Cloud SQL for MySQL |
| Secrets | Secrets Manager / SSM | Key Vault | Secret Manager |
| File uploads | EFS volume mounted at `/app/data/uploads` | Azure Files | Filestore / GCS FUSE |
| TLS / routing | ALB + ACM | Front Door / App Gateway | HTTPS Load Balancer |

Steps:
1. Build & push images: `docker build -t <registry>/assesshub-backend:1.0 backend` and the same for `frontend`.
2. Create the MySQL database + user (`database/01_create_database_and_user.sql`); Flyway creates tables on boot.
3. Run the backend with the environment variables from `.env.example` injected from the secret store.
   Use JDBC TLS: `DB_PARAMS=sslMode=VERIFY_IDENTITY&connectionTimeZone=UTC&characterEncoding=UTF-8`.
4. Run the frontend with `API_UPSTREAM=http://<internal backend address>:8080`.
5. Route the public domain to the frontend only. Health checks: frontend `/healthz`, backend `/actuator/health/readiness`.

**Scaling notes.** The backend is stateless (JWT + DB) except for:
* the in-memory login rate limiter → limit at the gateway/WAF or switch to a shared store when running >1 replica;
* scheduled jobs (auto-submit, reminders, status changes) → idempotent, but add a distributed lock such as ShedLock for many replicas;
* uploads → use a shared volume.

## 3. E-mail providers

Set `MAIL_SMTP_AUTH=true`, `MAIL_SMTP_STARTTLS=true`, `MAIL_PORT=587` and:

| Provider | MAIL_HOST | MAIL_USERNAME / MAIL_PASSWORD |
|---|---|---|
| SendGrid | smtp.sendgrid.net | `apikey` / API key |
| Mailgun | smtp.mailgun.org | SMTP credentials |
| Amazon SES | email-smtp.<region>.amazonaws.com | SES SMTP credentials |
| Microsoft 365 | smtp.office365.com | mailbox + app password / OAuth relay |
| Gmail / Workspace | smtp.gmail.com | address + app password |

Verify the sender domain (SPF, DKIM, DMARC) so invitations are not marked as spam. Delivery results are visible
under **Audit logs → E-mail log** and in the `email_notifications` table.

## 4. Security hardening checklist

- [ ] HTTPS everywhere, `COOKIE_SECURE=true`, HSTS enabled at the edge.
- [ ] Only ports 80/443 open publicly; MySQL and backend bound to private networks (`127.0.0.1` in Compose).
- [ ] MySQL user limited to the `assesshub` schema; host part restricted to the backend subnet; TLS required.
- [ ] Secrets in a secret manager or a root-only `.env` (`chmod 600 .env`); never in Git or images.
- [ ] `SWAGGER_ENABLED=false`, `DEMO_DATA_ENABLED=false`, `MAIL_LOG_LINKS=false`.
- [ ] Bootstrap admin password removed from the environment after first login.
- [ ] Regular OS/image updates (`docker compose pull && docker compose up -d --build`).
- [ ] Log shipping from `docker compose logs` / container stdout to your log platform.

## 5. Backups & restore

* **Database:** nightly `mysqldump --single-transaction` (see `database/README.md`) or the managed service's
  automated backups + point-in-time recovery. Test restores regularly.
* **Uploads:** back up the `uploads` volume, e.g.
  `docker run --rm -v assesshub_uploads:/data -v $PWD/backups:/b alpine tar czf /b/uploads-$(date +%F).tgz -C /data .`
* Keep backups encrypted and off-site; retention per your data-protection policy.

## 6. Upgrades

```bash
git pull
docker compose up -d --build     # Flyway applies new migrations automatically on backend start
docker compose logs -f backend   # watch for "Successfully applied N migrations"
```
Never edit an already-applied migration; add `V3__…sql`, `V4__…sql` etc.

## 7. Troubleshooting

| Symptom | Fix |
|---|---|
| Backend exits with `JWT_SECRET is not configured` | Set a ≥32-byte `JWT_SECRET`. |
| `Communications link failure` | Check `DB_HOST`/`DB_PORT`, firewall, MySQL `bind-address`, and that the MySQL user host allows Docker. |
| `Public Key Retrieval is not allowed` | Keep `allowPublicKeyRetrieval=true` in `DB_PARAMS` or enable TLS. |
| Flyway: `Found non-empty schema(s) without schema history table` | Tables were created manually: set `FLYWAY_BASELINE_ON_MIGRATE=true` and `FLYWAY_BASELINE_VERSION=2`. |
| Signed out after every refresh | Front-end and API must share one origin (use the nginx `/api` proxy); over HTTP keep `COOKIE_SECURE=false`. |
| E-mails not arriving | Check **E-mail log**; for local dev open MailHog at http://localhost:8026. |
