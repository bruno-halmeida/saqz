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
]
