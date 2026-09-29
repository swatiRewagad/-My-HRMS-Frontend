#!/usr/bin/env node
/**
 * Builds the reviewable HTML (and the CSV template to mark it up) from manifest.json.
 *
 * Usage: node e2e/review/build-report.js [reviewDir]
 */
const fs = require('fs');
const path = require('path');

const dir = process.argv[2] || 'review-screens';
const manifestPath = path.join(dir, 'manifest.json');
if (!fs.existsSync(manifestPath)) {
  console.error(`No manifest at ${manifestPath} — run the crawl first.`);
  process.exit(1);
}
const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
const captures = manifest.captures || [];

const esc = (s) =>
  String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

const areas = [...new Set(captures.map((c) => c.area))];
const count = (s) => captures.filter((c) => c.status === s).length;

const toCsv = (rows) =>
  rows
    .map((r) => r.map((f) => (/[",\n\r]/.test(String(f ?? '')) ? `"${String(f ?? '').replace(/"/g, '""')}"` : String(f ?? ''))).join(','))
    .join('\r\n');

// ── The mark-up sheet. ONE file, not two: the reviewer needs the context columns to tell screens
// apart in Excel, and the two blank columns to fill in, in the same row. A blank row means
// "no change", so the columns are deliberately last and deliberately empty.
const markupRows = [
  ['Screen ID', 'Area', 'Screen name', 'Role used', 'URL', 'Capture status', 'Change in front end', 'Change in logic (backend)'],
  ...captures.map((c) => [c.id, c.area, c.name, c.actor, c.url, c.status, '', '']),
];
fs.writeFileSync(path.join(dir, 'review-template.csv'), toCsv(markupRows), 'utf8');

// Kept as a read-only index (same IDs) for anyone who wants the context without the blank columns.
fs.writeFileSync(
  path.join(dir, 'screen-index.csv'),
  toCsv([
    ['Screen ID', 'Area', 'Screen name', 'Role used', 'URL', 'Status', 'Detail', 'Console errors'],
    ...captures.map((c) => [c.id, c.area, c.name, c.actor, c.url, c.status, c.detail, (c.consoleErrors || []).length]),
  ]),
  'utf8'
);

const firstOfArea = new Map();
for (const c of captures) if (!firstOfArea.has(c.area)) firstOfArea.set(c.area, c.id);

const cards = captures
  .map((c) => {
    const badge = c.status.toLowerCase();
    const anchor =
      firstOfArea.get(c.area) === c.id
        ? `<div id="first-${esc(c.area.replace(/\s+/g, '-'))}"></div><h2 class="area">${esc(c.area)}</h2>`
        : '';
    const shot = c.shot
      ? `<div class="hint">click the image for the full-size capture</div>
         <a href="${esc(c.shot)}" target="_blank"><img loading="lazy" src="${esc(c.shot)}" alt="${esc(c.id)}"></a>`
      : `<div class="noshot">no screenshot captured</div>`;
    const warn = c.detail ? `<p class="detail">${esc(c.detail)}</p>` : '';
    const errs = c.consoleErrors?.length
      ? `<details class="errs"><summary>${c.consoleErrors.length} console error(s)</summary><pre>${esc(
          c.consoleErrors.join('\n')
        )}</pre></details>`
      : '';
    return `${anchor}
<section class="card" id="${esc(c.id)}" data-area="${esc(c.area)}" data-status="${esc(c.status)}">
  <header>
    <span class="id">${esc(c.id)}</span>
    <span class="badge ${badge}">${esc(c.status)}</span>
    <h3>${esc(c.name)}</h3>
  </header>
  <table class="meta">
    <tr><th>Area</th><td>${esc(c.area)}</td></tr>
    <tr><th>Signed in as</th><td>${esc(c.actor)}</td></tr>
    <tr><th>URL</th><td><a href="${esc(c.url)}" target="_blank">${esc(c.url)}</a></td></tr>
    ${c.finalUrl && c.finalUrl !== c.url ? `<tr><th>Ended up at</th><td>${esc(c.finalUrl)}</td></tr>` : ''}
    <tr><th>Page title</th><td>${esc(c.title)}</td></tr>
    ${c.interaction ? `<tr><th>Before the shot</th><td>${esc(c.interaction)}</td></tr>` : ''}
    ${c.headings?.length ? `<tr><th>Headings</th><td>${esc(c.headings.join(' · '))}</td></tr>` : ''}
  </table>
  ${warn}
  ${errs}
  <div class="shot">${shot}</div>
</section>`;
  })
  .join('\n');

const html = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>CMS 2.0 — Screen Review (${captures.length} screens)</title>
<style>
  :root { --line:#d8dce3; --ink:#1b2430; --muted:#5b6573; }
  * { box-sizing:border-box; }
  body { margin:0; font:14px/1.5 -apple-system,Segoe UI,Roboto,sans-serif; color:var(--ink); background:#f4f6f9; }
  header.top { position:sticky; top:0; z-index:10; background:#fff; border-bottom:1px solid var(--line); padding:14px 20px; }
  header.top h1 { margin:0 0 4px; font-size:19px; }
  .sub { color:var(--muted); font-size:13px; }
  .controls { margin-top:10px; display:flex; gap:8px; flex-wrap:wrap; align-items:center; }
  select,input { padding:6px 8px; border:1px solid var(--line); border-radius:6px; font:inherit; }
  .tallies { display:flex; gap:6px; flex-wrap:wrap; margin-top:8px; }
  .tally { font-size:12px; padding:3px 9px; border-radius:99px; border:1px solid var(--line); background:#fafbfc; }
  main { padding:20px; display:grid; gap:20px; }
  .card { background:#fff; border:1px solid var(--line); border-radius:10px; overflow:hidden; }
  .card header { display:flex; gap:10px; align-items:center; padding:12px 14px; border-bottom:1px solid var(--line); flex-wrap:wrap; }
  .card header h3 { margin:0; font-size:15px; font-weight:600; }
  .id { font:600 13px ui-monospace,SFMono-Regular,Menlo,monospace; background:#eef1f6; padding:3px 8px; border-radius:5px; }
  .badge { font-size:11px; font-weight:700; letter-spacing:.4px; padding:3px 8px; border-radius:99px; text-transform:uppercase; }
  .badge.captured { background:#e6f5ec; color:#1a6b3c; }
  .badge.redirected { background:#fff3e0; color:#8a5200; }
  .badge.blocked { background:#eceff3; color:#4b5563; }
  .badge.error { background:#fdeaea; color:#a11b1b; }
  table.meta { width:100%; border-collapse:collapse; font-size:13px; }
  table.meta th { text-align:left; width:130px; color:var(--muted); font-weight:500; padding:5px 14px; vertical-align:top; }
  table.meta td { padding:5px 14px 5px 0; word-break:break-all; }
  .detail { margin:0; padding:9px 14px; background:#fff8e6; border-top:1px solid #f0e2bf; color:#7a5200; font-size:13px; }
  .errs { padding:8px 14px; border-top:1px solid var(--line); font-size:12px; }
  .errs pre { white-space:pre-wrap; background:#fbfbfc; padding:8px; border-radius:6px; overflow:auto; max-height:170px; }
  /* Large thumbnails: the screenshot is the point of this document, so it gets the full card width
     and is capped by height rather than scaled down to a thumbnail strip. Click opens the full PNG. */
  .shot { border-top:1px solid var(--line); background:#eef0f4; padding:10px; }
  .shot img { display:block; width:100%; height:auto; max-height:1400px; object-fit:contain; object-position:top;
              border:1px solid var(--line); border-radius:6px; background:#fff; }
  .shot .hint { font-size:11px; color:var(--muted); padding:0 0 6px; }
  .jump { display:flex; gap:6px; flex-wrap:wrap; margin-top:8px; }
  .jump a { font-size:12px; padding:3px 9px; border-radius:99px; border:1px solid var(--line); background:#fff;
            text-decoration:none; color:var(--ink); }
  .jump a:hover { background:#eef1f6; }
  h2.area { margin:6px 0 0; font-size:15px; letter-spacing:.3px; color:var(--muted); text-transform:uppercase; }
  .noshot { padding:26px; text-align:center; color:var(--muted); font-style:italic; }
  .hidden { display:none !important; }
</style>
</head>
<body>
<header class="top">
  <h1>CMS 2.0 — Screen Review</h1>
  <div class="sub">
    ${captures.length} screens · captured ${new Date(manifest.generatedAt).toLocaleString()} ·
    app <code>${esc(manifest.appBase)}</code> · api <code>${esc(manifest.apiBase)}</code>
  </div>
  <div class="tallies">
    <span class="tally">CAPTURED ${count('CAPTURED')}</span>
    <span class="tally">REDIRECTED ${count('REDIRECTED')}</span>
    <span class="tally">BLOCKED ${count('BLOCKED')}</span>
    <span class="tally">ERROR ${count('ERROR')}</span>
  </div>
  <div class="controls">
    <label>Area
      <select id="fArea"><option value="">all</option>${areas.map((a) => `<option>${esc(a)}</option>`).join('')}</select>
    </label>
    <label>Status
      <select id="fStatus"><option value="">all</option><option>CAPTURED</option><option>REDIRECTED</option><option>BLOCKED</option><option>ERROR</option></select>
    </label>
    <label>Find <input id="fText" type="search" placeholder="id, name or url"></label>
    <span class="sub" id="shown"></span>
  </div>
  <div class="jump">${areas
    .map((a) => `<a href="#first-${esc(a.replace(/\s+/g, '-'))}">${esc(a)} (${captures.filter((c) => c.area === a).length})</a>`)
    .join('')}</div>
</header>
<main id="cards">
${cards}
</main>
<script>
  const cards = [...document.querySelectorAll('.card')];
  const fArea = document.getElementById('fArea');
  const fStatus = document.getElementById('fStatus');
  const fText = document.getElementById('fText');
  const shown = document.getElementById('shown');
  function apply() {
    const a = fArea.value, s = fStatus.value, t = fText.value.toLowerCase();
    let n = 0;
    for (const c of cards) {
      const ok =
        (!a || c.dataset.area === a) &&
        (!s || c.dataset.status === s) &&
        (!t || c.textContent.toLowerCase().includes(t));
      c.classList.toggle('hidden', !ok);
      if (ok) n++;
    }
    shown.textContent = n + ' of ' + cards.length + ' shown';
  }
  [fArea, fStatus, fText].forEach(el => el.addEventListener('input', apply));
  apply();
</script>
</body>
</html>`;

fs.writeFileSync(path.join(dir, 'screen-review.html'), html, 'utf8');

console.log(`[report] ${captures.length} screens -> ${path.join(dir, 'screen-review.html')}`);
console.log(`[report] mark-up template -> ${path.join(dir, 'review-template.csv')}`);
console.log(`[report] screen index     -> ${path.join(dir, 'screen-index.csv')}`);
console.log(
  `[report] captured ${count('CAPTURED')} · redirected ${count('REDIRECTED')} · blocked ${count('BLOCKED')} · error ${count('ERROR')}`
);
