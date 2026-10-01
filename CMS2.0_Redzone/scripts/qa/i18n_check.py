#!/usr/bin/env python3
"""Verify translation keys across the ten supported locales, over the live API.

WHY THIS EXISTS: every AA session hand-rolled the same `python -c` loop to fetch
/api/v1/i18n/translations/<locale> for ten locales and compare values. Each one prompted for
permission. This is that loop, reviewable and in git, so one narrow allowlist entry covers it.

READ-ONLY BY CONSTRUCTION, with ONE deliberate exception: --evict issues the documented
cache-eviction upsert, because newly seeded locales are INVISIBLE over the API until the
translation cache is evicted, and every session has lost time to that. The upsert re-writes an
existing key to its OWN current value, so it changes no data. Nothing else here writes anything.

USAGE
  # are these keys present in all ten locales, and genuinely translated?
  python scripts/qa/i18n_check.py --keys aa.detail.title aa.order.title

  # every key in a module (as seeded in the DB), checked across all locales
  python scripts/qa/i18n_check.py --module aa-frontend

  # keys referenced by the AA components but possibly never seeded
  python scripts/qa/i18n_check.py --scan-components

  # force a cache evict first (see note above)
  python scripts/qa/i18n_check.py --evict --module aa-workflow

  # a different backend
  python scripts/qa/i18n_check.py --base http://localhost:8097 --keys aa.sla.breached

WHAT IT ASSERTS
  1. the key resolves in ALL ten locales (a missing key renders as its own id on screen);
  2. each of the nine non-English values DIFFERS from the English — an identical value is an
     untranslated placeholder, which reads as a bug to a citizen using the portal in their own
     language. This is the check that catches a seeder that "ran" but copied English through.

Exit code 0 when everything passes, 1 when anything is missing or untranslated, so it is usable as
a gate in a shell pipeline.
"""
import argparse
import json
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

TEN_LOCALES = ["en", "hi", "mr", "bn", "te", "ta", "gu", "ur", "kn", "ml"]
DEFAULT_BASE = "http://localhost:8096"


def get_json(url, timeout=20):
    try:
        with urllib.request.urlopen(url, timeout=timeout) as r:
            return json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        print(f"HTTP {e.code} for {url}", file=sys.stderr)
    except Exception as e:
        print(f"{type(e).__name__} for {url}: {e}", file=sys.stderr)
    return None


def fetch_locale(base, locale):
    doc = get_json(f"{base}/api/v1/i18n/translations/{locale}")
    if doc is None:
        return None
    # Responses are sometimes bare and sometimes wrapped in {"data": {...}}.
    return doc.get("data", doc) if isinstance(doc, dict) else None


def evict_cache(base, sample_key, sample_value):
    """Re-upsert one existing key at its CURRENT value to trigger @CacheEvict(allEntries=true).

    A bare POST evicts nothing — the endpoint needs {code, locale, value}. Because the value is
    unchanged, no data is modified.
    """
    body = json.dumps({"code": sample_key, "locale": "en", "value": sample_value}).encode()
    req = urllib.request.Request(
        f"{base}/api/v1/i18n/translations", data=body, method="POST",
        headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            print(f"cache evict: HTTP {r.status}")
            return True
    except Exception as e:
        print(f"cache evict failed ({type(e).__name__}: {e})", file=sys.stderr)
        return False


def scan_component_keys(repo_root):
    """Translation keys referenced by the AA components, as 'key' | translate."""
    aa = repo_root / "cms-portal-frontend" / "src" / "app" / "components" / "aa"
    if not aa.is_dir():
        print(f"no AA components dir at {aa}", file=sys.stderr)
        return []
    rx = re.compile(r"""['"]((?:aa|classification|notifications)\.[a-z0-9_.]+)['"]""")
    keys = set()
    for path in list(aa.rglob("*.html")) + list(aa.rglob("*.ts")):
        try:
            keys.update(rx.findall(path.read_text(encoding="utf-8", errors="replace")))
        except OSError:
            pass
    return sorted(keys)


def main():
    ap = argparse.ArgumentParser(description="Check i18n keys across all ten locales.")
    ap.add_argument("--base", default=DEFAULT_BASE, help=f"backend base URL (default {DEFAULT_BASE})")
    ap.add_argument("--keys", nargs="*", help="explicit keys to check")
    ap.add_argument("--module", help="check every key whose id starts with this module prefix")
    ap.add_argument("--scan-components", action="store_true",
                    help="check every key referenced in components/aa/**")
    ap.add_argument("--evict", action="store_true", help="evict the translation cache first")
    ap.add_argument("--quiet", action="store_true", help="only print problems and the summary")
    args = ap.parse_args()

    english = fetch_locale(args.base, "en")
    if english is None:
        print(f"cannot reach {args.base} — is the backend up?", file=sys.stderr)
        return 2

    if args.evict:
        first = next(iter(sorted(english)), None)
        if first:
            evict_cache(args.base, first, english[first])
            english = fetch_locale(args.base, "en") or english

    keys = list(args.keys or [])
    if args.scan_components:
        repo_root = Path(__file__).resolve().parents[2]
        keys += scan_component_keys(repo_root)
    if args.module:
        # Module membership is not exposed per-key over the API, so approximate by id prefix.
        prefix = args.module.replace("aa-", "aa.").rstrip(".")
        keys += [k for k in english if k.startswith(prefix)]
    if not keys:
        print("nothing to check: pass --keys, --module or --scan-components", file=sys.stderr)
        return 2
    keys = sorted(set(keys))

    maps = {}
    for loc in TEN_LOCALES:
        m = fetch_locale(args.base, loc)
        if m is None:
            print(f"could not fetch locale {loc}", file=sys.stderr)
            return 2
        maps[loc] = m

    missing = []     # (locale, key)
    copied = []      # (locale, key) present but identical to English
    unseeded = []    # absent from English too -> referenced but never seeded

    for k in keys:
        if not english.get(k, "").strip():
            unseeded.append(k)
            continue
        for loc in TEN_LOCALES:
            val = maps[loc].get(k, "")
            if not val.strip():
                missing.append((loc, k))
            elif loc != "en" and val.strip() == english[k].strip():
                copied.append((loc, k))

    if not args.quiet:
        print(f"checked {len(keys)} keys x {len(TEN_LOCALES)} locales against {args.base}")

    if unseeded:
        print(f"\nREFERENCED BUT NEVER SEEDED ({len(unseeded)}) "
              f"— these render as the raw key on screen:")
        for k in unseeded:
            print(f"  {k}")
    if missing:
        print(f"\nMISSING IN A LOCALE ({len(missing)}):")
        for loc, k in missing[:60]:
            print(f"  [{loc}] {k}")
        if len(missing) > 60:
            print(f"  ... and {len(missing) - 60} more")
    if copied:
        print(f"\nENGLISH COPIED THROUGH ({len(copied)}) "
              f"— present but untranslated:")
        for loc, k in copied[:60]:
            print(f"  [{loc}] {k}")
        if len(copied) > 60:
            print(f"  ... and {len(copied) - 60} more")

    bad = len(unseeded) + len(missing) + len(copied)
    print(f"\n{'FAIL' if bad else 'PASS'}: {bad} problem(s)")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
