const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const script = fs.readFileSync(path.resolve(__dirname, '../index.html'), 'utf8').match(/<script>([\s\S]*?)<\/script>/)[1];
const code = 'A'.repeat(43);

function page(userAgent, pathname, search = '') {
  const elements = { open: { hidden: true }, download: { className: 'primary' }, lead: {} };
  const location = { pathname, search };
  vm.runInNewContext(script, {
    location, navigator: { userAgent }, URLSearchParams, encodeURIComponent,
    document: { getElementById: id => elements[id] },
    setTimeout: () => 1, clearTimeout: () => {}, addEventListener: () => {},
  });
  return { elements, location };
}

test('Android offers an explicit package intent with a safe browser fallback', () => {
  const result = page('Android', '/', '?saqz_invite=' + code);
  assert.equal(result.elements.open.hidden, false);
  assert.equal(result.elements.open.href,
    `intent://links.saqz.app/?saqz_invite=${code}#Intent;scheme=https;package=app.saqz;S.browser_fallback_url=https%3A%2F%2Fsaqz.app;end`);
  assert.equal(result.location.href, undefined);
});

test('attendance decline and onboarding reach the same Android routes', () => {
  assert.ok(page('Android', '/attendance/' + code, '?saqz_intent=decline').elements.open.href
    .startsWith(`intent://links.saqz.app/attendance/${code}?saqz_intent=decline#Intent;`));
  assert.ok(page('Android', '/', '?saqz_onboarding=' + code).elements.open.href
    .startsWith(`intent://links.saqz.app/?saqz_onboarding=${code}#Intent;`));
});

test('iOS retains its native route fallback', () => {
  const result = page('iPhone', '/attendance/' + code, '?saqz_intent=decline');
  assert.equal(result.location.href, 'saqz:///attendance/' + code + '?saqz_intent=decline');
  assert.equal(result.elements.open.href, result.location.href);
});

test('malformed codes cannot inject an Android intent or a destination', () => {
  for (const search of ['?saqz_invite=bad', '?saqz_invite=' + code + '%23Intent%3Bpackage%3Devil', '?url=https://evil.test']) {
    const result = page('Android', '/', search);
    assert.equal(result.elements.open.hidden, true);
    assert.equal(result.elements.open.href, undefined);
    assert.equal(result.location.href, undefined);
  }
});
