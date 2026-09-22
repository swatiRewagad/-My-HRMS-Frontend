# CMS 2.0 -- User Story GAP Analysis, Enhanced Acceptance Criteria, and Implementation Checklist

**Date:** 2026-09-10
**Analyst:** QA Architecture Review
**Scope:** UST421, UST118, UST115

---

## UST421 -- Eligibility Check: Simplify Question

### Current State of Implementation

**What exists:**
- `EligibilityQuestionnaireComponent` (`cms-portal-frontend/src/app/components/eligibility-questionnaire/`) -- Renders questions from the backend `QUESTION_MASTER` table but has NO "Simplify" feature in its template.
- `EligibilityWizardComponent` (`cms-portal-frontend/src/app/components/public/eligibility-wizard/`) -- A separate step-by-step wizard with hardcoded questions; it uses `hint` fields but has no toggle for simplified text.
- `EligibilityTranslationSeeder` -- Seeds simplified translations for 6 legal questions (`eligibility.q_sub_judice_simple`, `eligibility.q_already_settled_simple`, etc.) in 10 languages. The key `eligibility.simplify_btn` ("Simplify For Me") also exists in translations.
- `QUESTION_MASTER` table schema has NO `SIMPLIFIED_TEXT` column. Simplified text only exists in the i18n translation table keyed by `*_simple` suffix convention.
- `EligibilityQuestion` model (frontend) has NO `simplifiedText` field. The model only carries: `questionCode`, `questionText`, `questionType`, `category`, `mandatory`, `displayOrder`, `options`.
- The eligibility questionnaire HTML template shows questions, Yes/No/Single Choice buttons, validation errors, and results -- but has zero UI for the '?' icon or simplified text toggle.

**What is missing:**
- No '?' icon or "Simplify For Me" button in the eligibility questionnaire component template.
- No frontend state to track simplified-text visibility per question.
- No mapping logic that resolves `questionCode` to its `*_simple` translation key.
- The `EligibilityQuestion` model has no `simplifiedText` or `simplifiedTranslationKey` property.
- Backend `QUESTION_MASTER` has no `SIMPLIFIED_TEXT` column (though translations exist via seeder).
- The wizard component has `hint` text but no simplified toggle either.
- No analytics tracking of "Simplify" button usage.

### GAP Analysis

| # | Gap Category | Finding | Severity |
|---|-------------|---------|----------|
| G1 | **Missing Feature** | No '?' icon or "Simplify For Me" button exists in either eligibility component template | CRITICAL |
| G2 | **Data Model** | `EligibilityQuestion` frontend model lacks `simplifiedText` field; `QUESTION_MASTER` DDL lacks `SIMPLIFIED_TEXT` column | HIGH |
| G3 | **Graceful Degradation** | No handling when a question has no simplified text available (e.g., simple questions like "Have you filed a complaint with RE?") | HIGH |
| G4 | **Answer Recording** | The story says "answer recorded against original question regardless of simplified view" -- this is already satisfied since answers are keyed by `questionCode`, but there is no explicit test verifying this | MEDIUM |
| G5 | **i18n** | Simplified translations exist for 10 languages via `EligibilityTranslationSeeder`, but only for 6 out of ~10 eligibility questions. Questions like `q_select_re`, `q_filed_with_re`, `q_received_reply`, `q_sent_reminder` have no `_simple` variant | HIGH |
| G6 | **Accessibility** | No ARIA attributes for the '?' icon (needs `aria-label`, `aria-expanded`, `role="button"`). Simplified text panel needs `aria-live="polite"` for screen readers | HIGH |
| G7 | **Keyboard Navigation** | '?' icon must be focusable (tabindex) and togglable via Enter/Space | MEDIUM |
| G8 | **Mobile/Responsive** | No consideration for how simplified text displays on small screens (collapsible? inline? tooltip vs. expandable block?) | MEDIUM |
| G9 | **Performance** | Simplified texts are loaded as part of the full translation bundle per locale -- no extra API call needed. However, if simplified text were moved to a separate API, caching would be required | LOW |
| G10 | **Original Question Visibility** | Scenario 2 says "original question remains visible alongside simplified text" -- need to ensure simplified text is ADDITIVE, not a replacement. The component needs two distinct text areas | MEDIUM |
| G11 | **Browser Back Button** | Toggling simplify state should NOT affect browser history (no `pushState` on toggle) | LOW |
| G12 | **Concurrent Questions** | If multiple questions are displayed simultaneously (questionnaire mode), each question's simplify toggle must be independent | MEDIUM |

### Enhanced Acceptance Criteria (Missing Scenarios)

```gherkin
# --- Scenario 4: No simplified text available ---
Given I am on the eligibility questionnaire page
And the question "Have you filed a written complaint with the RE?" has no simplified text
When I click the '?' icon next to this question
Then the '?' icon should not appear for this question
# (or) Then a message "No simplified version available" is shown

# --- Scenario 5: Simplified text in non-English locale ---
Given I have selected Hindi as my language
And I am on the eligibility questionnaire page
When I click the '?' icon next to the "sub-judice" question
Then the simplified text is displayed in Hindi
And the original question remains visible in Hindi

# --- Scenario 6: Screen reader announcement ---
Given I am using a screen reader
When I click the '?' icon next to a question
Then the screen reader announces "Simplified version" followed by the simplified text
And the original question is not re-announced

# --- Scenario 7: Keyboard accessibility ---
Given I am navigating the eligibility questionnaire using only the keyboard
When I tab to the '?' icon and press Enter or Space
Then the simplified text is revealed
And focus moves to the simplified text area

# --- Scenario 8: Toggle off simplified text ---
Given the simplified text for a question is currently displayed
When I click the '?' icon again (or a close button)
Then the simplified text is hidden
And only the original question text remains visible

# --- Scenario 9: Answer binding unaffected ---
Given the simplified text for "Is the complaint sub-judice?" is displayed
When I select "Yes" as my answer
Then the answer "YES" is recorded against question code "SUB_JUDICE"
And the answer is NOT recorded against any simplified question variant

# --- Scenario 10: Multiple questions open simultaneously ---
Given the questionnaire displays all questions at once
When I click '?' on question 5 and then '?' on question 7
Then both simplified texts are visible simultaneously
And collapsing question 5's simplified text does not affect question 7

# --- Scenario 11: Mobile responsive layout ---
Given I am viewing the eligibility questionnaire on a device with viewport width < 768px
When I click the '?' icon
Then the simplified text appears below the original question (not as a tooltip)
And the layout does not break or cause horizontal scrolling

# --- Scenario 12: API failure -- translation load fails ---
Given the translation API returns a 500 error
And fallback English translations have loaded
When I click the '?' icon next to a question
Then the English simplified text is displayed (fallback)
# (or) The '?' icon is hidden if no fallback exists
```

### Implementation and Testing Checklist

**Frontend:**
- [ ] Add `simplifiedTranslationKey?: string` to `EligibilityQuestion` model (or derive it as `questionCode + '_simple'`)
- [ ] Add '?' icon button next to each question label in `eligibility-questionnaire.component.html`
- [ ] Add `showSimplified: Record<string, boolean>` signal to track toggle state per question
- [ ] Conditionally render simplified text block beneath each question when toggled
- [ ] Hide '?' icon for questions that have no `*_simple` translation key (check via `TranslationService.translate(key) !== key`)
- [ ] Add ARIA attributes: `role="button"`, `aria-label="Show simplified version"`, `aria-expanded`, `aria-controls`
- [ ] Ensure simplified text container has `id` matching `aria-controls` and `aria-live="polite"`
- [ ] Style: simplified text in a distinct visual container (light blue background, indented, smaller font)
- [ ] Mobile: convert tooltip approach to inline expandable block at breakpoint < 768px
- [ ] Verify answer payloads in `submitCheck()` only contain original `questionCode` keys

**Backend:**
- [ ] Option A (preferred): No backend change needed -- simplified text lives in translation table via `*_simple` key convention. Frontend resolves it.
- [ ] Option B (if per-question control is needed): Add `SIMPLIFIED_TEXT` column to `QUESTION_MASTER` and return it in the question DTO
- [ ] Ensure `EligibilityTranslationSeeder` covers ALL questions that need simplified versions
- [ ] Add `_simple` translations for missing questions: `q_select_re`, `q_filed_with_re`, `q_received_reply`, `q_sent_reminder`

**Playwright E2E Tests Needed:**
- [ ] `eligibility-simplify.spec.ts`: Click '?' icon, verify simplified text appears
- [ ] Verify original question text remains visible after toggle
- [ ] Verify answer submission payload uses original question code
- [ ] Verify '?' icon is hidden for questions without simplified text
- [ ] Language switch: toggle simplified text, switch to Hindi, verify Hindi simplified text
- [ ] Keyboard-only: Tab to '?' icon, press Enter, verify expanded
- [ ] Mobile viewport: verify no layout break at 375px width

---

## UST118 -- View FAQ

### Current State of Implementation

**What exists:**
- FAQ section in `public-home.component.html` (lines 242-269): An accordion-style FAQ list with expand/collapse.
- FAQs are **hardcoded** in `public-home.component.ts` as a `faqs` array with 3 items (question, answer, open state).
- "View All" link exists in HTML (`<a href="javascript:void(0)" class="view-all">`) but is a dead link -- it does nothing.
- `PublicHomePage` page object (e2e) has a `faqSection` locator.
- Basic ARIA: `aria-expanded` and `aria-controls` attributes are present on FAQ buttons.
- FAQ questions and answers are NOT translated (hardcoded English strings, not using `translate` pipe for FAQ content).
- There is NO FAQ table in the database.
- There is NO FAQ API endpoint in `cms-backend`.
- There is NO dedicated FAQ page or component.
- `cms-frontend` (officer portal) also has a hardcoded FAQ section in `home.component.html`.

**What is missing:**
- No backend FAQ API or database table.
- No "View All" page or route.
- FAQs are not from a CMS/admin-manageable source.
- FAQ content is not translated (not using i18n keys).
- No search/filter on FAQ list.
- No pagination or lazy loading for "all FAQs."

### GAP Analysis

| # | Gap Category | Finding | Severity |
|---|-------------|---------|----------|
| G1 | **Dead Link** | "View All" link (`href="javascript:void(0)"`) does nothing -- no route, no component, no page | CRITICAL |
| G2 | **No Backend** | FAQs are hardcoded in the component. No `FAQ` database table, no REST API, no admin CRUD | CRITICAL |
| G3 | **Only 3 FAQs** | The public portal shows exactly 3 hardcoded FAQs. For "View All" to make sense, there must be more FAQs and they must come from a backend | HIGH |
| G4 | **No i18n** | FAQ questions and answers are plain English strings, not translation keys. The system supports 10+ languages via `TranslationService` but FAQs bypass it entirely | HIGH |
| G5 | **No Search** | On a "View All" page with potentially 20+ FAQs, there is no search/filter capability | MEDIUM |
| G6 | **No Categorization** | FAQs have no category field (e.g., "Filing", "Eligibility", "Appeal", "General"). A large FAQ list needs categorization | MEDIUM |
| G7 | **No Pagination** | If FAQs grow to 50+, the page will be very long with no pagination or virtual scroll | MEDIUM |
| G8 | **Accessibility** | Current FAQ section has basic ARIA (`aria-expanded`, `aria-controls`) but FAQ button IDs (`faq-btn-{i}`) are referenced in `aria-labelledby` but never set as `id` on the button element | HIGH |
| G9 | **Deep Linking** | No URL hash or route parameter to link directly to a specific FAQ (e.g., `/faq#filing-complaint`) | LOW |
| G10 | **Admin Management** | No admin interface to add, edit, reorder, or deactivate FAQs | MEDIUM |
| G11 | **Caching** | If FAQ data moves to an API, it should be cached (FAQs rarely change). No cache strategy exists | LOW |
| G12 | **Empty State** | No handling if the FAQ API returns empty or errors out. Current hardcoded approach cannot fail, but a backend-driven approach needs error/empty states | MEDIUM |
| G13 | **SEO** | FAQ section content is in the DOM (good), but there is no JSON-LD `FAQPage` structured data for search engines | LOW |
| G14 | **Performance** | "View All" page loading 50+ FAQs with expand/collapse -- need to evaluate if all answers should be in the DOM or loaded on-demand | LOW |
| G15 | **Mobile** | FAQ accordion works on mobile (block layout), but no explicit mobile testing for long answer text wrapping | LOW |

### Enhanced Acceptance Criteria (Missing Scenarios)

```gherkin
# --- Scenario 3: FAQ section visible on scroll ---
Given I am on the public home page (unauthenticated)
When I scroll to the FAQ section
Then I see at least 3 frequently asked questions displayed in accordion format
And the first FAQ item is expanded by default
And the remaining items are collapsed

# --- Scenario 4: "View All" navigates to dedicated FAQ page ---
Given I am on the public home page
And I see the FAQ section with a "View All" link
When I click the "View All" link
Then I am navigated to "/public/faq" route
And I see a complete list of all FAQs loaded from the backend

# --- Scenario 5: FAQ page displays categories ---
Given I am on the FAQ page ("/public/faq")
When the page loads
Then FAQs are grouped by category (e.g., "Filing", "Eligibility", "Appeal", "General")
And each category heading is visible
And I can expand/collapse individual FAQ items within each category

# --- Scenario 6: FAQ search/filter ---
Given I am on the FAQ page with 20+ questions
When I type "mobile number" in the search field
Then only FAQs containing "mobile number" in the question or answer are shown
And a "No results found" message is shown if no FAQs match

# --- Scenario 7: FAQ in non-English language ---
Given I have selected Tamil as my language from the language switcher
When I scroll to the FAQ section on the home page
Then FAQ questions and answers are displayed in Tamil
And the "View All" link text is also in Tamil

# --- Scenario 8: FAQ API failure fallback ---
Given the FAQ API endpoint returns a 500 error
When the public home page loads
Then the FAQ section displays fallback hardcoded FAQs in English
And an error toast or banner is NOT shown (graceful degradation)

# --- Scenario 9: Empty FAQ state ---
Given the FAQ API returns an empty array
When I navigate to the FAQ page
Then a message "No FAQs available at this time" is displayed
And the page does not show a broken layout

# --- Scenario 10: FAQ accordion keyboard navigation ---
Given I am navigating the FAQ section using only the keyboard
When I press Tab to focus on a collapsed FAQ question
And I press Enter or Space
Then the FAQ answer expands
And focus remains on the question button

# --- Scenario 11: Deep link to specific FAQ ---
Given someone shares a link "/public/faq#q5"
When I open this link in my browser
Then the page scrolls to FAQ question 5
And that FAQ item is automatically expanded

# --- Scenario 12: FAQ accordion collapse/expand mutual exclusivity (optional) ---
Given FAQ item 1 is currently expanded
When I click on FAQ item 2
Then FAQ item 2 expands
And FAQ item 1 MAY or MAY NOT collapse (define business decision)
```

### Implementation and Testing Checklist

**Database:**
- [ ] Create `FAQ_MASTER` table: `ID`, `QUESTION_KEY` (i18n), `ANSWER_KEY` (i18n), `CATEGORY`, `DISPLAY_ORDER`, `IS_ACTIVE`, `CREATED_AT`, `UPDATED_AT`
- [ ] Seed initial FAQ data (migrate the 3 existing hardcoded FAQs plus additional ones)
- [ ] Seed translation keys for all FAQ Q&A in 10+ languages via `TranslationSeeder`

**Backend:**
- [ ] Create `FaqController` with endpoints: `GET /api/v1/faq` (all active, ordered), `GET /api/v1/faq?category={cat}` (filtered)
- [ ] Create `FaqService` with caching (`@Cacheable("faqs")`, evict on admin update)
- [ ] Create `FaqEntity`, `FaqRepository`
- [ ] Admin endpoints (if UST calls for admin CRUD): `POST /api/v1/admin/faq`, `PUT /api/v1/admin/faq/{id}`, `DELETE /api/v1/admin/faq/{id}`
- [ ] Role-guard admin endpoints with `@PreAuthorize("hasRole('ADMIN')")`

**Frontend:**
- [ ] Create `FaqService` in `cms-portal-frontend/src/app/services/faq.service.ts`
- [ ] Create `FaqPageComponent` at `/public/faq` route with full FAQ list, category tabs, and search bar
- [ ] Update `PublicHomeComponent` to load top-N FAQs from backend (with fallback to hardcoded)
- [ ] Wire "View All" link to `routerLink="/public/faq"` (replace `javascript:void(0)`)
- [ ] Apply `translate` pipe to FAQ content (Q&A keys from backend)
- [ ] Fix ARIA: add `id="faq-btn-{i}"` on FAQ question buttons (matches existing `aria-labelledby` refs)
- [ ] Add search input with debounce (300ms) on FAQ page
- [ ] Add empty state component for zero FAQ results
- [ ] Add category filter tabs/chips

**Playwright E2E Tests Needed:**
- [ ] `faq.spec.ts`: Home page FAQ section visible on scroll
- [ ] Click FAQ accordion item to expand, verify answer text visible
- [ ] Click "View All" link, verify navigation to `/public/faq`
- [ ] FAQ page: verify all FAQs loaded, accordion works
- [ ] FAQ page: search/filter functionality
- [ ] Language switch on FAQ page, verify translated content
- [ ] Keyboard navigation: Tab + Enter on FAQ items
- [ ] Empty state: mock empty API response, verify "No FAQs" message

---

## UST115 -- Configurable Timelines (Admin)

### Current State of Implementation

**What exists:**
- `MreProperties.java` -- Spring `@ConfigurationProperties(prefix = "cms.mre")` with fields: `reWindowDays` (30), `npciWindowDays` (30), `cardNetworkWindowDays` (60), `filingDeadlineDays` (90), `limitationPeriodYears` (3), `windowBasis` (BUSINESS/CALENDAR).
- `MreController` -- Exposes `GET /api/v1/mre/config` (read-only) and `POST /api/v1/mre/evaluate`.
- `EligibilityWizardController` -- Uses `@Value("${cms.mre.re-window-days:30}")` and `@Value("${cms.mre.filing-deadline-days:90}")` for hardcoded defaults.
- `MaintainabilityRulesEngine` -- The core rules engine reads from `MreProperties` (injected bean). It uses `getApplicableWindowDays()` for category-specific windows and `config.getFilingDeadlineDays()` throughout.
- `configmaps.yaml` (OpenShift) -- Has `FILING_WINDOW_DAYS: "365"` in the eligibility-service ConfigMap.
- `CepcTimelineComponent` -- A complaint AUDIT TRAIL timeline, NOT a configurable-timelines admin UI.
- `AdminDashboardComponent` -- An analytics dashboard with charts. NO timeline configuration UI.
- `AdminService` -- Only has `getDashboardStats()`. No timeline config CRUD.
- `AuditLog` entity -- Exists for complaint-level audit events, but NOT for admin configuration changes.
- **There is NO admin UI to update timeline/window values.**
- **There is NO API to update timeline/window values at runtime (they are application properties, requiring restart or ConfigMap update + pod restart).**
- **There is NO audit log for admin configuration changes (the AuditLog entity is complaint-scoped).**
- **There is NO database table storing configurable timeline values -- they live in `application.yml` / ConfigMaps.**

### GAP Analysis

| # | Gap Category | Finding | Severity |
|---|-------------|---------|----------|
| G1 | **No Admin UI** | There is no UI for an admin to view or update timeline/window values. The admin dashboard only shows charts and rule stats | CRITICAL |
| G2 | **No Runtime Update API** | Timeline values are Spring `@ConfigurationProperties` / `@Value` injected at boot. There is no `PUT /api/v1/admin/config/timelines` endpoint. Changing values requires ConfigMap edit + pod restart | CRITICAL |
| G3 | **No Database Persistence** | Timeline config lives in `application.yml` / OpenShift ConfigMaps, not in a database table. For admin-editable config, values must be in a `SYSTEM_CONFIG` or `TIMELINE_CONFIG` table | CRITICAL |
| G4 | **No Audit Trail for Config Changes** | `AuditLog` entity is complaint-scoped (has `complaintNumber` NOT NULL). There is no config-change audit table or entity. Scenario 2 explicitly requires "system logs timeline changes with admin details and timestamp" | CRITICAL |
| G5 | **Enforcement After Update** | If an admin changes `reWindowDays` from 30 to 45, the `MaintainabilityRulesEngine` reads from `MreProperties`. If values are in a DB, the properties bean must be refreshed or the engine must query the DB directly | HIGH |
| G6 | **In-Flight Complaints** | No business rule defined: when the filing window changes from 90 to 120 days, do in-flight complaints use the old value (snapshot at filing time) or the new value? The `COMPLAINTS` table has `ELIGIBILITY_TIMELINE` (CLOB) which could store the snapshot | HIGH |
| G7 | **Input Validation -- Minimum/Maximum** | No defined minimum or maximum for timeline values. Can an admin set `reWindowDays = 0`? `filingDeadlineDays = -5`? `limitationPeriodYears = 0`? | HIGH |
| G8 | **Input Validation -- Type** | Timeline values must be positive integers. No frontend or backend validation exists since there is no form | HIGH |
| G9 | **Concurrent Admin Edits** | If two admins update timelines simultaneously, last-write-wins. Need optimistic locking (version column) or last-modified check | MEDIUM |
| G10 | **Role-Based Access** | Only ADMIN role should be able to update timelines. Currently `MreController.getConfig()` has no `@PreAuthorize` annotation -- any authenticated user can read config | HIGH |
| G11 | **Confirmation Dialog** | Changing filing deadlines has legal implications. The admin UI must have a confirmation step ("Are you sure? This affects X in-flight complaints") | MEDIUM |
| G12 | **Category-Specific Windows** | `MreProperties` has `npciWindowDays` and `cardNetworkWindowDays` in addition to the default `reWindowDays`. The admin UI needs to show ALL configurable window types, not just one | MEDIUM |
| G13 | **Effective Date** | Should timeline changes take effect immediately or from a specified future date? No requirement exists | MEDIUM |
| G14 | **Rollback / History** | If an admin makes a mistake, there is no UI to view historical values or rollback to a previous configuration | LOW |
| G15 | **Cache Invalidation** | `MaintainabilityRulesEngine` uses `@Cacheable("mre-rules")`. If timelines change, the cache key (`version`) must increment. Without a DB-backed version, cache invalidation is manual | HIGH |
| G16 | **Multi-Instance Consistency** | In OpenShift, multiple pods run the backend. A DB update on one pod must propagate. Using DB + cache eviction via Kafka event or Hazelcast distributed cache (which exists) solves this | MEDIUM |
| G17 | **Hazelcast** | The system uses embedded Hazelcast for caching. Config changes must invalidate the `mre-rules` cache across all cluster nodes | MEDIUM |

### Enhanced Acceptance Criteria (Missing Scenarios)

```gherkin
# --- Scenario 3: Admin views current timeline configuration ---
Given I am logged in as an admin user with ADMIN role
When I navigate to the "Timeline Configuration" page
Then I see the current values for:
  | Field                        | Current Value |
  | RE Response Window (days)    | 30            |
  | NPCI Window (days)           | 30            |
  | Card Network Window (days)   | 60            |
  | Filing Deadline (days)       | 90            |
  | Limitation Period (years)    | 3             |
  | Window Calculation Basis     | BUSINESS      |
And each field shows the last-modified date and the admin who last changed it

# --- Scenario 4: Input validation -- zero/negative values ---
Given I am on the Timeline Configuration page as an admin
When I enter "0" in the "RE Response Window (days)" field
And I click "Save"
Then an error message "Value must be at least 1 day" is shown
And the value is NOT saved

Given I am on the Timeline Configuration page as an admin
When I enter "-5" in the "Filing Deadline (days)" field
And I click "Save"
Then an error message "Value must be a positive integer" is shown
And the value is NOT saved

# --- Scenario 5: Input validation -- unreasonably large values ---
Given I am on the Timeline Configuration page as an admin
When I enter "9999" in the "RE Response Window (days)" field
And I click "Save"
Then a warning "Value exceeds 365 days. Are you sure?" is shown
And I must confirm to proceed

# --- Scenario 6: Non-integer input rejected ---
Given I am on the Timeline Configuration page as an admin
When I enter "30.5" in the "RE Response Window (days)" field
Then the input field rejects the decimal and only accepts "30"
# (or) An error message "Must be a whole number" is shown

# --- Scenario 7: Confirmation dialog before save ---
Given I have changed "Filing Deadline (days)" from 90 to 120
When I click "Save"
Then a confirmation dialog appears showing:
  - The old value (90 days) and new value (120 days)
  - The number of in-flight complaints that may be affected
  - A warning about legal implications
And I must click "Confirm" to proceed
And clicking "Cancel" reverts the form to previous values

# --- Scenario 8: Audit log entry created ---
Given I am admin "john.doe" and I change "RE Response Window" from 30 to 45
When I confirm and save the change
Then a record is created in the audit log with:
  | Field             | Value                                   |
  | Admin Username    | john.doe                                |
  | Admin IP Address  | 192.168.1.100                           |
  | Timestamp         | 2026-09-10T14:30:00                     |
  | Config Key        | cms.mre.re-window-days                  |
  | Old Value         | 30                                      |
  | New Value         | 45                                      |
And the audit log is immutable (no UPDATE/DELETE allowed)

# --- Scenario 9: Non-admin user cannot access ---
Given I am logged in as a user with CEPC_DO role
When I attempt to navigate to "/admin/timeline-config"
Then I am shown an "Access Denied" page or redirected to my dashboard
And the API endpoint returns HTTP 403

# --- Scenario 10: Effect on in-flight complaints ---
Given there are 50 complaints filed when "Filing Deadline" was 90 days
When an admin changes "Filing Deadline" to 120 days
Then complaints that were already evaluated retain their original eligibility assessment
And only NEW complaints use the updated 120-day deadline
(The complaint's ELIGIBILITY_TIMELINE CLOB stores the snapshot)

# --- Scenario 11: Concurrent admin edit ---
Given admin "alice" opens the Timeline Config page and sees reWindowDays = 30
And admin "bob" simultaneously changes reWindowDays to 45 and saves
When "alice" changes reWindowDays to 40 and clicks Save
Then the system detects the conflict (optimistic lock / version mismatch)
And shows "This configuration was modified by bob at 14:30. Please refresh and try again."

# --- Scenario 12: Changes propagate across pods ---
Given the system runs 3 backend pod replicas
When an admin updates "Filing Deadline" from 90 to 120 on pod-1
Then pods 2 and 3 pick up the new value within 30 seconds
And the MRE cache is invalidated on all pods

# --- Scenario 13: View audit history ---
Given I am on the Timeline Configuration page as an admin
When I click "View Change History"
Then I see a table of all past timeline changes with:
  - Timestamp, Admin Name, Config Key, Old Value, New Value
And the table is sorted by most recent first
And the table supports pagination

# --- Scenario 14: Window basis change ---
Given the current window calculation basis is "BUSINESS" days
When an admin changes it to "CALENDAR" days
Then a warning is shown: "Changing calculation basis affects all in-flight window calculations"
And I must acknowledge before saving
```

### Implementation and Testing Checklist

**Database:**
- [ ] Create `SYSTEM_CONFIG` table:
  ```
  ID               NUMBER(19) PRIMARY KEY
  CONFIG_KEY       VARCHAR2(100) NOT NULL UNIQUE
  CONFIG_VALUE     VARCHAR2(500) NOT NULL
  CONFIG_TYPE      VARCHAR2(20) -- INTEGER, STRING, ENUM
  DESCRIPTION      VARCHAR2(500)
  MIN_VALUE        NUMBER(10)
  MAX_VALUE        NUMBER(10)
  VERSION          NUMBER(10) DEFAULT 1  -- optimistic lock
  UPDATED_BY       VARCHAR2(200)
  UPDATED_AT       TIMESTAMP
  CREATED_AT       TIMESTAMP DEFAULT SYSTIMESTAMP
  ```
- [ ] Create `CONFIG_AUDIT_LOG` table:
  ```
  ID               NUMBER(19) PRIMARY KEY
  CONFIG_KEY       VARCHAR2(100) NOT NULL
  OLD_VALUE        VARCHAR2(500)
  NEW_VALUE        VARCHAR2(500) NOT NULL
  CHANGED_BY       VARCHAR2(200) NOT NULL
  CHANGED_BY_ROLE  VARCHAR2(50)
  CHANGED_AT       TIMESTAMP NOT NULL
  IP_ADDRESS       VARCHAR2(50)
  REMARKS          VARCHAR2(1000)
  ```
- [ ] Seed initial values from current `MreProperties` defaults
- [ ] Add migration script: `V7__system_config_tables.sql`

**Backend:**
- [ ] Create `SystemConfigEntity`, `SystemConfigRepository`
- [ ] Create `ConfigAuditLogEntity`, `ConfigAuditLogRepository`
- [ ] Create `TimelineConfigService`:
  - `getAll()` -- returns all timeline-related config entries
  - `update(key, value, adminUsername)` -- validates, saves, creates audit log entry, invalidates cache
  - `getAuditHistory(key, pageable)` -- paged audit log for a key
- [ ] Create `TimelineConfigController`:
  - `GET /api/v1/admin/timeline-config` -- returns current values
  - `PUT /api/v1/admin/timeline-config` -- updates values (array of key-value pairs)
  - `GET /api/v1/admin/timeline-config/audit` -- paged audit history
  - All endpoints: `@PreAuthorize("hasRole('ADMIN')")`
- [ ] Refactor `MreProperties` to read from DB instead of (or fallback to) application.yml:
  - Option A: `MreProperties` reads from `SystemConfigRepository` on each access (with Hazelcast cache)
  - Option B: `MreProperties` is refreshed via a scheduled task or event listener
- [ ] Input validation (Jakarta Bean Validation):
  - `reWindowDays`: min=1, max=365
  - `filingDeadlineDays`: min=1, max=730
  - `limitationPeriodYears`: min=1, max=10
  - `windowBasis`: must be `CALENDAR` or `BUSINESS`
  - All integer fields: must be positive whole numbers
- [ ] Optimistic locking: check `version` on update, throw `409 Conflict` on mismatch
- [ ] Hazelcast cache eviction: after config update, evict `mre-rules` cache entry and publish a cluster-wide event
- [ ] Add `@PreAuthorize("hasRole('ADMIN')")` to `MreController.getConfig()` (currently unprotected)

**Frontend:**
- [ ] Create `TimelineConfigComponent` at route `/admin/timeline-config`
- [ ] Add sidebar item in `AdminDashboardComponent` for "Timeline Configuration"
- [ ] Form with input fields for each configurable value, showing current values on load
- [ ] Frontend validation:
  - HTML `type="number"`, `min="1"`, `step="1"` attributes
  - Angular reactive form validators: `Validators.required`, `Validators.min(1)`, `Validators.max(365)`, `Validators.pattern(/^[0-9]+$/)`
  - Warning dialog when value > 365
- [ ] Confirmation dialog before save (show old vs. new values, affected complaint count)
- [ ] Success toast on save, error toast on failure (409 conflict, 403 forbidden, 500 server error)
- [ ] "View Change History" section or dialog with paginated audit log table
- [ ] Show "Last modified by X at Y" beneath each field
- [ ] Role guard: route guard checking `hasRole('ADMIN')`

**Playwright E2E Tests Needed:**
- [ ] `admin-timeline-config.spec.ts`:
  - Admin login, navigate to timeline config page
  - Verify current values are displayed
  - Update a value, confirm dialog appears, confirm, verify success
  - Verify audit log entry appears in change history
  - Attempt invalid input (0, negative, decimal), verify validation errors
  - Attempt access with non-admin role, verify 403 / redirect
  - Update value, navigate to eligibility wizard, verify new value is used in eligibility check
- [ ] `admin-timeline-audit.spec.ts`:
  - Make multiple changes, verify audit history shows all entries
  - Verify audit history pagination
  - Verify audit entries show correct old/new values, admin name, timestamp

---

## Summary Matrix

| User Story | Implementation Status | # Critical Gaps | # High Gaps | # Medium Gaps | # Low Gaps |
|------------|----------------------|-----------------|-------------|---------------|------------|
| UST421 -- Simplify Question | **Partial** (translations seeded, no UI) | 1 | 3 | 4 | 3 |
| UST118 -- View FAQ | **Minimal** (hardcoded, dead "View All") | 2 | 2 | 4 | 4 |
| UST115 -- Configurable Timelines | **Not Started** (read-only properties only) | 4 | 4 | 5 | 1 |

### Priority Recommendations

1. **UST115** needs the most work. The entire admin config feature is unbuilt -- database table, API, UI, and audit trail are all missing. Begin with the database migration and backend service.

2. **UST118** has a live but dead-end FAQ section. The hardcoded FAQs provide a visual baseline, but "View All" is broken and there is no backend. Creating the FAQ table + API and wiring the "View All" link is the critical path.

3. **UST421** is closest to done. Translation keys with simplified text exist in 10 languages. The remaining work is purely frontend: adding the '?' toggle icon, conditional rendering of simplified text, and accessibility attributes.
