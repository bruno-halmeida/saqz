// Membros e papéis (MEM-*).
import { randomUUID } from 'node:crypto'

export default [
  {
    id: 'MEM-001', feature: 'membros', critico: false,
    titulo: 'Os três papéis e como se muda de papel',
    async rodar(c) {
      const { ORG, ADM, ATL1, ATL2, FORA } = await c.atores('ORG', 'ADM', 'ATL1', 'ATL2', 'FORA')
      const g = await c.passo('ORG cria o grupo; ADM, ATL1 e ATL2 entram', () => c.grupo(ORG, [ADM, ATL1, ATL2]))
      await c.passo('dono promove ATL1 a admin; repetir não faz nada e não dá erro', async () => {
        c.igual(c.status(await ORG.mudarPapel(g.id, ADM.id, 'ADMIN'), 200).corpo.role, 'ADMIN', 'ADM promovido')
        c.igual(c.status(await ORG.mudarPapel(g.id, ATL1.id, 'ADMIN'), 200).corpo.role, 'ADMIN', 'ATL1 promovido')
        c.igual(c.status(await ORG.mudarPapel(g.id, ATL1.id, 'ADMIN'), 200).corpo.role, 'ADMIN', 'promover quem já é admin')
        c.igual(c.status(await ORG.mudarPapel(g.id, ATL2.id, 'ATHLETE'), 200).corpo.role, 'ATHLETE', 'rebaixar quem já é atleta')
      })
      await c.passo('o promovido recebe um aviso ("Você agora é administrador de <grupo>"); quem é rebaixado não recebe', async () => {
        const aviso = async (a) => (await a.avisosDoGrupo(g.id)).filter((n) => /administrador/i.test(n.corpo))
        const x = await c.esperar('aviso de promoção a admin', async () => { const v = await aviso(ATL1); return v.length ? v : null }, { teto: 15_000 }).catch(() => [])
        c.divergir(x.length === 1, `ATL1 deveria receber um aviso "Você agora é administrador de <grupo>" ao ser promovido (recebeu ${x.length}) — DEB-060, alvo`)
        c.status(await ORG.mudarPapel(g.id, ATL1.id, 'ATHLETE'), 200)
        await c.dormir(1500)
        c.igual((await ATL1.avisosDoGrupo(g.id)).filter((n) => /rebaixad|deixou de ser/i.test(n.corpo)).length, 0, 'rebaixado não recebe aviso')
      })
      await c.passo('admin não muda papel de ninguém (403), nem o próprio; atleta também não', async () => {
        c.status(await ADM.mudarPapel(g.id, ATL2.id, 'ADMIN'), 403)
        c.status(await ADM.mudarPapel(g.id, ADM.id, 'ATHLETE'), 403)
        c.status(await ATL2.mudarPapel(g.id, ATL2.id, 'ADMIN'), 403)
        c.igual(c.status(await ORG.membros(g.id), 200).corpo.find((m) => m.userId === ATL2.id).role, 'ATHLETE', 'ATL2 segue atleta')
      })
      await c.passo('o dono nunca muda de papel (dono imutável)', async () => {
        c.status(await ORG.mudarPapel(g.id, ORG.id, 'ATHLETE'), 403)
        c.status(await ORG.mudarPapel(g.id, ORG.id, 'ADMIN'), 403)
        c.igual(c.status(await ORG.membros(g.id), 200).corpo.find((m) => m.userId === ORG.id).role, 'OWNER', 'dono segue dono')
      })
      await c.passo('o atleta vê o grupo e a lista de membros mas não gerencia: convite, atletas e financeiro dão 403', async () => {
        c.status(await ATL2.grupo(g.id), 200)
        c.status(await ATL2.atletas(g.id), 200)
        c.status(await ATL2.membros(g.id), 403)  // a lista de papéis (GET /memberships) é da gestão; o atleta usa a lista de atletas
        c.status(await ATL2.convite(g.id), 403)
        c.status(await ATL2.cobrancas(g.id), 403)
        c.status(await ATL2.pedidos(g.id), 403)
      })
      await c.passo('admin gerencia: convite, financeiro e atletas funcionam para ADM', async () => {
        c.status(await ADM.convite(g.id), 200)
        c.status(await ADM.cobrancas(g.id), 200)
        c.status(await ADM.atletas(g.id), 200)
      })
      await c.passo('quem não é membro não sabe que o grupo existe: 404 para dono, admin e atleta', async () => {
        c.status(await FORA.grupo(g.id), 404)
        c.status(await FORA.membros(g.id), 404)
        c.status(await FORA.convite(g.id), [403, 404])
      })
    },
  },
  {
    id: 'MEM-002', feature: 'membros', critico: false,
    titulo: 'A lista de membros',
    async rodar(c) {
      const { ORG, ADM, ATL1, ATL2, SAIU, FORA } = await c.atores('ORG', 'ADM', 'ATL1', 'ATL2', 'SAIU', 'FORA')
      const g = await c.passo('ORG cria o grupo, ADM vira admin; ATL1 vira mensalista (R$ 50, dia 10); SAIU entra e sai', async () => {
        const grupo = await c.grupo(ORG, [ADM, ATL1, ATL2, SAIU])
        c.status(await ORG.mudarPapel(grupo.id, ADM.id, 'ADMIN'), 200)
        await c.tipo(ORG, grupo.id, ATL1)
        c.status(await SAIU.sairDoGrupo(grupo.id), 204)
        return grupo
      })
      await c.passo('atleta vê a lista sem situação financeira ("desconhecido") nem valor de mensalidade', async () => {
        const lista = c.status(await ATL2.atletas(g.id), 200).corpo.athletes
        const ele = lista.find((a) => a.userId === ATL1.id)
        c.afirmar(ele, 'ATL1 deve estar na lista')
        c.igual([ele.membershipType, ele.monthlyFeeCents ?? null, ele.monthlyDueDay ?? null], ['MENSALISTA', null, null], 'atleta não vê valor e dia da mensalidade')
        c.afirmar(/DESCONHECIDO/i.test(ele.financialStatus), `situação financeira para atleta: ${ele.financialStatus}`)
      })
      await c.passo('atleta filtra por situação financeira: 403', async () => {
        c.status(await ATL2.atletasComConsulta(g.id, '?financialStatus=PENDENTE'), 403)
      })
      await c.passo('admin e dono veem tudo: situação financeira e valor e dia da mensalidade', async () => {
        for (const o of [ORG, ADM]) {
          const ele = c.status(await o.atletas(g.id), 200).corpo.athletes.find((a) => a.userId === ATL1.id)
          c.igual([ele.monthlyFeeCents, ele.monthlyDueDay], [5000, 10], `${o.papel}: mensalidade`)
          c.afirmar(!/DESCONHECIDO/i.test(ele.financialStatus), `${o.papel}: situação financeira real (${ele.financialStatus})`)
        }
        c.status(await ADM.atletasComConsulta(g.id, '?financialStatus=PENDENTE'), 200)
      })
      await c.passo('quem saiu não aparece para ninguém; quem o organizador inativou só aparece com includeInactive, e só para o organizador', async () => {
        const ids = (r) => r.corpo.athletes.map((a) => a.userId)
        c.afirmar(!ids(await ORG.atletas(g.id)).includes(SAIU.id), 'SAIU (saiu do grupo) não aparece nem para o dono com inativos')
        c.status(await ORG.ajustarAtleta(g.id, ATL2.id, { membershipType: 'AVULSO', active: false }), 200)
        c.afirmar(!ids(await ORG.chamar('GET', `/api/groups/${g.id}/athletes`)).includes(ATL2.id), 'ATL2 inativo não aparece na lista comum')
        c.afirmar(ids(await ORG.atletas(g.id)).includes(ATL2.id), 'o dono vê o inativo com includeInactive=true')
        const r = await ATL1.atletas(g.id)
        c.divergir(r.status === 403 || (r.status === 200 && !ids(r).includes(ATL2.id)), `atleta pedindo inativos deve ser recusado ou ignorado (veio ${r.status}${r.status === 200 && ids(r).includes(ATL2.id) ? ' e a lista MOSTRA o inativo' : ''}) — DEB-061`)
        c.status(await ORG.ajustarAtleta(g.id, ATL2.id, { membershipType: 'AVULSO', active: true }), 200)
      })
      await c.passo('busca por nome e filtro por tipo', async () => {
        const porNome = c.status(await ATL2.atletasComConsulta(g.id, `?search=${encodeURIComponent('ATL1')}`), 200).corpo.athletes
        c.igual(porNome.map((a) => a.userId), [ATL1.id], 'busca por "ATL1"')
        const mensalistas = c.status(await ATL2.atletasComConsulta(g.id, '?type=MENSALISTA'), 200).corpo.athletes
        c.igual(mensalistas.map((a) => a.userId), [ATL1.id], 'filtro por tipo')
      })
      await c.passo('quem não é membro: 404', async () => { c.status(await FORA.atletas(g.id), 404) })
      await c.passo('a mesma pessoa em vários grupos tem papel e tipo independentes', async () => {
        const g2 = await c.grupo(ATL1, [ORG], { nome: `QA Outro grupo ${c.tag}` })
        const papel = async (viewer, grupo) => c.status(await viewer.membros(grupo), 200).corpo.find((m) => m.userId === ATL1.id).role
        c.igual([await papel(ORG, g.id), await papel(ATL1, g2.id)], ['ATHLETE', 'OWNER'], 'papel de ATL1 em cada grupo')
        c.igual(c.status(await ATL1.atletas(g2.id), 200).corpo.athletes.find((a) => a.userId === ATL1.id).membershipType, 'AVULSO', 'tipo em G2 independe do tipo em G1')
      })
    },
  },
  {
    id: 'MEM-003', feature: 'membros', critico: false,
    titulo: 'Mensalista ou avulso',
    async rodar(c) {
      const { ORG, ATL1, ATL2 } = await c.atores('ORG', 'ATL1', 'ATL2')
      const g = await c.passo('ORG cria o grupo sem mensalidade padrão; ATL1 e ATL2 entram como avulsos', async () => {
        const grupo = await c.grupo(ORG, [ATL1, ATL2])
        return grupo
      })
      const tipoDe = async (a) => (await ORG.atletas(g.id)).corpo.athletes.find((x) => x.userId === a.id)
      await c.passo('quem entra começa como avulso', async () => { c.igual((await tipoDe(ATL1)).membershipType, 'AVULSO', 'tipo inicial') })
      await c.passo('virar mensalista sem valor, num grupo sem padrão: recusado', async () => {
        const r = await ORG.ajustarAtleta(g.id, ATL1.id, { membershipType: 'MENSALISTA' })
        c.status(r, [400, 422])
        c.igual((await tipoDe(ATL1)).membershipType, 'AVULSO', 'segue avulso')
      })
      await c.passo('virar mensalista com valor e dia: passa a ser mensalista, com valor e dia próprios (desconto por pessoa)', async () => {
        const r = c.status(await ORG.ajustarAtleta(g.id, ATL1.id, { membershipType: 'MENSALISTA', monthlyFeeCents: 4500, monthlyDueDay: 5 }), 200)
        c.igual([r.corpo.membershipType, r.corpo.monthlyFeeCents, r.corpo.monthlyDueDay], ['MENSALISTA', 4500, 5], 'mensalista com desconto')
      })
      await c.passo('grupo com mensalidade padrão: virar mensalista sem valor usa o padrão do grupo', async () => {
        await c.configurar(ORG, g.id, { monthlyFeeCents: 7000, monthlyDueDay: 12 })
        const r = c.status(await ORG.ajustarAtleta(g.id, ATL2.id, { membershipType: 'MENSALISTA' }), 200)
        c.igual(r.corpo.membershipType, 'MENSALISTA', 'ATL2 mensalista')
        const ele = await tipoDe(ATL2)
        c.igual([ele.monthlyFeeCents ?? 7000, ele.monthlyDueDay ?? 12], [7000, 12], 'valor e dia do padrão (ou herdados do grupo)')
      })
      await c.passo('mensalista vira avulso: perde valor e dia, e a mensalidade pendente continua pendente', async () => {
        const mes = new Date().toISOString().slice(0, 7)
        c.status(await ORG.chamar('POST', `/api/groups/${g.id}/charges/monthly`, { requestId: randomUUID(), month: mes, amountCents: 4500, dueDate: `${mes}-28`, memberIds: [ATL1.id] }), 200)
        const r = c.status(await ORG.ajustarAtleta(g.id, ATL1.id, { membershipType: 'AVULSO' }), 200)
        c.igual([r.corpo.membershipType, r.corpo.monthlyFeeCents ?? null, r.corpo.monthlyDueDay ?? null], ['AVULSO', null, null], 'avulso sem valor nem dia')
        const mensal = (await ORG.cobrancas(g.id)).corpo.charges.find((x) => x.kind === 'MONTHLY' && x.memberId === ATL1.id)
        c.igual(mensal.status, 'PENDING', 'a mensalidade pendente não foi mexida')
      })
      await c.passo('passa a pagar diária nos próximos jogos', async () => {
        await c.configurar(ORG, g.id, { mensalistaPriority: false })
        const j = await c.jogo(ORG, g.id, { vagas: 4, valorCents: 2000 })
        c.status(await ATL1.vou(g.id, j.id), 200)
        c.igual((await ORG.cobrancas(g.id)).corpo.charges.filter((x) => x.gameId === j.id && x.memberId === ATL1.id).length, 1, 'diária de ATL1')
      })
      await c.passo('atleta muda o próprio tipo: 403 e nada muda', async () => {
        c.status(await ATL2.ajustarAtleta(g.id, ATL2.id, { membershipType: 'AVULSO' }), 403)
        c.igual((await tipoDe(ATL2)).membershipType, 'MENSALISTA', 'ATL2 segue mensalista')
      })
    },
  },
  {
    id: 'MEM-004', feature: 'membros', critico: false,
    titulo: 'O perfil de atleta',
    async rodar(c) {
      const { ORG, ATL1, ATL2 } = await c.atores('ORG', 'ATL1', 'ATL2')
      const quadra = await c.passo('ORG cria um grupo de quadra e um de praia; ATL1 e ATL2 entram nos dois', async () => {
        const gq = await c.grupo(ORG, [ATL1, ATL2], { nome: `QA Quadra ${c.tag}` })
        const gp = await c.grupo(ORG, [ATL1, ATL2], { nome: `QA Praia ${c.tag}`, extra: { modality: 'BEACH_VOLLEYBALL' } })
        return { gq, gp }
      })
      const { gq, gp } = quadra
      await c.passo('quadra: posição líbero e altura 180 são aceitas; perfil vazio também', async () => {
        c.status(await ATL1.meuPerfil(gq.id, { nickname: 'Bia', position: 'LIBERO', level: 'INTERMEDIARIO', heightCm: 180 }), 200)
        c.status(await ATL2.meuPerfil(gq.id, {}), 200)
        const eu = c.status(await ATL1.atletas(gq.id), 200).corpo.athletes.find((a) => a.userId === ATL1.id)
        c.igual([eu.nickname, eu.position, eu.level, eu.heightCm], ['Bia', 'LIBERO', 'INTERMEDIARIO', 180], 'perfil gravado')
      })
      await c.passo('quadra: lado preferido, posição secundária igual à principal e altura 99 ou 251 são recusados', async () => {
        c.status(await ATL1.meuPerfil(gq.id, { preferredSide: 'ESQUERDA' }), [400, 422])
        c.status(await ATL1.meuPerfil(gq.id, { position: 'PONTA', secondaryPosition: 'PONTA' }), [400, 422])
        for (const h of [99, 251]) c.status(await ATL1.meuPerfil(gq.id, { heightCm: h }), [400, 422])
      })
      await c.passo('apelido com 1 caractere ou com espaço na ponta é recusado', async () => {
        for (const ruim of ['B', ' Bia', 'Bia ']) c.status(await ATL1.meuPerfil(gq.id, { nickname: ruim }), [400, 422])
      })
      await c.passo('praia: posição líbero e altura são recusadas; lado preferido é aceito', async () => {
        c.status(await ATL1.meuPerfil(gp.id, { position: 'LIBERO' }), [400, 422])
        c.status(await ATL1.meuPerfil(gp.id, { heightCm: 180 }), [400, 422])
        c.status(await ATL1.meuPerfil(gp.id, { nickname: 'Bia', preferredSide: 'ESQUERDA' }), 200)
      })
      await c.passo('o organizador corrige o perfil de qualquer atleta; atleta não edita o de outro (403)', async () => {
        c.status(await ORG.ajustarAtleta(gq.id, ATL2.id, { membershipType: 'AVULSO', nickname: 'Dudu' }), 200)
        c.igual(c.status(await ORG.atletas(gq.id), 200).corpo.athletes.find((a) => a.userId === ATL2.id).nickname, 'Dudu', 'apelido corrigido pelo organizador')
        c.status(await ATL1.ajustarAtleta(gq.id, ATL2.id, { membershipType: 'AVULSO', nickname: 'Hack' }), 403)
      })
      await c.passo('mudar a modalidade do grupo de quadra para praia: posição e altura somem da lista e voltam quando a modalidade volta', async () => {
        const v1 = c.status(await ORG.grupo(gq.id), 200).corpo.version
        const r1 = await ORG.ajustarGrupo(gq.id, v1, { name: gq.name, modality: 'BEACH_VOLLEYBALL', composition: 'MIXED' })
        c.status(r1, 200)
        const meio = c.status(await ATL2.atletas(gq.id), 200).corpo.athletes.find((a) => a.userId === ATL1.id)
        c.afirmar(!meio.position && !meio.heightCm, `em praia, posição e altura ficam escondidas (veio ${meio.position} / ${meio.heightCm}) — DEB-063`)
        await c.configurar(ORG, gq.id, { modality: 'COURT_VOLLEYBALL' })
        const volta = c.status(await ATL2.atletas(gq.id), 200).corpo.athletes.find((a) => a.userId === ATL1.id)
        c.igual([volta.position, volta.heightCm], ['LIBERO', 180], 'campos voltam com a modalidade de quadra')
      })
    },
  },
  {
    id: 'MEM-005', feature: 'membros', critico: false,
    titulo: 'O telefone: quem vê',
    async rodar(c) {
      const { ORG, ADM, ATL1, ATL2 } = await c.atores('ORG', 'ADM', 'ATL1', 'ATL2')
      const g = await c.passo('ORG cria o grupo, ADM vira admin; ATL1 e ATL2 entram', async () => {
        const grupo = await c.grupo(ORG, [ADM, ATL1, ATL2])
        c.status(await ORG.mudarPapel(grupo.id, ADM.id, 'ADMIN'), 200)
        return grupo
      })
      const veTelefone = async (quem, alvo) => (await quem.atletas(g.id)).corpo.athletes.find((a) => a.userId === alvo.id)?.phone ?? null
      const escolher = (a, phoneVisibility) => a.chamar('PATCH', '/api/session/profile', { phoneVisibility })
      await c.passo('padrão "só dono e admin": o atleta não vê o telefone de ATL1; admin e dono veem; ATL1 vê o próprio', async () => {
        c.igual((await ATL1.sessao()).corpo.user.phoneVisibility, 'ADMINS', 'padrão')
        c.igual(await veTelefone(ATL2, ATL1), null, 'atleta')
        for (const o of [ORG, ADM]) c.igual(await veTelefone(o, ATL1), `+55${ATL1.telefone}`, `${o.papel} vê`)
        c.igual(await veTelefone(ATL1, ATL1), `+55${ATL1.telefone}`, 'a própria pessoa vê')
      })
      await c.passo('"todos": o atleta passa a ver', async () => {
        c.status(await escolher(ATL1, 'EVERYONE'), 200)
        c.igual(await veTelefone(ATL2, ATL1), `+55${ATL1.telefone}`, 'atleta com "todos"')
      })
      await c.passo('"ninguém": nem dono e admin veem; a pessoa ainda vê o próprio; o campo vem vazio, sem erro', async () => {
        c.status(await escolher(ATL1, 'NOBODY'), 200)
        for (const o of [ATL2, ADM, ORG]) c.igual(await veTelefone(o, ATL1), null, `${o.papel} com "ninguém"`)
        c.igual(await veTelefone(ATL1, ATL1), `+55${ATL1.telefone}`, 'a própria pessoa vê')
      })
      await c.passo('a escolha vale para todos os grupos', async () => {
        const g2 = await c.grupo(ORG, [ATL1, ATL2], { nome: `QA Outro grupo ${c.tag}` })
        c.igual(await ATL2.atletas(g2.id).then((r) => r.corpo.athletes.find((a) => a.userId === ATL1.id)?.phone ?? null), null, 'atleta no outro grupo')
        c.igual(await ORG.atletas(g2.id).then((r) => r.corpo.athletes.find((a) => a.userId === ATL1.id)?.phone ?? null), null, 'dono no outro grupo')
      })
      await c.passo('o servidor guarda o telefone para o WhatsApp do Saqz, independente da escolha', async () => {
        c.igual(await c.valor(`select phone from access_users where id='${ATL1.id}'`), `+55${ATL1.telefone}`, 'telefone guardado')
      })
    },
  },
]
