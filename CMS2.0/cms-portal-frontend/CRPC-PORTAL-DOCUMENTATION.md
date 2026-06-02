# CRPC Portal Frontend - Architecture & Flow Documentation

**Version:** 2.0  
**Date:** June 2026  
**Platform:** Angular 20 (Standalone Components, Signals)  
**Module:** Complaint Redressal Processing Centre (CRPC)

---

## Table of Contents

1. [Overview](#1-overview)
2. [Architecture](#2-architecture)
3. [Module Structure](#3-module-structure)
4. [User Roles & Access](#4-user-roles--access)
5. [Functional Flows](#5-functional-flows)
6. [Component Reference](#6-component-reference)
7. [Services](#7-services)
8. [Models](#8-models)
9. [Routes](#9-routes)
10. [Key Features](#10-key-features)
11. [Run Instructions](#11-run-instructions)

---

## 1. Overview

The CMS 2.0 Portal Frontend is a single-page application (SPA) built with Angular 20, implementing the RBI Integrated Ombudsman Scheme's CRPC workflows. It covers the complete complaint lifecycle from ingestion (email/physical letter/portal) through assessment, review, and resolution.

### Key Modules

| Module | Purpose |
|--------|---------|
| **Public Portal** | Complaint filing, tracking, withdrawal, feedback, appeal |
| **Email Syndication (Admin)** | Email queue monitoring, DEO/Reviewer management, ignore list |
| **CRPC (DEO)** | Draft assessment, maintainability check, screening, routing to reviewer |
| **CRPC (Reviewer)** | Review drafts, approve/reject, generate complaint numbers |
| **Admin** | Rules management, dashboard, system configuration |
| **Officer** | Complaint action, resolution |

---

## 2. Architecture

### 2.1 High-Level Architecture

```
+---------------------------+
|     Angular 20 SPA        |
|  (Standalone Components)  |
|  (Signals + Computed)     |
+---------------------------+
           |
           | HTTP (REST)
           v
+---------------------------+
|   Spring Boot Backend     |
|   (API Gateway :8080)     |
+---------------------------+
           |
    +------+------+
    |             |
    v             v
+--------+  +----------+
| Oracle |  |  Kafka   |
|  DB    |  | (Events) |
+--------+  +----------+
```

### 2.2 Frontend Architecture Patterns

| Pattern | Implementation |
|---------|---------------|
| **State Management** | Angular Signals + Computed Signals |
| **Component Architecture** | Standalone Components (no NgModules) |
| **Control Flow** | @if, @for, @switch (Angular 17+ syntax) |
| **Lazy Loading** | Route-level lazy loading via `loadComponent()` |
| **Reactivity** | Signal-based with immutable updates |
| **PDF Generation** | jsPDF with AcroForm (editable fields) |
| **Session Management** | sessionStorage (role-based routing) |
| **Mock Data** | Client-side setTimeout simulation (backend-ready) |

### 2.3 Technology Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| Angular | 20.x | SPA framework |
| TypeScript | 5.x | Language |
| Angular Signals | Built-in | Reactive state |
| RxJS | 7.x | Async operations, HTTP |
| jsPDF | Latest | PDF generation (acknowledgement, editable forms) |
| PrimeIcons | Latest | Icon library |
| SCSS | - | Styling |

---

## 3. Module Structure

```
cms-portal-frontend/
├── src/app/
│   ├── app.routes.ts                    # All route definitions
│   ├── app.component.ts                 # Root component
│   ├── components/
│   │   ├── landing/                     # Main dashboard (entry point)
│   │   ├── layout/                      # Shared layout wrapper
│   │   ├── crpc/                        # CRPC Module
│   │   │   ├── crpc-login/             # Role-based login (all 6 roles)
│   │   │   ├── deo-home/              # DEO grid view (assigned drafts)
│   │   │   ├── draft-assessment/       # DEO 5-tab assessment view
│   │   │   ├── physical-letter/        # Physical letter wizard (4 steps)
│   │   │   ├── reviewer-home/          # Reviewer queue
│   │   │   ├── reviewer-assessment/    # Reviewer 4-module review + action
│   │   │   └── reviewer-management/    # Admin: manage reviewer pool
│   │   ├── email-syndication/           # Email Admin Module
│   │   │   ├── email-queue/            # Queue dashboard
│   │   │   ├── draft-detail/           # Draft view (assign to DEO)
│   │   │   ├── email-simulator/        # Simulate incoming emails
│   │   │   ├── ignore-list/            # Manage ignore patterns
│   │   │   └── deo-management/         # Admin: manage DEO pool
│   │   ├── public/                      # Public-facing Portal
│   │   │   ├── public-layout/          # Public portal wrapper
│   │   │   ├── public-home/            # Public landing page
│   │   │   ├── file-complaint/         # Complaint filing form
│   │   │   ├── withdraw-complaint/     # Complaint withdrawal
│   │   │   ├── submit-feedback/        # Feedback submission
│   │   │   └── file-appeal/            # Appeal filing
│   │   ├── admin/                       # System Admin Module
│   │   │   ├── admin-dashboard/        # Admin overview
│   │   │   ├── rules-management/       # Drools rule management
│   │   │   ├── rule-editor/            # Create/edit rules
│   │   │   └── rule-tester/            # Test rules
│   │   ├── officer/                     # Officer Module
│   │   │   ├── officer-dashboard/      # Complaint action dashboard
│   │   │   └── complaint-action/       # Take action on complaint
│   │   ├── complaint-form/              # Legacy complaint form
│   │   ├── complaint-tracker/           # Track complaint status
│   │   ├── search/                      # Full-text search (OpenSearch)
│   │   └── eligibility-questionnaire/   # Pre-filing eligibility check
│   ├── services/
│   │   ├── crpc.service.ts             # Reviewer CRUD (signals-based mock)
│   │   ├── email-syndication.service.ts # Email queue, DEO management
│   │   ├── complaint.service.ts         # Complaint CRUD
│   │   ├── admin.service.ts             # Admin operations
│   │   ├── officer.service.ts           # Officer operations
│   │   ├── eligibility.service.ts       # Eligibility checks
│   │   ├── rules.service.ts             # Rules management
│   │   ├── search.service.ts            # OpenSearch queries
│   │   └── session.service.ts           # Session management
│   └── models/
│       ├── crpc.model.ts               # ReviewerUser interface
│       ├── email-syndication.model.ts   # EmailDraft, DeoUser, etc.
│       ├── complaint.model.ts           # Complaint interfaces
│       ├── eligibility.model.ts         # Eligibility interfaces
│       └── api-response.model.ts        # Generic API response wrapper
```

---

## 4. User Roles & Access

### 4.1 CRPC Roles (BRD-defined)

| Role | Login Credentials | Home Route | Responsibilities |
|------|------------------|------------|-----------------|
| **DEO** | DEO001/deo123, DEO002/deo123, DEO003/deo123 | `/crpc/home` | Assess drafts, maintainability check, screening, route to reviewer |
| **Reviewer** | REV001/rev123, REV002/rev123 | `/crpc/reviewer` | Review assessments, approve/reject, generate complaint numbers |
| **CRPC Head** | HEAD001/head123 | `/crpc/home` | Oversight, escalation |
| **Admin** | ADMIN001/admin123 | `/crpc/home` | System configuration |
| **In-Charge** | IC001/ic123 | `/crpc/home` | Regional oversight |
| **Help Desk** | HD001/hd123 | `/crpc/home` | Public queries |

### 4.2 Admin Access

| Page | URL | Who Uses It |
|------|-----|-------------|
| Email Syndication Dashboard | `/email-syndication` | CRPC Admin |
| DEO Management | `/email-syndication/deo-management` | CRPC Admin |
| Reviewer Management | `/crpc/reviewer-management` | CRPC Admin |
| Email Simulator | `/email-syndication/simulator` | Testing |

### 4.3 Session Management

- Login stores `crpc_user` in `sessionStorage` with `{id, name, role, username, loginTime}`
- DEO/Reviewer home pages read session and display user bar with name, role, logout
- Logout clears session and redirects to `/crpc/login`

---

## 5. Functional Flows

### 5.1 Email-Originated Complaint Flow

```
[Incoming Email to crpc@rbi.org.in]
        |
        v
[Email Syndication Service - Auto Ingest]
        |
        v
[Email Queue (/email-syndication)]
  - OCR processed
  - Duplicate detection
  - Round-robin assignment to DEO
        |
        v
[Admin Assigns/Reassigns to DEO]
        |
        v
[DEO Home (/crpc/home)]
  - Draft appears in grid
  - Status: DRAFT
        |
        v
[DEO Opens Draft (/crpc/draft/:id)]
  - 5 Tabs: Summary | Attachments | Assessment | Screening | Route
  - Verify/edit complainant details
  - Upload additional attachments
  - Request additional info (email with editable PDF form)
  - Maintainability assessment (weighted questionnaire)
  - Auto-closure screening (sequential YES/NO)
  - Select decision: MAINTAINABLE or NON_MAINTAINABLE
  - Route to Reviewer (round-robin suggested)
        |
        v
[Sent for Approval]
        |
        v
[Reviewer Queue (/crpc/reviewer)]
  - Draft appears with DEO's decision
        |
        v
[Reviewer Opens Draft (/crpc/reviewer/draft/:id)]
  - 4 Modules (read-only, toggleable edit): Summary | Email | Attachments | History
  - Reviewer Action tab
        |
        +---> [APPROVE] --> Complaint Number Generated --> Routes to Regional Office
        |
        +---> [SEND BACK TO DEO] --> Returns to DEO queue with remarks
        |
        +---> [NOT A COMPLAINT] --> Closed (Disposition: Closed/Sent to Other Dept/Suggestion)
```

### 5.2 Physical Letter Flow

```
[Physical Letter Received at CRPC Office]
        |
        v
[DEO clicks "Create Complaint (Physical Letter)"]
        |
        v
[4-Step Wizard (/crpc/physical-letter)]
  Step 1: Scan/Upload (PDF/JPEG/PNG/TIFF, max 10MB)
  Step 2: OCR → Pre-fills complainant details (Name, Address, State, District, Phone)
  Step 3: Complaint details (Category, Entity, Subject, Amount, Dates)
  Step 4: Auto-Closure Screening Questions
        |
        v
[Draft Created (DRF-YYYYMMDD-XXXXXX)]
  - Scanned file auto-attached in Attachments module
  - All wizard data persisted via sessionStorage
        |
        v
[Auto-navigates to /crpc/draft/:id]
  - Same 5-tab assessment view as email drafts
  - Pre-filled with OCR + manual entry from wizard
  - DEO completes assessment → Sent for Approval
        |
        v
[Same Reviewer flow as email complaints]
```

### 5.3 Request Additional Information Flow

```
[DEO in Draft Assessment → Email Tab]
        |
        v
[Click "Request Additional Info"]
        |
        v
[Email Composer Opens]
  - To: pre-filled from complainant email
  - Subject: auto-generated with draft reference
  - Body: DEO types message
  - OPTION: Attach editable PDF form
    - Select fields to include (Name, Phone, Account No, Transaction ID, Amount, etc.)
    - Fields pre-filled from existing draft data
    - PDF uses AcroForm TextField (genuinely editable in Adobe/Chrome)
    - Complainant fills/corrects → saves → emails back
        |
        v
[Send Email]
  - Correspondence logged in Email thread
  - PDF attachment noted in email body
```

### 5.4 Reviewer Decision Flow

```
[Reviewer opens draft from queue]
        |
        v
[4 Modules visible (BRD requirement)]
  1. Summary (read-only, edit toggle)
  2. Email Communication (thread view)
  3. Attachments (view/download)
  4. History/Audit trail
        |
        v
[Reviewer Action Tab]
  |
  +---> APPROVE
  |       - Generates complaint number (CMP-YYYYMMDD-XXXXXX)
  |       - Routes to regional office
  |       - Saved comment template applied
  |
  +---> SEND BACK TO DEO
  |       - Reviewer remarks (mandatory)
  |       - Reassigns to same/different DEO
  |       - Draft returns to DEO queue as REJECTED_BY_REVIEWER
  |
  +---> NOT A COMPLAINT
          - Disposition: Closed / Sent to Other Dept / Suggestion
          - Closure reason selected
          - Saved comment template applied
```

### 5.5 Public Portal Flow

```
[Citizen visits /public]
        |
        +---> [File Complaint (/public/file-complaint)]
        |       - Eligibility questionnaire
        |       - Complaint form (multi-step)
        |       - Acknowledgement PDF download (jsPDF)
        |
        +---> [Track Complaint (/public/track)]
        |       - Enter complaint number → view status
        |
        +---> [Withdraw Complaint (/public/withdraw)]
        |       - Enter complaint number → submit withdrawal
        |
        +---> [Submit Feedback (/public/feedback)]
        |       - Rate experience, comments
        |
        +---> [File Appeal (/public/appeal)]
                - Against closed complaint decision
```

---

## 6. Component Reference

### 6.1 CRPC Components

| Component | Route | Purpose |
|-----------|-------|---------|
| `CrpcLoginComponent` | `/crpc/login` | Multi-role login page |
| `DeoHomeComponent` | `/crpc/home` | DEO dashboard with grid, stats, filters |
| `DraftAssessmentComponent` | `/crpc/draft/:id` | 5-tab DEO assessment view |
| `PhysicalLetterComponent` | `/crpc/physical-letter` | 4-step physical letter wizard |
| `ReviewerHomeComponent` | `/crpc/reviewer` | Reviewer queue with filters |
| `ReviewerAssessmentComponent` | `/crpc/reviewer/draft/:id` | Reviewer 4-module view + action |
| `ReviewerManagementComponent` | `/crpc/reviewer-management` | Admin: reviewer pool management |

### 6.2 Email Syndication Components

| Component | Route | Purpose |
|-----------|-------|---------|
| `EmailQueueComponent` | `/email-syndication` | Admin queue dashboard |
| `DraftDetailComponent` | `/email-syndication/draft/:draftId` | Assign/reassign to DEO |
| `EmailSimulatorComponent` | `/email-syndication/simulator` | Test email ingestion |
| `IgnoreListComponent` | `/email-syndication/ignore-list` | Manage blocked patterns |
| `DeoManagementComponent` | `/email-syndication/deo-management` | DEO pool management |

### 6.3 Public Portal Components

| Component | Route | Purpose |
|-----------|-------|---------|
| `PublicLayoutComponent` | `/public` | Layout wrapper |
| `PublicHomeComponent` | `/public` | Public landing |
| `PublicFileComplaintComponent` | `/public/file-complaint` | Filing form |
| `ComplaintTrackerComponent` | `/public/track` | Status lookup |
| `WithdrawComplaintComponent` | `/public/withdraw` | Withdrawal |
| `SubmitFeedbackComponent` | `/public/feedback` | Feedback |
| `FileAppealComponent` | `/public/appeal` | Appeal |

---

## 7. Services

| Service | File | Purpose |
|---------|------|---------|
| `CrpcService` | `services/crpc.service.ts` | Reviewer CRUD (add, toggle active/leave, update threshold, remove, round-robin reset). Uses signals internally for mock data. |
| `EmailSyndicationService` | `services/email-syndication.service.ts` | HTTP calls for email queue, drafts, ignore list, DEO management (getDeos, addDeo, removeDeo, updateThreshold, resetRoundRobin). |
| `ComplaintService` | `services/complaint.service.ts` | Complaint CRUD, status updates |
| `EligibilityService` | `services/eligibility.service.ts` | Pre-filing eligibility check |
| `AdminService` | `services/admin.service.ts` | Admin dashboard data |
| `RulesService` | `services/rules.service.ts` | Drools rule CRUD |
| `OfficerService` | `services/officer.service.ts` | Officer actions |
| `SearchService` | `services/search.service.ts` | Full-text search via OpenSearch |
| `SessionService` | `services/session.service.ts` | Session management |

---

## 8. Models

### 8.1 ReviewerUser (`models/crpc.model.ts`)

```typescript
interface ReviewerUser {
  id: string;
  displayName: string;
  email: string;
  isActive: boolean;
  isOnLeave: boolean;
  maxLoad: number;
  currentLoad: number;
  region: string;
  sortOrder: number;
}
```

### 8.2 DeoUser (`models/email-syndication.model.ts`)

```typescript
interface DeoUser {
  id: number;
  userId: string;
  displayName: string;
  email: string;
  isActive: boolean;
  isOnLeave: boolean;
  maxThreshold: number;
  currentAssignedCount: number;
  sortOrder: number;
}
```

### 8.3 EmailDraft (`models/email-syndication.model.ts`)

```typescript
interface EmailDraft {
  id: number;
  draftId: string;
  messageId: string;
  senderEmail: string;
  subject: string;
  body: string;
  complainantName: string;
  complainantPhone: string;
  category: string;
  modeOfReceipt: string;
  status: EmailDraftStatus;
  assignedTo: string;
  ocrProcessed: boolean;
  receivedAt: string;
}
```

---

## 9. Routes

```typescript
// Landing
''                              → LandingComponent

// Email Syndication (Admin)
'email-syndication'             → EmailQueueComponent
'email-syndication/draft/:id'   → DraftDetailComponent
'email-syndication/ignore-list' → IgnoreListComponent
'email-syndication/deo-management' → DeoManagementComponent
'email-syndication/simulator'   → EmailSimulatorComponent

// CRPC (DEO + Reviewer)
'crpc/login'                    → CrpcLoginComponent
'crpc/home'                     → DeoHomeComponent
'crpc/physical-letter'          → PhysicalLetterComponent
'crpc/draft/:id'                → DraftAssessmentComponent
'crpc/reviewer-management'      → ReviewerManagementComponent
'crpc/reviewer'                 → ReviewerHomeComponent
'crpc/reviewer/draft/:id'       → ReviewerAssessmentComponent

// Admin
'admin/dashboard'               → AdminDashboardComponent
'admin/rules'                   → RulesManagementComponent
'admin/rules/new'               → RuleEditorComponent
'admin/rules/edit/:id'          → RuleEditorComponent
'admin/rules/test'              → RuleTesterComponent

// Officer
'officer'                       → OfficerDashboardComponent
'officer/complaint/:id'         → ComplaintActionComponent

// Public Portal
'public'                        → PublicLayoutComponent (children below)
'public/file-complaint'         → PublicFileComplaintComponent
'public/track'                  → ComplaintTrackerComponent
'public/withdraw'               → WithdrawComplaintComponent
'public/feedback'               → SubmitFeedbackComponent
'public/appeal'                 → FileAppealComponent
```

---

## 10. Key Features

### 10.1 DEO Assessment (5 Tabs)

| Tab | Features |
|-----|----------|
| **Summary** | Editable complainant fields, complaint details, mode of receipt, vernacular flag |
| **Attachments** | Upload (PDF/JPEG/PNG/TIFF/EML, 10MB max), view, remove, auto-attached scanned letters |
| **Assessment** | 8 weighted maintainability questions (YES/NO/NA), computed score (0-100%) |
| **Screening** | 8 sequential auto-closure questions, triggers closure recommendation on YES |
| **Route** | Decision (Maintainable/Non-Maintainable), NM reason, closure tags, reviewer selection (round-robin), saved comment templates, validation summary |

### 10.2 Request Additional Info (Editable PDF)

- Email composer with "Attach editable PDF form" option
- DEO selects fields to include (14 available: Name, Phone, Email, Address, State, District, Pincode, Account Number, Transaction ID, Date, Amount, Branch, Description, Supporting Docs)
- PDF generated with **AcroForm TextFields** (genuinely editable in any PDF viewer)
- Fields **pre-filled** with existing draft data
- Complainant opens PDF → edits/completes → saves → emails back

### 10.3 Auto-Closure Screening

- Sequential YES/NO questions (signal-based with immutable updates)
- Any YES triggers auto-closure recommendation with specific clause
- Closure clauses: TIME_BARRED, SUB_JUDICE, ALREADY_SETTLED, NOT_APPROACHED_RE, ANONYMOUS, INSUFFICIENT_DETAILS, OUT_OF_JURISDICTION, FRIVOLOUS

### 10.4 Reviewer Actions

| Action | Result |
|--------|--------|
| **Approve** | Complaint number generated (CMP-YYYYMMDD-XXXXXX), routed to regional office |
| **Send Back to DEO** | Returns to DEO queue, reviewer remarks mandatory |
| **Not a Complaint** | Closed with disposition (Closed/Sent to Other Dept/Suggestion) |

### 10.5 Round-Robin Assignment

- Reviewers sorted by `currentLoad` (ascending)
- Least-loaded active reviewer suggested automatically
- DEO can override and select any active reviewer
- Admin can reset round-robin pointer

### 10.6 Mandatory Field Validation

Before "Sent for Approval", system validates:
- Complainant Name
- State
- District
- Phone OR Email
- Category
- Entity Name
- Subject
- DEO Decision selected
- Reviewer selected
- If Maintainable: all screening questions answered
- If Non-Maintainable: NM reason selected

### 10.7 PDF Acknowledgement (Public Portal)

- Generated client-side using jsPDF
- Includes: RBI header, reference number, filing date, complainant details, bank info, category, amount, tracking info, contact details
- Downloaded as formatted PDF (not .txt)

---

## 11. Run Instructions

### Prerequisites
- Node.js 18+
- npm 9+

### Start Development Server

```bash
cd CMS2.0/cms-portal-frontend
npm install
ng serve
```

Application runs at: `http://localhost:4200`

### Key Entry Points

| URL | Purpose |
|-----|---------|
| `http://localhost:4200` | Landing dashboard |
| `http://localhost:4200/crpc/login` | CRPC staff login |
| `http://localhost:4200/email-syndication` | Admin email queue |
| `http://localhost:4200/public` | Public complaint portal |
| `http://localhost:4200/crpc/reviewer-management` | Reviewer pool management |

### Build for Production

```bash
ng build --configuration production
```

Output: `dist/cms-portal-frontend/`

---

## Appendix: Login Credentials (Development)

| User ID | Password | Role | Name |
|---------|----------|------|------|
| DEO001 | deo123 | DEO | Arjun Mehta |
| DEO002 | deo123 | DEO | Sneha Iyer |
| DEO003 | deo123 | DEO | Ravi Verma |
| REV001 | rev123 | Reviewer | Sunil Rao |
| REV002 | rev123 | Reviewer | Kavita Nair |
| HEAD001 | head123 | CRPC Head | Dr. V.K. Bansal |
| ADMIN001 | admin123 | Admin | System Admin |
| IC001 | ic123 | In-Charge | Regional IC |
| HD001 | hd123 | Help Desk | Help Desk |
