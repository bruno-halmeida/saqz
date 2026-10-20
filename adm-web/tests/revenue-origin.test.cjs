const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const html = require('node:fs').readFileSync(require.resolve('../index.html'), 'utf8');
const source = html.match(/<script type="text\/x-dc"[^>]*>([\s\S]*?)<\/script>/)[1];

function setup(rotas = {}) {
  const requests = [];
  const ctx = vm.createContext({
    DCLogic: class { props = {}; setState(p, done) { Object.assign(this.state, typeof p === 'function' ? p(this.state) : p); done?.(); } },
    window: { saqzAdmin: { me: {}, onSession() {}, fetchAdmin(path) {
      requests.push(path);
      const r = rotas[path];
      return Promise.resolve(r ? { ok: true, status: 200, json: async () => r } : { ok: false, status: 404, json: async () => ({}) });
    } } },
    setTimeout, clearTimeout, console, Date, Promise,
  });
  const App = vm.runInContext(source + '\nComponent', ctx);
  const app = new App(); app.componentDidMount();
  return { app, requests };
}
const flush = () => new Promise(setImmediate);

test('chips de origem e de cobrança: Real, Teste e Sem cobrança', () => {
  const { app } = setup();
  assert.equal(app.origemNome('SITE'), 'Site');
  assert.equal(app.origemNome('APP_STORE'), 'App Store');
  assert.equal(app.origemNome('GOOGLE_PLAY'), 'Google Play');
  assert.equal(app.origemNome('COURTESY'), 'Cortesia');
  assert.equal(app.origemNome('TRIAL'), 'Teste gratuito');
  assert.equal(app.origemNome(undefined), 'Site');
  assert.equal(app.cobrancaChip('REAL').txt, 'Real');
  assert.equal(app.cobrancaChip('TEST').txt, 'Teste');
  assert.equal(app.cobrancaChip('NO_CHARGE').txt, 'Sem cobrança');
});

test('receita: "a definir" onde não há líquido, moeda quando não é BRL e mês em formato brasileiro', () => {
  const { app } = setup();
  const [site, apple, usd, google] = app.receitaLinhas([
    { month: '2026-10', source: 'SITE', currency: 'BRL', payments: 2, grossCents: 11980, netCents: 11400 },
    { month: '2026-10', source: 'APP_STORE', currency: 'BRL', payments: 1, grossCents: 5990, netCents: null },
    { month: '2026-09', source: 'APP_STORE', currency: 'USD', payments: 3, grossCents: 2997, netCents: null },
    { month: '2026-09', source: 'GOOGLE_PLAY', currency: null, payments: 4, grossCents: null, netCents: null },
  ]);
  assert.equal(site.mes, '10/2026');
  assert.equal(site.origem, 'Site');
  assert.equal(site.pagamentos, '2');
  assert.match(site.bruto, /119,80/);
  assert.match(site.liquido, /114,00/);
  assert.equal(apple.liquido, 'a definir');
  assert.equal(apple.moeda, '');
  assert.equal(usd.moeda, 'USD');
  assert.match(usd.bruto, /29,97/);
  assert.match(usd.bruto, /US\$|USD/);
  assert.equal(google.bruto, 'a definir');
  assert.equal(app.receitaLinhas(null).length, 0);
});

test('filtro de origem vai na consulta da lista de assinaturas', async () => {
  const { app, requests } = setup({
    '/admin/subscriptions?page=1&size=25': { items: [], total: 0 },
    '/admin/subscriptions?page=1&size=25&source=COURTESY': { items: [], total: 0 },
  });
  app.carregarAssinaturas(1);
  await flush();
  app.filtroOrigem()({ target: { value: 'COURTESY' } });
  await flush();
  assert.ok(requests.includes('/admin/subscriptions?page=1&size=25'));
  assert.ok(requests.includes('/admin/subscriptions?page=1&size=25&source=COURTESY'));
});

test('receita carrega de /admin/revenue e erro vira mensagem, não quebra', async () => {
  const { app, requests } = setup({ '/admin/revenue?months=12': { real: [], test: [] } });
  app.carregarReceita();
  await flush();
  assert.ok(requests.includes('/admin/revenue?months=12'));
  assert.deepEqual(app.state.revenueData, { real: [], test: [] });
  const { app: falha } = setup();
  falha.carregarReceita();
  await flush();
  assert.equal(falha.state.revenueData.erro, true);
});

test('a tela tem o filtro de origem, o bloco de receita, a linha de teste à parte e a linha só abre o site', () => {
  assert.match(html, /Origem: todas/);
  assert.match(html, /Receita por mês e origem/);
  assert.match(html, /FORA DA RECEITA/);
  assert.match(html, /\{\{ a\.cobrancaTxt \}\}/);
  assert.match(html, /site \? \(\) => this\.go\("assinatura"/);
});
