// Promoção da lista de espera (PROM-*): quem libera vaga, quem sobe primeiro e a escolha manual.
import { randomUUID } from 'node:crypto'

const MIN = 60_000

export default [
  {
    id: 'PROM-001', feature: 'promocao', critico: true,
    titulo: 'O que libera uma vaga',
    async rodar(c) {
      const { ORG, ATL1, ATL2, ATL3, ATL4, ATL5 } = await c.atores('ORG', 'ATL1', 'ATL2', 'ATL3', 'ATL4', 'ATL5')
      const g = await c.passo('ORG cria o grupo com ATL1 a ATL5 e desliga a prioridade (ordem de chegada pura)', async () => {
        const grupo = await c.grupo(ORG, [ATL1, ATL2, ATL3, ATL4, ATL5])
        await c.configurar(ORG, grupo.id, { mensalistaPriority: false })
        return grupo
      })
      await c.passo('jogo de 2 vagas: ATL1 e ATL2 confirmam, ATL3 e ATL4 esperam; ATL1 diz Não vou e ATL3 (1º da fila) sobe', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 2 })
        for (const a of [ATL1, ATL2, ATL3, ATL4]) c.status(await a.vou(g.id, j.id), 200)
        c.igual([await c.statusDe(j.id, ATL3), await c.statusDe(j.id, ATL4)], ['WAITLISTED', 'WAITLISTED'], 'fila antes')
        c.status(await ATL1.naoVou(g.id, j.id), 200)
        c.igual(await c.statusDe(j.id, ATL3), 'CONFIRMED', 'ATL3 subiu')
        c.igual(await c.statusDe(j.id, ATL4), 'WAITLISTED', 'ATL4 segue esperando')
        c.igual((await c.eventos(j.id, ATL3)).at(-1), 'WAITLISTED>CONFIRMED:SYSTEM', 'subida automática')
      })
      await c.passo('quem estava na espera desiste: não libera vaga e nada muda', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 2 })
        for (const a of [ATL1, ATL2, ATL3]) c.status(await a.vou(g.id, j.id), 200)
        c.status(await ATL3.naoVou(g.id, j.id), 200)
        c.igual([await c.statusDe(j.id, ATL1), await c.statusDe(j.id, ATL2)], ['CONFIRMED', 'CONFIRMED'], 'confirmados seguem')
        c.igual(await c.valor(`select count(*) from game_attendance where game_id='${j.id}' and status='CONFIRMED'`), '2', 'confirmados')
      })
      await c.passo('ORG tira um confirmado (Não vou por outro, com motivo): libera 1 e a fila anda', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 2 })
        for (const a of [ATL1, ATL2, ATL3]) c.status(await a.vou(g.id, j.id), 200)
        c.status(await ORG.porOutro(g.id, j.id, ATL1.id, 'DECLINE', 'Não pôde vir'), 200)
        c.igual([await c.statusDe(j.id, ATL1), await c.statusDe(j.id, ATL3)], ['DECLINED', 'CONFIRMED'], 'ATL1 fora, ATL3 sobe')
      })
      await c.passo('ORG aumenta as vagas de 4 para 6 com 4 confirmados e 2 na espera: sobem 2', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 4 })
        for (const a of [ATL1, ATL2, ATL3, ATL4, ATL5, ORG]) c.status(await a.vou(g.id, j.id), 200)
        c.igual(await c.valor(`select count(*) from game_attendance where game_id='${j.id}' and status='WAITLISTED'`), '2', 'na espera antes')
        const r = c.status(await ORG.ajustarVagas(g.id, j.id, 6), 200)
        c.igual(r.corpo.promotedCount, 2, 'promovidos pelo aumento')
        c.igual(await c.valor(`select count(*) from game_attendance where game_id='${j.id}' and status='CONFIRMED'`), '6', 'confirmados depois')
      })
      await c.passo('anfitrião ATL1 confirmado com 2 convidados confirmados diz Não vou: libera 3 e a fila sobe', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 4 })
        c.status(await ATL1.vou(g.id, j.id), 200)
        c.status(await ATL1.convidado(g.id, j.id, 'Convidado Um'), 201)
        c.status(await ATL1.convidado(g.id, j.id, 'Convidado Dois'), 201)
        c.status(await ATL2.vou(g.id, j.id), 200)
        for (const a of [ATL3, ATL4, ATL5]) c.status(await a.vou(g.id, j.id), 200)
        c.igual(await c.valor(`select count(*) from game_attendance where game_id='${j.id}' and status='CONFIRMED'`), '4', 'confirmados antes')
        c.status(await ATL1.naoVou(g.id, j.id), 200)
        c.igual([await c.statusDe(j.id, ATL3), await c.statusDe(j.id, ATL4), await c.statusDe(j.id, ATL5)], ['CONFIRMED', 'CONFIRMED', 'CONFIRMED'], 'três da fila sobem')
      })
      await c.passo('ATL2 sai do grupo: libera a vaga nos jogos futuros e o próximo da fila sobe', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 2 })
        for (const a of [ATL2, ATL3, ATL4]) c.status(await a.vou(g.id, j.id), 200)
        c.status(await ATL2.sairDoGrupo(g.id), 204)
        c.igual(await c.statusDe(j.id, ATL4) , 'CONFIRMED', 'ATL4 sobe depois da saída de ATL2')
      })
    },
  },
  {
    id: 'PROM-002', feature: 'promocao', critico: true,
    titulo: 'Quem sobe primeiro',
    async rodar(c) {
      const { ORG, ATL1, ATL2, ATL3, ATL4, ATL5 } = await c.atores('ORG', 'ATL1', 'ATL2', 'ATL3', 'ATL4', 'ATL5')
      // ATL1, ATL4 e ATL5 mensalistas; ATL2 e ATL3 avulsos. Jogos de 2 vagas: ATL4 e ATL5 (mensalistas) lotam;
      // a fila é formada por A = ATL2 (avulso), M = ATL1 (mensalista) e B = ATL3 (avulso).
      const g = await c.passo('ORG cria o grupo; ATL1, ATL4 e ATL5 viram mensalistas', async () => {
        const grupo = await c.grupo(ORG, [ATL1, ATL2, ATL3, ATL4, ATL5])
        for (const a of [ATL1, ATL4, ATL5]) await c.tipo(ORG, grupo.id, a)
        return grupo
      })
      const lotado = async (...fila) => {
        const j = await c.jogo(ORG, g.id, { vagas: 2 })
        c.status(await ATL4.vou(g.id, j.id), 200)
        c.status(await ATL5.vou(g.id, j.id), 200)
        for (const a of fila) c.status(await a.vou(g.id, j.id), 200)
        return j
      }
      await c.passo('antes do prazo, prioridade ligada: fila A, M, B; ATL4 diz Não vou e sobe o mensalista M (ATL1), não o avulso mais antigo', async () => {
        const j = await lotado(ATL2, ATL1, ATL3)
        c.igual(await c.statusDe(j.id, ATL1), 'WAITLISTED', 'M espera com o jogo cheio')
        c.status(await ATL4.naoVou(g.id, j.id), 200)
        c.igual([await c.statusDe(j.id, ATL1), await c.statusDe(j.id, ATL2), await c.statusDe(j.id, ATL3)], ['CONFIRMED', 'WAITLISTED', 'WAITLISTED'], 'M sobe')
      })
      await c.passo('depois do prazo: fila A, M; o prazo passa e ATL4 diz Não vou; sobe o mais antigo (A, avulso), sem olhar o tipo', async () => {
        const j = await lotado(ATL2, ATL1)
        await c.prazoPara(j.id, new Date(Date.now() - MIN))
        c.status(await ATL4.naoVou(g.id, j.id), 200)
        c.igual([await c.statusDe(j.id, ATL2), await c.statusDe(j.id, ATL1)], ['CONFIRMED', 'WAITLISTED'], 'A sobe')
      })
      await c.passo('convidado do mensalista entra na fila como avulso, na ordem de chegada dele: fila A, convidado de ATL5; sobe A', async () => {
        const j = await lotado(ATL2)
        c.status(await ATL5.convidado(g.id, j.id, 'Convidado do ATL5'), 201)
        c.igual(await c.valor(`select status from game_attendance where game_id='${j.id}' and member_user_id='${ATL5.id}' and guest_seq=1`), 'WAITLISTED', 'convidado espera')
        c.status(await ATL4.naoVou(g.id, j.id), 200)
        c.igual(await c.statusDe(j.id, ATL2), 'CONFIRMED', 'A, que chegou antes, sobe')
      })
      await c.passo('prioridade desligada: fila A, M; ATL4 diz Não vou e sobe A (o mais antigo)', async () => {
        await c.configurar(ORG, g.id, { mensalistaPriority: false })
        const j = await c.jogo(ORG, g.id, { vagas: 2 })
        for (const a of [ATL4, ATL5, ATL2, ATL1]) c.status(await a.vou(g.id, j.id), 200)
        c.igual([await c.statusDe(j.id, ATL2), await c.statusDe(j.id, ATL1)], ['WAITLISTED', 'WAITLISTED'], 'fila A, M')
        c.status(await ATL4.naoVou(g.id, j.id), 200)
        c.igual([await c.statusDe(j.id, ATL2), await c.statusDe(j.id, ATL1)], ['CONFIRMED', 'WAITLISTED'], 'A sobe')
      })
      await c.passo('duas vagas abertas em sequência: fila A, B; ATL4 sai e sobe A; ATL5 sai e sobe B', async () => {
        const j = await lotado(ATL2, ATL3)
        c.status(await ATL4.naoVou(g.id, j.id), 200)
        c.igual([await c.statusDe(j.id, ATL2), await c.statusDe(j.id, ATL3)], ['CONFIRMED', 'WAITLISTED'], 'primeiro A')
        c.status(await ATL5.naoVou(g.id, j.id), 200)
        c.igual(await c.statusDe(j.id, ATL3), 'CONFIRMED', 'depois B')
      })
    },
  },
  {
    id: 'PROM-004', feature: 'promocao', critico: true,
    titulo: 'Escolha manual: "Colocar no jogo"',
    async rodar(c) {
      const { ORG, ADM, ATL1, ATL2, ATL3 } = await c.atores('ORG', 'ADM', 'ATL1', 'ATL2', 'ATL3')
      const g = await c.passo('ORG cria o grupo, passa ADM a administrador e troca para escolha manual', async () => {
        const grupo = await c.grupo(ORG, [ADM, ATL1, ATL2, ATL3])
        c.status(await ORG.mudarPapel(grupo.id, ADM.id, 'ADMIN'), 200)
        await c.configurar(ORG, grupo.id, { promotionMode: 'MANUAL', mensalistaPriority: false })
        return grupo
      })
      const vencido = async (vagas) => {
        const j = await c.jogo(ORG, g.id, { vagas })
        await c.prazoPara(j.id, new Date(Date.now() - MIN))
        return j
      }
      await c.passo('prazo vencido, vaga livre: ATL1 diz Vou e fica na espera; ADM (admin) o coloca no jogo e ele confirma (sai da fila, com motivo registrado)', async () => {
        const j = await vencido(4)
        c.status(await ATL1.vou(g.id, j.id), 200)
        c.igual(await c.statusDe(j.id, ATL1), 'WAITLISTED', 'manual deixa na espera')
        const r = c.status(await ADM.colocarNoJogo(g.id, j.id, ATL1.id), 200)
        c.igual(r.corpo.promotedCount, 1, 'promovidos')
        c.igual(r.corpo.audit.source, 'ORGANIZER', 'origem')
        c.igual(await c.statusDe(j.id, ATL1), 'CONFIRMED', 'ATL1 confirmado')
        c.igual(await c.valor(`select reason from attendance_events where game_id='${j.id}' and member_user_id='${ATL1.id}' order by seq desc limit 1`), 'Escolha do organizador', 'motivo')
      })
      await c.passo('atleta comum ATL3 tenta colocar ATL2 no jogo: 403 e nada muda', async () => {
        const j = await vencido(4)
        c.status(await ATL2.vou(g.id, j.id), 200)
        c.status(await ATL3.colocarNoJogo(g.id, j.id, ATL2.id), 403)
        c.igual(await c.statusDe(j.id, ATL2), 'WAITLISTED', 'ATL2 segue na espera')
      })
      await c.passo('jogo cheio: ORG tenta colocar ATL2 sem vaga livre: recusado (promotion)', async () => {
        const j = await vencido(2)
        c.status(await ATL1.vou(g.id, j.id), 200)
        c.status(await ATL3.vou(g.id, j.id), 200)
        c.status(await ORG.colocarNoJogo(g.id, j.id, ATL1.id), 200)  // abre o jogo cheio: 1 vaga
        c.status(await ORG.colocarNoJogo(g.id, j.id, ATL3.id), 200)  // 2 vagas ocupadas
        c.status(await ATL2.vou(g.id, j.id), 200)
        const r = c.status(await ORG.colocarNoJogo(g.id, j.id, ATL2.id), 400)
        c.afirmar(r.corpo.fieldErrors?.promotion, 'esperado erro no campo promotion', r)
        c.igual(await c.statusDe(j.id, ATL2), 'WAITLISTED', 'ATL2 segue na espera')
      })
      await c.passo('quem não está na espera (ATL3 sem resposta) não pode ser colocado: recusado (promotion)', async () => {
        const j = await vencido(4)
        const r = c.status(await ORG.colocarNoJogo(g.id, j.id, ATL3.id), 400)
        c.afirmar(r.corpo.fieldErrors?.promotion, 'esperado erro no campo promotion', r)
      })
      await c.passo('mesmo requestId repetido: mesmo resultado e um único efeito', async () => {
        const j = await vencido(4)
        c.status(await ATL2.vou(g.id, j.id), 200)
        const id = randomUUID()
        const a = await ORG.colocarNoJogo(g.id, j.id, ATL2.id, id)
        const b = await ORG.colocarNoJogo(g.id, j.id, ATL2.id, id)
        c.status(a, 200); c.status(b, 200)
        c.igual((await c.eventos(j.id, ATL2)).filter((e) => e.endsWith('>CONFIRMED:ORGANIZER')).length, 1, 'eventos de colocação')
      })
      await c.passo('rascunho: não dá para colocar ninguém', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 4, publicar: false })
        c.status(await ORG.colocarNoJogo(g.id, j.id, ATL2.id), [403, 404, 409, 422, 400])
      })
      await c.passo('ordem de chegada: com o modo trocado, a ação deixa de existir (promotionMode)', async () => {
        await c.configurar(ORG, g.id, { promotionMode: 'FIFO' })
        const j = await vencido(4)
        const r = c.status(await ORG.colocarNoJogo(g.id, j.id, ATL1.id), 400)
        c.afirmar(r.corpo.fieldErrors?.promotionMode || r.corpo.fieldErrors?.promotion, 'esperado erro de modo/promotion', r)
      })
    },
  },
{
    id: 'PROM-003', feature: 'promocao', critico: false,
    titulo: 'Ordem de chegada: promoção automática',
    async rodar(c) {
      const { ORG, ATL1, ATL2, ATL3, ATL4, ATL5 } = await c.atores('ORG', 'ATL1', 'ATL2', 'ATL3', 'ATL4', 'ATL5')
      const g = await c.passo('ORG cria o grupo e desliga a prioridade', async () => {
        const grupo = await c.grupo(ORG, [ATL1, ATL2, ATL3, ATL4, ATL5])
        await c.configurar(ORG, grupo.id, { mensalistaPriority: false })
        return grupo
      })
      await c.passo('confirmado desiste com fila de 2: o 1º sobe, o 2º continua; o evento é de sistema com o autor da ação que abriu a vaga', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 2 })
        for (const a of [ATL1, ATL2, ATL3, ATL4]) c.status(await a.vou(g.id, j.id), 200)
        c.status(await ATL1.naoVou(g.id, j.id), 200)
        c.igual([await c.statusDe(j.id, ATL3), await c.statusDe(j.id, ATL4)], ['CONFIRMED', 'WAITLISTED'], 'só o 1º sobe')
        const [[origem, autor]] = await c.sql(`select source, actor_user_id from attendance_events where game_id='${j.id}' and member_user_id='${ATL3.id}' order by seq desc limit 1`)
        c.igual([origem, autor], ['SYSTEM', ATL1.id], 'origem "sistema" com o autor da desistência')
      })
      await c.passo('fila vazia: a vaga fica aberta e ninguém sobe', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 2 })
        c.status(await ATL1.vou(g.id, j.id), 200); c.status(await ATL2.vou(g.id, j.id), 200)
        c.status(await ATL1.naoVou(g.id, j.id), 200)
        c.igual(await c.valor(`select count(*) from game_attendance where game_id='${j.id}' and status='CONFIRMED'`), '1', 'confirmados')
        c.igual(c.status(await ORG.presenca(g.id, j.id), 200).corpo.availableSpots, 1, 'vaga aberta')
      })
      await c.passo('"Vou" tardio com vaga: entra na espera e sobe na hora, com a fila antiga na frente', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 2 })
        c.status(await ATL1.vou(g.id, j.id), 200); c.status(await ATL2.vou(g.id, j.id), 200); c.status(await ATL3.vou(g.id, j.id), 200)
        await c.prazoPara(j.id, new Date(Date.now() - 60_000))
        c.status(await ATL1.naoVou(g.id, j.id), 200)  // abre 1 vaga: ATL3, da fila antiga, sobe
        c.status(await ATL4.vou(g.id, j.id), 200)     // tardio e sem vaga: fila
        c.igual([await c.statusDe(j.id, ATL3), await c.statusDe(j.id, ATL4)], ['CONFIRMED', 'WAITLISTED'], 'fila antiga na frente do tardio')
      })
      await c.passo('depois do início do jogo ninguém sobe: o organizador tira um confirmado e a espera continua', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 2 })
        for (const a of [ATL1, ATL2, ATL3]) c.status(await a.vou(g.id, j.id), 200)
        await c.inicioPara(j.id, new Date(Date.now() - 5 * 60_000))
        c.status(await ORG.porOutro(g.id, j.id, ATL1.id, 'DECLINE', 'Foi embora'), 200)
        c.igual(await c.statusDe(j.id, ATL3), 'WAITLISTED', 'ATL3 não sobe depois do início')
        c.status(await ORG.ajustarVagas(g.id, j.id, 6), [403, 409, 422], 'ATTENDANCE_FROZEN')
      })
    },
  },
  {
    id: 'PROM-005', feature: 'promocao', critico: false, lento: true, teto: 300_000,
    titulo: 'O job do prazo',
    async rodar(c) {
      const { ORG, ADM, ATL1, ATL2, ATL3, ATL4, ATL5 } = await c.atores('ORG', 'ADM', 'ATL1', 'ATL2', 'ATL3', 'ATL4', 'ATL5')
      const g = await c.passo('ORG cria o grupo (ordem de chegada, prioridade ligada); ADM vira admin; 5 atletas entram', async () => {
        const grupo = await c.grupo(ORG, [ADM, ATL1, ATL2, ATL3, ATL4, ATL5])
        c.status(await ORG.mudarPapel(grupo.id, ADM.id, 'ADMIN'), 200)
        return grupo
      })
      const fifo = await c.passo('jogo A (ordem de chegada, 4 vagas): os 5 avulsos dizem Vou e ficam na espera até o prazo', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 4 })
        for (const a of [ATL1, ATL2, ATL3, ATL4, ATL5]) c.status(await a.vou(g.id, j.id), 200)
        c.igual(await c.valor(`select count(*) from game_attendance where game_id='${j.id}' and status='WAITLISTED'`), '5', 'na espera')
        return j
      })
      const manual = await c.passo('grupo B no modo manual: jogo com 4 vagas e 2 avulsos na espera', async () => {
        const gb = await c.grupo(ORG, [ADM, ATL1, ATL2], { nome: `QA Manual ${c.tag}` })
        c.status(await ORG.mudarPapel(gb.id, ADM.id, 'ADMIN'), 200)
        await c.configurar(ORG, gb.id, { promotionMode: 'MANUAL' })
        const j = await c.jogo(ORG, gb.id, { vagas: 4 })
        for (const a of [ATL1, ATL2]) c.status(await a.vou(gb.id, j.id), 200)
        return { grupo: gb, jogo: j }
      })
      const semVaga = await c.passo('jogo C (cheio) e jogo D (sem espera): nada deve acontecer no prazo', async () => {
        const gc = await c.grupo(ORG, [ATL1, ATL2, ATL3], { nome: `QA Cheio ${c.tag}` })
        await c.configurar(ORG, gc.id, { mensalistaPriority: false })
        const cheio = await c.jogo(ORG, gc.id, { vagas: 2 })
        for (const a of [ATL1, ATL2, ATL3]) c.status(await a.vou(gc.id, cheio.id), 200)
        const sem = await c.jogo(ORG, gc.id, { vagas: 4 })
        c.status(await ATL1.vou(gc.id, sem.id), 200)
        return { grupo: gc, cheio, sem }
      })
      await c.passo('o prazo vence em todos os jogos (banco) e o job (a cada minuto) roda: A sobe 4 pela ordem e deixa 1; B não sobe ninguém', async () => {
        for (const j of [fifo, manual.jogo, semVaga.cheio, semVaga.sem]) await c.prazoPara(j.id, new Date(Date.now() - 60_000))
        await c.esperar('o job do prazo promover o jogo A', async () => (await c.valor(`select count(*) from game_attendance where game_id='${fifo.id}' and status='CONFIRMED'`)) === '4', { teto: 130_000, passo: 2_000 })
        const confirmados = await c.sql(`select member_display_name from game_attendance where game_id='${fifo.id}' and status='CONFIRMED' order by responded_at`)
        c.igual(confirmados.length, 4, 'confirmados em A')
        c.igual(await c.valor(`select count(*) from game_attendance where game_id='${fifo.id}' and status='WAITLISTED'`), '1', 'sobra 1 na espera')
        c.igual(await c.valor(`select count(*) from game_attendance where game_id='${manual.jogo.id}' and status='CONFIRMED'`), '0', 'B manual: ninguém sobe')
      })
      await c.passo('B (manual): dono e admin recebem um aviso cada ("Prazo de confirmação encerrado"), os atletas não', async () => {
        const aviso = async (a) => (await a.avisosDoJogo(manual.jogo.id)).filter((n) => n.canal === 'WAITLIST_DEADLINE')
        for (const a of [ORG, ADM]) {
          const x = await c.esperar(`aviso do prazo para ${a.papel}`, async () => { const v = await aviso(a); return v.length ? v : null }, { teto: 130_000, passo: 2_000 })
          c.igual(x.length, 1, `${a.papel}: um aviso por jogo`)
          c.afirmar(/Prazo de confirmação encerrado/.test(x[0].corpo), `texto: ${x[0].corpo}`)
        }
        c.igual((await aviso(ATL1)).length, 0, 'atleta não recebe o aviso do organizador')
      })
      await c.passo('C (cheio) e D (sem espera): nada acontece, nem aviso; rodar de novo (próximo minuto) não repete nada', async () => {
        c.igual(await c.valor(`select count(*) from group_messages where game_id in ('${semVaga.cheio.id}','${semVaga.sem.id}') and channel='WAITLIST_DEADLINE'`), '0', 'avisos de prazo em C e D')
        const eventosAntes = await c.valor(`select count(*) from attendance_events where game_id in ('${fifo.id}','${manual.jogo.id}')`)
        await c.dormir(65_000)
        c.igual(await c.valor(`select count(*) from attendance_events where game_id in ('${fifo.id}','${manual.jogo.id}')`), eventosAntes, 'eventos depois de outra rodada do job')
        c.igual(await c.valor(`select count(*) from group_messages where game_id='${manual.jogo.id}' and channel='WAITLIST_DEADLINE'`), '1', 'avisos do jogo B')
      })
    },
  },
  {
    id: 'PROM-006', feature: 'promocao', critico: false,
    titulo: 'Aumentar e reduzir vagas',
    async rodar(c) {
      const { ORG, ADM, ATL1, ATL2, ATL3, ATL4, ATL5, ATL6 } = await c.atores('ORG', 'ADM', 'ATL1', 'ATL2', 'ATL3', 'ATL4', 'ATL5', 'ATL6')
      const g = await c.passo('ORG cria o grupo; ADM vira admin; ATL1 a ATL6 entram; prioridade desligada', async () => {
        const grupo = await c.grupo(ORG, [ADM, ATL1, ATL2, ATL3, ATL4, ATL5, ATL6])
        c.status(await ORG.mudarPapel(grupo.id, ADM.id, 'ADMIN'), 200)
        await c.configurar(ORG, grupo.id, { mensalistaPriority: false })
        return grupo
      })
      const cheio = async (vagas, quantos) => {
        const j = await c.jogo(ORG, g.id, { vagas })
        for (const a of [ATL1, ATL2, ATL3, ATL4, ATL5, ATL6].slice(0, quantos)) c.status(await a.vou(g.id, j.id), 200)
        return j
      }
      await c.passo('aumentar na ordem de chegada: 4 vagas, 4 confirmados, 2 na espera; ADM aumenta para 6 e sobem os 2', async () => {
        const j = await cheio(4, 6)
        const r = c.status(await ADM.ajustarVagas(g.id, j.id, 6), 200)
        c.igual([r.corpo.capacity, r.corpo.promotedCount], [6, 2], 'vagas e promovidos')
      })
      await c.passo('valores fora de 4 a 100 são rejeitados (3 e 101); atleta comum recebe 403', async () => {
        const j = await cheio(4, 4)
        for (const ruim of [3, 101]) c.status(await ORG.ajustarVagas(g.id, j.id, ruim), 400)
        c.status(await ATL1.ajustarVagas(g.id, j.id, 8), 403)
      })
      await c.passo('reduzir não derruba ninguém: 6 confirmados e vagas reduzidas para 4; novo Vou vai para a espera', async () => {
        const j = await cheio(6, 6)
        const r = c.status(await ORG.ajustarVagas(g.id, j.id, 4), 200)
        c.igual(r.corpo.capacity, 4, 'vagas depois da redução')
        c.igual(await c.valor(`select count(*) from game_attendance where game_id='${j.id}' and status='CONFIRMED'`), '6', 'confirmados continuam')
        c.igual(r.corpo.detail.availableSpots, 0, 'vagas livres')
        c.status(await ADM.porOutro(g.id, j.id, ORG.id, 'CONFIRM', 'Chegou'), 200)
        c.igual(await c.statusDe(j.id, ORG), 'WAITLISTED', 'novo "Vou" com o jogo acima do limite')
      })
      await c.passo('escolha manual: aumentar as vagas não sobe ninguém', async () => {
        await c.configurar(ORG, g.id, { promotionMode: 'MANUAL', mensalistaPriority: false })
        const j = await c.jogo(ORG, g.id, { vagas: 4 })
        await c.prazoPara(j.id, new Date(Date.now() - 60_000))
        for (const a of [ATL1, ATL2]) c.status(await a.vou(g.id, j.id), 200)
        const r = c.status(await ORG.ajustarVagas(g.id, j.id, 6), 200)
        c.igual(r.corpo.promotedCount, 0, 'promovidos no modo manual')
        c.igual(await c.statusDe(j.id, ATL1), 'WAITLISTED', 'ATL1 segue na espera')
        await c.configurar(ORG, g.id, { promotionMode: 'FIFO', mensalistaPriority: false })
      })
      await c.passo('rascunho, cancelado e jogo iniciado: o ajuste de vagas fica congelado', async () => {
        const rasc = await c.jogo(ORG, g.id, { vagas: 4, publicar: false })
        c.status(await ORG.ajustarVagas(g.id, rasc.id, 6), [403, 409, 422], 'ATTENDANCE_FROZEN')
        const canc = await c.jogo(ORG, g.id, { vagas: 4 })
        c.status(await ORG.cancelarJogo(g.id, canc.id), 200)
        c.status(await ORG.ajustarVagas(g.id, canc.id, 6), [403, 409, 422], 'ATTENDANCE_FROZEN')
        const ini = await c.jogo(ORG, g.id, { vagas: 4 })
        await c.inicioPara(ini.id, new Date(Date.now() - 5 * 60_000))
        c.status(await ORG.ajustarVagas(g.id, ini.id, 6), [403, 409, 422], 'ATTENDANCE_FROZEN')
      })
      await c.passo('dois organizadores salvam com a mesma versão: um passa e o outro recebe conflito', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 4 })
        const etag = (await ORG.jogo(g.id, j.id)).etag
        const salvar = (quem, n) => quem.chamar('PUT', `/api/groups/${g.id}/games/${j.id}/capacity`, { requestId: randomUUID(), capacity: n }, { 'If-Match': etag })
        const [a, b] = await Promise.all([salvar(ORG, 6), salvar(ADM, 8)])
        c.igual([a.status, b.status].sort(), [200, 409], 'um passa, o outro conflita')
      })
    },
  },
  {
    id: 'PROM-007', feature: 'promocao', critico: false,
    titulo: 'Posição na fila',
    async rodar(c) {
      const { ORG, ATL1, ATL2, ATL3, ATL4, ATL5 } = await c.atores('ORG', 'ATL1', 'ATL2', 'ATL3', 'ATL4', 'ATL5')
      const g = await c.passo('ORG cria o grupo; ATL1, ATL4 e ATL5 viram mensalistas; ATL2 e ATL3 avulsos', async () => {
        const grupo = await c.grupo(ORG, [ATL1, ATL2, ATL3, ATL4, ATL5])
        for (const a of [ATL1, ATL4, ATL5]) await c.tipo(ORG, grupo.id, a)
        return grupo
      })
      const posicoes = async (j, ...atores) => Promise.all(atores.map(async (a) => (await a.presenca(g.id, j.id)).corpo.ownAttendance?.waitlistPosition))
      const j = await c.passo('jogo de 2 vagas lotado por mensalistas (ATL4, ATL5); fila por chegada: A=ATL2 (avulso), M=ATL1 (mensalista), B=ATL3 (avulso)', async () => {
        const jogo = await c.jogo(ORG, g.id, { vagas: 2 })
        for (const a of [ATL4, ATL5, ATL2, ATL1, ATL3]) c.status(await a.vou(g.id, jogo.id), 200)
        return jogo
      })
      await c.passo('antes do prazo, com prioridade: 1º M (ATL1), 2º A (ATL2), 3º B (ATL3), igual para o dono na lista', async () => {
        c.igual(await posicoes(j, ATL1, ATL2, ATL3), [1, 2, 3], 'posições de ATL1, ATL2, ATL3')
        const lista = c.status(await ORG.lista(g.id, j.id), 200).corpo.waitlisted.map((m) => m.displayName)
        c.igual(lista, [ATL1, ATL2, ATL3].map((a) => a.nome), 'ordem da lista do organizador')
      })
      await c.passo('o prazo passa: a ordem vira só por chegada (1º A, 2º M, 3º B), sem ninguém entrar ou sair', async () => {
        await c.prazoPara(j.id, new Date(Date.now() - 60_000))
        c.igual(await posicoes(j, ATL2, ATL1, ATL3), [1, 2, 3], 'posições de ATL2, ATL1, ATL3')
        const lista = c.status(await ORG.lista(g.id, j.id), 200).corpo.waitlisted.map((m) => m.displayName)
        c.igual(lista, [ATL2, ATL1, ATL3].map((a) => a.nome), 'ordem da lista do organizador')
      })
      await c.passo('quando A sobe todo mundo sobe uma posição', async () => {
        c.status(await ATL4.naoVou(g.id, j.id), 200)
        c.igual(await c.statusDe(j.id, ATL2), 'CONFIRMED', 'ATL2 sobe')
        c.igual(await posicoes(j, ATL1, ATL3), [1, 2], 'posições de ATL1 e ATL3')
      })
    },
  },
  {
    id: 'PROM-008', feature: 'promocao', critico: false,
    titulo: 'A cobrança de quem sobe',
    async rodar(c) {
      const { ORG, ATL1, ATL2, ATL3, ATL4 } = await c.atores('ORG', 'ATL1', 'ATL2', 'ATL3', 'ATL4')
      const g = await c.passo('ORG cria o grupo; ATL1 é mensalista; ATL2, ATL3 e ATL4 avulsos; prioridade desligada', async () => {
        const grupo = await c.grupo(ORG, [ATL1, ATL2, ATL3, ATL4])
        await c.tipo(ORG, grupo.id, ATL1)
        await c.configurar(ORG, grupo.id, { mensalistaPriority: false })
        return grupo
      })
      const cobrancas = async (j, a) => (await ORG.cobrancas(g.id)).corpo.charges.filter((x) => x.gameId === j.id && x.memberId === a.id && !x.guestDisplayName)
      await c.passo('mensalista promovido da espera não gera cobrança; avulso promovido gera uma, uma só vez', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 2, valorCents: 2500 })
        c.status(await ATL2.vou(g.id, j.id), 200); c.status(await ATL3.vou(g.id, j.id), 200)
        c.status(await ATL1.vou(g.id, j.id), 200); c.status(await ATL4.vou(g.id, j.id), 200)
        c.status(await ATL2.naoVou(g.id, j.id), 200)   // ATL1 (mensalista) sobe
        c.igual((await cobrancas(j, ATL1)).length, 0, 'mensalista promovido não é cobrado')
        c.status(await ATL3.naoVou(g.id, j.id), 200)   // ATL4 (avulso) sobe
        c.igual((await cobrancas(j, ATL4)).length, 1, 'avulso promovido é cobrado')
        c.status(await ATL4.vou(g.id, j.id), 200)
        c.igual((await cobrancas(j, ATL4)).length, 1, 'confirmar de novo não cria outra')
      })
      await c.passo('escolha manual: o avulso colocado pelo organizador é cobrado na promoção', async () => {
        await c.configurar(ORG, g.id, { promotionMode: 'MANUAL', mensalistaPriority: false })
        const j = await c.jogo(ORG, g.id, { vagas: 4, valorCents: 2500 })
        c.status(await ATL2.vou(g.id, j.id), 200)
        c.igual((await cobrancas(j, ATL2)).length, 0, 'na espera não é cobrado')
        c.status(await ORG.colocarNoJogo(g.id, j.id, ATL2.id), 200)
        const [cob] = await cobrancas(j, ATL2)
        c.igual([cob?.status, cob?.amountCents], ['PENDING', 2500], 'cobrança na promoção')
      })
      await c.passo('jogo sem valor: nunca cria cobrança', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 4, valorCents: null })
        c.status(await ORG.porOutro(g.id, j.id, ATL3.id, 'CONFIRM', 'Confirmada pelo organizador'), 200)
        c.igual((await cobrancas(j, ATL3)).length, 0, 'sem valor, sem cobrança')
      })
    },
  },
  {
    id: 'PROM-009', feature: 'promocao', critico: false, achado: 'AVISAR-APÓS-PRAZO',
    titulo: 'Os avisos da lista de espera e da promoção',
    async rodar(c) {
      const { ORG, ATL1, ATL2, ATL3, ATL4 } = await c.atores('ORG', 'ATL1', 'ATL2', 'ATL3', 'ATL4')
      const g = await c.passo('ORG cria o grupo e desliga a prioridade; jogo de 2 vagas: ATL1 e ATL2 confirmados, ATL3 na espera, ATL4 Não vou', async () => {
        const grupo = await c.grupo(ORG, [ATL1, ATL2, ATL3, ATL4])
        await c.configurar(ORG, grupo.id, { mensalistaPriority: false })
        grupo.jogo = await c.jogo(ORG, grupo.id, { vagas: 2 })
        for (const a of [ATL1, ATL2, ATL3]) c.status(await a.vou(grupo.id, grupo.jogo.id), 200)
        c.status(await ATL4.naoVou(grupo.id, grupo.jogo.id), 200)
        return grupo
      })
      await c.passo('"Avisar" manual: o corpo traz prazo, vagas e as listas Confirmados, Lista de espera e Fora, uma pessoa por linha', async () => {
        const r = c.status(await ORG.avisarPendentes(g.id, g.jogo.id), 200)
        const corpo = r.corpo.body
        for (const trecho of ['Confirmações até', 'Lista completa', '✅ Confirmados', '🕒 Lista de espera', '❌ Fora', ATL1.nome, ATL3.nome, ATL4.nome]) c.afirmar(corpo.includes(trecho), `o lembrete deve conter "${trecho}":\n${corpo}`)
        c.afirmar(corpo.length <= 2000, 'corpo cortado em 2.000 caracteres')
      })
      await c.passo('depois do prazo o corpo mostra "Prazo encerrado"', async () => {
        await c.prazoPara(g.jogo.id, new Date(Date.now() - 60_000))
        const r = await ORG.avisarPendentes(g.id, g.jogo.id)
        c.divergir(r.status === 200, `"Avisar" depois do prazo (jogo ainda não começou) deveria mandar o lembrete com "Prazo encerrado", como o automático; veio HTTP ${r.status} ${r.texto.slice(0, 120)}`)
        if (r.status === 200) c.divergir(r.corpo.body.includes('Prazo encerrado'), `o lembrete deve conter "Prazo encerrado":\n${r.corpo.body}`)
      })
    },
  },
]
