// Assinatura e teste grátis (ASS-*). Não há Asaas nem loja no stack local: "assinar" é simulado estendendo o teste no banco.
import { local, daqui, semSegundos } from '../lib/tempo.mjs'
import { randomUUID } from 'node:crypto'

const DIA = 24 * 60 * 60_000

export default [
  {
    id: 'ASS-002', feature: 'assinatura', critico: true,
    titulo: 'O teste grátis',
    async rodar(c) {
      const modoAntes = await c.valor("select mode from trial_offer_settings")
      const modo = (novo) => c.sql(`update trial_offer_settings set mode='${novo}', updated_at=now()`)
      const teste = async (dono) => {
        const [linha] = await c.sql(`select extract(epoch from (ends_at - started_at))/86400, coalesce(coupon_code,''), started_at from organizer_trials where owner_user_id='${dono.id}'`)
        return linha ? { dias: Math.round(Number(linha[0])), cupom: linha[1], inicio: linha[2] } : null
      }
      try {
        const { ORG } = await c.atores('ORG')
        await c.passo('oferta ligada: ORG, dono novo, ainda sem teste e pode criar grupo', async () => {
          await modo('ON')
          const t = c.status(await ORG.chamar('GET', '/subscriptions/trial'), 200).corpo
          c.igual([t.canCreateGroup, t.offerMode, await teste(ORG)], [true, 'ON', null], 'antes do primeiro grupo')
        })
        const g1 = await c.passo('ORG cria o primeiro grupo: o teste de 14 dias começa e o plano é do dono', async () => {
          const g = await c.grupo(ORG, [])
          const t = await teste(ORG)
          c.igual([t?.dias, t?.cupom], [14, ''], 'teste de 14 dias sem cupom')
          const api = c.status(await ORG.chamar('GET', '/subscriptions/trial'), 200).corpo
          c.igual([api.status, api.readOnly, api.trialDays], ['ACTIVE', false, 14], 'estado do teste pela API')
          return g
        })
        await c.passo('apagar o grupo e criar outro não renova o teste (mesmo início, mesmos 14 dias)', async () => {
          const inicio = (await teste(ORG)).inicio
          c.status(await ORG.chamar('DELETE', `/api/groups/${g1.id}`), 204)
          await c.grupo(ORG, [], { nome: `QA Segundo grupo ${c.tag}` })
          const t = await teste(ORG)
          c.igual([t.dias, t.inicio], [14, inicio], 'teste igual ao primeiro')
          c.igual((await ORG.chamar('GET', '/subscriptions/trial')).corpo.canRedeemCoupon, false, 'quem já foi dono não resgata cupom de teste')
        })
        await c.passo('oferta "só com cupom", sem cupom: dono novo não ganha teste', async () => {
          await modo('COUPON_ONLY')
          const { SEM } = await c.atores('SEM')
          const r = await SEM.criarGrupo(`QA Sem cupom ${c.tag}`)
          c.afirmar(r.status === 403 || (await teste(SEM)) === null, `sem cupom e oferta só com cupom, o dono não pode ganhar teste (criar grupo: HTTP ${r.status}; teste: ${JSON.stringify(await teste(SEM))})`, r)
          c.igual(await teste(SEM), null, 'nenhum teste gravado')
        })
        await c.passo('oferta "só com cupom", com cupom de 30 dias: o dono escolhe o cupom, cria o grupo e o teste dura 30 dias', async () => {
          const codigo = `QA${c.tag.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 20)}`
          await c.sql(`insert into trial_coupons (id, code, campaign, trial_days, active, created_at) values ('${randomUUID()}', '${codigo}', 'qa-atores', 30, true, now())`)
          const { COM } = await c.atores('COM')
          c.status(await COM.chamar('POST', '/subscriptions/trial/coupon', { code: codigo }), 200)
          await c.grupo(COM, [], { nome: `QA Com cupom ${c.tag}` })
          const t = await teste(COM)
          c.igual([t?.dias, t?.cupom], [30, codigo], 'teste de 30 dias com o cupom')
          c.status(await COM.chamar('POST', '/subscriptions/trial/coupon', { code: 'NAOEXISTE' }), [400, 409])
        })
        await c.passo('oferta desligada: dono novo não ganha teste', async () => {
          await modo('OFF')
          const { OFF } = await c.atores('OFF')
          const r = await OFF.criarGrupo(`QA Desligada ${c.tag}`)
          c.afirmar(r.status === 403 || (await teste(OFF)) === null, `com a oferta desligada não há teste (criar grupo: HTTP ${r.status})`, r)
          c.igual(await teste(OFF), null, 'nenhum teste gravado')
        })
      } finally {
        if (modoAntes) await modo(modoAntes)
      }
    },
  },
  {
    id: 'ASS-003', feature: 'assinatura', critico: true,
    titulo: 'Sem plano ativo: o grupo fica congelado',
    async rodar(c) {
      const { ORG, ADM, ATL1, ATL2, ATL3, NOVO } = await c.atores('ORG', 'ADM', 'ATL1', 'ATL2', 'ATL3', 'NOVO')
      const g = await c.passo('ORG cria o grupo (diária R$ 20), ADM vira admin; ATL1, ATL2 e ATL3 entram; jogo publicado; ATL1 e ATL2 confirmam', async () => {
        const grupo = await c.grupo(ORG, [ADM, ATL1, ATL2, ATL3])
        c.status(await ORG.mudarPapel(grupo.id, ADM.id, 'ADMIN'), 200)
        await c.configurar(ORG, grupo.id, { mensalistaPriority: false })
        grupo.jogo = await c.jogo(ORG, grupo.id, { vagas: 4, valorCents: 2000 })
        for (const a of [ATL1, ATL2]) c.status(await a.vou(grupo.id, grupo.jogo.id), 200)
        return grupo
      })
      await c.passo('o teste do dono acaba (banco): o grupo congela', async () => {
        await c.encerrarTeste(ORG)
        const t = c.status(await ATL1.chamar('GET', `/api/groups/${g.id}/trial`), 200).corpo
        c.igual([t.readOnly, t.status], [true, 'EXPIRED'], 'grupo em somente leitura')
      })
      await c.passo('dono cria jogo: recusado (SUBSCRIPTION_REQUIRED); admin também', async () => {
        const inicio = semSegundos(daqui(60 * 24 * 5))
        const corpo = { title: 'Jogo novo', venue: { name: 'Quadra QA', address: 'Rua QA, 1' }, ...local(inicio), startsAt: inicio.toISOString(), durationMinutes: 90, capacity: 4, confirmationDeadline: daqui(60 * 24 * 4).toISOString(), useDefaultGameFee: false, gameFeeCents: null }
        c.status(await ORG.criarJogo(g.id, corpo), 403, 'SUBSCRIPTION_REQUIRED')
        c.status(await ADM.criarJogo(g.id, corpo), 403, 'SUBSCRIPTION_REQUIRED')
      })
      await c.passo('atleta ATL3 toca "Vou": recusado com aviso de plano congelado (SUBSCRIPTION_REQUIRED)', async () => {
        c.status(await ATL3.vou(g.id, g.jogo.id), 403, 'SUBSCRIPTION_REQUIRED')
        c.igual(await c.statusDe(g.jogo.id, ATL3), null, 'nenhuma presença gravada')
      })
      await c.passo('atleta ATL2 toca "Não vou": funciona e libera a vaga', async () => {
        c.status(await ATL2.naoVou(g.id, g.jogo.id), 200)
        c.igual(await c.statusDe(g.jogo.id, ATL2), 'DECLINED', 'ATL2')
      })
      await c.passo('congelado, todos continuam lendo: grupo, jogos, lista de presença e financeiro (dono e atleta)', async () => {
        c.status(await ATL1.grupo(g.id), 200)
        c.status(await ATL1.jogos(g.id), 200)
        c.status(await ATL1.lista(g.id, g.jogo.id), 200)
        c.status(await ATL1.minhasCobrancas(g.id), 200)
        c.status(await ORG.cobrancas(g.id), 200)
        c.status(await ORG.membros(g.id), 200)
      })
      await c.passo('congelado, o dono não dá baixa nem troca o código do convite; admin também não', async () => {
        const [d1] = (await ORG.cobrancas(g.id)).corpo.charges.filter((x) => x.memberId === ATL1.id)
        c.status(await ORG.baixa(g.id, d1, 'PAID', { paidMethod: 'PIX' }), 403, 'SUBSCRIPTION_REQUIRED')
        c.status(await ADM.trocarConvite(g.id), 403, 'SUBSCRIPTION_REQUIRED')
      })
      await c.passo('congelado, convite novo: quem tenta entrar pelo código é recusado', async () => {
        const r = await NOVO.entrarPorConvite(g.codigo)
        c.afirmar(r.status === 403 || r.status === 402 || r.status === 409, `convite em grupo congelado deve ser recusado (veio ${r.status})`, r)
        c.igual(await c.valor(`select count(*) from group_memberships where group_id='${g.id}' and user_id='${NOVO.id}'`), '0', 'NOVO fora do grupo')
      })
      await c.passo('congelado, ATL1 sai do grupo: funciona', async () => {
        c.status(await ATL1.sairDoGrupo(g.id), 204)
        c.igual(await c.valor(`select count(*) from group_memberships where group_id='${g.id}' and user_id='${ATL1.id}' and active`), '0', 'ATL1 saiu')
      })
      await c.passo('o dono assina (teste estendido no banco): tudo volta na hora — ATL3 diz Vou e o dono dá baixa', async () => {
        await c.reabrirTeste(ORG)
        c.status(await ATL3.vou(g.id, g.jogo.id), 200)
        const [d] = (await ORG.cobrancas(g.id)).corpo.charges.filter((x) => x.memberId === ATL3.id)
        c.status(await ORG.baixa(g.id, d, 'PAID', { paidMethod: 'PIX' }), 200)
        c.status(await ADM.trocarConvite(g.id), 200)
      })
    },
  },
]
