# Growth Finance Services Ltd — Loan Platform

Single-tenant loan management platform for Growth Finance Services Ltd.
Built with Spring Boot 3.3 (Java 21) · Next.js 14 · PostgreSQL 16 · Docker.

This deployment serves exactly one institution. The public site is at the
root URL — there is no tenant picker and no `/growthfinance` path prefix.
See [BANK_READINESS.md](./BANK_READINESS.md) before taking this live with
real customer funds or data.

---

## Quick Start (Local Dev — no Docker needed)

```bash
# Terminal 1: Backend (uses H2 in-memory, no PostgreSQL needed)
cd backend/loan-management-api/loan-backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Terminal 2: Frontend
cd frontend/loan-management-ui
npm install && npm run dev
```

Open http://localhost:3000 for the public site, or http://localhost:3000/login
with `admin@growthfinance.rw` / `Admin@1234` for the staff dashboard.

---

## Production Deployment (Docker + PostgreSQL)

### 1. Prerequisites
- Docker 24+ and Docker Compose v2
- A server with 2GB+ RAM (4GB recommended)
- This bank's real domain (e.g. `growthfinance.rw`), pointed at the server

### 2. Configure Environment
```bash
cp .env.example .env
# Edit .env with real values — DB_PASSWORD, JWT_SECRET, APP_ENCRYPTION_KEY,
# APP_INDEX_KEY, MAIL_*, CORS_ORIGINS, DOMAIN. Every secret must be freshly
# generated — none of the example values are safe to deploy with.
```

### 3. Set the domain in nginx
Edit `docker/nginx/conf.d/loansaas.conf` — replace `growthfinance.rw
www.growthfinance.rw` in the `server_name` directive with this bank's real
domain(s).

### 4. Deploy
```bash
chmod +x deploy/scripts/*.sh
./deploy/scripts/deploy.sh
```

This will:
- Generate a self-signed SSL cert (replace with Let's Encrypt for production)
- Start PostgreSQL, run Flyway migrations automatically
- Build and start the Spring Boot backend (seeded for Growth Finance only)
- Build and start the Next.js frontend (root URL = the bank's public site)
- Start the Nginx reverse proxy

### 5. SSL (Let's Encrypt — real domain only)
```bash
./deploy/scripts/ssl-init.sh admin@growthfinance.rw growthfinance.rw www.growthfinance.rw
```

---

## Service URLs (after deployment)

| Service            | URL                                              |
|--------------------|---------------------------------------------------|
| Public site        | https://growthfinance.rw                          |
| Staff login         | https://growthfinance.rw/login                    |
| API                | https://growthfinance.rw/api                       |
| Swagger UI          | internal/VPN only — see BANK_READINESS.md          |
| Health check        | internal only (private IP ranges)                  |
| H2 Console          | http://localhost:8080/h2-console (dev profile only) |

---

## Demo Login Credentials (dev/staging only — remove before go-live)

| Role    | Email                     | Password      |
|---------|---------------------------|---------------|
| Admin   | admin@growthfinance.rw    | Admin@1234    |
| Officer | officer@growthfinance.rw  | Officer@1234  |

These are seeded by `DataSeeder.java` for local/dev use. Disable or remove
this seeder path before running against real customer data — see
BANK_READINESS.md.

---

## Docker Commands

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f backend
docker-compose logs -f frontend
docker-compose logs -f nginx

# Stop all services
docker-compose down

# Stop and remove volumes (DELETES ALL DATA)
docker-compose down -v

# Rebuild after code changes
docker-compose build --no-cache backend
docker-compose up -d backend

# Health check
./deploy/scripts/health-check.sh

# Database backup (run daily via cron)
./deploy/scripts/backup.sh

# Database restore
./deploy/scripts/restore.sh ./backups/loansaas_20240101_020000.sql.gz
```

---

## Database Management

### Connect to PostgreSQL
```bash
docker-compose exec postgres psql -U loansaas -d loansaas_growthfinance
```

### Flyway Migration Status
Query the `flyway_schema_history` table directly, or use the authenticated
`/actuator/flyway` endpoint (internal network only).

### Add a new migration
Create `src/main/resources/db/migration/V15__your_change.sql`.
Flyway auto-runs it on next startup.

### Switch to PostgreSQL locally (without Docker)
1. Install PostgreSQL and create the database:
```sql
CREATE DATABASE loansaas_growthfinance;
CREATE USER loansaas WITH PASSWORD 'change_me';
GRANT ALL PRIVILEGES ON DATABASE loansaas_growthfinance TO loansaas;
```
2. Remove `-Dspring-boot.run.profiles=dev` from the startup command and set
   `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`/`JWT_SECRET`/`APP_ENCRYPTION_KEY`/`APP_INDEX_KEY`.

---

## Architecture

```
Internet
   │
   ▼
[Nginx :443] ──────────── SSL termination, rate limiting, gzip, security headers
   ├── /api/*   ─────────► [Spring Boot :8080] ─► [PostgreSQL :5432 (loansaas_growthfinance)]
   ├── /swagger-ui/ ─────► [Spring Boot :8080]   (internal IPs only)
   └── /*       ─────────► [Next.js :3000]        (root URL = the bank's public site)
```

Single tenant end to end: one backend process, one database, one frontend
build, one domain. See `docker-compose.yml`.

---

## Environment Variables Reference

| Variable              | Required | Default        | Description                              |
|------------------------|----------|----------------|-------------------------------------------|
| DB_PASSWORD            | YES      | —              | PostgreSQL password                        |
| JWT_SECRET             | YES      | —              | JWT signing secret (64+ random chars)      |
| APP_ENCRYPTION_KEY     | YES      | —              | PII field-level encryption key             |
| APP_INDEX_KEY          | YES      | —              | PII lookup-hash key                        |
| CORS_ORIGINS           | YES      | —              | This bank's allowed origin(s)              |
| NEXT_PUBLIC_API_URL    | YES      | /api           | API URL seen by the browser                |
| NEXT_PUBLIC_TENANT_SLUG| No       | growthfinance  | Which org's data the frontend renders      |
| MAIL_ENABLED           | No       | false          | Enable email notifications                 |
| MAIL_HOST/USERNAME/PASSWORD | No  | —              | SMTP settings                              |
| FLUTTERWAVE_SECRET_KEY | No       | —              | Payment provider secret key                |
| OXR_APP_ID             | No       | —              | Open Exchange Rates app ID                 |
| SPRING_PROFILE         | No       | default        | Spring profile (default/dev)               |

None of these have safe production defaults — every value in `.env` must be
generated fresh for this deployment.
