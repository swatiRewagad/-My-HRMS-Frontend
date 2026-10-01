#!/usr/bin/env python3
"""Build the self-contained pass-3 HTML report.

Reads the Playwright line-reporter outputs in C:/tmp/e2e-final and emits
D:/screenShots/ui-homog-pass3/index.html with embedded CSS and RELATIVE image paths so the folder zips.

Reuses ui_homog_report.py's CSS rather than restyling, per the prompt's instruction to EXTEND the
committed generator instead of writing a second one.

Usage:
  python scripts/qa/ui_homog_pass3_report.py
"""
from __future__ import annotations

import html
import re
from pathlib import Path

from ui_homog_report import CSS, SUMMARY_RE  # the committed generator is the styling authority

ROOT = Path("D:/screenShots/ui-homog-pass3")
SHOTS = ROOT / "shots"
TMP = Path("C:/tmp/e2e-final")

DIRS = ["public", "admin", "i18n", "cepc", "ui-homogenisation", "aa", "rbio", "re-portal"]

# Baseline stated in the pass-3 brief, measured 2026-09-22 at HEAD f180732.
# (passed, failed, skipped)
BASELINE = {
    "public": (111, 0, 0),
    "admin": (106, 0, 9),
    "cepc": (28, 1, 6),
    "rbio": (182, 3, 11),
    "aa": (159, 19, 23),
    "re-portal": (29, 2, 39),
    "i18n": (9, 0, 0),
    "ui-homogenisation": (35, 0, 0),
}

# Files this pass added, so the report can separate "my additions" from pre-existing coverage.
NEW_SPECS = {
    "public": [
        ("complaint-categories.spec.ts", 7, "category master is authoritative + localised in 11 locales"),
        ("seo.spec.ts", 16, "per-route meta, hreflang, generated sitemap, robots coverage, JSON-LD"),
        ("seo-headings.spec.ts", 11, "one h1 per indexable page, heading order, alt text"),
    ],
    "ui-homogenisation": [
        ("upload-limits.spec.ts", 3, "no source file may hardcode a 2 MB limit"),
        ("signal-filters.spec.ts", 4, "dead computed() filters, driven BY TYPING"),
    ],
}


def parse_counts(path: Path):
    """Returns (passed, failed, skipped, flaky, did_not_run) or None."""
    if not path.exists():
        return None
    text = path.read_text(encoding="utf-8", errors="replace")
    counts = {"passed": 0, "failed": 0, "skipped": 0, "flaky": 0}
    for value, label in SUMMARY_RE.findall(text):
        counts[label] = int(value)
    dnr = re.search(r"(\d+)\s+did not run", text)
    if not any(counts.values()):
        return None
    return (counts["passed"], counts["failed"], counts["skipped"],
            counts["flaky"], int(dnr.group(1)) if dnr else 0)


def failing_tests(path: Path) -> list[str]:
    if not path.exists():
        return []
    text = path.read_text(encoding="utf-8", errors="replace")
    # The failure list is the block after the summary line, so take the last occurrences.
    return re.findall(r"^\s+\[chromium\] › (.+?)\s*$", text, re.MULTILINE)


def esc(value) -> str:
    return html.escape(str(value))


# ── What was delivered, with the evidence for each claim ─────────────────────────────────────
DELIVERED = [
    ("§4.1", "OTP authentication bypass CLOSED",
     "<code>send-otp</code> returned the OTP in its own response body in every deployed environment, "
     "because the ConfigMap set <code>SPRING_PROFILES_ACTIVE=openshift</code> and no such profile "
     "existed — so every <code>prod</code> override was dead code. Three layers now: safe default, a "
     "real <code>openshift</code> profile block, explicit ConfigMap value, plus "
     "<code>OtpExposureGuard</code> which REFUSES TO START if the flag is true outside dev-local.",
     "Booted with the flag true under <code>openshift</code> → <b>REFUSING TO START</b>. With it false → "
     "app starts and <code>send-otp</code> returns <b>no devOtp</b>. 10 tests; 2 mutations caught."),

    ("§1.2", "<code>block_sub_judice</code> ARMED",
     "The mechanism, exception, 409 mapping, translated key and ground-persistence ALL already existed — "
     "only the default value was missing. Armed via <code>DEFAULT_BLOCK_SUB_JUDICE</code> + V65/V63.",
     "Sub-judice tests 4 → 6. Flipping the default back fails exactly 2 of them. Migration replay "
     "proven across 4 cases (fresh / idempotent / operator-override-survives / row-absent)."),

    ("§1.3", "Category labels localised in 11 locales",
     "The described defect was ALREADY FIXED (see divergences). Delivered the real gap instead: a "
     "<code>LABEL_KEY</code> column (V66/V64) and <code>category.*</code> keys in all eleven locales, "
     "with Punjabi seeded for the first time.",
     "public suite <b>111 → 118</b>, 0 failures. Gurmukhi labels verified rendering in the browser."),

    ("§1.4", "Every hardcoded 2 MB upload limit removed",
     "The brief listed 3 client gates; there were <b>9</b>, plus <b>9</b> user-visible “2MB” labels and "
     "<code>file-validator.ts</code> (<code>MAX_FILE_SIZE_MB = 2</code>) used by the citizen complaint "
     "form itself. Seeded message text converted to a <code>{{size}}</code> placeholder in 10 locales "
     "(V67/V65).",
     "Direct API upload: 4 MB → <b>HTTP 200</b>, 6 MB → <b>refused “5MB”</b>. Source-scan guard test "
     "proven to fail when a literal is reintroduced."),

    ("§1.7", "Dead <code>computed()</code> filters fixed",
     "<b>5</b> components, not the 4 reported. <code>cepc-dashboard</code> had the identical defect and "
     "had been recorded as already fixed; its column dialog turned out to be UNREACHABLE dead code after "
     "the pass-2 migration (nothing set <code>showColumnConfig</code> true), so it was deleted.",
     "4 typing-driven tests. <b>3 mutations caught</b> — reverting either component to a plain-field "
     "read fails them."),

    ("§1.5", "Shared grid taught server-side paging",
     "<code>totalCount</code> + <code>pageChange</code>, an <code>emptyState</code> template slot, "
     "<code>retryable</code>/<code>retry</code>, and <code>rowClass</code> (for RE's deadline tint). "
     "<code>ui.grid.refined_count</code> added in 11 locales so a page-local filter is never reported "
     "against a server-wide total.",
     "ui-homogenisation <b>35 → 42</b>. Migrations NOT done — see “deliberately not done”."),

    ("Task 3", "Offline static analysis installed and baselined",
     "ESLint + angular-eslint + <code>eslint-plugin-sonarjs</code> (<code>npm run lint</code> could not "
     "work at all before: no dependency, no config). SpotBugs + find-sec-bugs, PMD and JaCoCo wired into "
     "Maven, none bound to a lifecycle phase except JaCoCo's agent.",
     "ESLint <b>1715 problems (202 errors)</b>; SpotBugs <b>1437</b>; PMD <b>93</b>; JaCoCo "
     "<b>14.3% line / 21.1% branch</b> — measured for the first time ever."),

    ("Task 2", "Public portal made findable",
     "Per-route title/description/canonical/OG/Twitter from translation keys, hreflang ×11 + x-default, "
     "<code>&lt;html lang&gt;</code>, a sitemap GENERATED from the route table, a hardened robots.txt, and "
     "four JSON-LD types. Fails closed: a route absent from the table gets <code>noindex</code>.",
     "27 new SEO tests, stable over 3 consecutive runs. Found and fixed a real defect: "
     "<code>/public/track</code> had <b>no h1 at all</b>."),
]

SECURITY_FIXES = [
    ("Authentication bypass", "CRITICAL",
     "<code>send-otp</code> returned the OTP in its response body in any deployed environment — anyone "
     "could authenticate as any mobile number.", "FIXED + proven by boot test"),
    ("Timing attack on OTP and CAPTCHA", "HIGH",
     "<code>OtpService.verifyOtp</code> and <code>CaptchaService.verifyCaptcha</code> compared hashes "
     "with <code>String.equals</code>, whose early return leaks the shared-prefix length. Found by "
     "find-sec-bugs (<code>UNSAFE_HASH_EQUALS</code>).", "FIXED — <code>MessageDigest.isEqual</code>"),
    ("Tokenised upload link was crawlable", "HIGH",
     "<code>/public/upload/:token</code> was absent from robots.txt. The token IS the credential, so an "
     "indexed URL is a working upload link for someone else's complaint.", "FIXED + guard test"),
    ("Sanitiser bypass on the failure path", "MEDIUM",
     "<code>draft-assessment</code> fell back to <code>bypassSecurityTrustResourceUrl(att.url)</code> — "
     "server-supplied data — precisely when the fetch had just failed.", "FIXED — reports the error"),
    ("Sanitiser bypass on sessionStorage", "MEDIUM",
     "A raw <code>sessionStorage</code> value was passed to "
     "<code>bypassSecurityTrustResourceUrl</code>; any script on the origin can write it.",
     "FIXED — scheme pinned to <code>blob:</code>"),
    ("Per-complaint URLs indexable", "MEDIUM",
     "No route had a <code>robots</code> meta tag, so a complaint-detail URL could be indexed, "
     "confirming that a given reference number exists.", "FIXED — fails closed to noindex"),
]

DIVERGENCES = [
    ("§1.3 <code>category_master</code> had <b>0 rows</b>, not 14",
     "The brief described 14 rows of E2E pollution driving the citizen category list. Re-measured: the "
     "table is empty and <code>public/file-complaint</code> ALREADY read the authoritative "
     "<code>/api/categories</code>. The purge migration was skipped by your ruling — there was nothing "
     "to purge."),
    ("§1.4 undercounted the 2 MB gates by 3×",
     "3 named, <b>9</b> found — including <code>file-validator.ts</code>, used by the citizen complaint "
     "form. Plus 9 user-visible “2MB” labels the brief did not mention at all."),
    ("§1.7 undercounted the dead filters",
     "4 named, <b>5</b> found. <code>cepc-dashboard</code> was recorded as fixed in pass 2 but its "
     "filter had been orphaned rather than repaired."),
    ("<code>aa.upload.error_file_too_large</code> already said 5 MB in English",
     "…but all nine translations still said 2. The limit a citizen was told depended on their language — "
     "invisible to anyone testing in English, and worse than being uniformly wrong."),
    ("The <code>re-portal</code> baseline is not reproducible",
     "Two runs of UNTOUCHED code gave 32P/35S and 36P/1F/33S/34-did-not-run. The brief's stated "
     "29P/2F/39S matches neither. This is why the grid migrations were not attempted."),
]

NOT_DONE = [
    ("§1.5.3–4 — migrate re-portal / aa / rbio onto the shared grid",
     "Your ruling, after I raised it. §1.5.5 says to STOP and report rather than ship a regression. Two "
     "blockers: <b>9 existing E2E assertions</b> bind the old markup selectors "
     "(<code>.complaints-table</code>, <code>.complaint-row</code>, <code>.complaint-number</code>) in "
     "other sessions' specs, and the re-portal baseline moves by ±4 tests between runs, so no migration "
     "could be honestly proven regression-free. <b>The grid capability itself shipped and is tested.</b>"),
    ("§1.6 — shared detail frame + <code>app-shell</code> rollout",
     "Not attempted. Deprioritised by your ruling in favour of Task 3 then Task 2. Still 6 separate "
     "detail components and <code>app-shell</code> in 3 of 48 templates."),
    ("§2.2.1 — PRERENDERING",
     "Attempted and REVERTED, with evidence. Installed <code>@angular/ssr</code> + "
     "<code>platform-server</code>, wrote the server entry, wired <code>prerender.routesFile</code> — the "
     "build <b>hangs and times out</b>. Root cause: services make HTTP calls at construction "
     "(<code>TranslationService</code> fetches its locale bundle from its constructor), and with no "
     "server reachable the request never resolves, so route extraction never finishes. <b>35 files</b> "
     "touch browser-only globals. The per-route meta, sitemap, hreflang and JSON-LD all shipped without "
     "it; a crawler still receives an empty <code>&lt;app-root&gt;</code> on first byte."),
    ("§1.1 / §2.7 — screenshots and Lighthouse",
     "Not delivered. The capture matrix (every screen at 1280 and 1440 plus a <code>pa</code> grid) and "
     "the Lighthouse before/after both needed the time that went into the security fixes you asked me to "
     "pull forward. <b>This remains pass 2's gap and is now also pass 3's.</b> Stated plainly rather "
     "than partially faked."),
]

STILL_OPEN = [
    ("<code>ddl-auto: update</code> in the deployed profile", "HIGH",
     "The new <code>openshift</code> block deliberately does NOT set <code>validate</code>. It is coupled "
     "to the migrations: a clean-DB replay applies 15 of 60 migrations and creates 55 of 113 tables, so "
     "<code>validate</code> would fail to boot. Hibernate mutating a production schema at boot remains a "
     "real go-live risk. Fix the migrations first."),
    ("<code>KEYCLOAK_ADMIN_PASSWORD</code> defaults to <code>admin</code>", "HIGH",
     "<code>application.yml:79</code>. Reported, not changed, per §4.2."),
    ("CORS defaults to localhost only", "MEDIUM",
     "A real UAT host breaks unless <code>CMS_CORS_ORIGINS</code> is set. Note <code>dev-local</code> "
     "hardcodes its allowlist, so the variable is silently ignored there."),
    ("25 <code>permitAll</code> matchers in SecurityConfig", "MEDIUM",
     "Needs re-audit before public exposure. The GET-only pattern used for "
     "<code>/api/v1/config/upload-limits</code> is the one to follow."),
    ("14.3% line coverage", "MEDIUM",
     "Now measured rather than unknown. No assertion-free tests were written to inflate it."),
    ("461 <code>: any</code> and 533 a11y warnings", "LOW",
     "ESLint severity is <code>warn</code> for these so the command is usable from day one. Tighten as "
     "the debt is paid down."),
    ("<code>npm test</code> cannot run", "LOW",
     "There is no <code>test</code> target in <code>angular.json</code> at all, and 1 spec file in "
     "<code>src/</code>. The real safety net is the Playwright suite."),
    ("<code>package-lock.json</code> is gitignored", "LOW",
     "<code>CMS2.0_Redzone/.gitignore:30</code>. CI cannot reproduce the exact dependency tree of the "
     "tooling just installed. Pre-existing policy, flagged not changed."),
]

SIGN_OFF = [
    ("Canonical public domain", "<code>https://cms.rbi.org.in</code> is asserted in robots.txt, the "
     "sitemap and every canonical tag. Confirmed by you for this pass; needs RBI confirmation before "
     "go-live. The origin is overridable at runtime so it is not compiled in."),
    ("Staff modules on a separate host", "Flagged for RBI sign-off per your ruling, not decided here. "
     "Today they are same-host, excluded by robots.txt and enforced by route guards + "
     "<code>@PreAuthorize</code>."),
    ("Retiring <code>category_master</code>", "PROPOSED, not executed. It holds 0 rows and no caller "
     "reads it for the citizen list. A schema drop is not a UI batch's call."),
    ("<code>block_sub_judice</code> wording", "The refusal message is English-only by the existing "
     "ruling that a legal ground needs the same sign-off as a clause label."),
    ("JSON-LD contact details", "<code>GovernmentOrganization</code> publishes the Scheme's toll-free "
     "number (14448) and <code>rbi.org.in</code> — both already on the portal's own pages. Confirm "
     "before go-live."),
]


def build() -> str:
    parts: list[str] = []
    a = parts.append

    a("<!DOCTYPE html><html lang='en'><head><meta charset='utf-8'>")
    a("<meta name='viewport' content='width=device-width,initial-scale=1'>")
    a("<title>CMS 2.0 — Pass 3 Report</title>")
    a(f"<style>{CSS}</style></head><body>")

    a("<header class='top'><h1>CMS 2.0 — UI Pass 3</h1>")
    a("<p>Statutory guards, upload limits, dead filters, offline static analysis and public-portal SEO."
      " Branch <code>CMS_21092026</code>, from HEAD <code>f180732</code>. Generated 2026-09-23.</p>")
    a("</header><main>")

    # ── Verdict ──────────────────────────────────────────────────────────────────────────────
    a("<div class='verdict'><span class='tag'>PARTIAL — security and correctness done, "
      "evidence pass not</span>")
    a("<p>Every correctness and security item in the brief is delivered and independently verified. "
      "Three items are NOT done and are listed with their reasons: the grid migrations (your ruling), "
      "the shared detail frame (deprioritised by your ruling), and prerendering (attempted, blocked by "
      "an architectural constraint, reverted). <b>The screenshot matrix and Lighthouse measurement are "
      "again not delivered</b> — that was pass 2's biggest gap and it is now also pass 3's.</p></div>")

    # ── Delivered ────────────────────────────────────────────────────────────────────────────
    a("<h2>1. Delivered, with evidence</h2>")
    a("<table><tr><th style='width:56px'>§</th><th style='width:210px'>What</th><th>Detail</th>"
      "<th style='width:300px'>Evidence</th></tr>")
    for ref, what, detail, evidence in DELIVERED:
        a(f"<tr><td><code>{ref}</code></td><td><b>{what}</b></td><td>{detail}</td>"
          f"<td class='ok'>{evidence}</td></tr>")
    a("</table>")

    # ── Security ─────────────────────────────────────────────────────────────────────────────
    a("<h2>2. Security defects found and fixed</h2>")
    a("<div class='note red'>The OTP exposure was a live authentication bypass in every deployed "
      "environment. It was last in the brief's order; I asked and you agreed to pull it forward.</div>")
    a("<table><tr><th style='width:250px'>Defect</th><th style='width:80px'>Severity</th>"
      "<th>Detail</th><th style='width:220px'>Status</th></tr>")
    for name, sev, detail, status in SECURITY_FIXES:
        cls = "bad" if sev in ("CRITICAL", "HIGH") else "warn"
        a(f"<tr><td><b>{name}</b></td><td class='{cls}'>{sev}</td><td>{detail}</td>"
          f"<td class='ok'>{status}</td></tr>")
    a("</table>")

    # ── Test results ─────────────────────────────────────────────────────────────────────────
    a("<h2>3. Test results — passed, failed and skipped as separate numbers</h2>")
    a("<div class='note'>A skip is <b>not</b> a pass. <code>re-portal</code> skips roughly half its "
      "tests by design, and “did not run” means the suite stopped early — both are reported "
      "separately rather than folded into either column.</div>")
    a("<table><tr><th>Directory</th><th>Passed</th><th>Failed</th><th>Skipped</th><th>Flaky</th>"
      "<th>Did not run</th><th>Baseline (P/F/S)</th><th>Verdict</th></tr>")

    tot = [0, 0, 0, 0, 0]
    for d in DIRS:
        # aa2 is the re-run after the overruled 2 MB assertion was updated.
        path = TMP / ("aa2.txt" if d == "aa" and (TMP / "aa2.txt").exists() else f"{d}.txt")
        counts = parse_counts(path)
        base = BASELINE.get(d)
        base_txt = "/".join(str(x) for x in base) if base else "—"
        if not counts:
            a(f"<tr><td><code>{d}</code></td><td colspan='6' class='muted'>not measured</td>"
              f"<td class='muted'>—</td></tr>")
            continue
        p, f_, s, fl, dnr = counts
        for i, v in enumerate((p, f_, s, fl, dnr)):
            tot[i] += v
        if base:
            delta = p - base[0]
            if f_ <= base[1]:
                verdict = f"<span class='ok'>no worse (passed {delta:+d})</span>"
            else:
                verdict = f"<span class='bad'>failures {base[1]} → {f_}</span>"
        else:
            verdict = "<span class='muted'>no baseline</span>"
        a(f"<tr><td><code>{d}</code></td><td class='ok'>{p}</td>"
          f"<td class='{'bad' if f_ else 'ok'}'>{f_}</td><td class='warn'>{s}</td>"
          f"<td class='warn'>{fl or ''}</td><td class='warn'>{dnr or ''}</td>"
          f"<td class='muted'>{base_txt}</td><td>{verdict}</td></tr>")

    a(f"<tr style='background:#f8fafc;font-weight:700'><td>TOTAL</td><td class='ok'>{tot[0]}</td>"
      f"<td class='{'bad' if tot[1] else 'ok'}'>{tot[1]}</td><td class='warn'>{tot[2]}</td>"
      f"<td class='warn'>{tot[3]}</td><td class='warn'>{tot[4]}</td><td colspan='2'></td></tr>")
    a("</table>")

    a("<h3>Backend</h3>")
    a("<table><tr><th>Suite</th><th>Tests</th><th>Failures</th><th>Errors</th><th>Skipped</th>"
      "<th>Baseline</th></tr>")
    a("<tr><td><code>cms-backend</code></td><td class='ok'>1332</td><td class='ok'>0</td>"
      "<td class='ok'>0</td><td class='ok'>0</td><td class='muted'>1310 — the 22 new are mine</td></tr>")
    a("</table>")
    a("<div class='note'><code>mvn -q test</code> suppresses the summary AND nested <code>@Nested</code> "
      "classes report as separate surefire files with the parent showing <code>tests=\"0\"</code>, so "
      "these numbers aggregate <code>target/surefire-reports/TEST-*.xml</code> including the "
      "<code>$Nested</code> files.</div>")

    # ── New tests ────────────────────────────────────────────────────────────────────────────
    a("<h2>4. Tests this pass added</h2>")
    a("<table><tr><th>Directory</th><th>Spec</th><th>Tests</th><th>What it locks down</th></tr>")
    added = 0
    for d, specs in NEW_SPECS.items():
        for name, n, why in specs:
            added += n
            a(f"<tr><td><code>{d}</code></td><td><code>{name}</code></td><td class='ok'>{n}</td>"
              f"<td>{why}</td></tr>")
    a(f"<tr style='background:#f8fafc;font-weight:700'><td colspan='2'>TOTAL NEW E2E</td>"
      f"<td class='ok'>{added}</td><td>plus 22 new backend tests</td></tr>")
    a("</table>")
    a("<div class='note'>Every one of these was <b>mutation-verified</b>: the code was reverted to its "
      "broken state and the test was confirmed to fail, then restored. A test that cannot fail is worse "
      "than no test.</div>")

    # ── Divergences ──────────────────────────────────────────────────────────────────────────
    a("<h2>5. Where the brief's measurements were wrong</h2>")
    a("<div class='note amber'>The brief says to re-verify before relying on it, and not to trust an "
      "earlier report over the code. These are the cases where that mattered.</div>")
    a("<table><tr><th style='width:340px'>Claim</th><th>What I measured</th></tr>")
    for claim, detail in DIVERGENCES:
        a(f"<tr><td><b>{claim}</b></td><td>{detail}</td></tr>")
    a("</table>")

    # ── Not done ─────────────────────────────────────────────────────────────────────────────
    a("<h2>6. Deliberately NOT done, and why</h2>")
    a("<table><tr><th style='width:300px'>Item</th><th>Reason</th></tr>")
    for item, reason in NOT_DONE:
        a(f"<tr><td><b>{item}</b></td><td>{reason}</td></tr>")
    a("</table>")

    # ── Screenshot coverage ──────────────────────────────────────────────────────────────────
    a("<h2>7. Screenshot coverage — the mandatory table</h2>")
    shot_count = len(list(SHOTS.rglob("*.png"))) if SHOTS.exists() else 0
    a(f"<div class='note red'><b>0 of ~18 screens have a before/after pair.</b> Captured this pass: "
      f"<b>{shot_count}</b>.<br><br>"
      "Stated plainly because the brief requires every gap to carry a reason rather than be omitted: "
      "the capture spec was not written. The session time went into the security fixes pulled forward "
      "at your request (§4.1 OTP bypass, timing attacks, sanitiser bypasses), Task 3 tooling, and Task 2 "
      "SEO. <b>This is the second consecutive pass to miss it.</b><br><br>"
      "The ceiling also has not moved: only ~354 of ~790 tests touch <code>page.</code> at all; "
      "<code>e2e/admin/</code> is almost entirely API-level and can never yield an image.</div>")
    a("<table><tr><th>Screen group</th><th>Before</th><th>After</th><th>Reason for the gap</th></tr>")
    for group in ["task grids (crpc, cepc, rbio, aa, re-portal)",
                  "complaint / appeal detail views (6)",
                  "public home, file-complaint, track, FAQ, eligibility wizard",
                  "language switcher open, a toast, one modal",
                  "the same grid in <code>pa</code> (Gurmukhi proof)"]:
        a(f"<tr><td>{group}</td><td class='bad'>0</td><td class='bad'>0</td>"
          f"<td class='muted'>capture spec not written — see above</td></tr>")
    a("</table>")
    a("<div class='note'>Gurmukhi rendering IS proven, just not photographically: "
      "<code>complaint-categories.spec.ts</code> asserts every rendered category option matches "
      "<code>/[&#2560;-&#2687;]/</code> under the <code>pa</code> locale, and fails if the label falls "
      "back to English.</div>")

    # ── Static analysis ──────────────────────────────────────────────────────────────────────
    a("<h2>8. Static analysis — baseline, delta, and what a real server would still catch</h2>")
    a("<table><tr><th>Tool</th><th>Baseline</th><th>After</th><th>Notes</th></tr>")
    a("<tr><td>ESLint + angular-eslint + sonarjs</td><td>1715 (202 err)</td><td>1712 (199 err)</td>"
      "<td>Could not run at all before: no dependency, no config.</td></tr>")
    a("<tr><td>SpotBugs + find-sec-bugs</td><td>1437</td><td class='muted'>not re-baselined</td>"
      "<td>994 “SECURITY” is misleading — 428 are <code>SPRING_ENDPOINT</code> (“this is an endpoint”) "
      "and 403 <code>CRLF_INJECTION_LOGS</code>. The genuinely serious set is small.</td></tr>")
    a("<tr><td>PMD</td><td>93</td><td>90</td><td><code>EmptyCatchBlock</code> 1 → 0; "
      "<code>UnusedPrivateMethod</code> 4 → 2.</td></tr>")
    a("<tr><td>JaCoCo</td><td class='muted'>never measured</td><td>14.3% line / 21.1% branch</td>"
      "<td>421 classes. <code>config</code> at 1.3% is inflated by the huge translation seeders — data, "
      "not logic.</td></tr>")
    a("</table>")
    a("<h3>False positives NOT “fixed”</h3><ul>")
    a("<li><code>SQL_INJECTION_SPRING_JDBC</code> ×3 in <code>RetentionService</code> — already defended "
      "by a compiled-in <code>ALLOWED_TABLES</code> allowlist plus an identifier pattern.</li>")
    a("<li><code>no-hardcoded-passwords</code> ×22 — E2E test credentials in "
      "<code>e2e/utils/auth.ts</code>. The 23rd is <code>pwd</code> meaning “Person with "
      "Disabilities”.</li>")
    a("<li>Most of the 34 empty catches are deliberate best-effort <code>localStorage</code> parses.</li>")
    a("</ul>")
    a("<div class='note amber'><b>What an offline pass cannot do:</b> no cross-module taint analysis "
      "(so a user value flowing from a controller through three services into a query is not traced), no "
      "historical trend or new-code quality gate, no PR decoration, no duplication metric across the "
      "whole repo, and no security hotspot review workflow. <code>find-sec-bugs</code> approximates "
      "Coverity's security rules on a single-module basis only.</div>")

    # ── SEO ──────────────────────────────────────────────────────────────────────────────────
    a("<h2>9. SEO — baseline vs after</h2>")
    a("<table><tr><th>Item</th><th>Before</th><th>After</th></tr>")
    for item, before, after in [
        ("Per-route <code>&lt;title&gt;</code>", "<span class='bad'>0 calls anywhere</span>",
         "<span class='ok'>every route, from translation keys</span>"),
        ("Meta description", "<span class='bad'>1 static, shared by 84 routes</span>",
         "<span class='ok'>per route, 11 locales</span>"),
        ("Canonical", "static, single-valued", "per route, runtime-overridable origin"),
        ("hreflang", "<span class='bad'>none</span>",
         "<span class='ok'>11 + x-default, on every indexable URL</span>"),
        ("<code>&lt;html lang&gt;</code>", "fixed <code>en</code>", "follows the active locale"),
        ("JSON-LD", "<span class='bad'>0 occurrences</span>",
         "<span class='ok'>GovernmentOrganization, WebSite+SearchAction, FAQPage (18 rows), "
         "BreadcrumbList</span>"),
        ("sitemap.xml", "<span class='bad'>6 URLs, hand-written, included an auth-guarded page, "
         "omitted the FAQ</span>",
         "<span class='ok'>generated from the route table; 5 indexable × 11 locales</span>"),
        ("robots.txt", "<span class='bad'>5 prefixes; missed /cepc/ /rbio/ /aa/ "
         "/email-syndication/ and /public/upload/</span>",
         "<span class='ok'>14 staff prefixes + 8 private paths, verified against the router</span>"),
        ("noindex on private pages", "<span class='bad'>no robots meta at all</span>",
         "<span class='ok'>fails closed — unlisted routes get noindex</span>"),
        ("SSR / prerender", "<span class='bad'>none</span>",
         "<span class='bad'>still none — attempted, blocked, reverted (§6)</span>"),
        ("Lighthouse SEO / CWV", "<span class='muted'>not measured</span>",
         "<span class='bad'>not measured</span>"),
    ]:
        a(f"<tr><td>{item}</td><td>{before}</td><td>{after}</td></tr>")
    a("</table>")
    a("<div class='note amber'><b>Infrastructure consequence, stated as required:</b> everything above "
      "ships with the existing RHEL8 nginx + <code>envsubst</code> container — <b>no deployment "
      "change</b>. Prerendering would also have required no new runtime, which is why it was chosen over "
      "SSR; it is blocked by application code, not by infrastructure. Full SSR WOULD require replacing "
      "the nginx container with a Node process and needs DevOps sign-off.</div>")
    a("<div class='note'>Indexable URL count: <b>5</b> canonical public routes (× 11 locale alternates). "
      "Everything else — 5 session-only citizen pages, the tokenised upload link, and all 14 staff "
      "prefixes — is deliberately excluded.</div>")

    # ── Still open ───────────────────────────────────────────────────────────────────────────
    a("<h2>10. Still open — reported, not silently changed (§4.2)</h2>")
    a("<table><tr><th style='width:300px'>Item</th><th style='width:80px'>Severity</th>"
      "<th>Detail</th></tr>")
    for item, sev, detail in STILL_OPEN:
        cls = "bad" if sev == "HIGH" else ("warn" if sev == "MEDIUM" else "muted")
        a(f"<tr><td><b>{item}</b></td><td class='{cls}'>{sev}</td><td>{detail}</td></tr>")
    a("</table>")
    a("<h3>Demo-data honesty, restated</h3><ul>")
    a("<li>RBI master data was never supplied.</li>")
    a("<li>The DSC is a deliberate mock; its citizen-facing wording must keep saying "
      "<b>“NOT signed”</b>.</li>")
    a("<li>SMS writes an outbox row and sends nothing.</li>")
    a("<li>Kafka is absent locally, so <code>complaint.ingested</code> publishes fail by design.</li>")
    a("</ul>")

    # ── Sign-off ─────────────────────────────────────────────────────────────────────────────
    a("<h2>11. Needs sign-off</h2>")
    a("<table><tr><th style='width:280px'>Item</th><th>Position</th></tr>")
    for item, detail in SIGN_OFF:
        a(f"<tr><td><b>{item}</b></td><td>{detail}</td></tr>")
    a("</table>")

    # ── Changed assertions ───────────────────────────────────────────────────────────────────
    a("<h2>12. Assertions changed because a ruling overruled them</h2>")
    a("<div class='note amber'>The brief requires these to be called out rather than quietly "
      "updated.</div>")
    a("<table><tr><th style='width:300px'>Test</th><th>Change</th></tr>")
    a("<tr><td><code>aa/s2a-register-appeal.spec.ts</code> — “the per-file cap is 2MB”</td>"
      "<td>Required a 3 MB file to be REJECTED, correct under the superseded 2 MB figure. Ruling 0.4 "
      "makes the limit 5 MB, so 3 MB is now legal and the test was failing against correct behaviour. "
      "The boundary is now DERIVED from <code>/api/v1/config/upload-limits</code> so the next retune "
      "cannot strand it again.</td></tr>")
    a("<tr><td><code>AaAppealOrderServiceTest</code> — “the default is inert”</td>"
      "<td>Asserted <code>block_sub_judice</code> was unenforced by default. Ruling 0.2 arms it, so the "
      "test now asserts the armed default plus that setting the flag false restores the old "
      "behaviour.</td></tr>")
    a("<tr><td><code>FileUploadValidatorTest</code></td>"
      "<td>Its fixture set a 2 MB cap and its “oversized” file was 3 MB — which stopped being oversized "
      "when the rule became 5 MB. It was passing only because the fixture was stale. Now derived from "
      "the configured ceiling.</td></tr>")
    a("</table>")

    # ── Failures ─────────────────────────────────────────────────────────────────────────────
    a("<h2>13. Remaining failures, classified</h2>")
    a("<table><tr><th style='width:330px'>Spec</th><th style='width:130px'>Classification</th>"
      "<th>Root cause</th></tr>")
    a("<tr><td><code>cepc/workflow-actions.spec.ts:36</code></td>"
      "<td class='warn'>pre-existing</td><td>Documented in the brief as predating all UI work.</td></tr>")
    a("<tr><td><code>cepc/dashboard.spec.ts</code> ×2</td><td class='warn'>harness — flaky login</td>"
      "<td>Drove it in a real browser first, per the brief: the <code>h1</code> IS present and reads "
      "“CEPC Complaints”. With <code>--retries=1</code> both pass. Not a defect.</td></tr>")
    a("<tr><td><code>re-portal/reassignment</code></td><td class='warn'>known-environmental</td>"
      "<td><code>reassignment-auth.ts</code> hardcodes port 8095; nothing listens there.</td></tr>")
    a("<tr><td><code>aa/*</code> residual</td><td class='warn'>pre-existing</td>"
      "<td>The brief's own baseline records 19 <code>aa</code> failures. The one I could attribute to "
      "this pass (the 2 MB cap assertion) is fixed — see §12.</td></tr>")
    a("</table>")

    a("<h2>14. How to reproduce</h2>")
    a("<table><tr><th style='width:230px'>What</th><th>Command</th></tr>")
    for what, cmd in [
        ("Frontend lint", "npx ng lint"),
        ("SpotBugs + find-sec-bugs", "mvn spotbugs:spotbugs   # target/spotbugsXml.xml"),
        ("PMD", "mvn org.apache.maven.plugins:maven-pmd-plugin:3.26.0:pmd"),
        ("Coverage", "mvn -o test &amp;&amp; mvn org.jacoco:jacoco-maven-plugin:0.8.12:report"),
        ("Regenerate sitemap + robots", "node scripts/generate-sitemap.mjs"),
        ("SEO tests", "npx playwright test e2e/public/seo.spec.ts e2e/public/seo-headings.spec.ts"),
        ("This report", "python scripts/qa/ui_homog_pass3_report.py"),
    ]:
        a(f"<tr><td>{what}</td><td><code>{cmd}</code></td></tr>")
    a("</table>")
    a("<div class='note'>Use FULLY-QUALIFIED Maven plugin coordinates: <code>mvn jacoco:report</code> and "
      "<code>mvn pmd:pmd</code> fail with “No plugin found for prefix” because the reactor root is the "
      "parent pom. Drop <code>-o</code> the first time a new plugin is fetched.</div>")

    a("</main></body></html>")
    return "\n".join(parts)


def main() -> int:
    ROOT.mkdir(parents=True, exist_ok=True)
    out = ROOT / "index.html"
    out.write_text(build(), encoding="utf-8")
    print(f"wrote {out} ({out.stat().st_size:,} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
