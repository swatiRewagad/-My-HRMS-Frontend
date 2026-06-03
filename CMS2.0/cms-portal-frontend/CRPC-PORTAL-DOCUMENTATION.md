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

---

## 12. Developer Guidance

### 12.1 Adding a New Component

```bash
# 1. Create the component directory
mkdir src/app/components/crpc/new-component

# 2. Create files: new-component.component.ts, .html, .scss
# 3. Follow this template:
```

```typescript
import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

@Component({
  selector: 'app-new-component',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './new-component.component.html',
  styleUrl: './new-component.component.scss'
})
export class NewComponent implements OnInit {
  private router = inject(Router);

  // Use signals for reactive state
  data = signal<any[]>([]);
  loading = signal(false);

  // Use computed for derived state
  filteredData = computed(() => this.data().filter(d => d.active));

  ngOnInit() {
    this.loadData();
  }

  loadData() {
    this.loading.set(true);
    // Service call here
    this.loading.set(false);
  }
}
```

```bash
# 4. Add lazy-loaded route in app.routes.ts:
{
  path: 'crpc/new-page',
  loadComponent: () => import('./components/crpc/new-component/new-component.component')
    .then(m => m.NewComponent)
}
```

### 12.2 Adding a New Service

```typescript
// src/app/services/new.service.ts
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { ApiResponse } from '../models/api-response.model';

@Injectable({ providedIn: 'root' })
export class NewService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiBaseUrl}/api/v1/new-endpoint`;

  getItems(): Observable<Item[]> {
    return this.http.get<ApiResponse<Item[]>>(this.baseUrl)
      .pipe(map(res => res.data));
  }
}
```

### 12.3 Signal Best Practices

**DO:**
```typescript
// Immutable updates for arrays (triggers computed re-evaluation)
this.items.set([...this.items(), newItem]);
this.items.set(this.items().map(i => i.id === id ? { ...i, updated: true } : i));
this.items.set(this.items().filter(i => i.id !== id));

// Use computed for derived state
total = computed(() => this.items().length);
activeItems = computed(() => this.items().filter(i => i.active));
```

**DON'T:**
```typescript
// NEVER mutate arrays in place (computed won't detect change)
this.items().push(newItem);        // BAD - no reactivity
this.items()[0].name = 'new';      // BAD - no reactivity

// NEVER use plain arrays for reactive data that computed signals depend on
maintainabilityQuestions = [...];    // BAD if used in computed()
// Instead use: maintainabilityQuestions = signal([...]);
```

### 12.4 Template Patterns (Angular 17+ Control Flow)

```html
<!-- Conditional rendering -->
@if (loading()) {
  <div class="spinner"></div>
} @else {
  <div class="content">...</div>
}

<!-- Loops with tracking -->
@for (item of items(); track item.id) {
  <div>{{ item.name }}</div>
} @empty {
  <p>No items found.</p>
}

<!-- Switch -->
@switch (status) {
  @case ('DRAFT') { <span class="badge-draft">Draft</span> }
  @case ('APPROVED') { <span class="badge-approved">Approved</span> }
  @default { <span>Unknown</span> }
}
```

### 12.5 Adding a New Role

1. Add user credentials in `crpc-login.component.ts` → `mockUsers` array
2. Add routing logic in the `switch(user.role)` block
3. Create role-specific home component if needed
4. Add session reading in the new component's `ngOnInit()`

### 12.6 Adding Fields to the Draft Assessment

1. Add property in `draft-assessment.component.ts`
2. Add to the HTML template in the appropriate tab
3. If mandatory, add validation in `mandatoryFieldsComplete` computed signal
4. If mandatory, add label in `missingMandatoryFields` computed signal
5. For physical letter flow, add field to sessionStorage save in `physical-letter.component.ts` → `submitDraft()`
6. Add field reading in `draft-assessment.component.ts` → `loadDraft()` physical letter branch

### 12.7 Adding a New Screening Question

```typescript
// In draft-assessment.component.ts → screeningQuestions signal:
{ id: 'SQ9', question: 'Your new question?', answer: null as ('YES' | 'NO' | null), closureClause: 'NEW_CLAUSE' }
```

The sequential screening, auto-closure detection, and validation will work automatically.

### 12.8 Adding a Comment Template

```typescript
// In draft-assessment.component.ts → commentTemplates array:
{ id: 'T8', label: 'Template Label', text: 'Full template text that will be applied to DEO remarks.' }
```

### 12.9 Adding PDF Form Fields

```typescript
// In draft-assessment.component.ts → formPdfFields array:
{ key: 'newField', label: 'Display Label', include: false }

// If pre-fill value exists, add to getFieldPrefillValue():
newField: this.someExistingProperty || '',
```

---

## 13. Tech Stack Deep Dive

### 13.1 Angular 20 Standalone Components

Every component uses `standalone: true` — no NgModule declarations needed. Components declare their own imports:

```typescript
@Component({
  standalone: true,
  imports: [CommonModule, FormsModule],  // Each component imports what it needs
})
```

**Why standalone:**
- Simpler mental model (no module dependency graph)
- Better tree-shaking (only imports what's used)
- Easier lazy loading (direct component reference in routes)

### 13.2 Angular Signals (Reactive State)

Signals replace traditional BehaviorSubject/Observable patterns for local component state:

| Concept | Usage | Example |
|---------|-------|---------|
| `signal()` | Mutable state | `loading = signal(false)` |
| `computed()` | Derived state (auto-updates) | `total = computed(() => this.items().length)` |
| `.set()` | Replace value | `this.loading.set(true)` |
| `.update()` | Transform value | `this.count.update(c => c + 1)` |
| `()` call | Read value (in template or code) | `{{ loading() }}` or `if (this.loading())` |

**Key gotcha:** `computed()` only detects changes when signal references change. For arrays, you must create a new array reference (`[...arr]` or `.map()`) — in-place `.push()` won't trigger.

### 13.3 jsPDF + AcroForm (Editable PDFs)

```typescript
// Lazy-load jsPDF (reduces initial bundle size)
const { jsPDF } = await import('jspdf');
const doc = new jsPDF();

// Create editable text field
const textField = new doc.AcroFormTextField();
textField.fieldName = 'uniqueFieldName';
textField.x = 14;           // X position (mm)
textField.y = 50;           // Y position (mm)
textField.width = 180;      // Field width (mm)
textField.height = 10;      // Field height (mm)
textField.fontSize = 10;
textField.value = 'Pre-filled value';      // Current value
textField.defaultValue = 'Pre-filled value'; // Reset value
textField.multiline = false;  // true for textarea-like fields
textField.readOnly = false;   // false = editable by user
doc.addField(textField);

doc.save('filename.pdf');
```

**Supported field types:** TextField, PasswordField, ComboBox, ListBox, RadioButton, PushButton, CheckBox

### 13.4 Session Management Pattern

```typescript
// Login (crpc-login.component.ts)
sessionStorage.setItem('crpc_user', JSON.stringify({
  id: user.id,
  name: user.name,
  role: user.role,
  username: user.username,
  loginTime: new Date().toISOString()
}));

// Read session (any component)
const stored = sessionStorage.getItem('crpc_user');
if (stored) {
  this.loggedInUser = JSON.parse(stored);
}

// Logout
sessionStorage.removeItem('crpc_user');
this.router.navigate(['/crpc/login']);
```

**Why sessionStorage (not localStorage):**
- Cleared on tab close (security requirement for bank systems)
- Per-tab isolation (multiple users can test in different tabs)

### 13.5 Route-Level Lazy Loading

```typescript
// Every route uses loadComponent() for code splitting
{
  path: 'crpc/home',
  loadComponent: () => import('./components/crpc/deo-home/deo-home.component')
    .then(m => m.DeoHomeComponent)
}
```

**Result:** Each page is a separate JS chunk, loaded only when navigated to. Initial bundle stays small.

### 13.6 SCSS Conventions

- Each component has its own `.scss` file (ViewEncapsulation.Emulated by default)
- Common patterns: `.btn`, `.btn-primary`, `.btn-outline`, `.btn-danger`, `.btn-sm`
- Grid layouts: CSS Grid (`grid-template-columns: repeat(auto-fill, minmax(320px, 1fr))`)
- Color scheme: `#1a237e` (primary navy), `#1976d2` (blue), `#4caf50` (green), `#f44336` (red), `#ff9800` (orange)
- Border radius: `6-8px` for cards, `4px` for inputs, `10-12px` for badges

### 13.7 Mock Data Pattern (Backend-Ready)

All services currently use mock data via `setTimeout` (simulating API latency). To connect to real backend:

```typescript
// Current (mock):
loadDrafts() {
  this.loading.set(true);
  setTimeout(() => {
    this.drafts.set(this.getMockDrafts());
    this.loading.set(false);
  }, 500);
}

// Production (real API):
loadDrafts() {
  this.loading.set(true);
  this.crpcService.getDrafts().subscribe({
    next: (data) => { this.drafts.set(data); this.loading.set(false); },
    error: () => this.loading.set(false)
  });
}
```

The `CrpcService` uses internal signals for mock state. Replace with `HttpClient` calls when backend is ready.

---

## 14. Troubleshooting & Known Issues

### 14.1 Common Build Errors

| Error | Cause | Fix |
|-------|-------|-----|
| `NG8001: 'app-xyz' is not a known element` | Component not imported | Add to parent's `imports` array |
| `NG8002: Can't bind to 'ngModel'` | FormsModule not imported | Add `FormsModule` to component's `imports` |
| `Property 'x' does not exist on type` | Signal called without `()` | Use `items()` not `items` in template |
| `Module 'jspdf' has no exported member` | Wrong import syntax | Use `const { jsPDF } = await import('jspdf')` |
| `CommonJS or AMD dependencies` (warning) | jsPDF/html2canvas are CJS | Safe to ignore — not an error |

### 14.2 Runtime Issues

| Issue | Cause | Fix |
|-------|-------|-----|
| Screening validation won't clear after answering all questions | `screeningQuestions` was plain array, computed can't detect mutations | Convert to `signal([...])`, use immutable `.map()` + `.set()` updates |
| Physical letter draft shows empty fields after wizard | `loadDraft()` was resetting fields to empty for physical letter drafts | Save wizard data to `sessionStorage`, read in `loadDraft()` |
| PDF downloads as .txt | Using Blob with wrong MIME type | Use jsPDF `doc.save()` which handles MIME correctly |
| PDF form not editable | Using `doc.text()` instead of AcroForm | Use `doc.AcroFormTextField()` with `readOnly: false` |
| Reviewer list empty in route tab | Hardcoded array replaced by signal but no load call | Add `this.crpcService.getReviewers().subscribe(...)` in `ngOnInit()` |

### 14.3 Signal Reactivity Issues

**Symptom:** UI doesn't update after changing data.

**Diagnosis checklist:**
1. Is the data stored in a `signal()`? (Plain properties won't trigger computed)
2. Are you using `.set()` with a **new** array/object reference?
3. In the template, are you calling the signal with `()` — e.g., `items()` not `items`?
4. For radio buttons / checkboxes tied to signal arrays, use `[checked]` + `(change)` pattern instead of `[(ngModel)]`:

```html
<!-- CORRECT for signal-backed arrays -->
<input type="radio" [checked]="q.answer === 'YES'" (change)="answerQuestion(q.id, 'YES')" />

<!-- WRONG (ngModel won't trigger signal update on array item) -->
<input type="radio" [(ngModel)]="q.answer" />
```

### 14.4 PDF Generation Issues

| Issue | Fix |
|-------|-----|
| Text overflows page | Check `y > 270` before adding content, call `doc.addPage()` |
| Unicode characters not rendering | jsPDF default fonts don't support all Unicode. Use `doc.addFont()` for custom fonts |
| AcroForm fields not visible in some viewers | Some viewers (old Chrome) don't render AcroForm. Test in Adobe Acrobat |
| Pre-fill values not showing | Ensure both `textField.value` AND `textField.defaultValue` are set |

### 14.5 Routing Issues

| Issue | Fix |
|-------|-----|
| Page shows blank after navigation | Check component is exported and route path matches exactly |
| Query params lost on navigation | Use `this.router.navigate(['/path'], { queryParams: {...} })` |
| ActivatedRoute params empty | Ensure route has `:param` placeholder. Use `snapshot.paramMap.get('param')` |
| Wildcard catch redirects everything | Ensure `'**'` route is LAST in the routes array |

---

## 15. Data Flow Diagrams

### 15.1 Email Ingestion → Complaint Resolution

```
                    ┌─────────────────────────────────────────────────────────────┐
                    │                    EMAIL FLOW                                │
                    └─────────────────────────────────────────────────────────────┘

  ┌──────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
  │ Incoming │────▶│ Email Syndic │────▶│  Admin Queue │────▶│ Assign DEO   │
  │ Email    │     │ (Auto-ingest)│     │ (Monitor)    │     │ (Round-Robin) │
  └──────────┘     └──────────────┘     └──────────────┘     └──────────────┘
                                                                      │
                         ┌────────────────────────────────────────────┘
                         │
                         ▼
  ┌──────────────────────────────────────────────────────────────────────┐
  │                        DEO WORKFLOW                                   │
  │                                                                      │
  │  ┌──────────┐  ┌────────────┐  ┌────────────┐  ┌──────────────┐   │
  │  │ Summary  │  │ Attachments│  │ Assessment │  │  Screening   │   │
  │  │ (Edit)   │  │ (Upload)   │  │ (8 Qs)     │  │ (Sequential) │   │
  │  └──────────┘  └────────────┘  └────────────┘  └──────────────┘   │
  │                                                                      │
  │  ┌──────────────────────────────────────────────────────────────┐   │
  │  │ Route Tab: Decision + Reviewer Selection + Send for Approval │   │
  │  └──────────────────────────────────────────────────────────────┘   │
  └──────────────────────────────────────────────────────────────────────┘
                         │
                         │ "Sent for Approval"
                         ▼
  ┌──────────────────────────────────────────────────────────────────────┐
  │                      REVIEWER WORKFLOW                                │
  │                                                                      │
  │  ┌──────────┐  ┌──────────┐  ┌────────────┐  ┌──────────────┐     │
  │  │ Summary  │  │ Email    │  │ Attachments│  │   History    │     │
  │  │(ReadOnly)│  │ (Thread) │  │ (View)     │  │ (Audit log)  │     │
  │  └──────────┘  └──────────┘  └────────────┘  └──────────────┘     │
  │                                                                      │
  │  ┌──────────────────────────────────────────────────────────────┐   │
  │  │ Action: APPROVE │ SEND BACK TO DEO │ NOT A COMPLAINT         │   │
  │  └──────────────────────────────────────────────────────────────┘   │
  └──────────────────────────────────────────────────────────────────────┘
                         │
              ┌──────────┼──────────┐
              │          │          │
              ▼          ▼          ▼
      ┌───────────┐ ┌────────┐ ┌────────────┐
      │ Complaint │ │  Back  │ │   Closed   │
      │ # Created │ │ to DEO │ │ (Disposed) │
      └───────────┘ └────────┘ └────────────┘
```

### 15.2 Physical Letter Flow

```
  ┌──────────────┐
  │ Physical     │
  │ Letter Rcvd  │
  └──────┬───────┘
         │
         ▼
  ┌──────────────────────────────────────────────┐
  │         PHYSICAL LETTER WIZARD               │
  │                                              │
  │  Step 1: Scan/Upload ──▶ Step 2: OCR/Fill   │
  │  (PDF/JPG/PNG/TIFF)     (Name, Addr, State) │
  │                                              │
  │  Step 3: Complaint ────▶ Step 4: Screening  │
  │  (Category, Entity,     (Auto-closure Qs)   │
  │   Subject, Amount)                           │
  └──────────────────┬───────────────────────────┘
                     │
                     │ Submit → Save to sessionStorage
                     │ → Generate DRF-YYYYMMDD-XXXXXX
                     │ → Auto-navigate to /crpc/draft/:id
                     ▼
  ┌──────────────────────────────────────────────┐
  │    SAME DEO 5-TAB VIEW (draft-assessment)    │
  │    - Fields pre-filled from wizard           │
  │    - Scanned letter in Attachments           │
  │    - Complete assessment → Sent for Approval │
  └──────────────────────────────────────────────┘
```

### 15.3 Admin Management Flow

```
  ┌─────────────────────────────────────────────────────┐
  │            ADMIN DASHBOARD (/email-syndication)      │
  │                                                     │
  │  ┌────────────┐  ┌──────────────┐  ┌───────────┐  │
  │  │ Email Queue│  │ Ignore List  │  │ Simulator │  │
  │  │ (Monitor)  │  │ (Block spam) │  │ (Test)    │  │
  │  └────────────┘  └──────────────┘  └───────────┘  │
  │                                                     │
  │  ┌──────────────────┐  ┌───────────────────────┐   │
  │  │ DEO Management   │  │ Reviewer Management   │   │
  │  │ - Add/Remove DEO │  │ - Add/Remove Reviewer │   │
  │  │ - Set threshold  │  │ - Set max load        │   │
  │  │ - Toggle active  │  │ - Toggle active/leave │   │
  │  │ - Mark on leave  │  │ - Region assignment   │   │
  │  │ - Reset RR ptr   │  │ - Reset RR pointer    │   │
  │  └──────────────────┘  └───────────────────────┘   │
  └─────────────────────────────────────────────────────┘
```

---

## 16. API Integration Guide (When Backend is Ready)

### 16.1 Environment Configuration

```typescript
// src/environments/environment.ts (development)
export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8080'
};

// src/environments/environment.prod.ts (production)
export const environment = {
  production: true,
  apiBaseUrl: 'https://cms-api.rbi.org.in'
};
```

### 16.2 Expected Backend APIs (CRPC Module)

| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/v1/crpc/drafts` | Get DEO's assigned drafts |
| GET | `/api/v1/crpc/drafts/:id` | Get draft details |
| PUT | `/api/v1/crpc/drafts/:id` | Update draft fields |
| POST | `/api/v1/crpc/drafts/:id/send-for-approval` | Route to reviewer |
| POST | `/api/v1/crpc/drafts/:id/request-info` | Send email with PDF form |
| POST | `/api/v1/crpc/physical-letter` | Create draft from physical letter |
| POST | `/api/v1/crpc/physical-letter/ocr` | Run OCR on uploaded file |
| GET | `/api/v1/crpc/reviewer/queue` | Get reviewer's queue |
| POST | `/api/v1/crpc/reviewer/drafts/:id/approve` | Approve and generate complaint# |
| POST | `/api/v1/crpc/reviewer/drafts/:id/send-back` | Send back to DEO |
| POST | `/api/v1/crpc/reviewer/drafts/:id/close` | Close as not-a-complaint |
| GET | `/api/v1/crpc/reviewers` | List all reviewers |
| POST | `/api/v1/crpc/reviewers` | Add reviewer |
| PUT | `/api/v1/crpc/reviewers/:id` | Update reviewer |
| DELETE | `/api/v1/crpc/reviewers/:id` | Remove reviewer |

### 16.3 API Response Format

All APIs should return:

```json
{
  "success": true,
  "data": { ... },
  "message": "Operation successful",
  "timestamp": "2026-06-02T10:30:00Z"
}
```

Error format:

```json
{
  "success": false,
  "data": null,
  "message": "Validation failed: Complainant name is required",
  "errorCode": "VALIDATION_ERROR",
  "timestamp": "2026-06-02T10:30:00Z"
}
```

### 16.4 OCR Integration (Backend)

When backend is ready, replace the simulated OCR:

```typescript
// Current (simulated)
runOcr() {
  setTimeout(() => { this.complainantName = 'Suresh Patel'; ... }, 2000);
}

// Production (real API)
runOcr() {
  const formData = new FormData();
  formData.append('file', this.scannedFile!);
  this.http.post('/api/v1/crpc/physical-letter/ocr', formData).subscribe({
    next: (res: any) => {
      this.complainantName = res.data.name || '';
      this.complainantAddress = res.data.address || '';
      this.complainantState = res.data.state || '';
      // ... map all extracted fields
    }
  });
}
```

Recommended OCR services: Azure Document Intelligence, Google Cloud Vision, AWS Textract

---

## 17. Security Considerations

| Area | Implementation | Notes |
|------|---------------|-------|
| **Authentication** | sessionStorage-based (dev), Keycloak JWT (prod) | Session clears on tab close |
| **Authorization** | Role-based routing after login | DEOs can't access reviewer pages |
| **File Upload** | Type whitelist (PDF/JPG/PNG/TIFF/EML), 10MB limit | Server should re-validate |
| **XSS Prevention** | Angular auto-escapes template bindings | Never use `innerHTML` with user data |
| **CSRF** | Not applicable (no cookies in API calls) | JWT in Authorization header |
| **Input Validation** | Mandatory fields checked before submission | Server must re-validate all inputs |
| **PDF Form** | Editable but not executable | No JavaScript in generated PDFs |
| **Data Sensitivity** | Complainant PII visible only to assigned DEO/Reviewer | Enforce at API level |

---

## 18. Deployment Notes

### 18.1 Production Build

```bash
ng build --configuration production
# Output: dist/cms-portal-frontend/browser/
```

### 18.2 Nginx Configuration (Recommended)

```nginx
server {
    listen 80;
    server_name cms-portal.rbi.org.in;
    root /var/www/cms-portal-frontend/browser;
    index index.html;

    # SPA routing — all paths serve index.html
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API proxy to backend gateway
    location /api/ {
        proxy_pass http://cms-api-gateway:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # Cache static assets aggressively
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff2)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
}
```

### 18.3 Docker

```dockerfile
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npx ng build --configuration production

FROM nginx:alpine
COPY --from=build /app/dist/cms-portal-frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

### 18.4 Environment Variables (Runtime)

For runtime configuration without rebuild, use `assets/config.json`:

```json
{
  "apiBaseUrl": "https://cms-api.rbi.org.in",
  "keycloakUrl": "https://auth.rbi.org.in",
  "keycloakRealm": "cms",
  "keycloakClientId": "cms-portal"
}
```

Load in `APP_INITIALIZER` before bootstrap.

---

## 19. Non-Functional Requirements (NFR) — Public Portal

### 19.1 Performance & Scalability Requirements (BRD Section 1.1)

| Requirement | Target | Implementation Strategy |
|-------------|--------|------------------------|
| 10,000 concurrent user sessions | No degradation | Horizontal auto-scaling (K8s HPA), stateless frontend (session in browser), CDN for static assets |
| Complaint submission end-to-end < 5 seconds | Form load → complaint number display | Route-level lazy loading, async form submission, backend responds with ID synchronously |
| OTP delivery within 30 seconds | Any Indian mobile network | Backend integration with multi-provider SMS gateway (failover: primary + fallback), retry queue |
| Database queries for status tracking < 2 seconds | Result display | OpenSearch index for read queries, Redis cache for frequently accessed complaints, connection pooling |
| Horizontal auto-scaling for burst traffic | Cloud infrastructure | Kubernetes HPA (CPU/memory triggers), stateless pods, CDN + edge caching |

### 19.2 Frontend Performance Optimizations (Implemented)

| Optimization | Location | Impact |
|--------------|----------|--------|
| **Route-level lazy loading** | `app.routes.ts` (all routes use `loadComponent()`) | Initial bundle < 200KB, pages load on demand |
| **jsPDF lazy import** | `import('jspdf')` — dynamic | jsPDF (300KB+) only loaded when user downloads PDF |
| **Session timer (15 min)** | `file-complaint.component.ts` | Prevents resource hogging from idle sessions |
| **Auto-save every 60s** | Draft saved to localStorage | No data loss on session timeout, reduces re-entry |
| **Debounced search** | DEO home grid | API called only after user stops typing (300ms) |
| **Immutable signal updates** | All signal-based components | Angular change detection skips unchanged subtrees |
| **OnPush-compatible** | Standalone components with signals | Minimal DOM diffing on state changes |
| **Minimal third-party deps** | Only jsPDF, PrimeIcons | Small bundle, fast first paint |

### 19.3 Backend Requirements for NFR Compliance

| Area | Requirement | Implementation |
|------|-------------|----------------|
| **Load Balancing** | Distribute 10K sessions across instances | Nginx/ALB with sticky sessions OFF (stateless API) |
| **Database** | < 2s query for status tracking | Indexed queries on `complaint_number`, Redis cache (5 min TTL) |
| **OTP Service** | < 30s delivery | Multi-provider SMS: MSG91 (primary) + Twilio (fallback), Kafka-based async queue |
| **File Upload** | Large files don't block requests | Chunked upload (5MB chunks), streaming to object storage |
| **Connection Pool** | Handle burst | HikariCP: max 100 connections (prod), 3s timeout |
| **Cache** | Reduce DB load | Redis: complaint status (5 min), eligibility questions (1 hr), bank list (24 hr) |
| **CDN** | Static asset delivery | CloudFront/Akamai for JS/CSS/images, 1-year cache with content hash |
| **API Gateway** | Rate limiting | Bucket4j: 100 req/sec per IP, 10 complaint submissions per minute per user |

### 19.4 Scalability Architecture

```
                    ┌─────────────────────────────────────────────────────────────┐
                    │                    PRODUCTION DEPLOYMENT                     │
                    └─────────────────────────────────────────────────────────────┘

  [10,000 Users]
       │
       ▼
  ┌──────────┐     ┌──────────────┐     ┌──────────────────────────────────┐
  │   CDN    │────▶│  Load        │────▶│  Angular Static Files            │
  │(CloudFront)│    │  Balancer   │     │  (Nginx pods × N, auto-scaled)   │
  └──────────┘     │  (ALB/Nginx) │     └──────────────────────────────────┘
                   │              │
                   │              │────▶┌──────────────────────────────────┐
                   └──────────────┘     │  API Gateway (Spring Cloud)      │
                                        │  (Pods × N, HPA: CPU > 60%)     │
                                        └─────────────────┬────────────────┘
                                                          │
                                        ┌─────────────────┼─────────────────┐
                                        │                 │                 │
                                        ▼                 ▼                 ▼
                               ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
                               │ Complaint Svc│  │  OTP Service │  │ Search Svc   │
                               │ (Pods × N)   │  │  (Pods × N)  │  │ (Pods × N)   │
                               └──────┬───────┘  └──────┬───────┘  └──────┬───────┘
                                      │                  │                 │
                               ┌──────▼───────┐  ┌──────▼───────┐  ┌─────▼────────┐
                               │ Oracle DB    │  │ SMS Gateway  │  │ OpenSearch   │
                               │ (Primary +   │  │ (MSG91 +     │  │ (3-node      │
                               │  Standby)    │  │  Twilio)     │  │  cluster)    │
                               └──────────────┘  └──────────────┘  └──────────────┘
                                      │
                               ┌──────▼───────┐
                               │ Redis Cache  │
                               │ (Cluster)    │
                               └──────────────┘
```

### 19.5 Performance Testing Checklist

| Test Type | Tool | Target |
|-----------|------|--------|
| Load test (10K concurrent) | Apache JMeter / k6 | < 5s response time at P95 |
| Stress test (burst: 20K) | k6 with ramp-up | Auto-scale triggers within 30s |
| Soak test (8 hours) | JMeter | No memory leaks, stable response time |
| OTP delivery latency | Custom monitor | P95 < 30s, P99 < 45s |
| Database query time | APM (Grafana + Prometheus) | Status lookup P95 < 2s |
| Frontend FCP (First Contentful Paint) | Lighthouse | < 1.5s on 4G connection |
| Frontend TTI (Time to Interactive) | Lighthouse | < 3s on 4G connection |
| Bundle size | `ng build --stats-json` | Initial chunk < 200KB gzipped |

### 19.6 Monitoring & Alerting (Production)

| Metric | Alert Threshold | Dashboard |
|--------|----------------|-----------|
| Response time P95 | > 3s | Grafana: API Latency |
| Error rate (5xx) | > 1% | Grafana: Error Dashboard |
| Active sessions | > 8,000 (80% capacity) | Prometheus: session_gauge |
| Pod CPU usage | > 70% sustained 2 min | K8s HPA auto-scale trigger |
| OTP delivery failure | > 5% in 5 min window | PagerDuty alert |
| Database connection pool | > 80% utilization | HikariCP metrics → Prometheus |
| Redis cache hit ratio | < 60% | Grafana: Cache Performance |

### 19.7 Public Portal Features (Already Implemented)

| BRD Requirement | Implementation | Status |
|-----------------|---------------|--------|
| OTP-based login (mobile) | 6-digit OTP input, 30s resend timer, CAPTCHA | Done |
| Email + password login | Alternate login mode with CAPTCHA | Done |
| 15-minute session timeout | Auto-expire, countdown display, redirect to login | Done |
| Eligibility questionnaire | Sequential questions, block on ineligibility, non-maintainable closure | Done |
| Multi-step complaint form (6 steps) | Complainant → RE Details → Complaint → Auth Rep → Declaration → Review | Done |
| Auto-save every 60s | localStorage draft persistence | Done |
| Complaint categories + sub-categories | 8 categories, 40+ sub-categories | Done |
| File attachments | Multi-file, type validation, size limit | Done |
| Speech-to-text | Web Speech API integration | Done |
| Keyboard/tab navigation | Tab through fields, auto-advance steps | Done |
| Field tooltips | Hover help text for all fields | Done |
| PDF acknowledgement download | jsPDF with RBI header, reference number, complaint details | Done |
| Non-maintainable closure letter | Case ID generated, closure reason, download | Done |
| Complaint tracking | Reference number lookup → status display | Done |
| Withdraw complaint | Enter reference → submit withdrawal | Done |
| Submit feedback | Rating + comments | Done |
| File appeal | Against closed complaint | Done |
