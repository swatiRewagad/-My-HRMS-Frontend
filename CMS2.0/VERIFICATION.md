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
| — | Not started | — |

## Phase 5 — Asset Performance
| Test | Result | Date |
|------|--------|------|
| — | Not started | — |

## Phase 6 — Mobile-Responsive
| Test | Result | Date |
|------|--------|------|
| — | Not started | — |

## Phase 7 — Geo-Location
| Test | Result | Date |
|------|--------|------|
| — | Not started | — |

## Phase 8 — Performance Audit
| Test | Result | Date |
|------|--------|------|
| — | Not started | — |

## Phase 9 — TAT Timer
| Test | Result | Date |
|------|--------|------|
| — | Not started | — |

## Phase 10 — Officer Dashboard
| Test | Result | Date |
|------|--------|------|
| — | Not started | — |

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
