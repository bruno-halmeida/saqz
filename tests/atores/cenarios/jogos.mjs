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
{
    id: 'JOGO-001', feature: 'jogos', critico: false,
    titulo: 'Estados do jogo e quem os vê',
    async rodar(c) {
      const { ORG, ADM, ATL1 } = await c.atores('ORG', 'ADM', 'ATL1')
      const g = await c.passo('ORG cria o grupo; ADM vira admin; ATL1 entra', async () => {
        const grupo = await c.grupo(ORG, [ADM, ATL1])
        c.status(await ORG.mudarPapel(grupo.id, ADM.id, 'ADMIN'), 200)
        return grupo
      })
      const rascunho = await c.jogo(ORG, g.id, { vagas: 4, publicar: false })
      await c.passo('rascunho: dono e admin enxergam; o atleta não vê o jogo nem na lista', async () => {
        c.status(await ORG.jogo(g.id, rascunho.id), 200)
        c.status(await ADM.jogo(g.id, rascunho.id), 200)
        c.status(await ATL1.jogo(g.id, rascunho.id), 404)
        c.afirmar(!c.status(await ATL1.jogos(g.id), 200).corpo.some((x) => x.id === rascunho.id), 'rascunho fora da lista do atleta')
      })
      await c.passo('rascunho → publicado por admin: todos os membros passam a ver', async () => {
        c.status(await ADM.publicarJogo(g.id, rascunho.id), 200)
        c.igual(c.status(await ATL1.jogo(g.id, rascunho.id), 200).corpo.status, 'PUBLISHED', 'ATL1 vê publicado')
      })
      await c.passo('o atleta não muda o estado de nenhum jogo (403): publicar, cancelar e concluir', async () => {
        c.status(await ATL1.publicarJogo(g.id, rascunho.id), 403)
        c.status(await ATL1.cancelarJogo(g.id, rascunho.id), 403)
        c.status(await ATL1.transicao(g.id, rascunho.id, 'complete'), 403)
      })
      await c.passo('publicado → cancelado: todos veem cancelado; cancelado não volta (publicar, cancelar e concluir são recusados)', async () => {
        c.status(await ADM.cancelarJogo(g.id, rascunho.id), 200)
        c.igual(c.status(await ATL1.jogo(g.id, rascunho.id), 200).corpo.status, 'CANCELLED', 'ATL1 vê cancelado')
        for (const acao of ['publish', 'cancel', 'complete']) c.status(await ORG.transicao(g.id, rascunho.id, acao), [409, 422], 'INVALID_GAME_TRANSITION')
      })
      await c.passo('publicado → concluído: todos veem concluído; concluído não volta', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 4 })
        await c.sql(`update games set status='COMPLETED', version=version+1 where id='${j.id}'`)
        c.igual(c.status(await ATL1.jogo(g.id, j.id), 200).corpo.status, 'COMPLETED', 'ATL1 vê concluído')
        for (const acao of ['publish', 'cancel']) c.status(await ORG.transicao(g.id, j.id, acao), [409, 422], 'INVALID_GAME_TRANSITION')
      })
    },
  },
  {
    id: 'JOGO-002', feature: 'jogos', critico: false,
    titulo: 'Criar o jogo',
    async rodar(c) {
      const { ORG, ADM, ATL1, ATL2 } = await c.atores('ORG', 'ADM', 'ATL1', 'ATL2')
      const g = await c.passo('ORG cria o grupo com padrões (10 vagas, prazo de 3 h, diária R$ 15, quadra); ADM vira admin; ATL1 e ATL2 entram', async () => {
        const grupo = await c.grupo(ORG, [ADM, ATL1, ATL2], { extra: { defaultCapacity: 10, defaultConfirmationLeadMinutes: 180, defaultGameFeeCents: 1500, defaultVenue: { name: 'Arena Padrão', address: 'Rua do Padrão, 10', court: 'Quadra 2' } } })
        c.status(await ORG.mudarPapel(grupo.id, ADM.id, 'ADMIN'), 200)
        return grupo
      })
      const base = (extra = {}) => {
        const inicio = semSegundos(daqui(60 * 24 * 3 + 600 * (c.njogos++)))
        return { title: 'Jogo QA', venue: { name: 'Quadra QA', address: 'Rua QA, 1' }, ...local(inicio), startsAt: inicio.toISOString(), durationMinutes: 90, capacity: 4, confirmationDeadline: new Date(inicio.getTime() - 3600_000).toISOString(), useDefaultGameFee: false, gameFeeCents: null, ...extra }
      }
      await c.passo('tudo preenchido e início futuro: criado como rascunho; o admin também cria', async () => {
        c.igual(c.status(await ORG.criarJogo(g.id, base()), 201).corpo.status, 'DRAFT', 'rascunho')
        c.status(await ADM.criarJogo(g.id, base()), 201)
      })
      await c.passo('atleta cria jogo: 403', async () => { c.status(await ATL1.criarJogo(g.id, base()), 403) })
      await c.passo('início no passado, prazo depois do início, 3 vagas, 101 vagas, duração de 10 min e título curto: recusados', async () => {
        const passado = semSegundos(daqui(-120))
        c.status(await ORG.criarJogo(g.id, base({ ...local(passado), startsAt: passado.toISOString(), confirmationDeadline: passado.toISOString() })), 400)
        const b = base()
        c.status(await ORG.criarJogo(g.id, { ...b, confirmationDeadline: new Date(Date.parse(b.startsAt) + 60_000).toISOString() }), 400)
        c.status(await ORG.criarJogo(g.id, base({ capacity: 3 })), 400)
        c.status(await ORG.criarJogo(g.id, base({ capacity: 101 })), 400)
        c.status(await ORG.criarJogo(g.id, base({ durationMinutes: 10 })), 400)
        c.status(await ORG.criarJogo(g.id, base({ title: 'A' })), 400)
      })
      await c.passo('título impróprio (xingamento forte): recusado', async () => {
        const r = c.status(await ORG.criarJogo(g.id, base({ title: 'Jogo do vsf' })), 400)
        c.afirmar(r.corpo.fieldErrors?.title, 'o erro deve apontar o título', r)
      })
      await c.passo('campos em branco usam o padrão do grupo (vagas, quadra, valor, prazo; a duração o app preenche)', async () => {
        const b = base()
        const minimo = { localDate: b.localDate, localTime: b.localTime, zoneId: b.zoneId, startsAt: b.startsAt, durationMinutes: 90 }
        const r = c.status(await ORG.criarJogo(g.id, minimo), 201).corpo
        c.igual([r.capacity, r.gameFeeCents, r.venue.name, r.venue.court], [10, 1500, 'Arena Padrão', 'Quadra 2'], 'padrões do grupo')
        c.igual(Date.parse(r.startsAt) - Date.parse(r.confirmationDeadline), 180 * 60_000, 'prazo = início menos 3 h')
      })
      await c.passo('jogo marcado em cima da hora (prazo padrão já passou): o prazo vira o início e a prioridade de mensalista não vale', async () => {
        await c.tipo(ORG, g.id, ATL1)
        const inicio = semSegundos(daqui(60 * 2))   // daqui a ~2 h < prazo padrão de 3 h
        const r = c.status(await ORG.criarJogo(g.id, { localDate: local(inicio).localDate, localTime: local(inicio).localTime, zoneId: 'America/Sao_Paulo', startsAt: inicio.toISOString(), durationMinutes: 90 }), 201).corpo
        c.igual(r.confirmationDeadline, r.startsAt, 'prazo = início')
        c.status(await ORG.publicarJogo(g.id, r.id), 200)
        c.status(await ATL2.vou(g.id, r.id), 200)
        c.igual(await c.statusDe(r.id, ATL2), 'CONFIRMED', 'avulso confirma direto (sem janela de prioridade)')
      })
    },
  },
  {
    id: 'JOGO-003', feature: 'jogos', critico: false,
    titulo: 'Editar o jogo',
    async rodar(c) {
      const { ORG, ADM, ATL1, ATL2, ATL3, ATL4 } = await c.atores('ORG', 'ADM', 'ATL1', 'ATL2', 'ATL3', 'ATL4')
      const g = await c.passo('ORG cria o grupo; ADM vira admin; atletas entram; prioridade desligada; jogo de 4 vagas com 4 confirmados e 3 na espera', async () => {
        const grupo = await c.grupo(ORG, [ADM, ATL1, ATL2, ATL3, ATL4])
        c.status(await ORG.mudarPapel(grupo.id, ADM.id, 'ADMIN'), 200)
        await c.configurar(ORG, grupo.id, { mensalistaPriority: false })
        grupo.jogo = await c.jogo(ORG, grupo.id, { vagas: 2 })
        await c.sql(`update games set capacity=4, version=version+1 where id='${grupo.jogo.id}'`)
        for (const a of [ATL1, ATL2, ATL3, ATL4, ADM, ORG]) c.status(await a.vou(grupo.id, grupo.jogo.id), 200)
        return grupo
      })
      const ler = async () => { const r = c.status(await ORG.jogo(g.id, g.jogo.id), 200); return r }
      await c.passo('edita antes do início: salvo, e as presenças continuam como estavam ao mudar a hora', async () => {
        const antes = await c.presencas(g.jogo.id)
        const r = await ler()
        const novo = semSegundos(new Date(Date.parse(r.corpo.startsAt) + 3600_000))
        c.status(await ADM.editarJogo(g.id, r.corpo, r.etag, { ...local(novo), startsAt: novo.toISOString(), title: 'Jogo QA editado' }), 200)
        c.igual((await c.presencas(g.jogo.id)).map((p) => [p.nome, p.status]), antes.map((p) => [p.nome, p.status]), 'presenças depois da edição')
      })
      await c.passo('salva com a versão antiga: conflito (VERSION_CONFLICT); dois organizadores ao mesmo tempo: um passa e o outro recebe conflito', async () => {
        const r = await ler()
        c.status(await ORG.editarJogo(g.id, r.corpo, '"1"', { title: 'Jogo velho' }), 409, 'VERSION_CONFLICT')
        const [a, b] = await Promise.all([ORG.editarJogo(g.id, r.corpo, r.etag, { title: 'Edição do dono' }), ADM.editarJogo(g.id, r.corpo, r.etag, { title: 'Edição do admin' })])
        c.igual([a.status, b.status].sort(), [200, 409], 'um passa, o outro conflita')
      })
      await c.passo('início movido para o passado: recusado', async () => {
        const r = await ler()
        const passado = semSegundos(daqui(-120))
        c.status(await ORG.editarJogo(g.id, r.corpo, r.etag, { ...local(passado), startsAt: passado.toISOString(), confirmationDeadline: passado.toISOString() }), 400)
      })
      await c.passo('aumenta as vagas pelo editor com fila de 3 em ordem de chegada: os da fila sobem', async () => {
        const r = await ler()
        const ed = await ORG.editarJogo(g.id, r.corpo, r.etag, { capacity: 7 })
        c.status(ed, 200)
        c.igual(await c.valor(`select count(*) from game_attendance where game_id='${g.jogo.id}' and status='CONFIRMED'`), '6', 'confirmados depois de aumentar para 7 com 6 respostas')
      })
      await c.passo('depois do início ninguém edita: recusado (INVALID_GAME_TRANSITION)', async () => {
        await c.inicioPara(g.jogo.id, new Date(Date.now() - 10 * 60_000))
        const r = await ler()
        c.status(await ORG.editarJogo(g.id, r.corpo, r.etag, { title: 'Tarde demais' }), [409, 422], 'INVALID_GAME_TRANSITION')
      })
    },
  },
  {
    id: 'JOGO-006', feature: 'jogos', critico: false, lento: true, teto: 420_000,
    titulo: 'Encerramento automático',
    async rodar(c) {
      const { ORG, ATL1, ATL2, ATL3 } = await c.atores('ORG', 'ATL1', 'ATL2', 'ATL3')
      const g = await c.passo('ORG cria o grupo; jogo de 2 vagas: ATL1 confirmada, ATL2 na espera, ATL3 "Não vou"; um cancelado e um rascunho do mesmo grupo', async () => {
        const grupo = await c.grupo(ORG, [ATL1, ATL2, ATL3])
        await c.configurar(ORG, grupo.id, { mensalistaPriority: false })
        grupo.jogo = await c.jogo(ORG, grupo.id, { vagas: 2 })
        const outro = await c.jogo(ORG, grupo.id, { vagas: 2 })
        await ATL3.naoVou(grupo.id, grupo.jogo.id)
        c.status(await ORG.porOutro(grupo.id, grupo.jogo.id, ORG.id, 'CONFIRM', 'Dono no jogo'), 200)
        c.status(await ATL1.vou(grupo.id, grupo.jogo.id), 200); c.status(await ATL2.vou(grupo.id, grupo.jogo.id), 200)
        grupo.cancelado = await c.jogo(ORG, grupo.id, { vagas: 4 })
        c.status(await ORG.cancelarJogo(grupo.id, grupo.cancelado.id), 200)
        grupo.rascunho = await c.jogo(ORG, grupo.id, { vagas: 4, publicar: false })
        grupo.futuro = outro
        return grupo
      })
      await c.passo('o jogo passa de início + duração (banco): o job de encerramento (a cada 5 min) o conclui; cancelado, rascunho e jogo futuro ficam como estão', async () => {
        const fim = new Date(Date.now() - 2 * 3600_000)
        for (const j of [g.jogo, g.cancelado, g.rascunho]) await c.sql(`update games set starts_at='${fim.toISOString()}', confirmation_deadline='${new Date(fim.getTime() - 3600_000).toISOString()}', local_date='${fim.toISOString().slice(0, 10)}', version=version+1 where id='${j.id}'`).catch(() => {})
        await c.esperar('o job encerrar o jogo', async () => (await c.valor(`select status from games where id='${g.jogo.id}'`)) === 'COMPLETED', { teto: 360_000, passo: 5_000 })
        c.igual(await c.valor(`select status from games where id='${g.cancelado.id}'`), 'CANCELLED', 'cancelado continua cancelado')
        c.igual(await c.valor(`select status from games where id='${g.rascunho.id}'`), 'DRAFT', 'rascunho nunca é encerrado')
        c.igual(await c.valor(`select status from games where id='${g.futuro.id}'`), 'PUBLISHED', 'jogo futuro continua publicado')
      })
      await c.passo('presença congelada e "Jogos realizados": só quem estava confirmada ganha +1 (ATL1), não a da espera (ATL2) nem quem disse Não vou (ATL3)', async () => {
        c.status(await ATL3.vou(g.id, g.jogo.id), [403, 409, 422], 'ATTENDANCE_FROZEN')
        const jogos = async (a) => c.status(await ORG.chamar('GET', `/api/groups/${g.id}/athletes/${a.id}/stats`), 200).corpo.games
        c.igual([await jogos(ATL1), await jogos(ATL2), await jogos(ATL3)], [1, 0, 0], 'jogos realizados')
      })
    },
  },
  {
    id: 'JOGO-007', feature: 'jogos', critico: false,
    titulo: 'Local e quadra',
    async rodar(c) {
      const { ORG, ATL1 } = await c.atores('ORG', 'ATL1')
      const g = await c.passo('ORG cria o grupo com a quadra padrão "Quadra Velha"; ATL1 entra', () => c.grupo(ORG, [ATL1], { extra: { defaultVenue: { name: 'Quadra Velha', address: 'Rua Velha, 100', court: null } } }))
      const base = () => {
        const inicio = semSegundos(daqui(60 * 24 * 3 + 600 * (c.njogos++)))
        return { title: 'Jogo QA', ...local(inicio), startsAt: inicio.toISOString(), durationMinutes: 90, capacity: 4, confirmationDeadline: new Date(inicio.getTime() - 3600_000).toISOString(), useDefaultGameFee: false, gameFeeCents: null }
      }
      await c.passo('nome da quadra em branco ou de 1 caractere é recusado (venue.name)', async () => {
        for (const nome of ['', ' ', 'A']) { const r = await ORG.criarJogo(g.id, { ...base(), venue: { name: nome, address: 'Rua QA, 1' } }); c.afirmar(r.status === 400, `nome de quadra ${JSON.stringify(nome)} deve ser recusado (veio ${r.status})`, r) }
      })
      await c.passo('endereço em branco é aceito; com 3 caracteres é recusado ("pelo menos 5")', async () => {
        c.status(await ORG.criarJogo(g.id, { ...base(), venue: { name: 'Quadra Sem Endereço', address: '' } }), 201)
        c.status(await ORG.criarJogo(g.id, { ...base(), venue: { name: 'Quadra Curta', address: 'abc' } }), 400)
      })
      await c.passo('mudar a quadra do grupo depois não altera os jogos já criados (cópia do local)', async () => {
        const j = c.status(await ORG.criarJogo(g.id, { ...base(), venue: { name: 'Quadra Velha', address: 'Rua Velha, 100' } }), 201).corpo
        await c.configurar(ORG, g.id, { defaultVenue: { name: 'Quadra Nova', address: 'Rua Nova, 200', court: null } })
        c.igual(c.status(await ORG.jogo(g.id, j.id), 200).corpo.venue.name, 'Quadra Velha', 'jogo antigo mantém o local')
      })
    },
  },
  {
    id: 'JOGO-008', feature: 'jogos', critico: false,
    titulo: 'Valor do jogo (e recado removido)',
    async rodar(c) {
      const { ORG, ATL1 } = await c.atores('ORG', 'ATL1')
      const g = await c.passo('ORG cria o grupo e desliga a prioridade; ATL1 entra', async () => {
        const grupo = await c.grupo(ORG, [ATL1])
        await c.configurar(ORG, grupo.id, { mensalistaPriority: false })
        return grupo
      })
      await c.passo('valor vazio: jogo sem cobrança', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 4, valorCents: null })
        c.status(await ATL1.vou(g.id, j.id), 200)
        c.igual((await ORG.cobrancas(g.id)).corpo.charges.filter((x) => x.gameId === j.id).length, 0, 'cobranças sem valor')
      })
      await c.passo('valor R$ 0,00 é recusado; valor acima de R$ 999.999,99 também; R$ 0,01 é aceito', async () => {
        const inicio = semSegundos(daqui(60 * 24 * 9))
        const corpo = (gameFeeCents) => ({ title: 'Jogo valor', venue: { name: 'Quadra QA', address: 'Rua QA, 1' }, ...local(inicio), startsAt: inicio.toISOString(), durationMinutes: 90, capacity: 4, confirmationDeadline: new Date(inicio.getTime() - 3600_000).toISOString(), useDefaultGameFee: false, gameFeeCents })
        c.status(await ORG.criarJogo(g.id, corpo(0)), 400)
        c.status(await ORG.criarJogo(g.id, corpo(100_000_000)), 400)
        c.status(await ORG.criarJogo(g.id, corpo(1)), 201)
      })
      await c.passo('recado enviado por app antigo (notes): aceito e ignorado, não é gravado', async () => {
        const inicio = semSegundos(daqui(60 * 24 * 11))
        const r = c.status(await ORG.criarJogo(g.id, { title: 'Jogo recado', venue: { name: 'Quadra QA', address: 'Rua QA, 1' }, ...local(inicio), startsAt: inicio.toISOString(), durationMinutes: 90, capacity: 4, confirmationDeadline: new Date(inicio.getTime() - 3600_000).toISOString(), useDefaultGameFee: false, gameFeeCents: null, notes: 'Levem água e bola' }), 201)
        c.igual(r.corpo.notes ?? null, null, 'recado devolvido')
        c.igual(await c.valor(`select coalesce(notes,'(nulo)') from games where id='${r.corpo.id}'`), '(nulo)', 'recado gravado')
      })
    },
  },
  {
    id: 'JOGO-009', feature: 'jogos', critico: false,
    titulo: 'Um jogo por vez no grupo',
    async rodar(c) {
      const { ORG, ADM } = await c.atores('ORG', 'ADM')
      const g = await c.passo('ORG cria o grupo e o ADM vira admin', async () => {
        const grupo = await c.grupo(ORG, [ADM])
        c.status(await ORG.mudarPapel(grupo.id, ADM.id, 'ADMIN'), 200)
        return grupo
      })
      const inicioBase = semSegundos(daqui(60 * 24 * 6))
      const doze = (min, duracao = 120) => {
        const inicio = new Date(inicioBase.getTime() + min * 60_000)
        return { title: 'Jogo sobreposição', venue: { name: 'Quadra QA', address: 'Rua QA, 1' }, ...local(inicio), startsAt: inicio.toISOString(), durationMinutes: duracao, capacity: 4, confirmationDeadline: new Date(inicio.getTime() - 3600_000).toISOString(), useDefaultGameFee: false, gameFeeCents: null }
      }
      const primeiro = await c.passo('jogo de 2 h criado às 19h', async () => c.status(await ORG.criarJogo(g.id, doze(0)), 201).corpo)
      await c.passo('criar uma hora depois (sobrepõe): conflito de horário (GAME_SCHEDULE_CONFLICT) apontando o jogo', async () => {
        const r = c.status(await ORG.criarJogo(g.id, doze(60)), 409, 'GAME_SCHEDULE_CONFLICT')
        c.igual(r.corpo.conflictGameId, primeiro.id, 'jogo em conflito')
      })
      await c.passo('criar exatamente quando o outro termina (encostado): permitido', async () => { c.status(await ORG.criarJogo(g.id, doze(120)), 201) })
      await c.passo('editar um jogo para cima de outro: conflito', async () => {
        const solto = c.status(await ORG.criarJogo(g.id, doze(600)), 201).corpo
        const lido = c.status(await ORG.jogo(g.id, solto.id), 200)
        c.status(await ORG.editarJogo(g.id, lido.corpo, lido.etag, { ...local(new Date(inicioBase.getTime() + 30 * 60_000)), startsAt: new Date(inicioBase.getTime() + 30 * 60_000).toISOString(), confirmationDeadline: new Date(inicioBase.getTime()).toISOString() }), 409, 'GAME_SCHEDULE_CONFLICT')
      })
      await c.passo('jogo cancelado libera o horário: criar no mesmo horário agora é permitido', async () => {
        c.status(await ORG.cancelarJogo(g.id, primeiro.id), 200)
        c.status(await ORG.criarJogo(g.id, doze(0)), 201)
      })
      await c.passo('dois organizadores criando no mesmo horário ao mesmo tempo: um passa e o outro recebe conflito', async () => {
        const [a, b] = await Promise.all([ORG.criarJogo(g.id, doze(900)), ADM.criarJogo(g.id, doze(900))])
        c.igual([a.status, b.status].sort(), [201, 409], 'um cria, o outro conflita')
      })
    },
  },
  {
    id: 'JOGO-010', feature: 'jogos', critico: false,
    titulo: 'Horários fixos (série semanal)',
    async rodar(c) {
      const { ORG, ATL1 } = await c.atores('ORG', 'ATL1')
      const dia = (dias) => ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'][new Date(Date.now() - 3 * 3600_000 + dias * 86_400_000).getUTCDay()]
      const g = await c.passo('ORG cria o grupo com quadra padrão; ATL1 entra', () => c.grupo(ORG, [ATL1]))
      const agenda = async (campos) => {
        const atual = c.status(await ORG.chamar('GET', `/api/groups/${g.id}/schedule`), 200)
        return ORG.chamar('PUT', `/api/groups/${g.id}/schedule`, { recurring: true, slots: [], durationMinutes: 90, confirmationLeadMinutes: 180, paused: false, ...campos }, { 'If-Match': atual.etag })
      }
      await c.passo('ORG cria o horário fixo: o próximo jogo desse horário nasce publicado na hora e vinculado à série', async () => {
        c.status(await agenda({ slots: [{ weekday: dia(3), startTime: '20:00:00' }] }), 200)
        const jogos = c.status(await ORG.jogos(g.id), 200).corpo
        const serie = jogos.filter((j) => j.status === 'PUBLISHED')
        c.afirmar(serie.length >= 1, `deveria haver um jogo publicado da série (jogos: ${JSON.stringify(jogos.map((j) => [j.localDate, j.localTime, j.status]))})`)
        c.igual(serie[0].localTime, '20:00:00', 'hora do jogo da série')
        c.igual(await c.valor(`select count(*) from games where id='${serie[0].id}' and series_id is not null`), '1', 'jogo vinculado à série')
      })
      await c.passo('os jogos seguintes da série ficam em rascunho, invisíveis para o atleta', async () => {
        const rasc = Number(await c.valor(`select count(*) from games where group_id='${g.id}' and series_id is not null and status='DRAFT'`))
        const publicados = c.status(await ATL1.jogos(g.id), 200).corpo.length
        c.afirmar(rasc >= 1, 'deveria haver ocorrências futuras em rascunho')
        c.igual(publicados, 1, 'o atleta só vê o jogo publicado')
      })
      await c.passo('ORG edita o horário para 21h: vale daqui para frente (o próximo jogo passa para 21h)', async () => {
        c.status(await agenda({ slots: [{ weekday: dia(3), startTime: '21:00:00' }] }), 200)
        const jogos = c.status(await ORG.jogos(g.id), 200).corpo.filter((j) => j.status === 'PUBLISHED')
        c.igual(jogos.map((j) => j.localTime).sort(), ['21:00:00'], 'horário do jogo publicado da série')
      })
      await c.passo('horários pausados: os jogos existentes continuam e nenhum jogo novo nasce', async () => {
        const antes = await c.valor(`select count(*) from games where group_id='${g.id}'`)
        c.status(await agenda({ slots: [{ weekday: dia(3), startTime: '21:00:00' }], paused: true }), 200)
        await c.dormir(1500)
        c.igual(await c.valor(`select count(*) from games where group_id='${g.id}' and status='PUBLISHED'`) >= '1', true, 'o jogo publicado continua')
        c.igual(Number(await c.valor(`select count(*) from games where group_id='${g.id}'`)) <= Number(antes), true, 'nenhum jogo novo com a série pausada')
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
