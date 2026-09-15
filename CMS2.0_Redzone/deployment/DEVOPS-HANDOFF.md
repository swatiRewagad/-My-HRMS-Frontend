# CMS 2.0 — DevOps Deployment Handoff

## Repository Split Strategy

Split the monorepo into these **independent** repositories:

| # | Repository | Type | Build Order |
|---|-----------|------|-------------|
| 1 | `cms-parent` | Maven Parent POM | **Build FIRST** |
| 2 | `cms-common` | Shared Java Library | **Build SECOND** |
| 3 | `cms-backend` | Spring Boot (standalone) | Anytime |
| 4 | `cms-api-gateway` | Spring Boot microservice | After #1, #2 |
| 5 | `cms-assignment-service` | Spring Boot microservice | After #1, #2 |
| 6 | `cms-audit-service` | Spring Boot microservice | After #1, #2 |
| 7 | `cms-eligibility-service` | Spring Boot microservice | After #1, #2 |
| 8 | `cms-ingestion-service` | Spring Boot microservice | After #1, #2 |
| 9 | `cms-notification-service` | Spring Boot microservice | After #1, #2 |
| 10 | `cms-outbox-publisher` | Spring Boot microservice | After #1, #2 |
| 11 | `cms-rules-service` | Spring Boot microservice | After #1, #2 |
| 12 | `cms-search-service` | Spring Boot microservice | After #1, #2 |
| 13 | `cms-sla-monitor-service` | Spring Boot microservice | After #1, #2 |
| 14 | `cms-storage-service` | Spring Boot microservice | After #1, #2 |
| 15 | `cms-workflow-service` | Spring Boot microservice | After #1, #2 |
| 16 | `cms-portal-frontend` | Angular (public portal) | Anytime |
| 17 | `cms-frontend` | Angular (staff portal) | Anytime |
| 18 | `cms-paddle-ocr` | Python FastAPI | Anytime |

### CRITICAL: Build Order Dependency

1. **First**: Publish `cms-parent` (root `pom.xml`) to Nexus:
   ```bash
   mvn deploy -N -s settings.xml  # -N = non-recursive (parent only)
   ```

2. **Second**: Publish `cms-common` to Nexus:
   ```bash
   cd cms-common && mvn deploy -s settings.xml
   ```

3. **Then**: All 12 microservices can build in parallel (they pull `cms-parent` + `cms-common` from Nexus)

4. `cms-backend`, both frontends, and `cms-paddle-ocr` are fully standalone — no Nexus dependency.

---

## Nexus Setup (Required Before Microservice Builds)

Each microservice repo needs a `settings.xml` file (provided in `deployment/maven-settings.xml`):

```bash
# Copy to each microservice repo root:
cp deployment/maven-settings.xml <service-repo>/settings.xml
```

The Dockerfile reads `settings.xml` during build to resolve `cms-common` from Nexus.

---

## Environment Variables Per Service

### cms-backend (port 8082)

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `DB_URL` | Yes | `jdbc:mysql://localhost:3306/cms_db?...` | MySQL JDBC URL |
| `DB_USERNAME` | Yes | `cms_user` | Database username |
| `DB_PASSWORD` | Yes | _(empty)_ | Database password |
| `CMS_ENCRYPTION_SECRET` | **Yes** | _(none)_ | 32-char AES key for PII encryption |
| `KAFKA_BOOTSTRAP` | No | `localhost:9092` | Kafka broker |
| `KEYCLOAK_URL` | No | `http://localhost:8180` | Keycloak server |
| `KEYCLOAK_REALM` | No | `cms` | Keycloak realm |
| `KEYCLOAK_ADMIN_CLIENT` | No | `admin-cli` | Keycloak admin client |
| `KEYCLOAK_ADMIN_USER` | No | _(empty)_ | Keycloak admin username |
| `KEYCLOAK_ADMIN_PASSWORD` | No | _(empty)_ | Keycloak admin password |
| `SPRING_PROFILES_ACTIVE` | No | _(default)_ | Set to `openshift` for cluster |
| `SERVER_PORT` | No | `8082` | HTTP port |

### All 12 Microservices (port 8080 each)

These services share a common set of env vars via ConfigMap `cms-common-config`:

| Variable | Required | Description |
|----------|----------|-------------|
| `SPRING_PROFILES_ACTIVE` | Yes | Set to `openshift` |
| `DB_HOST` | Yes | Oracle/MySQL host (from ConfigMap) |
| `DB_PORT` | Yes | Database port |
| `DB_SERVICE` | Yes | Database service name |
| `DB_USERNAME` | Yes | From Secret `cms-db-secret` |
| `DB_PASSWORD` | Yes | From Secret `cms-db-secret` |
| `KAFKA_BOOTSTRAP_SERVERS` | Yes | Kafka broker address |

#### Service-specific additions:

| Service | Extra Env Vars |
|---------|---------------|
| `cms-notification-service` | `SMS_API_KEY`, `SMS_SENDER_ID`, `SMS_TEMPLATE_ID` (from `cms-sms-secret`) |
| `cms-search-service` | `OPENSEARCH_USER`, `OPENSEARCH_PASSWORD` (from `cms-opensearch-secret`) |
| `cms-backend` | `CMS_ENCRYPTION_SECRET`, Keycloak vars (from `cms-keycloak-secret`) |

### Frontend Services (port 80)

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `API_BACKEND_HOST` | Yes | `cms-backend` | Backend service hostname |
| `API_BACKEND_PORT` | Yes | `8080` | Backend service port |

### cms-paddle-ocr (port 8000)

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `PYTHONUNBUFFERED` | No | `1` | Python stdout buffering |

---

## OpenShift Deployment Order

Run `deployment/deploy-all.sh` or apply manually in this order:

```
Phase 1: namespace.yaml
Phase 2: configmap-common.yaml, secret-*.yaml (all 5 secrets)
Phase 3: pvc.yaml
Phase 4: Core services (audit, storage, OCR, notification, search)
Phase 5: Business logic (rules, eligibility, SLA, assignment, workflow, ingestion, outbox)
Phase 6: cms-backend
Phase 7: cms-api-gateway
Phase 8: Frontends (portal + staff)
```

---

## Pre-Deployment Checklist

### Infrastructure Required:
- [ ] MySQL/Oracle database — accessible from cluster
- [ ] Keycloak — with `cms` realm configured
- [ ] Kafka — 3-node cluster (or single for dev)
- [ ] OpenSearch — for full-text search
- [ ] Nexus — for Maven artifact hosting (cms-common, cms-parent)
- [ ] NFS or persistent storage — for `cms-attachments-pvc` and `cms-storage-pvc`

### Secrets to Configure (replace `CHANGE_ME_IN_PRODUCTION`):
- [ ] `cms-db-secret` — DB_USER, DB_PASSWORD, SMTP credentials
- [ ] `cms-keycloak-secret` — client ID, client secret, admin credentials
- [ ] `cms-sms-secret` — SMS gateway API key
- [ ] `cms-opensearch-secret` — OpenSearch credentials
- [ ] `cms-kafka-secret` — Kafka SASL credentials (if auth enabled)

### Routes to Configure:
- [ ] `cms-portal-frontend` → update `spec.host` in `deployment-portal-frontend.yaml`
- [ ] `cms-frontend` → update `spec.host` in `deployment-frontend.yaml`

### Database:
- [ ] Schema: Hibernate `ddl-auto: update` will create tables on first startup
- [ ] No separate migration scripts needed — JPA handles DDL
- [ ] Seed data: `DataInitializer` seeds banks, categories, and regulated entities automatically (only in non-prod profile)

---

## What Each Service Repo Should Contain

### Java Microservice Repo:
```
<service-name>/
├── pom.xml
├── src/
├── Dockerfile
├── .dockerignore
├── settings.xml          ← Copy from deployment/maven-settings.xml
└── README.md            ← Optional
```

### Frontend Repo:
```
<frontend-name>/
├── package.json
├── package-lock.json
├── angular.json
├── src/
├── nginx.conf
├── docker-entrypoint.sh
├── Dockerfile
├── .dockerignore
└── README.md            ← Optional
```

### cms-backend Repo (standalone):
```
cms-backend/
├── pom.xml              ← Uses spring-boot-starter-parent directly (no Nexus needed)
├── src/
├── Dockerfile
├── .dockerignore
└── README.md
```

---

## Verifying Pods Are Running

After deployment, verify with:

```bash
# Check all pods
oc get pods -n rbi-cms

# Check service health
for svc in cms-backend cms-api-gateway cms-rules-service cms-workflow-service; do
  oc exec deploy/$svc -n rbi-cms -- wget -qO- http://localhost:8080/actuator/health
done

# Check frontend routing
curl -k https://<portal-route>/healthz
curl -k https://<staff-route>/healthz
```

---

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| Pod CrashLoopBackOff | Missing env var | Check `oc logs deploy/<svc>` for `IllegalStateException` |
| `cms-common` build failure | Nexus not accessible | Verify `settings.xml` and Nexus URL |
| Frontend 502 | Backend not ready | Check `cms-backend` pod is Running first |
| DB connection refused | Wrong DB_HOST | Verify ConfigMap `cms-common-config` |
| Readiness probe failing | Slow startup | Increase `initialDelaySeconds` in deployment YAML |
