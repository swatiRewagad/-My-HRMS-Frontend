"""Build a single HTML summary from Maven surefire XML.

Read-only: parses target/surefire-reports/*.xml and writes one HTML file. Exists because
maven-surefire-report-plugin is not present in the offline repository, so `surefire-report:report`
fails with a PluginVersionResolutionException on this machine.
"""
import glob
import io
import os
import sys
import xml.etree.ElementTree as ET
from datetime import datetime

reports = sys.argv[1] if len(sys.argv) > 1 else "target/surefire-reports"
out = sys.argv[2] if len(sys.argv) > 2 else "test-reports/junit-report.html"

suites = []
tot = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0, "time": 0.0}

for path in sorted(glob.glob(os.path.join(reports, "TEST-*.xml"))):
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        continue
    s = {
        "name": root.get("name", os.path.basename(path)),
        "tests": int(root.get("tests", 0)),
        "failures": int(root.get("failures", 0)),
        "errors": int(root.get("errors", 0)),
        "skipped": int(root.get("skipped", 0)),
        "time": float(root.get("time", 0) or 0),
        "cases": [],
    }
    for tc in root.iter("testcase"):
        status = "passed"
        detail = ""
        for tag in ("failure", "error"):
            node = tc.find(tag)
            if node is not None:
                status = tag
                detail = (node.get("message") or "")[:400]
        if tc.find("skipped") is not None:
            status = "skipped"
        s["cases"].append((tc.get("name", "?"), status, float(tc.get("time", 0) or 0), detail))
    for k in ("tests", "failures", "errors", "skipped"):
        tot[k] += s[k]
    tot["time"] += s["time"]
    suites.append(s)

ok = tot["failures"] == 0 and tot["errors"] == 0
banner = "#166534" if ok else "#b91c1c"

h = []
h.append("<!DOCTYPE html><html><head><meta charset='utf-8'><title>CMS 2.0 backend JUnit report</title>")
h.append("<style>body{font-family:Segoe UI,Arial,sans-serif;margin:24px;color:#111}"
         "h1{margin:0 0 4px}.sub{color:#555;margin-bottom:18px}"
         ".banner{background:%s;color:#fff;padding:14px 18px;border-radius:6px;font-size:18px;"
         "font-weight:600;margin-bottom:18px}"
         "table{border-collapse:collapse;width:100%%;margin-bottom:24px}"
         "th,td{border:1px solid #ddd;padding:6px 9px;text-align:left;font-size:13px}"
         "th{background:#f3f4f6}tr:nth-child(even){background:#fafafa}"
         ".n{text-align:right}.bad{color:#b91c1c;font-weight:600}.good{color:#166534}"
         ".skip{color:#92400e}details{margin:4px 0}summary{cursor:pointer}"
         "code{background:#f3f4f6;padding:1px 4px;border-radius:3px}</style></head><body>" % banner)
h.append("<h1>CMS 2.0 &mdash; Backend JUnit report</h1>")
h.append("<div class='sub'>cms-backend &middot; generated %s</div>"
         % datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
h.append("<div class='banner'>%s &mdash; %d tests, %d failures, %d errors, %d skipped (%.1fs)</div>"
         % ("ALL TESTS PASSED" if ok else "FAILURES PRESENT",
            tot["tests"], tot["failures"], tot["errors"], tot["skipped"], tot["time"]))

h.append("<h2>Suites</h2><table><tr><th>Suite</th><th class='n'>Tests</th><th class='n'>Fail</th>"
         "<th class='n'>Err</th><th class='n'>Skip</th><th class='n'>Time (s)</th></tr>")
for s in sorted(suites, key=lambda x: (-(x["failures"] + x["errors"]), x["name"])):
    bad = s["failures"] + s["errors"] > 0
    h.append("<tr><td>%s</td><td class='n'>%d</td><td class='n %s'>%d</td><td class='n %s'>%d</td>"
             "<td class='n %s'>%d</td><td class='n'>%.2f</td></tr>"
             % (s["name"], s["tests"], "bad" if s["failures"] else "", s["failures"],
                "bad" if s["errors"] else "", s["errors"],
                "skip" if s["skipped"] else "", s["skipped"], s["time"]))
h.append("</table>")

bad_suites = [s for s in suites if s["failures"] + s["errors"] > 0]
if bad_suites:
    h.append("<h2>Failures and errors</h2>")
    for s in bad_suites:
        h.append("<h3>%s</h3><table><tr><th>Test</th><th>Status</th><th>Message</th></tr>" % s["name"])
        for name, status, _t, detail in s["cases"]:
            if status in ("failure", "error"):
                h.append("<tr><td>%s</td><td class='bad'>%s</td><td><code>%s</code></td></tr>"
                         % (name, status, (detail or "").replace("<", "&lt;")))
        h.append("</table>")

h.append("<h2>All tests</h2>")
for s in sorted(suites, key=lambda x: x["name"]):
    h.append("<details><summary>%s &mdash; %d tests</summary><table>"
             "<tr><th>Test</th><th>Status</th><th class='n'>Time (s)</th></tr>" % (s["name"], s["tests"]))
    for name, status, t, _d in s["cases"]:
        cls = {"passed": "good", "skipped": "skip"}.get(status, "bad")
        h.append("<tr><td>%s</td><td class='%s'>%s</td><td class='n'>%.3f</td></tr>"
                 % (name, cls, status, t))
    h.append("</table></details>")

h.append("</body></html>")

d = os.path.dirname(out)
if d:
    try:
        os.makedirs(d)
    except OSError:
        pass
io.open(out, "w", encoding="utf-8").write("\n".join(h))
print("%s: %d tests, %d failures, %d errors, %d skipped"
      % (out, tot["tests"], tot["failures"], tot["errors"], tot["skipped"]))
