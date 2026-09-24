import { test, expect } from '@playwright/test';
import { readFileSync, readdirSync, statSync } from 'fs';
import { join, relative } from 'path';

/**
 * The upload limit is CONFIGURATION, and no source file may hardcode it.
 *
 * <p>The ruling is that configuration decides and the figure is expected to move; it currently stands at
 * 2 MB per file and 25 MB per set, held in `system_config` and served by
 * GET /api/v1/config/upload-limits. What must hold is that ONE place decides.
 *
 * <p>THIS GUARD DELIBERATELY NAMES NO PARTICULAR SIZE. An earlier version forbade the literal `2` because
 * 2 was the stale value at the time; when the ruling moved the figure, the guard was asserting the wrong
 * side of its own rule and would have passed a codebase that hardcoded 5 everywhere. A size-agnostic scan
 * survives the next retune.
 *
 * <p>These tests scan the SOURCE rather than exercising the UI, because a per-screen test cannot prove the
 * ABSENCE of a hardcoded limit on a screen nobody thought to test — and that is exactly how the stale
 * values survived a pass that believed it had removed them.
 */

const SRC = join(__dirname, '..', '..', 'src');

/**
 * The single file permitted to name a size: it holds the fallback for a failed
 * /api/v1/config/upload-limits fetch, which has to be a literal by definition.
 */
const LIMIT_OWNER = join('app', 'services', 'upload-limits.service.ts');

/**
 * `N * 1024 * 1024`, or the byte count of any plausible MB limit written out.
 *
 * ONE megabyte is absent on purpose: `1048576` and a bare `1024 * 1024` are the UNIT OF MEASURE, used by
 * every `formatSize` helper to render a file's own size as "1.4 MB". A limit is a MULTIPLE of the unit.
 * Forbidding the unit would flag each size formatter in the codebase and teach the next reader to
 * disable this guard rather than trust it.
 *
 * KNOWN BLIND SPOT, accepted deliberately: a limit of exactly 1 MB would not be caught here. It would
 * still be caught in prose by HARDCODED_LABEL and, if it reached the fallback, by the
 * frontend/backend equality test below. Distinguishing `size > ONE_MB` from `size < ONE_MB` needs the
 * surrounding expression, and a guard that tries to parse intent is a guard nobody believes.
 */
const HARDCODED_BYTES = /\b(?!1\s*\*)\d+\s*\*\s*1024\s*\*\s*1024\b|\b(?:2097152|3145728|5242880|10485760|20971520|26214400|52428800)\b/;

/** A size stated in prose to the user, e.g. "Maximum size: 5MB". */
const HARDCODED_LABEL = /\b\d+\s?MB\b/i;

function sourceFiles(dir: string, out: string[] = []): string[] {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      sourceFiles(full, out);
    } else if (/\.(ts|html)$/.test(entry) && !/\.spec\.ts$/.test(entry)) {
      out.push(full);
    }
  }
  return out;
}

/**
 * Strips comments so a file may still EXPLAIN a past limit in prose.
 * Without this the guard would forbid documenting why the constant was removed.
 */
function stripComments(source: string): string {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '')
    .replace(/<!--[\s\S]*?-->/g, '');
}

test.describe('Upload limits are configured, never compiled in', () => {

  test('no source file outside upload-limits.service hardcodes a byte limit', () => {
    const offenders: string[] = [];

    for (const file of sourceFiles(SRC)) {
      const rel = relative(SRC, file);
      if (rel === LIMIT_OWNER) continue;
      const code = stripComments(readFileSync(file, 'utf-8'));
      if (HARDCODED_BYTES.test(code)) {
        offenders.push(rel);
      }
    }

    expect(offenders, `these files hardcode a size limit; inject UploadLimitsService instead:\n${offenders.join('\n')}`)
      .toEqual([]);
  });

  test('no template states a size in MB outside a comment', () => {
    const offenders: string[] = [];

    for (const file of sourceFiles(SRC).filter(f => f.endsWith('.html'))) {
      const markup = stripComments(readFileSync(file, 'utf-8'));
      // A rendered file's OWN size, e.g. `{{ f.size / 1048576 }} MB`, is a unit label, not a limit.
      const withoutUnits = markup.replace(/\{\{[^}]*\}\}\s*MB/gi, '');
      if (HARDCODED_LABEL.test(withoutUnits)) {
        offenders.push(relative(SRC, file));
      }
    }

    expect(offenders, `these templates state a fixed MB limit in prose; bind uploadLimits instead:\n${offenders.join('\n')}`)
      .toEqual([]);
  });

  test('file-validator takes its limits as arguments rather than exporting constants', () => {
    const source = readFileSync(join(SRC, 'app', 'utils', 'file-validator.ts'), 'utf-8');

    // These exports were the shared source of the stale limit. Their absence is the fix.
    expect(source).not.toMatch(/export const MAX_FILE_SIZE_MB/);
    expect(source).not.toMatch(/export const MAX_TOTAL_SIZE_MB/);
    expect(source).toMatch(/limits:\s*FileSizeLimits/);
  });

  test('the frontend fallback equals the server fallback', () => {
    const fe = readFileSync(join(SRC, 'app', 'services', 'upload-limits.service.ts'), 'utf-8');
    const be = readFileSync(
      join(__dirname, '..', '..', '..', 'cms-backend', 'src', 'main', 'java', 'com', 'hrms', 'cms',
        'service', 'UploadLimitsService.java'), 'utf-8');

    // A citizen whose /api/v1/config/upload-limits call fails must still be shown the rule the server
    // enforces. These two diverging is the original defect in miniature.
    //
    // BOTH SIDES ARE COMPARED IN BYTES. An earlier version read the frontend's `maxFileSizeMb` digit and
    // the backend's `DEFAULT_MAX_FILE_BYTES = N L * 1024 * 1024` multiplier and compared those, which
    // happens to agree while both are written as `N * 1024 * 1024` but would pass a backend that said
    // `2L * 1024` — the same 2, a thousandth of the limit.
    const feBytes = /maxFileSizeBytes:\s*([\d*\s]+?),/.exec(fe)?.[1];
    const beBytes = /DEFAULT_MAX_FILE_BYTES\s*=\s*([\dL*\s]+?);/.exec(be)?.[1];
    expect(feBytes, 'frontend fallback maxFileSizeBytes not found').toBeTruthy();
    expect(beBytes, 'backend DEFAULT_MAX_FILE_BYTES not found').toBeTruthy();

    // eslint-disable-next-line no-eval -- both operands are matched against [\d*\s] / [\dL*\s] above,
    // so nothing but an arithmetic product of literals can reach here.
    const evalBytes = (expr: string) => eval(expr.replace(/L/g, '')) as number;
    expect(evalBytes(feBytes!), `frontend fallback (${feBytes}) must equal backend default (${beBytes})`)
      .toBe(evalBytes(beBytes!));

    // And the MB figure the UI prints must be that same byte count, not an independently typed digit.
    const feMb = /maxFileSizeMb:\s*(\d+)/.exec(fe)?.[1];
    expect(feMb, 'frontend fallback maxFileSizeMb not found').toBeTruthy();
    expect(Number(feMb)).toBe(Math.floor(evalBytes(feBytes!) / (1024 * 1024)));
  });
});
