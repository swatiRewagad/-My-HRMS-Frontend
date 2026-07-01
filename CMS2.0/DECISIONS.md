# CMS 2.0 — Decisions Log

> Every judgment call, assumption, what was chosen, and why.
> Operator reads this for review — not the full diff.

---

## Pending Decisions (Require Operator Confirmation)

### D-001: i18n Schema Design
**Options:**
- A) **Normalized table** (recommended): `translation_keys` (key, module, description) + `translations` (key, language_code, value). Adding a language = inserting rows, zero DDL.
- B) Wide table: one column per language. Adding a language = ALTER TABLE.

**Recommendation:** Option A. Scales without schema changes, supports admin UI as a view.
**Status:** ⏳ Awaiting confirmation

---

### D-002: TAT Clock Basis
**Options:**
- A) **Wall-clock time**: Timer counts real elapsed hours/days
- B) **Business hours only**: Excludes weekends/holidays, aligns with RBI working-day frameworks

**Context:** The existing `SLA_DUE_DATE` in the Oracle `COMPLAINT_MASTER` table already tracks SLA. RBI's grievance redress framework uses working days. The existing SLA monitor uses Instant (wall-clock) for breach detection.
**Recommendation:** Need clarity — if existing SLA_DUE_DATE was computed on business days, the timer display should match.
**Status:** ⏳ Awaiting confirmation

---

### D-003: Mobile Detection Strategy
**Options:**
- A) **Responsive CSS** (recommended): Mobile-first breakpoints, single codebase
- B) Server-side UA header sniffing: Different rendering per device type

**Recommendation:** Option A. UA sniffing is brittle, responsive CSS is the Angular SPA standard.
**Status:** ⏳ Awaiting confirmation

---

### D-004: Geo-IP Data Source
**Options:**
- A) **Local/offline MaxMind GeoLite2 database** (recommended): No external API calls, no citizen IP sent to third parties
- B) Third-party geo-IP API: Simpler but sends citizen IPs externally

**Recommendation:** Option A. For a regulator-adjacent platform, sending citizen IPs to external services is inappropriate.
**Status:** ⏳ Awaiting confirmation

---

### D-005: Similar-Cases Search Engine
**Options:**
- A) **OpenSearch `more_like_this`** (recommended): Already deployed and integrated in `cms-search-service`. No new infrastructure.
- B) Oracle Text: Available but requires index setup on the Oracle side (workflow/SLA DB), not the MySQL side where complaints live.
- C) New Elasticsearch: Rejected — OpenSearch already fills this niche.

**Recommendation:** Option A. OpenSearch is already indexing complaints with full-text fields. `more_like_this` query is native and needs zero new infrastructure.
**Status:** ⏳ Awaiting confirmation

---

### D-006: Citizen Email Source/Verification
**Context:** The complaint form includes an optional email field (`complainant_email`). It is:
- Entered by the citizen during complaint filing (Step 1 of form)
- **Not verified** (no confirmation link/code sent)
- Stored in `COMPLAINTS.complainant_email`
- Indexed in OpenSearch

**Question for Operator:** Can we use this unverified email as an OTP fallback channel? Options:
- A) Require email verification (send a link/code) before allowing email-OTP
- B) Trust the email as-is (risk: attacker could enter someone else's email and receive their OTP)
- C) Only offer email-OTP for citizens who have previously verified their email in a prior complaint

**Recommendation:** Option A (verify before trusting) — but this adds a registration-like step that may not align with the "anonymous complaint filing" flow.
**Status:** ⏳ Awaiting confirmation

---

### D-007: PII Encryption at Rest
**Context:** Currently, PII (name, email, phone, account number, address) is stored plaintext in MySQL. Phase 3 adds per-session in-transit encryption. The question is whether at-rest encryption is also needed.

**Options:**
- A) MySQL TDE (Transparent Data Encryption): Encrypts data files, transparent to application
- B) Column-level application encryption: Encrypt before JPA persist, decrypt on read
- C) No at-rest encryption: Rely on DB access controls + TLS to DB

**Recommendation:** Flag for compliance review. TDE is the least invasive. Column-level breaks searching/indexing.
**Status:** ⏳ Awaiting confirmation

---

### D-008: Dashboard Refresh Enforcement
**Context:** Requirement says "no manual refresh" for officer dashboard, refresh every 10 min.

**Options:**
- A) **Server-side cache per officer** (recommended): API returns cached response until interval elapses, even on F5
- B) UI-only: Hide refresh button but allow browser reload to fetch fresh data

**Recommendation:** Option A. Truly enforces the intent (reduce DB load across distributed officers).
**Status:** ⏳ Awaiting confirmation

---

### D-009: Cache Strategy
**Context:** Hazelcast is configured in `HazelcastCacheConfig.java` with maps for dashboard, categories, banks, form-config, email-stats. But `spring.cache.type: none` in application.yml disables it.

**Options:**
- A) **Enable Hazelcast**: Set `spring.cache.type: hazelcast` (or remove the `none` override)
- B) Switch to Caffeine (simple in-process): Lighter, no clustering needed for single-instance
- C) Keep disabled, add caching only where new phases need it

**Recommendation:** Option A — Hazelcast is already configured and dependency exists. Enable it. For Phase 10's per-officer cache, add a new map config.
**Status:** ⏳ Awaiting confirmation

---

## Confirmed Decisions

| ID | Decision | Rationale | Date |
|----|----------|-----------|------|
| — | (none yet) | — | — |

---

## Assumptions Made (Low-Risk Defaults)

| ID | Assumption | Phase | Rationale |
|----|-----------|-------|-----------|
| A-001 | Use JPA ddl-auto for schema changes (no Flyway) | All | Existing pattern, no migration tooling present |
| A-002 | Single-instance deployment for cms-backend | 1 | Rate-limit stored in MySQL covers multi-instance, but primary deployment appears single-instance based on Hazelcast TCP disabled |
| A-003 | OTP length = 6 digits, expiry = 5 minutes | 1 | Industry standard for SMS OTP |
| A-004 | Cooloff progression: 30s → 60s → 120s → 300s → 600s (cap) | 1 | Exponential with 10-min ceiling |
| A-005 | CAPTCHA difficulty: 6 alphanumeric characters, server-generated image | 1 | Matches existing UI slot |
| A-006 | Week boundary for officer stats = ISO calendar week (Mon-Sun) | 10 | Standard in Indian business context |
