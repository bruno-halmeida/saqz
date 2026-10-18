// Convites e entrada no grupo (CONV-*).
const ALFABETO = /^[A-HJKMNP-Z2-9]{4}-[A-HJKMNP-Z2-9]{4}$/  // sem 0, O, 1, I, L

export default [
  {
    id: 'CONV-001', feature: 'convites', critico: true,
    titulo: 'O convite do grupo',
    async rodar(c) {
      const { ORG, ADM, ATL1, ATL2 } = await c.atores('ORG', 'ADM', 'ATL1', 'ATL2')
      const g = await c.passo('ORG cria o grupo; ADM e ATL1 entram pelo convite; ADM vira administrador', async () => {
        const grupo = await c.grupo(ORG, [ADM, ATL1])
        c.status(await ORG.mudarPapel(grupo.id, ADM.id, 'ADMIN'), 200)
        return grupo
      })
      const codigo = await c.passo('ORG abre o convite pela primeira vez: código curto de 8 caracteres, sem 0/O/1/I/L, sem prazo', async () => {
        const r = c.status(await ORG.convite(g.id), 200)
        c.afirmar(ALFABETO.test(r.corpo.code), `código fora do formato XXXX-XXXX: ${r.corpo.code}`, r)
        c.igual(r.corpo.active, true, 'convite ativo')
        c.igual(r.corpo.expiresAt ?? null, null, 'convite não expira')
        c.afirmar(r.corpo.inviteUrl.includes('saqz_invite='), 'link do convite', r)
        return r.corpo
      })
      await c.passo('ADM (outro aparelho) vê o mesmo código e o mesmo link', async () => {
        const r = c.status(await ADM.convite(g.id), 200)
        c.igual(r.corpo.code, codigo.code, 'código')
        c.igual(r.corpo.inviteUrl, codigo.inviteUrl, 'link')
      })
      await c.passo('ATL1 (atleta) tenta ler o convite: 403', async () => {
        c.status(await ATL1.convite(g.id), 403)
      })
      await c.passo('ATL2 digita o código em minúscula e sem hífen: é aceito e vira atleta', async () => {
        const digitado = codigo.code.replace('-', '').toLowerCase()
        const r = c.status(await ATL2.entrarPorConvite(digitado), 200)
        c.igual(r.corpo.status, 'JOINED', 'entrada')
        c.igual(r.corpo.role, 'ATHLETE', 'papel')
      })
    },
  },
  {
    id: 'CONV-002', feature: 'convites', critico: true,
    titulo: 'Trocar o código',
    async rodar(c) {
      const { ORG, ADM, ATL1, ATL2, ATL3 } = await c.atores('ORG', 'ADM', 'ATL1', 'ATL2', 'ATL3')
      const g = await c.passo('ORG cria o grupo; ADM e ATL1 entram; ADM vira administrador', async () => {
        const grupo = await c.grupo(ORG, [ADM, ATL1])
        c.status(await ORG.mudarPapel(grupo.id, ADM.id, 'ADMIN'), 200)
        return grupo
      })
      const antigo = g.codigo
      const novo = await c.passo('ADM troca o código: vem código e link novos, sem prazo', async () => {
        const r = c.status(await ADM.trocarConvite(g.id), 200)
        c.afirmar(r.corpo.code !== antigo, 'o código novo deve ser diferente do antigo', r)
        c.igual(r.corpo.expiresAt ?? null, null, 'sem prazo')
        return r.corpo
      })
      await c.passo('ATL2 abre o código antigo: convite inválido; o código novo funciona', async () => {
        const velho = await ATL2.entrarPorConvite(antigo)
        c.afirmar(velho.status >= 400 && String(velho.codigo).startsWith('INVITE_'), 'o código antigo deve ser recusado (INVITE_*)', velho)
        c.igual(await c.valor(`select count(*) from group_memberships where group_id='${g.id}' and user_id='${ATL2.id}'`), '0', 'ATL2 não entrou com o código velho')
        c.igual((await ATL2.entrarPorConvite(novo.code)).status, 200, 'código novo')
      })
      await c.passo('ORG (outro admin, outro aparelho) lê o convite depois: vê o código novo', async () => {
        const r = c.status(await ORG.convite(g.id), 200)
        c.igual(r.corpo.code, novo.code, 'código visto pelo dono')
        c.igual(r.corpo.revision, novo.revision, 'revisão')
      })
      await c.passo('ATL1 (atleta) tenta trocar o código: 403 e o código não muda', async () => {
        c.status(await ATL1.trocarConvite(g.id), 403)
        c.igual((await ORG.convite(g.id)).corpo.code, novo.code, 'código segue o mesmo')
      })
      await c.passo('trocas seguidas: o código de cada troca invalida o anterior (sem limite de trocas)', async () => {
        const a = (await ORG.trocarConvite(g.id)).corpo.code
        const b = (await ORG.trocarConvite(g.id)).corpo.code
        c.afirmar(a !== b && b !== novo.code, 'códigos diferentes a cada troca')
        c.afirmar((await ATL3.entrarPorConvite(a)).status >= 400, 'o código intermediário não vale mais')
        c.igual((await ATL3.entrarPorConvite(b)).status, 200, 'o último vale')
      })
    },
  },
  {
    id: 'CONV-007', feature: 'convites', critico: true,
    titulo: 'Aprovar o pedido de entrada',
    async rodar(c) {
      const { ORG, ADM, ATL1, ATL2, ATL3 } = await c.atores('ORG', 'ADM', 'ATL1', 'ATL2', 'ATL3')
      const g = await c.passo('ORG cria o grupo, liga "entrada com aprovação"; ADM entra e vira administrador', async () => {
        const grupo = await c.grupo(ORG, [])
        await c.configurar(ORG, grupo.id, { entryRequiresApproval: true })
        c.igual((await ADM.entrarPorConvite(grupo.codigo)).corpo.status, 'PENDING', 'ADM fica pendente')
        c.status(await ORG.aprovarPedido(grupo.id, ADM.id), 200)
        c.status(await ORG.mudarPapel(grupo.id, ADM.id, 'ADMIN'), 200)
        return grupo
      })
      await c.passo('ATL1 e ATL2 abrem o convite: ficam como pedido pendente; ATL1 preenche o apelido do cadastro de atleta', async () => {
        for (const a of [ATL1, ATL2]) c.igual((await a.entrarPorConvite(g.codigo)).corpo.status, 'PENDING', `${a.papel} pendente`)
        c.status(await ATL1.chamar('PUT', `/api/groups/${g.id}/entry-requests/me/athlete-profile`, { nickname: 'Apelido1', position: 'LEVANTADOR', secondaryPosition: null, level: null, preferredSide: null, heightCm: null }), 204)
        const lista = c.status(await ORG.pedidos(g.id), 200)
        c.igual(lista.corpo.map((p) => p.userId).sort(), [ATL1.id, ATL2.id].sort(), 'pedidos na lista do dono')
      })
      await c.passo('ATL2 (pendente, sem papel de gestão) tenta aprovar o pedido de ATL1: 403/404 e nada muda', async () => {
        const r = await ATL2.aprovarPedido(g.id, ATL1.id)
        c.afirmar([403, 404].includes(r.status), `atleta não aprova (veio ${r.status})`, r)
        c.igual(await c.valor(`select count(*) from group_memberships where group_id='${g.id}' and user_id='${ATL1.id}'`), '0', 'ATL1 ainda fora')
      })
      await c.passo('ADM aprova o pedido de ATL1: vira atleta, cadastro aplicado, pedido sai da lista', async () => {
        const r = c.status(await ADM.aprovarPedido(g.id, ATL1.id), 200)
        c.igual(r.corpo.role, 'ATHLETE', 'papel')
        const atletas = c.status(await ORG.atletas(g.id), 200)
        const ele = atletas.corpo.athletes.find((a) => a.userId === ATL1.id)
        c.afirmar(ele, 'ATL1 deve aparecer na lista de atletas', atletas)
        c.igual(ele.nickname, 'Apelido1', 'cadastro de atleta aplicado')
        c.igual(ele.membershipType, 'AVULSO', 'entra como avulso')
        c.igual((await ORG.pedidos(g.id)).corpo.map((p) => p.userId), [ATL2.id], 'só ATL2 segue pendente')
        c.status(await ATL1.chamar('GET', `/api/groups/${g.id}/entry-requests/me`), 404, 'ENTRY_REQUEST_NOT_FOUND')
      })
      await c.passo('aprovar de novo quem já é membro (ATL1) é sucesso sem efeito; aprovar pedido que nunca existiu (ATL3) é "pedido não encontrado"', async () => {
        c.status(await ORG.aprovarPedido(g.id, ATL1.id), 200)
        c.igual(await c.valor(`select count(*) from group_memberships where group_id='${g.id}' and user_id='${ATL1.id}'`), '1', 'ATL1 com uma só participação')
        c.status(await ORG.aprovarPedido(g.id, ATL3.id), 404, 'ENTRY_REQUEST_NOT_FOUND')
      })
      await c.passo('ATL2 já virou membro por outro caminho (aprovação desligada e convite de novo) e o dono aprova o pedido velho: sucesso sem efeito', async () => {
        await c.configurar(ORG, g.id, { entryRequiresApproval: false })
        const entrou = await ATL2.entrarPorConvite(g.codigo)
        c.igual(entrou.corpo.status, 'JOINED', 'ATL2 entrou direto')
        const aprovar = await ORG.aprovarPedido(g.id, ATL2.id)
        c.afirmar([200, 404].includes(aprovar.status), `aprovação de quem já é membro: 200 (sem efeito) ou 404 se o pedido já foi apagado; veio ${aprovar.status}`, aprovar)
        c.igual(await c.valor(`select count(*) from group_memberships where group_id='${g.id}' and user_id='${ATL2.id}'`), '1', 'uma só participação')
      })
      await c.passo('plano do dono vencido: ATL3 pede entrada (aprovação ligada) e ORG tenta aprovar: bloqueado (SUBSCRIPTION_REQUIRED); com o plano de volta, aprova', async () => {
        await c.configurar(ORG, g.id, { entryRequiresApproval: true })
        c.igual((await ATL3.entrarPorConvite(g.codigo)).corpo.status, 'PENDING', 'ATL3 pendente')
        await c.encerrarTeste(ORG)
        c.status(await ORG.aprovarPedido(g.id, ATL3.id), 403, 'SUBSCRIPTION_REQUIRED')
        c.igual(await c.valor(`select count(*) from group_memberships where group_id='${g.id}' and user_id='${ATL3.id}'`), '0', 'ATL3 segue fora')
        await c.reabrirTeste(ORG)
        c.status(await ORG.aprovarPedido(g.id, ATL3.id), 200)
      })
    },
  },
]
