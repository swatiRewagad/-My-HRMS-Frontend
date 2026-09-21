# RBIO Module — Backend API Audit

**Scope:** Complete user journey from the RBIO Module Dashboard through to the final End Page (complaint details / workflow close-out).
**Date:** 2026-09-21
**Branch:** `rbio_updated_frontend_sprint1`
**Method:** Static trace of every HTTP call in `cms-portal-frontend/src/app/components/rbio/**` plus the services those components inject, each path then matched against the actual `@RequestMapping` / `@*Mapping` declarations in `cms-backend` and `cms-search-service`.

## Legend

| Status | Meaning |
|---|---|
| OK | Frontend path resolves to a real backend handler |
| **MISSING** | Frontend calls it; **no backend handler exists** (runtime 404) |
| **MISMATCH** | A handler exists but at a different path than the frontend calls |

---

## 1. Route Map

| Route | Component | Role |
|---|---|---|
| `/rbio` | `RbioHomeComponent` → `RbioDashboardComponent` | Dashboard (entry point) |
| `/rbio/complaint/:id` | `RbioComplaintDetailsView` | **End Page** (details + all workflow actions) |
| `/rbio/create-complaint` | `RbioCreateComplaintComponent` | Intake / task form |
| `/rbio/supervisor-dashboard` | `RbioSupervisorDashboardComponent` | Supervisor queue |
| `/staff/rbio/task/:id` | `RbioCreateComplaintComponent` | Staff task view |

---

## 2. Dashboard Page

| Page / Component | Controller Name | API Path | Method | Status |
|---|---|---|---|---|
| Dashboard — status code dropdown (`loadStatusCodes`) | `DepartmentRoleStatusController` | `/api/v1/departments/{department}/role-status?role=` | GET | OK |
| Dashboard — global search / list view / KPI / tabs / column filters / unread / without-attachments (single unified `POST`) | `ComplaintSearchController` *(cms-search-service)* | `/cms-search/api/v1/search/complaints/search?page=&size=&sort=` | POST | OK ⚠ |
| Dashboard filter — states | `LocationController` | `/api/v1/location/states` | GET | OK |
| Dashboard filter — districts | `LocationController` | `/api/v1/location/districts?state=` | GET | OK |
| Advanced search — category dropdown | `CategoryController` | `/api/v1/categories` | GET | **MISMATCH** |
| Advanced search — bank / entity dropdown | `BankController` | `/api/v1/banks` | GET | **MISMATCH** |

⚠ **`complaintsSearchUrl` is hardcoded** to `http://localhost:8091/cms-search/api/v1/search/complaints/search` in
[rbio-dashboard.component.ts:61](CMS2.0/cms-portal-frontend/src/app/components/rbio/rbio-dashboard/rbio-dashboard.component.ts#L61).
It ignores `environment.apiBaseUrl` entirely, so the dashboard grid — the primary view of the module — will not load in Docker, OpenShift or production.

**MISMATCH detail:** `ApiService` prefixes every path with `/api/v1`
([api.service.ts:15](CMS2.0/cms-portal-frontend/src/app/services/api.service.ts#L15)), but `CategoryController` is mapped at `/api/categories` and `BankController` at `/api/banks` (no `v1`). `SecurityConfig` permits `/api/v1/categories/**` but no controller serves it.

> Note: KPI counts, tab counts, unread-only and without-attachments are **not** separate endpoints — they are fields in the unified search request body, resolved by the search service.

---

## 3. End Page — `/rbio/complaint/:id` (`RbioComplaintDetailsView`)

### 3.1 Page initialization & summary

| Page / Component | Controller Name | API Path | Method | Status |
|---|---|---|---|---|
| Summary load | `ComplaintController` | `/api/complaints/rbio/{id}/summary` | GET | OK |
| Summary save | `ComplaintController` | `/api/complaints/rbio/{id}/summary` | PUT | OK |
| Complaint fetch (detail view) | `ComplaintApiV1Controller` | `/api/v1/complaints/{complaintNumber}` | GET | OK |
| Past complaints of complainant | `PastComplaintController` | `/api/v1/past-complaints/by-complainant` | GET | OK |

### 3.2 History / audit log

| Page / Component | Controller Name | API Path | Method | Status |
|---|---|---|---|---|
| Timeline / audit trail | `ComplaintController` | `/api/complaints/{id}/timeline` | GET | OK |
| Linked-complaint timeline | `ComplaintController` | `/api/complaints/{targetId}/timeline` | GET | OK |
| Action override history (`rbio-action-override-history`) | — | `/api/v1/complaints/{id}/action-override` | GET | **MISSING** |
| Record action override | — | `/api/v1/complaints/{id}/action-override` | POST | **MISSING** |

### 3.3 Attachments (upload / download)

| Page / Component | Controller Name | API Path | Method | Status |
|---|---|---|---|---|
| List attachments | `FileUploadController` | `/api/files/complaint/{complaintId}` | GET | OK |
| Upload attachment | `FileUploadController` | `/api/files/upload` | POST | OK |
| Download attachment | `FileUploadController` | `/api/files/download/{attachmentId}` | GET | OK |
| Stream / preview attachment | `FileUploadController` | `/api/files/stream/{attachmentId}` | GET | OK |
| Conciliation document upload (`rbio-conciliation`) | — | `/api/v1/complaints/{complaintNumber}/documents` | POST | **MISSING** |

### 3.4 "Other users" data & permissions

| Page / Component | Controller Name | API Path | Method | Status |
|---|---|---|---|---|
| Office list | `KeycloakUserController` | `/api/v1/keycloak/offices` | GET | OK |
| User availability by role | `KeycloakUserController` | `/api/v1/keycloak/users/availability?role=` | GET | OK |
| Round-robin next assignee | `KeycloakUserController` | `/api/v1/keycloak/users/next-assignee?role=&office=` | GET | OK |
| DEO pool (details view) | `EmailSyndicationApiController` | `/api/v1/email-syndication/deo` | GET | OK |
| DEO pool (create-complaint) | — | `/api/v1/email-syndication/deo-pool` | GET | **MISSING** |
| Last-active officer (auto-reassign) | — | `/api/v1/complaints/{id}/last-active-officer` | GET | **MISSING** |

### 3.5 Workflow action triggers

| Page / Component | Controller Name | API Path | Method | Status |
|---|---|---|---|---|
| **Sent for Approval** | `ComplaintApiV1Controller` | `/api/v1/complaints/{complaintNumber}/send-for-approval` | POST | OK |
| **Sent Back** / Office-head decision | `ComplaintApiV1Controller` | `/api/v1/complaints/{complaintNumber}/office-head-decision` | POST | OK |
| Generic RBIO action (all actions below funnel here) | `WorkflowController` | `/api/v1/workflow/rbio/action/{complaintNumber}` | POST | OK |
| Create complaint from details view | `WorkflowController` | `/api/v1/workflow/rbio/create-complaint` | POST | OK |
| Closure clause dropdown (`rbio-complaint-detail`) | `WorkflowController` | `/api/v1/workflow/closure-clauses?role=` | GET | OK |
| Transfer request | `CrpcHeadController` | `/api/v1/crpc/head/transfers/request` | POST | OK |

**Actions routed through `POST /api/v1/workflow/rbio/action/{complaintNumber}`** — one endpoint, discriminated by the `action` field in the body:

| Component | `action` value |
|---|---|
| `rbio-conciliation` | conciliation submit / outcome |
| `rbio-advisory` | advisory issue |
| `rbio-adjudication` | 4 distinct adjudication actions |
| `rbio-deputy-decision` | `DEPUTY_OMBUDSMAN_DECISION` |
| `rbio-forward-regulatory` | `FORWARD_TO_REGULATORY_BODY` |
| `RbioWorkflowService.reassignComplaint` | `REASSIGN` |
| `rbio-supervisor-dashboard` | supervisor actions |
| `rbio-complaint-detail` | dealing-official actions |

### 3.6 Comments & email correspondence

| Page / Component | Controller Name | API Path | Method | Status |
|---|---|---|---|---|
| Load comments | `ComplaintApiV1Controller` | `/api/v1/complaints/{complaintNumber}/comments` | GET | OK |
| Add comment | `ComplaintApiV1Controller` | `/api/v1/complaints/{complaintNumber}/comments` | POST | OK |
| Load emails | `ComplaintApiV1Controller` | `/api/v1/complaints/{complaintNumber}/emails` | GET | OK |
| Send email | `ComplaintApiV1Controller` | `/api/v1/complaints/{complaintNumber}/emails` | POST | OK |
| Email threads for complaint | `EmailSimulationController` | `/api/email-simulation/complaints/{complaintNumber}/threads` | GET | OK |
| Single thread detail | `EmailSimulationController` | `/api/email-simulation/thread/{threadId}` | GET | **MISMATCH** |
| Physical letter draft | `EmailSyndicationApiController` | `/api/v1/email-syndication/drafts/physical-letter` | POST | OK |

**MISMATCH detail:** the frontend calls `/thread/{threadId}` (singular) at
[rbio-complaint-details-view.component.ts:2957](CMS2.0/cms-portal-frontend/src/app/components/rbio/rbio-complaint-details-view/rbio-complaint-details-view.component.ts#L2957); the controller declares `/threads/{threadId}` (plural).

### 3.7 Nodal officer records

| Page / Component | Controller Name | API Path | Method | Status |
|---|---|---|---|---|
| List nodal records | `ComplaintApiV1Controller` | `/api/v1/complaints/nodal-records` | GET | OK |
| Nodal record comments | `ComplaintApiV1Controller` | `/api/v1/complaints/nodal-records/{recordNumber}/comments` | GET | OK |
| Add nodal record comment | `ComplaintApiV1Controller` | `/api/v1/complaints/nodal-records/{recordNumber}/comments` | POST | OK |
| Forward to RE | `ComplaintApiV1Controller` | `/api/v1/complaints/nodal-records/{recordNumber}/forward-to-re` | POST | OK |

### 3.8 Conciliation

| Page / Component | Controller Name | API Path | Method | Status |
|---|---|---|---|---|
| Load conciliation | `ComplaintController` | `/api/complaints/rbio/{id}/conciliation` | GET | OK |
| Save conciliation | `ComplaintController` | `/api/complaints/rbio/{id}/conciliation` | PUT | OK |

### 3.9 Master data, entity & location lookups

| Page / Component | Controller Name | API Path | Method | Status |
|---|---|---|---|---|
| Regulators dropdown | `MasterDataController` | `/api/v1/masters/regulators` | GET | OK |
| RBI departments dropdown | `MasterDataController` | `/api/v1/masters/rbi-departments` | GET | OK |
| Entity list (search/autocomplete) | `ComplaintRoutingController` | `/api/v1/routing/entities/list` | GET | OK |
| Entity detail | `ComplaintRoutingController` | `/api/v1/routing/entities/{id}` | GET | OK |
| States | `LocationController` | `/api/v1/location/states` | GET | OK |
| Districts | `LocationController` | `/api/v1/location/districts?state=` | GET | OK |
| Pincode lookup | `LocationController` | `/api/v1/location/pincode/{pincode}` | GET | OK |
| OCR extraction | `OcrController` | `/api/v1/ocr/extract` | POST | OK |
| Regulatory bodies (`rbio-forward-regulatory`) | — | `/api/v1/master-data/regulatory-bodies` | GET | **MISSING** |

---

## 4. Create Complaint Page — `/rbio/create-complaint`

Reuses §3.4, §3.5, §3.6 and §3.9 endpoints. Additional / distinct calls:

| Page / Component | Controller Name | API Path | Method | Status |
|---|---|---|---|---|
| Load complaint for task | `ComplaintApiV1Controller` | `/api/v1/complaints/{complaintNumber}` | GET | OK |
| Create RBIO complaint | `WorkflowController` | `/api/v1/workflow/rbio/create-complaint` | POST | OK |
| DEO pool | — | `/api/v1/email-syndication/deo-pool` | GET | **MISSING** |

---

## 5. Supervisor Dashboard — `/rbio/supervisor-dashboard`

| Page / Component | Controller Name | API Path | Method | Status |
|---|---|---|---|---|
| Supervisor task queue | `WorkflowController` | `/api/v1/workflow/rbio/all-tasks?officer=&role=SUPERVISOR` | GET | OK |
| Supervisor action | `WorkflowController` | `/api/v1/workflow/rbio/action/{complaintNumber}` | POST | OK |

---

## 6. Sub-Components Backed Entirely by Missing Endpoints

`RbioWorkflowService` ([rbio-workflow.service.ts](CMS2.0/cms-portal-frontend/src/app/services/rbio-workflow.service.ts)) declares 8 endpoint families with **no backend implementation anywhere** in `cms-backend`. Every one swallows its error via `catchError(() => of(...))`, so these components render silently empty instead of failing loudly.

| Component | Controller Name | API Path | Method | Status |
|---|---|---|---|---|
| `rbio-add-entity` | — | `/api/v1/complaints/{id}/additional-entities` | GET | **MISSING** |
| `rbio-add-entity` | — | `/api/v1/complaints/{id}/additional-entities` | POST | **MISSING** |
| `rbio-legal-case` | — | `/api/v1/complaints/{id}/legal-case` | GET | **MISSING** |
| `rbio-legal-case` | — | `/api/v1/complaints/{id}/legal-case` | POST | **MISSING** |
| `rbio-legal-case` | — | `/api/v1/complaints/{id}/legal-case` | PUT | **MISSING** |
| `rbio-action-override-history` | — | `/api/v1/complaints/{id}/action-override` | GET / POST | **MISSING** |
| `rbio-forward-regulatory` | — | `/api/v1/master-data/regulatory-bodies` | GET | **MISSING** |
| Auto-reassign | — | `/api/v1/complaints/{id}/last-active-officer` | GET | **MISSING** |
| Closure validation | — | `/api/v1/complaints/{id}/final-decision-status` | GET | **MISSING** |
| Impleading | — | `/api/v1/complaints/{id}/implead-nodal-record` | POST | **MISSING** |
| Impleading validation | — | `/api/v1/complaints/{id}/implead-validation` | GET | **MISSING** |

> `ComplaintApiV1Controller` does expose `POST /api/v1/complaints/{complaintNumber}/rbio/reassign`, but no frontend code calls it — reassignment goes through `POST /workflow/rbio/action/{id}` with `action: 'REASSIGN'` instead.

---

## 7. Hidden / Global Actions

Five HTTP interceptors are registered globally in [app.config.ts:70-76](CMS2.0/cms-portal-frontend/src/app/app.config.ts#L70-L76) and run on every RBIO request:

| Interceptor | Behaviour | Calls a CMS API? |
|---|---|---|
| `keycloakTokenInterceptor` | Attaches bearer token; skips `/api/v1/complaints`, `/api/v1/eligibility`, `/api/v1/tat`, `/api/v1/citizen/auth` | No — token obtained from Keycloak via `keycloak-js` |
| `securityHeadersInterceptor` | Adds security headers; special-cases `/api/pincode` | No |
| `antiAutomationInterceptor` | Adds anti-bot headers for `/api/v1/citizen/` and `/api/v1/complaints` | No |
| `sessionTimeoutInterceptor` | Reads token to detect expiry | No |
| `errorHandlerInterceptor` | Central error mapping | No |

**Findings on background/global traffic:**

- **No telemetry or analytics endpoint exists.** Nothing in the RBIO module posts usage, tracing or metrics data.
- **Session checks are entirely client-side.** `SessionService` reads only `sessionStorage`/`localStorage`; `KeycloakAuthService` talks to `environment.keycloakUrl` (the Keycloak realm), never to a CMS controller. There is no server-side session-validation API in this flow.
- **`NotificationController` (`/api/v1/notifications`, `/unread-count`, `/unread`, `/mark-read`, `/mark-all-read`) and the `/ws/notifications` WebSocket are implemented and wrapped by `NotificationService`, but `NotificationService` is not injected by any RBIO component** — no notification traffic occurs in this module.
- **`TranslationController` (`/api/v1/i18n/translations/{locale}`, `/api/v1/i18n/locales`) is likewise unused by RBIO components** — the only `translate` matches in the RBIO tree are CSS transforms and a local `translateTabIdToStatus()` helper.
- **`ComplaintReadReceipt` entity and `ComplaintReadReceiptRepository` exist** (added on this branch) but **no controller exposes read-receipt endpoints**. The dashboard's unread flag is a field in the unified search payload, resolved inside `cms-search-service`.
- **`/api/pincode/*` is proxied to the third party `https://api.postalpincode.in`** via [proxy.conf.json](CMS2.0/cms-portal-frontend/proxy.conf.json) — not a CMS controller. RBIO's own pincode lookup uses `LocationController` instead.
- Visited-complaint state is kept in `localStorage` (`rbio_visitedComplaintIds`) and user state in `sessionStorage` (`rbio_user`) — no API involved.

---

## 8. Controller Summary

Distinct backend controllers touched by the RBIO journey:

| # | Controller Name | Base Path | Service |
|---|---|---|---|
| 1 | `ComplaintApiV1Controller` | `/api/v1/complaints` | cms-backend |
| 2 | `ComplaintController` | `/api/complaints` | cms-backend |
| 3 | `WorkflowController` | `/api/v1/workflow` | cms-backend |
| 4 | `DepartmentRoleStatusController` | `/api/v1/departments` | cms-backend |
| 5 | `MasterDataController` | `/api/v1/masters` | cms-backend |
| 6 | `KeycloakUserController` | `/api/v1/keycloak` | cms-backend |
| 7 | `LocationController` | `/api/v1/location` | cms-backend |
| 8 | `ComplaintRoutingController` | `/api/v1/routing` | cms-backend |
| 9 | `FileUploadController` | `/api/files` | cms-backend |
| 10 | `OcrController` | `/api/v1/ocr` | cms-backend |
| 11 | `PastComplaintController` | `/api/v1/past-complaints` | cms-backend |
| 12 | `CrpcHeadController` | `/api/v1/crpc/head` | cms-backend |
| 13 | `EmailSyndicationApiController` | `/api/v1/email-syndication` | cms-backend |
| 14 | `EmailSimulationController` | `/api/email-simulation` | cms-backend |
| 15 | `ComplaintSearchController` | `/cms-search/api/v1/search/complaints` | cms-search-service |

Referenced but not reachable at the path called: `CategoryController` (`/api/categories`), `BankController` (`/api/banks`).

---

## 9. Defects Found

| # | Severity | Issue |
|---|---|---|
| 1 | **High** | Dashboard grid search URL hardcoded to `http://localhost:8091` — breaks the module's primary view outside local dev ([rbio-dashboard.component.ts:61](CMS2.0/cms-portal-frontend/src/app/components/rbio/rbio-dashboard/rbio-dashboard.component.ts#L61)) |
| 2 | **High** | 11 `RbioWorkflowService` endpoints have no backend implementation; errors are swallowed by `catchError`, so legal-case, add-entity, override-history and forward-regulatory fail silently (§6) |
| 3 | **High** | Conciliation attachment upload posts to `/api/v1/complaints/{id}/documents`, which does not exist |
| 4 | Medium | Advanced-search category and bank dropdowns call `/api/v1/categories` and `/api/v1/banks`; controllers are mapped at `/api/categories` and `/api/banks` |
| 5 | Medium | Email thread detail calls `/api/email-simulation/thread/{id}`; controller declares `/threads/{id}` |
| 6 | Medium | `/api/v1/email-syndication/deo-pool` does not exist (details view correctly uses `/deo`) |
| 7 | Low | `SecurityConfig` permits `/api/v1/categories/**`, a path no controller serves — stale rule |
| 8 | Low | `ComplaintReadReceipt` entity/repository added with no controller exposing them — incomplete feature |
| 9 | Low | `POST /api/v1/complaints/{id}/rbio/reassign` is implemented but dead — no caller |
