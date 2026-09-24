"""Apply V102 / oracle-V100 (RE-window {{days}} interpolation) to a RUNNING backend.

The EligibilityTranslationSeeder is insert-if-absent, so a backend that has already seeded
eligibility.block_less_than_30_days keeps the baked "30" even after the seeder source is corrected —
only a fresh DB or the migration rewrites it. This does the same rewrite through
POST /api/v1/i18n/translations, which also evicts the translations cache, so an already-running
instance picks it up without a restart.

Idempotent: re-running is a no-op once the literal is gone.

Usage:  python scripts/qa/apply_re_window_interpolation.py [base_url]
"""
import json
import sys
import urllib.error
import urllib.request

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8092").rstrip("/")

# Every citizen-facing key that quotes the RE response window.
DAYS_KEYS = [
    "eligibility.block_less_than_30_days",
    "wizard.timeline_step2",
    "wizard.timeline_step2_desc",
]
OPENS_KEY = "eligibility.re_window_opens_on"
OPENS_EN = "You may file your complaint with the RBI Ombudsman on or after {{date}}."
UNAVAILABLE_KEY = "wizard.check_unavailable"
UNAVAILABLE_EN = (
    "The eligibility check could not be completed because the service is unavailable. "
    "Please retry. This check is advisory only — you may still proceed to file your complaint."
)

# Bengali stores the digits in Bengali numerals, so an ASCII replace silently skips it.
LITERALS = {"en": "30", "hi": "30", "mr": "30", "te": "30", "ta": "30", "gu": "30",
            "ur": "30", "kn": "30", "ml": "30", "bn": "৩০"}


def get(path):
    with urllib.request.urlopen(f"{BASE}{path}", timeout=30) as r:
        return json.loads(r.read().decode("utf-8"))


def post(path, payload):
    req = urllib.request.Request(
        f"{BASE}{path}", data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"}, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status
    except urllib.error.HTTPError as e:
        return f"{e.code} {e.read().decode('utf-8', 'replace')[:200]}"


def main():
    changed = 0

    for locale, literal in LITERALS.items():
        body = get(f"/api/v1/i18n/translations/{locale}")
        t = body.get("data", body)
        for key in DAYS_KEYS:
            value = t.get(key)
            if not value or value == key:
                print(f"  {locale} {key}: absent — skipped")
                continue
            if "{{days}}" in value and literal not in value:
                print(f"  {locale} {key}: already interpolated")
                continue
            rewritten = value.replace(literal, "{{days}}")
            print(f"  {locale} {key}: "
                  f"{post('/api/v1/i18n/translations', {'code': key, 'locale': locale, 'value': rewritten})}")
            changed += 1

    # The new keys are English-only on purpose: the nine Indian-language values must be authored by
    # the translation team, and the portal already falls back to English for a missing key.
    en = (lambda b: b.get("data", b))(get("/api/v1/i18n/translations/en"))
    for key, module, desc, value in [
        (OPENS_KEY, "eligibility", "Notice: date the RE window opens", OPENS_EN),
        (UNAVAILABLE_KEY, "wizard", "Eligibility check unavailable", UNAVAILABLE_EN),
    ]:
        if en.get(key) in (None, key):
            print(f"  key {key}: {post('/api/v1/i18n/keys', {'code': key, 'module': module, 'description': desc, 'defaultValue': value})}")
            print(f"  en  {key}: {post('/api/v1/i18n/translations', {'code': key, 'locale': 'en', 'value': value})}")
            changed += 1
        else:
            print(f"  {key}: already present")

    print(f"done, {changed} change(s)")


if __name__ == "__main__":
    main()
