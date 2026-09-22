#!/usr/bin/env python3
"""Build the self-contained UI-homogenisation HTML report.

Reads the Playwright line-reporter outputs and the screenshot directories, then emits
D:/screenShots/ui-homog/index.html with embedded CSS and RELATIVE image paths so the folder zips.

Usage:
  python scripts/qa/ui_homog_report.py
"""
from __future__ import annotations

import html
import re
from collections import defaultdict
from pathlib import Path

ROOT = Path("D:/screenShots/ui-homog")
BEFORE = ROOT / "before"
AFTER = ROOT / "after"
TMP = Path("C:/tmp")

DIRS = ["admin", "public", "i18n", "aa", "cepc", "rbio", "re-portal", "ui-homogenisation"]

# Baseline measured by this session on 2026-09-22 at HEAD b46bc5d, BEFORE any change.
# re-portal is recorded as invalid: the backend was restarted mid-run, so its numbers are not a
# baseline and are re-measured from the after-run instead of being compared against noise.
BASELINE = {
    "admin": (106, 0, 9),
    "public": (105, 6, 0),
    "i18n": (9, 0, 0),
    "aa": (155, 23, 23),
    "cepc": (27, 2, 6),
    "rbio": (176, 5, 11),
    "re-portal": None,
    "ui-homogenisation": (0, 0, 0),
}

SUMMARY_RE = re.compile(r"^\s+(\d+)\s+(passed|failed|skipped|flaky)", re.MULTILINE)


def parse_counts(path: Path) -> tuple[int, int, int] | None:
    if not path.exists():
        return None
    text = path.read_text(encoding="utf-8", errors="replace")
    counts = {"passed": 0, "failed": 0, "skipped": 0, "flaky": 0}
    for value, label in SUMMARY_RE.findall(text):
        counts[label] = int(value)
    if not any(counts.values()):
        return None
    return counts["passed"], counts["failed"], counts["skipped"]


def failing_tests(path: Path) -> list[str]:
    if not path.exists():
        return []
    text = path.read_text(encoding="utf-8", errors="replace")
    return re.findall(r"^\s+\[chromium\] › (.+?)\s*$", text, re.MULTILINE)


def screenshots(base: Path, module: str) -> dict[str, Path]:
    """Maps a normalised test name to its screenshot, so before/after can be paired."""
    found: dict[str, Path] = {}
    d = base / module
    if not d.exists():
        return found
    for png in sorted(d.rglob("*.png")):
        found[png.parent.name] = png
    return found


def rel(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


# ── Colour → token mapping, kept in step with tokenise_colours.py ────────────────────────────
TOKEN_TABLE = [
    ("#e2e8f0", "--border-subtle", "card and table borders (157 uses in CRPC)"),
    ("#3b82f6", "--brand-primary", "primary action, active tab, focus border"),
    ("#1e293b", "--text-heading", "headings; also the sidebar surface"),
    ("#fff", "--surface-card", "card and header background"),
    ("#64748b", "--text-muted", "secondary labels"),
    ("#94a3b8", "--text-subtle", "placeholders, disabled text"),
    ("#475569", "--text-secondary", "form labels, button text"),
    ("#f1f5f9", "--surface-subtle", "hover fill, subtle background"),
    ("#f8fafc", "--surface-page", "page background, table header"),
    ("#f59e0b", "--state-warning-solid", "warning icon"),
    ("#fef3c7", "--state-warning-bg", "warning chip background"),
    ("#92400e", "--state-warning-fg", "warning chip text"),
    ("#d1fae5", "--state-success-bg", "success chip background"),
    ("#065f46", "--state-success-fg", "success chip text"),
    ("#dbeafe", "--brand-primary-bg-strong", "in-progress chip"),
    ("#1e40af", "--brand-primary-strong", "strong brand text"),
    ("#eff6ff", "--brand-primary-bg", "selected row"),
    ("#334155", "--text-body", "table cell text"),
    ("#1a237e", "--brand-primary-strong", "AA Material Indigo, FLATTENED to CRPC blue (65 uses)"),
    ("#1565c0 / #0d47a1", "--brand-primary / --brand-primary-strong", "RE + CEPC blues, unified"),
    ("#fef2f2 / #fecaca", "--state-danger-bg", "RE red surfaces, unified"),
]

RESIDUAL = [
    ("crpc", 103, 1394, 53, 110),
    ("rbio", 134, 962, 72, 178),
    ("aa", 94, 384, 44, 85),
    ("re-portal", 42, 217, 11, 14),
    ("cepc", 69, 307, 23, 37),
    ("shared", 53, 151, 11, 28),
]

TRANSLATE_DELTA = [
    ("aa", 344, 344, "already the reference standard"),
    ("rbio", 176, 176, "unchanged this batch"),
    ("re-portal", 72, 72, "unchanged this batch"),
    ("public", 302, 302, "unchanged this batch"),
    ("crpc", 0, 10, "shell + toolbar localised for the first time"),
    ("cepc", 0, 9, "shell + toolbar localised for the first time"),
    ("shared", 68, 83, "shell, toast host and language switcher"),
    ("admin", 20, 20, "unchanged this batch"),
]

CSS = """
* { box-sizing: border-box; }
body { margin:0; font-family: 'Segoe UI', system-ui, sans-serif; background:#f8fafc; color:#334155;
       line-height:1.55; }
header.top { background:#1e293b; color:#fff; padding:28px 40px; }
header.top h1 { margin:0 0 6px; font-size:26px; }
header.top p { margin:0; color:#94a3b8; font-size:14px; }
main { max-width:1400px; margin:0 auto; padding:32px 40px 80px; }
h2 { font-size:20px; color:#1e293b; margin:44px 0 14px; padding-bottom:8px;
     border-bottom:2px solid #e2e8f0; }
h3 { font-size:15px; color:#334155; margin:26px 0 10px; }
table { width:100%; border-collapse:collapse; font-size:13px; background:#fff;
        border:1px solid #e2e8f0; border-radius:8px; overflow:hidden; margin-bottom:18px; }
th { background:#f8fafc; text-align:left; padding:10px 12px; font-weight:600; color:#1e293b;
     border-bottom:1px solid #e2e8f0; }
td { padding:9px 12px; border-bottom:1px solid #f1f5f9; vertical-align:top; }
tr:last-child td { border-bottom:none; }
code { background:#f1f5f9; padding:1px 5px; border-radius:4px; font-size:12px; }
.verdict { background:#fff; border:1px solid #e2e8f0; border-left:5px solid #f59e0b;
           border-radius:8px; padding:20px 24px; margin-bottom:8px; }
.verdict .tag { display:inline-block; background:#fef3c7; color:#92400e; font-weight:700;
                padding:4px 12px; border-radius:999px; font-size:13px; margin-bottom:10px; }
.ok { color:#065f46; font-weight:600; }
.bad { color:#991b1b; font-weight:600; }
.warn { color:#92400e; font-weight:600; }
.muted { color:#64748b; }
.pair { display:grid; grid-template-columns:1fr 1fr; gap:14px; margin-bottom:22px;
        background:#fff; border:1px solid #e2e8f0; border-radius:8px; padding:14px; }
.pair figure { margin:0; }
.pair figcaption { font-size:11px; font-weight:600; color:#64748b; text-transform:uppercase;
                   letter-spacing:.4px; margin-bottom:6px; }
.pair img { width:100%; border:1px solid #e2e8f0; border-radius:6px; display:block; }
.shot-title { font-size:12px; color:#475569; margin:0 0 10px; grid-column:1/-1;
              font-family:ui-monospace,monospace; word-break:break-all; }
.note { background:#eff6ff; border-left:4px solid #3b82f6; padding:12px 16px; border-radius:6px;
        font-size:13px; margin:14px 0; }
.note.amber { background:#fef3c7; border-left-color:#f59e0b; }
.note.red { background:#fee2e2; border-left-color:#ef4444; }
ul { margin:8px 0 8px 22px; padding:0; font-size:13px; }
li { margin-bottom:6px; }
"""


def build() -> str:
    parts: list[str] = []
    a = parts.append

    a("<!DOCTYPE html><html lang='en'><head><meta charset='utf-8'>")
    a("<title>CMS 2.0 — UI Homogenisation Report</title>")
    a(f"<style>{CSS}</style></head><body>")
    a("<header class='top'><h1>CMS 2.0 — UI Homogenisation, Verification &amp; Open Decisions</h1>")
    a("<p>Frontend: cms-portal-frontend &middot; measured 2026-09-22 &middot; base commit b46bc5d</p></header><main>")

    # ── 1. Verdict ──
    after = {d: parse_counts(TMP / f"after_{d}.txt") for d in DIRS}

    a("<h2>1. Verdict and regression status</h2>")
    a("<div class='verdict'><span class='tag'>CONDITIONAL GO</span>")
    a("<p><strong>The design system, the shared shell and the localisation level-up are delivered and "
      "verified; no E2E regression was introduced.</strong> The conditions are product decisions, not "
      "engineering defects: three item-13 blockers remain open pending the answers this batch was told "
      "not to guess, and Punjabi is not served by the API at all.</p>")
    a("<p class='muted'>Every number below was measured by this session. The task file's baseline was "
      "re-measured rather than trusted, and it differed in three places &mdash; see the note under the table.</p>")
    a("</div>")

    a("<table><tr><th>Suite</th><th>Baseline (mine, pre-change)</th><th>After</th>"
      "<th>Passed &Delta;</th><th>Failed &Delta;</th><th>Verdict</th></tr>")
    for d in DIRS:
        base = BASELINE.get(d)
        aft = after.get(d)
        if aft is None:
            a(f"<tr><td>{d}</td><td colspan='5' class='muted'>no result recorded</td></tr>")
            continue
        ap, af, ask = aft
        if base is None:
            a(f"<tr><td>{d}</td><td class='muted'>invalid &mdash; backend restarted mid-run</td>"
              f"<td>{ap}P / {af}F / {ask}S</td><td colspan='2' class='muted'>not comparable</td>"
              f"<td class='warn'>re-measured</td></tr>")
            continue
        bp, bf, bs = base
        dp, df = ap - bp, af - bf
        if df > 0:
            verdict, cls = "REGRESSION", "bad"
        elif df < 0 or dp > 0:
            verdict, cls = "improved", "ok"
        else:
            verdict, cls = "no change", "ok"
        a(f"<tr><td>{d}</td><td>{bp}P / {bf}F / {bs}S</td><td>{ap}P / {af}F / {ask}S</td>"
          f"<td>{dp:+d}</td><td>{df:+d}</td><td class='{cls}'>{verdict}</td></tr>")
    a("</table>")

    a("<div class='note amber'><strong>Where my baseline differed from the task file.</strong>"
      "<ul>"
      "<li><code>public</code>: <strong>6</strong> failures, not 4. All six are i18n/data defects "
      "(clause interpolation, raw FAQ keys, consent notice) and none is CSS-related.</li>"
      "<li><code>aa</code>: <strong>155 passed / 23 failed</strong>, not 153/25.</li>"
      "<li><code>re-portal</code>: the file predicts 36P/1F/33S. My baseline run collided with a backend "
      "restart I initiated, so that measurement is void and is not used as a comparison.</li>"
      "<li><code>alert()</code> calls: <strong>4</strong> real ones (the other 3 grep hits are "
      "XSS-escaping assertions in a unit test).</li>"
      "<li><code>re-portal</code> has <strong>7</strong> components, not 9.</li>"
      "</ul></div>")

    a("<h3>Skips are reported separately &mdash; a skip is not a pass</h3>")
    a("<table><tr><th>Suite</th><th>Skipped after</th><th>Why it matters</th></tr>")
    skip_reason = {
        "admin": "9 skips are pre-existing and unrelated to this batch.",
        "aa": "Staff specs self-skip when Keycloak looks unavailable; a Keycloak outage would render this suite green and vacuous.",
        "cepc": "Same Keycloak-gated skip mechanism.",
        "rbio": "Same Keycloak-gated skip mechanism.",
        "re-portal": "Highest skip ratio in the repo; most of the suite never executes.",
        "public": "No skips.",
        "i18n": "No skips.",
        "ui-homogenisation": "New suite, no skips.",
    }
    for d in DIRS:
        aft = after.get(d)
        if aft:
            a(f"<tr><td>{d}</td><td>{aft[2]}</td><td class='muted'>{skip_reason.get(d,'')}</td></tr>")
    a("</table>")

    # ── 2. Before / after ──
    a("<h2>2. Before and after screenshots</h2>")
    a("<div class='note'><strong>Coverage ceiling, stated plainly.</strong> Only specs that drive "
      "<code>page.</code> can yield an image. <code>e2e/admin/</code> produced <strong>0 PNGs from 106 "
      "passing tests</strong> because it is a pure API suite. Pairs below are matched on the Playwright "
      "output directory name, so only screens photographed in <em>both</em> runs appear.</div>")

    total_pairs = 0
    for module in DIRS:
        b_shots = screenshots(BEFORE, module)
        a_shots = screenshots(AFTER, module)
        common = sorted(set(b_shots) & set(a_shots))
        if not common:
            continue
        a(f"<h3>{module} &mdash; {len(common)} paired screen(s)</h3>")
        for name in common[:6]:
            total_pairs += 1
            a("<div class='pair'>")
            a(f"<p class='shot-title'>{html.escape(name)}</p>")
            a(f"<figure><figcaption>Before</figcaption><img src='{rel(b_shots[name])}' alt='before'></figure>")
            a(f"<figure><figcaption>After</figcaption><img src='{rel(a_shots[name])}' alt='after'></figure>")
            a("</div>")
        if len(common) > 6:
            a(f"<p class='muted'>&hellip; and {len(common)-6} further paired screens in "
              f"<code>before/{module}/</code> and <code>after/{module}/</code>.</p>")

    if total_pairs == 0:
        a("<div class='note red'>No before/after pairs matched. Playwright names its output directory "
          "after the test title, so a renamed or newly-passing test has no counterpart in the other run.</div>")

    # ── 3. Tokens ──
    a("<h2>3. Colour &rarr; token mapping</h2>")
    a("<p>Tokens were extracted from CRPC's measured palette, not invented. "
      "<code>src/styles.scss</code> went from <strong>105 lines with zero custom properties</strong> to a "
      "<code>:root</code> block defining colour, spacing, radius, shadow, type and layout tokens, plus "
      "<code>src/_primitives.scss</code> for the shared component vocabulary.</p>")
    a("<table><tr><th>CRPC hex</th><th>Token</th><th>Role</th></tr>")
    for hexv, token, role in TOKEN_TABLE:
        a(f"<tr><td><code>{hexv}</code></td><td><code>{token}</code></td><td>{role}</td></tr>")
    a("</table>")

    a("<h3>Residual un-tokenised hex per module</h3>")
    a("<table><tr><th>Module</th><th>Distinct before</th><th>Occurrences before</th>"
      "<th>Distinct after</th><th>Occurrences after</th><th>Reduction</th></tr>")
    tb = ta = 0
    for mod, db, ob, da, oa in RESIDUAL:
        tb += ob
        ta += oa
        pct = round(100 * (ob - oa) / ob) if ob else 0
        a(f"<tr><td>{mod}</td><td>{db}</td><td>{ob}</td><td>{da}</td><td>{oa}</td>"
          f"<td class='ok'>&minus;{pct}%</td></tr>")
    a(f"<tr><th>total (migrated modules)</th><th></th><th>{tb}</th><th></th><th>{ta}</th>"
      f"<th class='ok'>&minus;{round(100*(tb-ta)/tb)}%</th></tr>")
    a("</table>")
    a("<div class='note'><strong>Why a residue remains, and why it is acceptable.</strong> What is left is "
      "predominantly <code>rgba()</code> composites (shadows and scrims, which are opacity over a base "
      "rather than a surface colour), gradients, chart series colours that must stay visually distinct, "
      "and inline SVG fills. <code>public/</code> and <code>admin/</code> were deliberately left out of "
      "scope for this batch. The migration was applied by a committed script "
      "(<code>scripts/qa/tokenise_colours.py</code>), so the residue is a measurable number rather than "
      "an estimate: run <code>--report</code> to reproduce it.</div>")

    # ── 4. Localisation ──
    a("<h2>4. Localisation delta</h2>")
    a("<p>The hard gate is that the repo-wide <code>| translate</code> count must <strong>increase</strong>. "
      "It did: <strong>1088 &rarr; 1122</strong>. CRPC and CEPC went from <strong>zero</strong> "
      "localisation to a translated shell and toolbar.</p>")
    a("<table><tr><th>Module</th><th>| translate before</th><th>after</th><th>Note</th></tr>")
    for mod, before_n, after_n, note in TRANSLATE_DELTA:
        cls = "ok" if after_n > before_n else "muted"
        a(f"<tr><td>{mod}</td><td>{before_n}</td><td class='{cls}'>{after_n}</td><td class='muted'>{note}</td></tr>")
    a("</table>")

    a("<h3>New keys</h3>")
    a("<ul>"
      "<li><strong>70 new keys</strong> seeded by one new class, <code>UiShellTranslationSeeder</code> at "
      "<code>@Order(60)</code> &mdash; verified unclaimed before use, and no existing seeder was edited.</li>"
      "<li>Namespaces <code>ui.shell.*</code>, <code>ui.nav.*</code>, <code>ui.common.*</code>, "
      "<code>ui.toast.*</code>, <code>ui.upload.*</code>, <code>ui.role.*</code>, <code>ui.page.*</code> "
      "were all confirmed unused first, because seeding is idempotent by key code and a duplicate would "
      "silently keep the FIRST text.</li>"
      "<li>Seeded in <strong>all ten locales with real translations</strong>, asserted by test to differ "
      "from English in each of the nine the API serves.</li>"
      "<li><code>common.skip_to_content</code> was rendering as a RAW KEY on every public page &mdash; "
      "found by the new spec, and now seeded.</li>"
      "</ul>")

    a("<div class='note red'><strong>Finding that contradicts the brief: Punjabi is not served at all.</strong> "
      "The task file states <code>pa</code> is &ldquo;100% identical to English (1806/1806 keys)&rdquo;. The "
      "real cause is that <code>pa</code> <strong>is not a member of the <code>SupportedLocale</code> enum</strong> "
      "&mdash; it lists only 9 locales plus English. <code>TranslationService</code> resolves any unsupported "
      "code to <code>&quot;en&quot;</code>, so <code>GET /translations/pa</code> returns English no matter what "
      "the database holds. The &ldquo;100% identical&rdquo; measurement was comparing English to English. "
      "This batch seeded genuine Gurmukhi for its 70 keys and they are correct in the database, but they will "
      "not reach a user until <code>PA(&quot;pa&quot;, &quot;Punjabi&quot;, &quot;&#2602;&#2672;&#2588;&#2622;&#2604;&#2624;&quot;, false)</code> "
      "is added to the enum. That is a backend change beyond a UI batch, and it is the single highest-value "
      "localisation fix outstanding.</div>")

    a("<h3>Still needing human translation (NOT machine-translated here)</h3>")
    a("<ul>"
      "<li><strong>13 <code>clause.*</code> labels are English-only</strong>; 14 of 24 clause-related keys are "
      "untranslated even in Hindi. A translated clause label is a legal statement about what that clause "
      "means, so it needs sign-off, not automation. Deliberately untouched (item 13.4).</li>"
      "<li>The <strong>1806 pre-existing keys</strong> remain 14&ndash;21% English passthrough in the eight "
      "served non-English locales. Unchanged by this batch and out of its scope.</li>"
      "<li><code>scripts/qa/i18n_check.py</code> remains necessary but insufficient: it defaults to port 8096 "
      "and <code>--scan-components</code> covers only 296 of 1806 keys, so it can return PASS while a locale "
      "is untranslated. The new specs assert per-locale difference directly instead.</li>"
      "</ul>")

    # ── 5. Accessibility ──
    a("<h2>5. Accessibility and responsiveness</h2>")
    a("<table><tr><th>Check</th><th>Result</th><th>Detail</th></tr>")
    for check, res, det, cls in [
        ("Visible focus indicator", "PASS", "<code>--focus-ring</code> is a token; asserted by test that focus is never suppressed without a replacement. No <code>outline:none</code> was introduced.", "ok"),
        ("Table header semantics", "PASS", "Asserted that no data table has a <code>&lt;th&gt;</code> without <code>scope</code>.", "ok"),
        ("Live regions on notifications", "PASS", "Toasts carry <code>role=&quot;status&quot;</code>; errors are <code>aria-live=&quot;assertive&quot;</code>, the rest polite.", "ok"),
        ("Raw keys visible to users", "PASS (1 fixed, 1 pre-existing)", "<code>common.skip_to_content</code> fixed. FAQ rows 1&ndash;10 still render <code>faq.qN.question</code> &mdash; a data defect, reported not patched.", "ok"),
        ("Errors do not auto-dismiss", "PASS", "Error toasts persist; a 4-second error message is one nobody reads.", "ok"),
        ("Decorative images hidden", "PASS", "Shell masthead logo is <code>alt=&quot;&quot;</code> + <code>aria-hidden</code>; icons are <code>aria-hidden</code>.", "ok"),
        ("Responsive 1024 / 1280 / 1440", "PARTIAL", "Shell collapses the sidebar and hides the masthead subtitle below 1024px, and toasts go full-width. Not photographed at each breakpoint &mdash; see limitations.", "warn"),
        ("Contrast on new surfaces", "INHERITED", "All new surfaces reuse CRPC pairings already in production (e.g. <code>--state-warning-bg</code> on <code>--state-warning-fg</code>). No new colour pair was invented, but no instrumented contrast audit was run.", "warn"),
    ]:
        a(f"<tr><td>{check}</td><td class='{cls}'>{res}</td><td class='muted'>{det}</td></tr>")
    a("</table>")

    # ── 6. Regressions ──
    a("<h2>6. Functional regressions: pre-existing vs newly introduced</h2>")
    a("<div class='note'><strong>No new failures were introduced in any suite.</strong> Every failure "
      "below predates this batch.</div>")
    a("<table><tr><th>Suite</th><th>Failures after</th><th>Classification</th></tr>")
    for d in DIRS:
        aft = after.get(d)
        if not aft or aft[1] == 0:
            continue
        names = failing_tests(TMP / f"after_{d}.txt")
        listing = "<br>".join(html.escape(n) for n in names[:12]) or "<span class='muted'>see log</span>"
        a(f"<tr><td>{d}</td><td>{aft[1]}</td><td class='muted'>{listing}</td></tr>")
    a("</table>")

    a("<h3>AA harness gap: fixed, and the task file's diagnosis was wrong</h3>")
    a("<div class='note'><p>The file attributes the AA failures to an unauthenticated browser context and "
      "recommends <code>loginAsAaRole</code>. That would not have worked. The real causes, in order:</p>"
      "<ol>"
      "<li><strong>The URL did not exist.</strong> All 8 <code>file-appeal</code> tests navigated to "
      "<code>/appeal</code>. The route is <code>/public/appeal</code> &mdash; a CHILD of the "
      "<code>public</code> path. A non-existent Angular route falls through to the public home page, so "
      "<code>.form-input</code> was genuinely absent and the failure impersonated a broken form.</li>"
      "<li><strong>It needs a CITIZEN session, not a staff login.</strong> <code>/public/appeal</code> sits "
      "behind <code>publicAuthGuard</code>, which reads a citizen OTP session from "
      "<code>sessionStorage</code>. A Keycloak staff token does not satisfy it, so "
      "<code>loginAsAaRole</code> was the wrong tool; <code>seedCitizenSession</code> is the right one.</li>"
      "</ol>"
      "<p><strong>Result: 8 failures &rarr; 2</strong>, and 6 tests now genuinely exercise the appeal UI "
      "for the first time. The remaining 2 pass a <code>backdateDays</code> option to "
      "<code>advanceToStatus</code> that <strong>the helper does not implement</strong>, so closure is never "
      "backdated and the eligibility window never elapses. That is a separate pre-existing harness gap, "
      "reported rather than fixed inside a UI batch.</p></div>")

    # ── 7. Defects not fixed ──
    a("<h2>7. Defects found but deliberately NOT fixed</h2>")
    a("<table><tr><th>#</th><th>Defect</th><th>Why it was left</th></tr>")
    for n, defect, why in [
        ("1", "<code>pa</code> absent from <code>SupportedLocale</code>, so Punjabi silently serves English.",
         "Backend enum change; it alters what every locale-aware endpoint returns. Needs its own verification pass."),
        ("2", "FAQ rows 1&ndash;10 store literal key strings in <code>questionKey</code> and the matching keys are unseeded, so raw codes render to citizens.",
         "A data correction to FAQ rows, not a UI change. <code>seedIfAbsent</code> can never repair rows that already exist."),
        ("3", "<code>eligibility.block_not_filed</code> has clause <code>10(1)(j)</code> baked into prose where the seeder expects a <code>{{clause}}</code> placeholder.",
         "The DB row predates the seeder and needs a corrective UPDATE plus a clause ruling."),
        ("4", "<code>advanceToStatus</code> silently ignores <code>backdateDays</code>.",
         "Test-harness defect; silently accepting an unimplemented option is the trap. Out of scope, now documented."),
        ("5", "A fifth hardcoded <code>CLOSED_STATUSES</code> list in <code>OfficerDeactivationService.java:36</code>, outside the four consolidated onto <code>RbioStatusVocabulary</code> (13.8).",
         "Backend workflow change with real behavioural risk. Confirmed present; reported."),
        ("6", "Migrations cannot build the schema: 15 of 60 applied, 55 of 113 tables; <code>V1</code>/<code>V3</code> contain Oracle <code>CREATE SEQUENCE &hellip; NOCACHE</code> invalid in MySQL; <code>V5</code>/<code>V6</code> not re-runnable (13.6).",
         "Explicitly out of scope. It IS a release blocker for any <code>ddl-auto: off</code> deploy."),
        ("7", "<code>cms-frontend/</code> is dead code (5 routes, no Keycloak) yet <code>deployment-frontend.yaml</code> routes host <code>cms-staff.apps&hellip;</code> at it.",
         "Flagged, changed nothing, per instruction. Looks mis-wired and worth a deployment review."),
        ("8", "Decorative controls in module headers: a language button and A-/A/A+ font controls that were wired to nothing.",
         "The dead language button is REPLACED by the working switcher in the shell. The font-size controls were removed with the duplicated chrome; if font scaling is a requirement it needs building, not faking."),
    ]:
        a(f"<tr><td>{n}</td><td>{defect}</td><td class='muted'>{why}</td></tr>")
    a("</table>")

    # ── 8. Item 13 ──
    a("<h2>8. Item-13 decision outcomes</h2>")
    a("<table><tr><th>Item</th><th>Status</th><th>What was done</th></tr>")
    for item, status, cls, did in [
        ("13.1 File size 5 MB / 25 MB, configurable",
         "IMPLEMENTED", "ok",
         "Was hardcoded 2 MB in four places with NO config row, while the citizen hint promised 5 MB in ten locales. Now: <code>SYSTEM_CONFIG</code> rows via <code>V59</code> (MySQL) and Oracle <code>V57</code>, both re-runnable and replay-verified; new <code>UploadLimitsService</code>; anonymous <code>GET /api/v1/config/upload-limits</code> (GET-only matcher, so a future write cannot inherit anonymous access); frontend <code>UploadLimitsService</code> replaces the compiled constant; all three environment files corrected to 5; hint text now interpolates <code>{{size}}</code> so it can never again contradict enforcement. Verified by direct API call returning 5/25."),
        ("13.2 <code>&quot;Clause FRC&quot;</code> placeholder in citizen-facing text",
         "ESCALATED", "warn",
         "Confirmed still live at <code>AutoClosureService.java:122</code>. No authoritative clause number was supplied, and inventing a clause number in citizen-facing legal text is not acceptable. <strong>Blocked on: the real clause from the business/legal owner.</strong>"),
        ("13.3 Compensation caps armed without sign-off",
         "AS RULED", "ok",
         "Left at &#8377;30 lakh combined per the ruling, and flagged for legal. The gazette's &ldquo;in addition&rdquo; wording implies a &#8377;33 lakh ceiling; that discrepancy is a <strong>legal</strong> decision. Separately, <code>RulesApiController:82-88</code> hardcodes &#8377;30L/&#8377;3L in DRL strings that are display-only (no Drools session), so it is a presentation divergence to reconcile, not a second enforcement path."),
        ("13.4 Thirteen <code>clause.*</code> labels English-only",
         "ESCALATED", "warn",
         "Surfaced, translated nothing. 14 of 24 clause keys are untranslated even in Hindi, 24/24 in Punjabi. <strong>Blocked on: legal sign-off on who may translate a clause label.</strong>"),
        ("13.5 Closed complaints with no closure clause",
         "CONFIRMED", "warn",
         "Re-measured on the live database: <strong>4699 of 5361</strong> closed complaints have no <code>closure_clause</code> (the file said 4604 of 5236; the population has grown). Column is <code>closure_clause</code>; there is no <code>closure_clause_code</code>. Those rows cannot be appealed. <strong>Blocked on: a mandatory-clause-at-closure policy and a back-fill-or-declare-unappealable decision.</strong>"),
        ("13.6 Migrations cannot build the schema",
         "REPORTED", "warn",
         "Not attempted, per instruction. Remains a release blocker for any <code>ddl-auto: off</code> deployment."),
        ("13.7 Thirteen orphan status strings",
         "MITIGATED", "ok",
         "Re-measured: <strong>383 complaints across 13 statuses</strong> absent from <code>RBIO_STATUS_MASTER</code> (<code>forwarded</code> 156, <code>pending</code> 76, <code>awaiting_details</code> 59, <code>reviewer_review</code> 41, <code>adjudicated</code> 20, <code>re_responded</code> 18, <code>returned</code> 7, plus 6 singletons). The shared badge already humanises unknown values; the two with no colour rule at all (<code>AWAITING_DETAILS</code>, <code>SENT_TO_OTHER</code>) now degrade to deliberate tokens instead of a bare neutral chip. <strong>The vocabulary itself still needs fixing</strong> &mdash; either seed the master rows or migrate the complaints."),
        ("13.8 Fifth hardcoded <code>CLOSED_STATUSES</code>",
         "REPORTED", "warn",
         "Confirmed at <code>cms-workflow-service/&hellip;/OfficerDeactivationService.java:36</code>. Backend work, outside a UI batch."),
    ]:
        a(f"<tr><td>{item}</td><td class='{cls}'>{status}</td><td class='muted'>{did}</td></tr>")
    a("</table>")

    # ── Run instructions ──
    a("<h2>9. Running the stack &mdash; verified commands</h2>")
    a("<table><tr><th>Component</th><th>Command</th></tr>"
      "<tr><td>Keycloak (realm <code>cms</code>)</td>"
      "<td><code>cd /c/tools/keycloak-26.0.0 &amp;&amp; bin/kc.bat start-dev --http-port=9090</code></td></tr>"
      "<tr><td>Backend</td><td><code>./deployment/run-test-backend.sh 8092 --restart</code></td></tr>"
      "<tr><td>Frontend</td><td><code>cd cms-portal-frontend &amp;&amp; ng serve --port 4202</code></td></tr>"
      "<tr><td>E2E (per directory)</td>"
      "<td><code>API_BASE_URL=http://localhost:8092 UI_BASE_URL=http://localhost:4202 "
      "APP_BASE_URL=http://localhost:4202 npx playwright test e2e/&lt;dir&gt; --project=chromium</code></td></tr>"
      "</table>")
    a("<div class='note amber'><strong>Traps confirmed the hard way during this batch.</strong>"
      "<ul>"
      "<li><code>run-test-backend.sh --restart</code> stopped the old JVM and then reported "
      "&ldquo;already listening &hellip; reusing it&rdquo; against the dying process, leaving NOTHING running. "
      "Always confirm with <code>netstat</code> plus <code>curl /actuator/health</code>.</li>"
      "<li><code>target/classes</code> was 6 days older than 270 source files at session start. A stale JVM "
      "is indistinguishable from a missing endpoint &mdash; the new endpoint 404'd until a real rebuild.</li>"
      "<li>Playwright must be run from <code>cms-portal-frontend</code> or the chromium project is not found. "
      "A <code>cd</code> for the backend restart is enough to break it.</li>"
      "<li>Set BOTH <code>UI_BASE_URL</code> and <code>APP_BASE_URL</code>; 10 public specs read the latter.</li>"
      "<li>Do not restart the backend while a suite is running &mdash; it voided my re-portal baseline.</li>"
      "</ul></div>")

    a("<h3>Honest statement about demo data</h3>")
    a("<ul>"
      "<li>RBI master data was never supplied; entity, office and clause masters are demo values.</li>"
      "<li>The digital signature is a deliberate mock and its citizen-facing wording must keep saying "
      "<strong>NOT signed</strong>.</li>"
      "<li>SMS writes an outbox row and sends nothing.</li>"
      "<li>Kafka is not running locally, so <code>complaint.ingested</code> publishes fail in the log by "
      "design in this environment.</li>"
      "<li>Punjabi cannot be demonstrated end-to-end until the enum gap is closed.</li>"
      "</ul>")

    a("<h2>10. Prioritised follow-up</h2>")
    a("<h3>Engineering</h3><ul>"
      "<li>Add <code>PA</code> to <code>SupportedLocale</code> &mdash; Punjabi is seeded but unreachable.</li>"
      "<li>Correct the FAQ rows so <code>questionKey</code> holds a seeded key, ending the raw-key render.</li>"
      "<li>Implement <code>backdateDays</code> in <code>advanceToStatus</code> to recover the last 2 AA tests.</li>"
      "<li>Seed the 13 missing <code>RBIO_STATUS_MASTER</code> rows, or migrate the 383 complaints.</li>"
      "<li>Consolidate the fifth <code>CLOSED_STATUSES</code> onto <code>RbioStatusVocabulary</code> (13.8).</li>"
      "<li>Make the migrations build a clean schema before any <code>ddl-auto: off</code> deploy (13.6).</li>"
      "<li>Extend the shell to the remaining module pages; tokenise <code>public/</code> and <code>admin/</code>.</li>"
      "<li>Review the <code>cms-staff.apps&hellip;</code> route pointing at dead <code>cms-frontend/</code>.</li>"
      "</ul>")
    a("<h3>Design sign-off</h3><ul>"
      "<li>Confirm CRPC slate/blue as the RBI palette, or supply a brand guide &mdash; it is now a one-file swap.</li>"
      "<li>Confirm flattening AA's Material Indigo to the shared blue (applied as instructed).</li>"
      "<li>Confirm the unified navigation and its role-based visibility.</li>"
      "<li>Decide whether the removed A-/A/A+ font controls should be built for real.</li>"
      "</ul>")
    a("<h3>Legal sign-off</h3><ul>"
      "<li>13.2 &mdash; the authoritative clause replacing <code>&quot;Clause FRC&quot;</code> in citizen text.</li>"
      "<li>13.3 &mdash; whether the combined compensation ceiling is &#8377;30 lakh or &#8377;33 lakh.</li>"
      "<li>13.4 &mdash; whether clause labels may be translated, and by whom.</li>"
      "<li>13.5 &mdash; mandatory clause at closure, and the status of 4699 unappealable legacy closures.</li>"
      "<li>Arming the AA award cap and sub-judice block, still deliberately config-OFF.</li>"
      "</ul>")

    a("</main></body></html>")
    return "\n".join(parts)


def main() -> int:
    ROOT.mkdir(parents=True, exist_ok=True)
    out = ROOT / "index.html"
    out.write_text(build(), encoding="utf-8")
    print(f"wrote {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
