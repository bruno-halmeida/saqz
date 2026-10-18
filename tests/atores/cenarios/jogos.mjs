// Jogos (JOGO-*) e os avisos que saem deles (NOT-003). Os avisos se conferem na central (GET /api/me/notifications): não há push no stack local.
import { local, daqui, semSegundos } from '../lib/tempo.mjs'

const MIN = 60_000
const canais = (avisos) => avisos.map((a) => a.canal)

export default [
  {
    id: 'JOGO-004', feature: 'jogos', critico: true,
    titulo: 'Publicar',
    async rodar(c) {
      const { ORG, ADM, ATL1, ATL2 } = await c.atores('ORG', 'ADM', 'ATL1', 'ATL2')
      const g = await c.passo('ORG cria o grupo; ADM e ATL1 entram; ADM vira administrador; ATL2 entra', async () => {
        const grupo = await c.grupo(ORG, [ADM, ATL1, ATL2])
        c.status(await ORG.mudarPapel(grupo.id, ADM.id, 'ADMIN'), 200)
        return grupo
      })
      const rascunho = await c.passo('ORG cria o jogo como rascunho: ninguém é avisado e ATL1 não consegue dizer Vou', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 4, publicar: false })
        c.igual(canais(await ATL1.avisosDoJogo(j.id)), [], 'rascunho não avisa')
        c.status(await ATL1.vou(g.id, j.id), [403, 404, 409, 422], undefined)
        return j
      })
      await c.passo('ATL1 (atleta) tenta publicar o rascunho: 403/404 (o atleta nem enxerga rascunho) e segue rascunho', async () => {
        c.status(await ATL1.publicarJogo(g.id, rascunho.id), [403, 404])  // rascunho é invisível para atleta
        c.igual(await c.valor(`select status from games where id='${rascunho.id}'`), 'DRAFT', 'status')
      })
      await c.passo('ADM publica o rascunho (jogo futuro e próximo do grupo): publicado, presença aberta, ATL1 e ATL2 recebem "O jogo está liberado"', async () => {
        const r = c.status(await ADM.publicarJogo(g.id, rascunho.id), 200)
        c.igual(r.corpo.status, 'PUBLISHED', 'status')
        c.status(await ATL1.vou(g.id, rascunho.id), 200)
        for (const a of [ATL1, ATL2]) {
          const avisos = await c.esperar(`aviso "jogo liberado" de ${a.papel}`, async () => { const x = await a.avisosDoJogo(rascunho.id); return x.length ? x : null }, { teto: 20_000 })
          c.igual(canais(avisos), ['GAME_OPEN'], `${a.papel}: canal`)
          c.afirmar(avisos[0].corpo.includes('O jogo está liberado. Confirme sua presença.'), `texto do aviso: ${avisos[0].corpo}`)
        }
      })
      await c.passo('publicar de novo um jogo já publicado: recusado (INVALID_GAME_TRANSITION)', async () => {
        c.status(await ORG.publicarJogo(g.id, rascunho.id), [409, 422], 'INVALID_GAME_TRANSITION')
      })
      await c.passo('rascunho com início no passado: ORG tenta publicar e é recusado', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 4, publicar: false, inicioMin: 60 * 100 })
        const passado = new Date(Date.now() - 30 * MIN)
        await c.sql(`update games set starts_at='${passado.toISOString()}', confirmation_deadline='${new Date(passado.getTime() - 60 * MIN).toISOString()}', version=version+1 where id='${j.id}'`)
        const r = await ORG.publicarJogo(g.id, j.id)
        c.afirmar(r.status >= 400 && r.status < 500, `publicar jogo com início passado deve ser recusado (veio ${r.status})`, r)
        c.igual(await c.valor(`select status from games where id='${j.id}'`), 'DRAFT', 'segue rascunho')
      })
      await c.passo('publicar um jogo que NÃO é o próximo do grupo: publica sem aviso agora', async () => {
        const mais = await c.jogo(ORG, g.id, { vagas: 4, publicar: false, inicioMin: 60 * 200 })
        c.status(await ORG.publicarJogo(g.id, mais.id), 200)
        await c.dormir(2500)
        c.igual(canais(await ATL2.avisosDoJogo(mais.id)), [], 'jogo distante não avisa na publicação')
        c.igual(await c.valor(`select count(*) from group_messages where game_id='${mais.id}' and channel='GAME_OPEN'`), '0', 'mensagens GAME_OPEN do jogo distante')
      })
    },
  },
  {
    id: 'JOGO-005', feature: 'jogos', critico: true,
    titulo: 'Cancelar',
    async rodar(c) {
      const { ORG, ADM, ATL1, ATL2, ATL3, ATL4 } = await c.atores('ORG', 'ADM', 'ATL1', 'ATL2', 'ATL3', 'ATL4')
      const g = await c.passo('ORG cria o grupo com ADM (admin) e ATL1 a ATL4; desliga a prioridade', async () => {
        const grupo = await c.grupo(ORG, [ADM, ATL1, ATL2, ATL3, ATL4])
        c.status(await ORG.mudarPapel(grupo.id, ADM.id, 'ADMIN'), 200)
        await c.configurar(ORG, grupo.id, { mensalistaPriority: false })
        return grupo
      })
      const j = await c.passo('jogo de 2 vagas com diária: ATL1 e ATL2 confirmam, ATL3 espera, ATL4 diz Não vou; ORG dá baixa na diária de ATL1', async () => {
        const jogo = await c.jogo(ORG, g.id, { vagas: 2, valorCents: 2000 })
        for (const a of [ATL1, ATL2, ATL3]) c.status(await a.vou(g.id, jogo.id), 200)
        c.status(await ATL4.naoVou(g.id, jogo.id), 200)
        const [d1] = (await ORG.cobrancas(g.id)).corpo.charges.filter((x) => x.memberId === ATL1.id)
        c.status(await ORG.baixa(g.id, d1, 'PAID', { paidMethod: 'PIX' }), 200)
        return jogo
      })
      await c.passo('ATL1 (atleta) tenta cancelar o jogo: 403 e o jogo segue publicado', async () => {
        c.status(await ATL1.cancelarJogo(g.id, j.id), 403)
        c.igual(await c.valor(`select status from games where id='${j.id}'`), 'PUBLISHED', 'status')
      })
      await c.passo('ADM cancela o jogo publicado antes do início: cancelado, com financeiro a revisar', async () => {
        const r = c.status(await ADM.cancelarJogo(g.id, j.id), 200)
        c.igual([r.corpo.status, r.corpo.financeReviewRequired], ['CANCELLED', true], 'jogo cancelado')
      })
      await c.passo('presença congelada: ATL4 tenta Vou e ATL2 tenta Não vou, ambos recusados (ATTENDANCE_FROZEN)', async () => {
        c.status(await ATL4.vou(g.id, j.id), [403, 409, 422], 'ATTENDANCE_FROZEN')
        c.status(await ATL2.naoVou(g.id, j.id), [403, 409, 422], 'ATTENDANCE_FROZEN')
        c.igual(await c.statusDe(j.id, ATL2), 'CONFIRMED', 'ATL2 segue como estava')
      })
      await c.passo('cobranças: a pendente (ATL2) vira cancelada; a recebida (ATL1) continua com "revisar"', async () => {
        const todas = (await ORG.cobrancas(g.id)).corpo.charges.filter((x) => x.gameId === j.id)
        const de = (a) => todas.find((x) => x.memberId === a.id)
        c.igual(de(ATL2).status, 'CANCELLED', 'pendente cancelada')
        c.igual([de(ATL1).status, de(ATL1).reviewRequired], ['PAID', true], 'recebida revisar')
      })
      await c.passo('confirmados (ATL1, ATL2) e espera (ATL3) recebem o aviso de cancelamento na central; ATL4 (Não vou) não recebe', async () => {
        const recebeu = async (a) => esperarAviso(c, a, j.id)
        for (const a of [ATL1, ATL2, ATL3]) {
          const x = await recebeu(a)
          c.afirmar(x.some((n) => /cancelad/i.test(n.corpo)), `${a.papel} deve receber o aviso de cancelamento; recebeu ${JSON.stringify(x)}`)
        }
        c.afirmar(!(await ATL4.avisosDoJogo(j.id)).some((n) => /cancelad/i.test(n.corpo)), 'ATL4 disse Não vou e não deve receber o cancelamento')
      })
      await c.passo('cancelar de novo o jogo cancelado: recusado (INVALID_GAME_TRANSITION)', async () => {
        c.status(await ORG.cancelarJogo(g.id, j.id), [409, 422], 'INVALID_GAME_TRANSITION')
      })
      await c.passo('rascunho cancelado: cancela, sem aviso para ninguém', async () => {
        const rasc = await c.jogo(ORG, g.id, { vagas: 4, publicar: false })
        c.igual(c.status(await ORG.cancelarJogo(g.id, rasc.id), 200).corpo.status, 'CANCELLED', 'rascunho cancelado')
        await c.dormir(2000)
        c.igual(canais(await ATL1.avisosDoJogo(rasc.id)), [], 'rascunho cancelado não avisa')
      })
      await c.passo('jogo já concluído não cancela: recusado (INVALID_GAME_TRANSITION)', async () => {
        const feito = await c.jogo(ORG, g.id, { vagas: 4 })
        await c.sql(`update games set status='COMPLETED', version=version+1 where id='${feito.id}'`)
        c.status(await ORG.cancelarJogo(g.id, feito.id), [409, 422], 'INVALID_GAME_TRANSITION')
      })
      await c.passo('depois do início também dá para cancelar (chuva): jogo publicado que já começou é cancelado', async () => {
        const chuva = await c.jogo(ORG, g.id, { vagas: 4 })
        await c.inicioPara(chuva.id, new Date(Date.now() - 10 * MIN))
        c.igual(c.status(await ORG.cancelarJogo(g.id, chuva.id), 200).corpo.status, 'CANCELLED', 'cancelado depois do início')
      })
    },
  },
]

async function esperarAviso(c, ator, jogoId) {
  return c.esperar(`avisos do jogo para ${ator.papel}`, async () => {
    const x = (await ator.avisosDoJogo(jogoId)).filter((n) => /cancelad/i.test(n.corpo))
    return x.length ? await ator.avisosDoJogo(jogoId) : null
  }, { teto: 30_000 })
}
