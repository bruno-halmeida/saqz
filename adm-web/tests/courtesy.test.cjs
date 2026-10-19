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

test('data de fim: vazia = sem fim; inválida ou passada é recusada', () => {
  const { app } = setup();
  assert.equal(app.cortFim(''), null);
  assert.equal(app.cortFim('31/02/2099'), false);
  assert.equal(app.cortFim('01/01/2020'), false);
  assert.equal(app.cortFim('2099-01-01'), false);
  assert.match(app.cortFim(futuro()), /^\d{4}-\d{2}-\d{2}T02:59:59\.000Z$/);
});

test('corpo da cortesia: motivo é obrigatório e sem fim não manda endsAt', () => {
  const { app } = setup();
  assert.ok(app.cortCorpo({ plan: 'TITULAR', fim: '', motivo: '  ' }).erro);
  assert.ok(app.cortCorpo({ plan: 'GRATIS', fim: '', motivo: 'x' }).erro);
  assert.equal(JSON.stringify(app.cortCorpo({ plan: 'ILIMITADO', fim: '', motivo: ' teste ' }).body), JSON.stringify({ plan: 'ILIMITADO', reason: 'teste' }));
});

test('detalhe: mostra a ativa com plano, fim e motivo', async () => {
  const { app } = setup({
    'GET /admin/users/u1/courtesy': json({ active: { plan: 'ORGANIZADOR', endsAt: null, reason: 'testador', grantedBy: 'uuid-do-admin', createdAt: '2026-10-19T12:00:00Z' }, history: [] }),
    'GET /admin/users/u1': json({ userId: 'u1', displayName: 'Ana', groups: [] }),
  });
  app.setState({ selU: 'u1' }); app.carregarUsuario('u1'); await flush();
  const v = app.cortVals({ userId: 'u1', displayName: 'Ana' });
  assert.equal(v.cortOk, true); assert.equal(v.cortTemAtiva, true);
  assert.equal(v.cortAtivaTitulo, 'Organizador · sem fim');
  assert.match(v.cortAtivaSub, /^testador · dada em /);
  assert.equal(v.cortAtivaSub.includes('uuid-do-admin'), false);
  assert.equal(v.cortSalvarLabel, 'Alterar cortesia');
});

test('sem backend novo (404): a seção avisa e as ações somem, o resto da página carrega', async () => {
  const { app } = setup({ 'GET /admin/users/u1': json({ userId: 'u1', displayName: 'Ana', groups: [] }) });
  app.setState({ selU: 'u1' }); app.carregarUsuario('u1'); await flush();
  const v = app.cortVals({ userId: 'u1' });
  assert.equal(v.cortIndisp, true); assert.equal(v.cortOk, false); assert.equal(v.cortErro, false);
  assert.equal(app.state.userDetail.displayName, 'Ana');
});

test('dar cortesia: manda plano, fim e motivo; recarrega o detalhe', async () => {
  const { app, requests } = setup({
    'GET /admin/users/u1/courtesy': json({ active: null, history: [] }),
    'GET /admin/users/u1': json({ userId: 'u1', groups: [] }),
    'POST /admin/users/u1/courtesy': json({ plan: 'TITULAR' }),
  });
  app.setState({ selU: 'u1' }); app.carregarCortesia('u1'); await flush();
  app.setState({ cf: { plan: 'TITULAR', fim: futuro(), motivo: 'teste' } });
  app.salvarCortesia('u1'); await flush();
  const post = requests.find((r) => r.method === 'POST');
  assert.equal(post.body.plan, 'TITULAR'); assert.equal(post.body.reason, 'teste'); assert.ok(post.body.endsAt);
  assert.equal(requests.filter((r) => r.path === '/admin/users/u1/courtesy' && r.method === 'GET').length, 2); // inicial + recarga do detalhe
});

test('revogar: pede confirmação e só então chama a rota', async () => {
  const { app, requests } = setup({
    'GET /admin/users/u1/courtesy': json({ active: { plan: 'TITULAR', reason: 'x', createdAt: '2026-10-19T12:00:00Z' }, history: [] }),
    'GET /admin/users/u1': json({ userId: 'u1', groups: [] }),
    'POST /admin/users/u1/courtesy/revoke': { ok: true, status: 204 },
  });
  app.setState({ selU: 'u1' }); app.carregarCortesia('u1'); await flush();
  app.cortVals({ userId: 'u1', displayName: 'Ana' }).onCortRevogar();
  assert.equal(requests.some((r) => r.path.endsWith('/revoke')), false);
  assert.equal(app.state.modal.confirm, 'Revogar cortesia');
  app.state.modal.acao(); await flush();
  assert.equal(requests.some((r) => r.path.endsWith('/revoke')), true);
});

test('lote: Ver lista usa dryRun e Confirmar manda exatamente os ids mostrados', async () => {
  const { app, requests } = setup({
    'POST /admin/courtesies/batch': (b) => b.dryRun
      ? json({ dryRun: true, results: null, candidates: [{ ownerUserId: 'a', displayName: 'Ana', email: 'a@x', groups: ['Racha'], effectivePlan: null, hasCourtesy: false }, { ownerUserId: 'b', displayName: 'Beto', email: 'b@x', groups: ['Vôlei'], effectivePlan: 'TITULAR', hasCourtesy: true }] })
      : json({ dryRun: false, candidates: null, results: [{ ownerUserId: 'a', granted: true, error: null }, { ownerUserId: 'b', granted: false, error: 'NOT_ELIGIBLE' }] }),
    'GET /admin/users': json({ items: [], total: 0 }),
  });
  app.setState({ lote: { ...app.loteVazio(), motivo: 'fase de testes' } });
  app.loteVer(); await flush();
  assert.equal(requests[0].body.dryRun, true); assert.equal('ownerIds' in requests[0].body, false);
  const v = app.cortVals(null);
  assert.equal(v.loteResumo, '2 donos de grupo'); assert.equal(v.loteRows[1].jaTem, 'Já tem cortesia');
  app.loteConfirmar(); await flush();
  const grava = requests[1].body;
  assert.equal(grava.dryRun, false); assert.equal(JSON.stringify(grava.ownerIds), '["a","b"]'); assert.equal(grava.reason, 'fase de testes');
  assert.equal(app.cortVals(null).loteResultadoResumo, '1 de 2 com cortesia.');
});

test('lote: mexer no formulário invalida a lista conferida', async () => {
  const { app } = setup({ 'POST /admin/courtesies/batch': json({ dryRun: true, results: null, candidates: [{ ownerUserId: 'a', displayName: 'Ana', groups: [] }] }) });
  app.setState({ lote: { ...app.loteVazio(), motivo: 'x' } });
  app.loteVer(); await flush();
  assert.ok(app.state.lote.lista);
  app.loteEdit('plan', 'ILIMITADO');
  assert.equal(app.state.lote.lista, null);
});

test('lote sem backend novo: mostra "ainda não disponível" e não confirma nada', async () => {
  const { app } = setup();
  app.setState({ lote: { ...app.loteVazio(), motivo: 'x' } });
  app.loteVer(); await flush();
  assert.equal(app.state.lote.erro, 'Cortesia ainda não disponível neste ambiente.');
  assert.equal(app.cortVals(null).loteConfirmaVisivel, false);
});

test('revogar com 409: avisa que não havia cortesia ativa e recarrega', async () => {
  const { app, requests } = setup({
    'GET /admin/users/u1/courtesy': json({ active: null, history: [] }),
    'GET /admin/users/u1': json({ userId: 'u1', groups: [] }),
    'POST /admin/users/u1/courtesy/revoke': json({}, 409),
  });
  app.setState({ selU: 'u1' });
  app.revogarCortesia('u1'); await flush();
  assert.equal(app.state.toast, 'Não havia cortesia ativa.');
  assert.equal(requests.some((r) => r.method === 'GET' && r.path === '/admin/users/u1'), true);
});

test('histórico: mostra data e motivo da revogação, sem uuid do admin', async () => {
  const { app } = setup({ 'GET /admin/users/u1/courtesy': json({ active: null, history: [{ id: 'c1', plan: 'TITULAR', endsAt: null, reason: 'teste', grantedBy: 'uuid-admin', createdAt: '2026-10-01T12:00:00Z', revokedAt: '2026-10-10T12:00:00Z', revokedBy: 'uuid-admin', revokedReason: 'Substituída por outra cortesia' }] }) });
  app.setState({ selU: 'u1' }); app.carregarCortesia('u1'); await flush();
  const h = app.cortVals({ userId: 'u1' }).cortHist[0];
  assert.match(h.sub, /revogada em .* \(Substituída por outra cortesia\)/);
  assert.equal(h.sub.includes('uuid-admin'), false);
});
