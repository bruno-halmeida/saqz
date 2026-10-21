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


const summary = (over = {}) => ({ partner: { id: 'p1', name: 'Arena Beach Moema', active: true, contactName: 'Rui', contactPhone: '11999990000', ...over }, coupons: [{ code: 'ARENA30' }, { code: 'ARENATESTE' }], metrics: { users: 5, payers: 2, activeSubscribers: 1, revenueCents: 8383, revenueBySource: {}, paymentsWithoutValue: 0 } });

test('lista de parceiros com números e só os ativos nas opções dos cupons', async () => {
  const { app } = setup({ 'GET /admin/partners': json([summary(), summary({ id: 'p2', name: 'Antiga', active: false })]) });
  app.carregarParceiros(); await flush();
  const v = app.parceiroVals();
  assert.equal(v.parRows[0].cupons, 'ARENA30, ARENATESTE');
  assert.equal(v.parRows[0].pagaram, '2');
  assert.equal(JSON.stringify(v.parceiroOpcoes), JSON.stringify([{ id: 'p1', nome: 'Arena Beach Moema' }]));
});

test('relatório pede o período em São Paulo e mostra pagantes e valor desconhecido', async () => {
  const report = { partner: summary().partner, coupons: [], metrics: { users: 3, payers: 1, activeSubscribers: 1, revenueCents: 4190, revenueBySource: { APP_STORE: 4190 }, paymentsWithoutValue: 1 },
    payers: [{ userId: 'u', name: 'Ana', email: 'ana@x.com', plan: 'ORGANIZADOR', cycle: 'MONTHLY', source: 'GOOGLE_PLAY', firstCouponUseAt: '2026-10-01T12:00:00Z', firstPaymentAt: '2026-10-02T12:00:00Z', totalPaidCents: 0, paymentsWithoutValue: 1, status: 'PAST_DUE' }] };
  const { app, requests } = setup({ 'GET /admin/partners/p1/report?from=2026-10-01T03%3A00%3A00.000Z&to=2026-10-31T02%3A59%3A59.999Z': json(report) });
  app.setState({ partnerSel: 'p1', prDe: '01/10/2026', prAte: '30/10/2026' });
  app.carregarRelatorioParceiro(); await flush();
  assert.equal(requests.at(-1).path, '/admin/partners/p1/report?from=2026-10-01T03%3A00%3A00.000Z&to=2026-10-31T02%3A59%3A59.999Z');
  const v = app.parceiroVals();
  assert.equal(v.prKpis[1].valor, '1');
  assert.match(v.prPorPlataforma, /1 pagamento\(s\) sem valor/);
  assert.equal(v.prPagantes[0].plataforma, 'Android');
  assert.match(v.prPagantes[0].total, /\+\?$/);
  app.setState({ prDe: '31/02/2026' }); app.carregarRelatorioParceiro();
  assert.match(app.state.prErroTxt, /dd\/mm\/aaaa/);
});

test('cupom com "+ Novo parceiro" cria o parceiro antes e manda o partnerId', async () => {
  const { app, requests } = setup({
    'POST /admin/coupons/preview': json({ stores: false, rows: [] }),
    'POST /admin/partners': json({ id: 'novo' }, 201),
    'POST /admin/coupons': json({ id: 'c1' }, 201),
    'GET /admin/coupons': json([]), 'GET /admin/partners': json([]),
  });
  app.editarCupom({ campanha: 'Arena', cod: 'ARENA30', meses: 0, parceiro: 'NOVO', parceiroNovo: 'Arena Moema' });
  app.criarCupom(); await flush(); await flush();
  app.criarCupom(); for (let i = 0; i < 6; i++) await flush();
  assert.equal(requests.find(r => r.path === '/admin/partners' && r.method === 'POST').body.name, 'Arena Moema');
  assert.equal(requests.find(r => r.path === '/admin/coupons' && r.method === 'POST').body.partnerId, 'novo');
});

test('comissão: meses com extrato, fechar e pagar, e regra com vírgula', async () => {
  const months = [
    { month: '2026-10', closed: false, canClose: false, totalCents: -419, lines: [{ customerName: 'Ana', line: { kind: 'REFUND', source: 'SITE', paidAt: '2026-09-10T12:00:00Z', grossCents: -4193, commissionCents: -419 } }] },
    { month: '2026-09', closed: true, paidOn: null, totalCents: 419, lines: [{ customerName: 'Ana', line: { kind: 'PAYMENT', source: 'GOOGLE_PLAY', paidAt: '2026-09-10T12:00:00Z', grossCents: 4190, commissionCents: 419 } }] },
  ];
  const { app, requests } = setup({
    'GET /admin/partners/p1/commission': json(months),
    'PUT /admin/partners/p1/commission-rule': json({}, 204),
    'POST /admin/partners/p1/commission/2026-09/paid': json({}, 204),
    'GET /admin/partners': json([]),
  });
  app.setState({ partnerSel: 'p1' });
  app.carregarComissao(); await flush();
  const v = app.comissaoVals();
  assert.equal(v.comLinhas.length, 4);
  assert.equal(v.comLinhas[0].mes, 'outubro de 2026');
  assert.match(v.comLinhas[1].comissao, /^Estorno /);
  assert.equal(v.comLinhas[2].podePagar, true);
  assert.equal(v.comLinhas[3].plataforma, 'Android');
  v.comLinhas[2].onPagar();
  app.setState(s => ({ comPago: { ...s.comPago, data: '21/10/2026', nota: 'Pix 21/10' } }));
  app.salvarPago(); await flush();
  assert.deepEqual(JSON.parse(JSON.stringify(requests.at(-1).body ?? requests.find(r => r.path.endsWith('/paid')).body)), { paidOn: '2026-10-21', note: 'Pix 21/10' });

  app.setState({ comRegra: { pct: '12,5', duracao: 'MONTHS', meses: 6 } });
  app.salvarRegra(); await flush();
  assert.deepEqual(JSON.parse(JSON.stringify(requests.find(r => r.method === 'PUT').body)), { percent: 12.5, duration: 'MONTHS', months: 6 });
  app.setState({ comRegra: { pct: '12,555', duracao: 'LIFETIME', meses: 12 } });
  const antes = requests.length; app.salvarRegra();
  assert.equal(requests.length, antes);
});
