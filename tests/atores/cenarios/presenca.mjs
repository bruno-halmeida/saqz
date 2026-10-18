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
{
    id: 'PRES-001', feature: 'presenca', critico: false,
    titulo: 'Só se responde em jogo publicado',
    async rodar(c) {
      const { ORG, ATL1, ATL2 } = await c.atores('ORG', 'ATL1', 'ATL2')
      const g = await c.passo('ORG cria o grupo; ATL1 e ATL2 entram', async () => {
        const grupo = await c.grupo(ORG, [ATL1, ATL2])
        await c.configurar(ORG, grupo.id, { mensalistaPriority: false })
        return grupo
      })
      await c.passo('rascunho: o atleta nem enxerga o jogo e o organizador não responde por ninguém (presença congelada)', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 4, publicar: false })
        c.status(await ATL1.vou(g.id, j.id), [403, 404, 409, 422])
        c.status(await ORG.porOutro(g.id, j.id, ATL1.id, 'CONFIRM', 'Veio avisar'), [403, 404, 409, 422], 'ATTENDANCE_FROZEN')
        c.igual(await c.statusDe(j.id, ATL1), null, 'nenhuma presença gravada no rascunho')
      })
      await c.passo('publicado: atleta e organizador respondem', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 4 })
        c.status(await ATL1.vou(g.id, j.id), 200)
        c.status(await ORG.porOutro(g.id, j.id, ATL2.id, 'CONFIRM', 'Avisou por fora'), 200)
        c.igual([await c.statusDe(j.id, ATL1), await c.statusDe(j.id, ATL2)], ['CONFIRMED', 'CONFIRMED'], 'presenças')
      })
      for (const estado of ['CANCELLED', 'COMPLETED']) {
        await c.passo(`jogo ${estado === 'CANCELLED' ? 'cancelado' : 'concluído'}: a presença congela para o atleta e para o organizador`, async () => {
          const j = await c.jogo(ORG, g.id, { vagas: 4 })
          c.status(await ATL1.vou(g.id, j.id), 200)
          await c.sql(`update games set status='${estado}', version=version+1 where id='${j.id}'`)
          c.status(await ATL1.naoVou(g.id, j.id), [403, 409, 422], 'ATTENDANCE_FROZEN')
          c.status(await ATL2.vou(g.id, j.id), [403, 409, 422], 'ATTENDANCE_FROZEN')
          c.status(await ORG.porOutro(g.id, j.id, ATL1.id, 'DECLINE', 'Não veio'), [403, 409, 422], 'ATTENDANCE_FROZEN')
          c.igual(await c.statusDe(j.id, ATL1), 'CONFIRMED', 'presença como estava')
        })
      }
    },
  },
  {
    id: 'PRES-003', feature: 'presenca', critico: false,
    titulo: 'Cada pessoa responde só por si',
    async rodar(c) {
      const { ORG, ADM, ADM2, ATL1, ATL2, FORA } = await c.atores('ORG', 'ADM', 'ADM2', 'ATL1', 'ATL2', 'FORA')
      const g = await c.passo('ORG cria o grupo; ADM e ADM2 viram administradores; ATL1 e ATL2 entram', async () => {
        const grupo = await c.grupo(ORG, [ADM, ADM2, ATL1, ATL2])
        for (const a of [ADM, ADM2]) c.status(await ORG.mudarPapel(grupo.id, a.id, 'ADMIN'), 200)
        await c.configurar(ORG, grupo.id, { mensalistaPriority: false })
        grupo.jogo = await c.jogo(ORG, grupo.id, { vagas: 4 })
        return grupo
      })
      await c.passo('ATL1 responde por si: permitido', async () => { c.status(await ATL1.vou(g.id, g.jogo.id), 200) })
      await c.passo('ATL1 tenta a rota do organizador para mexer na presença de ATL2: 403 e nada muda', async () => {
        c.status(await ATL1.porOutro(g.id, g.jogo.id, ATL2.id, 'CONFIRM', 'Eu confirmo por ele'), 403)
        c.igual(await c.statusDe(g.jogo.id, ATL2), null, 'ATL2 sem presença')
      })
      await c.passo('ADM (admin) altera a presença de ATL2, do dono ORG e do outro admin ADM2, sempre com motivo', async () => {
        for (const alvo of [ATL2, ORG, ADM2]) c.status(await ADM.porOutro(g.id, g.jogo.id, alvo.id, 'CONFIRM', 'Estava no local'), 200)
        c.igual([await c.statusDe(g.jogo.id, ATL2), await c.statusDe(g.jogo.id, ORG), await c.statusDe(g.jogo.id, ADM2)], ['CONFIRMED', 'CONFIRMED', 'CONFIRMED'], 'presenças')
        c.igual((await c.eventos(g.jogo.id, ATL2))[0], 'null>CONFIRMED:ORGANIZER', 'origem do evento')
      })
      await c.passo('quem está fora do grupo não enxerga o jogo: 404 para ler, responder e usar a rota do organizador', async () => {
        c.status(await FORA.jogo(g.id, g.jogo.id), 404)
        c.status(await FORA.vou(g.id, g.jogo.id), 404)
        c.status(await FORA.porOutro(g.id, g.jogo.id, ATL1.id, 'DECLINE', 'Tentando'), [403, 404])
        c.status(await FORA.presenca(g.id, g.jogo.id), 404)
      })
    },
  },
  {
    id: 'PRES-004', feature: 'presenca', critico: false,
    titulo: '"Não vou" sempre vale e libera a vaga',
    async rodar(c) {
      const { ORG, ATL1, ATL2, ATL3, ATL4 } = await c.atores('ORG', 'ATL1', 'ATL2', 'ATL3', 'ATL4')
      const g = await c.passo('ORG cria o grupo e desliga a prioridade', async () => {
        const grupo = await c.grupo(ORG, [ATL1, ATL2, ATL3, ATL4])
        await c.configurar(ORG, grupo.id, { mensalistaPriority: false })
        return grupo
      })
      await c.passo('sem resposta + Não vou: vira Não vou e não libera vaga nem mexe na fila', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 2 })
        c.status(await ATL1.vou(g.id, j.id), 200); c.status(await ATL2.vou(g.id, j.id), 200); c.status(await ATL3.vou(g.id, j.id), 200)
        c.status(await ATL4.naoVou(g.id, j.id), 200)
        c.igual(await c.statusDe(j.id, ATL4), 'DECLINED', 'ATL4')
        c.igual(await c.statusDe(j.id, ATL3), 'WAITLISTED', 'ATL3 segue na espera')
      })
      await c.passo('confirmado + Não vou: libera 1 vaga e a fila anda; Não vou de novo não muda nada', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 2 })
        c.status(await ATL1.vou(g.id, j.id), 200); c.status(await ATL2.vou(g.id, j.id), 200); c.status(await ATL3.vou(g.id, j.id), 200)
        c.status(await ATL1.naoVou(g.id, j.id), 200)
        c.igual([await c.statusDe(j.id, ATL1), await c.statusDe(j.id, ATL3)], ['DECLINED', 'CONFIRMED'], 'ATL1 fora, ATL3 sobe')
        const antes = (await c.eventos(j.id, ATL1)).length
        c.status(await ATL1.naoVou(g.id, j.id), 200)
        c.igual((await c.eventos(j.id, ATL1)).length, antes, 'Não vou repetido não cria evento')
      })
      await c.passo('na espera + Não vou: sai da fila e não é cobrado', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 2, valorCents: 2000 })
        c.status(await ATL1.vou(g.id, j.id), 200); c.status(await ATL2.vou(g.id, j.id), 200); c.status(await ATL3.vou(g.id, j.id), 200)
        c.status(await ATL3.naoVou(g.id, j.id), 200)
        c.igual(await c.statusDe(j.id, ATL3), 'DECLINED', 'ATL3')
        c.igual((await ORG.cobrancas(g.id)).corpo.charges.filter((x) => x.gameId === j.id && x.memberId === ATL3.id).length, 0, 'cobranças de ATL3')
      })
      await c.passo('depois do prazo ainda dá para dizer Não vou; a cobrança de quem estava confirmado continua pendente com a etiqueta "Desistiu"', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 4, valorCents: 2000 })
        c.status(await ATL1.vou(g.id, j.id), 200)
        await c.prazoPara(j.id, new Date(Date.now() - 60_000))
        c.status(await ATL1.naoVou(g.id, j.id), 200)
        const [cob] = (await ORG.cobrancas(g.id)).corpo.charges.filter((x) => x.gameId === j.id && x.memberId === ATL1.id)
        c.igual([cob.status, cob.withdrew], ['PENDING', true], 'cobrança do desistente')
      })
      await c.passo('anfitrião que desiste leva os convidados junto, antes da fila andar', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 4 })
        c.status(await ATL1.vou(g.id, j.id), 200)
        c.status(await ATL2.vou(g.id, j.id), 200)
        c.status(await ATL1.convidado(g.id, j.id, 'Convidada Um'), 201)
        c.status(await ATL3.vou(g.id, j.id), 200); c.status(await ATL4.vou(g.id, j.id), 200)
        c.status(await ATL1.naoVou(g.id, j.id), 200)
        c.igual(await c.valor(`select status from game_attendance where game_id='${j.id}' and member_user_id='${ATL1.id}' and guest_seq=1`), 'DECLINED', 'convidado saiu junto')
        c.igual((await c.eventos(j.id, ATL1, 1)).at(-1).endsWith(':SYSTEM'), true, 'saída do convidado é evento de sistema')
      })
    },
  },
  {
    id: 'PRES-007', feature: 'presenca', critico: false,
    titulo: '"Vou" antes do prazo, em escolha manual',
    async rodar(c) {
      const { ORG, ATL1, ATL2, ATL3, ATL4 } = await c.atores('ORG', 'ATL1', 'ATL2', 'ATL3', 'ATL4')
      const g = await c.passo('ORG cria o grupo no modo escolha manual; ATL1, ATL3 e ATL4 viram mensalistas; ATL2 avulso', async () => {
        const grupo = await c.grupo(ORG, [ATL1, ATL2, ATL3, ATL4])
        for (const a of [ATL1, ATL3, ATL4]) await c.tipo(ORG, grupo.id, a)
        await c.configurar(ORG, grupo.id, { promotionMode: 'MANUAL', mensalistaPriority: true })
        return grupo
      })
      await c.passo('prioridade ligada: mensalista com vaga confirma; avulso vai para a espera mesmo com vaga', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 4 })
        c.status(await ATL1.vou(g.id, j.id), 200); c.status(await ATL2.vou(g.id, j.id), 200)
        c.igual([await c.statusDe(j.id, ATL1), await c.statusDe(j.id, ATL2)], ['CONFIRMED', 'WAITLISTED'], 'mensalista confirma, avulso espera')
      })
      await c.passo('prioridade ligada, sem vaga: mensalista também espera', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 2 })
        c.status(await ATL1.vou(g.id, j.id), 200); c.status(await ATL3.vou(g.id, j.id), 200); c.status(await ATL4.vou(g.id, j.id), 200)
        c.igual(await c.statusDe(j.id, ATL4), 'WAITLISTED', 'terceiro mensalista espera')
      })
      await c.passo('prioridade desligada: todo "Vou" vai para a espera, mensalista inclusive', async () => {
        await c.configurar(ORG, g.id, { promotionMode: 'MANUAL', mensalistaPriority: false })
        const j = await c.jogo(ORG, g.id, { vagas: 4 })
        c.status(await ATL1.vou(g.id, j.id), 200); c.status(await ATL2.vou(g.id, j.id), 200)
        c.igual([await c.statusDe(j.id, ATL1), await c.statusDe(j.id, ATL2)], ['WAITLISTED', 'WAITLISTED'], 'ninguém confirma sozinho')
        await c.dormir(1500)
        c.igual(await c.valor(`select count(*) from game_attendance where game_id='${j.id}' and status='CONFIRMED'`), '0', 'nada sobe sozinho')
      })
    },
  },
  {
    id: 'PRES-008', feature: 'presenca', critico: false,
    titulo: 'Convidado entra sempre pela espera',
    async rodar(c) {
      const { ORG, ADM, ATL1, ATL2, ATL3 } = await c.atores('ORG', 'ADM', 'ATL1', 'ATL2', 'ATL3')
      const g = await c.passo('ORG cria o grupo, ADM vira admin, desliga a prioridade; jogo de 4 vagas com diária de R$ 20', async () => {
        const grupo = await c.grupo(ORG, [ADM, ATL1, ATL2, ATL3])
        c.status(await ORG.mudarPapel(grupo.id, ADM.id, 'ADMIN'), 200)
        await c.configurar(ORG, grupo.id, { mensalistaPriority: false })
        grupo.jogo = await c.jogo(ORG, grupo.id, { vagas: 4, valorCents: 2000 })
        return grupo
      })
      await c.passo('quem não respondeu ou disse Não vou não leva convidado ("Responda Vou"): bloqueado', async () => {
        c.status(await ATL2.convidado(g.id, g.jogo.id, 'Convidado Sem Resposta'), [403, 409, 422], 'ATTENDANCE_HOST_NOT_GOING')
        c.status(await ATL2.naoVou(g.id, g.jogo.id), 200)
        c.status(await ATL2.convidado(g.id, g.jogo.id, 'Convidado De Quem Recusou'), [403, 409, 422], 'ATTENDANCE_HOST_NOT_GOING')
      })
      await c.passo('anfitrião confirmado leva 3 convidados com vaga sobrando: todos entram na espera, sem cobrança (sem limite de convidados)', async () => {
        c.status(await ATL1.vou(g.id, g.jogo.id), 200)
        for (const n of ['Amigo Um', 'Amigo Dois', 'Amigo Tres']) c.igual(c.status(await ATL1.convidado(g.id, g.jogo.id, n), 201).corpo.status, 'WAITLISTED', `convidado ${n}`)
        c.igual((await ORG.cobrancas(g.id)).corpo.charges.filter((x) => x.memberId === ATL1.id).length, 1, 'só a diária do anfitrião; convidado na espera não gera')
      })
      await c.passo('anfitrião na espera também pode levar convidado (entra no fim da fila)', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 2 })
        c.status(await ATL1.vou(g.id, j.id), 200); c.status(await ATL3.vou(g.id, j.id), 200)
        c.status(await ADM.vou(g.id, j.id), 200)
        c.igual(await c.statusDe(j.id, ADM), 'WAITLISTED', 'ADM espera')
        c.igual(c.status(await ADM.convidado(g.id, j.id, 'Amigo Do Admin'), 201).corpo.status, 'WAITLISTED', 'convidado do anfitrião na espera')
      })
      await c.passo('abre vaga e o convidado sobe com a diária no nome do anfitrião', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 4, valorCents: 3000 })
        c.status(await ATL1.vou(g.id, j.id), 200); c.status(await ATL3.vou(g.id, j.id), 200)
        const conv = c.status(await ATL1.convidado(g.id, j.id, 'Amigo Que Sobe'), 201)
        c.status(await ATL3.naoVou(g.id, j.id), 200)
        c.igual(await c.valor(`select status from game_attendance where game_id='${j.id}' and member_user_id='${ATL1.id}' and guest_seq=${conv.corpo.guestSeq}`), 'CONFIRMED', 'convidado subiu')
        const cob = (await ORG.cobrancas(g.id)).corpo.charges.filter((x) => x.gameId === j.id && x.memberId === ATL1.id && x.guestDisplayName)
        c.igual([cob.length, cob[0]?.guestDisplayName, cob[0]?.amountCents], [1, 'Amigo Que Sobe', 3000], 'cobrança do convidado')
      })
      await c.passo('admin remove convidado de outro anfitrião: permitido; atleta que não é o anfitrião não pode', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 4 })
        c.status(await ATL1.vou(g.id, j.id), 200)
        const conv = c.status(await ATL1.convidado(g.id, j.id, 'Amigo Para Remover'), 201)
        c.status(await ATL3.removerConvidado(g.id, j.id, ATL1.id, conv.corpo.guestSeq), [403, 404])
        c.status(await ADM.removerConvidado(g.id, j.id, ATL1.id, conv.corpo.guestSeq), 204)
        c.igual(await c.valor(`select status from game_attendance where game_id='${j.id}' and member_user_id='${ATL1.id}' and guest_seq=${conv.corpo.guestSeq}`), 'DECLINED', 'convidado removido')
      })
      await c.passo('anfitrião diz Não vou: os convidados saem junto, por evento de sistema, e a fila anda sem promover convidado que cairia', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 2 })
        c.status(await ATL1.vou(g.id, j.id), 200); c.status(await ATL3.vou(g.id, j.id), 200)
        const conv = c.status(await ATL1.convidado(g.id, j.id, 'Amigo Que Cai'), 201)
        c.status(await ATL2.vou(g.id, j.id), 200)
        c.status(await ATL1.naoVou(g.id, j.id), 200)
        c.igual(await c.valor(`select status from game_attendance where game_id='${j.id}' and member_user_id='${ATL1.id}' and guest_seq=${conv.corpo.guestSeq}`), 'DECLINED', 'convidado saiu')
        c.igual(await c.statusDe(j.id, ATL2), 'CONFIRMED', 'ATL2, da fila, sobe')
      })
    },
  },
  {
    id: 'PRES-010', feature: 'presenca', critico: false,
    titulo: 'O mesmo requestId só é repetição se for a última resposta',
    async rodar(c) {
      const { ORG, ATL1 } = await c.atores('ORG', 'ATL1')
      const g = await c.passo('ORG cria o grupo e desliga a prioridade', async () => {
        const grupo = await c.grupo(ORG, [ATL1])
        await c.configurar(ORG, grupo.id, { mensalistaPriority: false })
        return grupo
      })
      const sequencia = async (rotulo, passos, finalEsperado, eventosEsperados) => {
        const j = await c.jogo(ORG, g.id, { vagas: 4 })
        const ids = { V: randomUUID(), N: randomUUID() }
        for (const [intent, chave] of passos) c.status(await ATL1.responder(g.id, j.id, intent, ids[chave]), 200)
        c.igual(await c.statusDe(j.id, ATL1), finalEsperado, `${rotulo}: resultado final`)
        c.igual((await c.eventos(j.id, ATL1)).length, eventosEsperados, `${rotulo}: eventos`)
      }
      await c.passo('Vou, Não vou, Vou pelo mesmo código: confirmado com 3 eventos', () => sequencia('V,N,V', [['CONFIRM', 'V'], ['DECLINE', 'N'], ['CONFIRM', 'V']], 'CONFIRMED', 3))
      await c.passo('Não vou, Vou, Não vou: recusado com 3 eventos', () => sequencia('N,V,N', [['DECLINE', 'N'], ['CONFIRM', 'V'], ['DECLINE', 'N']], 'DECLINED', 3))
      await c.passo('Vou, Vou (retry imediato): confirmado com 1 evento', () => sequencia('V,V', [['CONFIRM', 'V'], ['CONFIRM', 'V']], 'CONFIRMED', 1))
      await c.passo('Vou, Não vou, retry do Não vou: recusado com 2 eventos (só repete a última)', () => sequencia('V,N,N', [['CONFIRM', 'V'], ['DECLINE', 'N'], ['DECLINE', 'N']], 'DECLINED', 2))
    },
  },
  {
    id: 'PRES-011', feature: 'presenca', critico: false,
    titulo: 'Quem desiste e volta vai para o fim da fila',
    async rodar(c) {
      const { ORG, ATL1, ATL2, ATL3, ATL4 } = await c.atores('ORG', 'ATL1', 'ATL2', 'ATL3', 'ATL4')
      const g = await c.passo('ORG cria o grupo e desliga a prioridade', async () => {
        const grupo = await c.grupo(ORG, [ATL1, ATL2, ATL3, ATL4])
        await c.configurar(ORG, grupo.id, { mensalistaPriority: false })
        return grupo
      })
      const posicao = async (a, j) => (await a.presenca(g.id, j.id)).corpo.ownAttendance?.waitlistPosition
      await c.passo('jogo de 2 vagas cheio: ATL3 é 1º da fila e ATL4 é 2º; ATL3 desiste e volta: vai para o fim (2º) e ATL4 passa a 1º', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 2 })
        for (const a of [ATL1, ATL2, ATL3, ATL4]) c.status(await a.vou(g.id, j.id), 200)
        c.igual([await posicao(ATL3, j), await posicao(ATL4, j)], [1, 2], 'fila antes')
        c.status(await ATL3.naoVou(g.id, j.id), 200)
        c.status(await ATL3.vou(g.id, j.id), 200)
        c.igual([await posicao(ATL4, j), await posicao(ATL3, j)], [1, 2], 'fila depois: ATL3 foi para o fim')
      })
      await c.passo('na espera em 2º, tocar Vou de novo mantém o 2º', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 2 })
        for (const a of [ATL1, ATL2, ATL3, ATL4]) c.status(await a.vou(g.id, j.id), 200)
        c.status(await ATL4.vou(g.id, j.id), 200)
        c.igual(await posicao(ATL4, j), 2, 'ATL4 continua em 2º')
      })
      await c.passo('confirmado que desistiu e volta com o jogo cheio: espera no fim da fila com número novo', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 2 })
        for (const a of [ATL1, ATL2, ATL3]) c.status(await a.vou(g.id, j.id), 200)
        c.status(await ATL1.naoVou(g.id, j.id), 200)  // ATL3 sobe
        c.status(await ATL1.vou(g.id, j.id), 200)     // jogo cheio de novo
        c.igual(await c.statusDe(j.id, ATL1), 'WAITLISTED', 'ATL1 na espera')
        c.igual(await posicao(ATL1, j), 1, 'único da fila')
      })
    },
  },
  {
    id: 'PRES-012', feature: 'presenca', critico: false,
    titulo: 'O organizador responde por alguém',
    async rodar(c) {
      const { ORG, ADM, ATL1, ATL2, ATL3 } = await c.atores('ORG', 'ADM', 'ATL1', 'ATL2', 'ATL3')
      const g = await c.passo('ORG cria o grupo, ADM vira admin; jogo de 2 vagas com diária de R$ 20', async () => {
        const grupo = await c.grupo(ORG, [ADM, ATL1, ATL2, ATL3])
        c.status(await ORG.mudarPapel(grupo.id, ADM.id, 'ADMIN'), 200)
        await c.configurar(ORG, grupo.id, { mensalistaPriority: false })
        grupo.jogo = await c.jogo(ORG, grupo.id, { vagas: 2, valorCents: 2000 })
        return grupo
      })
      await c.passo('motivo vazio, só espaços, 1 caractere, acima de 500 ou com quebra de linha: rejeitado (400, campo reason)', async () => {
        for (const ruim of ['', '   ', 'x', 'y'.repeat(501), 'linha um\nlinha dois']) {
          const r = await ORG.porOutro(g.id, g.jogo.id, ATL1.id, 'CONFIRM', ruim)
          c.afirmar(r.status === 400 && r.corpo.fieldErrors?.reason, `motivo ${JSON.stringify(ruim.slice(0, 20))} deveria ser rejeitado (veio ${r.status})`, r)
        }
        c.igual(await c.statusDe(g.jogo.id, ATL1), null, 'nada gravado')
      })
      await c.passo('motivo válido com espaços nas pontas: confirmado, motivo aparado e gravado com quem fez; avulso ganha a cobrança', async () => {
        c.status(await ADM.porOutro(g.id, g.jogo.id, ATL1.id, 'CONFIRM', '  Chegou na quadra  '), 200)
        c.igual(await c.statusDe(g.jogo.id, ATL1), 'CONFIRMED', 'ATL1')
        const [[motivo, autor]] = await c.sql(`select reason, actor_user_id from attendance_events where game_id='${g.jogo.id}' and member_user_id='${ATL1.id}' order by seq desc limit 1`)
        c.igual([motivo, autor], ['Chegou na quadra', ADM.id], 'motivo aparado e autor')
        c.igual((await ORG.cobrancas(g.id)).corpo.charges.filter((x) => x.memberId === ATL1.id).length, 1, 'cobrança do avulso')
      })
      await c.passo('confirmar com o jogo cheio coloca na espera (o organizador não passa por cima das vagas)', async () => {
        c.status(await ORG.porOutro(g.id, g.jogo.id, ATL2.id, 'CONFIRM', 'Veio avisar'), 200)
        c.status(await ORG.porOutro(g.id, g.jogo.id, ATL3.id, 'CONFIRM', 'Veio avisar também'), 200)
        c.igual(await c.statusDe(g.jogo.id, ATL3), 'WAITLISTED', 'ATL3 com jogo cheio')
      })
      await c.passo('tirar alguém ("Não vou"): vaga liberada anda a fila', async () => {
        c.status(await ORG.porOutro(g.id, g.jogo.id, ATL1.id, 'DECLINE', 'Desistiu por fora'), 200)
        c.igual([await c.statusDe(g.jogo.id, ATL1), await c.statusDe(g.jogo.id, ATL3)], ['DECLINED', 'CONFIRMED'], 'ATL1 fora, ATL3 sobe')
      })
      await c.passo('depois do início do jogo o organizador ainda confirma quem não estava na espera', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 4 })
        await c.inicioPara(j.id, new Date(Date.now() - 5 * 60_000))
        c.status(await ORG.porOutro(g.id, j.id, ATL2.id, 'CONFIRM', 'Apareceu na quadra'), 200)
        c.igual(await c.statusDe(j.id, ATL2), 'CONFIRMED', 'ATL2 depois do início')
      })
      await c.passo('o atleta mandando "reason" na própria resposta: o motivo é ignorado e não é gravado', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 4 })
        const r = await ATL1.chamar('PUT', `/api/groups/${g.id}/games/${j.id}/attendance`, { requestId: randomUUID(), intent: 'CONFIRM', reason: 'Posso escrever o que quiser' })
        c.status(r, 200)
        c.igual(await c.valor(`select coalesce(reason,'(nulo)') from attendance_events where game_id='${j.id}' and member_user_id='${ATL1.id}' order by seq desc limit 1`), '(nulo)', 'motivo gravado')
      })
    },
  },
  {
    id: 'PRES-013', feature: 'presenca', critico: false,
    titulo: 'Aviso direto quando a pessoa é confirmada ou tirada por outra parte',
    async rodar(c) {
      const { ORG, ATL1, ATL2, ATL3 } = await c.atores('ORG', 'ATL1', 'ATL2', 'ATL3')
      const g = await c.passo('ORG cria o grupo e desliga a prioridade', async () => {
        const grupo = await c.grupo(ORG, [ATL1, ATL2, ATL3])
        await c.configurar(ORG, grupo.id, { mensalistaPriority: false })
        return grupo
      })
      const vagas = async (a) => (await a.avisosDoGrupo(g.id)).filter((n) => n.canal === 'AUTO_CONFIRMATION' && /vaga/i.test(n.corpo))
      await c.passo('quem confirma por si na hora (ATL1) e quem desiste sozinho não recebem aviso direto', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 4 })
        c.status(await ATL1.vou(g.id, j.id), 200)
        c.status(await ATL1.naoVou(g.id, j.id), 200)
        await c.dormir(1500)
        c.igual((await vagas(ATL1)).length, 0, 'avisos de vaga de ATL1')
      })
      await c.passo('sobe da espera por desistência: ATL3 recebe "Sua vaga está confirmada"; quem fica na espera não recebe nada', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 2 })
        c.status(await ATL1.vou(g.id, j.id), 200); c.status(await ATL2.vou(g.id, j.id), 200); c.status(await ATL3.vou(g.id, j.id), 200)
        c.igual((await vagas(ATL3)).length, 0, 'ATL3 ainda na espera')
        c.status(await ATL1.naoVou(g.id, j.id), 200)
        const x = await c.esperar('aviso de vaga confirmada de ATL3', async () => { const v = await vagas(ATL3); return v.length ? v : null }, { teto: 20_000 })
        c.afirmar(/Sua vaga está confirmada/.test(x[0].corpo), `texto: ${x[0].corpo}`)
        c.igual((await vagas(ATL2)).length, 0, 'ATL2 já estava confirmado, nada novo')
      })
      await c.passo('toque duplo na desistência não manda um segundo aviso', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 2 })
        c.status(await ATL1.vou(g.id, j.id), 200); c.status(await ATL2.vou(g.id, j.id), 200); c.status(await ATL3.vou(g.id, j.id), 200)
        const antes = (await vagas(ATL3)).length
        await Promise.all([ATL1.naoVou(g.id, j.id), ATL1.naoVou(g.id, j.id), ATL1.naoVou(g.id, j.id)])
        await c.esperar('um novo aviso para ATL3', async () => (await vagas(ATL3)).length > antes, { teto: 20_000 })
        await c.dormir(1500)
        c.igual((await vagas(ATL3)).length, antes + 1, 'só um aviso novo para ATL3')
      })
      await c.passo('organizador confirma alguém: a pessoa recebe "Sua vaga está confirmada"; organizador tira: "foi removida pela organização"', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 4 })
        const antes = (await vagas(ATL2)).length
        c.status(await ORG.porOutro(g.id, j.id, ATL2.id, 'CONFIRM', 'Chegou na quadra'), 200)
        const x = await c.esperar('aviso de confirmação pelo organizador', async () => { const v = await vagas(ATL2); return v.length > antes ? v : null }, { teto: 20_000 })
        c.afirmar(x.some((n) => /confirmada/.test(n.corpo)), 'aviso de confirmada')
        c.status(await ORG.porOutro(g.id, j.id, ATL2.id, 'DECLINE', 'Motivo que a pessoa não vê'), 200)
        const y = await c.esperar('aviso de remoção', async () => { const v = await vagas(ATL2); return v.some((n) => /removida pela organização/.test(n.corpo)) ? v : null }, { teto: 20_000 })
        c.afirmar(!y.some((n) => /Motivo que a pessoa não vê/.test(n.corpo)), 'o motivo digitado não aparece para a pessoa')
      })
      await c.passo('convidado sobe da espera: o anfitrião recebe aviso citando o nome do convidado', async () => {
        const j = await c.jogo(ORG, g.id, { vagas: 2 })
        c.status(await ATL1.vou(g.id, j.id), 200); c.status(await ATL2.vou(g.id, j.id), 200)
        c.status(await ATL1.convidado(g.id, j.id, 'Amiga Do Atl Um'), 201)
        c.status(await ATL2.naoVou(g.id, j.id), 200)
        const x = await c.esperar('aviso ao anfitrião', async () => { const v = (await vagas(ATL1)).filter((n) => /Amiga Do Atl Um/.test(n.corpo)); return v.length ? v : null }, { teto: 20_000 })
        c.afirmar(/A vaga de Amiga Do Atl Um está confirmada/.test(x[0].corpo), `texto: ${x[0].corpo}`)
      })
    },
  },
]
