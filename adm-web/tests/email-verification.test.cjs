// Conta criada por e-mail no /comecar/ recebe o "Confirme seu e-mail no Saqz", como no app:
// POST /api/email-verification/request, autenticado pelo token do Firebase, sem corpo.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const flush = () => new Promise(setImmediate);
const source = fs.readFileSync(require.resolve('../comecar/comecar.js'), 'utf8');
const html = fs.readFileSync(require.resolve('../comecar/index.html'), 'utf8');

function setup({ verificationFails = false } = {}) {
  const elements = new Map([...html.matchAll(/id="([^"]+)"/g)].map(([, id]) => [id, {
    value: '', hidden: false, disabled: false, textContent: '',
    classList: { toggle() {} }, reset() {}, removeAttribute() {}, setAttribute() {}, getAttribute() { return null; },
  }]));
  const requests = [];
  const user = { uid: 'fb-1', getIdToken: async () => 'firebase-token', updateProfile: async () => {} };
  const fetch = (path, options) => {
    requests.push({ path, options });
    if (path.endsWith('/api/email-verification/request') && verificationFails) return Promise.reject(new Error('offline'));
    return new Promise(() => {}); // a sessão e o resto não importam aqui
  };
  const context = vm.createContext({ window: { SAQZ_FIREBASE_CONFIG: { apiBaseUrl: 'https://api.test/' } }, URL, URLSearchParams,
    document: { readyState: 'loading', addEventListener() {}, getElementById: id => elements.get(id) }, fetch });
  vm.runInContext(source.replace('  if (document.readyState', `
    window.t = {
      arm() {
        auth = { currentUser: window.user,
          createUserWithEmailAndPassword: () => Promise.resolve({ user: window.user }),
          signInWithEmailAndPassword: () => Promise.resolve({ user: window.user }) };
      },
      submit(mode) { setMode(mode); authenticateWithCredentials({ preventDefault() {} }); },
    };
    if (document.readyState`), context);
  context.window.user = user;
  context.window.t.arm();
  elements.get('email').value = 'ana@example.com';
  elements.get('password').value = 'segredo1';
  elements.get('name').value = 'Ana';
  return { t: context.window.t, requests };
}
const verification = requests => requests.filter(r => r.path.endsWith('/api/email-verification/request'));
const settle = async () => { for (let i = 0; i < 6; i++) await flush(); };

test('criar conta por e-mail pede o e-mail de confirmação com o token do Firebase e sem corpo', async () => {
  const { t, requests } = setup();
  t.submit('signup');
  await settle();
  const calls = verification(requests);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].path, 'https://api.test/api/email-verification/request');
  assert.equal(calls[0].options.method, 'POST');
  assert.equal(calls[0].options.headers.Authorization, 'Bearer firebase-token');
  assert.equal(calls[0].options.body, undefined);
});

test('entrar numa conta existente não pede a confirmação', async () => {
  const { t, requests } = setup();
  t.submit('login');
  await settle();
  assert.equal(verification(requests).length, 0);
});

test('se o pedido falhar, o cadastro segue para a sessão sem erro na tela', async () => {
  const { t, requests } = setup({ verificationFails: true });
  t.submit('signup');
  await settle();
  assert.equal(verification(requests).length, 1);
  assert.ok(requests.some(r => r.path === 'https://api.test/api/session'), 'a sessão continua sendo criada');
});

test('o cadastro com Google não pede a confirmação de e-mail', () => {
  const google = source.slice(source.indexOf('function authenticateWithGoogle'), source.indexOf('function staleProvider'));
  assert.doesNotMatch(google, /requestEmailVerification/);
});
