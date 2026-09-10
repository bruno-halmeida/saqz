const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const vm = require('node:vm');

// Exercise the shipped DC logic, not a second implementation of pagination.
const html = readFileSync(require.resolve('../index.html'), 'utf8');
const source = html.match(/<script type="text\/x-dc"[^>]*>([\s\S]*?)<\/script>/)[1];
const configs = [
  ['users', 'carregarUsuarios', '/admin/users', 'usuarios'],
  ['groups', 'carregarGrupos', '/admin/groups', 'grupos'],
  ['subs', 'carregarAssinaturas', '/admin/subscriptions', 'receita'],
];
function setup() {
  let session;
  const timers = new Map();
  const context = vm.createContext({
    DCLogic: class {
      props = {};
      setState(patch, done) { Object.assign(this.state, patch); done?.(); }
    },
    window: { saqzAdmin: { me: {}, onSession(fn) { session = fn; } } },
    setTimeout(fn) { const id = Symbol(); timers.set(id, fn); return id; },
    clearTimeout(id) { timers.delete(id); },
  });
  const Component = vm.runInContext(`${source}\nComponent`, context);
  const app = new Component();
  const requests = [];
  app.api = (path) => new Promise((resolve, reject) => requests.push({ path, resolve, reject }));
  return { app, requests, timers, logout() { session(null); } };
}
const flush = () => new Promise(setImmediate);
const result = (page, total = 51) => ({ page, size: 25, total, items: [{ id: `page-${page}` }] });

for (const [key, load, endpoint, route] of configs) {
  test(`${key}: pages 1/2/3, bounds, busy guard and return from detail`, async () => {
    const { app, requests } = setup();
    app[load]();
    assert.equal(requests[0].path, `${endpoint}?page=1&size=25`);
    assert.equal(app.paginacao(key).anteriorDesabilitado, true);
    assert.equal(app.paginacao(key).proximaDesabilitada, true);
    requests[0].resolve(result(1)); await flush();
    assert.equal(app.paginacao(key).rodape, 'Mostrando 1–1 de 51 · página 1 de 3');
    app.paginacao(key).onProxima();
    app.paginacao(key).onProxima();
    assert.equal(requests.length, 2);
    assert.equal(requests[1].path, `${endpoint}?page=2&size=25`);
    requests[1].resolve(result(2)); await flush();
    app.go(route);
    assert.equal(requests.length, 2);
    assert.equal(app.state[`${key}Page`].items[0].id, 'page-2');
    app.paginacao(key).onProxima();
    requests[2].resolve(result(3)); await flush();
    assert.equal(app.paginacao(key).proximaDesabilitada, true);
    app.paginacao(key).onProxima();
    assert.equal(requests.length, 3);
    app.paginacao(key).onAnterior();
    assert.equal(requests[3].path, `${endpoint}?page=2&size=25`);
  });

  test(`${key}: zero results cannot move beyond first page`, async () => {
    const { app, requests } = setup(); app[load]();
    requests[0].resolve({ ...result(1, 0), items: [] }); await flush();
    assert.equal(app.paginacao(key).rodape, 'Nenhum resultado · página 1 de 1');
    app.paginacao(key).onProxima(); app.paginacao(key).onAnterior();
    assert.equal(requests.length, 1);
  });

  test(`${key}: failed page retries same page, previous works and refresh resets index`, async () => {
    const { app, requests } = setup(); app[load](2);
    requests[0].reject(new Error('offline')); await flush();
    assert.equal(app.paginacao(key).rodape, 'Não foi possível carregar a página 2. Tente novamente.');
    assert.equal(app.paginacao(key).recarregarLabel, 'Tentar novamente');
    app.paginacao(key).onRecarregar();
    assert.equal(requests[1].path, `${endpoint}?page=2&size=25`);
    requests[1].reject(new Error('offline')); await flush();
    app.paginacao(key).onAnterior();
    assert.equal(requests[2].path, `${endpoint}?page=1&size=25`);
    requests[2].resolve(result(1)); await flush();
    app[load](3); requests[3].resolve(result(3)); await flush();
    app.paginacao(key).onRecarregar();
    assert.equal(requests[4].path, `${endpoint}?page=1&size=25`);
  });

  test(`${key}: late response never overwrites a newer page`, async () => {
    const { app, requests } = setup(); app[load](2); app[load](1);
    requests[1].resolve(result(1)); await flush();
    requests[0].resolve(result(2)); await flush();
    assert.equal(app.state[`${key}PageNumber`], 1);
    assert.equal(app.state[`${key}Page`].items[0].id, 'page-1');
  });
}

for (const [key, load, filter, field, query] of [
  ['users', 'carregarUsuarios', 'filtroUsuarios', 'uBusca', 'nome & e-mail'],
  ['groups', 'carregarGrupos', 'filtroGrupos', 'gBusca', 'quadra & praia'],
]) {
  test(`${key}: filter invalidates in-flight response before debounce and resets page`, async () => {
    const { app, requests, timers } = setup();
    app[load](3);
    app[filter](field)({ target: { value: query } });
    requests[0].resolve(result(3)); await flush();
    assert.equal(app.state[`${key}Page`], null);
    assert.equal(app.state[`${key}PageNumber`], 1);
    for (const fn of timers.values()) fn();
    assert.match(requests[1].path, new RegExp(`query=${encodeURIComponent(query)}&page=1&size=25`));
    requests[1].resolve(result(1)); await flush();
    app.paginacao(key).onProxima();
    assert.match(requests[2].path, new RegExp(`query=${encodeURIComponent(query)}&page=2&size=25`));
  });
}

test('logout clears page indices and pending filter timers', () => {
  const { app, timers, logout } = setup();
  app.componentDidMount();
  Object.assign(app.state, { usersPageNumber: 3, groupsPageNumber: 4, subsPageNumber: 5 });
  app.filtroUsuarios('uBusca')({ target: { value: 'Ana' } });
  app.filtroGrupos('gBusca')({ target: { value: 'Praia' } });
  logout();
  for (const [key] of configs) {
    assert.equal(app.state[`${key}PageNumber`], 1);
    assert.equal(app.state[`${key}Page`], null);
  }
  assert.equal(timers.size, 0);
});
