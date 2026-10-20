const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const script = fs.readFileSync(path.resolve(__dirname, '../index.html'), 'utf8').match(/<script>([\s\S]*?)<\/script>/)[1];
const code = 'A'.repeat(43);
const play = 'https://play.google.com/store/apps/details?id=app.saqz';
const appStore = 'https://apps.apple.com/br/app/id6812743525';

function page(userAgent, pathname, search = '', { source = script, hidden = false, fetch, wasm = WebAssembly, clipboard } = {}) {
  const elements = {
    heading: { textContent: 'Este link abre no app Saqz' },
    lead: { textContent: 'Entre no grupo ou confirme sua presença no Saqz.' },
    primary: { href: 'https://saqz.app', textContent: 'Acessar site do Saqz', hidden: false, addEventListener(event, callback) { this.clicks = { ...this.clicks, [event]: callback }; } },
    secondary: { hidden: true },
    tertiary: { hidden: true },
  };
  const location = { pathname, search, replace(url) { this.replaced = url; } };
  const timers = [];
  const cleared = new Set();
  const listeners = {};
  const requests = [];
  const copied = [];
  const legacyCopies = [];
  const field = { setAttribute() {}, select() {}, setSelectionRange() {}, style: {} };
  const context = {
    location, navigator: { userAgent, clipboard: clipboard && { writeText: text => { copied.push(text); return clipboard(text); } } },
    URLSearchParams, encodeURIComponent, WebAssembly: wasm,
    document: {
      getElementById: id => elements[id], hidden,
      createElement: () => field,
      body: { appendChild: node => legacyCopies.push(node.value), removeChild() {} },
      execCommand: () => true,
    },
    setTimeout: (callback, delay) => timers.push({ callback, delay }),
    clearTimeout: id => cleared.add(id),
    addEventListener: (event, callback) => { listeners[event] = callback; },
  };
  // Sem `fetch` no contexto o script segue o caminho do app, como num navegador sem a API.
  if (fetch) context.fetch = (url, options) => { requests.push(url); return fetch(url, options); };
  vm.runInNewContext(source, context);
  // Roda o relógio: só dispara o que não foi cancelado (ids são as posições + 1).
  const elapse = () => timers.forEach((timer, index) => { if (!cleared.has(index + 1)) timer.callback(); });
  return { ...elements, location, timers, listeners, elapse, requests, copied, legacyCopies, field };
}


test('Android invite opens the app by itself or falls back to Google Play with the invite in the referrer', () => {
  const result = page('Android', '/', '?saqz_invite=' + code);
  const store = `${play}&referrer=saqz_invite%3D${code}`;
  const intent = `intent://links.saqz.app/?saqz_invite=${code}#Intent;scheme=https;package=app.saqz;S.browser_fallback_url=${encodeURIComponent(store)};end`;
  assert.equal(result.location.href, intent);
  assert.equal(result.primary.href, store);
  assert.equal(result.primary.textContent, 'Baixar no Google Play');
  assert.equal(result.secondary.href, intent);
  assert.equal(result.secondary.textContent, 'Já instalei, abrir o app');
  assert.equal(result.heading.textContent, 'Entre no grupo no Saqz');
  assert.equal(result.lead.textContent, 'Instale o Saqz e entre com sua conta: o app abre o convite sozinho.');
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
  assert.equal(attendance.heading.textContent, 'Responda no Saqz');
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

function tap(result) {
  let prevented = false;
  result.primary.clicks.click({ preventDefault() { prevented = true; } });
  return prevented;
}

test('iPhone invite stays on the page: no automatic redirect, a button copies the link and opens the App Store', async () => {
  const result = page(iPhone, '/', '?saqz_invite=' + code, { clipboard: () => Promise.resolve() });
  assert.equal(result.location.href, undefined);
  assert.equal(result.location.replaced, undefined);
  assert.equal(result.primary.href, appStore);
  assert.equal(result.primary.textContent, 'Baixar o app e continuar');
  assert.equal(result.heading.textContent, 'Entre no grupo no Saqz');
  assert.equal(result.lead.textContent, 'Instale o Saqz e entre com sua conta: o app abre o convite sozinho.');
  assert.doesNotMatch(result.lead.textContent, /toque de novo/);
  assert.equal(result.secondary.href, 'saqz:///?saqz_invite=' + code);
  assert.equal(result.secondary.textContent, 'Já instalei, abrir o app');
  assert.ok(!JSON.stringify(result.secondary).includes('Google Play'));
  assert.equal(tap(result), true);
  assert.deepEqual(result.copied, ['https://links.saqz.app/?saqz_invite=' + code]);
  assert.equal(result.location.href, undefined, 'a loja só abre depois da cópia');
  await settle();
  assert.equal(result.location.href, appStore);
});

test('iPhone attendance copies the full link, decline included, then opens the App Store', async () => {
  const result = page(iPhone, '/attendance/' + code, '?saqz_intent=decline', { clipboard: () => Promise.resolve() });
  assert.equal(result.location.replaced, undefined);
  assert.equal(result.primary.textContent, 'Baixar o app e continuar');
  assert.equal(result.heading.textContent, 'Responda no Saqz');
  assert.equal(result.secondary.href, 'saqz:///attendance/' + code + '?saqz_intent=decline');
  tap(result);
  await settle();
  assert.deepEqual(result.copied, [`https://links.saqz.app/attendance/${code}?saqz_intent=decline`]);
  assert.equal(result.location.href, appStore);
});

test('iPhone still opens the App Store when the clipboard is refused or missing', async () => {
  const refused = page(iPhone, '/', '?saqz_invite=' + code, { clipboard: () => Promise.reject(new Error('negado')) });
  tap(refused);
  await settle();
  assert.deepEqual(refused.legacyCopies, [`https://links.saqz.app/?saqz_invite=${code}`]);
  assert.equal(refused.location.href, appStore);
  const missing = page(iPhone, '/', '?saqz_invite=' + code);
  tap(missing);
  assert.deepEqual(missing.legacyCopies, [`https://links.saqz.app/?saqz_invite=${code}`]);
  assert.equal(missing.location.href, appStore);
});

test('iPhone without a link code shows a plain App Store link and copies nothing', () => {
  const result = page(iPhone, '/', '', { clipboard: () => Promise.resolve() });
  assert.equal(result.location.replaced, undefined);
  assert.equal(result.primary.href, appStore);
  assert.equal(result.primary.textContent, 'Baixar na App Store');
  assert.equal(result.primary.clicks, undefined);
  assert.equal(result.secondary.hidden, true);
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

// Web app (saqz.app/app/): só computador; o Android vai ao Google Play e o iPhone à App Store.
const webUp = () => Promise.resolve({ ok: true });
const webDown = () => Promise.resolve({ ok: false });
const settle = () => new Promise(resolve => setImmediate(resolve));
const iPhone = 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)';
const desktop = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)';

test('iPhone never asks for the web app and never redirects by itself', async () => {
  for (const [pathname, search] of [['/', '?saqz_invite=' + code], ['/attendance/' + code, '?saqz_intent=decline'], ['/', '']]) {
    const result = page(iPhone, pathname, search, { fetch: webUp });
    await settle();
    assert.deepEqual(result.requests, []);
    assert.equal(result.location.replaced, undefined);
    assert.equal(result.location.href, undefined);
  }
});

test('desktop onboarding opens the web app too', async () => {
  const result = page(desktop, '/', '?saqz_onboarding=' + code, { fetch: webUp });
  await settle();
  assert.equal(result.location.replaced, 'https://saqz.app/app/?saqz_onboarding=' + code);
});

test('desktop with the web app down keeps the site and asks for the phone', async () => {
  const result = page(desktop, '/', '?saqz_invite=' + code, { fetch: webDown });
  await settle();
  assert.equal(result.location.replaced, undefined);
  assert.match(result.lead.textContent, /Abra este link no celular/);
});

const android = 'Mozilla/5.0 (Linux; Android 14; Pixel 8) Chrome/130';

test('Android invite and attendance go to Google Play with the referrer, and the web app is never the main path', async () => {
  const cases = [
    ['/', '?saqz_invite=' + code, `saqz_invite%3D${code}`, `intent://links.saqz.app/?saqz_invite=${code}#Intent;`],
    ['/attendance/' + code, '', `saqz_attendance%3D${code}`, `intent://links.saqz.app/attendance/${code}#Intent;`],
    ['/attendance/' + code, '?saqz_intent=decline', `saqz_attendance%3D${code}%26saqz_intent%3Ddecline`,
      `intent://links.saqz.app/attendance/${code}?saqz_intent=decline#Intent;`],
  ];
  for (const [pathname, search, referrer, intentStart] of cases) {
    const result = page(android, pathname, search, { fetch: webUp });
    await settle();
    const store = `${play}&referrer=${referrer}`;
    assert.equal(result.primary.href, store, search);
    assert.equal(result.primary.textContent, 'Baixar no Google Play');
    assert.ok(result.location.href.startsWith(intentStart), search);
    assert.ok(result.location.href.endsWith(`S.browser_fallback_url=${encodeURIComponent(store)};end`), search);
    assert.equal(result.secondary.textContent, 'Já instalei, abrir o app');
    result.elapse();
    assert.equal(result.location.replaced, store);
  }
});

test('Android shows a quiet "Continuar no navegador" link only when the web app answers and WasmGC exists', async () => {
  const web = `https://saqz.app/app/?saqz_attendance=${code}&saqz_intent=decline`;
  const up = page(android, '/attendance/' + code, '?saqz_intent=decline', { fetch: webUp });
  assert.deepEqual(up.requests, ['https://saqz.app/app/disponivel.json']);
  assert.equal(up.tertiary.hidden, true);
  await settle();
  assert.equal(up.tertiary.href, web);
  assert.equal(up.tertiary.textContent, 'Continuar no navegador');
  assert.equal(up.tertiary.hidden, false);
  const invite = page(android, '/', '?saqz_invite=' + code, { fetch: webUp });
  await settle();
  assert.equal(invite.tertiary.href, 'https://saqz.app/app/?saqz_invite=' + code);
  const down = page(android, '/', '?saqz_invite=' + code, { fetch: webDown });
  await settle();
  assert.equal(down.tertiary.hidden, true);
  const noGc = page(android, '/', '?saqz_invite=' + code, { fetch: webUp, wasm: { validate: () => false } });
  await settle();
  assert.deepEqual(noGc.requests, []);
  assert.equal(noGc.tertiary.hidden, true);
  // Nunca vira fallback automático.
  assert.ok(!up.location.href.includes(encodeURIComponent('saqz.app/app')));
  assert.equal(up.location.replaced, undefined);
});

test('Android onboarding and the bare link never ask for the web app', async () => {
  for (const search of ['?saqz_onboarding=' + code, '']) {
    const result = page(android, '/', search, { fetch: webUp });
    await settle();
    assert.deepEqual(result.requests, []);
    assert.equal(result.tertiary.hidden, true);
  }
});

test('an iPhone without WasmGC (iOS before 18.2) behaves the same: button, no redirect', async () => {
  const noGc = { validate: () => false };
  const result = page(iPhone, '/', '?saqz_invite=' + code, { fetch: webUp, wasm: noGc });
  await settle();
  assert.deepEqual(result.requests, []);
  assert.equal(result.location.replaced, undefined);
  assert.equal(result.primary.textContent, 'Baixar o app e continuar');
});

// Código curto do convite (44DF-8SA8): o link leva o código sem hífen.
test('short invite code opens the app and keeps the code in the Play referrer', () => {
  const short = '44DF8SA8';
  const result = page('Android', '/', '?saqz_invite=' + short);
  const store = `${play}&referrer=saqz_invite%3D${short}`;
  const intent = `intent://links.saqz.app/?saqz_invite=${short}#Intent;scheme=https;package=app.saqz;S.browser_fallback_url=${encodeURIComponent(store)};end`;
  assert.equal(result.heading.textContent, 'Entre no grupo no Saqz');
  assert.equal(result.location.href, intent);
  assert.equal(result.primary.href, store);
});

test('short invite code is normalized: lowercase and hyphen open the same invite', () => {
  for (const typed of ['44df8sa8', '44DF-8SA8', '44df-8sa8']) {
    const result = page('Android', '/', '?saqz_invite=' + typed);
    assert.equal(result.heading.textContent, 'Entre no grupo no Saqz', typed);
    assert.match(result.location.href, /^intent:\/\/links\.saqz\.app\/\?saqz_invite=44DF8SA8#Intent/, typed);
  }
  const phone = page('Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)', '/', '?saqz_invite=44df-8sa8');
  assert.equal(phone.secondary.href, 'saqz:///?saqz_invite=44DF8SA8');
});

test('short invite code reaches the web app (desktop) with the canonical code', async () => {
  const result = page(desktop, '/', '?saqz_invite=44df-8sa8', { fetch: () => Promise.resolve({ ok: true }) });
  await settle();
  assert.equal(result.location.replaced, 'https://saqz.app/app/?saqz_invite=44DF8SA8');
});

test('short invite codes with ambiguous characters or wrong size are not invites', () => {
  for (const bad of ['44DF8SA0', '44DF8SAO', '44DF8SA1', '44DF8SAI', '44DF8SAL', '44DF8SA', '44DF8SA88', '44DF8SA8%23Intent']) {
    const result = page('Android', '/', '?saqz_invite=' + bad);
    assert.equal(result.secondary.hidden, true, bad);
    assert.equal(result.location.href, undefined, bad);
    assert.equal(result.primary.href, play, bad);
  }
});
