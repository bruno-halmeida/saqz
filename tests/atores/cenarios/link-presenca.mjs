// Link de presença do lembrete do WhatsApp (LINK-*). Não há WhatsApp no stack local: o código do link é lido do banco
// (notification_attendance_links), que é onde o lembrete o guarda, e resolvido pelo mesmo endpoint que o app usa.

const MIN = 60_000

/** Código do link do lembrete mais recente de um jogo. */
const codigoDoLembrete = async (c, jogoId) => c.valor(`select l.code from notification_attendance_links l join group_messages m on m.id = l.message_id where m.game_id='${jogoId}' order by m.sequence desc limit 1`)

export default [
  {
    id: 'LINK-001', feature: 'link', critico: false,
    titulo: 'Os links de presença',
    async rodar(c) {
      const { ORG, ATL1 } = await c.atores('ORG', 'ATL1')
      const g = await c.passo('ORG cria o grupo e marca jogo publicado; ATL1 entra', async () => {
        const grupo = await c.grupo(ORG, [ATL1])
        grupo.jogo = await c.jogo(ORG, grupo.id, { vagas: 4 })
        return grupo
      })
      await c.passo('o lembrete ("Avisar") gera um link de presença único para o jogo; avisos seguintes geram outro', async () => {
        c.status(await ORG.avisarPendentes(g.id, g.jogo.id), 200)
        const primeiro = await codigoDoLembrete(c, g.jogo.id)
        c.afirmar(primeiro && primeiro.length >= 40, `código do link do lembrete: ${primeiro}`)
        c.status(await ORG.avisarPendentes(g.id, g.jogo.id), 200)
        const segundo = await codigoDoLembrete(c, g.jogo.id)
        c.afirmar(segundo && segundo !== primeiro, 'cada lembrete leva o próprio código')
      })
      await c.passo('o link aberto por um membro leva ao jogo certo (grupo e jogo)', async () => {
        const r = c.status(await ATL1.resolverLink(await codigoDoLembrete(c, g.jogo.id)), 200).corpo
        c.igual([r.groupId, r.gameId], [g.id, g.jogo.id], 'destino do link')
      })
    },
  },
  {
    id: 'LINK-002', feature: 'link', critico: false,
    titulo: 'Abrir o link sendo membro',
    async rodar(c) {
      const { ORG, ATL1, ATL2 } = await c.atores('ORG', 'ATL1', 'ATL2')
      const g = await c.passo('ORG cria o grupo e desliga a prioridade; ATL1 e ATL2 entram; jogo publicado e lembrete enviado', async () => {
        const grupo = await c.grupo(ORG, [ATL1, ATL2])
        await c.configurar(ORG, grupo.id, { mensalistaPriority: false })
        grupo.jogo = await c.jogo(ORG, grupo.id, { vagas: 4 })
        c.status(await ORG.avisarPendentes(grupo.id, grupo.jogo.id), 200)
        grupo.codigo2 = await codigoDoLembrete(c, grupo.jogo.id)
        return grupo
      })
      await c.passo('membro abre o link "Vou" em jogo futuro: o link resolve e a resposta se registra pelas regras da presença', async () => {
        c.status(await ATL1.resolverLink(g.codigo2), 200)
        c.status(await ATL1.vou(g.id, g.jogo.id), 200)
        c.igual(await c.statusDe(g.jogo.id, ATL1), 'CONFIRMED', 'ATL1')
        c.status(await ATL1.resolverLink(g.codigo2), 200)  // mesmo link de novo: repetição sem efeito
        c.status(await ATL1.vou(g.id, g.jogo.id), 200)
        c.igual((await c.eventos(g.jogo.id, ATL1)).length, 1, 'eventos depois de abrir o link duas vezes')
      })
      await c.passo('Vou, Não vou, Vou pelo mesmo link funciona (o último vale)', async () => {
        const base = (await c.eventos(g.jogo.id, ATL1)).length
        c.status(await ATL1.naoVou(g.id, g.jogo.id), 200)
        c.status(await ATL1.vou(g.id, g.jogo.id), 200)
        c.igual(await c.statusDe(g.jogo.id, ATL1), 'CONFIRMED', 'ATL1 depois de V,N,V')
        c.igual((await c.eventos(g.jogo.id, ATL1)).length, base + 2, 'eventos')
      })
      await c.passo('depois do prazo e antes do início o membro ainda responde, e o "Vou" vai para a espera passando pela fila', async () => {
        await c.prazoPara(g.jogo.id, new Date(Date.now() - MIN))
        c.status(await ATL2.vou(g.id, g.jogo.id), 200)
        c.igual((await c.eventos(g.jogo.id, ATL2))[0], 'null>WAITLISTED:SELF', 'Vou depois do prazo entra pela espera')
      })
      await c.passo('jogo que já começou: "O jogo já começou" (GAME_STARTED); cancelado: GAME_CANCELLED; rascunho: GAME_UNPUBLISHED', async () => {
        const razao = async (jogo) => ATL1.resolverLink(await codigoDoLembrete(c, jogo.id))
        const comeco = await c.jogo(ORG, g.id, { vagas: 4 })
        c.status(await ORG.avisarPendentes(g.id, comeco.id), 200)
        await c.inicioPara(comeco.id, new Date(Date.now() - 5 * MIN))
        c.igual((await razao(comeco)).corpo.reason, 'GAME_STARTED', 'jogo começou')
        const cancelado = await c.jogo(ORG, g.id, { vagas: 4 })
        c.status(await ORG.avisarPendentes(g.id, cancelado.id), 200)
        c.status(await ORG.cancelarJogo(g.id, cancelado.id), 200)
        c.igual((await razao(cancelado)).corpo.reason, 'GAME_CANCELLED', 'jogo cancelado')
        const volta = await c.jogo(ORG, g.id, { vagas: 4 })
        c.status(await ORG.avisarPendentes(g.id, volta.id), 200)
        await c.sql(`update games set status='DRAFT', published_at=null, version=version+1 where id='${volta.id}'`)
        c.igual((await razao(volta)).corpo.reason, 'GAME_UNPUBLISHED', 'jogo em rascunho')
      })
      await c.passo('código inventado: mensagem genérica (ATTENDANCE_LINK_INVALID_OR_EXPIRED, sem motivo)', async () => {
        const r = await ATL2.resolverLink('x'.repeat(43))
        c.igual([r.status, r.codigo, r.corpo.reason ?? null], [404, 'ATTENDANCE_LINK_INVALID_OR_EXPIRED', null], 'código inventado')
      })
      await c.passo('11 códigos inválidos em 10 minutos: bloqueio temporário (429, ATTENDANCE_LINK_ATTEMPT_LIMIT)', async () => {
        let ultimo
        for (let i = 0; i < 11; i++) ultimo = await ATL2.resolverLink(`${'y'.repeat(40)}${String(100 + i)}`)
        c.afirmar(ultimo.status === 429, `a 11ª tentativa inválida deveria esperar (veio ${ultimo.status})`, ultimo)
        c.igual(ultimo.codigo, 'ATTENDANCE_LINK_ATTEMPT_LIMIT', 'código do bloqueio')
      })
    },
  },
  {
    id: 'LINK-003', feature: 'link', critico: false,
    titulo: 'Abrir o link sem ser membro',
    async rodar(c) {
      const { ORG, ADM, ATL1, NM1, NM2, NM3 } = await c.atores('ORG', 'ADM', 'ATL1', 'NM1', 'NM2', 'NM3')
      const g = await c.passo('ORG cria o grupo e desliga a prioridade; jogo de 4 vagas; ORG manda o lembrete e o link sai', async () => {
        const grupo = await c.grupo(ORG, [ATL1])
        await c.configurar(ORG, grupo.id, { mensalistaPriority: false })
        grupo.jogo = await c.jogo(ORG, grupo.id, { vagas: 4 })
        c.status(await ORG.avisarPendentes(grupo.id, grupo.jogo.id), 200)
        grupo.link = await codigoDoLembrete(c, grupo.jogo.id)
        return grupo
      })
      await c.passo('não membro NM1 abre o link, grupo sem aprovação: entra como atleta avulso (o link vale como convite) e depois responde', async () => {
        const r = c.status(await NM1.entrarPorConvite(g.link), 200)
        c.igual([r.corpo.status, r.corpo.role, r.corpo.groupId], ['JOINED', 'ATHLETE', g.id], 'entrada pelo link')
        c.status(await NM1.vou(g.id, g.jogo.id), 200)
        c.igual(await c.statusDe(g.jogo.id, NM1), 'CONFIRMED', 'NM1 confirmado')
      })
      await c.passo('o organizador troca o código do convite: o lembrete anterior deixa de valer como convite; o lembrete novo vale', async () => {
        c.status(await ORG.trocarConvite(g.id), 200)
        const velho = await NM2.entrarPorConvite(g.link)
        c.afirmar(velho.status >= 400, `lembrete anterior à troca não deve valer como convite (veio ${velho.status})`, velho)
        c.status(await ORG.avisarPendentes(g.id, g.jogo.id), 200)
        const novo = await codigoDoLembrete(c, g.jogo.id)
        c.igual(c.status(await NM2.entrarPorConvite(novo), 200).corpo.status, 'JOINED', 'lembrete posterior à troca vale')
        g.novo = novo
      })
      await c.passo('o organizador desativa o convite: lembretes anteriores não valem; os novos voltam a valer', async () => {
        c.status(await ORG.desativarConvite(g.id), 204)
        const r = await NM3.entrarPorConvite(g.novo)
        c.afirmar(r.status >= 400, `lembrete anterior à desativação não deve valer (veio ${r.status})`, r)
        c.status(await ORG.trocarConvite(g.id), 200)
        c.status(await ORG.avisarPendentes(g.id, g.jogo.id), 200)
        c.igual(c.status(await NM3.entrarPorConvite(await codigoDoLembrete(c, g.jogo.id)), 200).corpo.status, 'JOINED', 'lembrete depois de reativar o convite')
      })
      await c.passo('grupo com aprovação: o link vira pedido de entrada e a presença não é registrada', async () => {
        const gb = await c.grupo(ORG, [], { nome: `QA Aprovação ${c.tag}` })
        await c.configurar(ORG, gb.id, { entryRequiresApproval: true })
        gb.jogo = await c.jogo(ORG, gb.id, { vagas: 4 })
        c.status(await ORG.avisarPendentes(gb.id, gb.jogo.id), 200)
        const { PEND } = await c.atores('PEND')
        c.igual(c.status(await PEND.entrarPorConvite(await codigoDoLembrete(c, gb.jogo.id)), 200).corpo.status, 'PENDING', 'pedido pendente')
        c.igual(await c.statusDe(gb.jogo.id, PEND), null, 'nenhuma presença registrada')
      })
      await c.passo('quem foi removido antes e abre o link: vira pedido de entrada', async () => {
        c.status(await ORG.remover(g.id, NM1.id), [200, 204])
        c.status(await ORG.avisarPendentes(g.id, g.jogo.id), 200)
        c.igual(c.status(await NM1.entrarPorConvite(await codigoDoLembrete(c, g.jogo.id)), 200).corpo.status, 'PENDING', 'removido volta por pedido')
      })
      await c.passo('grupo excluído: o link deixa de valer', async () => {
        c.status(await ORG.excluirGrupo(g.id), 204)
        const r = await NM2.entrarPorConvite(await codigoDoLembrete(c, g.jogo.id))
        c.afirmar(r.status >= 400, `link de grupo excluído deve ser recusado (veio ${r.status})`, r)
      })
    },
  },
  {
    id: 'LINK-004', feature: 'link', critico: false,
    titulo: 'O cadastro de atleta antes da resposta',
    async rodar(c) {
      const { ORG, ATL1, ATL2 } = await c.atores('ORG', 'ATL1', 'ATL2')
      const g = await c.passo('ORG cria o grupo; ATL1 entra pelo convite; jogo publicado e lembrete enviado', async () => {
        const grupo = await c.grupo(ORG, [ATL1])
        grupo.jogo = await c.jogo(ORG, grupo.id, { vagas: 4 })
        c.status(await ORG.avisarPendentes(grupo.id, grupo.jogo.id), 200)
        grupo.link = await codigoDoLembrete(c, grupo.jogo.id)
        return grupo
      })
      await c.passo('membro com cadastro incompleto: o link pede o cadastro antes da resposta (registrationRequired)', async () => {
        c.igual(c.status(await ATL1.resolverLink(g.link), 200).corpo.registrationRequired, true, 'cadastro pendente')
      })
      await c.passo('depois de completar o cadastro de atleta (uma vez por grupo), o link responde direto', async () => {
        c.status(await ATL1.meuPerfil(g.id, { nickname: 'Atl Um', level: 'INTERMEDIARIO' }), 200)
        const r = c.status(await ATL1.resolverLink(g.link), 200).corpo
        c.afirmar(!r.registrationRequired, 'cadastro completo: sem registrationRequired', null)
      })
      await c.passo('grupo com aprovação, pedido pendente: o cadastro vai guardado no pedido e é aplicado na aprovação', async () => {
        const gb = await c.grupo(ORG, [], { nome: `QA Aprovação ${c.tag}` })
        await c.configurar(ORG, gb.id, { entryRequiresApproval: true })
        c.igual(c.status(await ATL2.entrarPorConvite(gb.codigo), 200).corpo.status, 'PENDING', 'pedido')
        c.status(await ATL2.chamar('PUT', `/api/groups/${gb.id}/entry-requests/me/athlete-profile`, { nickname: 'Atl Dois', position: 'CENTRAL', secondaryPosition: null, level: null, preferredSide: null, heightCm: null }), 204)
        c.status(await ORG.aprovarPedido(gb.id, ATL2.id), 200)
        c.igual(c.status(await ORG.atletas(gb.id), 200).corpo.athletes.find((a) => a.userId === ATL2.id).nickname, 'Atl Dois', 'cadastro aplicado na aprovação')
      })
    },
  },
  {
    id: 'LINK-006', feature: 'link', critico: false,
    titulo: 'Link de compartilhar a lista (removido)',
    async rodar(c) {
      const { ORG, ATL1 } = await c.atores('ORG', 'ATL1')
      const g = await c.passo('ORG cria o grupo e o jogo; ATL1 entra', async () => {
        const grupo = await c.grupo(ORG, [ATL1])
        grupo.jogo = await c.jogo(ORG, grupo.id, { vagas: 4 })
        return grupo
      })
      await c.passo('as rotas de gerar o link de compartilhar e de ler a lista não existem mais (404/405)', async () => {
        const gerar = await ORG.chamar('POST', `/api/groups/${g.id}/games/${g.jogo.id}/attendance-link`, {})
        const ler = await ORG.chamar('GET', `/api/groups/${g.id}/games/${g.jogo.id}/attendance-share`)
        c.afirmar([404, 405].includes(gerar.status), `gerar o link deveria sumir (veio ${gerar.status})`, gerar)
        c.afirmar([404, 405].includes(ler.status), `ler a lista compartilhada deveria sumir (veio ${ler.status})`, ler)
      })
      await c.passo('o link do lembrete e a resolução do link continuam funcionando', async () => {
        c.status(await ORG.avisarPendentes(g.id, g.jogo.id), 200)
        c.status(await ATL1.resolverLink(await codigoDoLembrete(c, g.jogo.id)), 200)
      })
    },
  },
]
