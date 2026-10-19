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
{
    id: 'ASS-001', feature: 'assinatura', critico: false,
    titulo: 'Planos e limites',
    async rodar(c) {
      const { TIT, ORG3, ILI } = await c.atores('TIT', 'ORG3', 'ILI')
      const titular = await c.passo('dono TIT assina o plano Titular (1 grupo, até 25 atletas por grupo) e cria o primeiro grupo', async () => {
        const g = await c.grupo(TIT, [])
        await c.assinar(TIT, { plano: 'TITULAR' })
        return g
      })
      await c.passo('Titular com 1 grupo cria outro: recusado (GROUP_LIMIT_EXCEEDED)', async () => {
        c.status(await TIT.criarGrupo(`QA Segundo grupo ${c.tag}`), 403, 'GROUP_LIMIT_EXCEEDED')
      })
      await c.passo('Titular com 25 atletas no grupo: o 26º entra recusado (ATHLETE_LIMIT_EXCEEDED) e quem já é membro segue normal', async () => {
        const lote = await Promise.all(Array.from({ length: 25 }, (_, i) => c.ator(`M${i + 1}`)))
        for (let i = 0; i < lote.length; i += 5) await Promise.all(lote.slice(i, i + 5).map((a) => a.entrarPorConvite(titular.codigo).then((r) => c.status(r, 200))))
        const extra = await c.ator('EXTRA')
        const r = await extra.entrarPorConvite(titular.codigo)
        c.afirmar(r.status >= 400 && r.codigo === 'ATHLETE_LIMIT_EXCEEDED', `o 26º atleta deve ser recusado com ATHLETE_LIMIT_EXCEEDED (veio HTTP ${r.status} ${r.codigo})`, r)
        c.status(await lote[0].entrarPorConvite(titular.codigo), 200)  // quem já é membro nunca é bloqueado
        c.status(await lote[0].grupo(titular.id), 200)
        c.status(await lote[24].sairDoGrupo(titular.id), 204)
        c.status(await extra.entrarPorConvite(titular.codigo), 200)    // a vaga volta na hora
      })
      await c.passo('relatórios fechados para o Titular', async () => {
        c.igual(c.status(await TIT.chamar('GET', `/api/groups/${titular.id}/reports/availability`), 200).corpo.enabled, false, 'relatórios no Titular')
        c.status(await TIT.chamar('GET', `/api/groups/${titular.id}/reports`), 403, 'SUBSCRIPTION_REQUIRED')
      })
      await c.passo('Organizador: até 3 grupos, atletas ilimitados, relatórios fechados', async () => {
        const g1 = await c.grupo(ORG3, [], { nome: `QA Org 1 ${c.tag}` })
        await c.assinar(ORG3, { plano: 'ORGANIZADOR' })
        await c.grupo(ORG3, [], { nome: `QA Org 2 ${c.tag}` }); await c.grupo(ORG3, [], { nome: `QA Org 3 ${c.tag}` })
        c.status(await ORG3.criarGrupo(`QA Org 4 ${c.tag}`), 403, 'GROUP_LIMIT_EXCEEDED')
        c.igual(c.status(await ORG3.chamar('GET', `/api/groups/${g1.id}/reports/availability`), 200).corpo.enabled, false, 'relatórios no Organizador')
      })
      await c.passo('troca agendada para plano menor: já vale o limite do plano menor (Organizador com troca para Titular não cria outro grupo)', async () => {
        const { OR4 } = await c.atores('OR4')
        await c.grupo(OR4, [])
        await c.assinar(OR4, { plano: 'ORGANIZADOR', planoPendente: 'TITULAR' })
        c.status(await OR4.criarGrupo(`QA Segundo ${c.tag}`), 403, 'GROUP_LIMIT_EXCEEDED')
      })
      await c.passo('Ilimitado: grupos ilimitados e relatórios abertos', async () => {
        const gi = await c.grupo(ILI, [], { nome: `QA Ili 1 ${c.tag}` })
        await c.assinar(ILI, { plano: 'ILIMITADO' })
        for (let i = 2; i <= 5; i++) c.status(await ILI.criarGrupo(`QA Ili ${i} ${c.tag}`), 201)
        c.igual(c.status(await ILI.chamar('GET', `/api/groups/${gi.id}/reports/availability`), 200).corpo.enabled, true, 'relatórios no Ilimitado')
        c.status(await ILI.chamar('GET', `/api/groups/${gi.id}/reports`), 200)
      })
    },
  },
  {
    id: 'ASS-005', feature: 'assinatura', critico: false,
    titulo: 'Trocar de plano, cancelar e reativar (só o que o stack local prova)',
    async rodar(c) {
      const { ORG, ATL1 } = await c.atores('ORG', 'ATL1')
      const g = await c.passo('ORG cria o grupo, assina o Organizador e ATL1 entra', async () => {
        const grupo = await c.grupo(ORG, [ATL1])
        await c.assinar(ORG, { plano: 'ORGANIZADOR' })
        await c.encerrarTeste(ORG)  // sem o teste de pé: só a assinatura sustenta o plano
        return grupo
      })
      await c.passo('assinatura ativa: o grupo funciona normalmente (sem o teste, quem sustenta é a assinatura)', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 4 })
        c.status(await ATL1.vou(g.id, j.id), 200)
      })
      await c.passo('cancelar: o acesso continua até o fim do período pago', async () => {
        await c.assinar(ORG, { plano: 'ORGANIZADOR', status: 'CANCELED', canceladaHaDias: 1, fimEmDias: 15 })
        c.igual(c.status(await ORG.chamar('GET', '/subscriptions/trial'), 200).corpo.status, 'SUBSCRIBED', 'plano ainda vale')
        const j = await c.jogo(ORG, g.id, { vagas: 4 })
        c.status(await ATL1.vou(g.id, j.id), 200)
      })
      await c.passo('o período pago acaba: o grupo congela (somente leitura); o atleta não confirma presença e o dono não cria jogo', async () => {
        await c.assinar(ORG, { plano: 'ORGANIZADOR', status: 'CANCELED', canceladaHaDias: 20, fimEmDias: -1 })
        const t = c.status(await ATL1.chamar('GET', `/api/groups/${g.id}/trial`), 200).corpo
        c.igual(t.readOnly, true, 'grupo congelado')
        const aberto = (await ORG.jogos(g.id)).corpo.find((x) => x.status === 'PUBLISHED')
        c.status(await ATL1.vou(g.id, aberto.id), 403, 'SUBSCRIPTION_REQUIRED')
      })
      await c.passo('descer de plano agenda a troca: o limite do plano menor já vale (Organizador com 2 grupos, troca para Titular)', async () => {
        const { OR5 } = await c.atores('OR5')
        await c.grupo(OR5, [], { nome: `QA G1 ${c.tag}` }); await c.grupo(OR5, [], { nome: `QA G2 ${c.tag}` })
        await c.assinar(OR5, { plano: 'ORGANIZADOR', planoPendente: 'TITULAR' })
        c.status(await OR5.criarGrupo(`QA G3 ${c.tag}`), 403, 'GROUP_LIMIT_EXCEEDED')
      })
    },
  },
  {
    id: 'ASS-006', feature: 'assinatura', critico: false,
    titulo: 'Pagamento atrasado e a carência',
    async rodar(c) {
      const { ORG, ATL1 } = await c.atores('ORG', 'ATL1')
      const g = await c.passo('ORG cria o grupo, assina o Organizador (teste já encerrado) e ATL1 entra', async () => {
        const grupo = await c.grupo(ORG, [ATL1])
        await c.assinar(ORG, { plano: 'ORGANIZADOR' })
        await c.encerrarTeste(ORG)
        await c.configurar(ORG, grupo.id, { mensalistaPriority: false })
        grupo.jogo = await c.jogo(ORG, grupo.id, { vagas: 4 })
        return grupo
      })
      const congelado = async () => c.status(await ATL1.chamar('GET', `/api/groups/${g.id}/trial`), 200).corpo.readOnly
      await c.passo('venceu ontem e não pagou ("Pagamento pendente"): o acesso segue normal na carência', async () => {
        await c.assinar(ORG, { plano: 'ORGANIZADOR', status: 'PAST_DUE', atrasadaHaDias: 1 })
        c.igual(await congelado(), false, 'grupo congelado no 1º dia')
        c.status(await ATL1.vou(g.id, g.jogo.id), 200)
      })
      await c.passo('no 5º dia ainda vale; pagou no 5º dia: volta a Ativo', async () => {
        await c.assinar(ORG, { plano: 'ORGANIZADOR', status: 'PAST_DUE', atrasadaHaDias: 5 })
        c.igual(await congelado(), false, 'grupo congelado no 5º dia')
        await c.assinar(ORG, { plano: 'ORGANIZADOR', status: 'ACTIVE' })
        c.igual(await congelado(), false, 'grupo congelado depois de pagar')
      })
      await c.passo('passou 7 dias de carência: os grupos congelam', async () => {
        await c.assinar(ORG, { plano: 'ORGANIZADOR', status: 'PAST_DUE', atrasadaHaDias: 8 })
        c.igual(await congelado(), true, 'grupo congelado depois da carência')
        c.status(await ORG.trocarConvite(g.id), 403, 'SUBSCRIPTION_REQUIRED')
        c.status(await ORG.criarGrupo(`QA Congelado ${c.tag}`), 403)

      })
      await c.passo('pagou no dia 10: volta na hora', async () => {
        await c.assinar(ORG, { plano: 'ORGANIZADOR', status: 'ACTIVE' })
        c.igual(await congelado(), false, 'grupo congelado depois do pagamento tardio')
      })
      await c.passo('quem nunca pagou (abriu o Pix e não pagou) não tem carência: sem plano até o primeiro pagamento', async () => {
        await c.assinar(ORG, { plano: 'ORGANIZADOR', status: 'PAST_DUE', atrasadaHaDias: 1, pagou: false })
        c.igual(await congelado(), true, 'nunca pagou: congelado já no primeiro dia')
      })
    },
  },
]
