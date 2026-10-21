// Cadastro (/comecar/): a tela final só manda baixar o app, em qualquer aparelho (selos da App Store e do Google Play;
// o Android só vê o do Google Play). Assinatura (/assinar/): no computador, depois do pagamento, o mesmo "Baixe o app"
// com um QR de links.saqz.app; no celular, "Volte ao app". O web app (saqz.app/app/) saiu do ar.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const flush = () => new Promise(setImmediate);
const PLAY = 'https://play.google.com/store/apps/details?id=app.saqz';
const APP_STORE = 'https://apps.apple.com/br/app/id6812743525';
const IPHONE = 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_4 like Mac OS X) AppleWebKit/605.1.15 Version/18.4 Mobile/15E148 Safari/604.1';
const ANDROID = 'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/133.0 Mobile Safari/537.36';
const DESKTOP = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/133.0 Safari/537.36';

function elementsOf(html) {
  return new Map([...html.matchAll(/id="([^"]+)"/g)].map(([, id]) => [id, {
    id, value: '', hidden: /hidden/.test(html.slice(html.indexOf(`id="${id}"`), html.indexOf('>', html.indexOf(`id="${id}"`)))),
    disabled: false, textContent: '', attrs: {},
    classList: { set: new Set(), toggle(c, on) { on ? this.set.add(c) : this.set.delete(c); }, contains(c) { return this.set.has(c); } },
    reset() {}, removeAttribute(n) { delete this.attrs[n]; }, setAttribute(n, v) { this.attrs[n] = v; }, getAttribute(n) { return this.attrs[n] || null; },
    addEventListener() {},
  }]));
}

function context(userAgent, elements, available, storeConfig) {
  const fetched = [];
  const fetch = (path) => {
    fetched.push(path);
    if (path === '/app/disponivel.json') return Promise.resolve({ ok: available });
    return new Promise(() => {}); // trial e afins: não importam aqui
  };
  return { fetched, ctx: vm.createContext({
    window: { SAQZ_FIREBASE_CONFIG: storeConfig || { androidPlayStoreUrl: PLAY, iosAppStoreUrl: APP_STORE } },
    URL, URLSearchParams, WebAssembly, fetch,
    navigator: { userAgent, platform: userAgent.includes('Macintosh') ? 'MacIntel' : 'Linux', maxTouchPoints: 0 },
    document: { readyState: 'loading', addEventListener() {}, getElementById: (id) => elements.get(id) },
  }) };
}

function comecar(userAgent, available = true, storeConfig) {
  const html = fs.readFileSync(require.resolve('../comecar/index.html'), 'utf8');
  const source = fs.readFileSync(require.resolve('../comecar/comecar.js'), 'utf8');
  const elements = elementsOf(html);
  const { ctx, fetched } = context(userAgent, elements, available, storeConfig);
  vm.runInContext(source.replace('  if (document.readyState', `
    window.handoffTest = { ready() { displayProfile({ user: { phoneRequired: false } }); } };
    if (document.readyState`), ctx);
  return { ready: ctx.window.handoffTest.ready, el: (id) => elements.get(id), fetched };
}

const READY_COPY = 'Baixe o app e entre com esta mesma conta. O teste começa quando você criar o primeiro grupo no celular.';

test('iPhone: depois do cadastro, os selos das lojas são a ação principal e o web app nem é consultado', async () => {
  const page = comecar(IPHONE);
  page.ready(); await flush(); await flush();
  assert.ok(!page.fetched.includes('/app/disponivel.json'));
  assert.equal(page.el('ready-title').textContent, 'Baixe o app');
  assert.equal(page.el('ready-copy').textContent, READY_COPY);
  assert.equal(page.el('download-panel').hidden, false);
  assert.equal(page.el('ios-store').hidden, false);
  assert.equal(page.el('ios-store').href, APP_STORE);
  assert.equal(page.el('android-store').hidden, false, 'iPhone também vê o selo do Google Play');
  assert.equal(page.el('android-store').href, PLAY);
  assert.equal(page.el('download-open').hidden, false, 'secundária: já tem o app? abrir neste celular');
});

test('computador: só os selos das lojas, sem web app', async () => {
  const page = comecar(DESKTOP);
  page.ready(); await flush(); await flush();
  assert.ok(!page.fetched.includes('/app/disponivel.json'));
  assert.equal(page.el('ready-title').textContent, 'Baixe o app');
  assert.equal(page.el('ready-copy').textContent, READY_COPY);
  assert.equal(page.el('download-panel').hidden, false);
  assert.equal(page.el('download-open').hidden, true, '"Abrir o app neste celular" só aparece no celular');
});

test('Android: segue nas lojas, sem consultar o web app', async () => {
  const page = comecar(ANDROID);
  page.ready(); await flush(); await flush();
  assert.ok(!page.fetched.includes('/app/disponivel.json'));
  assert.equal(page.el('ready-copy').textContent, READY_COPY);
  assert.equal(page.el('download-panel').hidden, false, 'principal: o selo da Google Play');
  assert.equal(page.el('android-store').hidden, false);
  assert.equal(page.el('download-open').hidden, false, 'secundária: já tem o app? abrir neste celular');
});

test('cadastro: o botão "Continuar no Saqz" e a consulta ao web app não existem mais; "Abrir neste celular" fica', () => {
  const html = fs.readFileSync(require.resolve('../comecar/index.html'), 'utf8');
  const source = fs.readFileSync(require.resolve('../comecar/comecar.js'), 'utf8');
  assert.doesNotMatch(html, /web-app-cta|Continuar no Saqz|href="\/app\/"/);
  assert.doesNotMatch(source, /web-app-cta|disponivel\.json|offerWebApp/);
  assert.match(html, /<h2 id="ready-title">Baixe o app<\/h2>/);
  assert.match(html, /id="request-app-link" type="button">Já tem o app\? Abrir neste celular<\/button>/);
  assert.match(html, /id="app-link"/);
});

function assinar(userAgent, storeConfig) {
  const html = fs.readFileSync(require.resolve('../assinar/index.html'), 'utf8');
  const source = fs.readFileSync(require.resolve('../assinar/assinar.js'), 'utf8');
  const elements = elementsOf(html);
  const { ctx } = context(userAgent, elements, false, storeConfig);
  ctx.qrcode = () => ({ addData() {}, make() {}, createSvgTag: () => '<svg></svg>' });
  const auth = { currentUser: null, useEmulator() {}, onAuthStateChanged() {} };
  ctx.firebase = { initializeApp() {}, auth: Object.assign(() => auth, { GoogleAuthProvider: function () {} }) };
  ctx.history = { replaceState() {} };
  ctx.window.location = { search: '', pathname: '/assinar/', hash: '' };
  // O resto da página (planos, formulários) liga eventos na carga: aqui só importam os estados finais.
  ctx.document.querySelectorAll = () => [];
  ctx.window.SaqzCartao = new Proxy({}, { get: () => () => '' });
  const spare = elementsOf('<i id="x">').get('x');
  ctx.document.getElementById = (id) => elements.get(id) || { ...spare, append() {}, appendChild() {}, style: {} };
  vm.runInContext(source.replace('  function identify(user)', `
  window.assinarTest = { mostrar: function (estado) { mostrar(estado); } };
  function identify(user)`), ctx);
  return { mostrar: ctx.window.assinarTest.mostrar, el: (id) => elements.get(id) };
}

test('assinatura: no computador, sucesso e "já é assinante" mostram os selos das lojas e o QR do links.saqz.app', () => {
  for (const estado of ['sucesso', 'assinante']) {
    const page = assinar(DESKTOP);
    page.el(`${estado}-sub`).textContent = estado === 'sucesso'
      ? 'Pagamento confirmado. Volte ao app Saqz e crie seu grupo.'
      : 'Sua assinatura está ativa. Volte ao app para continuar.';
    page.mostrar(estado);
    assert.equal(page.el(`${estado}-baixar`).hidden, false);
    assert.equal(page.el(`${estado}-ios-store`).hidden, false);
    assert.equal(page.el(`${estado}-ios-store`).href, APP_STORE);
    assert.equal(page.el(`${estado}-android-store`).hidden, false);
    assert.equal(page.el(`${estado}-android-store`).href, PLAY);
    assert.equal(page.el(`${estado}-qr`).hidden, false);
    assert.equal(page.el(`${estado}-qr`).attrs['data-link'], 'https://links.saqz.app/');
    assert.doesNotMatch(page.el(`${estado}-sub`).textContent, /Volte ao app/);
    assert.match(page.el(`${estado}-sub`).textContent, /Baixe o app no celular/);
  }
});

test('assinatura: no celular, o "Volte ao app" fica e o bloco de baixar não aparece', () => {
  for (const userAgent of [IPHONE, ANDROID]) {
    const page = assinar(userAgent);
    page.el('sucesso-sub').textContent = 'Pagamento confirmado. Volte ao app Saqz e crie seu grupo.';
    page.mostrar('sucesso');
    assert.equal(page.el('sucesso-baixar').hidden, true);
    assert.equal(page.el('sucesso-sub').textContent, 'Pagamento confirmado. Volte ao app Saqz e crie seu grupo.');
  }
});

test('assinatura: nada aponta para o web app (/app/)', () => {
  const html = fs.readFileSync(require.resolve('../assinar/index.html'), 'utf8');
  const source = fs.readFileSync(require.resolve('../assinar/assinar.js'), 'utf8');
  assert.doesNotMatch(html, /href="\/app\/|Continuar no Saqz|-web-app"/);
  assert.doesNotMatch(source, /\/app\/|disponivel\.json|offerWebApp|-web-app|via: "web"/);
  for (const estado of ['sucesso', 'assinante']) {
    assert.match(html, new RegExp(`id="${estado}-baixar" hidden`));
    assert.match(html, new RegExp(`id="${estado}-qr"`));
  }
  assert.match(html, /<script src="qrcode\.js"><\/script>/);
});

test('selos: App Store vem antes do Google Play; Android só vê o do Google Play', async () => {
  const html = fs.readFileSync(require.resolve('../comecar/index.html'), 'utf8');
  assert.ok(html.indexOf('id="ios-store"') < html.indexOf('id="android-store"'));
  const android = comecar(ANDROID);
  android.ready(); await flush(); await flush();
  assert.equal(android.el('android-store').hidden, false);
  assert.equal(android.el('ios-store').hidden, true);
});

test('a dica de Tela de Início do iPhone não existe mais', () => {
  const html = fs.readFileSync(require.resolve('../comecar/index.html'), 'utf8');
  assert.doesNotMatch(html, /web-app-ios-tip|Adicionar à Tela de Início/);
});

test('computador: os dois selos oficiais aparecem', async () => {
  const page = comecar(DESKTOP);
  page.ready();
  await flush(); await flush();
  assert.equal(page.el('android-store').hidden, false);
  assert.equal(page.el('ios-store').hidden, false);
  assert.equal(page.el('ios-store').href, APP_STORE);
});

test('sem link da App Store configurado, o selo da App Store fica escondido no iPhone', async () => {
  const page = comecar(IPHONE, true, { androidPlayStoreUrl: PLAY });
  page.ready();
  await flush(); await flush();
  assert.equal(page.el('ios-store').hidden, true);
  assert.equal(page.el('android-store').hidden, false);
});

test('cartão final: só uma ação principal visível e o resto atrás de "Mais opções"', () => {
  const html = fs.readFileSync(require.resolve('../comecar/index.html'), 'utf8');
  const card = html.slice(html.indexOf('id="ready-card"'), html.indexOf('</section>', html.indexOf('id="ready-card"')));
  const details = card.slice(card.indexOf('<details'), card.indexOf('</details>'));
  for (const id of ['trial-coupon-form', 'refresh-trial', 'logout']) assert.ok(details.includes(`id="${id}"`), `${id} em Mais opções`);
  assert.match(details, /Conhecer os planos pagos/);
  assert.ok(!card.slice(0, card.indexOf('<details')).includes('id="logout"'));
  assert.doesNotMatch(card, /Experimentar o Organizador|iphone-web|Use pelo navegador|enroll-trial/);
});
