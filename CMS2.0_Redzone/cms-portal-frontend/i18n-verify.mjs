// Checks the CEPC i18n seeders are internally consistent, and that every `translateOr` key used in a
// template actually exists in one of them.

import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

const CFG = '../cms-backend/src/main/java/com/hrms/cms/config';
const SEEDERS = ['CepcDashboardI18nSeeder', 'CepcDetailViewI18nSeeder', 'CepcScreensI18nSeeder'];
const EXTRA_KEY_SOURCES = ['TaskGridTranslationSeeder'];

function puts(body) {
  const out = new Map();
  const re = /m\.put\(\s*"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*\)/g;
  let m;
  while ((m = re.exec(body)) !== null) out.set(m[1], m[2]);
  return out;
}

function sections(name) {
  const src = readFileSync(new URL(`${CFG}/${name}.java`, import.meta.url), 'utf8');
  const e = src.indexOf('private Map<String, String> english()');
  const h = src.indexOf('private Map<String, String> hindi()');
  if (e < 0 || h < 0) throw new Error(`cannot find english()/hindi() in ${name}`);
  return { en: puts(src.slice(e, h)), hi: puts(src.slice(h)) };
}

let problems = 0;
const owner = new Map();

for (const name of SEEDERS) {
  const { en, hi } = sections(name);
  const missingHi = [...en.keys()].filter(k => !hi.has(k));
  const orphanHi = [...hi.keys()].filter(k => !en.has(k));
  console.log(`${name.padEnd(28)} en=${String(en.size).padStart(4)}  hi=${String(hi.size).padStart(4)}`);
  for (const k of missingHi) { console.log(`    MISSING HINDI: ${k}`); problems++; }
  for (const k of orphanHi) { console.log(`    HINDI WITHOUT ENGLISH: ${k}`); problems++; }
  for (const k of en.keys()) {
    if (owner.has(k)) { console.log(`    DUPLICATE KEY ACROSS SEEDERS: ${k} (also ${owner.get(k)})`); problems++; }
    else owner.set(k, name);
  }
}

for (const name of EXTRA_KEY_SOURCES) {
  for (const k of sections(name).en.keys()) if (!owner.has(k)) owner.set(k, name);
}

// Every key referenced by a template must exist.
function walk(dir, acc = []) {
  for (const e of readdirSync(dir)) {
    const p = join(dir, e);
    if (statSync(p).isDirectory()) walk(p, acc);
    else if (p.endsWith('.component.html')) acc.push(p);
  }
  return acc;
}

const used = new Map();
for (const f of walk('src/app/components/cepc')) {
  const html = readFileSync(f, 'utf8');
  for (const m of html.matchAll(/['"]((?:ui|status)\.[a-z0-9_.]+)['"]\s*\|\s*translateOr/g)) {
    if (!used.has(m[1])) used.set(m[1], f);
  }
}

console.log(`\nkeys referenced by CEPC templates: ${used.size}`);
for (const [k, f] of used) {
  if (!owner.has(k)) { console.log(`    UNSEEDED KEY IN TEMPLATE: ${k}  (${f})`); problems++; }
}

console.log(`\ntotal CEPC i18n keys defined: ${owner.size}`);
console.log(problems === 0 ? 'OK — no problems found' : `${problems} problem(s) found`);
process.exit(problems === 0 ? 0 : 1);
