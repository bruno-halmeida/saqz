// Avisos e notificações (NOT-*). Sem push no stack local: tudo se confere na central (GET /api/me/notifications).
import { local, daqui, semSegundos } from '../lib/tempo.mjs'

const MIN = 60_000
const canais = (avisos) => avisos.map((a) => a.canal)

export default [
  {
    id: 'NOT-003', feature: 'avisos', critico: true,
    titulo: 'Avisos do jogo',
    async rodar(c) {
      const { ORG, ATL1, ATL2, ATL3 } = await c.atores('ORG', 'ATL1', 'ATL2', 'ATL3')
      const g = await c.passo('ORG cria o grupo; ATL1, ATL2 e ATL3 entram', () => c.grupo(ORG, [ATL1, ATL2, ATL3]))
      await c.passo('rascunho: ORG cria e edita o jogo; ninguém recebe nada', async () => {
        const rasc = await c.jogo(ORG, g.id, { vagas: 4, publicar: false })
        const lido = await ORG.jogo(g.id, rasc.id)
        c.status(await ORG.editarJogo(g.id, lido.corpo, lido.etag, { title: 'Jogo QA rascunho' }), 200)
        await c.dormir(2000)
        for (const a of [ATL1, ATL2, ATL3]) c.igual(canais(await a.avisosDoGrupo(g.id)), [], `${a.papel}: rascunho não avisa`)
        c.igual(await c.valor(`select count(*) from group_messages where group_id='${g.id}' and channel not in ('FIRST_MEMBER','FIRST_CONFIRMATION')`), '0', 'mensagens do grupo (fora o aviso de primeiro membro)')
        c.status(await ORG.cancelarJogo(g.id, rasc.id), 200)
      })
      const j = await c.passo('ORG publica o jogo (o próximo do grupo): todo o grupo recebe "O jogo está liberado" na central', async () => {
        const jogo = await c.jogo(ORG, g.id, { vagas: 4 })
        for (const a of [ATL1, ATL2, ATL3]) {
          const x = await c.esperar(`GAME_OPEN de ${a.papel}`, async () => { const v = await a.avisosDoJogo(jogo.id); return v.length ? v : null }, { teto: 20_000 })
          c.igual(canais(x), ['GAME_OPEN'], `${a.papel}: aviso de jogo liberado`)
        }
        return jogo
      })
      await c.passo('ATL1 diz Vou e ATL3 diz Não vou; ORG toca em "Avisar": só quem não respondeu (ATL2) recebe o lembrete', async () => {
        c.status(await ATL1.vou(g.id, j.id), 200)
        c.status(await ATL3.naoVou(g.id, j.id), 200)
        const r = c.status(await ORG.avisarPendentes(g.id, j.id), 200)
        c.igual(r.corpo.channel, 'REMINDER', 'canal do lembrete')
        c.igual(r.corpo.recipientCount, 1, 'destinatários do lembrete')
        const lembretes = async (a) => (await a.avisosDoJogo(j.id)).filter((n) => n.canal === 'REMINDER').length
        c.igual(await lembretes(ATL2), 1, 'ATL2 (sem resposta) recebe')
        c.igual(await lembretes(ATL1), 0, 'ATL1 (já respondeu Vou) não recebe')
        c.igual(await lembretes(ATL3), 0, 'ATL3 (já respondeu Não vou) não recebe')
      })
      await c.passo('mudou o horário: ORG edita o jogo publicado; todos recebem o aviso de mudança, menos ATL3 ("Não vou") e o próprio ORG', async () => {
        const lido = await ORG.jogo(g.id, j.id)
        const novo = semSegundos(daqui(60 * 60 * 3))
        const r = await ORG.editarJogo(g.id, lido.corpo, lido.etag, { ...local(novo), startsAt: novo.toISOString(), confirmationDeadline: daqui(60 * 24).toISOString() })
        c.status(r, 200)
        const mudou = async (a) => (await a.avisosDoGrupo(g.id)).filter((n) => / mudou: /.test(n.corpo))
        for (const a of [ATL1, ATL2]) {
          const x = await c.esperar(`aviso de mudança de ${a.papel}`, async () => { const v = await mudou(a); return v.length ? v : null }, { teto: 20_000 })
          c.afirmar(x[0].corpo.includes('às'), `o aviso traz a nova hora: ${x[0].corpo}`)
        }
        c.igual((await mudou(ATL3)).length, 0, 'ATL3 (Não vou) não recebe')
        c.igual((await mudou(ORG)).length, 0, 'quem editou não recebe')
      })
      await c.passo('mudar só as vagas (não é dia, hora nem local) não gera aviso de mudança', async () => {
        const mudancas = async () => (await ATL2.avisosDoGrupo(g.id)).filter((n) => / mudou: /.test(n.corpo)).length
        const antes = await mudancas()
        c.status(await ORG.ajustarVagas(g.id, j.id, 6), 200)
        await c.dormir(2000)
        c.igual(await mudancas(), antes, 'avisos de mudança de ATL2')
      })
      await c.passo('jogo cancelado: confirmados e espera recebem; ATL3 (Não vou) não recebe', async () => {
        c.status(await ORG.cancelarJogo(g.id, j.id), 200)
        const cancelou = async (a) => (await a.avisosDoJogo(j.id)).concat(await a.avisosDoGrupo(g.id)).filter((n) => /cancelad/i.test(n.corpo))
        for (const a of [ATL1]) {
          const x = await c.esperar(`aviso de cancelamento de ${a.papel}`, async () => { const v = await cancelou(a); return v.length ? v : null }, { teto: 20_000 })
          c.afirmar(x.length >= 1, 'recebe o cancelamento')
        }
        c.igual((await cancelou(ATL3)).length, 0, 'ATL3 não recebe o cancelamento')
      })
      await c.passo('jogo que já começou: "Avisar" não manda lembrete', async () => {
        const andando = await c.jogo(ORG, g.id, { vagas: 4 })
        await c.inicioPara(andando.id, new Date(Date.now() - 10 * MIN))
        const r = await ORG.avisarPendentes(g.id, andando.id)
        const lembretes = (await ATL2.avisosDoJogo(andando.id)).filter((n) => n.canal === 'REMINDER')
        c.afirmar(r.status >= 400 || lembretes.length === 0, `jogo iniciado não pode gerar lembrete (HTTP ${r.status}, ${lembretes.length} lembretes)`, r)
      })
    },
  },
]
