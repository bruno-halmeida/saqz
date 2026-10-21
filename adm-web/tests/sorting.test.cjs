const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const vm = require('node:vm');

// Ordenação por coluna (sort/dir) sobre a lógica DC publicada, no mesmo molde de pagination.test.cjs.
const html = readFileSync(require.resolve('../index.html'), 'utf8');
const source = html.match(/<script type="text\/x-dc"[^>]*>([\s\S]*?)<\/script>/)[1];
const configs = [
  ['users', 'carregarUsuarios', '/admin/users', 'name', 'city', { uBusca: 'bia', uStatus: 'active' }, 'query=bia&status=active&'],
  ['groups', 'carregarGrupos', '/admin/groups', 'members', 'owner', { gStatus: 'deleted' }, 'status=deleted&'],
  ['subs', 'carregarAssinaturas', '/admin/subscriptions', 'owner', 'plan', { sOrigem: 'SITE' }, ''],
];
function setup() {
  let session;
  const requests = [];
  const context = vm.createContext({
    DCLogic: class {
      props = {};
      setState(patch, done) { Object.assign(this.state, patch); done?.(); }
    },
    window: { saqzAdmin: { me: {}, onSession(fn) { session = fn; }, fetchAdmin() { return new Promise(() => {}); } } },
    setTimeout() { return Symbol(); },
    clearTimeout() {},
  });
  const Component = vm.runInContext(`${source}\nComponent`, context);
  const app = new Component();
  app.api = (path) => new Promise((resolve, reject) => { if (!path.startsWith('/admin/revenue')) requests.push({ path, resolve, reject }); });
  app.componentDidMount();
  return { app, requests, logout() { context.window.saqzAdmin.me = null; session(null); } };
}
const flush = () => new Promise(setImmediate);
const result = (page, id, total = 51) => ({ page, size: 25, total, items: [{ id }] });
const query = (path) => path.slice(path.indexOf('?') + 1);
// Monta a query esperada na ordem de cada lista (assinaturas põem página antes da origem).
function expected(key, filtros, page, sort) {
  const ordem = sort ? `&sort=${sort}` : '';
  if (key === 'subs') return `page=${page}&size=25${filtros ? '&source=SITE' : ''}${ordem}`;
  return `${filtros || ''}page=${page}&size=25${ordem}`;
}

for (const [key, load, endpoint, col, other, filtros, filtroQuery] of configs) {
  const sorter = (app, c = col) => app.ordenacao(key, c);

  test(`${key}: clicar ordena asc, inverte e volta ao padrão, sempre na página 1`, async () => {
    const { app, requests } = setup();
    app[load](); requests[0].resolve(result(1, 'p1')); await flush();
    app.paginacao(key).onProxima(); requests[1].resolve(result(2, 'p2')); await flush();
    assert.equal(sorter(app).aria, 'none');

    sorter(app).onClick();
    assert.equal(query(requests[2].path), expected(key, '', 1, `${col}&dir=asc`));
    assert.equal(app.state[`${key}PageNumber`], 1);
    requests[2].resolve(result(1, 'asc')); await flush();
    assert.equal(sorter(app).aria, 'ascending');
    assert.equal(sorter(app).seta, ' ▲');
    assert.equal(sorter(app, other).aria, 'none');

    // Próxima mantém a ordenação.
    app.paginacao(key).onProxima();
    assert.equal(query(requests[3].path), expected(key, '', 2, `${col}&dir=asc`));
    requests[3].resolve(result(2, 'asc-2')); await flush();

    sorter(app).onClick();
    assert.equal(query(requests[4].path), expected(key, '', 1, `${col}&dir=desc`));
    requests[4].resolve(result(1, 'desc')); await flush();
    assert.equal(sorter(app).aria, 'descending');
    assert.equal(sorter(app).seta, ' ▼');

    sorter(app).onClick();
    assert.equal(query(requests[5].path), expected(key, '', 1, ''));
    requests[5].resolve(result(1, 'padrao')); await flush();
    assert.equal(sorter(app).aria, 'none');
    assert.equal(app.state[`${key}Sort`], null);
  });

  test(`${key}: outra coluna recomeça em asc e filtros continuam no pedido`, async () => {
    const { app, requests } = setup();
    Object.assign(app.state, filtros);
    app[load](); requests[0].resolve(result(1, 'p1')); await flush();
    sorter(app).onClick(); requests[1].resolve(result(1, 'a')); await flush();
    sorter(app, other).onClick();
    assert.equal(query(requests[2].path), expected(key, filtroQuery || 'x', 1, `${other}&dir=asc`));
    assert.equal(sorter(app).aria, 'none');
  });

  test(`${key}: bloqueado durante a carga e resposta antiga não repovoa`, async () => {
    const { app, requests } = setup();
    app[load]();
    assert.equal(sorter(app).disabled, true);
    sorter(app).onClick();
    assert.equal(requests.length, 1);
    requests[0].resolve(result(1, 'p1')); await flush();
    app.paginacao(key).onProxima();
    // Clique enquanto a página 2 carrega é ignorado; quando ela chega, a ordenação nova sai.
    sorter(app).onClick();
    assert.equal(requests.length, 2);
    requests[1].resolve(result(2, 'p2')); await flush();
    sorter(app).onClick();
    const velho = requests[2];
    // A resposta da ordenação asc chega depois que a desc já foi pedida: é descartada.
    app.state[`${key}Page`] = result(1, 'fake');
    sorter(app).onClick();
    requests[3].resolve(result(1, 'desc')); await flush();
    velho.resolve(result(1, 'asc-velho')); await flush();
    assert.equal(app.state[`${key}Page`].items[0].id, 'desc');
  });

  test(`${key}: logout limpa a ordenação`, async () => {
    const { app, requests, logout } = setup();
    app[load](); requests[0].resolve(result(1, 'p1')); await flush();
    sorter(app).onClick();
    assert.ok(app.state[`${key}Sort`]);
    logout();
    assert.equal(app.state[`${key}Sort`], null);
  });
}

test('título fica igual ao de hoje: botão herda fonte, cor e caixa, com foco visível; VALOR e a seta ficam sem botão', () => {
  assert.match(html, /\.col-sort\{font:inherit;letter-spacing:inherit;color:inherit;text-transform:inherit;[^}]*cursor:pointer/);
  assert.match(html, /\.col-sort:focus-visible\{outline:2px solid/);
  assert.match(html, /<span>VALOR<\/span>/);
  assert.equal((html.match(/class="col-sort"/g) || []).length, 6 + 6 + 7);
  assert.match(html, /aria-sort="\{\{ uSort\.name\.aria \}\}"/);
});
