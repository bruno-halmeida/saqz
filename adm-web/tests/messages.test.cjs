const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const html = require('node:fs').readFileSync(require.resolve('../index.html'), 'utf8');
const source = html.match(/<script type="text\/x-dc"[^>]*>([\s\S]*?)<\/script>/)[1];
const flush = async () => { for (let i = 0; i < 6; i++) await new Promise(setImmediate); };
const json = (data, status = 200) => ({ ok: status < 400, status, json: async () => data });
const arquivo = nome => ({ ok: true, status: 200, blob: async () => ({ nome }) });
const rotaMidia = (chatId, id) => `GET /admin/whatsapp/media?chatId=${encodeURIComponent(chatId)}&messageId=${encodeURIComponent(id)}`;
const adiado = () => { let ok; const p = new Promise(r => { ok = r; }); return { p, ok }; };

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
  const requests = [], revogadas = [];
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
    URL: { createObjectURL: b => `blob:${b.nome}`, revokeObjectURL: u => revogadas.push(u) },
  });
  const App = vm.runInContext(source + '\nComponent', ctx);
  const app = new App(); app.componentDidMount();
  return { app, requests, revogadas, sessao: me => sessao(me), vals: () => app.mensagensVals() };
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

const plain = x => JSON.parse(JSON.stringify(x));
const midias = s => s.requests.filter(r => r.path.startsWith('/admin/whatsapp/media')).map(r => new URLSearchParams(r.path.split('?')[1]).get('messageId'));
const hora = (h, m = 0) => { const d = new Date(); d.setHours(h, m, 0, 0); return d.toISOString(); };
const reacao = (targetId, emoji, at, extra = {}) => ({ targetId, emoji, fromMe: false, at, senderName: 'Ana', senderPhone: '5511900001111', ...extra });

test('documento continua rótulo; foto carrega sozinha com legenda, áudio e vídeo esperam o toque', async () => {
  const s = await entrar({
    [rota(G1)]: json(pagina([
      msg('F1', false, 'legenda', { kind: 'IMAGE', media: { width: 800, height: 600, sizeBytes: 2048, animated: false, waveform: [] } }),
      msg('A1', false, null, { kind: 'AUDIO', media: { seconds: 14, sizeBytes: 8800, animated: false, waveform: [0, 100] } }),
      msg('V1', false, null, { kind: 'VIDEO', media: { seconds: 32, sizeBytes: 4300000, animated: false, waveform: [] } }),
      msg('D1', false, null, { kind: 'DOCUMENT' }),
    ])),
    [rotaMidia(G1, 'F1')]: arquivo('foto1'),
  });
  const l = s.vals().mgLinhas;
  assert.equal(l[0].dia, 'Hoje');
  assert.equal(l[1].ehFoto, true); assert.equal(l[1].midiaPronta, true); assert.equal(l[1].midiaUrl, 'blob:foto1');
  assert.equal(l[1].midia, ''); assert.equal(l[1].texto, 'legenda'); assert.equal(l[1].fotoEstilo, 'aspect-ratio:1.333');
  assert.match(l[1].classe, /mg-m/); assert.equal(l[1].fotoAlt, 'Foto de Ana: legenda');
  assert.equal(l[2].ehAudio, true); assert.equal(l[2].midiaEspera, true); assert.equal(l[2].audioInfo, 'Áudio · 0:14');
  assert.equal(l[2].audioRotulo, 'Ouvir áudio de Ana, 0:14'); assert.match(l[2].ondaPath, /^M1\.5 /);
  assert.equal(l[3].ehVideo, true); assert.equal(l[3].videoInfo, 'Vídeo · 0:32 · 4,1 MB');
  assert.equal(l[4].midia, 'Documento'); assert.equal(l[4].ehFoto, false);
  assert.deepEqual(midias(s), ['F1']);
  l[2].onTocar(); await flush();
  assert.deepEqual(midias(s), ['F1', 'A1']);
  assert.equal(s.app.mgDia(new Date(2020, 9, 31, 12).toISOString()), 'Sáb., 31 de out.');
});

test('fotos carregam três por vez e o toque passa na frente da fila', async () => {
  const pendentes = {};
  const rotas = { [rota(G1)]: json(pagina(['F1', 'F2', 'F3', 'F4', 'F5'].map(id => msg(id, false, null, { kind: 'IMAGE' }))
    .concat([msg('A1', false, null, { kind: 'AUDIO', media: { seconds: 3 } })]))) };
  ['F1', 'F2', 'F3', 'F4', 'F5', 'A1'].forEach(id => { pendentes[id] = adiado(); rotas[rotaMidia(G1, id)] = () => pendentes[id].p; });
  const s = await entrar(rotas);
  assert.deepEqual(midias(s), ['F1', 'F2', 'F3']);
  assert.equal(s.vals().mgLinhas[1].midiaEspera, true);
  s.vals().mgLinhas.find(l => l.ehAudio).onTocar(); await flush();
  assert.equal(s.vals().mgLinhas.find(l => l.ehAudio).audioInfo, 'Carregando…');
  pendentes.F1.ok(arquivo('f1')); await flush();
  assert.deepEqual(midias(s), ['F1', 'F2', 'F3', 'A1']);
  pendentes.F2.ok(arquivo('f2')); await flush();
  assert.deepEqual(midias(s), ['F1', 'F2', 'F3', 'A1', 'F4']);
  pendentes.A1.ok(arquivo('a1')); await flush();
  const audio = s.vals().mgLinhas.find(l => l.ehAudio);
  assert.equal(audio.midiaPronta, true); assert.equal(audio.midiaUrl, 'blob:a1');
});

test('falha deixa tentar de novo; arquivo que sumiu, grande ou de formato estranho só avisa', async () => {
  let tentativas = 0;
  const s = await entrar({
    [rota(G1)]: json(pagina([
      msg('F1', false, null, { kind: 'IMAGE' }), msg('F2', false, null, { kind: 'IMAGE' }),
      msg('F3', false, null, { kind: 'STICKER' }), msg('F4', false, null, { kind: 'IMAGE' }),
      msg('V1', false, null, { kind: 'VIDEO', media: { seconds: 90, sizeBytes: 20 * 1024 * 1024 } }),
    ])),
    [rotaMidia(G1, 'F1')]: () => (++tentativas === 1 ? json({ error: 'WHATSAPP_UNAVAILABLE' }, 503) : arquivo('f1')),
    [rotaMidia(G1, 'F2')]: json({ error: 'MEDIA_GONE' }, 410),
    [rotaMidia(G1, 'F3')]: json({ error: 'NOT_MEDIA' }, 415),
    [rotaMidia(G1, 'F4')]: json({ error: 'MEDIA_TOO_LARGE', sizeBytes: 15 * 1024 * 1024 }, 413),
  });
  let l = s.vals().mgLinhas;
  assert.equal(l[1].aviso, 'Não foi possível carregar a foto'); assert.equal(l[1].podeTentar, true); assert.equal(l[1].ehFoto, false);
  assert.equal(l[2].aviso, 'Foto não está mais disponível no WhatsApp'); assert.equal(l[2].podeTentar, false);
  assert.equal(l[3].aviso, 'Figurinha em formato que o adm não abre. Veja no WhatsApp.');
  assert.equal(l[4].aviso, 'Foto · 15,0 MB · grande demais para abrir aqui. Veja no WhatsApp.');
  assert.equal(l[5].aviso, 'Vídeo · 20,0 MB · grande demais para abrir aqui. Veja no WhatsApp.');
  assert.equal(l[5].ehVideo, false);
  assert.ok(!midias(s).includes('V1'));
  l[1].onTentar(); await flush();
  l = s.vals().mgLinhas;
  assert.equal(l[1].aviso, ''); assert.equal(l[1].midiaUrl, 'blob:f1');
  l[2].onTentar(); await flush();
  assert.equal(midias(s).filter(id => id === 'F2').length, 1);
});

test('foto amplia com nome, grupo, hora e legenda; fecha com Esc ou Fechar', async () => {
  const s = await entrar({ [rota(G1)]: json(pagina([msg('F1', false, 'quadra nova', { kind: 'IMAGE', at: hora(21, 45) })])), [rotaMidia(G1, 'F1')]: arquivo('f1') });
  assert.equal(s.vals().mgFotoAberta, false);
  s.vals().mgLinhas[1].onFoto();
  let v = s.vals();
  assert.equal(v.mgFotoAberta, true); assert.equal(v.mgFotoUrl, 'blob:f1');
  assert.equal(v.mgFotoTitulo, 'Ana · Rede Alta'); assert.equal(v.mgFotoQuando, 'Hoje, 21:45'); assert.equal(v.mgFotoLegenda, 'quadra nova');
  v.onMgFotoTecla({ key: 'Enter' }); assert.equal(s.vals().mgFotoAberta, true);
  v.onMgFotoTecla({ key: 'Escape' }); assert.equal(s.vals().mgFotoAberta, false);
  s.vals().mgLinhas[1].onFoto(); s.vals().onMgFecharFoto(); assert.equal(s.vals().mgFotoAberta, false);
});

test('reações: vale a última de cada pessoa, retirada some, e o toque mostra quem reagiu', async () => {
  const s = await entrar({ [rota(G1)]: json(pagina([msg('M1', false, 'bora?', { at: hora(20) })], { reactions: [
    reacao('M1', '👍', hora(20, 1), { senderName: 'Tiago', senderPhone: '5511900004444' }),
    reacao('M1', '👍', hora(20, 2), { senderName: 'Marcos', senderPhone: '5511900005555' }),
    reacao('M1', '🔥', hora(20, 3), { senderName: 'Tiago', senderPhone: '5511900004444' }),
    reacao('M1', '❤️', hora(20, 4), { senderName: 'Júlia', senderPhone: '5511900003333' }),
    reacao('M1', null, hora(20, 5), { senderName: 'Júlia', senderPhone: '5511900003333' }),
    reacao('M1', '🔥', hora(20, 6), { senderName: 'Ana', senderPhone: '5511900001111' }),
  ] })) });
  let m = s.vals().mgLinhas[1];
  assert.equal(m.temReacoes, true); assert.equal(m.reacoesClasse, 'mg-reacoes');
  assert.deepEqual(plain(m.reacoes.map(r => r.rotulo)), ['👍 1', '🔥 2']);
  assert.equal(m.reacoes[1].aria, '🔥: Tiago e Ana');
  assert.equal(m.quemReagiu, '');
  m.reacoes[1].alternar();
  m = s.vals().mgLinhas[1];
  assert.equal(m.reacoes[1].aberto, 'true'); assert.equal(m.quemReagiu, '🔥 Tiago e Ana');
  m.reacoes[0].alternar();
  assert.equal(s.vals().mgLinhas[1].quemReagiu, '👍 Marcos');
  s.vals().mgLinhas[1].reacoes[0].alternar();
  assert.equal(s.vals().mgLinhas[1].quemReagiu, '');
});

test('reação a mensagem fora da tela vira linha solta; em Todas fica sob a mensagem do Saqz', async () => {
  const s = await entrar({ [rota(G1)]: json(pagina([
    msg('S1', true, 'Lista aberta', { at: hora(19) }), msg('M1', false, 'oi', { at: hora(20) }),
  ], { reactions: [
    reacao('ANTIGA', '❤️', hora(19, 30), { senderName: 'Júlia' }),
    reacao('S1', '👍', hora(20, 30)),
    reacao('S1', '👍', hora(20, 35), { senderName: 'Tiago', senderPhone: '5511900004444' }),
    reacao('M1', '😂', hora(20, 40), { fromMe: true, senderName: null, senderPhone: null }),
    reacao('S1', '🙏', hora(20, 50), { fromMe: true, senderName: null, senderPhone: null }),
  ] })) });
  let l = s.vals().mgLinhas;
  assert.deepEqual(plain(l.filter(x => x.ehSolta).map(x => x.solta)), [
    `Júlia reagiu ❤️ a uma mensagem anterior · 19:30`, `Ana e Tiago reagiram 👍 a uma mensagem do Saqz · 20:35`,
  ]);
  assert.deepEqual(plain(l.map(x => x.ehDia ? 'dia' : x.ehSolta ? 'solta' : x.texto)), ['dia', 'solta', 'oi', 'solta']);
  assert.deepEqual(plain(l.find(x => x.texto === 'oi').reacoes.map(r => r.rotulo)), ['😂 1']);
  s.vals().onMgTodas();
  l = s.vals().mgLinhas;
  assert.deepEqual(plain(l.filter(x => x.ehSolta).map(x => x.solta)), [`Júlia reagiu ❤️ a uma mensagem anterior · 19:30`]);
  const saqz = l.find(x => x.texto === 'Lista aberta');
  assert.deepEqual(plain(saqz.reacoes.map(r => r.rotulo)), ['👍 2', '🙏 1']); assert.equal(saqz.reacoesClasse, 'mg-reacoes me');
  saqz.reacoes[1].alternar();
  assert.equal(s.vals().mgLinhas.find(x => x.texto === 'Lista aberta').quemReagiu, '🙏 Saqz');
});

test('carregar anteriores junta reações sem repetir e prende a solta na mensagem antiga', async () => {
  const r1 = reacao('M1', '❤️', hora(20, 30));
  const s = await entrar({
    [rota(G1)]: json(pagina([msg('M2', false, 'novo', { at: hora(20) })], { hasMore: true, nextOffset: 50, reactions: [r1] })),
    [rota(G1, 50)]: json(pagina([msg('M1', false, 'antigo', { at: hora(19), kind: 'IMAGE' })], { reactions: [r1] })),
    [rotaMidia(G1, 'M1')]: arquivo('m1'),
  });
  assert.equal(s.vals().mgLinhas.filter(x => x.ehSolta).length, 1);
  s.vals().onMgAnteriores(); await flush();
  const l = s.vals().mgLinhas;
  assert.equal(l.filter(x => x.ehSolta).length, 0);
  const antiga = l.find(x => x.texto === 'antigo');
  assert.deepEqual(plain(antiga.reacoes.map(r => r.rotulo)), ['❤️ 1']);
  assert.equal(antiga.midiaUrl, 'blob:m1');
});

test('trocar de conversa ou sair libera os arquivos e descarta resposta atrasada', async () => {
  const atrasada = adiado();
  const s = await entrar({
    [rota(G1)]: json(pagina([msg('F1', false, null, { kind: 'IMAGE' }), msg('F2', false, null, { kind: 'IMAGE' })])),
    [rota(G2)]: json(pagina([msg('F3', false, null, { kind: 'IMAGE' })])),
    [rotaMidia(G1, 'F1')]: arquivo('f1'), [rotaMidia(G1, 'F2')]: () => atrasada.p, [rotaMidia(G2, 'F3')]: arquivo('f3'),
  });
  assert.equal(s.vals().mgLinhas[1].midiaUrl, 'blob:f1');
  s.vals().mgLinhas[1].onFoto();
  s.vals().mgRows[1].abrir(); await flush();
  assert.deepEqual(s.revogadas, ['blob:f1']);
  assert.equal(s.vals().mgFotoAberta, false);
  assert.equal(s.vals().mgLinhas[1].midiaUrl, 'blob:f3');
  atrasada.ok(arquivo('f2')); await flush();
  assert.deepEqual(s.revogadas, ['blob:f1', 'blob:f2']);
  assert.equal(s.app.state.mgMidias.F2, undefined);
  s.sessao(null); await flush();
  assert.deepEqual(s.revogadas, ['blob:f1', 'blob:f2', 'blob:f3']);
  assert.deepEqual(plain(s.app.state.mgMidias), {});
});

test('prévia de reação na lista diz Reagiu', async () => {
  const s = await entrar({ 'GET /admin/whatsapp/conversations': json(lista({ groups: [conversa(G1, { name: 'Rede Alta', preview: '❤️', previewKind: 'REACTION' })] })) });
  assert.equal(s.vals().mgRows[0].previa, 'Reagiu ❤️');
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

// Conversa com conta do Saqz (DEB-164): nome do cadastro nas privadas e "Nova conversa" pela busca de Usuários.
const C1 = '5511988887777@s.whatsapp.net';
const comConta = (extra = {}) => lista({ privates: [conversa(C1, { name: 'Ca 💙', phone: '5511988887777', account: { userId: 'u-carla', name: 'Carla Menezes' } }),
  conversa(P1, { name: 'Marina Costa', phone: '5511900001234' })], ...extra });
const conta = (extra = {}) => ({ userId: 'u-diego', displayName: 'Diego Ramos', phone: '+5521900000388', doNotContact: false, memberships: 1,
  ownedGroup: { groupId: 'g-9', name: 'Futebol de Quinta', deleted: false }, ...extra });
const busca = termo => `GET /admin/users?query=${encodeURIComponent(termo)}&page=1&size=20`;
const esperarBusca = async () => { await new Promise(r => setTimeout(r, 280)); await flush(); };

test('privada de uma conta mostra o nome do cadastro, a etiqueta e o link para a ficha', async () => {
  const s = await privadas({ 'GET /admin/whatsapp/conversations': json(comConta()) });
  const v = s.vals();
  assert.equal(v.mgRows[0].nome, 'Carla Menezes'); assert.equal(v.mgRows[0].conta, true); assert.equal(v.mgRows[0].ini, 'CM');
  assert.equal(v.mgRows[1].conta, false);
  assert.equal(v.mgNome, 'Carla Menezes');
  assert.equal(v.mgSub, '(11) 98888-7777 · Conta do Saqz');
  assert.equal(v.mgTemConta, true);
  s.vals().onMgQ({ target: { value: 'carla' } });
  assert.equal(s.vals().mgRows.length, 1);
  s.vals().onMgVerConta();
  assert.equal(s.app.state.page, 'usuario'); assert.equal(s.app.state.selU, 'u-carla');
});

test('Nova conversa busca contas e marca quem já tem conversa ou não pode receber', async () => {
  const itens = [conta(), conta({ userId: 'u-carla', displayName: 'Carla Menezes', phone: '+5511988887777' }),
    conta({ userId: 'u-renato', displayName: 'Renato Lima', doNotContact: true }), conta({ userId: 'u-sem', displayName: 'Sem Fone', phone: null, ownedGroup: null })];
  const s = await entrar({ 'GET /admin/whatsapp/conversations': json(comConta()), [busca('di')]: json({ items: itens, total: 4 }) });
  s.vals().onMgNova();
  let v = s.vals();
  assert.equal(v.mgEscolhendo, true); assert.equal(v.mgTemConversa, false); assert.equal(v.mgSemConversa, false);
  assert.equal(v.mgVista, 'conversa');
  assert.match(v.mgNovaMsg, /^Busque pelo nome/);
  s.vals().onMgNovaQ({ target: { value: 'd' } });
  assert.equal(s.vals().mgNovaMsg, 'Digite pelo menos 2 letras.');
  s.vals().onMgNovaQ({ target: { value: 'di' } });
  assert.equal(s.vals().mgNovaMsg, 'Buscando…');
  await esperarBusca();
  v = s.vals();
  assert.equal(s.requests.filter(r => r.path.startsWith('/admin/users?')).length, 1);
  assert.equal(v.mgNovaMsg, '');
  assert.deepEqual(v.mgPessoas.map(p => [p.nome, p.tag, p.bloqueado]), [
    ['Diego Ramos', '', false], ['Carla Menezes', 'Já tem conversa', false], ['Renato Lima', 'Não procurar', true], ['Sem Fone', 'Sem telefone', true]]);
  assert.equal(v.mgPessoas[0].sub, '(21) 90000-0388 · Organiza o Futebol de Quinta');
  v.mgPessoas[2].escolher();
  assert.equal(s.vals().mgEscolhendo, true);
  v.mgPessoas[1].escolher(); await flush();
  v = s.vals();
  assert.equal(v.mgEscolhendo, false);
  assert.equal(s.app.state.mgSel, C1);
  assert.equal(v.mgNome, 'Carla Menezes');
});

test('conta sem conversa abre conversa nova com saudação, e o envio vai pela conta e troca para a conversa real', async () => {
  let depois = false;
  const real = conversa('552190000388@s.whatsapp.net', { phone: '552190000388', account: { userId: 'u-diego', name: 'Diego Ramos' } });
  const s = await entrar({
    'GET /admin/whatsapp/conversations': () => json(comConta(depois ? { privates: [real, ...comConta().privates] } : {})),
    [busca('diego')]: json({ items: [conta()], total: 1 }),
    'POST /admin/whatsapp/users/u-diego/messages': () => { depois = true; return json({ status: 'SENT' }); },
  });
  s.vals().onMgNova();
  s.vals().onMgNovaQ({ target: { value: 'diego' } });
  await esperarBusca();
  s.vals().mgPessoas[0].escolher(); await flush();
  let v = s.vals();
  assert.equal(s.app.state.mgSel, 'conta:u-diego');
  assert.equal(v.mgNome, 'Diego Ramos');
  assert.equal(v.mgSub, '(21) 90000-0388 · Organiza o Futebol de Quinta');
  assert.equal(v.mgResponderA, 'Escreva para Diego Ramos');
  assert.equal(v.mgRascunho, 'Oi, Diego! Aqui é a equipe do Saqz. ');
  assert.equal(v.mgVazioTitulo, 'Nenhuma mensagem com Diego nos últimos 7 dias');
  assert.equal(s.requests.some(r => r.path.includes('conta%3A') || r.path.includes('conta:u-diego')), false);
  digitar(s, 'Oi, Diego! Aqui é a equipe do Saqz. Tudo certo com o grupo?');
  await enviar(s); await flush();
  const post = posts(s).at(-1);
  assert.equal(post.path, '/admin/whatsapp/users/u-diego/messages');
  assert.deepEqual(Object.keys(post.body).sort(), ['requestId', 'text']);
  v = s.vals();
  assert.equal(s.app.state.mgSel, '552190000388@s.whatsapp.net');
  assert.equal(s.app.state.mgContaNova, null);
  assert.equal(v.mgNome, 'Diego Ramos');
  assert.equal(v.mgResponderA, 'Responder a Diego Ramos');
  assert.equal(v.mgAnotado, true);
  assert.match(v.mgAnotadoTexto, /anotada em Contatos, na ficha de Diego/);
  assert.ok(v.mgLinhas.some(l => l.ehMsg && l.texto === 'Oi, Diego! Aqui é a equipe do Saqz. Tudo certo com o grupo?'));
  digitar(s, 'Mais uma');
  assert.equal(s.vals().mgAnotado, false);
  await enviar(s);
  assert.equal(posts(s).at(-1).path, '/admin/whatsapp/messages');
});

test('conversa nova continua aberta se a Uazapi ainda não mostrar a conversa, e recusas dizem que nada saiu', async () => {
  const s = await entrar({ 'GET /admin/whatsapp/conversations': json(comConta()), [busca('diego')]: json({ items: [conta()], total: 1 }),
    'POST /admin/whatsapp/users/u-diego/messages': json({ error: 'NOT_ON_WHATSAPP' }, 422) });
  s.vals().onMgNova();
  s.vals().onMgNovaQ({ target: { value: 'diego' } });
  await esperarBusca();
  s.vals().mgPessoas[0].escolher(); await flush();
  await enviar(s);
  assert.equal(s.vals().mgEnvioMsg, 'Este número não tem WhatsApp. Nada foi enviado.');
  s.vals().onMgAtualizar(); await flush();
  assert.equal(s.app.state.mgSel, 'conta:u-diego');
  assert.equal(s.vals().mgNome, 'Diego Ramos');
});

test('fechar Nova conversa volta à conversa e descarta busca atrasada', async () => {
  let responder;
  const s = await entrar({ 'GET /admin/whatsapp/conversations': json(comConta()), [busca('di')]: () => new Promise(r => { responder = r; }) });
  s.vals().onMgNova();
  s.vals().onMgNovaQ({ target: { value: 'di' } });
  await esperarBusca();
  s.vals().onMgFecharNova();
  responder(json({ items: [conta()], total: 1 })); await flush();
  const v = s.vals();
  assert.equal(v.mgEscolhendo, false);
  assert.equal(v.mgVista, 'lista');
  assert.equal(s.app.state.mgNovaRes.carregando, true);
  s.vals().onMgNova();
  assert.equal(s.vals().mgEscolhendo, true);
  s.vals().mgRows[1].abrir(); await flush();
  assert.equal(s.vals().mgEscolhendo, false);
  assert.equal(s.vals().mgNome, 'Quadra 7');
});
