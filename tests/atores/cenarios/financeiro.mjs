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
{
    id: 'FIN-005', feature: 'financeiro', critico: false,
    titulo: '"Cobrar": o lembrete ao devedor',
    async rodar(c) {
      const { ORG, ADM, ATL1, ATL2 } = await c.atores('ORG', 'ADM', 'ATL1', 'ATL2')
      const g = await c.passo('ORG cria o grupo, ADM vira admin; jogo com diária: ATL1 e ATL2 confirmam e ficam devendo', async () => {
        const grupo = await c.grupo(ORG, [ADM, ATL1, ATL2])
        c.status(await ORG.mudarPapel(grupo.id, ADM.id, 'ADMIN'), 200)
        await c.configurar(ORG, grupo.id, { mensalistaPriority: false })
        grupo.jogo = await c.jogo(ORG, grupo.id, { vagas: 4, valorCents: 2000 })
        for (const a of [ATL1, ATL2]) c.status(await a.vou(grupo.id, grupo.jogo.id), 200)
        return grupo
      })
      const cob = async (a) => (await ORG.cobrancas(g.id)).corpo.charges.find((x) => x.memberId === a.id)
      const avisosCobranca = async (a) => (await a.avisosDoGrupo(g.id)).filter((n) => n.canal === 'CHARGE')
      await c.passo('ORG toca em Cobrar na cobrança de ATL1: um aviso sai e ATL1 o recebe na central; ATL2 não', async () => {
        const r = c.status(await ORG.cobrar(g.id, [(await cob(ATL1)).id]), 200)
        c.igual([r.corpo.notificationCount, r.corpo.alreadyRemindedCount], [1, 0], 'recibo do Cobrar')
        const x = await c.esperar('aviso de cobrança de ATL1', async () => { const v = await avisosCobranca(ATL1); return v.length ? v : null }, { teto: 20_000 })
        c.igual(x.length, 1, 'avisos de cobrança de ATL1')
        c.igual((await avisosCobranca(ATL2)).length, 0, 'ATL2 não recebe')
      })
      await c.passo('cobrar de novo na mesma hora: não repete o aviso e o recibo diz que já foi cobrada', async () => {
        const r = c.status(await ORG.cobrar(g.id, [(await cob(ATL1)).id]), 200)
        c.igual([r.corpo.notificationCount, r.corpo.alreadyRemindedCount], [0, 1], 'recibo do segundo Cobrar')
        await c.dormir(1500)
        c.igual((await avisosCobranca(ATL1)).length, 1, 'avisos de cobrança de ATL1')
      })
      await c.passo('13 horas depois (banco): cobrar de novo manda outro aviso', async () => {
        await c.sql(`update group_messages set created_at = created_at - interval '13 hours' where charge_id = '${(await cob(ATL1)).id}'`)
        const r = c.status(await ADM.cobrar(g.id, [(await cob(ATL1)).id]), 200)
        c.igual(r.corpo.notificationCount, 1, 'avisos depois de 13 h')
        await c.esperar('segundo aviso de cobrança', async () => (await avisosCobranca(ATL1)).length === 2, { teto: 20_000 })
      })
      await c.passo('cobrança já recebida não pode ser cobrada; o atleta não tem o botão (403)', async () => {
        const paga = await cob(ATL2)
        c.status(await ORG.baixa(g.id, paga, 'PAID', { paidMethod: 'PIX' }), 200)
        c.status(await ORG.cobrar(g.id, [paga.id]), [409, 422])
        c.status(await ATL1.cobrar(g.id, [(await cob(ATL1)).id]), 403)
      })
      await c.passo('mais de 200 cobranças de uma vez ou lista vazia: rejeitado', async () => {
        c.status(await ORG.cobrar(g.id, []), 422)
        c.status(await ORG.cobrar(g.id, Array.from({ length: 201 }, () => randomUUID())), 422)
      })
    },
  },
  {
    id: 'FIN-006', feature: 'financeiro', critico: false,
    titulo: 'Caixa e extrato',
    async rodar(c) {
      const { ORG, ADM, ATL1, ATL2, ATL3 } = await c.atores('ORG', 'ADM', 'ATL1', 'ATL2', 'ATL3')
      const g = await c.passo('ORG cria o grupo, ADM vira admin; jogo de R$ 20: ATL1, ATL2 e ATL3 confirmam', async () => {
        const grupo = await c.grupo(ORG, [ADM, ATL1, ATL2, ATL3])
        c.status(await ORG.mudarPapel(grupo.id, ADM.id, 'ADMIN'), 200)
        await c.configurar(ORG, grupo.id, { mensalistaPriority: false })
        grupo.jogo = await c.jogo(ORG, grupo.id, { vagas: 4, valorCents: 2000 })
        for (const a of [ATL1, ATL2, ATL3]) c.status(await a.vou(grupo.id, grupo.jogo.id), 200)
        return grupo
      })
      const hoje = new Date().toISOString().slice(0, 10)
      const saldo = async () => (await ORG.extrato(g.id)).corpo.summary
      await c.passo('cobrança de R$ 20 recebida: entra no caixa (+ R$ 20); dispensada e cancelada não mudam o saldo', async () => {
        const [a, b, d] = (await ORG.cobrancas(g.id)).corpo.charges
        c.status(await ORG.baixa(g.id, a, 'PAID', { paidMethod: 'PIX' }), 200)
        c.igual((await saldo()).accumulatedBalanceCents, 2000, 'saldo depois de 1 recebida')
        c.status(await ORG.baixa(g.id, b, 'WAIVED', { note: 'Perdoado' }), 200)
        c.status(await ORG.baixa(g.id, d, 'CANCELLED', { note: 'Anulada' }), 200)
        c.igual((await saldo()).accumulatedBalanceCents, 2000, 'saldo depois de dispensar e cancelar')
      })
      const saida = await c.passo('ADM lança uma saída de R$ 100 (quadra): o saldo cai para - R$ 80', async () => {
        const r = c.status(await ADM.lancamento(g.id, { description: 'Aluguel da quadra', amountCents: 10000, expenseDate: hoje, category: 'VENUE', direction: 'OUT' }), 201)
        c.igual((await saldo()).accumulatedBalanceCents, -8000, 'saldo depois da saída')
        return r.corpo
      })
      await c.passo('entrada manual de patrocínio de R$ 50 entra no caixa', async () => {
        c.status(await ORG.lancamento(g.id, { description: 'Patrocínio da loja', amountCents: 5000, expenseDate: hoje, category: 'OTHER', customCategory: 'Patrocínio', direction: 'IN' }), 201)
        c.igual((await saldo()).accumulatedBalanceCents, -3000, 'saldo depois da entrada manual')
      })
      await c.passo('corrigir o valor da saída: o saldo usa o valor novo e o histórico guarda a edição', async () => {
        const r = c.status(await ADM.chamar('PUT', `/api/groups/${g.id}/expenses/${saida.id}`, { description: 'Aluguel da quadra', amountCents: 8000, expenseDate: hoje, category: 'VENUE', direction: 'OUT' }, { 'If-Match': `"${saida.version}"` }), 200)
        c.igual((await saldo()).accumulatedBalanceCents, -1000, 'saldo com o valor corrigido')
        c.afirmar(r.corpo.events.some((e) => e.action === 'EDITED'), 'o histórico guarda a edição', r)
      })
      await c.passo('anular a saída: sai da conta e continua visível no extrato como anulada', async () => {
        const atual = (await ORG.lancamentos(g.id)).corpo.expenses.find((x) => x.id === saida.id)
        c.status(await ORG.chamar('POST', `/api/groups/${g.id}/expenses/${saida.id}/void`, undefined, { 'If-Match': `"${atual.version}"` }), 200)
        c.igual((await saldo()).accumulatedBalanceCents, 7000, 'saldo sem a saída anulada')
        const anulado = (await ORG.lancamentos(g.id)).corpo.expenses.find((x) => x.id === saida.id)
        c.igual(anulado.status, 'VOIDED', 'continua listada, como anulada')
      })
      await c.passo('o atleta não abre o caixa nem o extrato nem os lançamentos (403)', async () => {
        c.status(await ATL1.extrato(g.id), 403)
        c.status(await ATL1.lancamentos(g.id), 403)
        c.status(await ATL1.totais(g.id), 403)
        c.status(await ATL1.lancamento(g.id, { description: 'Tentativa', amountCents: 100, expenseDate: hoje, category: 'VENUE', direction: 'OUT' }), 403)
      })
    },
  },
  {
    id: 'FIN-007', feature: 'financeiro', critico: false,
    titulo: 'O acerto do jogo',
    async rodar(c) {
      const { ORG, ATL1, ATL2, ATL3 } = await c.atores('ORG', 'ATL1', 'ATL2', 'ATL3')
      const g = await c.passo('ORG cria o grupo; dois jogos no mesmo dia; ATL1, ATL2 e ATL3 confirmam o primeiro (diária de R$ 20)', async () => {
        const grupo = await c.grupo(ORG, [ATL1, ATL2, ATL3])
        await c.configurar(ORG, grupo.id, { mensalistaPriority: false })
        grupo.a = await c.jogo(ORG, grupo.id, { vagas: 4, valorCents: 2000 })
        grupo.b = await c.jogo(ORG, grupo.id, { vagas: 4, valorCents: 2000 })
        for (const a of [ATL1, ATL2, ATL3]) c.status(await a.vou(grupo.id, grupo.a.id), 200)
        return grupo
      })
      const doJogo = async (j) => (await ORG.cobrancas(g.id)).corpo.charges.filter((x) => x.gameId === j.id)
      await c.passo('2 das 3 diárias recebidas: recebido R$ 40, falta receber R$ 20', async () => {
        const [a, b] = await doJogo(g.a)
        c.status(await ORG.baixa(g.id, a, 'PAID', { paidMethod: 'PIX' }), 200); c.status(await ORG.baixa(g.id, b, 'PAID', { paidMethod: 'CASH' }), 200)
        const linhas = await doJogo(g.a)
        const soma = (st) => linhas.filter((x) => x.status === st).reduce((t, x) => t + x.amountCents, 0)
        c.igual([soma('PAID'), soma('PENDING')], [4000, 2000], 'recebido e falta receber do jogo')
      })
      await c.passo('uma dispensada sai do "falta receber" e não entra no recebido', async () => {
        const pendente = (await doJogo(g.a)).find((x) => x.status === 'PENDING')
        c.status(await ORG.baixa(g.id, pendente, 'WAIVED', { note: 'Desistiu' }), 200)
        const linhas = await doJogo(g.a)
        const soma = (st) => linhas.filter((x) => x.status === st).reduce((t, x) => t + x.amountCents, 0)
        c.igual([soma('PAID'), soma('PENDING'), soma('WAIVED')], [4000, 0, 2000], 'recebido, falta e dispensado')
      })
      await c.passo('saída de quadra lançada no dia: as cobranças dos dois jogos não carregam custo (a conta é do grupo)', async () => {
        c.status(await ORG.lancamento(g.id, { description: 'Quadra do dia', amountCents: 15000, expenseDate: new Date().toISOString().slice(0, 10), category: 'VENUE', direction: 'OUT' }), 201)
        c.afirmar((await doJogo(g.b)).length === 0, 'o segundo jogo, sem confirmados, segue sem cobranças')
        c.afirmar((await doJogo(g.a)).every((x) => !('costCents' in x) && !('resultCents' in x)), 'cobrança não leva custo nem resultado')
      })
      await c.passo('o atleta não abre o acerto: a lista de cobranças do organizador devolve 403', async () => {
        c.status(await ATL1.cobrancas(g.id), 403)
      })
    },
  },
  {
    id: 'FIN-008', feature: 'financeiro', critico: false,
    titulo: 'O que o atleta vê',
    async rodar(c) {
      const { ORG, ATL1, ATL2 } = await c.atores('ORG', 'ATL1', 'ATL2')
      const g = await c.passo('ORG cria o grupo com chave Pix; jogos de R$ 20 e R$ 30; ATL1 confirma os dois; ATL2 confirma o primeiro', async () => {
        const grupo = await c.grupo(ORG, [ATL1, ATL2], { extra: { pixKey: 'racha@saqz.test', pixLabel: 'Tesoureiro' } })
        await c.configurar(ORG, grupo.id, { mensalistaPriority: false })
        grupo.a = await c.jogo(ORG, grupo.id, { vagas: 4, valorCents: 2000 })
        grupo.b = await c.jogo(ORG, grupo.id, { vagas: 4, valorCents: 3000 })
        for (const j of [grupo.a, grupo.b]) c.status(await ATL1.vou(grupo.id, j.id), 200)
        c.status(await ATL2.vou(grupo.id, grupo.a.id), 200)
        return grupo
      })
      await c.passo('ATL1 vê só as 2 cobranças dele, sem totais e sem as de ATL2; a chave Pix do grupo está no cadastro', async () => {
        const r = c.status(await ATL1.minhasCobrancas(g.id), 200).corpo
        c.igual(r.charges.map((x) => [x.memberId, x.status]).sort(), [[ATL1.id, 'PENDING'], [ATL1.id, 'PENDING']], 'cobranças de ATL1')
        c.afirmar(r.pendingTotalCents == null && r.paidTotalCents == null, 'atleta não recebe totais', null)
        c.igual(c.status(await ATL1.grupo(g.id), 200).corpo.profile.pixKey, 'racha@saqz.test', 'chave Pix do grupo')
      })
      await c.passo('o atleta não abre o caixa do grupo (403)', async () => {
        c.status(await ATL1.extrato(g.id), 403)
        c.status(await ATL1.totais(g.id), 403)
      })
      await c.passo('quem desistiu e ainda deve continua vendo a cobrança pendente, até o organizador dar baixa', async () => {
        c.status(await ATL2.naoVou(g.id, g.a.id), 200)
        const [d] = c.status(await ATL2.minhasCobrancas(g.id), 200).corpo.charges
        c.igual([d.status, d.withdrew], ['PENDING', true], 'cobrança do desistente')
      })
      await c.passo('convidado do ATL1: a cobrança aparece no nome do anfitrião, com o nome do convidado', async () => {
        c.status(await ATL1.convidado(g.id, g.b.id, 'Amiga Do ATL1'), 201)
        c.status(await ORG.porOutro(g.id, g.b.id, ATL2.id, 'CONFIRM', 'Chegou'), 200)
        c.status(await ATL2.naoVou(g.id, g.b.id), 200)  // abre vaga e o convidado sobe
        const mine = c.status(await ATL1.minhasCobrancas(g.id), 200).corpo.charges
        c.afirmar(mine.some((x) => x.guestDisplayName === 'Amiga Do ATL1'), `deveria haver cobrança do convidado: ${JSON.stringify(mine.map((x) => x.guestDisplayName))}`, null)
      })
      await c.passo('tudo pago: as cobranças continuam na lista como histórico, sem pendentes', async () => {
        for (const x of (await ORG.cobrancas(g.id)).corpo.charges.filter((x) => x.memberId === ATL1.id && x.status === 'PENDING')) c.status(await ORG.baixa(g.id, x, 'PAID', { paidMethod: 'PIX' }), 200)
        const mine = c.status(await ATL1.minhasCobrancas(g.id), 200).corpo.charges
        c.afirmar(mine.length >= 2 && mine.every((x) => x.status === 'PAID' || x.status === 'CANCELLED'), `sem pendentes: ${JSON.stringify(mine.map((x) => x.status))}`, null)
      })
    },
  },
]
