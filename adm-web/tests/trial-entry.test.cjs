const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const flush = () => new Promise(setImmediate);
const source = fs.readFileSync(require.resolve('../comecar/comecar.js'), 'utf8');
const html = fs.readFileSync(require.resolve('../comecar/index.html'), 'utf8');
const available = { status: 'AVAILABLE', canCreateGroup: true, canRedeemCoupon: true,
  offerMode: 'ON', trialDays: 14, preauthorized: false };
function setup() {
  const elements = new Map([...html.matchAll(/id="([^"]+)"/g)].map(([, id]) => [id, {
    value: '', hidden: false, disabled: false, textContent: '',
    classList: { toggle() {} }, reset() {}, removeAttribute() {}, setAttribute() {},
  }]));
  const requests = [];
  const user = { getIdToken: async () => 'test-token' };
  const context = vm.createContext({ window: {}, document: { readyState: 'loading',
    addEventListener() {}, getElementById: id => {
      assert.ok(elements.has(id), `Missing element ${id}`); return elements.get(id);
    } }, fetch: (path, options) => new Promise((resolve, reject) => requests.push({ path, options, resolve, reject })) });
  vm.runInContext(source.replace('  if (document.readyState', `
    window.trialTest = {
      setUser(user) { auth = { currentUser: user }; currentUser = user; generation++; profileReady = true; },
      load() { return loadTrial(currentUser, generation); },
      select: selectTrial,
      logout() { generation++; auth.currentUser = null; resetForLogout(); }
    };
    if (document.readyState`), context);
  const app = context.window.trialTest;
  app.setUser(user);
  return { app, requests, element: id => elements.get(id) };
}
function respond(request, body, status = 200) {
  request.resolve({ ok: status >= 200 && status < 300, status, json: async () => body });
}
test('Google is the first auth action and keeps the official mark', () => {
  const google = html.indexOf('id="google-auth"');
  const submit = html.indexOf('id="submit-auth"');
  assert.ok(google > 0 && google < submit);
  assert.match(html, /Continuar com o Google/);
  assert.match(html, /fill="#4285F4"/);
  assert.match(html, /Mais rápido · sem criar senha/);
});
test('public availability offers enrollment without claiming it is already selected', async () => {
  const { app, requests, element } = setup();
  app.load(); await flush(); respond(requests[0], available); await flush();
  assert.equal(element('enroll-trial').hidden, false);
  assert.match(element('trial-status').textContent, /pode liberar/);
  app.select(false); app.select(false); await flush();
  assert.equal(requests.length, 2);
  assert.equal(requests[1].path, '/subscriptions/trial/enrollment');
  assert.equal(requests[1].options.method, 'POST');
  respond(requests[1], { ...available, preauthorized: true }); await flush();
  assert.equal(element('enroll-trial').hidden, true);
  assert.match(element('trial-status').textContent, /prazo começa ao criar/);
  assert.equal(element('enroll-trial').disabled, false);
});
test('coupon is normalized on web and custom duration is confirmed from server', async () => {
  const { app, requests, element } = setup();
  element('trial-coupon').value = ' arena ';
  app.select(true); await flush();
  assert.equal(requests[0].path, '/subscriptions/trial/coupon');
  assert.deepEqual(JSON.parse(requests[0].options.body), { code: 'ARENA' });
  respond(requests[0], { ...available, offerMode: 'COUPON_ONLY', trialDays: 45, preauthorized: true }); await flush();
  assert.match(element('trial-status').textContent, /45 dias liberado/);
  assert.equal(element('enroll-trial').hidden, true);
});
test('invalid and rejected coupons never display a granted trial', async () => {
  const { app, requests, element } = setup();
  element('trial-coupon').value = 'A B'; app.select(true); await flush();
  assert.equal(requests.length, 0);
  element('trial-coupon').value = 'EXPIRED'; app.select(true); await flush();
  respond(requests[0], {}, 400); await flush();
  assert.match(element('trial-status').textContent, /inválido, expirado/);
  assert.equal(element('trial-coupon').value, 'EXPIRED');
  assert.equal(element('apply-trial-coupon').disabled, false);
});
test('stale lookup cannot overwrite a successful selection', async () => {
  const { app, requests, element } = setup();
  app.load(); await flush(); app.select(false); await flush();
  respond(requests[1], { ...available, preauthorized: true }); await flush();
  respond(requests[0], available); await flush();
  assert.match(element('trial-status').textContent, /liberado para esta conta/);
});
test('logout discards a late enrollment response and clears coupon', async () => {
  const { app, requests, element } = setup();
  element('trial-coupon').value = 'PRIVATE'; app.select(false); await flush(); app.logout();
  respond(requests[0], { ...available, preauthorized: true }); await flush();
  assert.equal(element('trial-coupon').value, '');
  assert.equal(element('enroll-trial').hidden, true);
  assert.doesNotMatch(element('trial-status').textContent, /liberado para esta conta/);
});
test('off mode hides enrollment and coupon; network errors allow retry', async () => {
  const { app, requests, element } = setup();
  app.load(); await flush(); requests[0].reject(Error('offline')); await flush();
  assert.match(element('trial-status').textContent, /Atualize/);
  app.load(); await flush(); respond(requests[1], { ...available, status: 'INELIGIBLE',
    canCreateGroup: false, canRedeemCoupon: false, offerMode: 'OFF' }); await flush();
  assert.equal(element('enroll-trial').hidden, true);
  assert.equal(element('trial-coupon-form').hidden, true);
});
