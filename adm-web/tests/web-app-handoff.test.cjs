// iPhone (sem app na App Store) e computador: depois do cadastro e da assinatura, o organizador segue
// para o web app em saqz.app/app/. Android continua nas lojas.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const flush = () => new Promise(setImmediate);
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
    window: { SAQZ_FIREBASE_CONFIG: storeConfig || { androidPlayStoreUrl: 'https://play.google.com/store/apps/details?id=app.saqz' } },
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

test('iPhone: depois do cadastro, "Continuar no Saqz" leva ao web app e o painel da loja some', async () => {
  const page = comecar(IPHONE);
  page.ready(); await flush(); await flush();
  assert.equal(page.el('web-app-cta').hidden, false);
  assert.equal(page.el('download-panel').hidden, true);
  assert.match(page.el('ready-copy').textContent, /Continue no Saqz/);
  assert.ok(page.fetched.includes('/app/disponivel.json'));
});

test('computador: oferece o web app e mantém o painel para quem vai instalar no celular', async () => {
  const page = comecar(DESKTOP);
  page.ready(); await flush(); await flush();
  assert.equal(page.el('web-app-cta').hidden, false);
  assert.notEqual(page.el('download-panel').hidden, true);
});

test('Android: segue nas lojas, sem consultar o web app', async () => {
  const page = comecar(ANDROID);
  page.ready(); await flush(); await flush();
  assert.equal(page.el('web-app-cta').hidden, true);
  assert.ok(!page.fetched.includes('/app/disponivel.json'));
  assert.match(page.el('ready-copy').textContent, /Baixe o app/);
});

test('web app fora do ar: a página fica como era', async () => {
  const page = comecar(IPHONE, false);
  page.ready(); await flush(); await flush();
  assert.equal(page.el('web-app-cta').hidden, true);
  assert.match(page.el('ready-copy').textContent, /Baixe o app/);
});

test('assinatura: sucesso e "já é assinante" oferecem o web app no iPhone', () => {
  const html = fs.readFileSync(require.resolve('../assinar/index.html'), 'utf8');
  for (const id of ['sucesso-web-app', 'assinante-web-app']) {
    assert.match(html, new RegExp(`<a class="btn" id="${id}" href="/app/" hidden>Continuar no Saqz</a>`));
  }
  const source = fs.readFileSync(require.resolve('../assinar/assinar.js'), 'utf8');
  assert.match(source, /estado === "sucesso" \|\| estado === "assinante"/);
  assert.match(source, /Continue no Saqz e crie seu grupo\./);
});

test('iPhone: abaixo do "Continuar no Saqz" vem a dica de adicionar à Tela de Início', async () => {
  const page = comecar(IPHONE);
  page.ready();
  await flush(); await flush();
  assert.equal(page.el('web-app-ios-tip').hidden, false);
});

test('computador: selo oficial do Google Play, sem selo da App Store, e o bloco "No iPhone" com a nota', async () => {
  const page = comecar(DESKTOP);
  page.ready();
  await flush(); await flush();
  assert.equal(page.el('android-store').hidden, false);
  assert.equal(page.el('ios-store').hidden, true);
  assert.equal(page.el('iphone-web').hidden, false);
  assert.equal(page.el('store-soon-note').hidden, false);
  assert.equal(page.el('web-app-ios-tip').hidden, true);
});

test('com link da App Store: o selo oficial aparece e o bloco "No iPhone" e a nota somem', async () => {
  const page = comecar(DESKTOP, true, {
    androidPlayStoreUrl: 'https://play.google.com/store/apps/details?id=app.saqz',
    iosAppStoreUrl: 'https://apps.apple.com/br/app/id6812743525',
  });
  page.ready();
  await flush(); await flush();
  assert.equal(page.el('ios-store').hidden, false);
  assert.equal(page.el('ios-store').href, 'https://apps.apple.com/br/app/id6812743525');
  assert.equal(page.el('iphone-web').hidden, true);
  assert.equal(page.el('store-soon-note').hidden, true);
  assert.equal(page.el('step-get').textContent, 'Baixe o app na loja do iPhone ou Android');
});
