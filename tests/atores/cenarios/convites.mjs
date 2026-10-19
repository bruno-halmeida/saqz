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
{
    id: 'CONV-003', feature: 'convites', critico: false,
    titulo: 'Desativar o convite',
    async rodar(c) {
      const { ORG, ADM, ATL1, ATL2, ATL3 } = await c.atores('ORG', 'ADM', 'ATL1', 'ATL2', 'ATL3')
      const g = await c.passo('ORG cria o grupo com aprovação; ADM e ATL1 entram e são aprovados (ADM vira admin); ATL2 pede entrada', async () => {
        const grupo = await c.grupo(ORG, [])
        await c.configurar(ORG, grupo.id, { entryRequiresApproval: true })
        for (const a of [ADM, ATL1]) { await a.entrarPorConvite(grupo.codigo); c.status(await ORG.aprovarPedido(grupo.id, a.id), 200) }
        c.status(await ORG.mudarPapel(grupo.id, ADM.id, 'ADMIN'), 200)
        c.igual((await ATL2.entrarPorConvite(grupo.codigo)).corpo.status, 'PENDING', 'ATL2 pendente')
        return grupo
      })
      await c.passo('ATL1 (atleta) tenta desativar o convite: 403 e o convite segue ativo', async () => {
        c.status(await ATL1.desativarConvite(g.id), 403)
        c.igual((await ORG.convite(g.id)).corpo.active, true, 'convite ativo')
      })
      await c.passo('ADM desativa: o convite deixa de valer, ATL3 recebe "convite inválido" e a tela mostra "sem convite ativo"', async () => {
        c.status(await ADM.desativarConvite(g.id), 204)
        const r = await ATL3.entrarPorConvite(g.codigo)
        c.afirmar(r.status >= 400 && String(r.codigo).startsWith('INVITE_'), 'o código desativado deve ser recusado (INVITE_*)', r)
        c.igual((await ORG.convite(g.id)).corpo.active, false, 'convite inativo')
      })
      await c.passo('reabrir a tela não recria o convite', async () => {
        c.igual((await ADM.convite(g.id)).corpo.active, false, 'segue inativo para ADM')
        c.igual((await ORG.convite(g.id)).corpo.active, false, 'segue inativo para ORG')
      })
      await c.passo('pedido de entrada que já existia continua na lista e quem já é membro não é afetado', async () => {
        c.igual((await ORG.pedidos(g.id)).corpo.map((p) => p.userId), [ATL2.id], 'pedido de ATL2 continua')
        c.status(await ATL1.grupo(g.id), 200)
      })
      await c.passo('"Gerar código" cria um convite novo e ativo; o código desativado continua inválido', async () => {
        const novo = c.status(await ADM.trocarConvite(g.id), 200).corpo
        c.afirmar(novo.code !== g.codigo, 'o código novo deve ser diferente')
        c.igual((await ORG.convite(g.id)).corpo.active, true, 'convite ativo de novo')
        c.afirmar((await ATL3.entrarPorConvite(g.codigo)).status >= 400, 'o código antigo continua inválido')
        c.igual((await ATL3.entrarPorConvite(novo.code)).corpo.status, 'PENDING', 'o novo vale (grupo com aprovação)')
      })
    },
  },
  {
    id: 'CONV-004', feature: 'convites', critico: false,
    titulo: 'A prévia do convite',
    async rodar(c) {
      const { ORG, ATL1, ATL2 } = await c.atores('ORG', 'ATL1', 'ATL2')
      // horário fixo em um dia da semana longe do jogo (que começa daqui a 2 dias), para não conflitar
      const dia = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'][(new Date(Date.now() - 3 * 3600_000 + 5 * 86_400_000)).getUTCDay()]
      const g = await c.passo('ORG cria o grupo com cidade e horário fixo, e um jogo publicado', async () => {
        const grupo = await c.grupo(ORG, [], { extra: { city: 'São Paulo', level: 'INTERMEDIATE', regularSlots: [{ weekday: dia, startTime: '20:00:00', durationMinutes: 120 }] } })
        grupo.jogo = await c.jogo(ORG, grupo.id, { vagas: 4 })
        return grupo
      })
      await c.passo('quem abre o link deslogado vê o cartão: nome, cidade, composição, nível, membros, horários, quem convidou, aprovação e o próximo jogo', async () => {
        const r = c.status(await ATL1.previa(g.codigo, { anonimo: true }), 200).corpo
        c.igual([r.groupName, r.city, r.composition, r.level, r.memberCount, r.entryRequiresApproval], [g.name, 'São Paulo', 'MIXED', 'INTERMEDIATE', 1, false], 'dados do grupo')
        c.igual(r.regularSlots, [{ weekday: dia, startTime: '20:00' }], 'horários fixos')
        c.igual(r.inviterName, ORG.nome, 'quem convidou')
        c.afirmar(r.nextGame?.startsAt && r.nextGame.venueName, 'próximo jogo com dia, hora e local', null)
      })
      await c.passo('grupo com aprovação: o cartão mostra o próximo jogo sem o local nem a quadra', async () => {
        await c.configurar(ORG, g.id, { entryRequiresApproval: true })
        const r = c.status(await ATL1.previa(g.codigo, { anonimo: true }), 200).corpo
        c.igual(r.entryRequiresApproval, true, 'exige aprovação')
        c.afirmar(r.nextGame?.startsAt, 'o dia e a hora aparecem')
        c.igual([r.nextGame.venueName, r.nextGame.court], ['Local após a aprovação', null], 'o local real e a quadra ficam escondidos até a aprovação')
      })
      await c.passo('código inválido: "convite inválido"; link de grupo excluído também', async () => {
        const ruim = await ATL1.previa('ZZZZ-ZZZZ', { anonimo: true })
        c.afirmar(ruim.status >= 400, 'código inexistente deve ser recusado', ruim)
        const { EXTRA } = await c.atores('EXTRA')
        const outro = await c.grupo(EXTRA, [])
        c.status(await EXTRA.chamar('DELETE', `/api/groups/${outro.id}`), 204)
        const apagado = await ATL1.previa(outro.codigo, { anonimo: true })
        c.afirmar(apagado.status >= 400, 'grupo excluído deve ser recusado', apagado)
      })
      await c.passo('11 códigos inválidos de uma conta logada em 10 minutos: bloqueio temporário (429 com espera)', async () => {
        let ultimo
        for (let i = 0; i < 11; i++) ultimo = await ATL2.previa(`QQQQ-${String(1000 + i)}`.replace(/[01]/g, '2'))
        c.afirmar(ultimo.status === 429 || String(ultimo.codigo).includes('ATTEMPT_LIMIT'), `a 11ª tentativa inválida deveria bloquear (veio HTTP ${ultimo.status})`, ultimo)
        c.afirmar(Number(ultimo.headers.get('retry-after')) > 0, 'Retry-After com o tempo de espera', ultimo)
      })
    },
  },
  {
    id: 'CONV-005', feature: 'convites', critico: false,
    titulo: 'Entrar com o convite',
    async rodar(c) {
      const { ORG, ATL1, ATL2, ATL3, ATL4 } = await c.atores('ORG', 'ATL1', 'ATL2', 'ATL3', 'ATL4')
      const g = await c.passo('ORG cria o grupo (sem aprovação)', () => c.grupo(ORG, []))
      await c.passo('logado com convite válido e sem aprovação: entra como atleta avulso; o dono recebe o aviso do primeiro membro (uma vez só)', async () => {
        const r = c.status(await ATL1.entrarPorConvite(g.codigo), 200)
        c.igual([r.corpo.status, r.corpo.role], ['JOINED', 'ATHLETE'], 'entrada')
        c.igual(c.status(await ORG.atletas(g.id), 200).corpo.athletes.find((a) => a.userId === ATL1.id).membershipType, 'AVULSO', 'avulso')
        const primeiro = await c.esperar('aviso de primeiro membro', async () => { const v = (await ORG.avisosDoGrupo(g.id)).filter((n) => n.canal === 'FIRST_MEMBER'); return v.length ? v : null }, { teto: 20_000 })
        c.igual(primeiro.length, 1, 'avisos de primeiro membro')
        c.status(await ATL2.entrarPorConvite(g.codigo), 200)
        await c.dormir(1500)
        c.igual((await ORG.avisosDoGrupo(g.id)).filter((n) => n.canal === 'FIRST_MEMBER').length, 1, 'o segundo membro não gera outro aviso')
      })
      await c.passo('quem já é membro usa o convite de novo: nada acontece (continua uma participação)', async () => {
        c.status(await ATL1.entrarPorConvite(g.codigo), 200)
        c.igual(await c.valor(`select count(*) from group_memberships where group_id='${g.id}' and user_id='${ATL1.id}'`), '1', 'participações')
      })
      await c.passo('o dono usando o próprio convite não vira outra coisa', async () => {
        const r = await ORG.entrarPorConvite(g.codigo)
        c.afirmar(r.status === 200, `dono com o próprio convite: sucesso sem efeito (veio ${r.status})`, r)
        c.igual(c.status(await ORG.membros(g.id), 200).corpo.find((m) => m.userId === ORG.id).role, 'OWNER', 'papel do dono')
      })
      await c.passo('grupo com aprovação: o convite vira pedido pendente', async () => {
        const gb = await c.grupo(ORG, [], { nome: `QA Aprovação ${c.tag}` })
        await c.configurar(ORG, gb.id, { entryRequiresApproval: true })
        c.igual((await ATL3.entrarPorConvite(gb.codigo)).corpo.status, 'PENDING', 'pedido pendente')
      })
      await c.passo('grupo excluído: "grupo excluído" (INVITE_GROUP_DELETED); código inexistente: INVITE_*', async () => {
        const gd = await c.grupo(ORG, [], { nome: `QA Excluído ${c.tag}` })
        c.status(await ORG.chamar('DELETE', `/api/groups/${gd.id}`), 204)
        const r = await ATL4.entrarPorConvite(gd.codigo)
        c.afirmar(r.status >= 400 && String(r.codigo).startsWith('INVITE_'), 'grupo excluído deve ser recusado (INVITE_*)', r)
        const ruim = await ATL4.entrarPorConvite('ZZZZ-ZZZZ')
        c.afirmar(ruim.status >= 400 && String(ruim.codigo).startsWith('INVITE_'), 'código inexistente deve ser recusado (INVITE_*)', ruim)
      })
      await c.passo('plano do organizador vencido: o convite novo é bloqueado e ninguém entra', async () => {
        const gf = await c.grupo(ORG, [], { nome: `QA Congelado ${c.tag}` })
        await c.encerrarTeste(ORG)
        const r = await ATL4.entrarPorConvite(gf.codigo)
        c.afirmar([402, 403, 409].includes(r.status), `convite em grupo com plano vencido deve ser bloqueado (veio ${r.status})`, r)
        c.igual(await c.valor(`select count(*) from group_memberships where group_id='${gf.id}' and user_id='${ATL4.id}'`), '0', 'ATL4 fora')
        await c.reabrirTeste(ORG)
      })
      await c.passo('10 códigos inválidos seguidos: a 11ª tentativa espera (INVITE_ATTEMPT_LIMIT, 429)', async () => {
        let ultimo
        for (let i = 0; i < 11; i++) ultimo = await ATL4.entrarPorConvite(`WWWW-${2000 + i}`)
        c.afirmar(ultimo.status === 429 || ultimo.codigo === 'INVITE_ATTEMPT_LIMIT', `a 11ª tentativa inválida deveria esperar (veio HTTP ${ultimo.status} ${ultimo.codigo})`, ultimo)
      })
    },
  },
  {
    id: 'CONV-006', feature: 'convites', critico: false,
    titulo: 'O pedido de entrada',
    async rodar(c) {
      const { ORG, ADM, ATL1, ATL2 } = await c.atores('ORG', 'ADM', 'ATL1', 'ATL2')
      const g = await c.passo('ORG cria o grupo com aprovação; ADM entra, é aprovado e vira admin', async () => {
        const grupo = await c.grupo(ORG, [])
        await c.configurar(ORG, grupo.id, { entryRequiresApproval: true })
        await ADM.entrarPorConvite(grupo.codigo); c.status(await ORG.aprovarPedido(grupo.id, ADM.id), 200)
        c.status(await ORG.mudarPapel(grupo.id, ADM.id, 'ADMIN'), 200)
        return grupo
      })
      const pedidos = async (a) => (await a.avisosDoGrupo(g.id)).filter((n) => n.canal === 'ENTRY_REQUEST' && n.corpo.includes(ATL1.nome))
      await c.passo('ATL1 usa o convite: vira pedido pendente e preenche o cadastro de atleta; dono e admin recebem um aviso cada', async () => {
        c.igual((await ATL1.entrarPorConvite(g.codigo)).corpo.status, 'PENDING', 'pedido')
        c.status(await ATL1.chamar('PUT', `/api/groups/${g.id}/entry-requests/me/athlete-profile`, { nickname: 'Pedro', position: 'PONTA', secondaryPosition: null, level: 'INICIANTE', preferredSide: null, heightCm: 180 }), 204)
        for (const a of [ORG, ADM]) {
          const x = await c.esperar(`aviso de pedido de ${a.papel}`, async () => { const v = await pedidos(a); return v.length ? v : null }, { teto: 20_000 })
          c.igual(x.length, 1, `${a.papel}: avisos de pedido`)
          c.afirmar(x[0].corpo.includes(ATL1.nome.split(' ').slice(0, 2).join(' ')) || x[0].corpo.includes('pediu para entrar'), `texto do aviso: ${x[0].corpo}`)
        }
      })
      await c.passo('pedir de novo enquanto pendente: mesmo pedido e nenhum aviso novo', async () => {
        c.igual((await ATL1.entrarPorConvite(g.codigo)).corpo.status, 'PENDING', 'segue pendente')
        await c.dormir(1500)
        c.igual(await c.valor(`select count(*) from group_entry_requests where group_id='${g.id}' and user_id='${ATL1.id}'`), '1', 'pedidos de ATL1')
        c.igual((await pedidos(ORG)).length, 1, 'avisos do dono')
      })
      await c.passo('a lista do organizador mostra nome e hora do pedido; a pessoa vê o próprio pedido ("aguardando aprovação") mas nada do grupo', async () => {
        const lista = c.status(await ORG.pedidos(g.id), 200).corpo
        c.igual(lista.map((p) => [p.userId, p.displayName]), [[ATL1.id, ATL1.nome]], 'pedido na lista')
        c.afirmar(!Number.isNaN(Date.parse(lista[0].requestedAt)), 'hora do pedido')
        const meu = c.status(await ATL1.meuPedido(g.id), 200).corpo
        c.igual([meu.groupName, meu.athleteProfile.nickname], [g.name, 'Pedro'], 'meu pedido e cadastro guardado')
        c.status(await ATL1.grupo(g.id), [403, 404])
      })
      await c.passo('a pessoa cancela o próprio pedido: pedido e cadastro guardado são apagados; cancelar de novo devolve 204', async () => {
        c.status(await ATL1.cancelarMeuPedido(g.id), 204)
        c.igual(await c.valor(`select count(*) from group_entry_requests where group_id='${g.id}' and user_id='${ATL1.id}'`), '0', 'pedido apagado')
        c.status(await ATL1.meuPedido(g.id), 404, 'ENTRY_REQUEST_NOT_FOUND')
        c.status(await ATL1.cancelarMeuPedido(g.id), 204)
      })
      await c.passo('aprovar um pedido cancelado: "pedido não encontrado"', async () => {
        c.status(await ORG.aprovarPedido(g.id, ATL1.id), 404, 'ENTRY_REQUEST_NOT_FOUND')
      })
    },
  },
  {
    id: 'CONV-008', feature: 'convites', critico: false,
    titulo: 'Recusar o pedido',
    async rodar(c) {
      const { ORG, ADM, ATL1, ATL2 } = await c.atores('ORG', 'ADM', 'ATL1', 'ATL2')
      const g = await c.passo('ORG cria o grupo com aprovação; ADM entra, é aprovado e vira admin; ATL1 pede entrada', async () => {
        const grupo = await c.grupo(ORG, [])
        await c.configurar(ORG, grupo.id, { entryRequiresApproval: true })
        await ADM.entrarPorConvite(grupo.codigo); c.status(await ORG.aprovarPedido(grupo.id, ADM.id), 200)
        c.status(await ORG.mudarPapel(grupo.id, ADM.id, 'ADMIN'), 200)
        c.igual((await ATL1.entrarPorConvite(grupo.codigo)).corpo.status, 'PENDING', 'ATL1 pendente')
        return grupo
      })
      await c.passo('ATL2 (sem papel de gestão) tenta recusar o pedido de ATL1: 403/404 e o pedido segue', async () => {
        const r = await ATL2.recusarPedido(g.id, ATL1.id)
        c.afirmar([403, 404].includes(r.status), `atleta não recusa (veio ${r.status})`, r)
        c.igual(await c.valor(`select count(*) from group_entry_requests where group_id='${g.id}' and user_id='${ATL1.id}'`), '1', 'pedido de ATL1')
      })
      await c.passo('ADM recusa: o pedido e o cadastro guardado são apagados; recusar de novo (pedido que sumiu) é sucesso sem efeito', async () => {
        c.status(await ADM.recusarPedido(g.id, ATL1.id), 204)
        c.igual(await c.valor(`select count(*) from group_entry_requests where group_id='${g.id}' and user_id='${ATL1.id}'`), '0', 'pedido apagado')
        c.status(await ADM.recusarPedido(g.id, ATL1.id), 204)
      })
      await c.passo('a pessoa recusada não recebe aviso e o cartão do pedido some', async () => {
        c.status(await ATL1.meuPedido(g.id), 404, 'ENTRY_REQUEST_NOT_FOUND')
        c.igual((await ATL1.avisosDoGrupo(g.id)).length, 0, 'avisos de ATL1')
      })
      await c.passo('a pessoa recusada pede de novo na hora: novo pedido e novo aviso ao organizador', async () => {
        const antes = (await ORG.avisosDoGrupo(g.id)).filter((n) => n.canal === 'ENTRY_REQUEST' && n.corpo.includes(ATL1.nome)).length
        c.igual((await ATL1.entrarPorConvite(g.codigo)).corpo.status, 'PENDING', 'novo pedido')
        await c.esperar('novo aviso de pedido', async () => (await ORG.avisosDoGrupo(g.id)).filter((n) => n.canal === 'ENTRY_REQUEST' && n.corpo.includes(ATL1.nome)).length > antes, { teto: 20_000 })
      })
    },
  },
]
