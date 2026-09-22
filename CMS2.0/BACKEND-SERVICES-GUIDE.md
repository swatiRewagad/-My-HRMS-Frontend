# CMS 2.0 — Backend Services Running Guide

> Step-by-step instructions for running backend services locally on Windows.

---

## Prerequisites

| Tool | Version | Path / Notes |
|------|---------|--------------|
| Java (cms-backend) | 17+ | Used by the monolith (`cms-backend`) |
| Java (microservices) | 21 | Used by all other services |
| Maven | 3.9.x | `D:\softwares\apache-maven-3.9.9-bin\apache-maven-3.9.9\bin\mvn.cmd` |
| Node.js | 20+ | For Angular frontends |
| Docker Desktop | Latest | For infrastructure containers (Oracle, Kafka, Keycloak) |

**Set Maven alias (PowerShell — run once per session):**
```powershell
Set-Alias mvn "D:\softwares\apache-maven-3.9.9-bin\apache-maven-3.9.9\bin\mvn.cmd"
```

---

## Scenario A: Frontend-Only Development (Minimal)

**Use when:** You're working on `cms-portal-frontend` or `cms-frontend` and just need APIs responding.

**Services needed:** Only `cms-backend` with H2 in-memory database.  
**External dependencies:** NONE (no Oracle, no Kafka, no Keycloak).

### Steps:

```powershell
# Terminal 1 — Start backend (H2 mode, zero dependencies)
cd d:\CMS-Frontend\cms2.0_dev\CMS2.0\cms-backend
& "D:\softwares\apache-maven-3.9.9-bin\apache-maven-3.9.9\bin\mvn.cmd" spring-boot:run "-Dspring-boot.run.profiles=dev-local"
```
Wait for: `Started CmsApplication in X seconds`

```powershell
# Terminal 2 — Start frontend
cd d:\CMS-Frontend\cms2.0_dev\CMS2.0\cms-portal-frontend
ng serve --port 4200
```

**What you get:**
- Backend API at `http://localhost:8082`
- H2 Console at `http://localhost:8082/h2-console` (JDBC URL: `jdbc:h2:mem:cms_db`, user: `sa`, no password)
- Frontend at `http://localhost:4200`
- Kafka errors suppressed (`missing-topics-fatal: false`)
- OTP auto-populated in dev mode (no real SMS)
- Relaxed rate limits (100 requests/sec)

---

## Scenario B: Full-Stack with Real Database

**Use when:** You need Keycloak SSO login, real data persistence, or Kafka event flows.

**Services needed:** Docker infrastructure + `cms-backend` + `cms-api-gateway` + frontend.

### Step 1 — Start Infrastructure (Docker)

```powershell
cd d:\CMS-Frontend\cms2.0_dev\CMS2.0\deployment\docker
docker-compose up -d oracle-db kafka keycloak-db keycloak
```

Wait times:
| Container | Ready in | How to verify |
|-----------|----------|---------------|
| oracle-db | ~60-90s | `docker logs cms-oracle` → "DATABASE IS READY TO USE" |
| kafka | ~30s | `docker logs cms-kafka` → "started" |
| keycloak-db | ~10s | auto (healthcheck) |
| keycloak | ~30s | Open `http://localhost:8180` → Keycloak login page |

### Step 2 — Set Environment Variables

```powershell
$env:DB_HOST = "localhost"
$env:DB_PORT = "1521"
$env:DB_SERVICE_NAME = "CMSPDB"
$env:DB_USERNAME = "cms_app"
$env:DB_PASSWORD = "cms_app_password"
$env:KAFKA_BOOTSTRAP = "localhost:9092"
$env:KEYCLOAK_URL = "http://localhost:8180"
$env:CMS_ENCRYPTION_SECRET = "dev-local-secret-key-do-not-use-in-prod"
```

### Step 3 — Start Backend

```powershell
cd d:\CMS-Frontend\cms2.0_dev\CMS2.0\cms-backend
& "D:\softwares\apache-maven-3.9.9-bin\apache-maven-3.9.9\bin\mvn.cmd" spring-boot:run
```

### Step 4 — Start API Gateway (Optional)

```powershell
cd d:\CMS-Frontend\cms2.0_dev\CMS2.0\cms-api-gateway
& "D:\softwares\apache-maven-3.9.9-bin\apache-maven-3.9.9\bin\mvn.cmd" spring-boot:run
```

### Step 5 — Start Frontend

```powershell
cd d:\CMS-Frontend\cms2.0_dev\CMS2.0\cms-portal-frontend
ng serve --port 4200
```

---

## Scenario C: Full Microservices Stack

**Use when:** End-to-end testing of complaint lifecycle (filing → assignment → workflow → resolution).

### Step 1 — Start ALL Infrastructure

```powershell
cd d:\CMS-Frontend\cms2.0_dev\CMS2.0\deployment\docker
docker-compose up -d
```

This starts: Oracle, Kafka, Keycloak (+Postgres), OpenSearch, Kafka UI, Prometheus, Grafana.

### Step 2 — Set Environment Variables (same as Scenario B)

### Step 3 — Build Shared Module First

```powershell
cd d:\CMS-Frontend\cms2.0_dev\CMS2.0
# `clean` matters: cms-backend is a separate Maven project and resolves cms-common as a jar from the local
# repository, so a plain `install` can republish stale classes. New types then fail at runtime with
# NoSuchFieldError rather than at compile time.
& "D:\softwares\apache-maven-3.9.9-bin\apache-maven-3.9.9\bin\mvn.cmd" clean install -pl cms-common -am -DskipTests
```

### Step 4 — Start Services in Order

Open separate terminals for each. Start in this sequence:

| Order | Service | Command | Port |
|-------|---------|---------|------|
| 1 | cms-outbox-publisher | `cd cms-outbox-publisher && mvn spring-boot:run` | 8089 |
| 2 | cms-audit-service | `cd cms-audit-service && mvn spring-boot:run` | 8088 |
| 3 | cms-storage-service | `cd cms-storage-service && mvn spring-boot:run` | 8090 |
| 4 | cms-notification-service | `cd cms-notification-service && mvn spring-boot:run` | 8087 |
<!-- cms-notification-service now needs a datasource: it resolves SIMULATED_EMAILS rows to SENT or FAILED
     after dispatching them, and refuses to start if database/V28 and V29 have not been applied.
     Started with no profile as above it uses application.yml, where cms.notification.mode defaults to
     SIMULATE - so it records outcomes without contacting any mail server. Real delivery needs BOTH
     CMS_NOTIFICATION_MODE=SEND and CMS_NOTIFICATION_EMAIL_ENABLED=true; no profile can enable it. -->
| 5 | cms-rules-service | `cd cms-rules-service && mvn spring-boot:run` | 8084 |
| 6 | cms-eligibility-service | `cd cms-eligibility-service && mvn spring-boot:run` | 8081 |
| 7 | cms-assignment-service | `cd cms-assignment-service && mvn spring-boot:run` | 8085 |
| 8 | cms-workflow-service | `cd cms-workflow-service && mvn spring-boot:run` | 8083 |
| 9 | cms-sla-monitor-service | `cd cms-sla-monitor-service && mvn spring-boot:run` | 8086 |
| 10 | cms-search-service | `cd cms-search-service && mvn spring-boot:run` | 8091 |
| 11 | cms-backend | `cd cms-backend && mvn spring-boot:run` | 8082 |
| 12 | cms-api-gateway | `cd cms-api-gateway && mvn spring-boot:run` | 8080 |

> **Note:** Services 1-10 can be started in parallel (no inter-service dependencies). The key rule is: infrastructure first → services → gateway last.

### Step 5 — Start Frontends

```powershell
# Public portal
cd cms-portal-frontend && ng serve --port 4200

# Officer portal (separate terminal)
cd cms-frontend && ng serve --port 4201
```

---

## Port Reference (Quick Lookup)

| Service | Port | Purpose |
|---------|------|---------|
| cms-api-gateway | 8080 | API routing / entry point |
| cms-eligibility-service | 8081 | MRE eligibility rules |
| cms-backend | 8082 | Monolith (RBIO, CEPC, AA, citizen auth) |
| cms-workflow-service | 8083 | BPMN process engine |
| cms-rules-service | 8084 | DRL rule management |
| cms-assignment-service | 8085 | Officer assignment |
| cms-sla-monitor-service | 8086 | SLA breach detection |
| cms-notification-service | 8087 | Email/SMS dispatch |
| cms-audit-service | 8088 | Audit trail |
| cms-outbox-publisher | 8089 | Outbox → Kafka bridge |
| cms-storage-service | 8090 | File storage |
| cms-search-service | 8091 | OpenSearch indexing |
| cms-paddle-ocr | 8100 | Python OCR service |
| cms-portal-frontend | 4200 | Public Angular app |
| cms-frontend | 4201 | Officer Angular app |

| Infrastructure | Port | Access |
|---------------|------|--------|
| Oracle DB | 1521 | JDBC connection |
| Kafka | 9092 | Bootstrap servers |
| Keycloak | 8180 | `http://localhost:8180` (admin/admin) |
| OpenSearch | 9200 | `http://localhost:9200` |
| Kafka UI | 8090 | `http://localhost:8090` |
| Prometheus | 9090 | `http://localhost:9090` |
| Grafana | 3000 | `http://localhost:3000` (admin/admin) |
| H2 Console | 8082 | `http://localhost:8082/h2-console` (dev-local only) |

---

## Common Errors & Fixes

### 1. `CMS_ENCRYPTION_SECRET must be set`

**Cause:** Running cms-backend without the dev-local profile.  
**Fix:** Add the profile flag:
```powershell
& mvn spring-boot:run "-Dspring-boot.run.profiles=dev-local"
```
This sets a pre-configured dev secret automatically.

---

### 2. `'mvn' is not recognized as the name of a cmdlet`

**Cause:** Maven not in system PATH.  
**Fix:** Use the full path or set alias:
```powershell
# Option A: Full path every time
& "D:\softwares\apache-maven-3.9.9-bin\apache-maven-3.9.9\bin\mvn.cmd" spring-boot:run

# Option B: Set alias (per session)
Set-Alias mvn "D:\softwares\apache-maven-3.9.9-bin\apache-maven-3.9.9\bin\mvn.cmd"

# Option C: Add to PATH permanently (run as Admin)
[Environment]::SetEnvironmentVariable("PATH", $env:PATH + ";D:\softwares\apache-maven-3.9.9-bin\apache-maven-3.9.9\bin", "User")
```

---

### 3. Port 8080 Conflict (Keycloak vs API Gateway)

**Cause:** Both Keycloak and cms-api-gateway want port 8080.  
**Fix:** Keycloak is configured on 8180 in docker-compose. If running Keycloak standalone:
```powershell
# Run Keycloak on port 9090 instead
bin\kc.bat start-dev --http-port=9090
```
Then set: `$env:KEYCLOAK_URL = "http://localhost:9090"`

---

### 4. Port 8082 Conflict (cms-backend vs cms-ingestion-service)

**Cause:** Both default to port 8082.  
**Fix:** Don't run both simultaneously. If you must:
```powershell
# Override ingestion port
cd cms-ingestion-service
& mvn spring-boot:run "-Dserver.port=8092"
```

---

### 5. Oracle Connection Refused / Timeout

**Cause:** Oracle container takes 60-90 seconds to initialize.  
**Fix:**
```powershell
# Wait for Oracle to be ready
docker logs cms-oracle --follow
# Look for: "DATABASE IS READY TO USE!"

# Or check health
docker inspect cms-oracle --format='{{.State.Health.Status}}'
# Should say "healthy"
```

---

### 6. Kafka Topic Errors / `TopicAuthorizationException`

**Cause:** Topics don't exist yet, or service can't connect.  
**Fix A (dev-local profile):** Already has `missing-topics-fatal: false` — errors are warnings only.  
**Fix B (create topics manually):**
```powershell
docker exec cms-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic complaint.ingested --partitions 6 --replication-factor 1
docker exec cms-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic complaint.assigned --partitions 6 --replication-factor 1
docker exec cms-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic complaint.inprogress --partitions 6 --replication-factor 1
docker exec cms-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic complaint.escalated --partitions 6 --replication-factor 1
docker exec cms-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic complaint.resolved --partitions 6 --replication-factor 1
docker exec cms-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic complaint.closed --partitions 6 --replication-factor 1
docker exec cms-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic complaint.dlq --partitions 3 --replication-factor 1
```

---

### 7. Keycloak Realm Not Found / 401 Unauthorized

**Cause:** The `cms` realm hasn't been imported into Keycloak.  
**Fix:**
1. Open `http://localhost:8180` → Login (admin/admin)
2. Click "Create Realm"
3. Import realm JSON from: `cms-infrastructure/keycloak/cms-realm.json` (if available)
4. Or manually create realm named `cms` with client `cms-portal`

---

### 8. Frontend `config.json` 404

**Cause:** Only happens in Docker/OpenShift builds. Locally Angular uses `environment.ts`.  
**Fix:** This is NOT an error in local development. Ignore it. The app works fine using the compiled environment file.

---

### 9. `BUILD FAILURE - Could not resolve dependencies`

**Cause:** `cms-common` module not installed in local Maven repo.  
**Fix:**
```powershell
cd d:\CMS-Frontend\cms2.0_dev\CMS2.0
# `clean` matters: cms-backend is a separate Maven project and resolves cms-common as a jar from the local
# repository, so a plain `install` can republish stale classes. New types then fail at runtime with
# NoSuchFieldError rather than at compile time.
& "D:\softwares\apache-maven-3.9.9-bin\apache-maven-3.9.9\bin\mvn.cmd" clean install -pl cms-common -am -DskipTests
```

---

### 10. `Address already in use: bind` (Port already taken)

**Cause:** Another process is occupying the port.  
**Fix:**
```powershell
# Find what's using a port (e.g., 8082)
netstat -ano | findstr :8082

# Kill the process by PID
Stop-Process -Id <PID> -Force
```

---

### 11. H2 Console — How to Access

Only available in `dev-local` profile.
1. Open: `http://localhost:8082/h2-console`
2. Settings:
   - JDBC URL: `jdbc:h2:mem:cms_db`
   - User: `sa`
   - Password: _(leave empty)_
3. Click "Connect"

---

### 12. Angular Build Warnings (exit code 255)

**Cause:** Angular writes warnings to stderr (deprecation notices in unrelated components). The build itself succeeds.  
**Fix:** Not a real error. Look for `Application bundle generation complete` in the output — that confirms success.

---

## Environment Variables (Full Reference)

```powershell
# === Database (Oracle) ===
$env:DB_HOST = "localhost"
$env:DB_PORT = "1521"
$env:DB_SERVICE_NAME = "CMSPDB"
$env:DB_USERNAME = "cms_app"
$env:DB_PASSWORD = "cms_app_password"

# === Kafka ===
$env:KAFKA_BOOTSTRAP = "localhost:9092"

# === Keycloak ===
$env:KEYCLOAK_URL = "http://localhost:8180"
$env:KEYCLOAK_REALM = "cms"
$env:KEYCLOAK_ADMIN_USER = "admin"
$env:KEYCLOAK_ADMIN_PASSWORD = "admin"

# === Security ===
$env:CMS_ENCRYPTION_SECRET = "dev-local-secret-key-do-not-use-in-prod"

# === OpenSearch (optional) ===
$env:OPENSEARCH_URL = "http://localhost:9200"

# === OCR (optional) ===
$env:GROQ_API_KEY = "your-groq-api-key"
```

---

## Quick Reference: "What Do I Need for X?"

| Task | Services Required |
|------|-------------------|
| Frontend UI changes | cms-backend (dev-local) + frontend |
| Login/Auth testing | cms-backend + Keycloak + frontend |
| Filing a complaint (full flow) | All infrastructure + cms-backend + frontend |
| Eligibility rules testing | Kafka + cms-eligibility-service + cms-backend |
| Officer dashboard work | cms-backend + Keycloak + cms-frontend (port 4201) |
| Email/SMS notification testing | Kafka + cms-notification-service |
| Search functionality | OpenSearch + Kafka + cms-search-service + cms-backend |
| File upload testing | cms-backend (dev-local) OR cms-storage-service |
| SLA breach testing | Kafka + Oracle + cms-sla-monitor-service |
| Workflow/BPMN testing | Kafka + Oracle + cms-workflow-service |
