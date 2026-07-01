# CMS 2.0 — Security & Enhancement Plan

> Phase breakdown with live status. Updated as work progresses.

---

## Git Strategy

| Branch | Purpose |
|--------|---------|
| `master` | Production baseline — never force-pushed |
| `security/phase-1-otp-captcha` | Phase 1 work |
| `security/phase-2-anti-automation` | Phase 2 work |
| `security/phase-3-pii-encryption` | Phase 3 work |
| `feature/phase-4-i18n` | Phase 4 work |
| `feature/phase-5-performance` | Phase 5 work |
| `feature/phase-6-responsive` | Phase 6 work |
| `feature/phase-7-geo-notice` | Phase 7 work |
| `audit/phase-8-perf-audit` | Phase 8 work |
| `feature/phase-9-tat-timer` | Phase 9 work |
| `feature/phase-10-officer-dashboard` | Phase 10 work |
| `feature/phase-11-similar-cases` | Phase 11 work |
| `audit/phase-12-wcag` | Phase 12 work |
| `audit/phase-13-adversarial` | Phase 13 work |

Each phase branch is merged to master on completion and tagged `phase-N-complete`.

---

## Phase Status

| Phase | Title | Status | Merge Bucket | Tag |
|-------|-------|--------|--------------|-----|
| 0 | Discovery & Baseline | ✅ Complete | N/A (docs only) | `phase-0-complete` |
| 1 | OTP, CAPTCHA, Cooloff, Email Fallback | 🔲 Not started | `security/phase-1-otp-captcha` | — |
| 2 | Anti-Automation / Bot Defense | 🔲 Not started | `security/phase-2-anti-automation` | — |
| 3 | PII Field-Level Encryption | 🔲 Not started | `security/phase-3-pii-encryption` | — |
| 4 | Multi-Language (10 languages) | 🔲 Not started | `feature/phase-4-i18n` | — |
| 5 | Asset Performance | 🔲 Not started | `feature/phase-5-performance` | — |
| 6 | Mobile-Responsive | 🔲 Not started | `feature/phase-6-responsive` | — |
| 7 | Geo-Location Notice | 🔲 Not started | `feature/phase-7-geo-notice` | — |
| 8 | Performance Audit | 🔲 Not started | `audit/phase-8-perf-audit` | — |
| 9 | TAT Timer | 🔲 Not started | `feature/phase-9-tat-timer` | — |
| 10 | Officer Dashboard | 🔲 Not started | `feature/phase-10-officer-dashboard` | — |
| 11 | Similar-Cases Widget | 🔲 Not started | `feature/phase-11-similar-cases` | — |
| 12 | WCAG 2.1 AA Audit | 🔲 Not started | `audit/phase-12-wcag` | — |
| 13 | Adversarial Verification | 🔲 Not started | `audit/phase-13-adversarial` | — |

---

## Phase Dependencies

```
Phase 0 ──► Phase 1 ──► Phase 2 ──► Phase 3 ──► Phase 4 ──► Phase 5 ──► Phase 6 ──► Phase 7 ──► Phase 8
                                                                                                      │
Phase 9 ◄──────────────────────────────────────────────────────────────────────────────────────────────┘
  │
  ▼
Phase 10 ──► Phase 11 ──► Phase 12 ──► Phase 13
```

- Phases 1-8: Citizen-facing, mostly frontend + backend security
- Phases 9-13: Officer-facing + verification, largely independent codebase areas
- Phase 8 must come AFTER 1-7 (it measures their impact)
- Phase 12 is a sweep of ALL prior phases
- Phase 13 is adversarial verification of everything

---

## Risk Flags

| Risk | Phase | Mitigation |
|------|-------|------------|
| No migration tooling (Flyway/Liquibase) | All | Use JPA `ddl-auto: update` (existing pattern) for new tables; document schema changes in AUDIT.md |
| Citizen OTP is entirely client-side mocked | 1 | Must build real server-side OTP generation + verification from scratch |
| Rate limiter is in-memory only | 1 | Store cooloff/attempts in MySQL (centralized), not Hazelcast (disabled) or in-memory |
| No frontend test runner | All | Add Jest or use the existing spec patterns from `cms-frontend` |
| Hazelcast configured but cache disabled (`spring.cache.type: none`) | 4, 10 | Enable or replace — decision needed |
| OpenSearch already in stack | 11 | Use it for similar-cases (no new infra needed) |
| Dual DB (MySQL + Oracle) | 9 | TAT timer data sources span both — SLA in Oracle, complaints in MySQL |
| No Spring Security on cms-backend | 1, 3 | Must add or build custom auth filter; check if adding Spring Security breaks existing endpoints |

---

## Decisions to Confirm (Stop-and-Ask Points)

See DECISIONS.md for full rationale. These require Operator approval before proceeding:

1. **i18n schema**: Normalized key/language table (recommended) vs. wide column-per-language table
2. **TAT clock basis**: Wall-clock vs. business-hours/working-days — and reuse of existing SLA_DUE_DATE
3. **Mobile detection**: Responsive CSS (recommended) vs. server-side UA detection
4. **Geo-IP data source**: Local/offline DB (recommended) vs. third-party API
5. **Similar-cases engine**: OpenSearch `more_like_this` (recommended, already in stack) vs. Oracle Text
6. **Citizen email verification**: How is email currently sourced/verified before using as OTP fallback?
7. **PII at-rest encryption**: Whether Oracle TDE/MySQL column encryption needed beyond in-transit
8. **Dashboard refresh enforcement**: Server-side cache (recommended) vs. UI-only hidden button
9. **Cache enablement**: Re-enable Hazelcast (`spring.cache.type: hazelcast`) or use another approach

---

## Merge Bucket Strategy (Revert Safety)

Each phase produces a single merge commit to `master`. If any phase needs reversion:

```bash
# Revert Phase N entirely:
git revert --no-commit <phase-N-merge-commit>
git commit -m "revert: Phase N - <reason>"
```

Within a phase, commits are small and logical (feature-by-feature), so partial reverts are also possible using the branch history.
