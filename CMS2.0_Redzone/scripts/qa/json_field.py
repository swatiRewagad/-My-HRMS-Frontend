#!/usr/bin/env python3
"""Print fields out of a JSON document, for shell pipelines.

WHY THIS EXISTS: sessions were writing `python -c "import sys,json; d=json.load(sys.stdin); ..."`
several hundred times a night, and every one of those prompted for permission because a wildcard on
the interpreter (`Bash(python *)`) is arbitrary code execution and must never be allowlisted. This
script does the same job from a reviewable, version-controlled file, so ONE narrow allowlist entry
covers all of it.

READ-ONLY BY CONSTRUCTION. It opens nothing for writing, imports no subprocess, and never evaluates
its input. That property is the whole point — do not add a write path here. If you need to modify a
file, use the Edit tool, or write a separate script that is reviewed on its own terms.

USAGE
  # a field from a response piped in
  curl -s http://localhost:8096/api/v1/appeals/APL-1 | python scripts/qa/json_field.py data.status

  # several fields at once
  ... | python scripts/qa/json_field.py data.status data.assignedOfficer data.availableActions

  # from a file instead of stdin
  python scripts/qa/json_field.py --file /tmp/resp.json data.sla.breached

  # the whole document, pretty-printed
  ... | python scripts/qa/json_field.py .

  # list length, and list-of-dict projection
  ... | python scripts/qa/json_field.py --len data.timeline
  ... | python scripts/qa/json_field.py 'hearingHistory[].eventType'

PATH SYNTAX
  dotted keys        data.sla.statusKey
  list index         data.timeline[0].action
  project over list  data.timeline[].action      -> one line per element
  whole document     .

This API wraps most payloads in {"success":..,"data":{..}}, so a bare key is ALSO tried under
"data" automatically: `status` finds `data.status`. That mirrors what the one-liners did by hand
with `d = d.get('data', d)`.
"""
import argparse
import json
import re
import sys

MISSING = "<MISSING>"


def _descend(node, token):
    """Apply one path token. Returns (found, value)."""
    m = re.fullmatch(r"([^\[\]]*)((?:\[\d*\])*)", token)
    if not m:
        return False, None
    key, brackets = m.group(1), m.group(2)

    if key:
        if not isinstance(node, dict) or key not in node:
            return False, None
        node = node[key]

    for br in re.findall(r"\[(\d*)\]", brackets):
        if br == "":
            # projection: caller handles the fan-out
            return True, ("__PROJECT__", node)
        idx = int(br)
        if not isinstance(node, list) or idx >= len(node):
            return False, None
        node = node[idx]
    return True, node


def lookup(doc, path):
    """Resolve a dotted path, retrying under 'data' for this API's envelope."""
    if path == ".":
        return True, doc

    # A LITERAL key containing dots wins over path traversal. The i18n endpoint returns a flat map
    # whose keys are themselves dotted ("aa.order.title"), so splitting on '.' looked for
    # doc['aa']['order']['title'] and reported <MISSING>* for a key that was plainly present.
    # Checked first because an exact hit is never ambiguous.
    if isinstance(doc, dict):
        if path in doc:
            return True, doc[path]
        inner = doc.get("data")
        if isinstance(inner, dict) and path in inner:
            return True, inner[path]

    for base in (doc, doc.get("data") if isinstance(doc, dict) else None):
        if base is None:
            continue
        node, ok = base, True
        for token in path.split("."):
            ok, node = _descend(node, token)
            if not ok:
                break
            if isinstance(node, tuple) and node and node[0] == "__PROJECT__":
                # remaining tokens apply to each element
                rest = path.split(".")[path.split(".").index(token) + 1:]
                out = []
                for el in node[1]:
                    cur, cok = el, True
                    for t in rest:
                        cok, cur = _descend(cur, t)
                        if not cok:
                            break
                    out.append(cur if cok else MISSING)
                return True, out
        if ok:
            return True, node
    return False, None


def render(value):
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (int, float, str)):
        return str(value)
    return json.dumps(value, ensure_ascii=False, default=str)


def main():
    ap = argparse.ArgumentParser(add_help=True, description="Print fields from a JSON document.")
    ap.add_argument("paths", nargs="*", default=["."], help="dotted paths, or '.' for the document")
    ap.add_argument("--file", help="read JSON from this file instead of stdin")
    ap.add_argument("--len", dest="want_len", action="store_true", help="print the length instead")
    ap.add_argument("--keys", action="store_true", help="print the object's keys, one per line")
    ap.add_argument("--raw", action="store_true", help="no labels, values only (default for 1 path)")
    args = ap.parse_args()

    try:
        text = (open(args.file, encoding="utf-8").read() if args.file
                else sys.stdin.read())
    except OSError as e:
        print(f"cannot read input: {e}", file=sys.stderr)
        return 2

    try:
        doc = json.loads(text)
    except json.JSONDecodeError as e:
        # Show what arrived — an HTML error page or an empty body is the usual culprit, and a bare
        # traceback hides that.
        print(f"not JSON ({e}); first 200 chars: {text[:200]!r}", file=sys.stderr)
        return 2

    paths = args.paths or ["."]
    label = len(paths) > 1 and not args.raw

    for path in paths:
        found, value = lookup(doc, path)
        if not found:
            out = MISSING
        elif args.keys:
            out = "\n".join(sorted(value)) if isinstance(value, dict) else MISSING
        elif args.want_len:
            out = str(len(value)) if isinstance(value, (list, dict, str)) else MISSING
        elif isinstance(value, list) and not args.raw and all(
                not isinstance(v, (dict, list)) for v in value):
            out = "\n".join(render(v) for v in value)
        else:
            out = render(value)
        print(f"{path}: {out}" if label else out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
