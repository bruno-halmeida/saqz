const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const source = require('node:fs').readFileSync(require.resolve('../index.html'), 'utf8').match(/<script type="text\/x-dc"[^>]*>([\s\S]*?)<\/script>/)[1];
const flush = () => new Promise(setImmediate);
const json = (data, status = 200) => ({ ok: status < 400, status, json: async () => data });

// rotas: { 'METHOD path': resposta | (body) => resposta }; sem rota = 404.
function setup(rotas = {}) {
  let session; const requests = [];
  const ctx = vm.createContext({
    DCLogic: class { props = {}; setState(p, done) { Object.assign(this.state, typeof p === 'function' ? p(this.state) : p); done?.(); } },
    window: { saqzAdmin: { me: {}, onSession(fn) { session = fn; }, fetchAdmin(path, options = {}) {
      const method = options.method || 'GET';
      const body = options.body ? JSON.parse(options.body) : undefined;
      requests.push({ method, path, body });
      const r = rotas[`${method} ${path}`];
      return Promise.resolve(typeof r === 'function' ? r(body) : r || json({}, 404));
    } } },
    setTimeout, clearTimeout, console, Date, Promise,
  });
  const App = vm.runInContext(source + '\nComponent', ctx);
  const app = new App(); app.componentDidMount();
  return { app, requests };
}
const futuro = () => { const d = new Date(Date.now() + 40 * 864e5); return [String(d.getDate()).padStart(2, '0'), String(d.getMonth() + 1).padStart(2, '0'), d.getFullYear()].join('/'); };


const preencher = (app, extra = {}) => app.editarCupom({ campanha: 'Volta às quadras', cod: 'volta30', desc: 30, meses: 3, ...extra });

test('corpo do cupom: campanha obrigatória, produtos e quem pode usar pelos segmentados', () => {
  const { app } = setup();
  assert.equal(typeof app.corpoCupom(), 'string');
  preencher(app, { plano: 'ORGANIZADOR', ciclo: 'MONTHLY', quem: 'NEVER_SUBSCRIBED', limite: '100' });
  assert.equal(JSON.stringify(app.corpoCupom()), JSON.stringify({
    code: 'VOLTA30', campaign: 'Volta às quadras', discountPercent: 30, durationCycles: 3,
    products: ['ORGANIZADOR:MONTHLY'], eligibility: ['NEVER_SUBSCRIBED'], maxUses: 100,
  }));
  app.editarCupom({ meses: 0 });
  assert.equal(app.corpoCupom().durationCycles, undefined);
  assert.equal(app.cupomFormVals().ncMesesTxt, 'Para sempre — só site');
});

test('criar mostra a prévia antes e só cria no segundo clique; mudar o formulário descarta a prévia', async () => {
  const preview = { stores: true, storeRejection: null, rows: [{ plan: 'ORGANIZADOR', cycle: 'MONTHLY', siteListPriceCents: 5990, sitePriceCents: 4193, storeListPriceCents: 5990, googlePlayPriceCents: 4193, appStorePriceCents: 4190, appStoreEstimated: false }] };
  const { app, requests } = setup({
    'POST /admin/coupons/preview': json(preview),
    'POST /admin/coupons': json({ id: 'c1', code: 'VOLTA30' }, 201),
    'GET /admin/coupons': json([]),
  });
  preencher(app, { plano: 'ORGANIZADOR', ciclo: 'MONTHLY' });
  app.criarCupom(); await flush(); await flush();
  assert.equal(requests.filter(r => r.path === '/admin/coupons').length, 0);
  const v = app.cupomFormVals();
  assert.equal(v.ncBotao, 'Confirmar e criar');
  assert.equal(v.ncPrevRows[0].ios, app.money(4190));
  app.criarCupom(); await flush(); await flush();
  assert.equal(requests.filter(r => r.method === 'POST' && r.path === '/admin/coupons').length, 1);
  preencher(app); assert.equal(app.state.ncPrev, null);
});

test('recusa da prévia (ex.: anual com mais de 1 ciclo) aparece no formulário', async () => {
  const { app } = setup({ 'POST /admin/coupons/preview': json({ message: 'Na App Store o desconto do plano anual vale só para o primeiro ano' }, 400) });
  preencher(app, { ciclo: 'ANNUAL' });
  app.criarCupom(); await flush(); await flush();
  const v = app.cupomFormVals();
  assert.equal(v.ncPrevErro, true);
  assert.match(v.ncPrevErroTxt, /anual/);
  assert.equal(v.ncBotao, 'Ver preços');
});

test('lista: estado por plataforma, motivo da falha e Tentar de novo', () => {
  const { app } = setup();
  const falha = app.estadoLoja({ state: 'FAILED', message: 'Google Play: offers.create respondeu 400' });
  assert.match(falha.txt, /^Falhou: Google Play/);
  assert.equal(app.estadoLoja({ state: 'WAITING_STORE' }).txt, 'Liberando na Apple (até 1 h)');
  assert.equal(app.estadoLoja(null).txt, '—');
  assert.equal(app.estadoLoja({ state: 'ACTIVE' }).txt, 'Ativo');
});
