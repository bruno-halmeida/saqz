// Funil de medição do cadastro (/comecar/) e da assinatura (/assinar/). Ver docs/medicao-web.md.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');

const comecar = fs.readFileSync(require.resolve('../comecar/comecar.js'), 'utf8');
const comecarHtml = fs.readFileSync(require.resolve('../comecar/index.html'), 'utf8');
const assinar = fs.readFileSync(require.resolve('../assinar/assinar.js'), 'utf8');
const assinarHtml = fs.readFileSync(require.resolve('../assinar/index.html'), 'utf8');
const loader = fs.readFileSync(require.resolve('../../landing-page/assets/analytics.js'), 'utf8');

function comecarSetup(withTracker = true) {
  const elements = new Map([...comecarHtml.matchAll(/id="([^"]+)"/g)].map(([, id]) => [id, {
    value: '', hidden: false, disabled: false, textContent: '',
    classList: { toggle() {}, contains() { return false; } }, reset() {}, removeAttribute() {}, setAttribute() {},
  }]));
  const events = [];
  const requests = [];
  const window = withTracker ? { saqzTrack: (name, params) => events.push([name, params]) } : {};
  const context = vm.createContext({ window, URL, URLSearchParams, document: { readyState: 'loading',
    addEventListener() {}, getElementById: id => elements.get(id) },
    fetch: (path, options) => new Promise(resolve => requests.push({ path, options, resolve })) });
  vm.runInContext(comecar.replace('  if (document.readyState', `
    window.funnelTest = {
      setUser(user) { auth = { currentUser: user }; currentUser = user; generation++; profileReady = true; },
      select: selectTrial,
    };
    if (document.readyState`), context);
  context.window.funnelTest.setUser({ uid: 'fb-1', getIdToken: async () => 'token' });
  return { app: context.window.funnelTest, events, requests };
}
const flush = () => new Promise(resolve => setImmediate(resolve));

test('releasing the trial on the web marks trial_released once the server confirms', async () => {
  const { app, events, requests } = comecarSetup();
  app.select(false);
  await flush();
  assert.equal(events.length, 0, 'nothing is counted before the server answers');
  requests[0].resolve({ ok: true, status: 200, json: async () => ({ status: 'AVAILABLE', preauthorized: true,
    canCreateGroup: true, canRedeemCoupon: true, offerMode: 'ON', trialDays: 14 }) });
  await flush(); await flush();
  assert.deepEqual(events.map(([name]) => name), ['trial_released']);
  assert.equal(events[0][1].via, 'enrollment');
});

test('a refused trial is not counted, and pages without the loader keep working', async () => {
  const refused = comecarSetup();
  refused.app.select(false);
  await flush();
  refused.requests[0].resolve({ ok: false, status: 409, json: async () => ({}) });
  await flush(); await flush();
  assert.equal(refused.events.length, 0);

  const bare = comecarSetup(false);
  bare.app.select(false);
  await flush();
  bare.requests[0].resolve({ ok: true, status: 200, json: async () => ({ status: 'AVAILABLE', preauthorized: true,
    canCreateGroup: true, canRedeemCoupon: true, offerMode: 'ON', trialDays: 14 }) });
  await flush(); await flush();
});

test('the purchase is counted once per checkout even if two polls confirm it', () => {
  const start = assinar.indexOf('  var compraRegistrada = null;');
  const end = assinar.indexOf('\n  }\n', assinar.indexOf('function registrarCompra')) + 4;
  const events = [];
  const context = vm.createContext({
    track: (name, params) => events.push([name, params]),
    checkoutAtual: { requestId: 'req-1' }, ultimoValorPendente: 39.9,
  });
  vm.runInContext(assinar.slice(start, end) + `
    registrarCompra({ plan: 'TITULAR', cycle: 'MONTHLY' }, false);
    registrarCompra({ plan: 'TITULAR', cycle: 'MONTHLY' }, false);
    checkoutAtual = { requestId: 'req-2' };
    registrarCompra({ plan: 'ORGANIZADOR', cycle: 'MONTHLY' }, true);`, context);
  assert.deepEqual(events.map(([, p]) => p.transaction_id), ['req-1', 'req-2']);
  assert.equal(events[0][1].value, 39.9);
  assert.equal(events[0][1].currency, 'BRL');
});

test('every funnel step is wired in the pages that own it', () => {
  for (const name of ['signup_start', 'sign_up', 'login', 'account_ready', 'profile_completed', 'trial_options',
    'trial_released', 'store_click', 'app_link_ready', 'app_open']) {
    assert.match(comecar, new RegExp(`track\\([^;]*"${name}"`), name);
  }
  for (const name of ['checkout_view', 'plan_selected', 'checkout_resumed', 'coupon_applied', 'begin_checkout', 'payment_pending',
    'payment_refused', 'plan_change_requested', 'plan_changed']) {
    assert.match(assinar, new RegExp(`track\\([^;]*"${name}"`), name);
  }
  assert.equal((assinar.match(/registrarCompra\(me, (true|false)\)/g) || []).length, 2, 'new subscription and upgrade');
  assert.match(comecarHtml, /analytics-config\.js[\s\S]*analytics\.js[\s\S]*comecar\.js/);
  assert.match(assinarHtml, /\/assets\/analytics-config\.js[\s\S]*\/assets\/analytics\.js/);
});

function loaderSetup(consent) {
  const scripts = [];
  const listeners = {};
  const fbq = [];
  const store = new Map(consent ? [['saqz_consent_ads', consent]] : []);
  const window = { SAQZ_ANALYTICS: { ga4MeasurementId: 'G-T', clarityProjectId: 'c', metaPixelId: 'm' } };
  const context = vm.createContext({ window, Date, JSON, encodeURIComponent,
    localStorage: { getItem: k => store.has(k) ? store.get(k) : null, setItem: (k, v) => store.set(k, v) },
    document: { head: { appendChild: s => scripts.push(s.src) }, createElement: () => ({}),
      addEventListener: (n, f) => { (listeners[n] = listeners[n] || []).push(f); }, getElementById: () => null } });
  vm.runInContext('var window = this.window;' + loader.replace('(function () {', '(function () { var gtag, clarity, fbq;'), context);
  return { window, scripts, store, listeners };
}

test('the Meta pixel loads only after consent and funnel steps use its standard events', () => {
  const declined = loaderSetup(null);
  assert.ok(declined.scripts.some(s => s.includes('googletagmanager')));
  assert.ok(!declined.scripts.some(s => s.includes('facebook')), 'no pixel without consent');

  const accepted = loaderSetup('yes');
  assert.ok(accepted.scripts.some(s => s.includes('connect.facebook.net')));
  const calls = [];
  accepted.window.fbq = (...args) => calls.push(args);
  accepted.window.saqzTrack('trial_released', { via: 'enrollment' });
  accepted.window.saqzTrack('faq_open', { question: 'x' });
  assert.deepEqual(calls.map(c => c.slice(0, 2)), [['track', 'StartTrial'], ['trackCustom', 'faq_open']]);
});

test('analytics user id is the internal account id, reused on checkout only for the same login', () => {
  const page = loaderSetup(null);
  const configs = [];
  page.window.gtag = (...args) => configs.push(args);
  page.window.saqzIdentify('account-uuid', 'fb-1');
  page.window.saqzIdentifyFirebase('fb-other');
  page.window.saqzIdentifyFirebase('fb-1');
  const ids = configs.filter(c => c[0] === 'config').map(c => c[2].user_id);
  assert.deepEqual(ids, ['account-uuid', 'account-uuid']);
});
