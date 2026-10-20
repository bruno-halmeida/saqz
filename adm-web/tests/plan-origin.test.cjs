const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const source = require('node:fs').readFileSync(require.resolve('../index.html'), 'utf8').match(/<script type="text\/x-dc"[^>]*>([\s\S]*?)<\/script>/)[1];

function setup() {
  const ctx = vm.createContext({
    DCLogic: class { props = {}; setState(p, done) { Object.assign(this.state, typeof p === 'function' ? p(this.state) : p); done?.(); } },
    window: { saqzAdmin: { me: {}, onSession() {}, fetchAdmin: () => Promise.resolve({ ok: false, status: 404, json: async () => ({}) }) } },
    setTimeout, clearTimeout, console, Date, Promise,
  });
  const App = vm.runInContext(source + '\nComponent', ctx);
  const app = new App(); app.componentDidMount();
  return app;
}
const fim = '2099-11-30T15:00:00Z';

test('origem do plano: um texto para cada fonte', () => {
  const app = setup();
  assert.equal(app.planoOrigem('SITE', null), 'Site');
  assert.equal(app.planoOrigem('APP_STORE', null), 'App Store');
  assert.equal(app.planoOrigem('GOOGLE_PLAY', null), 'Google Play');
  assert.equal(app.planoOrigem('COURTESY', fim), 'Cortesia até ' + app.dataBr(fim));
  assert.equal(app.planoOrigem('COURTESY', null), 'Cortesia sem prazo');
  assert.equal(app.planoOrigem('TRIAL', fim), 'Teste até ' + app.dataBr(fim));
  assert.match(app.planoOrigem('COURTESY', fim), /^Cortesia até \d{2}\/\d{2}\/\d{4}$/);
});

test('backend antigo, sem campo de origem: nada é acrescentado', () => {
  const app = setup();
  assert.equal(app.planoOrigem(undefined, undefined), '');
  assert.equal(app.planoOrigem(null, null), '');
  assert.equal(app.planoOrigem('OUTRA', fim), '');
});

test('nome do plano em português e vazio quando não há plano', () => {
  const app = setup();
  assert.equal(app.planoNome('TITULAR'), 'Titular');
  assert.equal(app.planoNome('ORGANIZADOR'), 'Organizador');
  assert.equal(app.planoNome('ILIMITADO'), 'Ilimitado');
  assert.equal(app.planoNome(null), '');
});
