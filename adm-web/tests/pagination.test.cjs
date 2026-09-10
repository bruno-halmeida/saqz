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
function setup(realApi = false) {
    let session;
  const requests = [];
  const timers = new Map();
  const context = vm.createContext({
    DCLogic: class {
      props = {};
      setState(patch, done) { Object.assign(this.state, patch); done?.(); }
    },
    window: { saqzAdmin: {
      me: {}, onSession(fn) { session = fn; },
      fetchAdmin(path) { return new Promise((resolve, reject) => requests.push({ path, resolve, reject })); },
    } },
    setTimeout(fn) { const id = Symbol(); timers.set(id, fn); return id; },
    clearTimeout(id) { timers.delete(id); },
  });
  const Component = vm.runInContext(`${source}\nComponent`, context);
  const app = new Component();
  if (!realApi) app.api = (path) => new Promise((resolve, reject) => requests.push({ path, resolve, reject }));
  return { app, requests, timers, logout() { context.window.saqzAdmin.me = null; session(null); } };
}
const flush = () => new Promise(setImmediate);
const result = (page, total = 51) => ({ page, size: 25, total, items: [{ id: `page-${page}` }] });

for (const [key, load, endpoint, route] of configs) {
  test(`${key}: shrinking total relocates to last valid page`, async () => {
    const { app, requests } = setup(); app[load](3);
    requests[0].resolve({ ...result(3, 20), items: [] }); await flush();
    assert.equal(requests[1].path, `${endpoint}?page=1&size=25`);
    assert.equal(app.state[`${key}PageNumber`], 1);
    assert.equal(app.state[`${key}Page`], null);
    requests[1].resolve(result(1, 20)); await flush();
    assert.equal(app.paginacao(key).rodape, 'Mostrando 1–1 de 20 · página 1 de 1');
    assert.equal(app.paginacao(key).proximaDesabilitada, true);
  });

  test(`${key}: reload while busy does not send another request`, () => {
    const { app, requests } = setup(); app[load]();
    app.paginacao(key).onRecarregar();
    assert.equal(app.paginacao(key).carregando, true);
    assert.equal(requests.length, 1);
  });

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

for (const [key, load, endpoint, filters] of [
  ['users', 'carregarUsuarios', '/admin/users', { uBusca: 'Ana & Silva', uPlano: 'ORGANIZADOR', uStatus: 'suspended' }],
  ['groups', 'carregarGrupos', '/admin/groups', { gBusca: 'Praia & Sol', gStatus: 'deleted' }],
]) {
  test(`${key}: all filters survive next, refresh and retry`, async () => {
    const { app, requests } = setup(); Object.assign(app.state, filters); app[load](2);
    const expectedQuery = key === 'users'
      ? 'query=Ana%20%26%20Silva&plan=ORGANIZADOR&status=suspended'
      : 'query=Praia%20%26%20Sol&status=deleted';
    assert.equal(requests[0].path, `${endpoint}?${expectedQuery}&page=2&size=25`);
    requests[0].resolve(result(2)); await flush();
    app.paginacao(key).onProxima();
    assert.equal(requests[1].path, `${endpoint}?${expectedQuery}&page=3&size=25`);
    requests[1].reject(new Error('offline')); await flush();
    app.paginacao(key).onRecarregar();
    assert.equal(requests[2].path, `${endpoint}?${expectedQuery}&page=3&size=25`);
    requests[2].resolve(result(3)); await flush();
    app.paginacao(key).onRecarregar();
    assert.equal(requests[3].path, `${endpoint}?${expectedQuery}&page=1&size=25`);
    for (const [field, value] of Object.entries(filters)) assert.equal(app.state[field], value);
  });
}

for (const [key, load, rows, back, detail] of [
  ['users', 'carregarUsuarios', 'usuariosRows', 'goUsu', 'usuario'],
  ['groups', 'carregarGrupos', 'gruposRows', 'goGru', 'grupo'],
  ['subs', 'carregarAssinaturas', 'assinRows', 'goRec', 'assinatura'],
]) {
  test(`${key}: row opens detail and actual back callback restores cached page`, async () => {
    const { app, requests } = setup(); app[load](2);
    const item = { id: 'record-26', userId: 'u26', groupId: 'g26', ownerUserId: 's26', displayName: 'Ana', name: 'Praia' };
    requests[0].resolve({ ...result(2), items: [item] }); await flush();
    app.renderVals()[rows][0].onOpen();
    assert.equal(app.state.page, detail);
    const requestsBeforeBack = requests.length;
    app.renderVals()[back]();
    assert.equal(requests.length, requestsBeforeBack);
    assert.equal(app.state[`${key}PageNumber`], 2);
    assert.equal(app.state[`${key}Page`].items[0].id, 'record-26');
  });
}

test('logout purges populated pages and rejects responses from the old session epoch', async () => {
  const { app, requests, logout } = setup(true);
  app.componentDidMount();
  for (const [, load] of configs) app[load](3);
  for (const request of requests) request.resolve({ ok: true, json: async () => result(3) });
  await flush();
  for (const [key] of configs) {
    assert.equal(app.state[`${key}PageNumber`], 3);
    assert.equal(app.state[`${key}Page`].items[0].id, 'page-3');
  }
  app.carregarUsuarios(2);
  const late = requests.at(-1);
  logout();
  late.resolve({ ok: true, json: async () => result(2) }); await flush();
  for (const [key] of configs) {
    assert.equal(app.state[`${key}PageNumber`], 1);
    assert.equal(app.state[`${key}Page`], null);
  }
});

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
