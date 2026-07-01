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
| — | Not started | — |

## Phase 2 — Anti-Automation
| Test | Result | Date |
|------|--------|------|
| — | Not started | — |

## Phase 3 — PII Encryption
| Test | Result | Date |
|------|--------|------|
| — | Not started | — |

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
