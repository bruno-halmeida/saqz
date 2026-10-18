// Financeiro (FIN-*): cobranças, baixa e a diária do jogo.
import { randomUUID } from 'node:crypto'

const MIN = 60_000

/** Cobranças de uma pessoa, como o organizador vê. */
const dela = (lista, ator) => lista.corpo.charges.filter((x) => x.memberId === ator.id)

export default [
  {
    id: 'FIN-001', feature: 'financeiro', critico: true,
    titulo: 'As cobranças: tipos, estados e quem vê',
    async rodar(c) {
      const { ORG, ADM, ATL1, ATL2, FORA } = await c.atores('ORG', 'ADM', 'ATL1', 'ATL2', 'FORA')
      const g = await c.passo('ORG cria o grupo (diária R$ 20 por jogo); ADM, ATL1 e ATL2 entram; ADM vira administrador', async () => {
        const grupo = await c.grupo(ORG, [ADM, ATL1, ATL2])
        c.status(await ORG.mudarPapel(grupo.id, ADM.id, 'ADMIN'), 200)
        await c.configurar(ORG, grupo.id, { mensalistaPriority: false })
        return grupo
      })
      const j = await c.passo('ORG publica o jogo com diária; ATL1 e ATL2 dizem Vou e viram duas cobranças diárias pendentes', async () => {
        const jogo = await c.jogo(ORG, g.id, { vagas: 4, valorCents: 2000 })
        c.status(await ATL1.vou(g.id, jogo.id), 200)
        c.status(await ATL2.vou(g.id, jogo.id), 200)
        return jogo
      })
      await c.passo('ORG e ADM veem todas as cobranças do grupo, com totais; ATL1 vê só a dele (sem totais); quem está fora do grupo não vê nada', async () => {
        for (const adm of [ORG, ADM]) {
          const r = c.status(await adm.cobrancas(g.id), 200)
          c.igual(r.corpo.charges.length, 2, `${adm.papel}: cobranças do grupo`)
          c.igual(r.corpo.pendingTotalCents, 4000, `${adm.papel}: total pendente`)
        }
        const minha = c.status(await ATL1.minhasCobrancas(g.id), 200)
        c.igual(minha.corpo.charges.map((x) => x.memberId), [ATL1.id], 'ATL1 vê só a própria')
        c.afirmar(minha.corpo.pendingTotalCents == null, 'atleta não recebe totais', minha)
        c.status(await ATL1.cobrancas(g.id), 403)
        c.status(await FORA.cobrancas(g.id), 404)
        c.status(await FORA.minhasCobrancas(g.id), 404)
      })
      const ch1 = dela(await ORG.cobrancas(g.id), ATL1)[0]
      await c.passo('a diária é do tipo GAME, pendente, com vencimento na data do jogo', async () => {
        c.igual([ch1.kind, ch1.status, ch1.gameId, ch1.amountCents], ['GAME', 'PENDING', j.id, 2000], 'diária de ATL1')
        const dia = await c.valor(`select local_date from games where id='${j.id}'`)
        c.igual(ch1.dueDate, dia, 'vencimento = data do jogo')
      })
      await c.passo('pendente → recebida, dispensada e cancelada são permitidos (uma cobrança para cada destino)', async () => {
        const j2 = await c.jogo(ORG, g.id, { vagas: 4, valorCents: 1000 })
        c.status(await ATL1.vou(g.id, j2.id), 200)
        c.status(await ATL2.vou(g.id, j2.id), 200)
        const ORG_ATL3 = (await ORG.cobrancas(g.id)).corpo.charges.filter((x) => x.gameId === j2.id)
        const [a, b] = ORG_ATL3
        c.status(await ORG.baixa(g.id, a, 'PAID', { paidMethod: 'PIX' }), 200)
        c.status(await ORG.baixa(g.id, b, 'WAIVED', { note: 'Perdoado' }), 200)
        const j3 = await c.jogo(ORG, g.id, { vagas: 4, valorCents: 1000 })
        c.status(await ATL1.vou(g.id, j3.id), 200)
        const [d] = (await ORG.cobrancas(g.id)).corpo.charges.filter((x) => x.gameId === j3.id)
        c.status(await ORG.baixa(g.id, d, 'CANCELLED', { note: 'Anulada' }), 200)
      })
      await c.passo('recebida e dispensada voltam a pendente (desfazer); cancelada é final e recusa qualquer destino', async () => {
        const todas = (await ORG.cobrancas(g.id)).corpo.charges
        const paga = todas.find((x) => x.status === 'PAID'); const perdoada = todas.find((x) => x.status === 'WAIVED'); const anulada = todas.find((x) => x.status === 'CANCELLED')
        const p1 = c.status(await ORG.baixa(g.id, paga, 'PENDING', { note: 'Desfeito' }), 200)
        c.igual(p1.corpo.status, 'PENDING', 'recebida → pendente')
        c.igual(c.status(await ORG.baixa(g.id, perdoada, 'PENDING', { note: 'Desfeito' }), 200).corpo.status, 'PENDING', 'dispensada → pendente')
        for (const destino of ['PENDING', 'PAID', 'WAIVED']) c.status(await ORG.baixa(g.id, anulada, destino, { paidMethod: destino === 'PAID' ? 'PIX' : undefined }), [409, 422, 400], 'INVALID_GAME_TRANSITION')
      })
      await c.passo('cada mudança de estado fica no histórico (quem, de qual para qual, nota)', async () => {
        const todas = (await ORG.cobrancas(g.id)).corpo.charges
        const comHistorico = todas.find((x) => x.events.length >= 2)
        c.afirmar(comHistorico, 'uma cobrança que mudou duas vezes deve ter 2 eventos no histórico')
        c.igual([comHistorico.events[0].oldStatus, comHistorico.events[0].newStatus], ['PENDING', comHistorico.events[0].newStatus], 'primeiro evento sai de PENDING')
        c.afirmar(comHistorico.events.slice(1).every((e) => e.actorId === ORG.id), 'quem mudou fica no evento (o primeiro evento é o nascimento da cobrança)')
      })
      await c.passo('mensalidade: ORG gera a do mês para o mensalista ATL2 e, repetindo, não duplica (uma por mês)', async () => {
        await c.tipo(ORG, g.id, ATL2)
        const mes = new Date().toISOString().slice(0, 7)
        const corpo = { month: mes, amountCents: 5000, dueDate: `${mes}-28`, memberIds: [ATL2.id] }
        const post = () => ORG.chamar('POST', `/api/groups/${g.id}/charges/monthly`, { requestId: randomUUID(), ...corpo })
        c.status(await post(), 200)
        c.status(await post(), [200, 409])
        const mens = (await ORG.cobrancas(g.id)).corpo.charges.filter((x) => x.kind === 'MONTHLY' && x.memberId === ATL2.id)
        c.igual(mens.length, 1, 'mensalidades de ATL2 no mês')
        c.igual(mens[0].month, mes, 'mês da mensalidade')
      })
    },
  },
  {
    id: 'FIN-002', feature: 'financeiro', critico: true,
    titulo: 'Dar baixa: Recebi, Dispensar e Cancelar',
    async rodar(c) {
      const { ORG, ADM, ATL1, ATL2, ATL3 } = await c.atores('ORG', 'ADM', 'ATL1', 'ATL2', 'ATL3')
      const g = await c.passo('ORG cria o grupo; ADM vira administrador; ATL1, ATL2 e ATL3 dizem Vou em jogo de R$ 20', async () => {
        const grupo = await c.grupo(ORG, [ADM, ATL1, ATL2, ATL3])
        c.status(await ORG.mudarPapel(grupo.id, ADM.id, 'ADMIN'), 200)
        await c.configurar(ORG, grupo.id, { mensalistaPriority: false })
        return grupo
      })
      await c.passo('três atletas confirmam presença no jogo de R$ 20', async () => {
        g.jogo = await c.jogo(ORG, g.id, { vagas: 6, valorCents: 2000 })
        for (const a of [ATL1, ATL2, ATL3]) c.status(await a.vou(g.id, g.jogo.id), 200)
      })
      const cob = async (ator) => dela(await ORG.cobrancas(g.id), ator)[0]
      await c.passo('ORG dá baixa em ATL1 sem dizer o jeito: recebida por Pix; em ATL2 com Dinheiro: recebida por dinheiro', async () => {
        const r1 = c.status(await ORG.baixa(g.id, await cob(ATL1), 'PAID'), 200)
        c.igual([r1.corpo.status, r1.corpo.paidMethod], ['PAID', 'PIX'], 'padrão Pix')
        const r2 = c.status(await ORG.baixa(g.id, await cob(ATL2), 'PAID', { paidMethod: 'CASH' }), 200)
        c.igual([r2.corpo.status, r2.corpo.paidMethod], ['PAID', 'CASH'], 'dinheiro')
      })
      await c.passo('nota de 1 caractere é recusada; baixa em cobrança já recebida é recusada ("desfazer primeiro")', async () => {
        c.status(await ORG.baixa(g.id, await cob(ATL3), 'WAIVED', { note: 'x' }), 400)
        c.igual((await cob(ATL3)).status, 'PENDING', 'ATL3 segue pendente')
        c.status(await ORG.baixa(g.id, await cob(ATL1), 'PAID'), [409, 422, 400], 'INVALID_GAME_TRANSITION')
      })
      await c.passo('ATL3 (atleta) tenta dar baixa na própria cobrança: 403 e nada muda', async () => {
        c.status(await ATL3.baixa(g.id, await cob(ATL3), 'PAID'), 403)
        c.igual((await cob(ATL3)).status, 'PENDING', 'ATL3 segue pendente')
      })
      await c.passo('ORG e ADM dão baixa na mesma cobrança ao mesmo tempo (mesma versão): um passa e o outro recebe conflito', async () => {
        const alvo = await cob(ATL3)
        const [a, b] = await Promise.all([ORG.baixa(g.id, alvo, 'PAID', { paidMethod: 'PIX' }), ADM.baixa(g.id, alvo, 'WAIVED', { note: 'Perdoado' })])
        const codigos = [a.status, b.status].sort()
        c.igual(codigos, [200, 409], 'um 200 e um 409')
        c.afirmar([a, b].find((r) => r.status === 409).codigo === 'VERSION_CONFLICT', 'o perdedor recebe VERSION_CONFLICT', [a, b].find((r) => r.status === 409))
      })
      await c.passo('desfazer recebida: volta a pendente e sai do caixa (total recebido cai); nota fica no histórico', async () => {
        const antes = (await ORG.cobrancas(g.id)).corpo
        const paga = antes.charges.find((x) => x.memberId === ATL1.id)
        const r = c.status(await ORG.baixa(g.id, paga, 'PENDING', { note: 'Pix não caiu' }), 200)
        c.igual(r.corpo.status, 'PENDING', 'volta a pendente')
        const depois = (await ORG.cobrancas(g.id)).corpo
        c.afirmar(depois.paidTotalCents === antes.paidTotalCents - 2000, `total recebido deve cair R$ 20 (era ${antes.paidTotalCents}, ficou ${depois.paidTotalCents})`)
        c.igual(r.corpo.events.at(-1).note, 'Pix não caiu', 'nota no histórico')
      })
      await c.passo('cobrança cancelada não volta a pendente (nem recebe baixa): recusado', async () => {
        const j2 = await c.jogo(ORG, g.id, { vagas: 4, valorCents: 1000 })
        c.status(await ATL1.vou(g.id, j2.id), 200)
        const [d] = (await ORG.cobrancas(g.id)).corpo.charges.filter((x) => x.gameId === j2.id)
        const cancelada = c.status(await ORG.baixa(g.id, d, 'CANCELLED', { note: 'Anulada' }), 200)
        c.status(await ORG.baixa(g.id, { id: d.id, version: cancelada.corpo.version }, 'PENDING', { note: 'Voltar' }), [409, 422, 400], 'INVALID_GAME_TRANSITION')
      })
    },
  },
  {
    id: 'FIN-004', feature: 'financeiro', critico: true,
    titulo: 'A diária do jogo',
    async rodar(c) {
      const { ORG, ATL1, ATL2, ATL3, ATL4 } = await c.atores('ORG', 'ATL1', 'ATL2', 'ATL3', 'ATL4')
      const g = await c.passo('ORG cria o grupo; ATL1 é mensalista; ATL2, ATL3 e ATL4 são avulsos', async () => {
        const grupo = await c.grupo(ORG, [ATL1, ATL2, ATL3, ATL4])
        await c.tipo(ORG, grupo.id, ATL1)
        return grupo
      })
      const cobrancasDe = async (jogo, ator) => dela(await ORG.cobrancas(g.id), ator).filter((x) => x.gameId === jogo.id)
      await c.passo('jogo sem valor: avulso confirma e nenhuma cobrança nasce', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 4, valorCents: null })
        await c.configurar(ORG, g.id, { mensalistaPriority: false })
        c.status(await ATL2.vou(g.id, j.id), 200)
        c.igual((await cobrancasDe(j, ATL2)).length, 0, 'cobranças sem valor de diária')
      })
      const j = await c.jogo(ORG, g.id, { vagas: 2, valorCents: 2500 })
      await c.passo('avulso ATL2 confirma em jogo com diária: nasce pendente, com vencimento na data do jogo', async () => {
        c.status(await ATL2.vou(g.id, j.id), 200)
        const [x] = await cobrancasDe(j, ATL2)
        c.igual([x.status, x.amountCents, x.dueDate], ['PENDING', 2500, await c.valor(`select local_date from games where id='${j.id}'`)], 'diária de ATL2')
      })
      await c.passo('mensalista ATL1 confirma: nenhuma cobrança (mensalista não paga diária)', async () => {
        await c.configurar(ORG, g.id, { mensalistaPriority: true })
        c.status(await ATL1.vou(g.id, j.id), 200)
        c.igual((await cobrancasDe(j, ATL1)).length, 0, 'mensalista não paga diária')
      })
      await c.passo('jogo de 2 vagas lotado: avulso ATL3 vai para a espera e não é cobrado; ATL2 desiste e ATL3 é promovido: a cobrança nasce na promoção', async () => {
        c.status(await ATL3.vou(g.id, j.id), 200)
        c.igual(await c.statusDe(j.id, ATL3), 'WAITLISTED', 'ATL3 espera')
        c.igual((await cobrancasDe(j, ATL3)).length, 0, 'quem espera não é cobrado')
        c.status(await ATL2.naoVou(g.id, j.id), 200)
        c.igual(await c.statusDe(j.id, ATL3), 'CONFIRMED', 'ATL3 subiu')
        c.igual((await cobrancasDe(j, ATL3)).length, 1, 'a cobrança nasce na promoção')
        const d2 = (await cobrancasDe(j, ATL2))[0]
        c.igual([d2.status, d2.withdrew], ['PENDING', true], 'ATL2 desistiu: segue pendente com a etiqueta "Desistiu"')
      })
      await c.passo('convidado do mensalista: o convidado sempre entra pela espera (sem cobrança); quando abre vaga ele sobe e a diária nasce no nome do anfitrião ATL1', async () => {
        await c.configurar(ORG, g.id, { mensalistaPriority: false })
        const j2 = await c.jogo(ORG, g.id, { vagas: 4, valorCents: 3000 })
        c.status(await ATL1.vou(g.id, j2.id), 200)
        c.status(await ATL3.vou(g.id, j2.id), 200)
        const conv = c.status(await ATL1.convidado(g.id, j2.id, 'Convidado do ATL1'), 201)
        c.igual(conv.corpo.status, 'WAITLISTED', 'convidado entra pela espera, mesmo com vaga')
        c.igual((await cobrancasDe(j2, ATL1)).length, 0, 'convidado na espera não é cobrado')
        c.status(await ATL3.naoVou(g.id, j2.id), 200)
        c.igual(await c.valor(`select status from game_attendance where game_id='${j2.id}' and member_user_id='${ATL1.id}' and guest_seq=${conv.corpo.guestSeq}`), 'CONFIRMED', 'o convidado sobe com a vaga que abriu')
        const [ch] = await cobrancasDe(j2, ATL1)
        c.afirmar(ch, 'o anfitrião deve ter uma diária pelo convidado')
        c.igual([ch.amountCents, ch.status, ch.guestDisplayName], [3000, 'PENDING', 'Convidado do ATL1'], 'diária do convidado')
        c.status(await ATL1.chamar('DELETE', `/api/groups/${g.id}/games/${j2.id}/attendance/guests/${ATL1.id}/${conv.corpo.guestSeq}`), 204)
        c.igual((await cobrancasDe(j2, ATL1))[0].status, 'CANCELLED', 'convidado removido, cobrança pendente cancelada')
      })
      await c.passo('o valor do jogo muda depois: as cobranças já criadas mantêm o valor antigo; as novas usam o novo', async () => {
        const j3 = await c.jogo(ORG, g.id, { vagas: 4, valorCents: 2000 })
        c.status(await ATL2.vou(g.id, j3.id), 200)
        const lido = c.status(await ORG.jogo(g.id, j3.id), 200)
        const corpo = lido.corpo
        c.status(await ORG.chamar('PUT', `/api/groups/${g.id}/games/${j3.id}`, {
          title: corpo.title, venue: { name: corpo.venue.name, address: corpo.venue.address }, localDate: corpo.localDate, localTime: corpo.localTime, zoneId: corpo.zoneId,
          startsAt: corpo.startsAt, durationMinutes: corpo.durationMinutes, capacity: corpo.capacity, confirmationDeadline: corpo.confirmationDeadline, useDefaultGameFee: false, gameFeeCents: 4000,
        }, { 'If-Match': lido.etag }), 200)
        c.status(await ATL3.vou(g.id, j3.id), 200)
        c.igual((await cobrancasDe(j3, ATL2))[0].amountCents, 2000, 'cobrança antiga')
        c.igual((await cobrancasDe(j3, ATL3))[0].amountCents, 4000, 'cobrança nova')
      })
      await c.passo('jogo cancelado: pendentes viram canceladas; a recebida continua e ganha "revisar"; o jogo fica "com financeiro a revisar"', async () => {
        const j4 = await c.jogo(ORG, g.id, { vagas: 4, valorCents: 2000 })
        c.status(await ATL2.vou(g.id, j4.id), 200)
        c.status(await ATL3.vou(g.id, j4.id), 200)
        const [pagaAlvo] = await cobrancasDe(j4, ATL2)
        c.status(await ORG.baixa(g.id, pagaAlvo, 'PAID', { paidMethod: 'PIX' }), 200)
        const cancelado = c.status(await ORG.cancelarJogo(g.id, j4.id), 200)
        c.igual(cancelado.corpo.financeReviewRequired, true, 'jogo com financeiro a revisar')
        const [paga] = await cobrancasDe(j4, ATL2); const [pendente] = await cobrancasDe(j4, ATL3)
        c.igual([paga.status, paga.reviewRequired], ['PAID', true], 'recebida fica, com "revisar"')
        c.igual(pendente.status, 'CANCELLED', 'pendente cancelada')
      })
    },
  },
]
