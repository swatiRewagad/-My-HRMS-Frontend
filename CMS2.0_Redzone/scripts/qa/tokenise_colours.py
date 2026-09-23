#!/usr/bin/env python3
"""Replace hardcoded hex colours in component stylesheets with design tokens.

Hand-editing ~340 distinct colours across ~50 components is unreviewable and regresses.
This applies one audited hex -> token mapping, reports what it changed, and reports every
colour it deliberately left alone so the residue is a measured number rather than a guess.

Usage:
  python scripts/qa/tokenise_colours.py --module rbio            # apply
  python scripts/qa/tokenise_colours.py --module rbio --dry-run  # preview
  python scripts/qa/tokenise_colours.py --report                 # residual hexes, all modules
"""
from __future__ import annotations

import argparse
import re
import sys
from collections import Counter
from pathlib import Path

FRONTEND = Path(__file__).resolve().parents[2] / "cms-portal-frontend"
COMPONENTS = FRONTEND / "src" / "app" / "components"

# Canonical CRPC palette -> semantic token. Grouped by role, not by hue, so that a value
# appearing in two roles (e.g. #f8fafc as both page background and row hover) maps to the
# role that dominates its usage in CRPC.
MAPPING: dict[str, str] = {
    # ── surfaces ──
    "#f8fafc": "var(--surface-page)",
    "#ffffff": "var(--surface-card)",
    "#fff": "var(--surface-card)",
    "#f1f5f9": "var(--surface-subtle)",
    "#f5f7fa": "var(--surface-sunken)",
    "#f4f6f9": "var(--surface-sunken)",
    "#f9fafb": "var(--surface-subtle)",
    "#f0f0f0": "var(--surface-subtle)",
    "#f3f4f6": "var(--surface-subtle)",
    # ── borders ──
    "#e2e8f0": "var(--border-subtle)",
    "#e2e8f0ff": "var(--border-subtle)",
    "#cbd5e1": "var(--border-strong)",
    "#d1d5db": "var(--border-input)",
    "#e0e0e0": "var(--border-subtle)",
    "#e5e7eb": "var(--border-subtle)",
    "#ddd": "var(--border-subtle)",
    "#eee": "var(--border-subtle)",
    # ── text ──
    "#1e293b": "var(--text-heading)",
    "#334155": "var(--text-body)",
    "#475569": "var(--text-secondary)",
    "#64748b": "var(--text-muted)",
    "#94a3b8": "var(--text-subtle)",
    "#6b7280": "var(--text-muted)",
    "#9ca3af": "var(--text-subtle)",
    "#1f2937": "var(--text-heading)",
    "#374151": "var(--state-neutral-fg)",
    "#333": "var(--text-body)",
    "#555": "var(--text-secondary)",
    "#666": "var(--text-muted)",
    "#5f6368": "var(--text-muted)",
    "#78909c": "var(--text-subtle)",
    # ── brand: the AA Material Indigo flattens onto the CRPC blue ──
    "#3b82f6": "var(--brand-primary)",
    "#2563eb": "var(--brand-primary-hover)",
    "#1e40af": "var(--brand-primary-strong)",
    "#eff6ff": "var(--brand-primary-bg)",
    "#dbeafe": "var(--brand-primary-bg-strong)",
    "#1a237e": "var(--brand-primary-strong)",
    "#3949ab": "var(--brand-primary)",
    "#e8eaf6": "var(--brand-primary-bg)",
    "#1565c0": "var(--brand-primary)",
    "#0d47a1": "var(--brand-primary-strong)",
    "#e8f0fe": "var(--brand-primary-bg)",
    # ── success ──
    "#d1fae5": "var(--state-success-bg)",
    "#065f46": "var(--state-success-fg)",
    "#10b981": "var(--state-success-solid)",
    "#16a34a": "var(--state-success-solid)",
    "#22c55e": "var(--state-success-solid)",
    "#dcfce7": "var(--state-success-bg)",
    "#166534": "var(--state-success-fg)",
    "#e8f5e9": "var(--state-success-bg)",
    "#2e7d32": "var(--state-success-fg)",
    "#f0fdf4": "var(--state-success-bg)",
    "#bbf7d0": "var(--state-success-bg)",
    # ── warning ──
    "#fef3c7": "var(--state-warning-bg)",
    "#92400e": "var(--state-warning-fg)",
    "#f59e0b": "var(--state-warning-solid)",
    "#fff8e1": "var(--state-warning-bg)",
    "#fff3e0": "var(--state-warning-bg)",
    "#e65100": "var(--state-warning-fg)",
    "#ea580c": "var(--state-warning-solid)",
    # ── danger ──
    "#fee2e2": "var(--state-danger-bg)",
    "#991b1b": "var(--state-danger-fg)",
    "#ef4444": "var(--state-danger-solid)",
    "#dc2626": "var(--state-danger-solid-hover)",
    "#b91c1c": "var(--state-danger-fg)",
    "#fef2f2": "var(--state-danger-bg)",
    "#fecaca": "var(--state-danger-bg)",
    "#ffebee": "var(--state-danger-bg)",
    # ── info / neutral ──
    "#e0f2fe": "var(--state-info-bg)",
    "#075985": "var(--state-info-fg)",
    # ── citizen-facing brand (public/ only) ──
    # Deliberately NOT mapped onto --brand-primary: the public portal has its own identity and
    # flattening it onto the staff palette would change what every complainant sees.
    "#472293": "var(--citizen-primary)",
    "#4a3fc7": "var(--citizen-primary-alt)",
    "#2460b9": "var(--citizen-accent)",
    "#146ef5": "var(--citizen-accent-bright)",
    "#f0f4ff": "var(--citizen-surface-tint)",
    "#1a1a2e": "var(--citizen-surface-dark)",
    # ── remaining neutrals and states seen in public/ and admin/ ──
    "#d32f2f": "var(--state-danger-solid-hover)",
    "#999": "var(--text-subtle)",
    "#888": "var(--text-subtle)",
    "#f5f5f5": "var(--surface-subtle)",
    "#d1d7e0": "var(--border-strong)",
    "#4b5563": "var(--text-secondary)",
    # ── categorical accents ──
    "#ede9fe": "var(--accent-violet-bg)",
    "#5b21b6": "var(--accent-violet-fg)",
    "#e0e7ff": "var(--accent-indigo-bg)",
    "#3730a3": "var(--accent-indigo-fg)",
    "#ccfbf1": "var(--accent-teal-bg)",
    "#115e59": "var(--accent-teal-fg)",
    "#fce7f3": "var(--accent-pink-bg)",
    "#9d174d": "var(--accent-pink-fg)",
}

HEX_RE = re.compile(r"#[0-9a-fA-F]{3,8}\b")

# Colours intentionally NOT tokenised, with the reason recorded for the report.
KEEP: dict[str, str] = {
    "#000": "opacity-composited shadow/overlay base, not a surface colour",
    "#000000": "opacity-composited shadow/overlay base, not a surface colour",
    "#6a11cb": "index.html theme-color meta, outside the component layer",
}


def target_files(module: str | None) -> list[Path]:
    root = COMPONENTS / module if module else COMPONENTS
    return sorted(p for p in root.rglob("*") if p.suffix in {".scss", ".css", ".html", ".ts"})


def apply(module: str, dry_run: bool) -> int:
    changed_files = 0
    total_subs = 0
    per_token: Counter[str] = Counter()

    for path in target_files(module):
        original = path.read_text(encoding="utf-8")

        def sub(match: re.Match[str]) -> str:
            raw = match.group(0)
            token = MAPPING.get(raw.lower())
            if token is None:
                return raw
            per_token[f"{raw.lower()} -> {token}"] += 1
            return token

        # Only stylesheet-ish content: a hex inside a .ts string may be chart config or an
        # inline style the tokens cannot reach, so restrict .ts to style-bearing lines.
        if path.suffix == ".ts":
            lines = original.splitlines(keepends=True)
            out = []
            for line in lines:
                if "style" in line.lower() or "color" in line.lower() or "background" in line.lower():
                    out.append(HEX_RE.sub(sub, line))
                else:
                    out.append(line)
            updated = "".join(out)
        else:
            updated = HEX_RE.sub(sub, original)

        if updated != original:
            changed_files += 1
            if not dry_run:
                path.write_text(updated, encoding="utf-8")

    total_subs = sum(per_token.values())
    verb = "would replace" if dry_run else "replaced"
    print(f"[{module}] {verb} {total_subs} hex occurrence(s) across {changed_files} file(s)")
    for entry, count in per_token.most_common():
        print(f"  {count:4d}  {entry}")
    return total_subs


def report() -> None:
    print(f"{'module':<12} {'distinct':>9} {'occurrences':>12}")
    grand: Counter[str] = Counter()
    for module in ("crpc", "rbio", "aa", "re-portal", "cepc", "public", "admin", "shared"):
        root = COMPONENTS / module
        if not root.exists():
            continue
        found: Counter[str] = Counter()
        for path in target_files(module):
            for match in HEX_RE.finditer(path.read_text(encoding="utf-8")):
                found[match.group(0).lower()] += 1
        grand.update(found)
        print(f"{module:<12} {len(found):>9} {sum(found.values()):>12}")
    print(f"\n{'TOTAL':<12} {len(grand):>9} {sum(grand.values()):>12}")
    print("\nTop residual colours:")
    for hex_value, count in grand.most_common(25):
        note = KEEP.get(hex_value, "")
        print(f"  {count:5d}  {hex_value:<10} {note}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--module", help="module folder under src/app/components")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--report", action="store_true")
    args = parser.parse_args()

    if args.report:
        report()
        return 0
    if not args.module:
        parser.error("--module is required unless --report is given")
    apply(args.module, args.dry_run)
    return 0


if __name__ == "__main__":
    sys.exit(main())
