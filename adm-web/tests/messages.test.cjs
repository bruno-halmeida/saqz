const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const html = require('node:fs').readFileSync(require.resolve('../index.html'), 'utf8');
const source = html.match(/<script type="text\/x-dc"[^>]*>([\s\S]*?)<\/script>/)[1];
const flush = async () => { for (let i = 0; i < 6; i++) await new Promise(setImmediate); };
const json = (data, status = 200) => ({ ok: status < 400, status, json: async () => data });

const G1 = '120363000000000001@g.us', G2 = '120363000000000002@g.us', P1 = '5511900001234@s.whatsapp.net', P2 = '5521900005678@s.whatsapp.net';
const agora = () => new Date().toISOString();
const conversa = (chatId, extra = {}) => ({ chatId, name: null, phone: null, lastMessageAt: agora(), preview: 'oi', previewKind: 'TEXT', saqzGroup: null, ...extra });
const lista = (extra = {}) => ({ connected: true, phone: '551153040175', truncated: false,
  groups: [conversa(G1, { name: 'Rede Alta', saqzGroup: { id: 'g-1', name: 'Rede Alta', broken: false } }), conversa(G2, { name: 'Quadra 7', saqzGroup: { id: 'g-2', name: 'Quadra', broken: true } })],
  privates: [conversa(P1, { name: 'Marina Costa', phone: '5511900001234' }), conversa(P2, { phone: '5521900005678' })], ...extra });
const msg = (id, fromMe, text = 'oi', extra = {}) => ({ id, fromMe, at: agora(), senderName: fromMe ? null : 'Ana', senderPhone: fromMe ? null : '5511900001111', text, kind: 'TEXT', ...extra });
const pagina = (messages = [], extra = {}) => ({ messages, hasMore: false, nextOffset: null, ...extra });
const rota = (chatId, offset = 0) => `GET /admin/whatsapp/messages?chatId=${encodeURIComponent(chatId)}&offset=${offset}&limit=50`;

// rotas: resposta | (body) => resposta | Promise; sem rota = página vazia nas mensagens.
function setup(rotas = {}) {
  const requests = [];
  let sessao;
  const ctx = vm.createContext({
    DCLogic: class { props = {}; setState(p, done) { Object.assign(this.state, typeof p === 'function' ? p(this.state) : p); done?.(); } },
    window: { saqzAdmin: { me: {}, onSession(fn) { sessao = fn; }, fetchAdmin(path, options = {}) {
      const method = options.method || 'GET';
      const body = options.body ? JSON.parse(options.body) : undefined;
      requests.push({ method, path, body });
      const r = rotas[`${method} ${path}`];
      if (r === undefined && path === '/admin/whatsapp/conversations') return Promise.resolve(json(lista()));
      if (r === undefined && method === 'GET') return Promise.resolve(json(pagina()));
      const v = typeof r === 'function' ? r(body) : r;
      return v instanceof Error ? Promise.reject(v) : Promise.resolve(v);
    } } },
    setTimeout, clearTimeout, console, Date, Promise, Math, JSON, Set, encodeURIComponent,
  });
  const App = vm.runInContext(source + '\nComponent', ctx);
  const app = new App(); app.componentDidMount();
  return { app, requests, sessao: me => sessao(me), vals: () => app.mensagensVals() };
}
const entrar = async (rotas) => { const s = setup(rotas); s.sessao({}); s.app.go('mensagens'); await flush(); return s; };
const privadas = async (rotas) => { const s = await entrar(rotas); s.vals().onMgPrivadas(); await flush(); return s; };
const digitar = (s, t) => s.vals().onMgRascunho({ target: { value: t } });
const enviar = async s => { s.vals().onMgEnviar(); await flush(); };
const posts = s => s.requests.filter(r => r.method === 'POST');

test('lista separa grupos e privadas e abre a primeira conversa em Recebidas', async () => {
  const s = await entrar();
  const v = s.vals();
  assert.equal(v.mgNGrupos, 2); assert.equal(v.mgNPrivadas, 2);
  assert.equal(v.mgRows.length, 2);
  assert.equal(v.mgRows[0].nome, 'Rede Alta'); assert.equal(v.mgRows[0].saqz, true);
  assert.equal(v.mgRecOn, 'true');
  assert.equal(v.mgNumero, '(11) 5304-0175');
  assert.match(v.mgSub, /^Vinculado ao grupo Rede Alta$/);
  assert.equal(v.mgPodeResponder, false); assert.equal(v.mgSoLeitura, true);
  v.onMgVerGrupo();
  assert.equal(s.app.state.page, 'grupo'); assert.equal(s.app.state.selG, 'g-1');
});

test('privadas abrem em Todas; sem nome mostra telefone e aviso', async () => {
  const s = await privadas();
  assert.equal(s.vals().mgTodasOn, 'true');
  assert.equal(s.vals().mgPodeResponder, true);
  assert.equal(s.vals().mgRows[1].nome, '(21) 90000-5678');
  s.vals().mgRows[1].abrir(); await flush();
  assert.equal(s.vals().mgSub, 'Sem nome no WhatsApp');
  assert.equal(s.vals().mgResponderA, 'Responder a (21) 90000-5678');
});

test('iniciais ignoram emoji e símbolos', async () => {
  const { app } = await entrar();
  assert.equal(app.mgIniciais({ name: '🏐 Vôlei da Firma' }), 'VD');
  assert.equal(app.mgIniciais({ name: '🔥Rede Alta' }), 'RA');
  assert.equal(app.mgIniciais({ name: 'Quadra 7' }), 'Q7');
  assert.equal(app.mgIniciais({ name: '🏐 🔥' }), '');
  assert.equal(app.mgIniciais({ name: null, phone: '5521900005678' }), '21');
});

test('vínculo quebrado fica em alerta e busca filtra por nome ou telefone', async () => {
  const s = await entrar();
  s.vals().mgRows[1].abrir(); await flush();
  assert.equal(s.vals().mgSub, 'Vínculo quebrado com o grupo Quadra'); assert.equal(s.vals().mgSubClasse, 'warn');
  s.vals().onMgPrivadas(); await flush();
  s.vals().onMgQ({ target: { value: '90000-5678' } });
  assert.equal(s.vals().mgRows.length, 1);
  s.vals().onMgQ({ target: { value: 'marina' } });
  assert.equal(s.vals().mgRows[0].nome, 'Marina Costa');
  s.vals().onMgQ({ target: { value: 'zzz' } });
  assert.match(s.vals().mgSemResultado, /Nenhuma conversa com esse nome/);
});

test('filtro Recebidas esconde enviadas e explica quando só há enviadas', async () => {
  const s = await entrar({ [rota(G1)]: json(pagina([msg('1', true, 'lembrete')])) });
  assert.equal(s.vals().mgVazioTitulo, 'Nenhuma mensagem recebida');
  s.vals().onMgTodas();
  assert.equal(s.vals().mgVazioTitulo, '');
  assert.equal(s.vals().mgLinhas.filter(l => l.ehMsg)[0].nome, 'Saqz');
});

test('conversa vazia cita a data da última mensagem', async () => {
  const data = lista(); data.groups[0].lastMessageAt = new Date(2026, 9, 14, 12).toISOString();
  const s = await entrar({ 'GET /admin/whatsapp/conversations': json(data) });
  assert.equal(s.vals().mgVazioTitulo, 'Nenhuma mensagem guardada');
  assert.match(s.vals().mgVazioTexto, /em 14\/10, e a Uazapi só guarda até 7 dias/);
});

test('mídia vira rótulo com legenda e separador de dia em Hoje', async () => {
  const s = await entrar({ [rota(G1)]: json(pagina([msg('1', false, 'legenda', { kind: 'IMAGE' }), msg('2', false, null, { kind: 'AUDIO' })])) });
  const l = s.vals().mgLinhas;
  assert.equal(l[0].dia, 'Hoje');
  assert.equal(l[1].midia, 'Imagem'); assert.equal(l[1].texto, 'legenda');
  assert.equal(l[2].midia, 'Áudio');
  assert.equal(s.app.mgDia(new Date(2020, 9, 31, 12).toISOString()), 'Sáb., 31 de out.');
});

test('carregar anteriores usa nextOffset e põe as antigas antes', async () => {
  const s = await entrar({ [rota(G1)]: json(pagina([msg('3', false, 'nova')], { hasMore: true, nextOffset: 50 })), [rota(G1, 50)]: json(pagina([msg('1', false, 'velha')])) });
  assert.equal(s.vals().mgPodeCarregar, true);
  s.vals().onMgAnteriores(); await flush();
  assert.deepEqual(s.app.state.mgConv.itens.map(m => m.id), ['1', '3']);
  assert.equal(s.vals().mgPodeCarregar, false);
});

test('rascunho é por conversa e o envio usa a conversa do clique', async () => {
  const s = await privadas({ 'POST /admin/whatsapp/messages': json({ status: 'SENT' }) });
  digitar(s, 'para a Marina');
  s.vals().mgRows[1].abrir(); await flush();
  assert.equal(s.vals().mgRascunho, '');
  assert.equal(s.vals().mgEnvioBloqueado, true);
  digitar(s, 'para o outro');
  s.vals().mgRows[0].abrir(); await flush();
  assert.equal(s.vals().mgRascunho, 'para a Marina');
  const clique = s.vals().onMgEnviar;
  s.vals().mgRows[1].abrir();
  clique(); await flush();
  assert.equal(posts(s)[0].body.chatId, P1);
  assert.equal(posts(s)[0].body.text, 'para a Marina');
  assert.equal(s.vals().mgRascunho, 'para o outro');
});

test('sucesso limpa, passa para Todas e mostra na hora; a local some quando a Uazapi reflete', async () => {
  let refletiu = false;
  const s = await privadas({ 'POST /admin/whatsapp/messages': json({ status: 'SENT' }),
    [rota(P1)]: () => json(pagina(refletiu ? [msg('9', true, 'olá')] : [])) });
  s.vals().onMgRecebidas();
  digitar(s, '  olá  ');
  await enviar(s);
  assert.equal(s.vals().mgRascunho, ''); assert.equal(s.vals().mgTodasOn, 'true');
  assert.equal(s.vals().mgLinhas.filter(l => l.ehMsg)[0].texto, 'olá');
  refletiu = true;
  s.vals().onMgAtualizar(); await flush();
  assert.equal(s.vals().mgLinhas.filter(l => l.ehMsg).length, 1);
});

test('não confirmado mantém texto e requestId; mudar o texto gera outro', async () => {
  const s = await privadas({ 'POST /admin/whatsapp/messages': json({ error: 'SEND_UNCONFIRMED' }, 503) });
  digitar(s, 'oi');
  await enviar(s);
  assert.equal(s.vals().mgEnvioMsg, 'Não foi possível confirmar o envio. Confira a conversa antes de tentar de novo.');
  assert.equal(s.vals().mgEnviarRotulo, 'Tentar de novo');
  assert.equal(s.vals().mgRascunho, 'oi');
  await enviar(s);
  assert.equal(posts(s)[0].body.requestId, posts(s)[1].body.requestId);
  digitar(s, 'oi de novo');
  assert.equal(s.vals().mgEnviarRotulo, 'Enviar');
  await enviar(s);
  assert.notEqual(posts(s)[2].body.requestId, posts(s)[1].body.requestId);
  assert.match(posts(s)[2].body.requestId, /^[0-9a-f-]{36}$/);
});

test('queda de rede conta como não confirmado', async () => {
  const s = await privadas({ 'POST /admin/whatsapp/messages': () => new Error('rede') });
  digitar(s, 'oi'); await enviar(s);
  assert.equal(s.vals().mgEnviarRotulo, 'Tentar de novo');
});

test('falha e conversa inexistente mantêm o rascunho', async () => {
  let resposta = json({ error: 'SEND_FAILED' }, 502);
  const s = await privadas({ 'POST /admin/whatsapp/messages': () => resposta });
  digitar(s, 'oi'); await enviar(s);
  assert.equal(s.vals().mgEnvioMsg, 'O envio falhou. Nada foi enviado.');
  assert.equal(s.vals().mgRascunho, 'oi');
  resposta = json({ error: 'CHAT_NOT_FOUND' }, 404);
  await enviar(s);
  assert.equal(s.vals().mgEnvioMsg, 'Esta conversa não existe mais.');
});

test('duplo clique não envia duas vezes e bloqueia durante o envio', async () => {
  let solta;
  const s = await privadas({ 'POST /admin/whatsapp/messages': () => new Promise(r => { solta = r; }) });
  digitar(s, 'oi');
  s.vals().onMgEnviar(); s.vals().onMgEnviar();
  assert.equal(posts(s).length, 1);
  assert.equal(s.vals().mgEnvioBloqueado, true);
  solta(json({ status: 'SENT' })); await flush();
  assert.equal(s.vals().mgEnvioBloqueado, true); // rascunho vazio
});

test('resposta fora de ordem é ignorada ao trocar de conversa', async () => {
  let soltaVelha;
  const s = await privadas({ [rota(P2)]: () => new Promise(r => { soltaVelha = r; }), [rota(P1)]: json(pagina([msg('1', false, 'atual')])) });
  s.vals().mgRows[1].abrir();
  s.vals().mgRows[0].abrir(); await flush();
  soltaVelha(json(pagina([msg('2', false, 'velha')]))); await flush();
  assert.equal(s.app.state.mgConv.chatId, P1);
  assert.equal(s.vals().mgLinhas.filter(l => l.ehMsg)[0].texto, 'atual');
});

test('estados da tela: desconectado, Uazapi fora e WhatsApp desligado', async () => {
  let s = await entrar({ 'GET /admin/whatsapp/conversations': json({ connected: false, phone: null, truncated: false, groups: [], privates: [] }) });
  assert.equal(s.vals().mgDesconectado, true);
  assert.match(s.vals().mgFalhaTexto, /desconectado da Uazapi/);
  assert.match(s.vals().mgQuando, /^Última tentativa às/);
  s = await entrar({ 'GET /admin/whatsapp/conversations': json({ error: 'WHATSAPP_UNAVAILABLE' }, 503) });
  assert.equal(s.vals().mgFalhaTitulo, 'Não foi possível ler as mensagens');
  assert.match(s.vals().mgFalhaTexto, /A Uazapi não respondeu/);
  assert.equal(s.vals().mgFalhaRetry, true);
  s = await entrar({ 'GET /admin/whatsapp/conversations': json({}, 404) });
  assert.equal(s.vals().mgFalhaTitulo, 'O WhatsApp está desligado neste ambiente.');
  assert.equal(s.vals().mgFalhaRetry, false);
});

test('truncated e Atualizar recarrega lista e conversa', async () => {
  const s = await entrar({ 'GET /admin/whatsapp/conversations': json(lista({ truncated: true })) });
  assert.equal(s.vals().mgTruncado, true);
  const antes = s.requests.length;
  s.vals().onMgAtualizar(); await flush();
  assert.deepEqual(s.requests.slice(antes).map(r => r.path), ['/admin/whatsapp/conversations', rota(G1).slice(4)]);
});

test('logout limpa tudo e resposta atrasada não repovoa', async () => {
  let solta;
  const s = await privadas({ 'POST /admin/whatsapp/messages': () => new Promise(r => { solta = r; }) });
  digitar(s, 'segredo');
  s.vals().onMgEnviar();
  s.sessao(null);
  const st = s.app.state;
  assert.equal(st.mgData, null); assert.equal(st.mgConv, null);
  assert.equal(Object.keys(st.mgRascunhos).length + Object.keys(st.mgPedidos).length + Object.keys(st.mgLocais).length, 0);
  solta(json({ status: 'SENT' })); await flush();
  assert.equal(Object.keys(s.app.state.mgLocais).length, 0);
  assert.equal(Object.keys(s.app.state.mgEnvio).length, 0);
});

test('no celular a lista vem primeiro: só o toque abre a conversa, e voltar devolve a lista', async () => {
  const s = await entrar();
  assert.equal(s.vals().mgVista, 'lista');
  assert.equal(s.vals().admSemNav, 'false');
  s.vals().mgRows[1].abrir(); await flush();
  assert.equal(s.vals().mgVista, 'conversa');
  assert.equal(s.vals().admSemNav, 'true');
  assert.equal(s.vals().mgNome, 'Quadra 7');
  s.vals().onMgVoltar();
  assert.equal(s.vals().mgVista, 'lista');
  assert.equal(s.vals().admSemNav, 'false');
  assert.equal(s.vals().mgNome, 'Quadra 7');
});

test('trocar entre Grupos e Privadas não abre conversa sozinho no celular', async () => {
  const s = await entrar();
  s.vals().onMgPrivadas(); await flush();
  assert.equal(s.vals().mgVista, 'lista');
});

test('conversa aberta que some da lista devolve a lista, sem tela vazia no celular', async () => {
  const s = await entrar({ 'GET /admin/whatsapp/conversations': json(lista({ groups: [] })) });
  s.app.setState({ mgNoChat: true });
  assert.equal(s.vals().mgVista, 'lista');
  assert.equal(s.vals().admSemNav, 'false');
});

test('fora da aba Mensagens a barra do celular volta, e o logout limpa a vista', async () => {
  const s = await entrar();
  s.vals().mgRows[0].abrir(); await flush();
  assert.equal(s.vals().admSemNav, 'true');
  s.vals().onMgVerGrupo();
  assert.equal(s.vals().admSemNav, 'false');
  s.sessao(null); await flush();
  assert.equal(s.app.state.mgNoChat, false);
});
