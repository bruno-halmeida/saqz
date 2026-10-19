// Login do painel com o Google: pop-up do Firebase escolhendo a conta e, depois, o mesmo /admin/me do login por
// e-mail (conta sem papel de admin vê a mesma recusa). Em produção o authDomain é o próprio adm.saqz.app e o nginx
// repassa /__/ ao Firebase, para a volta do Google não esbarrar no isolamento de armazenamento do Safari e do Chrome.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const flush = () => new Promise(setImmediate);
const settle = async () => { for (let i = 0; i < 6; i++) await flush(); };

const source = fs.readFileSync(require.resolve('../assets/admin-session.js'), 'utf8');
const root = path.join(__dirname, '..', '..');

function node() {
  const listeners = {};
  return { style: {}, textContent: '', value: '', disabled: false, listeners,
    addEventListener(type, listener) { listeners[type] = listener; } };
}

function setup({ adminStatus = 200 } = {}) {
  const t = { warnings: [], requests: [] };
  const selected = new Map();
  let overlay = null;
  let onState = null;
  let ready = null;
  const auth = {
    currentUser: null, popups: [], signOuts: 0,
    useEmulator() {},
    onAuthStateChanged(listener) { onState = listener; },
    signInWithPopup(provider) {
      auth.popups.push(provider);
      return new Promise((resolve, reject) => { t.popup = { resolve, reject }; });
    },
    signInWithEmailAndPassword() { return new Promise(() => {}); },
    signOut() { auth.signOuts++; auth.currentUser = null; onState(null); return Promise.resolve(); },
  };
  function GoogleAuthProvider() { this.providerId = 'google.com'; this.params = null; }
  GoogleAuthProvider.prototype.setCustomParameters = function (params) { this.params = params; };
  const document = {
    createElement(tag) {
      const element = node();
      if (tag !== 'div') return element;
      element.querySelector = selector => {
        const id = selector.match(/^#([\w-]+)/)[1];
        assert.ok(element.innerHTML.includes(`id="${id}"`), `${selector} não existe no login`);
        if (!selected.has(selector)) selected.set(selector, node());
        return selected.get(selector);
      };
      overlay = element;
      return element;
    },
    addEventListener(type, listener) { if (type === 'DOMContentLoaded') ready = listener; },
    body: { appendChild() {} },
  };
  const console = { warn: (...args) => t.warnings.push(args) };
  const context = vm.createContext({
    window: { console, SAQZ_FIREBASE_CONFIG: { apiKey: 'chave', authDomain: 'adm.saqz.test', projectId: 'saqz-test', apiBaseUrl: 'https://api.test' } },
    console,
    document,
    firebase: { initializeApp() {}, auth: Object.assign(() => auth, { GoogleAuthProvider }) },
    fetch: (url, options) => {
      t.requests.push({ url, options });
      return Promise.resolve({ ok: adminStatus === 200, status: adminStatus,
        json: () => Promise.resolve({ userId: 'u-1', email: 'dono@saqz.test', displayName: 'Dono' }) });
    },
  });
  vm.runInContext(source, context);
  ready();
  const user = { uid: 'fb-1', getIdToken: () => Promise.resolve('id-token') };
  return Object.assign(t, {
    auth, overlay, session: context.window.saqzAdmin,
    google: () => overlay.querySelector('#saqz-login-google'),
    error: () => overlay.querySelector('#saqz-login-erro'),
    clickGoogle: () => overlay.querySelector('#saqz-login-google').listeners.click(),
    // O Firebase avisa a troca de sessão depois do pop-up, como no login por e-mail.
    signedIn() { auth.currentUser = user; onState(user); },
    submitEmail(email, senha) {
      overlay.querySelector('#saqz-login-email').value = email;
      overlay.querySelector('#saqz-login-senha').value = senha;
      overlay.querySelector('#saqz-admin-login-form').listeners.submit({ preventDefault() {} });
    },
  });
}

test('o botão do Google fica acima do formulário e não envia o formulário de e-mail', () => {
  const html = setup().overlay.innerHTML;
  assert.match(html, /<button type="button" class="google" id="saqz-login-google">/);
  assert.match(html, /<span>Entrar com o Google<\/span>/);
  assert.ok(html.indexOf('saqz-login-google') < html.indexOf('saqz-admin-login-form'));
});

test('Entrar com o Google abre o pop-up do Google pedindo para escolher a conta', async () => {
  const t = setup();
  t.clickGoogle();
  assert.equal(t.auth.popups.length, 1);
  assert.equal(t.auth.popups[0].providerId, 'google.com');
  assert.equal(JSON.stringify(t.auth.popups[0].params), '{"prompt":"select_account"}');
  assert.equal(t.google().disabled, true);
  t.popup.resolve({ user: {} });
  await settle();
  assert.equal(t.google().disabled, false);
});

test('conta Google admin entra no painel pelo mesmo /admin/me do login por e-mail', async () => {
  const t = setup();
  t.clickGoogle();
  t.popup.resolve({ user: {} });
  t.signedIn();
  await settle();
  assert.equal(t.requests.length, 1);
  assert.equal(t.requests[0].url, 'https://api.test/admin/me');
  assert.equal(t.requests[0].options.headers.Authorization, 'Bearer id-token');
  assert.equal(t.overlay.style.display, 'none');
  assert.equal(t.session.me.email, 'dono@saqz.test');
  assert.equal(t.auth.signOuts, 0);
});

test('conta Google sem papel de admin vê a mesma recusa e sai; outro clique limpa a recusa', async () => {
  const t = setup({ adminStatus: 403 });
  t.clickGoogle();
  t.popup.resolve({ user: {} });
  t.signedIn();
  await settle();
  assert.equal(t.overlay.style.display, 'flex');
  assert.equal(t.error().textContent, 'Esta conta não tem acesso ao painel.');
  assert.equal(t.error().style.display, 'block');
  assert.equal(t.auth.signOuts, 1);
  assert.equal(t.session.me, null);

  t.clickGoogle();
  assert.equal(t.error().style.display, 'none');
  assert.equal(t.auth.popups.length, 2);
});

test('fechar o pop-up não mostra erro e libera o botão', async () => {
  const t = setup();
  t.clickGoogle();
  t.popup.reject({ code: 'auth/popup-closed-by-user', message: 'fechou' });
  await settle();
  assert.equal(t.error().style.display, 'none');
  assert.equal(t.google().disabled, false);
  assert.deepEqual(t.warnings.map(warning => warning[1]), ['auth/popup-closed-by-user']);
});

test('pop-up bloqueado e outras falhas avisam na tela e deixam o código no console', async () => {
  for (const [code, message] of [
    ['auth/popup-blocked', 'O navegador bloqueou a janela do Google. Libere pop-ups deste site e tente de novo.'],
    ['auth/unauthorized-domain', 'Não deu para entrar com o Google. Tente de novo.'],
  ]) {
    const t = setup();
    t.clickGoogle();
    t.popup.reject({ code, message: 'falhou' });
    await settle();
    assert.equal(t.error().textContent, message);
    assert.equal(t.error().style.display, 'block');
    assert.equal(t.google().disabled, false);
    assert.deepEqual(t.warnings.map(warning => warning[1]), [code]);
  }
});

test('o Entrar do e-mail desativa o próprio botão, não o do Google', () => {
  const t = setup();
  t.submitEmail('dono@saqz.test', 'segredo1');
  assert.equal(t.overlay.querySelector('#saqz-admin-login-form button').disabled, true);
  assert.equal(t.google().disabled, false);
});

test('em produção o authDomain é o próprio adm.saqz.app e o nginx repassa /__/ ao Firebase', () => {
  const window = {};
  vm.runInNewContext(fs.readFileSync(path.join(root, 'firebase-config.prod.js'), 'utf8'), { window });
  assert.equal(window.SAQZ_FIREBASE_CONFIG.authDomain, 'adm.saqz.app');
  const block = fs.readFileSync(path.join(root, 'nginx-adm-web-prod.conf'), 'utf8').match(/location \/__\/ \{([^}]*)\}/);
  assert.ok(block, 'falta o location /__/ no nginx-adm-web-prod.conf');
  assert.match(block[1], /proxy_pass https:\/\/saquz-app\.firebaseapp\.com;/);
  assert.match(block[1], /proxy_set_header Host saquz-app\.firebaseapp\.com;/);
  assert.match(block[1], /proxy_ssl_server_name on;/);
});
