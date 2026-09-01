# CMS Portal Frontend - Services Dependency Reference

## Services Required to Run cms-portal-frontend

### REQUIRED (Core functionality will break without these)

| # | Service | Port | Purpose | Must Run? |
|---|---------|------|---------|-----------|
| 1 | **cms-backend** | 8082 | Monolith backend — handles ALL `/api/v1/*` endpoints (complaints, auth, translations, workflow, CRPC, OCR, reports, etc.) | YES |
| 2 | **Keycloak** | 9090 | OIDC authentication for staff login (CEPC/RBIO/RE/AA roles). Frontend uses `keycloak-js` with realm `cms`, client `cms-frontend` | YES (for staff portal) |

### OPTIONAL (Features degrade gracefully without these)

| # | Service | Port | Purpose | Impact if Missing |
|---|---------|------|---------|-------------------|
| 3 | Kafka | 9092 | Async events (outbox publishing) | No immediate frontend impact; backend logs warnings, notifications won't dispatch |
| 4 | OpenSearch | 9200 | Full-text search (`/api/v1/search/complaints`) | Search feature unavailable; config `similar-cases.enabled: false` disables it |
| 5 | cms-paddle-ocr | 5000 | OCR extraction for scanned documents | OCR extract buttons will fail; manual data entry still works |
| 6 | External integrations (Ekamev, CDR, SIEM, SMS, SMTP) | 9001-9005 | External RBI systems | Not needed locally; only relevant in production |

### NOT NEEDED for Local Development

| Service | Why Not Needed |
|---------|----------------|
| cms-api-gateway (8080) | Frontend calls backend directly at 8082 in dev |
| cms-eligibility-service (8081) | Eligibility logic is in cms-backend monolith |
| cms-workflow-service (8083) | Workflow logic is in cms-backend monolith |
| cms-rules-service (8084) | Rules logic is in cms-backend monolith |
| cms-assignment-service (8085) | Assignment logic is in cms-backend monolith |
| cms-sla-monitor-service (8086) | SLA logic is in cms-backend monolith |
| cms-notification-service (8087) | Notification logic is in cms-backend monolith |
| cms-audit-service (8088) | Audit logic is in cms-backend monolith |
| cms-outbox-publisher (8089) | Outbox logic is in cms-backend monolith |
| cms-storage-service (8090) | Storage logic is in cms-backend monolith |
| cms-search-service (8091) | Search logic is in cms-backend monolith |
| Oracle DB | H2 in-memory used for dev-local profile |
| Redis | Cache type is `simple` (in-memory ConcurrentHashMap) for dev-local |

## Quick Start (Minimum Setup)

```bash
# Terminal 1: Start backend (H2 in-memory, no external DB needed)
cd cms-backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev-local

# Terminal 2: Start Keycloak (only needed for staff login flows)
cd c:/tools/keycloak-26.0.0
bin/kc.bat start-dev --http-port=9090

# Terminal 3: Start frontend
cd cms-portal-frontend
ng serve --port 4200
```

## Feature-to-Service Mapping

| Frontend Feature | Backend Endpoint Group | External Dependency |
|-----------------|----------------------|---------------------|
| Public Home Page | None (static) | None |
| Language Switch / Translations | `/api/v1/i18n/*` | cms-backend only |
| Citizen OTP Login | `/api/v1/citizen/auth/*` | cms-backend only |
| File Complaint | `/api/v1/complaints`, `/api/v1/eligibility/*`, `/api/v1/routing/*` | cms-backend only |
| Complaint Tracking | `/api/v1/complaints?phone=` | cms-backend only |
| File Appeal | `/api/v1/appeals/*` | cms-backend only |
| Staff SSO Login | Keycloak OIDC | Keycloak (port 9090) |
| CRPC Dashboard | `/api/v1/crpc/*`, `/api/v1/email-syndication/*` | cms-backend + Keycloak |
| RBIO Dashboard | `/api/v1/workflow/rbio/*`, `/api/v1/rbio/*` | cms-backend + Keycloak |
| RE Portal | `/api/v1/re-portal/*` | cms-backend + Keycloak |
| AA (Appellate) | `/api/v1/appeals/*` | cms-backend + Keycloak |
| OCR Extraction | `/api/v1/ocr/extract` | cms-backend (calls paddle-ocr or Groq API) |
| Report Builder | `/api/v1/reports/*` | cms-backend only |
| Full-text Search | `/api/v1/search/*` | cms-backend + OpenSearch (disabled in dev) |
| Real-time Notifications | WebSocket `/ws/notifications` | cms-backend only |
| Team Management | `/cms-workflow/api/v1/assignment/*` | Separate microservice (optional) |

## Environment Configuration

```typescript
// src/environments/environment.ts
{
  apiBaseUrl: 'http://localhost:8082',    // All API calls go here
  keycloakUrl: 'http://localhost:9090',   // Staff auth only
  realm: 'cms',
  devAutoPopulateOtp: true,              // Auto-fills OTP in dev
  devDefaultOtp: '123456'
}
```

## Summary

For **public citizen flows** (home, file complaint, track complaint, translations):
- Only `cms-backend` (port 8082) is needed

For **staff flows** (CRPC, RBIO, RE, AA dashboards):
- `cms-backend` (port 8082) + `Keycloak` (port 9090)

For **full feature parity** with production:
- Add Kafka, OpenSearch, and paddle-ocr as needed
