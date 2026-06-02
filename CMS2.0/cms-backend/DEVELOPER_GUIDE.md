# CMS (Complaint Management System) — Developer Guide

## Table of Contents
1. [Architecture Overview](#architecture-overview)
2. [Features Implemented](#features-implemented)
3. [Project Structure](#project-structure)
4. [API Reference](#api-reference)
5. [Security & Validation](#security--validation)
6. [Caching Strategy](#caching-strategy)
7. [File Upload System](#file-upload-system)
8. [Configuration Guide](#configuration-guide)
9. [Test Coverage](#test-coverage)
10. [Developer Modification Guide](#developer-modification-guide)

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                         FRONTEND (Angular 20)                        │
│  Port: 4200                                                          │
│  ┌──────────┐ ┌───────────┐ ┌──────────────┐ ┌───────────────────┐ │
│  │   Home   │ │File       │ │Track         │ │Email Simulation   │ │
│  │Dashboard │ │Complaint  │ │Complaint     │ │(Email-based flow) │ │
│  └──────────┘ └───────────┘ └──────────────┘ └───────────────────┘ │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  Interceptors: Security (CSRF) + Rate Limiting (client-side) │   │
│  └──────────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  Services: CMS | FileUpload | InputSanitizer | Translate     │   │
│  │            | Accessibility | OCR | ComplaintParser | Store    │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────┬───────────────────────────────┘
                                      │ HTTP (REST API)
┌─────────────────────────────────────▼───────────────────────────────┐
│                      BACKEND (Spring Boot 3.2.5)                      │
│  Port: 8082                                                          │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  Filters: CORS → Rate Limit (Bucket4j) → Controllers         │   │
│  └──────────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  Controllers: Complaint | File | Dashboard | Email | Bank     │   │
│  │               Category | FormConfig                            │   │
│  └──────────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  Services: ComplaintService | FileStorageService              │   │
│  │            EmailSimulationService | FormConfigService          │   │
│  └──────────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  Repositories (JPA): Complaint | Attachment | Timeline        │   │
│  │                      Bank | Category | FormConfig | Email     │   │
│  └──────────────────────────────────────────────────────────────┘   │
│  ┌────────────────┐  ┌───────────────┐  ┌───────────────────────┐  │
│  │  MySQL / H2    │  │  Redis Cache  │  │  File System Storage  │  │
│  └────────────────┘  └───────────────┘  └───────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

### Tech Stack

| Layer     | Technology                     | Version |
|-----------|-------------------------------|---------|
| Frontend  | Angular (Standalone Components) | 20      |
| Backend   | Spring Boot                    | 3.2.5   |
| Language  | Java / TypeScript              | 17 / 5  |
| Database  | MySQL (prod) / H2 (dev/test)   | 8+ / 2  |
| Cache     | Redis                          | 7+      |
| Build     | Maven / Angular CLI            | —       |
| Testing   | JUnit 5 + Mockito / Jest       | —       |

---

## Features Implemented

### 1. Complaint Management (Core)
- Multi-step complaint filing with dynamic form schema
- Complaint tracking by complaint number
- Status workflow: `pending → in_progress → resolved/escalated → closed`
- Priority management: `low`, `medium`, `high`
- Full timeline/audit trail per complaint
- Pagination and search (by subject, name, complaint number)

### 2. File Upload System
- **Chunked upload** for large files (>5MB) with resume capability
- **Single upload** for small files
- File assembly from chunks using `FileChannel`
- SHA-256 checksum verification after assembly
- Configurable limits (max size, allowed types, max files per complaint)
- Stale upload cleanup (temp chunks older than 1 hour)
- Media streaming with HTTP Range requests (206 Partial Content)

### 3. Email-Based Complaint Flow
- Simulated email intake: user sends email → system auto-replies with form → user replies with details → confirmation
- Thread-based email tracking
- Email statistics (total threads, awaiting form, completed)
- Downloadable form templates

### 4. Dynamic Form Configuration
- JSON-based form schema (stored in DB)
- Multi-step wizard support
- Pre-filing modal with conditional fields
- Supports field types: text, email, tel, select, textarea, radio, file, date
- Field dependencies (`dependsOn`) and dynamic options (`optionsSource`)

### 5. Dashboard & Analytics
- Real-time stats: total, pending, in-progress, resolved, closed, escalated
- Priority distribution: high, medium, low
- Cached for performance (2-minute TTL)

### 6. Multilingual Support (Frontend)
- English and Hindi language support
- Complaint text processing with Hindi-to-English translation
- OCR service integration for document text extraction
- Auto-detect language from text input

### 7. Accessibility (Frontend)
- High contrast mode
- Reduced motion support
- Configurable font sizes
- Screen reader announcements via live regions

### 8. Physical Complaint Support
- Voice-to-text complaint intake
- NLP-based field extraction from free text
- Auto-detection of: name, phone, email, bank, account number, category, state, district, pincode

---

## Project Structure

### Backend

```
cms-backend/
├── src/main/java/com/hrms/cms/
│   ├── CmsApplication.java              # Main Spring Boot entry point
│   ├── config/
│   │   ├── AsyncConfig.java             # Thread pool (10 core, 50 max)
│   │   ├── CorsConfig.java             # CORS for localhost:4200-4202
│   │   ├── DataInitializer.java        # Seeds categories, banks, form schema
│   │   ├── FileStorageConfig.java      # File storage properties bean
│   │   ├── GlobalExceptionHandler.java # @RestControllerAdvice error handling
│   │   ├── RateLimitFilter.java        # Bucket4j per-IP rate limiting
│   │   └── RedisCacheConfig.java       # Redis cache with per-cache TTLs
│   ├── controller/
│   │   ├── BankController.java         # GET /api/banks
│   │   ├── CategoryController.java     # GET /api/categories
│   │   ├── ComplaintController.java    # CRUD /api/complaints
│   │   ├── DashboardController.java    # GET /api/dashboard
│   │   ├── EmailSimulationController.java # /api/email-simulation
│   │   ├── FileUploadController.java   # /api/files (upload/download/stream)
│   │   └── FormConfigController.java   # /api/form-config
│   ├── dto/
│   │   ├── ChunkUploadResponse.java    # Chunk upload status response
│   │   ├── DashboardResponse.java      # Dashboard statistics DTO
│   │   ├── EmailReplyWithFormRequest.java # Form reply request
│   │   ├── FileComplaintRequest.java   # Complaint filing request
│   │   ├── IncomingEmailRequest.java   # Incoming email request
│   │   └── UpdateComplaintRequest.java # Complaint update request
│   ├── entity/
│   │   ├── Bank.java                   # Bank/financial institution
│   │   ├── Complaint.java             # Core complaint entity
│   │   ├── ComplaintAttachment.java   # File attachment metadata
│   │   ├── ComplaintCategory.java     # Hierarchical categories
│   │   ├── ComplaintTimeline.java     # Audit trail entries
│   │   ├── FormConfig.java           # Dynamic form schemas
│   │   └── SimulatedEmail.java       # Email thread messages
│   ├── repository/                    # Spring Data JPA interfaces
│   └── service/
│       ├── ComplaintService.java      # Core business logic
│       ├── EmailSimulationService.java # Email flow logic
│       ├── FileStorageService.java    # File I/O operations
│       └── FormConfigService.java     # Form schema CRUD
├── src/main/resources/
│   └── application.yml                # Multi-profile config
└── src/test/                          # Unit tests (140 tests)
```

### Frontend

```
cms-frontend/
├── src/app/
│   ├── app.routes.ts                  # Lazy-loaded routes
│   ├── components/
│   │   ├── dynamic-field/             # Generic form field renderer
│   │   ├── email-simulation/          # Email intake UI
│   │   ├── file-complaint/            # Multi-step complaint form
│   │   ├── footer/                    # App footer
│   │   ├── header/                    # App header with navigation
│   │   ├── home/                      # Dashboard page
│   │   ├── layout/                    # Shell layout (header+footer+router)
│   │   ├── physical-complaint/        # Voice/NLP complaint intake
│   │   └── track-complaint/           # Complaint tracking page
│   ├── interceptors/
│   │   ├── rate-limit.interceptor.ts  # Client-side rate limiting
│   │   └── security.interceptor.ts    # CSRF token + security headers
│   ├── models/
│   │   └── form-schema.model.ts       # TypeScript interfaces for form schema
│   ├── pipes/
│   │   └── translate.pipe.ts          # i18n pipe
│   ├── services/
│   │   ├── accessibility.service.ts   # A11y preferences
│   │   ├── cms.service.ts             # HTTP client for all APIs
│   │   ├── complaint-parser.service.ts # NLP field extraction
│   │   ├── complaint-store.service.ts  # Client-side state management
│   │   ├── complaint-text-processor.service.ts # Language detection + translation
│   │   ├── file-cache.service.ts      # IndexedDB file caching
│   │   ├── file-upload.service.ts     # Chunked upload logic
│   │   ├── input-sanitizer.service.ts # XSS/SQL/path traversal protection
│   │   ├── ocr.service.ts            # Document text extraction
│   │   └── translate.service.ts       # i18n translations
│   └── validators/
│       └── form-validators.ts         # Secure form validators + file constants
└── src/test-mocks/                    # Jest test mocking infrastructure
```

---

## API Reference

### Base URL: `http://localhost:8082/api`

### Complaints

| Method | Endpoint | Description | Parameters |
|--------|----------|-------------|------------|
| GET | `/complaints` | List all complaints | `?status=pending&search=query` |
| GET | `/complaints/paged` | Paginated list | `?page=0&size=20&status=&search=` |
| GET | `/complaints/{id}` | Get by ID | — |
| GET | `/complaints/track/{number}` | Track by complaint number | — |
| POST | `/complaints` | File new complaint | Body: `FileComplaintRequest` |
| PUT | `/complaints/{id}` | Update complaint | Body: `UpdateComplaintRequest` |
| DELETE | `/complaints/{id}` | Delete complaint | — |
| GET | `/complaints/{id}/timeline` | Get audit timeline | — |

#### FileComplaintRequest (POST Body)
```json
{
  "complainantName": "John Doe",
  "complainantEmail": "john@example.com",
  "complainantPhone": "9876543210",
  "complainantAddress": "123 Main St, Mumbai",
  "bankId": 1,
  "bankBranch": "Main Branch",
  "accountNumber": "1234567890",
  "categoryId": 1,
  "subject": "ATM transaction failed",
  "description": "Money debited but not dispensed",
  "reliefSought": "Refund of debited amount",
  "priority": "high",
  "filingType": "online",
  "bankComplaintReference": "REF-123",
  "bankComplaintDate": "2026-01-15"
}
```

#### UpdateComplaintRequest (PUT Body)
```json
{
  "status": "resolved",
  "priority": "high",
  "assignedOfficer": "Officer Smith",
  "remarks": "Issue resolved after investigation"
}
```

### Dashboard

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/dashboard` | Get complaint statistics |

#### Response
```json
{
  "totalComplaints": 100,
  "pendingComplaints": 30,
  "inProgressComplaints": 20,
  "resolvedComplaints": 25,
  "closedComplaints": 15,
  "escalatedComplaints": 10,
  "highPriority": 20,
  "mediumPriority": 50,
  "lowPriority": 30
}
```

### File Upload

| Method | Endpoint | Description | Parameters |
|--------|----------|-------------|------------|
| POST | `/files/upload/chunk` | Upload file chunk | multipart: `file`, params: `uploadId, chunkIndex, totalChunks, fileName, complaintNumber, complaintId, totalFileSize` |
| POST | `/files/upload` | Single file upload | multipart: `file`, params: `complaintNumber, complaintId` |
| GET | `/files/download/{id}` | Download attachment | Returns file with Content-Disposition |
| GET | `/files/stream/{id}` | Stream media (Range support) | `Range: bytes=0-1023` header |
| GET | `/files/complaint/{id}` | List attachments for complaint | — |
| DELETE | `/files/{id}` | Delete attachment | — |
| POST | `/files/cleanup` | Trigger stale upload cleanup | — |

#### ChunkUploadResponse
```json
{
  "uploadId": "uuid-string",
  "chunkIndex": 2,
  "totalChunks": 5,
  "complete": true,
  "attachmentId": 10,
  "fileName": "document.pdf",
  "storagePath": "CMS-20260515-ABC123/uuid_document.pdf",
  "message": "Upload complete"
}
```

### Banks

| Method | Endpoint | Description | Parameters |
|--------|----------|-------------|------------|
| GET | `/banks` | List all active banks | `?type=public` or `?type=private` |

### Categories

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/categories` | All active categories |
| GET | `/categories/root` | Root (top-level) categories |
| GET | `/categories/{parentId}/sub` | Sub-categories under parent |

### Form Configuration

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/form-config/{formKey}` | Get form JSON schema |
| PUT | `/form-config/{formKey}` | Update form schema (JSON body) |

### Email Simulation

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/email-simulation/receive` | Simulate incoming email |
| POST | `/email-simulation/reply-with-form` | Submit form reply |
| GET | `/email-simulation/threads` | List all email threads |
| GET | `/email-simulation/threads/{threadId}` | Get single thread |
| GET | `/email-simulation/inbox` | Get inbound emails |
| GET | `/email-simulation/sent` | Get outbound emails |
| GET | `/email-simulation/form-template/{number}` | Get form template for complaint |
| GET | `/email-simulation/stats` | Email statistics |

---

## Security & Validation

### Backend Security

| Layer | Mechanism | Description |
|-------|-----------|-------------|
| Network | CORS Filter | Only allows `localhost:4200-4202` origins |
| Transport | Rate Limiting | Bucket4j: 100 req/sec + 3000 req/min per IP |
| Input | GlobalExceptionHandler | Catches all exceptions, returns structured errors |
| Input | File type validation | Allowlist: `pdf,png,jpg,jpeg,doc,docx,xls,xlsx,txt,csv,zip,mp4,webm,ogg` |
| Input | File size validation | Max 50MB per file, max 10 files per complaint |
| Storage | File name sanitization | Strips all non-alphanumeric chars except `._-` |
| Storage | UUID file naming | Stored files get UUID prefix to prevent collisions |
| Data | JPA parameterized queries | Prevents SQL injection |
| Data | Transactional operations | `@Transactional` on all service methods |

### Frontend Security

| Layer | Mechanism | Description |
|-------|-----------|-------------|
| HTTP | Security Interceptor | Adds CSRF token, `X-Requested-With`, `X-Content-Type-Options` |
| HTTP | Rate Limit Interceptor | Client-side throttling per endpoint (5 complaints/min, 20 uploads/min) |
| Input | InputSanitizerService | XSS stripping, SQL injection detection, path traversal detection |
| Input | SecureValidators | Form validation with XSS/SQL checks on every field |
| Input | File magic byte validation | Validates file headers match declared type |
| Input | Double extension detection | Blocks `file.exe.pdf` tricks |
| Input | Complaint number sanitization | Only `[a-zA-Z0-9-]`, max 30 chars |

### Validation Rules (Form Validators)

| Field | Rule | Max Length |
|-------|------|-----------|
| Name | Unicode letters, spaces, `.',-` only | 100 |
| Email | RFC 5322 format, local ≤64, domain ≤253 | 254 |
| Phone | Indian mobile: starts 6-9, exactly 10 digits | 10 |
| Pincode | 6 digits, cannot start with 0 | 6 |
| Account Number | Alphanumeric, 6-20 chars | 20 |
| Amount | Up to 12 digits + 2 decimals, no negatives | 15 |
| Description | Max 5000 chars, XSS checked | 5000 |
| Subject | Max 200 chars | 200 |
| Address | Max 500 chars | 500 |
| Complaint Number | Pattern: `XX-XXXXXXXX-XXXXXX` | 30 |

---

## Caching Strategy

| Cache Name | TTL | Evicted On | Purpose |
|-----------|-----|------------|---------|
| `dashboard` | 2 min | fileComplaint, updateComplaint | Dashboard stats |
| `categories` | 1 hour | — | Active categories list |
| `categories-root` | 1 hour | — | Root categories |
| `categories-sub` | 1 hour (keyed by parentId) | — | Sub-categories |
| `banks` | 1 hour | — | Active banks |
| `banks-by-type` | 1 hour (keyed by type) | — | Banks filtered by type |
| `form-config` | 6 hours (keyed by formKey) | updateSchema | Form JSON schemas |
| `email-stats` | 3 min | receiveEmail, receiveFormReply | Email thread statistics |

**Test/Dev**: Cache disabled (`spring.cache.type: none` or `simple`)
**Production**: Redis with JSON serialization

---

## File Upload System

### Chunked Upload Flow
```
Frontend                              Backend
   │                                     │
   │  POST /files/upload/chunk           │
   │  (chunk 0/N, uploadId=null)         │
   │────────────────────────────────────▶│
   │                                     │ Generate uploadId
   │                                     │ Save chunk to temp-chunks/{uploadId}/chunk_00000
   │  Response: {uploadId, complete:false}│
   │◀────────────────────────────────────│
   │                                     │
   │  POST /files/upload/chunk           │
   │  (chunk 1/N, uploadId=xxx)          │
   │────────────────────────────────────▶│
   │                                     │ Save chunk_00001
   │  ...repeat until last chunk...      │
   │                                     │
   │  POST /files/upload/chunk           │
   │  (chunk N-1/N, uploadId=xxx)        │
   │────────────────────────────────────▶│
   │                                     │ All N chunks received
   │                                     │ assembleChunks() via FileChannel
   │                                     │ computeChecksum(SHA-256)
   │                                     │ Save ComplaintAttachment to DB
   │                                     │ Cleanup temp chunks
   │  Response: {complete:true,          │
   │   attachmentId, storagePath}        │
   │◀────────────────────────────────────│
```

### File Storage Layout
```
{cms.attachments.root-path}/
├── temp-chunks/
│   ├── {uploadId-1}/
│   │   ├── chunk_00000
│   │   ├── chunk_00001
│   │   └── ...
│   └── {uploadId-2}/
│       └── ...
├── CMS-20260515-ABC123/
│   ├── uuid1_document.pdf
│   └── uuid2_photo.jpg
└── CMS-20260516-DEF456/
    └── uuid3_report.xlsx
```

---

## Configuration Guide

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_URL` | `jdbc:mysql://localhost:3306/cms_db` | Database JDBC URL |
| `DB_USERNAME` | `root` | Database username |
| `DB_PASSWORD` | `root` | Database password |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `CMS_ATTACHMENTS_PATH` | `C:/cms-attachments` | File storage root |
| `CMS_MAX_FILE_SIZE` | `52428800` (50MB) | Max file size in bytes |
| `CMS_CHUNK_SIZE` | `5242880` (5MB) | Chunk size for uploads |
| `CMS_ALLOWED_TYPES` | `pdf,png,jpg,...` | Comma-separated allowed extensions |
| `CMS_MAX_FILES` | `10` | Max files per complaint |
| `RATE_LIMIT_RPS` | `100` | API requests per second per IP |

### Profiles

| Profile | Database | Cache | Use Case |
|---------|----------|-------|----------|
| (default) | MySQL | Redis | Standard deployment |
| `dev` | H2 (in-memory) | Simple (in-memory) | Local development |
| `prod` | MySQL (SSL, 100 pool) | Redis (pooled) | Production |

### Running Locally

```bash
# Backend (dev profile with H2)
cd cms-backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Backend (default with MySQL + Redis)
cd cms-backend
mvn spring-boot:run

# Frontend
cd cms-frontend
ng serve --port 4200
```

---

## Test Coverage

### Backend (140 tests, JUnit 5 + Mockito)

| Test Class | Tests | Coverage Area |
|-----------|-------|---------------|
| `ComplaintServiceTest` | 35 | All CRUD, dashboard, categories, banks, timeline |
| `FileStorageServiceTest` | 17 | Chunk upload, single upload, delete, cleanup |
| `EmailSimulationServiceTest` | 17 | Email receive/reply, threads, stats, templates |
| `FormConfigServiceTest` | 6 | Schema get/save/update, invalid JSON handling |
| `ComplaintControllerTest` | 14 | All REST endpoints via MockMvc |
| `FileUploadControllerTest` | 11 | Upload/download/stream/delete endpoints |
| `EmailSimulationControllerTest` | 8 | All email simulation endpoints |
| `DashboardControllerTest` | 1 | Dashboard endpoint |
| `BankControllerTest` | 2 | Bank listing with type filter |
| `CategoryControllerTest` | 3 | Category tree endpoints |
| `FormConfigControllerTest` | 3 | Form schema CRUD endpoints |
| `FileStorageConfigTest` | 20 | Allowed types, paths, init, defaults |

### Frontend (16 test suites, 312 tests, Jest)

| Test File | Tests | Coverage Area |
|-----------|-------|---------------|
| `complaint-parser.service.spec.ts` | Entity, category, bank, state detection |
| `complaint-text-processor.service.spec.ts` | Language detection, Hindi→English |
| `translate.service.spec.ts` | Language switching, key translation |
| `accessibility.service.spec.ts` | Contrast, motion, font size |
| `rate-limit.interceptor.spec.ts` | GET passthrough, POST limiting, 429 |
| `security.interceptor.spec.ts` | CSRF injection, security headers |
| `cms.service.spec.ts` | All HTTP API calls |
| `file-upload.service.spec.ts` | Stream URL, cancel, clear |
| `input-sanitizer.service.spec.ts` | XSS, SQL, path traversal |
| `form-validators.spec.ts` | All secure validators |
| `translate.pipe.spec.ts` | Pipe transform |
| `ocr.service.spec.ts` | Observable interface |

### Running Tests

```bash
# Backend
cd cms-backend
mvn test

# Frontend
cd cms-frontend
npx jest --coverage
```

---

## Developer Modification Guide

### Adding a New Complaint Status

1. **Backend**: No enum — statuses are strings. Just use the new status in `UpdateComplaintRequest`:
   - Update `ComplaintService.updateComplaint()` if you need special timestamp handling (like `resolvedAt`)
   - Add timeline action mapping in the same method

2. **Frontend**: Update status dropdown options in the form schema or component

3. **Dashboard**: Add count method if you want it displayed:
   - Add field to `DashboardResponse.java`
   - Add `countByStatus("new_status")` call in `ComplaintService.getDashboard()`
   - Cache auto-evicts on complaint changes

### Adding a New API Endpoint

1. Create/modify **Controller** in `cms-backend/src/main/java/com/hrms/cms/controller/`
2. Add business logic in the corresponding **Service**
3. Create **DTO** if new request/response shape needed (`dto/` package)
4. Add **Repository** method if new query needed
5. Add **frontend service method** in `cms.service.ts`
6. Write **tests** for both layers

### Adding a New Entity / Table

1. Create entity class in `entity/` with JPA annotations:
   ```java
   @Entity @Table(name = "MY_TABLE")
   @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
   public class MyEntity {
       @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
       private Long id;
       // ... fields
       @PrePersist
       protected void onCreate() { /* set defaults */ }
   }
   ```
2. Create repository interface extending `JpaRepository<MyEntity, Long>`
3. Add seed data in `DataInitializer.run()` if needed
4. DDL is auto-managed (`ddl-auto: update` in dev)

### Adding a New File Type

1. **Backend**: Add extension to `cms.attachments.allowed-types` in `application.yml`
2. **Backend**: Add MIME type mapping in `FileStorageService.detectContentType()`
3. **Frontend**: Add to `ALLOWED_FILE_TYPES` and `ALLOWED_MIME_TYPES` in `form-validators.ts`
4. **Frontend**: Add magic bytes in `FILE_MAGIC_BYTES` if available

### Modifying Form Schema (No Code Change Needed)

The complaint form is **fully driven by JSON** stored in the `FORM_CONFIGS` table:
- Call `PUT /api/form-config/raise-complaint` with updated JSON
- Or modify `DataInitializer.seedComplaintForm()` for initial seeding
- Frontend automatically renders based on schema (field types, dependencies, validation)

### Adding a New Cache

1. Add `@Cacheable(value = "my-cache")` on service method
2. Add `@CacheEvict(value = "my-cache", allEntries = true)` on mutation methods
3. Register TTL in `RedisCacheConfig.java`:
   ```java
   cacheConfigs.put("my-cache", defaultConfig.entryTtl(Duration.ofMinutes(10)));
   ```

### Adding a New Language (Frontend)

1. Add translations in `translate.service.ts` translations map
2. Add language option in the language switcher component
3. The `TranslatePipe` and service handle rendering automatically

### Modifying Rate Limits

- **Backend**: Change `cms.rate-limit.api-requests-per-second` in `application.yml`
- **Frontend**: Modify `RATE_LIMITS` map in `rate-limit.interceptor.ts`

### Database Schema Changes

- **Dev/Default**: `ddl-auto: update` — JPA auto-applies schema changes
- **Production**: `ddl-auto: validate` — You MUST create migration scripts manually
- Always add `@Index` annotations for query performance

### Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| String-based statuses (not enum) | Flexibility to add new statuses without code changes |
| Chunked upload with FileChannel | Memory-efficient large file handling |
| UUID-prefixed file names | Prevent filename collisions across uploads |
| Client-side + server-side rate limiting | Defense in depth — client reduces load, server enforces |
| Redis cache with per-cache TTL | Dashboard needs freshness (2min), reference data can be stale (1hr) |
| `@Async` for timeline writes | Non-blocking — complaint response returns before timeline is written |
| JSON-driven forms | Non-developers can modify form fields without deployment |
| Standalone Angular components | Better tree-shaking, no NgModule boilerplate |
| Functional interceptors (HttpInterceptorFn) | Modern Angular pattern, simpler than class-based |

---

## Error Response Format

All errors return a consistent structure:

```json
{
  "timestamp": "2026-05-17T12:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Complaint not found"
}
```

| Status | When |
|--------|------|
| 400 | RuntimeException (validation failures, not found) |
| 400 | MethodArgumentNotValidException (bean validation) |
| 413 | MaxUploadSizeExceededException |
| 429 | Rate limit exceeded |
| 500 | Unexpected/unhandled exceptions |
