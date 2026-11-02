// Cadastro completo, contato direto e fila de pós-teste no adm (DEB-162): a lógica é a do index.html publicado.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const source = require('node:fs').readFileSync(require.resolve('../index.html'), 'utf8').match(/<script type="text\/x-dc"[^>]*>([\s\S]*?)<\/script>/)[1];
const flush = () => new Promise(setImmediate);
const json = (data, status = 200) => ({ ok: status < 400, status, json: async () => data });
const dias = (n) => new Date(Date.now() + n * 864e5).toISOString();

function setup(rotas = {}, me = { displayName: 'Bruno Almeida' }) {
  let session; const requests = [];
  const saqzAdmin = { me, onSession(fn) { session = fn; }, fetchAdmin(path, options = {}) {
    const method = options.method || 'GET';
    const body = options.body ? JSON.parse(options.body) : undefined;
    requests.push({ method, path, body });
    const r = rotas[`${method} ${path}`];
    return Promise.resolve(typeof r === 'function' ? r(body) : r || json({}, 404));
  } };
  const ctx = vm.createContext({
    DCLogic: class { props = {}; setState(p, done) { Object.assign(this.state, typeof p === 'function' ? p(this.state) : p); done?.(); } },
    window: saqzAdmin && { saqzAdmin }, setTimeout, clearTimeout, console, Date, Promise, encodeURIComponent, navigator: {},
    document: undefined,
  });
  const App = vm.runInContext(source + '\nComponent', ctx);
  const app = new App(); app.componentDidMount();
  return { app, requests, logout() { saqzAdmin.me = null; session(null); } };
}

const carla = (extra = {}) => ({
  userId: 'u1', displayName: 'Carla Menezes', email: 'carla@exemplo.com', nickname: 'Carlinha', phone: '+5511900001234', phoneVerified: true,
  phoneVisibility: 'NOBODY', city: 'São Paulo', suspendedAt: null, createdAt: dias(-16), lastSeenAt: dias(-1), emailVerified: true, hasPhoto: false, platformAdmin: false,
  onboardingCompletedAt: dias(-16), plan: null, planSource: null, planEndsAt: null, subscription: null, doNotContact: false, origin: null,
  trial: { startedAt: dias(-16), endsAt: dias(-2), noticesSent: 3 },
  apps: [{ platform: 'IOS', appVersion: '1.0.16', appBuild: '33', firstSeenAt: dias(-16), lastSeenAt: dias(-1) }],
  groups: [{ groupId: 'g1', name: 'Vôlei do Parque', role: 'OWNER', members: 18, gamesPlayed: 6, frozen: true,
    athlete: { membershipType: 'MENSALISTA', active: true, position: 'PONTA', level: 'INTERMEDIARIO', monthlyFeeExempt: false } }],
  contacts: [], ...extra,
});

async function abrirUsuario(detalhe = carla(), rotas = {}) {
  const t = setup({ 'GET /admin/users/u1': json(detalhe), 'GET /admin/users/u1/courtesy': json({ active: null, history: [] }), ...rotas });
  t.app.setState({ page: 'usuario', selU: 'u1' });
  t.app.carregarUsuario('u1'); await flush(); await flush();
  return t;
}

test('telefone aparece sem +55, vai ao WhatsApp com o 55 e o texto vai codificado', () => {
  const { app } = setup();
  assert.equal(app.telefoneBr('+5511900001234'), '(11) 90000-1234');
  assert.equal(app.telefoneBr(null), '');
  assert.equal(app.linkWhatsApp('+5511900001234'), 'https://wa.me/5511900001234');
  assert.equal(app.linkWhatsApp('+5511900001234', 'Oi, tudo bem? & só'), 'https://wa.me/5511900001234?text=Oi%2C%20tudo%20bem%3F%20%26%20s%C3%B3');
  assert.equal(app.linkEmail('a@b.com', 'Oi'), 'mailto:a@b.com?subject=Saqz&body=Oi');
  assert.equal(app.linkEmail('a@b.com', ''), 'mailto:a@b.com?subject=Saqz');
});

test('quando do teste conta dias de calendário e o e-mail da Apple é reconhecido', () => {
  const { app } = setup();
  assert.equal(app.quandoRelativo(new Date().toISOString()), 'Hoje');
  assert.equal(app.quandoRelativo(dias(1)), 'Em 1 dia');
  assert.equal(app.quandoRelativo(dias(-2)), 'Há 2 dias');
  assert.equal(app.quandoRelativo(null), '—');
  assert.equal(app.emailPrivadoApple('x7k2@privaterelay.appleid.com'), true);
  assert.equal(app.emailPrivadoApple('carla@exemplo.com'), false);
});

test('mensagem pronta usa o primeiro nome dos dois lados e o momento do teste', () => {
  const { app } = setup();
  assert.equal(app.textoContato('acabou', 'Carla Menezes', 'Vôlei do Parque'),
    'Oi, Carla! Aqui é Bruno, do Saqz. Vi que o teste do Vôlei do Parque acabou e queria entender como foi para vocês. Faltou alguma coisa ou ficou alguma dúvida?');
  assert.equal(app.textoContato('acabando', 'Carla', ''), 'Oi, Carla! Aqui é Bruno, do Saqz. O seu teste acaba em breve. Quer ajuda com alguma coisa antes disso?');
  assert.equal(app.textoContato('branco', 'Carla', 'Grupo'), '');
  assert.equal(setup({}, {}).app.textoContato('duvida', '', ''), 'Oi! Aqui é a equipe do Saqz. Vi que você teve uma dúvida e queria te ajudar. Como posso ajudar?');
  assert.equal(app.modeloContatoPadrao(dias(3), 'TRIAL'), 'acabando');
  assert.equal(app.modeloContatoPadrao(dias(-3), null), 'acabou');
  assert.equal(app.modeloContatoPadrao(dias(-3), 'SITE'), 'duvida');
  assert.equal(app.modeloContatoPadrao(null, null), 'duvida');
});

test('fila pede o teste e ignora plano, status e ordem da aba Todos; a lista comum não muda', async () => {
  const { app, requests } = setup({});
  app.setState({ uPlano: 'TITULAR', uStatus: 'active', usersSort: { col: 'name', dir: 'asc' } });
  app.carregarUsuarios(1);
  assert.equal(requests.at(-1).path, '/admin/users?plan=TITULAR&status=active&page=1&size=25&sort=name&dir=asc');
  app.setState({ uTeste: 'ended', uBusca: ' 11 9000 ' });
  app.carregarUsuarios(1);
  assert.equal(requests.at(-1).path, '/admin/users?query=11%209000&trial=ended&page=1&size=25');
});

test('abas mostram os totais e trocar de aba volta à página 1', async () => {
  const { app, requests } = setup({
    'GET /admin/users?trial=active&page=1&size=1': json({ total: 8 }),
    'GET /admin/users?trial=ended&page=1&size=1': json({ total: 5 }),
    'GET /admin/users?trial=ended&page=1&size=25': json({ items: [], total: 0 }),
  });
  app.setState({ page: 'usuarios', usersPageNumber: 3 });
  app.carregarContagensTeste(); await flush(); await flush();
  const abas = app.renderVals().uAbas;
  assert.deepEqual([...abas.map(a => a.rotulo)], ['Todos', 'Em teste · 8', 'Teste acabou sem assinar · 5']);
  assert.deepEqual([...abas.map(a => a.curto)], ['Todos', 'Em teste · 8', 'Acabou · 5']);
  assert.equal(abas[0].on, 'true');
  abas[2].onClick();
  await new Promise(r => setTimeout(r, 5));
  assert.equal(app.state.uTeste, 'ended');
  assert.equal(app.state.usersPageNumber, 1);
  assert.equal(requests.at(-1).path, '/admin/users?trial=ended&page=1&size=25');
  assert.equal(app.renderVals().uEmFila, true);
});

test('linha da fila: WhatsApp com a mensagem certa, e quem não quer contato ou não tem telefone fica sem botão', () => {
  const { app } = setup();
  const base = { displayName: 'Carla Menezes', email: 'c@e.com', userId: 'u1', lastSeenAt: dias(-1), trialEndsAt: dias(-2), planSource: null, doNotContact: false,
    phoneVerified: true, phone: '+5511900001234', lastContact: null,
    ownedGroup: { groupId: 'g1', name: 'Vôlei do Parque', deleted: false, frozen: true, members: 1, gamesPlayed: 6 } };
  const itens = [
    base,
    { ...base, userId: 'u2', doNotContact: true, lastContact: { result: 'DO_NOT_CONTACT', at: dias(-1) } },
    { ...base, userId: 'u3', phone: null, email: 'x@privaterelay.appleid.com', ownedGroup: { ...base.ownedGroup, deleted: true, frozen: false } },
    { ...base, userId: 'u4', ownedGroup: null, lastContact: { result: 'WILL_SUBSCRIBE', at: dias(-1) } },
  ];
  app.setState({ uTeste: 'ended', usersPage: { items: itens, total: 4 } });
  const [normal, bloqueado, semTelefone, semGrupo] = app.renderVals().filaRows;

  assert.equal(normal.tel, '(11) 90000-1234');
  assert.equal(normal.telNota, 'Verificado');
  assert.equal(normal.quando, 'Há 2 dias');
  assert.equal(normal.grupoNota, 'Congelado');
  assert.equal(normal.jogos, '6 jogos');
  assert.equal(normal.membros, '1 membro');
  assert.equal(normal.contato, 'Sem contato');
  assert.equal(normal.temWhats, true);
  assert.match(normal.whatsUrl, /^https:\/\/wa\.me\/5511900001234\?text=Oi%2C%20Carla/);
  assert.match(decodeURIComponent(normal.whatsUrl), /o teste do Vôlei do Parque acabou/);

  assert.equal(bloqueado.temWhats, false);
  assert.equal(bloqueado.semWhatsTxt, 'Não procurar');
  assert.match(bloqueado.contato, /^Não quer contato · /);

  assert.equal(semTelefone.temWhats, false);
  assert.equal(semTelefone.semWhatsTxt, 'Sem telefone');
  assert.equal(semTelefone.telNota, 'E-mail privado da Apple');
  assert.equal(semTelefone.grupoNota, 'Apagado');

  assert.equal(semGrupo.grupo, 'Sem grupo');
  assert.equal(semGrupo.jogos, '—');
  assert.match(semGrupo.contato, /^Vai assinar · /);
});

test('ficha do usuário traz cadastro, teste, aparelho e perfil no grupo', async () => {
  const { app } = await abrirUsuario();
  const v = app.renderVals();
  const campo = (lista, k) => lista.find(c => c.k === k)?.v;
  assert.equal(v.ud.papelResumo, 'Organiza o Vôlei do Parque');
  assert.equal(v.ud.planoTitulo, 'Teste acabou sem assinar');
  assert.equal(v.ud.planoSub, 'Vôlei do Parque está congelado.');
  assert.equal(v.udTesteChip, 'Teste acabou há 2 dias');
  assert.equal(campo(v.udCadastro, 'Apelido'), 'Carlinha');
  assert.equal(campo(v.udCadastro, 'Quem vê o telefone'), 'Ninguém');
  assert.equal(campo(v.udCadastro, 'E-mail confirmado'), 'Sim');
  assert.equal(campo(v.udCadastro, 'Id da conta'), 'u1');
  assert.equal(campo(v.udCadastro, 'Admin do painel'), undefined);
  assert.equal(campo(v.udTeste, 'Avisos do fim do teste'), '3 de 3 enviados');
  assert.equal(campo(v.udTeste, 'Origem'), 'Sem cupom');
  assert.equal(campo(v.udTeste, 'Plano hoje'), 'Grátis');
  assert.equal(v.udApps[0].sistema, 'iOS');
  assert.equal(v.udApps[0].versao, 'App 1.0.16 (33)');
  assert.equal(v.udGrupos[0].sub, '18 membros · 6 jogos · congelado');
  assert.equal(v.udGrupos[0].perfil, 'Mensalista · Ponteiro(a) · Intermediário');
  assert.equal(v.udGrupos[0].papel, 'Organiza');
});

test('dono em teste vê o fim do teste; backend antigo sem os campos novos não derruba a tela', async () => {
  const emTeste = await abrirUsuario(carla({ plan: 'ORGANIZADOR', planSource: 'TRIAL', planEndsAt: dias(3), trial: { startedAt: dias(-11), endsAt: dias(3), noticesSent: 1 } }));
  const v = emTeste.app.renderVals();
  assert.match(v.ud.planoTitulo, /^Em teste até /);
  assert.equal(v.udTesteChip, 'Teste acaba em 3 dias');
  assert.equal(v.ct.modelos.find(m => m.on === 'true').rotulo, 'Teste acabando');

  const antigo = await abrirUsuario({ userId: 'u1', displayName: 'Ana', phone: '+5511900001234', groups: [] });
  const w = antigo.app.renderVals();
  assert.equal(w.ct.semContatos, true);
  assert.equal(w.udSemApps, true);
});

test('quem assinou e cancelou não recebe o texto de teste acabado', async () => {
  const { app } = await abrirUsuario(carla({ subscription: { plan: 'TITULAR', cycle: 'MONTHLY', status: 'CANCELED', since: dias(-40) } }));
  assert.equal(app.renderVals().ct.modelos.find(m => m.on === 'true').rotulo, 'Dúvida');
});

test('sair durante um envio não trava o registro na próxima ficha', async () => {
  const { app, requests, logout } = await abrirUsuario(carla(), { 'POST /admin/users/u1/contacts': () => new Promise(() => {}) });
  app.renderVals().ct.resultados[0].onClick();
  app.renderVals().ct.onSalvar();
  await flush();
  logout();
  app.state.page = 'usuario'; app.state.selU = 'u1';
  app.carregarUsuario('u1');
  app.renderVals().ct.resultados[0].onClick();
  app.renderVals().ct.onSalvar();
  assert.equal(requests.filter(r => r.method === 'POST').length, 2);
});

test('composição: trocar o modelo refaz o texto, editar vale para os dois links e copiar avisa', async () => {
  const { app } = await abrirUsuario();
  let ct = app.renderVals().ct;
  assert.equal(ct.modelos.find(m => m.on === 'true').rotulo, 'Teste acabou');
  assert.match(ct.texto, /Vi que o teste do Vôlei do Parque acabou/);
  ct.onTexto({ target: { value: 'Oi, tudo bem?' } });
  ct = app.renderVals().ct;
  assert.equal(ct.whatsUrl, 'https://wa.me/5511900001234?text=Oi%2C%20tudo%20bem%3F');
  assert.equal(ct.emailUrl, 'mailto:carla@exemplo.com?subject=Saqz&body=Oi%2C%20tudo%20bem%3F');
  ct.modelos.find(m => m.rotulo === 'Em branco').onClick();
  ct = app.renderVals().ct;
  assert.equal(ct.texto, '');
  assert.equal(ct.whatsUrl, 'https://wa.me/5511900001234');
});

test('registrar contato manda resultado e observação, entra no topo e limpa o formulário', async () => {
  const salvo = { id: 'c2', result: 'TOO_EXPENSIVE', note: 'Achou caro', authorName: 'Bruno Almeida', createdAt: dias(0) };
  const { app, requests } = await abrirUsuario(carla({ contacts: [{ id: 'c1', result: 'NO_ANSWER', note: null, authorName: null, createdAt: dias(-3) }] }),
    { 'POST /admin/users/u1/contacts': json(salvo, 201) });
  let ct = app.renderVals().ct;
  assert.equal(ct.naoPodeSalvar, true);
  assert.equal(ct.contatos[0].texto, 'Sem observação.');
  ct.resultados.find(r => r.rotulo === 'Achou caro').onClick();
  ct = app.renderVals().ct;
  ct.onNota({ target: { value: 'Achou caro' } });
  ct = app.renderVals().ct;
  assert.equal(ct.naoPodeSalvar, false);
  ct.onSalvar();
  ct.onSalvar();
  assert.equal(requests.filter(r => r.method === 'POST').length, 1);
  await flush(); await flush(); await flush();
  assert.deepEqual({ ...requests.find(r => r.method === 'POST').body }, { result: 'TOO_EXPENSIVE', note: 'Achou caro' });
  ct = app.renderVals().ct;
  assert.equal(ct.contatos.length, 2);
  assert.equal(ct.contatos[0].resultado, 'Achou caro');
  assert.match(ct.contatos[0].quando, /Bruno Almeida$/);
  assert.equal(ct.nota, '');
  assert.equal(ct.naoPodeSalvar, true);
  assert.equal(app.state.toast, 'Contato registrado.');
  assert.equal(app.state.usersPage, null);
});

test('falha ao registrar mantém o que foi escrito e permite tentar de novo', async () => {
  const { app, requests } = await abrirUsuario(carla(), { 'POST /admin/users/u1/contacts': json({}, 500) });
  app.renderVals().ct.resultados[0].onClick();
  app.renderVals().ct.onNota({ target: { value: 'Ligar amanhã' } });
  app.renderVals().ct.onSalvar();
  await flush(); await flush(); await flush();
  const ct = app.renderVals().ct;
  assert.equal(ct.nota, 'Ligar amanhã');
  assert.equal(ct.naoPodeSalvar, false);
  assert.equal(app.state.toast, 'Não deu para registrar agora. Tente de novo.');
  ct.onSalvar();
  assert.equal(requests.filter(r => r.method === 'POST').length, 2);
});

test('"Não quer contato" bloqueia a mensagem na hora e um novo contato reabre', async () => {
  const bloqueio = { id: 'c1', result: 'DO_NOT_CONTACT', note: null, authorName: 'Bruno', createdAt: dias(0) };
  const reabre = { id: 'c2', result: 'OTHER', note: 'Ela escreveu', authorName: 'Bruno', createdAt: dias(0) };
  let resposta = bloqueio;
  const { app } = await abrirUsuario(carla(), { 'POST /admin/users/u1/contacts': () => json(resposta, 201) });
  assert.equal(app.renderVals().ct.podeFalar, true);
  app.renderVals().ct.resultados.find(r => r.rotulo === 'Não quer contato').onClick();
  app.renderVals().ct.onSalvar();
  await flush(); await flush(); await flush();
  assert.equal(app.renderVals().ct.bloqueado, true);
  assert.equal(app.renderVals().ct.podeFalar, false);
  resposta = reabre;
  app.renderVals().ct.resultados.find(r => r.rotulo === 'Outro motivo').onClick();
  app.renderVals().ct.onSalvar();
  await flush(); await flush(); await flush();
  assert.equal(app.renderVals().ct.bloqueado, false);
});

test('resposta de contato de outra pessoa não entra na ficha aberta', async () => {
  let responder;
  const { app } = await abrirUsuario(carla(), { 'POST /admin/users/u1/contacts': () => new Promise(r => { responder = r; }) });
  app.renderVals().ct.resultados[0].onClick();
  app.renderVals().ct.onSalvar();
  await flush();
  app.setState({ selU: 'u2', userDetail: carla({ userId: 'u2', displayName: 'Outra' }) });
  responder(json({ id: 'c', result: 'NO_ANSWER', authorName: 'B', createdAt: dias(0) }, 201));
  await flush(); await flush(); await flush();
  assert.equal(app.state.userDetail.contacts.length, 0);
});

const grupo = (extra = {}) => ({
  groupId: 'g1', name: 'Vôlei do Parque', timeZone: 'America/Sao_Paulo', ownerUserId: 'u1', ownerName: 'Carla Menezes', ownerPlan: null, ownerPlanSource: null, ownerPlanEndsAt: null,
  members: 2, gamesPlayed: 6, deletedAt: null, createdAt: dias(-16), lastGames: [], frozen: true, lastGameAt: '2026-10-29T22:00:00Z',
  ownerContact: { email: 'carla@exemplo.com', phone: '+5511900001234', phoneVerified: true, doNotContact: false },
  profile: { city: 'São Paulo', description: 'Vôlei misto', modality: 'COURT_VOLLEYBALL', gameSystem: 'FIVE_ONE', composition: 'MIXED', level: 'CUSTOM', customLevel: 'Forte', playStyle: 'SIX_ZERO', customPlayStyle: null,
    privacy: 'PRIVATE', entryRequiresApproval: true, profileStatus: 'COMPLETE', hasPhoto: false, defaultCapacity: 18, defaultDurationMinutes: 120, defaultConfirmationLeadMinutes: 1440, schedulePaused: false,
    mensalistaPriority: true, promotionMode: 'FIFO', autoConfirmEnabled: false, attendanceUpdatesMode: 'ON_CHANGE', defaultGameFeeCents: 2500, defaultNoGameFee: false, monthlyFeeCents: 9000, monthlyDueDay: 10,
    monthlyLateJoinPolicy: 'NEXT_MONTH', advancePaymentRequired: false, pixKey: 'carla@exemplo.com', pixLabel: 'Carla', openingBalanceCents: 32000, currency: 'BRL' },
  venue: { name: 'Quadra Parque', address: 'Rua das Árvores, 10', court: 'Quadra 2' },
  slots: [{ weekday: 4, startTime: '19:00', durationMinutes: 120, venueName: 'Quadra Parque' }, { weekday: 7, startTime: '09:30', durationMinutes: 90, venueName: null }],
  whatsApp: { name: 'Vôlei WhatsApp', enabled: true, broken: false, chatId: '120363@g.us', linkedAt: dias(-10) }, ...extra,
});
const membro = (extra = {}) => ({ userId: 'u2', displayName: 'Bruna Lima', phone: '+5521900005678', phoneVerified: false, doNotContact: false, role: 'ATHLETE', membershipType: 'MENSALISTA', active: true,
  position: 'LEVANTADOR', secondaryPosition: null, level: 'AVANCADO', preferredSide: null, heightCm: 172, monthlyFeeExempt: true, lastSeenAt: dias(-1), joinedAt: dias(-9), ...extra });

async function abrirGrupo(detalhe = grupo(), itens = [membro()], rotas = {}) {
  const t = setup({ 'GET /admin/groups/g1': json(detalhe), 'GET /admin/groups/g1/members?page=1&size=25': json({ items: itens, total: itens.length, page: 1, size: 25 }), ...rotas });
  t.app.setState({ page: 'grupo', selG: 'g1' });
  t.app.carregarGrupo('g1'); await flush(); await flush(); await flush();
  return t;
}

test('ficha do grupo traduz o cadastro com os textos do app', async () => {
  const { app } = await abrirGrupo();
  const v = app.renderVals();
  const campo = (lista, k) => lista.find(c => c.k === k)?.v;
  assert.equal(campo(v.gdSobre, 'Modalidade'), 'Vôlei de quadra');
  assert.equal(campo(v.gdSobre, 'Público'), 'Misto');
  assert.equal(campo(v.gdSobre, 'Nível'), 'Forte');
  assert.equal(campo(v.gdSobre, 'Sistema de jogo'), '6-0');
  assert.equal(campo(v.gdSobre, 'Vagas por posição'), '5-1');
  assert.equal(campo(v.gdSobre, 'Entrada'), 'Com aprovação');
  assert.equal(campo(v.gdJogosCfg, 'Horário regular'), 'Quintas, 19:00 · Domingos, 09:30');
  assert.equal(campo(v.gdJogosCfg, 'Local'), 'Quadra Parque · Quadra 2');
  assert.equal(campo(v.gdJogosCfg, 'Duração'), '2 h');
  assert.equal(campo(v.gdJogosCfg, 'Prazo de prioridade'), '24 h antes');
  assert.equal(campo(v.gdJogosCfg, 'Quem entra quando surge vaga'), 'Ordem de chegada');
  assert.equal(campo(v.gdJogosCfg, 'Lista no grupo do WhatsApp'), 'A cada confirmação');
  assert.match(campo(v.gdDinheiro, 'Valor por jogo'), /25,00/);
  assert.equal(campo(v.gdDinheiro, 'Vencimento'), 'Dia 10');
  assert.equal(campo(v.gdDinheiro, 'Quem entra no meio do mês'), 'Só no mês seguinte');
  assert.equal(campo(v.gdDinheiro, 'Pix'), 'carla@exemplo.com');
  assert.equal(campo(v.gdWhats, 'Situação'), 'Funcionando');
  assert.equal(v.gdCongelado, true);
  assert.equal(v.gd.ultimoJogo, '29/10');
  assert.equal(v.gd.descricao, 'Vôlei misto');
});

test('valor por jogo marcado como sem cobrança, grupo sem cadastro e WhatsApp quebrado', async () => {
  const semCobranca = await abrirGrupo(grupo({ profile: { ...grupo().profile, defaultNoGameFee: true, pixKey: null }, whatsApp: { ...grupo().whatsApp, broken: true } }));
  const v = semCobranca.app.renderVals();
  assert.equal(v.gdDinheiro.find(c => c.k === 'Valor por jogo').v, 'Sem cobrança');
  assert.equal(v.gdDinheiro.find(c => c.k === 'Pix').v, '—');
  assert.equal(v.gdWhats.find(c => c.k === 'Situação').v, 'Vínculo quebrado');

  const antigo = await abrirGrupo({ groupId: 'g1', name: 'Grupo', timeZone: 'America/Sao_Paulo', ownerUserId: 'u1', ownerName: 'Ana', members: 1, gamesPlayed: 0, createdAt: dias(-1), lastGames: [] });
  const w = antigo.app.renderVals();
  assert.deepEqual([...w.gdSobre], []);
  assert.equal(w.gd.semWhats, true);
  assert.equal(w.gd.temWhats, false);
});

test('organizador fala com mensagem de teste acabado; quem pediu para não ser procurado fica sem botão', async () => {
  const { app } = await abrirGrupo();
  const v = app.renderVals();
  assert.match(decodeURIComponent(v.gd.whatsUrl), /o teste do Vôlei do Parque acabou/);
  assert.equal(v.gd.organizadorSub, '(11) 90000-1234 · Sem plano');

  const bloqueado = await abrirGrupo(grupo({ ownerContact: { ...grupo().ownerContact, doNotContact: true } }));
  const w = bloqueado.app.renderVals();
  assert.equal(w.gd.temWhats, false);
  assert.equal(w.gd.semWhatsTxt, 'Não procurar');
});

test('membros vêm com perfil e telefone; a busca volta à página 1 e pede ao servidor', async () => {
  const { app, requests } = await abrirGrupo(grupo(), [membro(), membro({ userId: 'u3', phone: null, position: null, level: null, heightCm: null, monthlyFeeExempt: false, role: 'OWNER', doNotContact: false }), membro({ userId: 'u4', doNotContact: true, role: 'ADMIN' })]);
  const [bruna, semDados, bloqueado] = app.renderVals().gmRows;
  assert.equal(bruna.papel, 'Membro');
  assert.equal(bruna.tipo, 'Mensalista · isento');
  assert.equal(bruna.perfil, 'Levantador(a) · Avançado · 172 cm');
  assert.equal(bruna.tel, '(21) 90000-5678');
  assert.equal(bruna.whatsUrl, 'https://wa.me/5521900005678');
  assert.equal(semDados.papel, 'Organiza');
  assert.equal(semDados.perfil, 'Sem perfil');
  assert.equal(semDados.temWhats, false);
  assert.equal(bloqueado.papel, 'Admin');
  assert.equal(bloqueado.temWhats, false);
  assert.equal(bloqueado.semWhatsTxt, 'Não procurar');

  app.setState({ membersPageNumber: 3 });
  app.renderVals().onGmBusca({ target: { value: '(21) 9000' } });
  assert.equal(app.state.membersPageNumber, 1);
  await new Promise(r => setTimeout(r, 400));
  assert.equal(requests.at(-1).path, '/admin/groups/g1/members?query=(21)%209000&page=1&size=25');
});

test('paginação de membros usa o mesmo rodapé das outras listas e falha pede nova tentativa', async () => {
  const itens = Array.from({ length: 25 }, (_, i) => membro({ userId: `u${i}` }));
  const { app, requests } = await abrirGrupo(grupo(), itens, { 'GET /admin/groups/g1/members?page=1&size=25': json({ items: itens, total: 30, page: 1, size: 25 }) });
  assert.equal(app.paginacao('members').rodape, 'Mostrando 1–25 de 30 · página 1 de 2');
  app.paginacao('members').onProxima();
  assert.equal(requests.at(-1).path, '/admin/groups/g1/members?page=2&size=25');
  await flush(); await flush();
  assert.equal(app.paginacao('members').recarregarLabel, 'Tentar novamente');
});

test('abrir em Mensagens leva à conversa do grupo', async () => {
  const { app } = await abrirGrupo();
  app.renderVals().onGdMensagens();
  assert.equal(app.state.page, 'mensagens');
  assert.equal(app.state.mgSel, '120363@g.us');
  assert.equal(app.state.mgTipo, 'grupos');
});

test('sair limpa rascunho, contato, membros e filtros da fila', async () => {
  const { app, logout } = await abrirUsuario();
  app.renderVals().ct.onNota({ target: { value: 'Anotação sigilosa' } });
  app.setState({ uTeste: 'ended', testeCounts: { active: 1, ended: 2 }, gmBusca: 'bru', membersPage: { items: [], total: 0 } });
  logout();
  assert.equal(app.state.ct.nota, '');
  assert.equal(app.state.uTeste, 'todos');
  assert.equal(app.state.testeCounts, null);
  assert.equal(app.state.gmBusca, '');
  assert.equal(app.state.membersPage, null);
});
