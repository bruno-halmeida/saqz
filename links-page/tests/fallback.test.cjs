const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const script = fs.readFileSync(path.resolve(__dirname, '../index.html'), 'utf8').match(/<script>([\s\S]*?)<\/script>/)[1];
const code = 'A'.repeat(43);
const play = 'https://play.google.com/store/apps/details?id=app.saqz';
const appStore = 'https://apps.apple.com/br/app/id6812743525';

function page(userAgent, pathname, search = '', { source = script, hidden = false } = {}) {
  const elements = {
    heading: { textContent: 'Este link abre no app Saqz' },
    lead: { textContent: 'Baixe o app para entrar no grupo ou confirmar sua presença.' },
    primary: { href: 'https://saqz.app', textContent: 'Acessar site do Saqz', hidden: false },
    secondary: { hidden: true },
  };
  const location = { pathname, search, replace(url) { this.replaced = url; } };
  const timers = [];
  const cleared = new Set();
  const listeners = {};
  vm.runInNewContext(source, {
    location, navigator: { userAgent }, URLSearchParams, encodeURIComponent,
    document: { getElementById: id => elements[id], hidden },
    setTimeout: (callback, delay) => timers.push({ callback, delay }),
    clearTimeout: id => cleared.add(id),
    addEventListener: (event, callback) => { listeners[event] = callback; },
  });
  // Roda o relógio: só dispara o que não foi cancelado (ids são as posições + 1).
  const elapse = () => timers.forEach((timer, index) => { if (!cleared.has(index + 1)) timer.callback(); });
  return { ...elements, location, timers, listeners, elapse };
}

const withAppStore = script.replace('var appStore = "";', `var appStore = "${appStore}";`);

test('Android invite opens the app by itself or falls back to Google Play with the invite in the referrer', () => {
  const result = page('Android', '/', '?saqz_invite=' + code);
  const store = `${play}&referrer=saqz_invite%3D${code}`;
  const intent = `intent://links.saqz.app/?saqz_invite=${code}#Intent;scheme=https;package=app.saqz;S.browser_fallback_url=${encodeURIComponent(store)};end`;
  assert.equal(result.location.href, intent);
  assert.equal(result.primary.href, store);
  assert.equal(result.primary.textContent, 'Baixar no Google Play');
  assert.equal(result.secondary.href, intent);
  assert.equal(result.secondary.textContent, 'Já instalei, abrir o app');
  assert.equal(result.heading.textContent, 'Entre no grupo pelo app Saqz');
  assert.equal(result.timers[0].delay, 1500);
  assert.equal(result.location.replaced, undefined);
  result.elapse();
  assert.equal(result.location.replaced, store);
});

test('Android does not leave for the store when the app took over the page', () => {
  const opened = page('Android', '/', '?saqz_invite=' + code);
  opened.listeners.pagehide();
  opened.elapse();
  assert.equal(opened.location.replaced, undefined);
  const backgrounded = page('Android', '/', '?saqz_invite=' + code, { hidden: true });
  backgrounded.elapse();
  assert.equal(backgrounded.location.replaced, undefined);
});

test('Android attendance goes to Google Play with the link in the referrer, decline included', () => {
  const attendance = page('Android', '/attendance/' + code);
  const confirmStore = `${play}&referrer=saqz_attendance%3D${code}`;
  const intent = `intent://links.saqz.app/attendance/${code}#Intent;scheme=https;package=app.saqz;S.browser_fallback_url=${encodeURIComponent(confirmStore)};end`;
  assert.equal(attendance.location.href, intent);
  assert.equal(attendance.heading.textContent, 'Responda pelo app Saqz');
  assert.equal(attendance.primary.href, confirmStore);
  assert.equal(attendance.secondary.href, intent);
  attendance.elapse();
  assert.equal(attendance.location.replaced, confirmStore);

  const decline = page('Android', '/attendance/' + code, '?saqz_intent=decline');
  const declineStore = `${play}&referrer=saqz_attendance%3D${code}%26saqz_intent%3Ddecline`;
  assert.ok(decline.location.href.startsWith(`intent://links.saqz.app/attendance/${code}?saqz_intent=decline#Intent;`));
  assert.ok(decline.location.href.endsWith(`S.browser_fallback_url=${encodeURIComponent(declineStore)};end`));
  assert.equal(decline.primary.href, declineStore);
});

test('onboarding reaches the same Android route with its referrer', () => {
  const onboarding = page('Android', '/', '?saqz_onboarding=' + code);
  assert.ok(onboarding.location.href.startsWith(`intent://links.saqz.app/?saqz_onboarding=${code}#Intent;`));
  assert.equal(onboarding.primary.href, `${play}&referrer=saqz_onboarding%3D${code}`);
});

test('iOS attendance behaves like the invite: never the scheme by itself, App Store once published', () => {
  const waiting = page('iPhone', '/attendance/' + code, '?saqz_intent=decline');
  assert.equal(waiting.location.href, undefined);
  assert.equal(waiting.location.replaced, undefined);
  assert.equal(waiting.secondary.href, 'saqz:///attendance/' + code + '?saqz_intent=decline');
  assert.equal(waiting.secondary.textContent, 'Já instalei, abrir o app');

  const published = page('iPhone', '/attendance/' + code, '', { source: withAppStore });
  assert.equal(published.location.href, undefined);
  assert.equal(published.location.replaced, appStore);
  assert.equal(published.primary.href, appStore);
});

test('iOS invite never opens the scheme by itself and goes to the App Store once published', () => {
  const waiting = page('iPhone', '/', '?saqz_invite=' + code);
  assert.equal(waiting.location.href, undefined);
  assert.equal(waiting.location.replaced, undefined);
  assert.equal(waiting.primary.href, 'https://saqz.app');
  assert.match(waiting.lead.textContent, /chega em breve na App Store/);
  assert.equal(waiting.secondary.href, 'saqz:///?saqz_invite=' + code);

  const published = page('iPhone', '/', '?saqz_invite=' + code, { source: withAppStore });
  assert.equal(published.location.href, undefined);
  assert.equal(published.location.replaced, appStore);
  assert.equal(published.primary.href, appStore);
  assert.equal(published.primary.textContent, 'Baixar na App Store');
  assert.equal(published.secondary.href, 'saqz:///?saqz_invite=' + code);
});

test('desktop keeps the site, asks for the phone and never redirects', () => {
  const result = page('Macintosh', '/', '?saqz_invite=' + code);
  assert.equal(result.primary.href, 'https://saqz.app');
  assert.equal(result.secondary.hidden, true);
  assert.equal(result.lead.textContent, 'Abra este link no celular para continuar no app.');
  assert.equal(result.location.href, undefined);
  assert.equal(result.location.replaced, undefined);
});

test('malformed codes cannot inject an Android intent or a destination', () => {
  for (const search of ['?saqz_invite=bad', '?saqz_invite=' + code + '%23Intent%3Bpackage%3Devil', '?url=https://evil.test']) {
    const result = page('Android', '/', search);
    assert.equal(result.secondary.hidden, true);
    assert.equal(result.secondary.href, undefined);
    assert.equal(result.primary.href, play);
    assert.equal(result.location.href, undefined);
    assert.equal(result.location.replaced, play);
  }
});
