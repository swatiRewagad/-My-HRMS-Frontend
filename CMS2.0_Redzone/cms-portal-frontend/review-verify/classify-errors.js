const m = require('../review-screens/manifest.json');
const errs = m.captures.filter(x => (x.consoleErrors || []).length);
const seen = new Map();
for (const c of errs) {
  for (const e of c.consoleErrors) {
    const s = String(e);
    let k;
    if (/TypeError|ReferenceError|NG0|ERROR Error/.test(s)) k = 'JS: ' + s.slice(0, 90);
    else if (/WebSocket/.test(s)) k = 'WEBSOCKET';
    else if (/status of 401/.test(s)) k = 'HTTP 401';
    else if (/status of 403/.test(s)) k = 'HTTP 403';
    else if (/status of 404/.test(s)) k = 'HTTP 404';
    else if (/status of 5/.test(s)) k = 'HTTP 5xx';
    else k = 'OTHER: ' + s.slice(0, 90);
    if (!seen.has(k)) seen.set(k, []);
    if (!seen.get(k).includes(c.id)) seen.get(k).push(c.id);
  }
}
console.log('screens with console errors: ' + errs.length + ' / ' + m.captures.length);
for (const [k, v] of [...seen].sort((a, b) => b[1].length - a[1].length)) {
  console.log(v.length + '  ' + k);
  console.log('     ' + v.join(' '));
}
