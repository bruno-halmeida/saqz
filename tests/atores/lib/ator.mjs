import { randomUUID } from 'node:crypto'
import { chamar } from './http.mjs'
import { criarConta, entrar, confirmarEmail, marcarEmailConfirmado } from './auth.mjs'
import { SENHA } from './config.mjs'

/**
 * Uma pessoa do cenário (ORG, ADM, ATL1…): conta no Auth Emulator + token + chamadas ao backend.
 * Todo método devolve a `Resposta` crua; quem decide se passou é o cenário (`c.afirmar`).
 */
export class Ator {
  constructor({ papel, email, nome, telefone, uid, token }) {
    Object.assign(this, { papel, email, nome, telefone, uid, token })
    this.id = null  // id do usuário no Saqz (vem da sessão)
  }

  /** `confirmado: true` (padrão) deixa o e-mail confirmado, como quem já abriu o link; ACE-002 usa `false`. */
  static async criar({ papel, tag, confirmado = true }) {
    const slug = papel.toLowerCase()
    const email = `qa-${slug}+${tag}@saqz.local`
    const nome = `QA ${papel} ${tag}`
    const telefone = `119${String(Math.floor(Math.random() * 1e8)).padStart(8, '0')}`
    let conta = await criarConta({ email, nome })
    if (confirmado) { await marcarEmailConfirmado(conta.uid); conta = await entrar({ email }) }
    const ator = new Ator({ papel, email, nome, telefone, uid: conta.uid, token: conta.token })
    const sessao = await ator.sessao()
    if (!sessao.ok) throw new Error(`sessão de ${papel}: ${sessao.resumo()}`)
    ator.id = sessao.corpo.user.id
    const perfil = await ator.chamar('PATCH', '/api/session/profile', { phone: telefone })
    if (!perfil.ok) throw new Error(`telefone de ${papel}: ${perfil.resumo()}`)
    return ator
  }

  chamar(metodo, caminho, corpo, headers) { return chamar(metodo, caminho, { token: this.token, corpo, headers }) }

  /** Sessão nova (PUT /api/session): reflete e-mail confirmado, planOwner e grupos. */
  sessao() { return this.chamar('PUT', '/api/session') }

  /** Confirma o e-mail pelo `oobLink` do emulador e renova o token (o claim `email_verified` vem no token novo). */
  async confirmarEmail() {
    if (!(await confirmarEmail(this.email))) throw new Error(`sem link de confirmação para ${this.email}`)
    await this.renovarToken()
  }

  async renovarToken() { this.token = (await entrar({ email: this.email, senha: SENHA })).token }

  // --- Grupos e convites ---

  criarGrupo(nome, extra = {}) {
    return this.chamar('POST', '/api/groups', {
      requestId: randomUUID(), name: nome, modality: 'COURT_VOLLEYBALL', composition: 'MIXED', timeZone: 'America/Sao_Paulo',
      defaultVenue: { name: 'Quadra QA', address: 'Rua QA, 1', court: null }, regularSlots: [], ...extra,
    })
  }
  grupo(grupoId) { return this.chamar('GET', `/api/groups/${grupoId}`) }
  ajustarGrupo(grupoId, versao, campos) { return this.chamar('PUT', `/api/groups/${grupoId}`, campos, { 'If-Match': `"${versao}"` }) }
  convite(grupoId) { return this.chamar('GET', `/api/groups/${grupoId}/invite`) }
  trocarConvite(grupoId) { return this.chamar('POST', `/api/groups/${grupoId}/invite`) }
  entrarPorConvite(codigo) { return this.chamar('POST', '/api/invites/redeem', { code: codigo }) }
  /** Prévia do convite (vale sem login: passe `anonimo: true`). */
  previa(codigo, { anonimo = false } = {}) { return chamar('POST', '/api/invites/preview', { token: anonimo ? undefined : this.token, corpo: { code: codigo } }) }
  desativarConvite(grupoId) { return this.chamar('DELETE', `/api/groups/${grupoId}/invite`) }
  recusarPedido(grupoId, usuarioId) { return this.chamar('DELETE', `/api/groups/${grupoId}/entry-requests/${usuarioId}`) }
  meuPedido(grupoId) { return this.chamar('GET', `/api/groups/${grupoId}/entry-requests/me`) }
  cancelarMeuPedido(grupoId) { return this.chamar('DELETE', `/api/groups/${grupoId}/entry-requests/me`) }
  removerConvidado(grupoId, jogoId, anfitriaoId, seq) { return this.chamar('DELETE', `/api/groups/${grupoId}/games/${jogoId}/attendance/guests/${anfitriaoId}/${seq}`) }
  membros(grupoId) { return this.chamar('GET', `/api/groups/${grupoId}/memberships`) }
  mudarPapel(grupoId, usuarioId, role) { return this.chamar('PUT', `/api/groups/${grupoId}/memberships/${usuarioId}/role`, { role }) }
  pedidos(grupoId) { return this.chamar('GET', `/api/groups/${grupoId}/entry-requests`) }
  aprovarPedido(grupoId, usuarioId) { return this.chamar('POST', `/api/groups/${grupoId}/entry-requests/${usuarioId}/approve`) }
  meuPerfil(grupoId, campos) { return this.chamar('PATCH', `/api/groups/${grupoId}/athletes/me`, { nickname: null, position: null, secondaryPosition: null, level: null, preferredSide: null, heightCm: null, ...campos }) }
  atletasComConsulta(grupoId, consulta) { return this.chamar('GET', `/api/groups/${grupoId}/athletes${consulta}`) }
  atletas(grupoId) { return this.chamar('GET', `/api/groups/${grupoId}/athletes?includeInactive=true`) }
  ajustarAtleta(grupoId, usuarioId, campos) {
    return this.chamar('PATCH', `/api/groups/${grupoId}/athletes/${usuarioId}`, { nickname: null, position: null, secondaryPosition: null, level: null, preferredSide: null, heightCm: null, monthlyFeeCents: null, monthlyDueDay: null, active: true, ...campos })
  }
  remover(grupoId, usuarioId) { return this.chamar('DELETE', `/api/groups/${grupoId}/athletes/${usuarioId}`) }
  excluirGrupo(grupoId) { return this.chamar('DELETE', `/api/groups/${grupoId}`) }
  sairDoGrupo(grupoId) { return this.chamar('DELETE', `/api/groups/${grupoId}/memberships/me`) }

  // --- Jogos e presença ---

  criarJogo(grupoId, campos) { return this.chamar('POST', `/api/groups/${grupoId}/games`, { requestId: randomUUID(), ...campos }) }
  jogo(grupoId, jogoId) { return this.chamar('GET', `/api/groups/${grupoId}/games/${jogoId}`) }
  jogos(grupoId) { return this.chamar('GET', `/api/groups/${grupoId}/games`) }
  async transicao(grupoId, jogoId, acao) {
    const atual = await this.jogo(grupoId, jogoId)
    if (!atual.ok) return atual
    return this.chamar('POST', `/api/groups/${grupoId}/games/${jogoId}/${acao}`, undefined, { 'If-Match': atual.etag })
  }
  publicarJogo(grupoId, jogoId) { return this.transicao(grupoId, jogoId, 'publish') }
  cancelarJogo(grupoId, jogoId) { return this.transicao(grupoId, jogoId, 'cancel') }
  ajustarVagas(grupoId, jogoId, capacity) {
    return this.jogo(grupoId, jogoId).then((j) => this.chamar('PUT', `/api/groups/${grupoId}/games/${jogoId}/capacity`, { requestId: randomUUID(), capacity }, { 'If-Match': j.etag }))
  }
  vou(grupoId, jogoId, requestId = randomUUID(), posicao = undefined) { return this.responder(grupoId, jogoId, 'CONFIRM', requestId, posicao) }
  naoVou(grupoId, jogoId, requestId = randomUUID()) { return this.responder(grupoId, jogoId, 'DECLINE', requestId) }
  responder(grupoId, jogoId, intent, requestId = randomUUID(), posicao = undefined) {
    return this.chamar('PUT', `/api/groups/${grupoId}/games/${jogoId}/attendance`, { requestId, intent, ...(posicao ? { position: posicao } : {}) })
  }
  /** DEB-102: "Vou" escolhendo a posição (o app antigo não manda `position`: use `vou` sem posição). */
  vouEm(grupoId, jogoId, posicao) { return this.vou(grupoId, jogoId, randomUUID(), posicao) }
  presenca(grupoId, jogoId) { return this.chamar('GET', `/api/groups/${grupoId}/games/${jogoId}/attendance`) }
  lista(grupoId, jogoId) { return this.chamar('GET', `/api/groups/${grupoId}/games/${jogoId}/attendance/roster`) }
  avisarPendentes(grupoId, jogoId) { return this.chamar('POST', `/api/groups/${grupoId}/games/${jogoId}/notify-pending`, { requestId: randomUUID() }) }
  editarJogo(grupoId, jogo, etag, campos) {
    return this.chamar('PUT', `/api/groups/${grupoId}/games/${jogo.id}`, {
      title: jogo.title, venue: { name: jogo.venue.name, address: jogo.venue.address }, localDate: jogo.localDate, localTime: jogo.localTime, zoneId: jogo.zoneId,
      startsAt: jogo.startsAt, durationMinutes: jogo.durationMinutes, capacity: jogo.capacity, confirmationDeadline: jogo.confirmationDeadline,
      useDefaultGameFee: false, gameFeeCents: jogo.gameFeeCents, ...campos,
    }, { 'If-Match': etag })
  }
  porOutro(grupoId, jogoId, membroId, intent, reason = 'Ajuste do organizador', { posicao, forcar } = {}) {
    return this.chamar('POST', `/api/groups/${grupoId}/games/${jogoId}/attendance/override`, {
      requestId: randomUUID(), memberId: membroId, intent, reason, ...(posicao ? { position: posicao } : {}), ...(forcar ? { force: true } : {}),
    })
  }
  colocarNoJogo(grupoId, jogoId, membroId, requestId = randomUUID()) {
    return this.chamar('POST', `/api/groups/${grupoId}/games/${jogoId}/attendance/promote`, { requestId, memberId: membroId, reason: 'Escolha do organizador' })
  }
  convidado(grupoId, jogoId, nome, posicao = undefined) {
    return this.chamar('POST', `/api/groups/${grupoId}/games/${jogoId}/attendance/guests`, { requestId: randomUUID(), displayName: nome, ...(posicao ? { position: posicao } : {}) })
  }
  // --- Vaga por posição (DEB-102) ---
  posicoesDoGrupo(grupoId) { return this.chamar('GET', `/api/groups/${grupoId}/position-slots`) }
  sugestaoDePosicoes(sistema, times = '') { return this.chamar('GET', `/api/position-slots/suggestion?system=${sistema}${times ? `&teams=${times}` : ''}`) }
  async configurarPosicoes(grupoId, positionSlots, gameSystem = 'FIVE_ONE') {
    const atual = await this.posicoesDoGrupo(grupoId)
    if (!atual.ok) return atual
    return this.chamar('PUT', `/api/groups/${grupoId}/position-slots`, { gameSystem, positionSlots }, { 'If-Match': atual.etag })
  }

  // --- Dinheiro ---

  cobrancas(grupoId) { return this.chamar('GET', `/api/groups/${grupoId}/charges`) }
  minhasCobrancas(grupoId) { return this.chamar('GET', `/api/groups/${grupoId}/charges/me`) }
  baixa(grupoId, cobranca, status, { note, paidMethod } = {}) {
    return this.chamar('POST', `/api/groups/${grupoId}/charges/${cobranca.id}/status`, { status, note, paidMethod }, { 'If-Match': `"${cobranca.version}"` })
  }

  cobrar(grupoId, ids, requestId = randomUUID()) { return this.chamar('POST', `/api/groups/${grupoId}/charges/notify`, { requestId, chargeIds: ids }) }
  lancamento(grupoId, campos) { return this.chamar('POST', `/api/groups/${grupoId}/expenses`, { requestId: randomUUID(), ...campos }) }
  lancamentos(grupoId) { return this.chamar('GET', `/api/groups/${grupoId}/expenses`) }
  extrato(grupoId, consulta = '') { return this.chamar('GET', `/api/groups/${grupoId}/finance/statement${consulta}`) }
  totais(grupoId) { return this.chamar('GET', `/api/groups/${grupoId}/finance/totals`) }

  resolverLink(codigo) { return this.chamar('POST', '/api/attendance-links/resolve', { code: codigo }) }

  publicar(grupoId, canal, corpo, requestId = randomUUID()) { return this.chamar('POST', `/api/groups/${grupoId}/messages?channel=${canal}`, { requestId, body: corpo }) }
  preferencias() { return this.chamar('GET', '/api/me/notification-preferences') }
  salvarPreferencias(campos) { return this.chamar('PUT', '/api/me/notification-preferences', campos) }
  denunciar(grupoId, tipo, alvoId, motivo = 'SPAM') { return this.chamar('POST', '/api/reports', { groupId: grupoId, targetType: tipo, targetId: alvoId, reason: motivo }) }
  bloquear(grupoId, usuarioId) { return this.chamar('PUT', `/api/me/blocks/${usuarioId}`, { groupId: grupoId }) }
  desbloquear(usuarioId) { return this.chamar('DELETE', `/api/me/blocks/${usuarioId}`) }

  // --- Avisos e conta ---

  avisos() { return this.chamar('GET', '/api/me/notifications') }
  /** Central de avisos filtrada pelo jogo: [{ canal, corpo, lido }]. */
  async avisosDoJogo(jogoId) {
    const r = await this.avisos()
    if (!r.ok) throw new Error(`central de avisos de ${this.papel}: ${r.resumo()}`)
    return r.corpo.items.filter((i) => i.message.gameId === jogoId).map((i) => ({ canal: i.message.channel, corpo: i.message.body, lido: !!i.read }))
  }
  /** Central de avisos filtrada pelo grupo (avisos sem jogo vinculado, como a mudança de horário). */
  async avisosDoGrupo(grupoId) {
    const r = await this.avisos()
    if (!r.ok) throw new Error(`central de avisos de ${this.papel}: ${r.resumo()}`)
    return r.corpo.items.filter((i) => i.message.groupId === grupoId).map((i) => ({ canal: i.message.channel, corpo: i.message.body, jogo: i.message.gameId }))
  }
  mensagens(grupoId, canal) { return this.chamar('GET', `/api/groups/${grupoId}/messages?channel=${canal}`) }
  excluirConta() { return this.chamar('DELETE', '/api/session', undefined, { 'X-Expected-User-Id': this.id }) }
}
