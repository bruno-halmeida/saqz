const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const script = fs.readFileSync(path.resolve(__dirname, '../index.html'), 'utf8').match(/<script>([\s\S]*?)<\/script>/)[1];
const code = 'A'.repeat(43);
const play = 'https://play.google.com/store/apps/details?id=app.saqz';
const appStore = 'https://apps.apple.com/br/app/id6812743525';

function page(userAgent, pathname, search = '', source = script) {
  const elements = {
    heading: { textContent: 'Este link abre no app Saqz' },
    lead: { textContent: 'Baixe o app para entrar no grupo ou confirmar sua presença.' },
    primary: { href: 'https://saqz.app', textContent: 'Acessar site do Saqz', hidden: false },
    secondary: { hidden: true },
  };
  const location = { pathname, search };
  vm.runInNewContext(source, {
    location, navigator: { userAgent }, URLSearchParams, encodeURIComponent,
    document: { getElementById: id => elements[id] },
  });
  return { ...elements, location };
}

const withAppStore = script.replace('var appStore = "";', `var appStore = "${appStore}";`);

test('Android invite goes straight to Google Play carrying the invite in the referrer', () => {
  const result = page('Android', '/', '?saqz_invite=' + code);
  const store = `${play}&referrer=saqz_invite%3D${code}`;
  assert.equal(result.primary.href, store);
  assert.equal(result.primary.textContent, 'Baixar no Google Play');
  assert.equal(result.secondary.hidden, false);
  assert.equal(result.secondary.textContent, 'Já instalei, abrir o app');
  assert.equal(result.secondary.href,
    `intent://links.saqz.app/?saqz_invite=${code}#Intent;scheme=https;package=app.saqz;S.browser_fallback_url=${encodeURIComponent(store)};end`);
  assert.equal(result.heading.textContent, 'Entre no grupo pelo app Saqz');
  assert.equal(result.location.href, undefined);
});

test('attendance decline and onboarding reach the same Android routes', () => {
  const attendance = page('Android', '/attendance/' + code, '?saqz_intent=decline');
  assert.ok(attendance.primary.href.startsWith(`intent://links.saqz.app/attendance/${code}?saqz_intent=decline#Intent;`));
  assert.ok(attendance.primary.href.endsWith(`S.browser_fallback_url=${encodeURIComponent(play)};end`));
  assert.equal(attendance.secondary.href, play);
  const onboarding = page('Android', '/', '?saqz_onboarding=' + code);
  assert.ok(onboarding.secondary.href.startsWith(`intent://links.saqz.app/?saqz_onboarding=${code}#Intent;`));
  assert.equal(onboarding.primary.href, `${play}&referrer=saqz_onboarding%3D${code}`);
});

test('iOS attendance still tries the native route on its own', () => {
  const result = page('iPhone', '/attendance/' + code, '?saqz_intent=decline');
  assert.equal(result.location.href, 'saqz:///attendance/' + code + '?saqz_intent=decline');
  assert.equal(result.primary.href, result.location.href);
});

test('iOS invite never opens the scheme on its own and waits for the App Store', () => {
  const result = page('iPhone', '/', '?saqz_invite=' + code);
  assert.equal(result.location.href, undefined);
  assert.equal(result.primary.href, 'https://saqz.app');
  assert.match(result.lead.textContent, /chega em breve na App Store/);
  assert.equal(result.secondary.href, 'saqz:///?saqz_invite=' + code);

  const published = page('iPhone', '/', '?saqz_invite=' + code, withAppStore);
  assert.equal(published.location.href, undefined);
  assert.equal(published.primary.href, appStore);
  assert.equal(published.primary.textContent, 'Baixar na App Store');
  assert.equal(published.secondary.href, 'saqz:///?saqz_invite=' + code);
});

test('desktop keeps the site and asks for the phone', () => {
  const result = page('Macintosh', '/', '?saqz_invite=' + code);
  assert.equal(result.primary.href, 'https://saqz.app');
  assert.equal(result.secondary.hidden, true);
  assert.equal(result.lead.textContent, 'Abra este link no celular para continuar no app.');
});

test('malformed codes cannot inject an Android intent or a destination', () => {
  for (const search of ['?saqz_invite=bad', '?saqz_invite=' + code + '%23Intent%3Bpackage%3Devil', '?url=https://evil.test']) {
    const result = page('Android', '/', search);
    assert.equal(result.secondary.hidden, true);
    assert.equal(result.secondary.href, undefined);
    assert.equal(result.primary.href, play);
    assert.equal(result.location.href, undefined);
  }
});
