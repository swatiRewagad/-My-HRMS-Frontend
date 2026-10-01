#!/usr/bin/env node
/**
 * Counts pass / fail / did-not-run from a Playwright JSON report.
 *
 * WHY THIS EXISTS: run-full-suite.sh used to scrape the summary line out of the log with
 *   grep -oE '[0-9]+ (passed|failed|skipped|flaky|did not run)'
 * which conflates two completely different things. When a `describe.serial` beforeAll throws,
 * Playwright reports ONE failure and marks every remaining test in the file "did not run" — so a
 * single wrong password in a 16-test file read as a 16-test defect. The 2026-10-01 gate inflated
 * three harness bugs into ~100 "failures" exactly this way.
 *
 * A test is classified by its results array:
 *   FAIL        — any result whose status is 'failed' / 'timedOut' / 'interrupted'
 *   FLAKY       — passed eventually but failed at least once (expectedStatus 'passed', >1 result)
 *   PASS        — a result with status 'passed'
 *   DID-NOT-RUN — every result is 'skipped' AND the test was expected to run
 *   SKIPPED     — expectedStatus is 'skipped' (an explicit test.skip, e.g. a service is absent)
 *
 * The last distinction is the point: safe-deactivation.spec.ts deliberately test.skip()s with a
 * reason when cms-workflow-service is down. That is an honest skip and must NOT be reported as a
 * failure — but nor may it be reported as a pass.
 *
 * Usage:  node e2e/review/count-results.js <report.json> [...]
 *         node e2e/review/count-results.js --tsv review-json/aa.json
 */
'use strict';

const fs = require('fs');

const FAILED_STATUSES = new Set(['failed', 'timedOut', 'interrupted']);

/** Walks the nested suite tree and yields every spec's tests. */
function* eachTest(suite) {
  for (const spec of suite.specs ?? []) {
    for (const test of spec.tests ?? []) {
      yield { spec, test };
    }
  }
  for (const child of suite.suites ?? []) {
    yield* eachTest(child);
  }
}

function classify(test) {
  const results = test.results ?? [];
  if (results.some((r) => FAILED_STATUSES.has(r.status))) return 'fail';
  const passed = results.some((r) => r.status === 'passed');
  if (passed) return results.length > 1 ? 'flaky' : 'pass';
  // Nothing failed and nothing passed → it never executed a body.
  // An explicit test.skip() is declared via expectedStatus; anything else is a cascade casualty.
  if (test.expectedStatus === 'skipped') return 'skipped';
  return 'didNotRun';
}

function tally(reportPath) {
  const counts = { pass: 0, fail: 0, flaky: 0, skipped: 0, didNotRun: 0 };
  const failures = [];
  const didNotRun = [];

  let report;
  try {
    report = JSON.parse(fs.readFileSync(reportPath, 'utf8'));
  } catch (err) {
    return { counts, failures, didNotRun, error: err.message };
  }

  for (const suite of report.suites ?? []) {
    for (const { spec, test } of eachTest(suite)) {
      const kind = classify(test);
      counts[kind] += 1;
      const label = `${spec.file}:${spec.line} › ${spec.title}`;
      if (kind === 'fail') failures.push(label);
      if (kind === 'didNotRun') didNotRun.push(label);
    }
  }
  return { counts, failures, didNotRun };
}

function main() {
  const args = process.argv.slice(2);
  const tsv = args.includes('--tsv');
  const paths = args.filter((a) => a !== '--tsv');
  if (paths.length === 0) {
    console.error('usage: count-results.js [--tsv] <report.json> [...]');
    process.exit(2);
  }

  let anyFail = false;
  for (const p of paths) {
    const { counts, failures, didNotRun, error } = tally(p);
    if (error) {
      console.log(`${p}: NO REPORT (${error})`);
      anyFail = true;
      continue;
    }
    const line =
      `pass=${counts.pass} fail=${counts.fail} did-not-run=${counts.didNotRun} ` +
      `skipped=${counts.skipped} flaky=${counts.flaky}`;
    console.log(tsv ? `${p}\t${line}` : `${p}: ${line}`);
    if (!tsv) {
      for (const f of failures) console.log(`    FAIL        ${f}`);
      // Any did-not-run is itself a defect in the harness: a beforeAll threw and hid real tests.
      for (const d of didNotRun) console.log(`    DID-NOT-RUN ${d}`);
    }
    if (counts.fail > 0 || counts.didNotRun > 0) anyFail = true;
  }
  process.exit(anyFail ? 1 : 0);
}

main();
