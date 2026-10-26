const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const html = require('node:fs').readFileSync(require.resolve('../index.html'), 'utf8');
const source = html.match(/<script type="text\/x-dc"[^>]*>([\s\S]*?)<\/script>/)[1];
const flush = () => new Promise(setImmediate);
const json = (data, status = 200) => ({ ok: status < 400, status, json: async () => data });

// rotas: { 'METHOD path': resposta | (body) => resposta }; sem rota = 404.
function setup(rotas = {}) {
  const requests = [];
  const ctx = vm.createContext({
    DCLogic: class { props = {}; setState(p, done) { Object.assign(this.state, typeof p === 'function' ? p(this.state) : p); done?.(); } },
    window: { saqzAdmin: { me: {}, onSession() {}, fetchAdmin(path, options = {}) {
      const method = options.method || 'GET';
      const body = options.body ? JSON.parse(options.body) : undefined;
      requests.push({ method, path, body });
      const r = rotas[`${method} ${path}`];
      return Promise.resolve(typeof r === 'function' ? r(body) : r || json({}, 404));
    } } },
    setTimeout, clearTimeout, console, Date, Promise, Math, JSON,
  });
  const App = vm.runInContext(source + '\nComponent', ctx);
  const app = new App(); app.componentDidMount();
  return { app, requests };
}

const regra = (extra = {}) => ({ android: true, ios: true, alwaysOff: { users: [], groups: [] }, alwaysOn: { users: [], groups: [] }, state: 'OFF',
  audience: { platforms: [], minVersion: null, maxVersion: null, plans: [], subscriptionStatuses: [], roles: [] }, percentage: 100, values: {}, variants: [], alertDisabled: false, ...extra });
const chat = (extra = {}) => ({ key: 'chat', description: 'Chat do grupo', backendEnforced: true, parameters: [], version: 3, rule: regra(extra), fullRolloutSince: null, alert: null, experimentRunning: false });
const rotasBase = (extra = {}) => ({
  'GET /admin/features': json({ flags: [chat({ state: 'ALL' }), { ...chat(), key: 'whatsapp', alert: 'RED', rule: regra({ state: 'ALL' }) }] }),
  'GET /admin/feature-groups': json({ groups: [{ name: 'revisores', version: 1, members: [{ userId: 'u-rev', displayName: 'Marina', email: 'revisao.organizadora@saqz.app' }] }] }),
  'GET /admin/app-versions': json({ version: 1, android: { minimum: null, recommended: null }, ios: { minimum: null, recommended: null } }),
  'GET /admin/features/chat': json(chat()),
  'GET /admin/features/history?type=FLAG&key=chat&page=1&size=50': json({ page: 1, size: 50, total: 1, items: [{ id: 'h1', version: 1, actorUserId: null, actorName: null, reason: 'Valor inicial', createdAt: '2026-10-26T12:00:00Z', before: null, after: regra() }] }),
  ...extra,
});

test('corpo da flag: motivo obrigatório e ninguém nas duas listas', () => {
  const { app } = setup();
  const flag = chat();
  assert.match(app.flgCorpo(flag, { rule: regra(), reason: ' x ' }).erro, /motivo/);
  assert.match(app.flgCorpo(flag, { rule: regra({ alwaysOn: { users: ['u1'], groups: [] }, alwaysOff: { users: ['u1'], groups: [] } }), reason: 'teste' }).erro, /sempre ligada/);
  const ok = app.flgCorpo(flag, { rule: regra({ state: 'ALL' }), reason: '  Liga para todos ' });
  assert.equal(ok.body.expectedVersion, 3);
  assert.equal(ok.body.reason, 'Liga para todos');
  assert.equal(ok.body.rule.state, 'ALL');
  assert.match(ok.body.requestId, /^[0-9a-f-]{36}$/);
});

test('lista: alerta com texto e cor, resumo com interruptores e estado', async () => {
  const { app } = setup(rotasBase());
  app.go('flags'); await flush(); await flush();
  const v = app.flagsVals();
  assert.equal(v.isFlags, true);
  assert.equal(v.flgRows.length, 2);
  const whats = v.flgRows.find(r => r.key === 'whatsapp');
  assert.equal(whats.alertTxt, '90+ dias em 100%');
  assert.match(whats.alertStyle, /B42318/);
  assert.equal(v.flgRows.find(r => r.key === 'chat').alertStyle, 'display:none');
  assert.equal(whats.resumo, 'Android ligado · iPhone ligado · Para todos');
  assert.equal(v.flgGroupRows[0].txt, '1 pessoa');
});

test('editar: segmentado do estado, contador da porcentagem e salvar com versão esperada', async () => {
  const { app, requests } = setup(rotasBase({ 'PUT /admin/features/chat': (body) => json({ ...chat(body.rule), version: 4 }) }));
  app.go('flags'); await flush();
  app.flgOpen('chat'); await flush(); await flush();
  let v = app.flagsVals();
  assert.equal(v.flgEstados.map(o => o.label).join('|'), 'Desligada|Para o público|Para todos');
  v.flgEstados[1].pick();
  v = app.flagsVals();
  assert.equal(v.flgPublico, true);
  v.onFlgPctMenos(); v = app.flagsVals(); v.onFlgPctMenos(); v = app.flagsVals();
  assert.equal(v.flgPct, '90%');
  v.flgSituacoes[2].pick(); v = app.flagsVals();
  v.onFlgPapel({ target: { value: 'OWNER' } }); v = app.flagsVals();
  v.onFlgIos(); v = app.flagsVals();
  assert.equal(v.flgIos, 'false');
  await app.flgSalvar();
  assert.match(app.state.flgMsg, /motivo/);
  app.flagsVals().onFlgReason({ target: { value: 'Donos atrasados no Android' } });
  await app.flgSalvar(); await flush();
  const put = requests.find(r => r.method === 'PUT');
  assert.equal(put.path, '/admin/features/chat');
  assert.equal(put.body.expectedVersion, 3);
  assert.equal(put.body.rule.state, 'AUDIENCE');
  assert.equal(put.body.rule.percentage, 90);
  assert.equal(put.body.rule.ios, false);
  assert.deepEqual(put.body.rule.audience.subscriptionStatuses, ['PAST_DUE']);
  assert.deepEqual(put.body.rule.audience.roles, ['OWNER']);
  assert.equal(app.state.flgFlag.version, 4);
  assert.match(app.state.flgMsg, /Salvo/);
});

test('conflito: avisa para recarregar e não deixa salvar por cima', async () => {
  const { app } = setup(rotasBase({ 'PUT /admin/features/chat': json({ error: 'CONFLICT', message: 'A flag mudou; recarregue' }, 409) }));
  app.flgOpen('chat'); await flush(); await flush();
  app.flagsVals().onFlgReason({ target: { value: 'Motivo bom' } });
  await app.flgSalvar();
  assert.match(app.state.flgMsg, /Recarregue/);
  assert.equal(app.flagsVals().flgSaveOff, true);
});

test('voltar para esta versão: pede motivo e manda o id da linha do histórico', async () => {
  const { app, requests } = setup(rotasBase({ 'POST /admin/features/chat/revert': json({ ...chat(), version: 4 }) }));
  app.flgOpen('chat'); await flush(); await flush();
  const linha = app.flagsVals().flgHistRows[0];
  assert.match(linha.titulo, /Sistema · versão 1$/);
  await app.flgVoltar({ id: 'h1' });
  assert.match(app.state.flgMsg, /motivo/);
  app.flagsVals().onFlgReason({ target: { value: 'Volta ao inicial' } });
  await app.flagsVals().flgHistRows[0].onVoltar(); await flush();
  const post = requests.find(r => r.method === 'POST');
  assert.equal(post.path, '/admin/features/chat/revert');
  assert.deepEqual([post.body.historyId, post.body.expectedVersion, post.body.reason], ['h1', 3, 'Volta ao inicial']);
});

test('grupos: cria com versão 0, adiciona a pessoa achada por telefone e salva com motivo', async () => {
  const { app, requests } = setup(rotasBase({
    'GET /admin/feature-groups/users?q=11900000008': json({ users: [{ userId: 'u-tst', displayName: 'Testadora', phone: '+5511900000008' }] }),
    'PUT /admin/feature-groups/testadores': (body) => json({ name: 'testadores', version: 1, members: body.members.map(userId => ({ userId })) }),
  }));
  app.go('flags'); await flush(); await flush();
  app.flagsVals().onFlgGroupNew({ target: { value: 'Testadores' } });
  app.flgGrupoCriar();
  assert.equal(app.flagsVals().flgGroupTitulo, 'Grupo testadores · novo');
  app.flagsVals().onFlgSearch({ target: { value: '11900000008' } });
  app.flgBuscar(); await flush(); await flush();
  const achada = app.flagsVals().flgFound[0];
  assert.equal(achada.txt, 'Testadora · +5511900000008');
  achada.onGroup();
  assert.equal(app.flagsVals().flgGroupMembers[0].txt, 'Testadora · +5511900000008');
  await app.flgGrupoSalvar();
  assert.match(app.state.flgGroupMsg, /motivo/);
  app.flagsVals().onFlgGroupReason({ target: { value: 'Grupo de QA' } });
  await app.flgGrupoSalvar(); await flush();
  const put = requests.find(r => r.method === 'PUT');
  assert.deepEqual([put.path, put.body.expectedVersion, put.body.members], ['/admin/feature-groups/testadores', 0, ['u-tst']]);
});

test('versões: formato N.N.N, vazio vira null, motivo obrigatório', async () => {
  const { app, requests } = setup(rotasBase({ 'PUT /admin/app-versions': (b) => json({ version: 2, android: b.android, ios: b.ios }) }));
  app.go('flags'); await flush(); await flush();
  let v = app.flagsVals();
  v.onFlgAm({ target: { value: '1.0' } }); v = app.flagsVals();
  v.onFlgVReason({ target: { value: 'Sobe a mínima' } });
  await app.flgVersoesSalvar();
  assert.match(app.state.flgVersionsMsg, /formato/);
  app.flagsVals().onFlgAm({ target: { value: '1.0.13' } });
  await app.flgVersoesSalvar(); await flush();
  const put = requests.find(r => r.method === 'PUT');
  assert.deepEqual(put.body.android, { minimum: '1.0.13', recommended: null });
  assert.deepEqual(put.body.ios, { minimum: null, recommended: null });
  assert.equal(put.body.expectedVersion, 1);
  assert.equal(app.flagsVals().flgVersoesMsgCarga, 'Versão 2');
});

test('tela: segmentados e contador do layout de formulários, sem chips', () => {
  const tela = html.slice(html.indexOf('<sc-if value="{{ isFlags }}">'), html.indexOf('<sc-if value="{{ isRecebimentos }}">'));
  assert.ok(tela.includes('aria-label="Estado"') && tela.includes('class="cc-seg"'));
  assert.ok(tela.includes('class="cc-count"'));
  assert.equal(/chip/i.test(tela), false);
  assert.ok(tela.includes('Motivo (obrigatório)'));
  assert.ok(tela.includes('Voltar para esta versão'));
});
