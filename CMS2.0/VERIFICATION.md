# CMS 2.0 — Verification Log

> What was tested per phase, results, and the final adversarial report.

---

## Phase 0 — Baseline Metrics

### Frontend Build (2026-06-30)
| Metric | Value |
|--------|-------|
| Angular version | 21.2.8 |
| Production build | ✅ SUCCESS |
| Total JS bundle size | 2.44 MB (57 lazy-loaded chunks) |
| Main chunk | 13.4 KB (`main-HP2VNK2B.js`) |
| Largest chunk | 411 KB (`chunk-L42MENPO.js` — likely jspdf/canvg) |
| Total dist size | ~11 MB (includes assets) |
| Output hashing | Enabled (all) |
| Budget | warning: 500kB initial, error: 1MB initial |
| CommonJS warnings | jspdf, html2canvas, canvg (optimization bailouts) |
| Test runner | None configured (no Karma/Jest in package.json) |
| Spec files in cms-portal-frontend | 0 |

### Backend Tests (2026-06-30)
| Metric | Value |
|--------|-------|
| Test files | 10 (controllers + services) |
| Test framework | JUnit 5 + Spring Boot Test + Mockito |
| Tests run | 140 |
| Passed | 124 |
| Failed | 0 |
| Errors | 16 (pre-existing) |
| Pre-existing errors | `FileUploadControllerTest` (4 — context load failure), `ComplaintServiceTest` (4 — NPE on routingService null) |
| Note | These errors pre-date this work; caused by missing mock for `ComplaintRoutingService` |

### Lighthouse/Performance Baseline
| Metric | Value |
|--------|-------|
| Lighthouse performance score | TBD (requires running app) |
| First Contentful Paint | TBD |
| Largest Contentful Paint | TBD |
| Time to Interactive | TBD |

> Lighthouse baseline will be captured when the app is served locally during Phase 5/8 work.

---

## Phase 1 — OTP/CAPTCHA/Cooloff
| Test | Result | Date |
|------|--------|------|
| OTP generation (secure random, 6 digits) | ✅ Pass | 2026-06-30 |
| OTP hashed storage (SHA-256, never plaintext) | ✅ Pass | 2026-06-30 |
| OTP verify correct code | ✅ Pass | 2026-06-30 |
| OTP verify incorrect code → INVALID | ✅ Pass | 2026-06-30 |
| OTP max attempts (3) → locks out | ✅ Pass | 2026-06-30 |
| OTP expired → EXPIRED_OR_NOT_FOUND | ✅ Pass | 2026-06-30 |
| Rate limit by mobile (5/hour) | ✅ Pass | 2026-06-30 |
| CAPTCHA image generation (PNG base64) | ✅ Pass | 2026-06-30 |
| CAPTCHA math alternative | ✅ Pass | 2026-06-30 |
| CAPTCHA verify correct (case-insensitive) | ✅ Pass | 2026-06-30 |
| CAPTCHA reject incorrect | ✅ Pass | 2026-06-30 |
| CAPTCHA reject expired/used token | ✅ Pass | 2026-06-30 |
| CAPTCHA reject null inputs | ✅ Pass | 2026-06-30 |
| Cooloff inactive when none exists | ✅ Pass | 2026-06-30 |
| Cooloff detected by IP/fingerprint | ✅ Pass | 2026-06-30 |
| Cooloff detected by mobile number | ✅ Pass | 2026-06-30 |
| Cooloff first failure = 30s | ✅ Pass | 2026-06-30 |
| Cooloff escalation (30→60→120→300→600) | ✅ Pass | 2026-06-30 |
| Cooloff caps at 600s maximum | ✅ Pass | 2026-06-30 |
| Cooloff resets after 60 min inactivity | ✅ Pass | 2026-06-30 |
| Cooloff cleared on successful login | ✅ Pass | 2026-06-30 |
| Angular build succeeds with new components | ✅ Pass | 2026-06-30 |
| Backend compiles cleanly | ✅ Pass | 2026-06-30 |
| Total new tests: 24, all pass | ✅ Pass | 2026-06-30 |

## Phase 2 — Anti-Automation
| Test | Result | Date |
|------|--------|------|
| Honeypot header filled → instant block (429) | ✅ Pass | 2026-06-30 |
| Honeypot absent → request passes | ✅ Pass | 2026-06-30 |
| Form submitted in <3s → flag + require CAPTCHA | ✅ Pass | 2026-06-30 |
| Form submitted in normal time → pass | ✅ Pass | 2026-06-30 |
| Missing User-Agent → flag | ✅ Pass | 2026-06-30 |
| Known automation tool UA → flag | ✅ Pass | 2026-06-30 |
| Velocity >30 req/min → hard block | ✅ Pass | 2026-06-30 |
| Non-protected paths unaffected | ✅ Pass | 2026-06-30 |
| Total new tests: 8, all pass | ✅ Pass | 2026-06-30 |

## Phase 3 — PII Encryption
| Test | Result | Date |
|------|--------|------|
| Key derivation produces 256-bit (32 bytes) | ✅ Pass | 2026-06-30 |
| Same session → same key (deterministic) | ✅ Pass | 2026-06-30 |
| Different sessions → different keys | ✅ Pass | 2026-06-30 |
| AES-256-GCM encrypt/decrypt round-trip | ✅ Pass | 2026-06-30 |
| Cross-session isolation (A cannot decrypt B) | ✅ Pass | 2026-06-30 |
| Tampered ciphertext rejected (AEAD tag fail) | ✅ Pass | 2026-06-30 |
| Frontend + backend build cleanly | ✅ Pass | 2026-06-30 |
| Total new tests: 6, all pass | ✅ Pass | 2026-06-30 |

## Phase 4 — Multi-Language
| Test | Result | Date |
|------|--------|------|
| Normalized schema (TranslationKey + Translation entities) | ✅ Pass | 2026-06-30 |
| SupportedLocale enum (10 languages, Urdu RTL) | ✅ Pass | 2026-06-30 |
| TranslationService returns translations by locale | ✅ Pass | 2026-06-30 |
| Fallback to English for unsupported locale | ✅ Pass | 2026-06-30 |
| Missing translations filled with defaultValue | ✅ Pass | 2026-06-30 |
| Filter translations by module | ✅ Pass | 2026-06-30 |
| Returns all 10 supported locales with metadata | ✅ Pass | 2026-06-30 |
| Upsert existing translation | ✅ Pass | 2026-06-30 |
| Create new translation for locale | ✅ Pass | 2026-06-30 |
| Reject upsert to non-existent key | ✅ Pass | 2026-06-30 |
| Create new translation key | ✅ Pass | 2026-06-30 |
| Reject duplicate key creation | ✅ Pass | 2026-06-30 |
| Urdu locale identified as RTL | ✅ Pass | 2026-06-30 |
| English locale identified as LTR | ✅ Pass | 2026-06-30 |
| Unsupported locale code defaults to English | ✅ Pass | 2026-06-30 |
| Hazelcast cache configured for translations (30 min TTL) | ✅ Pass | 2026-06-30 |
| REST API GET /api/v1/i18n/locales | ✅ Pass | 2026-06-30 |
| REST API GET /api/v1/i18n/translations/{locale} | ✅ Pass | 2026-06-30 |
| Angular TranslatePipe and TranslationService | ✅ Pass | 2026-06-30 |
| Language switcher with server-driven locale list | ✅ Pass | 2026-06-30 |
| RTL direction applied for Urdu | ✅ Pass | 2026-06-30 |
| Seed data: 90+ keys, all 10 languages seeded | ✅ Pass | 2026-06-30 |
| Angular production build succeeds | ✅ Pass | 2026-06-30 |
| Total new tests: 13, all pass | ✅ Pass | 2026-06-30 |

## Phase 5 — Asset Performance
| Test | Result | Date |
|------|--------|------|
| Image lazy loading (loading="lazy") on below-fold images | ✅ Pass | 2026-06-30 |
| Hero image fetchpriority="high" for LCP optimization | ✅ Pass | 2026-06-30 |
| decoding="async" on all images | ✅ Pass | 2026-06-30 |
| Preconnect hint to API origin | ✅ Pass | 2026-06-30 |
| DNS-prefetch fallback for older browsers | ✅ Pass | 2026-06-30 |
| Backend security headers (X-Content-Type-Options, X-Frame-Options, Referrer-Policy) | ✅ Pass | 2026-06-30 |
| Server-side gzip compression enabled (application.yml) | ✅ Pass | 2026-06-30 |
| Output hashing enabled for cache-busting | ✅ Pass | 2026-06-30 |
| Angular production build succeeds | ✅ Pass | 2026-06-30 |
| Backend compiles cleanly | ✅ Pass | 2026-06-30 |

## Phase 6 — Mobile-Responsive
| Test | Result | Date |
|------|--------|------|
| Viewport meta tag present in index.html | ✅ Pass | 2026-06-30 |
| 3 breakpoints: 1024px (tablet), 768px (mobile), 480px (small phone) | ✅ Pass | 2026-06-30 |
| Layout: hamburger nav, stacked session bar, responsive footer | ✅ Pass | 2026-06-30 |
| Home: hero stacks vertically, hidden decorative images on mobile | ✅ Pass | 2026-06-30 |
| Home: complaint types, scheme grid, ways grid collapse to 1-col | ✅ Pass | 2026-06-30 |
| Home: stats banner responsive on mobile | ✅ Pass | 2026-06-30 |
| Complaint form: already has 768px breakpoint (form-row stacks) | ✅ Pass | 2026-06-30 |
| CSS-only responsive (per D-003 decision: no UA detection) | ✅ Pass | 2026-06-30 |
| Angular production build succeeds | ✅ Pass | 2026-06-30 |

## Phase 7 — Geo-Location
| Test | Result | Date |
|------|--------|------|
| GeoLocationService graceful when disabled | ✅ Pass | 2026-06-30 |
| isAvailable() returns false when no DB loaded | ✅ Pass | 2026-06-30 |
| Loopback/private IPs return empty (no external call) | ✅ Pass | 2026-06-30 |
| GeoResult.toMap() contains expected keys | ✅ Pass | 2026-06-30 |
| GeoResult handles null fields gracefully | ✅ Pass | 2026-06-30 |
| REST API GET /api/v1/geo/locate | ✅ Pass | 2026-06-30 |
| REST API GET /api/v1/geo/jurisdiction (maps state → Ombudsman office) | ✅ Pass | 2026-06-30 |
| MaxMind GeoLite2 local DB integration (per D-004) | ✅ Pass | 2026-06-30 |
| Configuration: cms.geo.enabled + cms.geo.maxmind-db-path | ✅ Pass | 2026-06-30 |
| Angular GeoLocationService (signal-based, lazy detect) | ✅ Pass | 2026-06-30 |
| Frontend + backend build succeeds | ✅ Pass | 2026-06-30 |
| Total new tests: 5, all pass | ✅ Pass | 2026-06-30 |

## Phase 8 — Performance Audit
| Test | Result | Date |
|------|--------|------|
| Initial bundle: 391 KB raw / 107 KB transferred (under 500 KB budget) | ✅ Pass | 2026-06-30 |
| Main chunk: 13.5 KB (minimal bootstrap) | ✅ Pass | 2026-06-30 |
| Lazy chunks: 57 total (code-split per route) | ✅ Pass | 2026-06-30 |
| Largest lazy chunk: 411 KB (jspdf — only loaded for PDF export) | ✅ Pass | 2026-06-30 |
| Output hashing enabled for cache-busting | ✅ Pass | 2026-06-30 |
| Server gzip compression enabled (min 1024 bytes) | ✅ Pass | 2026-06-30 |
| Image lazy loading on below-fold images (Phase 5) | ✅ Pass | 2026-06-30 |
| Preconnect/dns-prefetch hints (Phase 5) | ✅ Pass | 2026-06-30 |
| Hazelcast cache for translations (30 min TTL, Phase 4) | ✅ Pass | 2026-06-30 |
| HTTP Cache-Control on i18n endpoints (30 min public) | ✅ Pass | 2026-06-30 |
| No render-blocking resources (system font stack, inline critical CSS) | ✅ Pass | 2026-06-30 |
| Security headers do not add performance overhead | ✅ Pass | 2026-06-30 |
| HikariCP connection pool (50 default, 100 prod) | ✅ Pass | 2026-06-30 |
| Tomcat thread pool: 400 max (800 prod) | ✅ Pass | 2026-06-30 |
| Lighthouse audit: TBD (requires running app with network traffic) | ⏳ Deferred | 2026-06-30 |

## Phase 9 — TAT Timer
| Test | Result | Date |
|------|--------|------|
| Weekday identified as business day | ✅ Pass | 2026-07-01 |
| Saturday not a business day | ✅ Pass | 2026-07-01 |
| Sunday not a business day | ✅ Pass | 2026-07-01 |
| Holiday not a business day (DB-driven) | ✅ Pass | 2026-07-01 |
| 10 AM within business hours | ✅ Pass | 2026-07-01 |
| 7 AM not within business hours | ✅ Pass | 2026-07-01 |
| 6 PM not within business hours (end exclusive) | ✅ Pass | 2026-07-01 |
| Due date calculation spans days correctly | ✅ Pass | 2026-07-01 |
| Due date skips weekends | ✅ Pass | 2026-07-01 |
| Elapsed hours same-day calculation | ✅ Pass | 2026-07-01 |
| Elapsed hours skip weekends | ✅ Pass | 2026-07-01 |
| Business hours per day = 9 (9AM-6PM) | ✅ Pass | 2026-07-01 |
| Holiday entity with year index | ✅ Pass | 2026-07-01 |
| TAT REST API /api/v1/tat/complaint/{number} | ✅ Pass | 2026-07-01 |
| Holiday CRUD endpoints (GET/POST/DELETE) | ✅ Pass | 2026-07-01 |
| Angular TatService | ✅ Pass | 2026-07-01 |
| Configuration: cms.tat.* (hours, timezone) | ✅ Pass | 2026-07-01 |
| Frontend + backend build succeeds | ✅ Pass | 2026-07-01 |
| Total new tests: 12, all pass | ✅ Pass | 2026-07-01 |

## Phase 10 — Officer Dashboard
| Test | Result | Date |
|------|--------|------|
| DashboardService with server-side Hazelcast cache (2 min TTL) | ✅ Pass | 2026-07-01 |
| GET /api/v1/dashboard/summary (cached officer stats) | ✅ Pass | 2026-07-01 |
| GET /api/v1/dashboard/summary/{department} | ✅ Pass | 2026-07-01 |
| POST /api/v1/dashboard/refresh (cache eviction, per D-008) | ✅ Pass | 2026-07-01 |
| Cache-Control: max-age=120, private on responses | ✅ Pass | 2026-07-01 |
| Resolution rate calculation | ✅ Pass | 2026-07-01 |
| Backend compiles cleanly | ✅ Pass | 2026-07-01 |

## Phase 11 — Similar-Cases
| Test | Result | Date |
|------|--------|------|
| — | Not started | — |

## Phase 12 — WCAG 2.1 AA
| Test | Result | Date |
|------|--------|------|
| — | Not started | — |

## Phase 13 — Adversarial Verification
| Test | Result | Date |
|------|--------|------|
| — | Not started | — |
