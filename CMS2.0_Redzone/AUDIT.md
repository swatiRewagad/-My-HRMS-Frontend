# CMS 2.0 — System Audit

> Living document. Updated as new areas of the system are explored per phase.

---

## 1. Architecture Overview

| Layer | Technology | Notes |
|-------|-----------|-------|
| Frontend (Portal) | Angular 21.2.8 (standalone components) | `cms-portal-frontend`, port 4300 |
| Frontend (Legacy) | Angular (older) | `cms-frontend` — has specs, translate pipe, accessibility service |
| Backend (Main) | Spring Boot 3.2.5, Java 17 | `cms-backend`, port 8082 |
| Workflow Engine | jBPM (KIE) with BPMN2 + Camunda BPMN | `cms-workflow-service`, port 8083, Oracle DB |
| SLA Monitor | Spring Boot + scheduled job | `cms-sla-monitor-service`, port 8086, Oracle DB |
| Search | OpenSearch (not Elasticsearch) | `cms-search-service`, already configured |
| Notification | Kafka-driven | `cms-notification-service` |
| Assignment | Rules-based | `cms-assignment-service` |
| OCR | Multi-provider chain (Groq, PaddleOCR, Gemini, OpenAI, HuggingFace, Azure) | `cms-paddle-ocr` sidecar |
| Auth (Staff) | Keycloak SSO (PKCE S256, check-sso) | realm: `cms`, client: `cms-frontend` |
| Auth (Citizen) | Client-side OTP mock (no real backend verification) | **Critical gap** |
| Database (Portal) | MySQL 8+ | `cms_db`, utf8mb4, HikariCP |
| Database (Workflow/SLA) | Oracle | `CMSDB`, hibernate `validate` mode |
| Cache | Hazelcast (configured but `spring.cache.type: none` in YAML) | Config exists, cache disabled in runtime |
| Message Broker | Apache Kafka | Used for event-driven microservices |
| Monitoring | Prometheus + Grafana + Actuator | Metrics exposed |
| API Gateway | `cms-api-gateway` service exists | Structure TBD |
| Infrastructure | Docker Compose (infra + deployment) | OpenSearch, Keycloak, Kafka, Prometheus, Grafana |

---

## 2. Citizen Auth Flow (OTP)

**Current state — CRITICAL FINDINGS:**

1. **CAPTCHA is client-side only**: Generated in TypeScript (`generateCaptcha()` in `public-login.component.ts:38-41`), validated client-side (`captchaInput !== captchaText` at line 49). Trivially bypassable by calling any API directly.
2. **OTP is entirely mocked**: `verifyOtp()` (line 77-84) accepts any 6-digit input without server verification. `completeLogin()` generates a mock token `'pub_' + Date.now().toString(36)`.
3. **No server-side OTP generation or verification endpoint exists** in `cms-backend`.
4. **Session management**: `sessionStorage`-based, 15-minute timeout with activity renewal. Token is a fabricated string, not a JWT or server-issued session.
5. **No rate limiting on login**: The existing `RateLimitFilter` only applies to `/api/*` paths and is IP-based with in-memory `ConcurrentHashMap` (not distributed — bypassable in multi-instance deployments).
6. **No cooloff mechanism** for failed OTP attempts.
7. **No email fallback** OTP path exists.
8. **Audio CAPTCHA button exists in HTML** (line 39 of template) but has no implementation.

**Citizen session guard**: `publicAuthGuard` checks `PublicAuthService.isSessionValid()` which only validates that sessionStorage has a non-expired activity timestamp. No server-side session validation.

---

## 3. Staff/Officer Auth Flow

- **Keycloak SSO** with PKCE (S256), `check-sso` on load.
- Roles detected: `RBIO_*`, `CEPC_*`, `CRPC_*`, `DEO`, `REVIEWER`, `ADMIN`.
- Departments: RBIO, CEPC, CRPC, ADMIN.
- Token refresh every 60s, session timeout with 60s warning countdown.
- `staffAuthGuard` and `staffRoleGuard` enforce authentication + role-based access.
- **Interceptor `keycloak-token.interceptor.ts`** likely attaches Bearer token to API calls.

---

## 4. Database Schema & Migration Tooling

**Migration tooling: NONE**
- JPA `ddl-auto: update` is used (both dev and prod profiles). No Flyway, no Liquibase.
- Schema changes are auto-applied by Hibernate. This is risky for production but is the current pattern.
- A manual `cms_database_scripts.sql` exists for reference/seeding only.

**Key tables (MySQL, cms_db):**
- `COMPLAINTS` — core entity, has `assigned_officer`, `department`, `assigned_role`, `workflow_stage`, `entity_code`
- `COMPLAINT_CATEGORIES` — hierarchical (parent_id), 10 seed categories
- `COMPLAINT_TIMELINE` — audit trail per complaint
- `COMPLAINT_ATTACHMENTS` — file metadata
- `BANKS` — 12 seeded banks (public + private)
- `FORM_CONFIGS` — JSON schema for dynamic forms
- `SIMULATED_EMAILS` — email simulation for testing
- `EMAIL_DRAFTS` / `EMAIL_IGNORE_ENTRY` — email syndication (new, uncommitted)

**Oracle (workflow/SLA services):**
- `COMPLAINT_MASTER` — referenced by SLA service (`SLA_DUE_DATE` column exists)
- Hibernate `validate` mode — schema must pre-exist.

---

## 5. TAT/SLA Definitions — ALREADY EXISTS

**Critical finding for Phase 9:**
- `cms-sla-monitor-service` already has SLA monitoring with:
  - `SLA_DUE_DATE` column on `COMPLAINT_MASTER` (Oracle)
  - Breach detection: complaints past `SLA_DUE_DATE` → escalation via Kafka
  - Warning detection: complaints within 3 days of `SLA_DUE_DATE`
  - Cron: breach check every 15 min, warning every 30 min
- The workflow service (`WorkflowService.java`) uses jBPM with process ID `com.rbi.cms.complaint-lifecycle`
- **Decision needed**: Whether TAT per-category averages for Phase 9 timer should derive from this existing SLA_DUE_DATE or compute independently from historical resolution times.

---

## 6. Existing i18n / Localization

- **No structured i18n in `cms-portal-frontend`**: All labels are hardcoded English in HTML templates.
- `LanguageTranslationService.java` exists but is for **translating citizen-submitted complaint text** (vernacular→English), not for UI label localization.
- The older `cms-frontend` has a `translate.pipe.ts` and `translate.service.ts` — possible reference for patterns.
- Language detection covers: hi, bn, ta, te, kn, ml, gu, pa, or, ur, as, mr, en.
- No DB-driven translation tables exist currently.

---

## 7. Security Configuration

- **CORS**: `CorsConfig.java` exists (not yet read in detail).
- **Rate Limiting**: IP-based, in-memory only (`bucket4j`), applies to `/api/*` paths.
- **Security Headers Interceptor**: Adds `X-Content-Type-Options`, `X-Requested-With`, `Cache-Control`, `Pragma` to outgoing requests.
- **No Spring Security dependency** in `pom.xml` — no server-side auth framework on cms-backend.
- **No WAF/API gateway configuration** found at the application level (may exist at infrastructure level outside this repo).
- **PII stored in plaintext** in MySQL: `complainant_name`, `complainant_email`, `complainant_phone`, `account_number`, `complainant_address`.

---

## 8. Build & Deploy Pipeline

**Frontend:**
- Angular CLI with `@angular/build:application` builder
- Production config: `outputHashing: all`, budget warnings at 500kB/1MB
- File replacements for environment.prod.ts
- **No explicit optimization/minification flags** — relies on Angular's default prod behavior
- No source maps in prod (not explicitly configured, Angular default omits them)
- Proxy: only `/api/pincode` → postalpincode.in API

**Backend:**
- Maven + Spring Boot plugin
- No CI/CD config (Jenkinsfile, GitHub Actions) found in repo
- Docker: `cms-infra/docker-compose.yml` and `deployment/docker/docker-compose.yml`

---

## 9. Test Suite

**Backend (`cms-backend`):**
- JUnit 5 + Spring Boot Test (dependency in pom.xml)
- Test files exist for: `BankController`, `CategoryController`, `ComplaintController`, `DashboardController`, `EmailSimulationController`, `FileUploadController`, `FormConfigController`, `ComplaintService`, `EmailSimulationService`, `FileStorageConfig`

**Frontend (`cms-portal-frontend`):**
- **No test runner configured** — no Karma/Jest in `package.json` devDependencies
- **No spec files** in `cms-portal-frontend`
- The older `cms-frontend` has specs (Jasmine-style)

**E2E:**
- No Playwright/Cypress setup found

---

## 10. Configuration Mechanism

- Backend: `application.yml` with Spring profiles, environment variables with defaults
- Configurable values: rate limits, OCR chain, file storage paths, Kafka, DB, Keycloak
- Cache TTLs in `HazelcastCacheConfig.java` (hardcoded in Java, not externalized)
- Frontend: `environment.ts` / `environment.prod.ts` — file replacement at build time
- **No centralized config service** (e.g., Spring Cloud Config) — each microservice has its own `application.yml`

---

## 11. Search Infrastructure (Phase 11 relevance)

- **OpenSearch is already deployed and integrated** via `cms-search-service`
- Index: `cms-complaints`
- Fields indexed: `subject`, `description`, `complainantName`, `entityName`, `complaintId`, `resolutionSummary`, `category`, `status`, `priority`, `assignedTeam`, `createdAt`
- Supports full-text multi-match with fuzziness
- **`more_like_this` query is natively available** in OpenSearch — no need for Elasticsearch addition or Oracle Text fallback

---

## 12. Officer Dashboard (Phase 10 relevance)

- `OfficerDashboardComponent` exists with team-based filtering, status filtering
- Stats: total, assigned, in_progress, escalated, SLA breach count
- `OfficerService` provides `getAssignedComplaints(team, status)`
- SLA percentage and due date already surfaced per complaint
- **No auto-refresh mechanism** currently — manual reload only
- **No per-officer scoping** — currently team-based

---

## 13. Mobile / Responsive State

- No viewport meta tag audit done yet (check `index.html`)
- No responsive breakpoints observed in component SCSS (to verify per component)
- PrimeNG is used — has some built-in responsive support

---

## 14. Geo-Location

- No existing geo-IP or location detection code found
- No CDN or edge infrastructure visible in repo

---

## 15. Infrastructure Notes

- Production URLs suggest RBI infrastructure: `cms.rbi.org.in`, `auth.rbi.org.in`
- Integrations configured: ekamev, CDR, SIEM, SMS gateway, SMTP
- Multi-instance potential: Hazelcast configured for cache (but disabled), no service mesh observed
- OpenSearch already in stack — no new infrastructure needed for Phase 11
