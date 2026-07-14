# CMS 2.0 — RBI Complaint Management System

## Project Overview

This is a **microservices-based complaint management system** for RBI (Reserve Bank of India) under the Integrated Ombudsman Scheme. It handles the full lifecycle of banking complaints: filing, eligibility check, assignment, workflow processing, SLA monitoring, appeals, and final orders.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│  FRONTENDS (Angular 21)                                             │
│  ├── cms-portal-frontend (Port 4200) — Public portal + Staff SSO   │
│  └── cms-frontend (Port 4201) — RBIO Officer portal                │
└────────────────────────┬────────────────────────────────────────────┘
                         │ /api/*
                         ▼
┌─────────────────────────────────────────────────────────────────────┐
│  cms-api-gateway (Port 8080) — Routes to microservices              │
└────────────────────────┬────────────────────────────────────────────┘
                         │
    ┌────────────────────┼────────────────────────────────┐
    ▼                    ▼                                ▼
┌─────────┐  ┌──────────────────┐  ┌──────────────────────────────┐
│Backend  │  │ Microservices    │  │ Infrastructure               │
│(8082)   │  │                  │  │                              │
│Monolith │  │ eligibility:8081 │  │ Oracle DB (1521)             │
│RBIO +   │  │ ingestion: 8082 │  │ Kafka (9092)                 │
│CEPC +   │  │ workflow:  8083 │  │ Keycloak (8080/9090)         │
│AA flows │  │ rules:     8084 │  │ OpenSearch (9200)            │
│         │  │ assignment:8085 │  │ Redis (6379) — optional      │
│         │  │ sla-mon:   8086 │  │                              │
│         │  │ notify:    8087 │  │                              │
│         │  │ audit:     8088 │  │                              │
│         │  │ outbox:    8089 │  │                              │
│         │  │ storage:   8090 │  │                              │
│         │  │ search:    8091 │  │                              │
└─────────┘  └──────────────────┘  └──────────────────────────────┘
```

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | Angular 21, PrimeNG 21, keycloak-js 26, SCSS |
| Backend | Java 21, Spring Boot 3.4.1, Spring Security OAuth2 |
| Database | Oracle (prod/SIT), H2 (dev-local) |
| Messaging | Apache Kafka (Transactional Outbox Pattern) |
| Auth | Keycloak 26 (OIDC/PKCE, realm: `rbi-cms`) |
| Search | OpenSearch 2.18 |
| Workflow | Camunda/Drools (BPMN + DRL rules) |
| OCR | PaddleOCR (Python microservice) |
| Cache | Hazelcast (embedded in cms-backend) |
| E2E Tests | Playwright |
| Deployment | OpenShift (RHEL8 nginx for frontends, Temurin JRE 21 for backends) |

## Module Reference

### Frontends
| Module | Purpose | Port |
|--------|---------|------|
| `cms-portal-frontend` | Public complaint filing, eligibility wizard, staff SSO portal (CEPC/RBIO/RE/AA) | 4200 |
| `cms-frontend` | Officer-only portal (RBIO dashboard, case management) | 4201 |

### Backend Services
| Module | Purpose | Port | DB? | Kafka? |
|--------|---------|------|-----|--------|
| `cms-backend` | Monolith: RBIO, CEPC, AA workflows, citizen auth, PII encryption | 8082 | Yes | Yes |
| `cms-api-gateway` | API routing, rate limiting, request forwarding | 8080 | No | No |
| `cms-eligibility-service` | MRE eligibility rules, pincode validation | 8081 | Yes | Yes |
| `cms-ingestion-service` | Email/form intake, OCR extraction, deduplication | 8082 | Yes | Yes |
| `cms-workflow-service` | BPMN process engine, state transitions | 8083 | Yes | Yes |
| `cms-rules-service` | DRL rule management, routing/escalation rules | 8084 | Yes | Yes |
| `cms-assignment-service` | Round-robin officer assignment, workload balancing | 8085 | Yes | Yes |
| `cms-sla-monitor-service` | SLA breach detection, escalation triggers | 8086 | Yes | Yes |
| `cms-notification-service` | Email/SMS dispatch (no DB, consumes Kafka) | 8087 | No | Yes |
| `cms-audit-service` | Audit trail logging | 8088 | Yes | Yes |
| `cms-outbox-publisher` | Polls outbox table, publishes to Kafka topics | 8089 | Yes | Yes |
| `cms-storage-service` | File storage (attachments, evidence) | 8090 | No | No |
| `cms-search-service` | OpenSearch indexing and full-text search | 8091 | No | Yes |
| `cms-paddle-ocr` | Python OCR microservice | 5000 | No | No |

### Supporting
| Module | Purpose |
|--------|---------|
| `cms-common` | Shared DTOs, enums, utilities (Maven dependency) |
| `cms-infrastructure` | OpenShift ConfigMaps, Secrets, manifests |
| `deployment` | Dockerfiles, OpenShift deployment YAMLs, Tekton pipelines |
| `cms-e2e-tests` | Playwright E2E tests (all modules) |
| `database` | Oracle DDL scripts (V1-V4), seed data |
| `camunda-modeler-setup` | BPMN process definitions |

## Kafka Topics (Outbox Pattern)

```
complaint.ingested    → New complaint created
complaint.assigned    → Officer assigned
complaint.inprogress  → Work started
complaint.escalated   → SLA breach / manual escalation
complaint.resolved    → Resolution provided
complaint.closed      → Final closure
complaint.dlq         → Dead letter (permanently failed)
```

Flow: Service writes to `OUTBOX_EVENTS` table → `cms-outbox-publisher` polls (5s interval, max 5 retries) → publishes to Kafka topic → marks as PUBLISHED.

## Authentication & Roles

**Keycloak Realm:** `rbi-cms`

| Client | Used by |
|--------|---------|
| `cms-portal` | Public portal frontend |
| `cms-officer-portal` | Officer frontend |

**Roles:**
- CEPC: `DO`, `REVIEWER`, `INCHARGE`, `CA`, `ADMIN`, `CP`
- RBIO: `RBIO_OFFICER`, `RBIO_SUPERVISOR`, `RBIO_CONCILIATOR`, `RBIO_ADJUDICATOR`, `RBIO_ADMIN`
- RE: `RE_NODAL_OFFICER`, `RE_PNO`
- AA: `AA_REGISTRAR`, `AA_BENCH_OFFICER`, `AA_AUTHORITY`, `AA_ADMIN`

Backend validates JWT via `spring.security.oauth2.resourceserver.jwt.issuer-uri`. Role extraction from `realm_access.roles` claim with `ROLE_` prefix.

## Running Locally

### Quick Start (cms-backend only, H2 in-memory)
```bash
cd cms-backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev-local
```
- No Kafka/Oracle needed
- H2 console: http://localhost:8082/h2-console
- Encryption secret pre-configured in `application-dev-local.yml`

### Full Stack (requires Kafka + Oracle on server)
```bash
# Set env vars
export DB_HOST=<oracle-ip> DB_PORT=1521 DB_SERVICE_NAME=CMSPDB
export DB_USERNAME=cms_user DB_PASSWORD=<pass>
export KAFKA_BOOTSTRAP_SERVERS=<kafka-ip>:9092
export KEYCLOAK_URL=http://localhost:9090
export CMS_ENCRYPTION_SECRET=<min-16-chars>

# Start Keycloak
cd c:/tools/keycloak-26.0.0 && bin/kc.bat start-dev --http-port=9090

# Start services (each in separate terminal)
cd cms-api-gateway && mvn spring-boot:run
cd cms-backend && mvn spring-boot:run
# ... other services as needed
```

### Frontends
```bash
cd cms-portal-frontend && ng serve --port 4200
cd cms-frontend && ng serve --port 4201
```

## E2E Tests

```bash
cd cms-e2e-tests

# Run all
npx playwright test

# Run specific spec
npx playwright test tests/public-portal2.spec.ts --headed --project=chromium

# With slow-mo for demos
npx playwright test tests/launch.spec.ts --headed --config=../cms-portal-frontend/playwright-slow.config.ts

# View report
npx playwright show-report
```

Test files: `cms-e2e-tests/tests/` (launch, public-portal2) and `cms-portal-frontend/e2e/` (aa, cepc, rbio, re-portal).

## OpenShift Deployment

**Image base:**
- Backend: `eclipse-temurin:21-jre-alpine` (multi-stage: build with Maven, run with JRE)
- Frontend: `rebit-quay-quay-image-registry.apps.hocpclz.rebit.local/buildpiper/rhel8_nginx_124:amd64-9.7`

**Frontend runtime config injection:**
1. `config-template.json` in image has `${API_BASE_URL}`, `${KEYCLOAK_URL}`, etc.
2. Container CMD: `envsubst < config-template.json > config.json && nginx -g 'daemon off;'`
3. Angular app fetches `/config.json` at startup

**Config sources:** `cms-infrastructure/openshift/configmaps.yaml` (17 ConfigMaps) + `secrets.yaml`

**Namespace:** `rbi-cms`

## Key Files

| Path | Purpose |
|------|---------|
| `cms-backend/src/main/resources/application.yml` | Production config (env var placeholders) |
| `cms-backend/src/main/resources/application-dev-local.yml` | Local dev (H2, relaxed security) |
| `cms-infrastructure/openshift/configmaps.yaml` | All OpenShift ConfigMaps |
| `cms-infrastructure/openshift/secrets.yaml` | DB/Kafka/Keycloak secrets |
| `deployment/openshift/` | Deployment YAMLs per service |
| `cms-portal-frontend/src/assets/config-template.json` | Runtime config template |
| `cms-backend/src/main/java/com/hrms/cms/service/EncryptionKeyService.java` | PII encryption (requires CMS_ENCRYPTION_SECRET) |
| `database/oracle/V1__complete_schema.sql` | Full Oracle DDL |

## Common Issues

1. **`CMS_ENCRYPTION_SECRET must be set`** → Use `dev-local` profile: `mvn spring-boot:run -Dspring-boot.run.profiles=dev-local`
2. **Port 4200 already in use** → Kill existing process or use `--port 4202`
3. **Keycloak port conflict with api-gateway (both 8080)** → Run Keycloak on 9090: `bin/kc.bat start-dev --http-port=9090`
4. **Frontend `config.json` 404** → Only relevant in Docker/OpenShift. Locally, Angular uses `environment.ts`
