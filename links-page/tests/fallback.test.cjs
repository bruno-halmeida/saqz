const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const script = fs.readFileSync(path.resolve(__dirname, '../index.html'), 'utf8').match(/<script>([\s\S]*?)<\/script>/)[1];
const qrLib = fs.readFileSync(path.resolve(__dirname, '../assets/qrcode.js'), 'utf8');
const code = 'A'.repeat(43);
const play = 'https://play.google.com/store/apps/details?id=app.saqz';
const appStore = 'https://apps.apple.com/br/app/id6812743525';

function page(userAgent, pathname, search = '', { source = script, hidden = false, fetch, wasm = WebAssembly, clipboard, qr = true } = {}) {
  const elements = {
    heading: { textContent: 'Este link abre no app Saqz' },
    lead: { textContent: 'Entre no grupo ou confirme sua presença no Saqz.' },
    primary: { href: 'https://saqz.app', textContent: 'Acessar site do Saqz', hidden: false, addEventListener(event, callback) { this.clicks = { ...this.clicks, [event]: callback }; } },
    secondary: { hidden: true },
    qr: { hidden: true, innerHTML: '', attributes: {}, setAttribute(name, value) { this.attributes[name] = value; } },
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
  // A página carrega assets/qrcode.js antes do script; `qr: false` simula a lib que não carregou.
  vm.runInNewContext((qr ? qrLib + '\n' : '') + source, context);
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

test('desktop invite shows the QR code and the stores and never redirects', () => {
  const result = page('Macintosh', '/', '?saqz_invite=' + code);
  assert.equal(result.lead.textContent, 'Aponte a câmera do celular para o QR code e continue no app.');
  assert.equal(result.primary.href, appStore);
  assert.equal(result.primary.textContent, 'Baixar na App Store');
  assert.equal(result.secondary.href, play);
  assert.equal(result.secondary.textContent, 'Baixar no Google Play');
  assert.equal(result.qr.hidden, false);
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

// Web app (saqz.app/app/): fora do ar desde 21/10/2026; a página nunca o consultou nem levou a ele. Os testes abaixo
// garantem que nenhum aparelho volta a perguntar por ele: o Android vai ao Google Play, o iPhone à App Store e o
// computador mostra o QR e as lojas.
const webUp = () => Promise.resolve({ ok: true });
const webDown = () => Promise.resolve({ ok: false });
const settle = () => new Promise(resolve => setImmediate(resolve));
const iPhone = 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X)';
const desktop = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)';
const android = 'Mozilla/5.0 (Linux; Android 14; Pixel 8) Chrome/130';

test('iPhone never asks for the web app and never redirects by itself', async () => {
  for (const [pathname, search] of [['/', '?saqz_invite=' + code], ['/attendance/' + code, '?saqz_intent=decline'], ['/', '']]) {
    const result = page(iPhone, pathname, search, { fetch: webUp });
    await settle();
    assert.deepEqual(result.requests, []);
    assert.equal(result.location.replaced, undefined);
    assert.equal(result.location.href, undefined);
  }
});

test('desktop onboarding shows the QR and the stores, never opens the web app by itself', async () => {
  const result = page(desktop, '/', '?saqz_onboarding=' + code, { fetch: webUp });
  await settle();
  assert.equal(result.location.replaced, undefined);
  assert.equal(result.heading.textContent, 'Continue no app Saqz');
  assert.equal(result.qr.hidden, false);
  assert.equal(result.qr.attributes['data-link'], 'https://links.saqz.app/?saqz_onboarding=' + code);
  assert.equal(result.primary.href, appStore);
  assert.equal(result.secondary.href, play);
  assert.deepEqual(result.requests, []);
});

test('desktop without a link code shows the QR and the stores, never opens the web app by itself', async () => {
  const result = page(desktop, '/', '', { fetch: webUp });
  await settle();
  assert.equal(result.location.replaced, undefined);
  assert.equal(result.location.href, undefined);
  assert.equal(result.lead.textContent, 'Aponte a câmera do celular para o QR code e continue no app.');
  assert.equal(result.qr.hidden, false);
  assert.equal(result.qr.attributes['data-link'], 'https://links.saqz.app/');
  assert.equal(result.primary.href, appStore);
  assert.equal(result.secondary.href, play);
  // Código malformado vale como link sem código: o computador também não segue ao web app.
  const bad = page(desktop, '/', '?saqz_invite=bad', { fetch: webUp });
  await settle();
  assert.equal(bad.location.replaced, undefined);
  assert.equal(bad.qr.attributes['data-link'], 'https://links.saqz.app/');
});

// Computador (DEB-118): QR com o link canônico e as duas lojas; nunca o web app, nem sozinho nem por link.
const qrDrawn = result => result.qr.hidden === false && /<svg[\s\S]*<path d="M/.test(result.qr.innerHTML);

test('desktop invite: QR with the canonical link and both stores, no redirect and no web app link', async () => {
  const result = page(desktop, '/', '?saqz_invite=' + code, { fetch: webUp });
  await settle();
  assert.equal(result.location.replaced, undefined);
  assert.equal(result.location.href, undefined);
  assert.equal(result.heading.textContent, 'Entre no grupo no Saqz');
  assert.ok(qrDrawn(result));
  assert.equal(result.qr.attributes['data-link'], 'https://links.saqz.app/?saqz_invite=' + code);
  assert.equal(result.primary.href, appStore);
  assert.equal(result.secondary.href, play);
  assert.deepEqual(result.requests, []);
});

test('desktop attendance: the QR carries the token, decline included', async () => {
  const confirm = page(desktop, '/attendance/' + code, '', { fetch: webUp });
  await settle();
  assert.equal(confirm.heading.textContent, 'Responda no Saqz');
  assert.equal(confirm.qr.attributes['data-link'], 'https://links.saqz.app/attendance/' + code);
  assert.equal(confirm.location.replaced, undefined);
  const decline = page(desktop, '/attendance/' + code, '?saqz_intent=decline', { fetch: webUp });
  await settle();
  assert.ok(qrDrawn(decline));
  assert.equal(decline.qr.attributes['data-link'], `https://links.saqz.app/attendance/${code}?saqz_intent=decline`);
  assert.equal(decline.location.replaced, undefined);
});

test('desktop never asks for the web app, whatever the web app state', async () => {
  const cases = [{ fetch: webUp }, { fetch: webDown }, { fetch: () => Promise.reject(new Error('rede')) }];
  for (const options of cases) {
    const result = page(desktop, '/', '?saqz_invite=' + code, options);
    await settle();
    assert.deepEqual(result.requests, []);
    assert.ok(qrDrawn(result));
    assert.equal(result.primary.href, appStore);
    assert.equal(result.secondary.href, play);
    assert.equal(result.location.replaced, undefined);
  }
});

test('desktop without the QR library hides the QR and keeps the stores', async () => {
  const result = page(desktop, '/', '?saqz_invite=' + code, { fetch: webUp, qr: false });
  await settle();
  assert.equal(result.qr.hidden, true);
  assert.equal(result.primary.href, appStore);
  assert.equal(result.secondary.href, play);
  assert.equal(result.location.replaced, undefined);
});

test('phones never show the QR code', async () => {
  for (const userAgent of [iPhone, android]) {
    for (const [pathname, search] of [['/', '?saqz_invite=' + code], ['/attendance/' + code, '?saqz_intent=decline']]) {
      const result = page(userAgent, pathname, search, { fetch: webUp, clipboard: () => Promise.resolve() });
      await settle();
      assert.equal(result.qr.hidden, true);
      assert.equal(result.qr.innerHTML, '');
    }
  }
});


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

test('Android never asks for the web app nor offers a link to it, with the web app up or down', async () => {
  for (const [pathname, search] of [['/attendance/' + code, '?saqz_intent=decline'], ['/', '?saqz_invite=' + code]]) {
    for (const fetch of [webUp, webDown]) {
      const result = page(android, pathname, search, { fetch });
      await settle();
      assert.deepEqual(result.requests, []);
      assert.ok(!result.location.href.includes(encodeURIComponent('saqz.app/app')));
      assert.equal(result.location.replaced, undefined);
    }
  }
});

test('Android onboarding and the bare link never ask for the web app', async () => {
  for (const search of ['?saqz_onboarding=' + code, '']) {
    const result = page(android, '/', search, { fetch: webUp });
    await settle();
    assert.deepEqual(result.requests, []);
  }
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

test('short invite code on the desktop: the QR carries the normalized code', async () => {
  const result = page(desktop, '/', '?saqz_invite=44df-8sa8', { fetch: () => Promise.resolve({ ok: true }) });
  await settle();
  assert.equal(result.location.replaced, undefined);
  assert.equal(result.qr.attributes['data-link'], 'https://links.saqz.app/?saqz_invite=44DF8SA8');
});

test('short invite codes with ambiguous characters or wrong size are not invites', () => {
  for (const bad of ['44DF8SA0', '44DF8SAO', '44DF8SA1', '44DF8SAI', '44DF8SAL', '44DF8SA', '44DF8SA88', '44DF8SA8%23Intent']) {
    const result = page('Android', '/', '?saqz_invite=' + bad);
    assert.equal(result.secondary.hidden, true, bad);
    assert.equal(result.location.href, undefined, bad);
    assert.equal(result.primary.href, play, bad);
  }
});
