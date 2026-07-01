# CMS 2.0 — Decisions Log

> Every judgment call, assumption, what was chosen, and why.
> Operator reads this for review — not the full diff.

---

## Pending Decisions (Require Operator Confirmation)

(None — all confirmed 2026-06-30)

---

## Confirmed Decisions

---

## Confirmed Decisions

| ID | Decision | Choice | Date |
|----|----------|--------|------|
| D-001 | i18n schema | **Normalized** (key table + translations table) | 2026-06-30 |
| D-002 | TAT clock basis | **Business hours** (9 AM–6 PM Mon–Fri, Indian holidays from backend, no frontend holiday management) | 2026-06-30 |
| D-003 | Mobile detection | **Responsive CSS** (mobile-first breakpoints) | 2026-06-30 |
| D-004 | Geo-IP source | **Local MaxMind GeoLite2 DB** (no external API) | 2026-06-30 |
| D-005 | Similar-cases engine | **OpenSearch more_like_this** with provider abstraction layer for future swap | 2026-06-30 |
| D-006 | Email OTP | **Require email verification first** before allowing as OTP channel | 2026-06-30 |
| D-007 | PII at-rest | **MySQL TDE** (transparent, no app code changes) | 2026-06-30 |
| D-008 | Dashboard refresh | **Server-side cache enforcement** per officer | 2026-06-30 |
| D-009 | Cache strategy | **Enable Hazelcast Community** (already configured) | 2026-06-30 |

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
