import { randomUUID } from 'node:crypto'
import { Ator } from './ator.mjs'
import { sql, valor } from './db.mjs'
import { esperar, dormir } from './espera.mjs'
import { local, daqui, semSegundos } from './tempo.mjs'
import { rastro } from './http.mjs'

/** Falha de conferência: leva a resposta do servidor que estava na mão. */
export class Falha extends Error {
  constructor(mensagem, resposta) { super(mensagem); this.resposta = resposta ?? rastro.ultima }
}

/** Cenário que não dá para provar por API (ex.: depende de push): sai como BLOQUEADO, com o motivo. */
export class Bloqueio extends Error {}

const ehResposta = (x) => x && typeof x === 'object' && 'status' in x && 'pedido' in x

/**
 * Contexto de um cenário. Cada cenário usa `<tag>` própria nos e-mails e nomes, então
 * roda em cima de banco sujo sem precisar de `stack.sh reset`.
 */
export class Contexto {
  constructor(regra) {
    this.regra = regra
    this.tag = `${Date.now().toString(36)}${randomUUID().slice(0, 4)}`
    this.passos = []
    this.njogos = 0  // cada jogo do grupo começa 4 h depois do anterior (o banco proíbe jogos sobrepostos)
    this.atual = null
    this.sql = sql
    this.valor = valor
    this.esperar = esperar
    this.dormir = dormir
  }

  /** Executa um passo nomeado ("ORG faz X; ATL1 faz Y; confere Z"). Falha leva o nome do passo. */
  async passo(nome, fn) {
    this.atual = nome
    const inicio = Date.now()
    try {
      const r = await fn()
      this.passos.push({ nome, ok: true, ms: Date.now() - inicio })
      return r
    } catch (e) {
      this.passos.push({ nome, ok: false, ms: Date.now() - inicio })
      if (e instanceof Falha) throw e
      throw Object.assign(new Falha(`${e.message}`, rastro.ultima), { stack: e.stack })
    }
  }

  bloqueado(motivo) { throw new Bloqueio(motivo) }

  // --- conferências ---
  afirmar(cond, mensagem, resposta) { if (!cond) throw new Falha(mensagem, ehResposta(resposta) ? resposta : undefined) }
  igual(real, esperado, mensagem, resposta) {
    this.afirmar(JSON.stringify(real) === JSON.stringify(esperado), `${mensagem}: esperado ${JSON.stringify(esperado)}, veio ${JSON.stringify(real)}`, resposta)
  }
  /** Confere o status HTTP (e opcionalmente o `code` do erro). */
  status(resposta, esperado, codigo) {
    const lista = Array.isArray(esperado) ? esperado : [esperado]
    this.afirmar(lista.includes(resposta.status), `esperado HTTP ${lista.join('/')}, veio ${resposta.status}`, resposta)
    if (codigo) this.afirmar(resposta.codigo === codigo, `esperado código ${codigo}, veio ${resposta.codigo}`, resposta)
    return resposta
  }

  // --- atores e mundo ---
  async atores(...papeis) {
    const lista = await Promise.all(papeis.map((papel) => Ator.criar({ papel, tag: this.tag })))
    return Object.fromEntries(lista.map((a) => [a.papel, a]))
  }

  /** Grupo do dono + entrada dos atletas pelo convite (como os apps fazem). Devolve { id, codigo }. */
  async grupo(dono, atletas = [], { nome, extra } = {}) {
    const g = this.status(await dono.criarGrupo(nome ?? `QA Vôlei ${this.tag}`, extra), 201)
    const convite = this.status(await dono.convite(g.corpo.id), 200)
    for (const a of atletas) this.status(await a.entrarPorConvite(convite.corpo.code), 200)
    return { id: g.corpo.id, versao: g.corpo.version, codigo: convite.corpo.code, ...g.corpo }
  }

  /**
   * Jogo publicado (ou rascunho) a `inicioMin` minutos, prazo de confirmação `prazoMin` antes do início.
   * `vagas` abaixo de 4 passa pelo ajuste de vagas da API depois de criar (o mínimo de criação é 4).
   */
  async jogo(dono, grupoId, { inicioMin = 60 * 48 + 240 * this.njogos++, prazoMin = 60 * 24, vagas = 4, valorCents = null, publicar = true, titulo = 'Jogo QA' } = {}) {
    const inicio = semSegundos(daqui(inicioMin))
    const prazo = new Date(inicio.getTime() - prazoMin * 60_000)
    const r = this.status(await dono.criarJogo(grupoId, {
      title: titulo, venue: { name: 'Quadra QA', address: 'Rua QA, 1' }, ...local(inicio), startsAt: inicio.toISOString(),
      durationMinutes: 90, capacity: Math.max(vagas, 4), confirmationDeadline: prazo.toISOString(),
      useDefaultGameFee: false, gameFeeCents: valorCents,
    }), 201)
    const jogoId = r.corpo.id
    if (publicar) this.status(await dono.publicarJogo(grupoId, jogoId), 200)
    if (vagas < 4) await this.sql(`update games set capacity=${vagas}, version=version+1 where id='${jogoId}'`)
    return { id: jogoId, inicio, prazo }
  }

  /** Ajusta a configuração do grupo (prioridade de mensalista, modo de promoção, aprovação de entrada…) sem perder o resto. */
  async configurar(dono, grupoId, campos) {
    const atual = this.status(await dono.grupo(grupoId), 200).corpo
    return this.status(await dono.ajustarGrupo(grupoId, atual.version, {
      name: atual.name, modality: atual.profile.modality, composition: atual.profile.composition, ...campos,
    }), 200)
  }

  /** Vira mensalista (ou avulso) pela API do organizador. */
  async tipo(dono, grupoId, atleta, membershipType = 'MENSALISTA') {
    const extra = membershipType === 'MENSALISTA' ? { monthlyFeeCents: 5000, monthlyDueDay: 10 } : {}
    return this.status(await dono.ajustarAtleta(grupoId, atleta.id, { membershipType, ...extra }), 200)
  }

  /** Fim do teste grátis do dono (mesma atualização de banco que o roteiro usa): os grupos dele congelam. */
  async encerrarTeste(dono) {
    await this.sql(`update organizer_trials set started_at = now() - interval '15 days', ends_at = now() - interval '1 minute' where owner_user_id='${dono.id}'`)
  }
  async reabrirTeste(dono, dias = 14) {
    await this.sql(`update organizer_trials set started_at = now() - interval '1 day', ends_at = now() + interval '${dias} days' where owner_user_id='${dono.id}'`)
  }

  /** Muda o prazo de confirmação como o roteiro 01 faz (banco). */
  async prazoPara(jogoId, instante) { await this.sql(`update games set confirmation_deadline='${instante.toISOString()}', version=version+1 where id='${jogoId}'`) }
  async inicioPara(jogoId, instante) { await this.sql(`update games set starts_at='${instante.toISOString()}', version=version+1 where id='${jogoId}'`) }

  /** Situação de presença de cada pessoa no jogo, direto do banco: { <nome>: 'CONFIRMED|1' }. */
  async presencas(jogoId) {
    const linhas = await this.sql(`select member_display_name, guest_seq, status, coalesce(waitlist_sequence::text,'') from game_attendance where game_id='${jogoId}' order by waitlist_sequence nulls first, responded_at`)
    return linhas.map(([nome, conv, status, fila]) => ({ nome, convidado: Number(conv), status, fila: fila ? Number(fila) : null }))
  }
  /** Trilha de eventos de presença de uma pessoa: ['null>WAITLISTED:SELF', 'WAITLISTED>CONFIRMED:SYSTEM']. */
  async eventos(jogoId, ator, convidado = 0) {
    const linhas = await this.sql(`select coalesce(old_status::text,'null'), new_status, source from attendance_events where game_id='${jogoId}' and member_user_id='${ator.id}' and guest_seq=${convidado} order by seq`)
    return linhas.map(([de, para, origem]) => `${de}>${para}:${origem}`)
  }
  async statusDe(jogoId, ator) {
    return this.valor(`select status from game_attendance where game_id='${jogoId}' and member_user_id='${ator.id}' and guest_seq=0`)
  }
}
