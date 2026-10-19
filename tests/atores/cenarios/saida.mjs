// Saída do grupo e exclusão (SAI-*). A exclusão da conta (SAI-009) está em acesso.mjs.
import { randomUUID } from 'node:crypto'

const MIN = 60_000

export default [
  {
    id: 'SAI-001', feature: 'saida', critico: false,
    titulo: 'Sair do grupo',
    async rodar(c) {
      const { ORG, ADM, ATL1 } = await c.atores('ORG', 'ADM', 'ATL1')
      const g = await c.passo('ORG cria o grupo; ADM vira admin; ATL1 entra', async () => {
        const grupo = await c.grupo(ORG, [ADM, ATL1])
        c.status(await ORG.mudarPapel(grupo.id, ADM.id, 'ADMIN'), 200)
        return grupo
      })
      await c.passo('atleta ATL1 sai: deixa de ver o grupo (404) e some da lista de membros', async () => {
        c.status(await ATL1.sairDoGrupo(g.id), 204)
        c.status(await ATL1.grupo(g.id), 404)
        c.afirmar(!c.status(await ORG.membros(g.id), 200).corpo.some((m) => m.userId === ATL1.id), 'ATL1 não aparece mais')
      })
      await c.passo('sair de um grupo de que já não faz parte: sucesso, sem efeito', async () => {
        c.status(await ATL1.sairDoGrupo(g.id), [204, 404])
      })
      await c.passo('admin ADM sai: perde a permissão de admin (convite e financeiro passam a dar 404)', async () => {
        c.status(await ADM.sairDoGrupo(g.id), 204)
        c.status(await ADM.convite(g.id), [403, 404])
        c.status(await ADM.cobrancas(g.id), [403, 404])
      })
      await c.passo('o dono não sai: recusado e segue dono', async () => {
        c.status(await ORG.sairDoGrupo(g.id), 403)
        c.igual(c.status(await ORG.membros(g.id), 200).corpo.find((m) => m.userId === ORG.id).role, 'OWNER', 'dono segue dono')
      })
    },
  },
  {
    id: 'SAI-002', feature: 'saida', critico: false,
    titulo: 'O que acontece com as presenças de quem sai',
    async rodar(c) {
      const { ORG, ATL1, ATL2, ATL3, ATL4, ATL5 } = await c.atores('ORG', 'ATL1', 'ATL2', 'ATL3', 'ATL4', 'ATL5')
      const g = await c.passo('ORG cria o grupo e desliga a prioridade', async () => {
        const grupo = await c.grupo(ORG, [ATL1, ATL2, ATL3, ATL4, ATL5])
        await c.configurar(ORG, grupo.id, { mensalistaPriority: false })
        return grupo
      })
      await c.passo('ordem de chegada: confirmado sai, jogo futuro: vira "Não vou" pelo sistema e sobe o próximo da fila', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 2 })
        for (const a of [ATL1, ATL2, ATL3]) c.status(await a.vou(g.id, j.id), 200)
        c.status(await ATL1.sairDoGrupo(g.id), 204)
        c.igual([await c.statusDe(j.id, ATL1), await c.statusDe(j.id, ATL3)], ['DECLINED', 'CONFIRMED'], 'ATL1 fora, ATL3 sobe')
        c.igual((await c.eventos(j.id, ATL1)).at(-1), 'CONFIRMED>DECLINED:SYSTEM', 'evento de sistema')
      })
      await c.passo('quem sai não recebe aviso', async () => {
        c.igual((await ATL1.avisosDoGrupo(g.id)).filter((n) => /sa(i|í)u|removid/i.test(n.corpo)).length, 0, 'avisos de ATL1 sobre a própria saída')
      })
      await c.passo('na espera sai do grupo: sai da fila', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 2 })
        for (const a of [ATL2, ATL3, ATL4]) c.status(await a.vou(g.id, j.id), 200)
        c.status(await ATL4.sairDoGrupo(g.id), 204)
        c.igual(await c.statusDe(j.id, ATL4), 'DECLINED', 'ATL4 fora da fila')
      })
      await c.passo('anfitrião com 2 convidados confirmados sai: 3 vagas liberadas e a fila sobe', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 4 })
        c.status(await ATL2.vou(g.id, j.id), 200)
        const c1 = c.status(await ATL2.convidado(g.id, j.id, 'Convidado Um'), 201), c2 = c.status(await ATL2.convidado(g.id, j.id, 'Convidado Dois'), 201)
        c.status(await ATL3.vou(g.id, j.id), 200)  // com vaga, e o prazo em dia (prioridade desligada)
        c.status(await ORG.porOutro(g.id, j.id, ATL5.id, 'CONFIRM', 'Chegou'), 200)
        c.status(await ATL3.naoVou(g.id, j.id), 200)
        // abre 2 vagas para os convidados subirem: ATL3 saiu e o convidado 1 sobe; força o segundo
        await c.sql(`update game_attendance set status='CONFIRMED', waitlist_sequence=null, version=version+1, updated_at=now() where game_id='${j.id}' and member_user_id='${ATL2.id}' and guest_seq>0`)
        c.status(await ATL2.sairDoGrupo(g.id), 204)
        for (const seq of [c1.corpo.guestSeq, c2.corpo.guestSeq]) c.igual(await c.valor(`select status from game_attendance where game_id='${j.id}' and member_user_id='${ATL2.id}' and guest_seq=${seq}`), 'DECLINED', `convidado ${seq} saiu junto`)
        c.igual(await c.statusDe(j.id, ATL2), 'DECLINED', 'anfitrião')
      })
      await c.passo('jogo já iniciado: nada muda para quem sai durante o jogo', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 4 })
        c.status(await ATL5.vou(g.id, j.id), 200)
        await c.inicioPara(j.id, new Date(Date.now() - 5 * MIN))
        c.status(await ATL5.sairDoGrupo(g.id), 204)
        c.igual(await c.statusDe(j.id, ATL5), 'CONFIRMED', 'ATL5 continua na lista do jogo iniciado')
      })
    },
  },
  {
    id: 'SAI-003', feature: 'saida', critico: false,
    titulo: 'Remover membro',
    async rodar(c) {
      const { ORG, ADM, ADM2, ATL1, ATL2, ATL3, SAIU } = await c.atores('ORG', 'ADM', 'ADM2', 'ATL1', 'ATL2', 'ATL3', 'SAIU')
      const g = await c.passo('ORG cria o grupo; ADM e ADM2 viram admins; ATL1, ATL2 e ATL3 entram', async () => {
        const grupo = await c.grupo(ORG, [ADM, ADM2, ATL1, ATL2, ATL3])
        for (const a of [ADM, ADM2]) c.status(await ORG.mudarPapel(grupo.id, a.id, 'ADMIN'), 200)
        return grupo
      })
      const ativo = async (a) => Number(await c.valor(`select count(*) from group_memberships where group_id='${g.id}' and user_id='${a.id}' and active`)) === 1
      await c.passo('atleta ATL3 tenta remover alguém: 403 e ninguém sai', async () => {
        c.status(await ATL3.remover(g.id, ATL1.id), 403)
        c.igual(await ativo(ATL1), true, 'ATL1 segue no grupo')
      })
      await c.passo('admin ADM remove um atleta (ATL1): removido; a pessoa removida não recebe aviso', async () => {
        c.status(await ADM.remover(g.id, ATL1.id), [200, 204])
        c.igual(await ativo(ATL1), false, 'ATL1 removido')
        c.status(await ATL1.grupo(g.id), 404)
        c.igual((await ATL1.avisosDoGrupo(g.id)).filter((n) => /removid/i.test(n.corpo)).length, 0, 'aviso para o removido')
      })
      await c.passo('qualquer um tenta remover o dono: recusado ("dono imutável")', async () => {
        for (const quem of [ORG, ADM]) c.status(await quem.remover(g.id, ORG.id), 403)
        c.igual(c.status(await ORG.membros(g.id), 200).corpo.find((m) => m.userId === ORG.id).role, 'OWNER', 'dono segue dono')
      })
      await c.passo('remover quem já saiu: sucesso, sem efeito', async () => {
        c.status(await SAIU.entrarPorConvite(g.codigo), 200)
        c.status(await SAIU.sairDoGrupo(g.id), 204)
        c.status(await ORG.remover(g.id, SAIU.id), [200, 204])
      })
      await c.passo('o dono remove um admin (ADM2): removido', async () => {
        c.status(await ORG.remover(g.id, ADM2.id), [200, 204])
        c.igual(await ativo(ADM2), false, 'ADM2 removido pelo dono')
      })
      await c.passo('admin remove OUTRO admin: deve ser recusado — só o dono remove admin (DEB-024; hoje é permitido)', async () => {
        const { ADM3 } = await c.atores('ADM3')
        await ADM3.entrarPorConvite(g.codigo)
        c.status(await ORG.mudarPapel(g.id, ADM3.id, 'ADMIN'), 200)
        const r = await ADM.remover(g.id, ADM3.id)
        c.divergir(r.status === 403, `admin removendo outro admin deveria dar 403 (veio HTTP ${r.status}); ADM3 ${await ativo(ADM3) ? 'segue' : 'FOI removido'} do grupo`)
      })
    },
  },
  {
    id: 'SAI-006', feature: 'saida', critico: false,
    titulo: 'Pendências financeiras de quem sai',
    async rodar(c) {
      const { ORG, ATL1, ATL2, ATL3 } = await c.atores('ORG', 'ATL1', 'ATL2', 'ATL3')
      const g = await c.passo('ORG cria o grupo; ATL1 é mensalista; ATL2 e ATL3 avulsos; jogo com diária de R$ 20', async () => {
        const grupo = await c.grupo(ORG, [ATL1, ATL2, ATL3])
        await c.tipo(ORG, grupo.id, ATL1)
        await c.configurar(ORG, grupo.id, { mensalistaPriority: false })
        grupo.jogo = await c.jogo(ORG, grupo.id, { vagas: 4, valorCents: 2000 })
        for (const a of [ATL2, ATL3]) c.status(await a.vou(grupo.id, grupo.jogo.id), 200)
        const mes = new Date().toISOString().slice(0, 7)
        c.status(await ORG.chamar('POST', `/api/groups/${grupo.id}/charges/monthly`, { requestId: randomUUID(), month: mes, amountCents: 5000, dueDate: `${mes}-28`, memberIds: [ATL1.id] }), 200)
        return grupo
      })
      const cob = async (a) => (await ORG.cobrancas(g.id)).corpo.charges.filter((x) => x.memberId === a.id && !x.guestDisplayName)
      await c.passo('mensalista ATL1 sai com a mensalidade pendente: ela continua pendente no caixa', async () => {
        c.status(await ATL1.sairDoGrupo(g.id), 204)
        c.igual((await cob(ATL1)).map((x) => x.status), ['PENDING'], 'mensalidade de ATL1')
      })
      await c.passo('avulso ATL2 removido com a diária pendente: continua pendente', async () => {
        c.status(await ORG.remover(g.id, ATL2.id), [200, 204])
        c.igual((await cob(ATL2)).map((x) => x.status), ['PENDING'], 'diária de ATL2')
      })
      await c.passo('o organizador decide: dispensar a cobrança de quem saiu funciona', async () => {
        const [d] = await cob(ATL2)
        c.status(await ORG.baixa(g.id, d, 'WAIVED', { note: 'Saiu do grupo' }), 200)
      })
      await c.passo('convidado confirmado do anfitrião que sai: a cobrança pendente do convidado é cancelada', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 4, valorCents: 3000 })
        c.status(await ATL3.vou(g.id, j.id), 200)
        const conv = c.status(await ATL3.convidado(g.id, j.id, 'Convidado Do Atl3'), 201)
        c.status(await ORG.porOutro(g.id, j.id, ORG.id, 'CONFIRM', 'Dono confirma'), 200)
        await c.sql(`update game_attendance set status='CONFIRMED', waitlist_sequence=null, version=version+1, updated_at=now() where game_id='${j.id}' and member_user_id='${ATL3.id}' and guest_seq=${conv.corpo.guestSeq}`)
        c.status(await ATL3.sairDoGrupo(g.id), 204)
        const doConvidado = (await ORG.cobrancas(g.id)).corpo.charges.filter((x) => x.gameId === j.id && x.guestDisplayName)
        c.afirmar(doConvidado.every((x) => x.status === 'CANCELLED'), `cobrança do convidado deve estar cancelada: ${JSON.stringify(doConvidado.map((x) => x.status))}`)
      })
      await c.passo('voltar ao grupo depois: reencontra as cobranças antigas', async () => {
        const r = await ATL1.entrarPorConvite(g.codigo)
        c.afirmar(r.status === 200, 'volta pelo convite', r)
        if (r.corpo.status === 'PENDING') c.status(await ORG.aprovarPedido(g.id, ATL1.id), 200)
        c.igual(c.status(await ATL1.minhasCobrancas(g.id), 200).corpo.charges.map((x) => x.status), ['PENDING'], 'mensalidade antiga')
      })
    },
  },
  {
    id: 'SAI-007', feature: 'saida', critico: false,
    titulo: 'Voltar ao grupo depois de sair ou ser removido',
    async rodar(c) {
      const { ORG, ADM, ATL1, ATL2, ATL3 } = await c.atores('ORG', 'ADM', 'ATL1', 'ATL2', 'ATL3')
      const g = await c.passo('ORG cria o grupo (convite direto, sem aprovação); ADM vira admin; ATL1 vira mensalista; ATL2 e ATL3 entram', async () => {
        const grupo = await c.grupo(ORG, [ADM, ATL1, ATL2, ATL3])
        c.status(await ORG.mudarPapel(grupo.id, ADM.id, 'ADMIN'), 200)
        await c.tipo(ORG, grupo.id, ATL1)
        return grupo
      })
      const membro = async (a) => Number(await c.valor(`select count(*) from group_memberships where group_id='${g.id}' and user_id='${a.id}' and active`)) === 1
      await c.passo('ATL2 sai por conta própria e usa o convite de novo, num grupo SEM aprovação: deve ficar pendente para o organizador (DEB-027)', async () => {
        c.status(await ATL2.sairDoGrupo(g.id), 204)
        const r = c.status(await ATL2.entrarPorConvite(g.codigo), 200)
        c.divergir(r.corpo.status === 'PENDING', `quem saiu e volta pelo convite deve virar pedido pendente (veio ${r.corpo.status}) — DEB-027`)
        if (r.corpo.status === 'PENDING') c.igual(await membro(ATL2), false, 'pendente não é membro ainda')
      })
      await c.passo('o organizador removeu ATL3 e ATL3 usa o convite: também deve ficar pendente', async () => {
        c.status(await ORG.remover(g.id, ATL3.id), [200, 204])
        const r = c.status(await ATL3.entrarPorConvite(g.codigo), 200)
        c.divergir(r.corpo.status === 'PENDING', `quem foi removido e volta pelo convite deve virar pedido pendente (veio ${r.corpo.status}) — DEB-027`)
      })
      await c.passo('pedido de volta aprovado: entra como atleta; recusado: não entra', async () => {
        const pend = (await ORG.pedidos(g.id)).corpo.map((p) => p.userId)
        if (pend.includes(ATL2.id)) { c.status(await ADM.aprovarPedido(g.id, ATL2.id), 200); c.igual(await membro(ATL2), true, 'ATL2 voltou') }
        if (pend.includes(ATL3.id)) { c.status(await ADM.recusarPedido(g.id, ATL3.id), 204); c.igual(await membro(ATL3), false, 'ATL3 recusado fora') }
      })
      await c.passo('ex-mensalista volta como avulso e ex-admin volta como atleta', async () => {
        c.status(await ATL1.sairDoGrupo(g.id), 204)
        c.status(await ADM.sairDoGrupo(g.id), 204)
        for (const a of [ATL1, ADM]) { const r = await a.entrarPorConvite(g.codigo); if (r.corpo.status === 'PENDING') c.status(await ORG.aprovarPedido(g.id, a.id), 200) }
        const lista = c.status(await ORG.atletas(g.id), 200).corpo.athletes
        c.igual(lista.find((a) => a.userId === ATL1.id)?.membershipType, 'AVULSO', 'ex-mensalista volta avulso')
        c.igual(lista.find((a) => a.userId === ADM.id)?.role, 'ATHLETE', 'ex-admin volta atleta')
      })
      await c.passo('grupo excluído: "grupo excluído" para quem tenta voltar', async () => {
        c.status(await ORG.excluirGrupo(g.id), 204)
        const r = await ATL3.entrarPorConvite(g.codigo)
        c.afirmar(r.status >= 400 && String(r.codigo).startsWith('INVITE_'), 'convite de grupo excluído deve ser recusado (INVITE_*)', r)
      })
    },
  },
  {
    id: 'SAI-008', feature: 'saida', critico: false,
    titulo: 'Excluir o grupo',
    async rodar(c) {
      const { ORG, ADM, ATL1 } = await c.atores('ORG', 'ADM', 'ATL1')
      const g = await c.passo('ORG cria o grupo; ADM vira admin; ATL1 entra', async () => {
        const grupo = await c.grupo(ORG, [ADM, ATL1])
        c.status(await ORG.mudarPapel(grupo.id, ADM.id, 'ADMIN'), 200)
        return grupo
      })
      await c.passo('admin e atleta não excluem o grupo (403) e o grupo segue de pé', async () => {
        c.status(await ADM.excluirGrupo(g.id), 403)
        c.status(await ATL1.excluirGrupo(g.id), 403)
        c.status(await ORG.grupo(g.id), 200)
      })
      await c.passo('o dono exclui: 204; o membro deixa de ver o grupo (404) e ele some da lista', async () => {
        c.status(await ORG.excluirGrupo(g.id), 204)
        c.status(await ATL1.grupo(g.id), 404)
        c.afirmar(!c.status(await ATL1.chamar('GET', '/api/groups'), 200).corpo.groups.some((x) => x.id === g.id), 'o grupo sai da lista do membro')
      })
      await c.passo('os membros recebem o aviso de grupo excluído (push; fica no banco e o corpo é neutro)', async () => {
        const avisos = await c.sql(`select m.body from group_notifications n join group_messages m on m.id = n.message_id where m.group_id='${g.id}' and m.body like '%excluído%' and n.recipient_id in ('${ADM.id}','${ATL1.id}')`)
        c.igual(avisos.length, 2, 'avisos para ADM e ATL1')
        c.afirmar(avisos.every(([corpo]) => /exclu/i.test(corpo)), `texto: ${avisos[0]?.[0]}`)
      })
      await c.passo('excluir de novo ou um grupo inexistente: 404; o convite do grupo excluído fala "Grupo excluído"', async () => {
        c.status(await ORG.excluirGrupo(g.id), 404)
        c.status(await ORG.excluirGrupo(randomUUID()), 404)
        const r = await ATL1.entrarPorConvite(g.codigo)
        c.igual(r.codigo, 'INVITE_GROUP_DELETED', 'convite de grupo excluído')
      })
      await c.passo('dono no limite de 3 grupos exclui 1 e pode criar outro', async () => {
        const { DONO } = await c.atores('DONO')
        const tres = []
        for (let i = 1; i <= 3; i++) tres.push(await c.grupo(DONO, [], { nome: `QA Limite ${i} ${c.tag}` }))
        c.status(await DONO.criarGrupo(`QA Limite 4 ${c.tag}`), 403, 'GROUP_LIMIT_EXCEEDED')
        c.status(await DONO.excluirGrupo(tres[0].id), 204)
        c.status(await DONO.criarGrupo(`QA Limite 4 ${c.tag}`), 201)
      })
    },
  },
]
