// Presença e lista de espera (PRES-*, PROM-*). Um cenário por regra, passos no formato "ORG faz X; ATL1 faz Y; confere Z".
import { randomUUID } from 'node:crypto'

const MIN = 60_000

export default [
  {
    id: 'PRES-002', feature: 'presenca', critico: true,
    titulo: 'Início do jogo trava o atleta; o prazo nunca nega',
    async rodar(c) {
      const { ORG, ATL1, ATL2, ATL3 } = await c.atores('ORG', 'ATL1', 'ATL2', 'ATL3')
      const g = await c.passo('ORG cria o grupo; ATL1, ATL2 e ATL3 entram pelo convite', () => c.grupo(ORG, [ATL1, ATL2, ATL3]))
      await c.configurar(ORG, g.id, { mensalistaPriority: false })
      const j = await c.passo('ORG publica jogo de 4 vagas com prazo 2 min no futuro', () => c.jogo(ORG, g.id, { vagas: 4, prazoMin: 0, inicioMin: 120 }))
      await c.prazoPara(j.id, new Date(Date.now() + 2 * MIN))
      await c.passo('ATL1 diz Vou antes do prazo; confere CONFIRMED', async () => {
        c.status(await ATL1.vou(g.id, j.id), 200)
        c.igual(await c.statusDe(j.id, ATL1), 'CONFIRMED', 'ATL1 antes do prazo')
      })
      await c.passo('o prazo passa; ATL2 diz Vou e não é negada (o prazo nunca nega); ATL3 diz Não vou', async () => {
        await c.prazoPara(j.id, new Date(Date.now() - MIN))
        c.status(await ATL2.vou(g.id, j.id), 200)
        c.afirmar(['CONFIRMED', 'WAITLISTED'].includes(await c.statusDe(j.id, ATL2)), 'depois do prazo o Vou deve ir para a espera (ou subir na hora), nunca ser negado')
        c.status(await ATL3.naoVou(g.id, j.id), 200)
        c.igual(await c.statusDe(j.id, ATL3), 'DECLINED', 'ATL3 depois do prazo')
      })
      await c.passo('o jogo começa; ATL1 tenta Não vou e ATL3 tenta Vou: ambos travados (ATTENDANCE_FROZEN)', async () => {
        await c.inicioPara(j.id, new Date(Date.now() - MIN))
        c.status(await ATL1.naoVou(g.id, j.id), [403, 409, 422], 'ATTENDANCE_FROZEN')
        c.status(await ATL3.vou(g.id, j.id), [403, 409, 422], 'ATTENDANCE_FROZEN')
        c.igual(await c.statusDe(j.id, ATL1), 'CONFIRMED', 'ATL1 segue confirmado')
      })
      await c.passo('depois do início, ORG altera a presença de ATL3 com motivo; ATL2 não consegue', async () => {
        c.status(await ORG.porOutro(g.id, j.id, ATL3.id, 'CONFIRM', 'Apareceu no jogo'), 200)
        c.igual(await c.statusDe(j.id, ATL3), 'CONFIRMED', 'ATL3 pelo organizador')
        c.status(await ATL2.naoVou(g.id, j.id), [403, 409, 422], 'ATTENDANCE_FROZEN')
      })
    },
  },

  {
    id: 'PRES-005', feature: 'presenca', critico: true,
    titulo: '"Vou" depois do prazo vai sempre para a espera',
    async rodar(c) {
      const { ORG, ATL1, ATL2, ATL3, ATL4 } = await c.atores('ORG', 'ATL1', 'ATL2', 'ATL3', 'ATL4')
      const g = await c.passo('ORG cria o grupo; ATL1 a ATL4 entram; ATL1 vira mensalista', async () => {
        const grupo = await c.grupo(ORG, [ATL1, ATL2, ATL3, ATL4])
        await c.tipo(ORG, grupo.id, ATL1)
        return grupo
      })
      const vencido = async (vagas) => {
        const j = await c.jogo(ORG, g.id, { vagas })
        await c.prazoPara(j.id, new Date(Date.now() - MIN))
        return j
      }
      await c.passo('ordem de chegada, vaga livre: o prazo passou; ATL1 (mensalista) e ATL2 dizem Vou e passam pela espera antes de subir', async () => {
        const j = await vencido(4)
        c.status(await ATL1.vou(g.id, j.id), 200)
        c.status(await ATL2.vou(g.id, j.id), 200)
        for (const a of [ATL1, ATL2]) {
          c.igual(await c.statusDe(j.id, a), 'CONFIRMED', `${a.papel} subiu na hora`)
          c.igual((await c.eventos(j.id, a))[0], 'null>WAITLISTED:SELF', `${a.papel}: o Vou depois do prazo entra primeiro na espera`)
        }
      })
      await c.passo('o dono (ORG) também passa pela espera depois do prazo', async () => {
        const j = await vencido(4)
        c.status(await ORG.vou(g.id, j.id), 200)
        c.igual((await c.eventos(j.id, ORG))[0], 'null>WAITLISTED:SELF', 'dono depois do prazo')
      })
      await c.passo('ordem de chegada, jogo cheio: ATL3 e ATL4 confirmam o jogo de 2 vagas; ATL2 vai para o fim da fila', async () => {
        const j = await vencido(2)
        c.status(await ATL3.vou(g.id, j.id), 200)
        c.status(await ATL4.vou(g.id, j.id), 200)
        c.status(await ATL2.vou(g.id, j.id), 200)
        c.igual(await c.statusDe(j.id, ATL2), 'WAITLISTED', 'ATL2 sem vaga')
        c.igual(await c.valor(`select waitlist_sequence from game_attendance where game_id='${j.id}' and member_user_id='${ATL2.id}'`) !== null, true, 'ATL2 tem posição na fila')
      })
      await c.passo('escolha manual: ORG troca o modo; com vaga livre e prazo vencido, ATL3 diz Vou e fica na espera até o organizador escolher', async () => {
        await c.configurar(ORG, g.id, { promotionMode: 'MANUAL' })
        const j = await vencido(4)
        c.status(await ATL3.vou(g.id, j.id), 200)
        await c.dormir(3000)
        c.igual(await c.statusDe(j.id, ATL3), 'WAITLISTED', 'ATL3 em modo manual')
      })
    },
  },
  {
    id: 'PRES-006', feature: 'presenca', critico: true,
    titulo: '"Vou" antes do prazo, em ordem de chegada (prioridade do mensalista)',
    async rodar(c) {
      const { ORG, ATL1, ATL2, ATL3, ATL4 } = await c.atores('ORG', 'ATL1', 'ATL2', 'ATL3', 'ATL4')
      const g = await c.passo('ORG cria o grupo; ATL1, ATL3 e ATL4 viram mensalistas; ATL2 segue avulso', async () => {
        const grupo = await c.grupo(ORG, [ATL1, ATL2, ATL3, ATL4])
        for (const a of [ATL1, ATL3, ATL4]) await c.tipo(ORG, grupo.id, a)
        return grupo
      })
      await c.passo('prioridade ligada, vaga livre: mensalista ATL1 confirma; avulso ATL2 vai para a espera', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 4 })
        c.status(await ATL1.vou(g.id, j.id), 200)
        c.status(await ATL2.vou(g.id, j.id), 200)
        c.igual(await c.statusDe(j.id, ATL1), 'CONFIRMED', 'mensalista com prioridade')
        c.igual(await c.statusDe(j.id, ATL2), 'WAITLISTED', 'avulso com prioridade ligada')
      })
      await c.passo('sem vaga: jogo de 2 vagas; ATL1 e ATL3 (mensalistas) lotam; ATL4 (mensalista) e ATL2 (avulso) vão para a espera', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 2 })
        c.status(await ATL1.vou(g.id, j.id), 200)
        c.status(await ATL3.vou(g.id, j.id), 200)
        c.status(await ATL4.vou(g.id, j.id), 200)
        c.status(await ATL2.vou(g.id, j.id), 200)
        c.igual([await c.statusDe(j.id, ATL1), await c.statusDe(j.id, ATL3)], ['CONFIRMED', 'CONFIRMED'], 'mensalistas lotam')
        c.igual([await c.statusDe(j.id, ATL4), await c.statusDe(j.id, ATL2)], ['WAITLISTED', 'WAITLISTED'], 'sem vaga todos esperam')
      })
      await c.passo('dois mensalistas disputam a última vaga em paralelo: um confirma e o outro espera', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 2 })
        c.status(await ATL1.vou(g.id, j.id), 200)
        const rs = await Promise.all([ATL3.vou(g.id, j.id), ATL4.vou(g.id, j.id)])
        rs.forEach((r) => c.status(r, 200))
        const estados = [await c.statusDe(j.id, ATL3), await c.statusDe(j.id, ATL4)].sort()
        c.igual(estados, ['CONFIRMED', 'WAITLISTED'], 'uma vaga, dois mensalistas')
        c.igual(await c.valor(`select count(*) from game_attendance where game_id='${j.id}' and status='CONFIRMED'`), '2', 'confirmados')
      })
      await c.passo('ORG desliga a prioridade: avulso ATL2 e mensalista ATL1 confirmam direto, por ordem de chegada', async () => {
        await c.configurar(ORG, g.id, { mensalistaPriority: false })
        const j = await c.jogo(ORG, g.id, { vagas: 4 })
        c.status(await ATL2.vou(g.id, j.id), 200)
        c.status(await ATL1.vou(g.id, j.id), 200)
        c.igual([await c.statusDe(j.id, ATL2), await c.statusDe(j.id, ATL1)], ['CONFIRMED', 'CONFIRMED'], 'prioridade desligada')
      })
    },
  },
  {
    id: 'PRES-009', feature: 'presenca', critico: true,
    titulo: 'Repetir a mesma resposta não muda nada (toque duplo)',
    async rodar(c) {
      const { ORG, ATL1, ATL2, ATL3, ATL4 } = await c.atores('ORG', 'ATL1', 'ATL2', 'ATL3', 'ATL4')
      const g = await c.passo('ORG cria o grupo, desliga a prioridade e marca jogo de 2 vagas com diária de R$ 20', async () => {
        const grupo = await c.grupo(ORG, [ATL1, ATL2, ATL3, ATL4])
        await c.configurar(ORG, grupo.id, { mensalistaPriority: false })
        return grupo
      })
      const j = await c.jogo(ORG, g.id, { vagas: 2, valorCents: 2000 })
      const linhas = (a) => c.valor(`select count(*) from game_attendance where game_id='${j.id}' and member_user_id='${a.id}'`)
      const versao = (a) => c.valor(`select version from game_attendance where game_id='${j.id}' and member_user_id='${a.id}'`)
      await c.passo('ATL1 toca Vou 5 vezes ao mesmo tempo: uma linha, um evento, uma cobrança', async () => {
        const rs = await Promise.all(Array.from({ length: 5 }, () => ATL1.vou(g.id, j.id)))
        rs.forEach((r) => c.status(r, 200))
        c.igual(await c.statusDe(j.id, ATL1), 'CONFIRMED', 'ATL1')
        c.igual(await linhas(ATL1), '1', 'linhas de presença')
        c.igual((await c.eventos(j.id, ATL1)).length, 1, 'eventos de presença')
        const cob = await ORG.cobrancas(g.id)
        c.igual(cob.corpo.charges.filter((x) => x.memberId === ATL1.id).length, 1, 'cobranças de ATL1')
      })
      await c.passo('ATL1 toca Vou de novo (nova chamada, outro requestId): segue confirmado, versão igual', async () => {
        const antes = await versao(ATL1)
        c.status(await ATL1.vou(g.id, j.id), 200)
        c.igual(await versao(ATL1), antes, 'versão da presença')
        c.igual((await c.eventos(j.id, ATL1)).length, 1, 'eventos de presença')
      })
      await c.passo('o mesmo requestId reenviado por falha de rede devolve o mesmo resultado', async () => {
        const id = randomUUID()
        const a = await ATL2.vou(g.id, j.id, id)
        const b = await ATL2.vou(g.id, j.id, id)
        c.status(a, 200); c.status(b, 200)
        c.igual(b.corpo.attendance.status, a.corpo.attendance.status, 'status repetido')
        c.igual((await c.eventos(j.id, ATL2)).length, 1, 'eventos de ATL2')
      })
      await c.passo('jogo cheio: ATL3 entra na espera e toca Vou de novo: mesma posição, sem novo evento', async () => {
        c.status(await ATL3.vou(g.id, j.id), 200)
        const fila = await c.valor(`select waitlist_sequence from game_attendance where game_id='${j.id}' and member_user_id='${ATL3.id}'`)
        c.status(await ATL3.vou(g.id, j.id), 200)
        c.igual(await c.statusDe(j.id, ATL3), 'WAITLISTED', 'ATL3')
        c.igual(await c.valor(`select waitlist_sequence from game_attendance where game_id='${j.id}' and member_user_id='${ATL3.id}'`), fila, 'posição na fila')
        c.igual((await c.eventos(j.id, ATL3)).length, 1, 'eventos de ATL3')
      })
      await c.passo('ATL4 toca Não vou duas vezes: continua Não vou, um só evento', async () => {
        c.status(await ATL4.naoVou(g.id, j.id), 200)
        c.status(await ATL4.naoVou(g.id, j.id), 200)
        c.igual(await c.statusDe(j.id, ATL4), 'DECLINED', 'ATL4')
        c.igual((await c.eventos(j.id, ATL4)).length, 1, 'eventos de ATL4')
      })
      await c.passo('última vaga disputada: ATL1 sai; ATL3 e ATL4 tocam Vou em paralelo e só um sobe', async () => {
        const jogo2 = await c.jogo(ORG, g.id, { vagas: 2 })
        c.status(await ATL1.vou(g.id, jogo2.id), 200)
        const rs = await Promise.all([ATL3.vou(g.id, jogo2.id), ATL4.vou(g.id, jogo2.id), ATL2.vou(g.id, jogo2.id)])
        rs.forEach((r) => c.status(r, 200))
        c.igual(await c.valor(`select count(*) from game_attendance where game_id='${jogo2.id}' and status='CONFIRMED'`), '2', 'confirmados em 2 vagas')
        c.igual(await c.valor(`select count(*) from game_attendance where game_id='${jogo2.id}' and status='WAITLISTED'`), '2', 'na espera')
      })
    },
  },
]
