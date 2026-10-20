// Vaga por posição (DEB-102, regras 1 a 12 do Débitos.md). Um cenário por bloco de regras; o ID é "DEB-102-<letra>".
// App antigo (1.0.6 a 1.0.9) não manda `position`: `vou()` sem posição é o pedido antigo; `vouEm()` é o novo.

const PONTA2_CENTRAL2 = [{ position: 'PONTA', slots: 2 }, { position: 'CENTRAL', slots: 2 }]

async function posicaoDe(c, jogoId, ator, convidado = 0) {
  // Sem posição volta "-": o leitor do banco descarta linha vazia, e '' viraria null (como "linha não existe").
  return c.valor(`select coalesce(position::text,'-') from game_attendance where game_id='${jogoId}' and member_user_id='${ator.id}' and guest_seq=${convidado}`)
}

export default [
  {
    id: 'DEB-102-A', feature: 'presenca', critico: true,
    titulo: 'Configuração por posição: sugestão pelo total, 5-1 exige posições com a soma certa, só dono/admin grava, o total do jogo é a soma e jogo antigo não muda',
    async rodar(c) {
      const { ORG, ATL1 } = await c.atores('ORG', 'ATL1')
      const g = await c.passo('ORG cria o grupo; ATL1 entra', () => c.grupo(ORG, [ATL1]))
      const antigo = await c.passo('ORG publica um jogo ANTES de configurar posições', () => c.jogo(ORG, g.id, { vagas: 6 }))
      await c.passo('sugestão 5-1 pelo total de 18 = 3 levantadores, 3 opostos, 6 pontas, 6 centrais, líbero 0; 6-0 não sugere posições', async () => {
        const s = c.status(await ORG.sugestaoDePosicoes('FIVE_ONE', 18), 200).corpo
        c.igual(s.positionSlots.reduce((t, p) => t + p.slots, 0), 18, 'soma da sugestão')
        c.igual(s.positionSlots.find((p) => p.position === 'PONTA').slots, 6, 'pontas')
        c.igual(s.positionSlots.find((p) => p.position === 'LIBERO').slots, 0, 'líbero')
        c.igual(c.status(await ORG.sugestaoDePosicoes('SIX_ZERO', 18), 200).corpo.positionSlots.length, 0, '6-0 sem posições')
        c.status(await ORG.sugestaoDePosicoes('FIVE_ONE', 3), 400)
        c.status(await ORG.sugestaoDePosicoes('FIVE_ONE'), 400)
      })
      await c.passo('ATL1 (atleta) não grava a configuração; 5-1 sem posições, 6-0 com posições e total que não bate são recusados', async () => {
        c.status(await ATL1.configurarPosicoes(g.id, PONTA2_CENTRAL2), 403)
        c.status(await ORG.configurarPosicoes(g.id, [{ position: 'PONTA', slots: 0 }]), 422)
        c.status(await ORG.configurarPosicoes(g.id, [{ position: 'PONTA', slots: 2 }, { position: 'PONTA', slots: 2 }]), 422)
        c.status(await ORG.configurarPosicoes(g.id, []), 422)
        c.status(await ORG.configurarPosicoes(g.id, PONTA2_CENTRAL2, 'SIX_ZERO'), 422)
        c.status(await ORG.configurarPosicoes(g.id, PONTA2_CENTRAL2, 'FIVE_ONE', 5), 422)
      })
      await c.passo('ORG grava a sugestão de 18 (total conferido); a leitura devolve as posições e o total', async () => {
        const s = c.status(await ORG.sugestaoDePosicoes('FIVE_ONE', 18), 200).corpo
        c.status(await ORG.configurarPosicoes(g.id, s.positionSlots.filter((p) => p.slots > 0), 'FIVE_ONE', 18), 200)
        const lido = c.status(await ATL1.posicoesDoGrupo(g.id), 200).corpo
        c.igual(lido.total, 18, 'total lido pelo atleta')
      })
      await c.passo('jogo novo nasce com 18 vagas e o resumo por posição; o jogo antigo segue sem posições', async () => {
        const novo = await c.jogo(ORG, g.id, { vagas: 4 })
        const det = c.status(await ATL1.presenca(g.id, novo.id), 200).corpo
        c.igual(det.capacity, 18, 'capacidade = soma')
        c.igual(det.positions.length, 4, 'posições com vaga')
        const velho = c.status(await ATL1.presenca(g.id, antigo.id), 200).corpo
        c.afirmar(!velho.positions, 'jogo antigo sem positions')
        c.igual(velho.capacity, 6, 'capacidade antiga intacta')
      })
      await c.passo('ORG tenta ajustar o total do jogo com posições: recusado (ajuste por posição fica fora)', async () => {
        const novo = await c.jogo(ORG, g.id, { vagas: 4 })
        c.status(await ORG.ajustarVagas(g.id, novo.id, 20), 400)
      })
    },
  },

  {
    id: 'DEB-102-B', feature: 'presenca', critico: true,
    titulo: 'Posição cheia = espera mesmo com vaga no jogo; a vaga que abre sobe só quem espera na mesma posição',
    async rodar(c) {
      const { ORG, A1, A2, A3, A4, A5, A6 } = await c.atores('ORG', 'A1', 'A2', 'A3', 'A4', 'A5', 'A6')
      const g = await c.passo('ORG cria o grupo, desliga a prioridade e configura PONTA 2 + CENTRAL 2', async () => {
        const grupo = await c.grupo(ORG, [A1, A2, A3, A4, A5, A6])
        await c.configurar(ORG, grupo.id, { mensalistaPriority: false })
        c.status(await ORG.configurarPosicoes(grupo.id, PONTA2_CENTRAL2), 200)
        return grupo
      })
      const j = await c.passo('ORG publica o jogo (4 vagas = 2 + 2)', () => c.jogo(ORG, g.id, { vagas: 4 }))
      await c.passo('A1 e A2 entram de ponta; A3 também quer ponta e vai para a espera, embora a central tenha vaga', async () => {
        c.status(await A1.vouEm(g.id, j.id, 'PONTA'), 200)
        c.status(await A2.vouEm(g.id, j.id, 'PONTA'), 200)
        c.status(await A3.vouEm(g.id, j.id, 'PONTA'), 200)
        c.igual(await c.statusDe(j.id, A3), 'WAITLISTED', 'A3 (ponta cheia)')
      })
      await c.passo('A4 e A5 enchem a central; A6 espera na central (chegou depois de A3)', async () => {
        c.status(await A4.vouEm(g.id, j.id, 'CENTRAL'), 200)
        c.status(await A5.vouEm(g.id, j.id, 'CENTRAL'), 200)
        c.status(await A6.vouEm(g.id, j.id, 'CENTRAL'), 200)
        c.igual(await c.statusDe(j.id, A6), 'WAITLISTED', 'A6 (central cheia)')
      })
      await c.passo('a fila de A3 é "1º" na ponta e a de A6 é "1º" na central; o total do jogo segue 4 vagas', async () => {
        c.igual((await A3.presenca(g.id, j.id)).corpo.ownAttendance.waitlistPosition, 1, 'A3')
        const det = (await A6.presenca(g.id, j.id)).corpo
        c.igual(det.ownAttendance.waitlistPosition, 1, 'A6')
        c.igual(det.capacity, 4, 'capacidade total')
        c.igual(det.confirmedCount, 4, 'confirmados')
      })
      await c.passo('A1 (ponta) diz Não vou: sobe A3 (ponta), não A6 (central)', async () => {
        c.status(await A1.naoVou(g.id, j.id), 200)
        c.igual(await c.statusDe(j.id, A3), 'CONFIRMED', 'A3 subiu')
        c.igual(await c.statusDe(j.id, A6), 'WAITLISTED', 'A6 segue esperando')
      })
      await c.passo('A4 (central) diz Não vou: agora sobe A6', async () => {
        c.status(await A4.naoVou(g.id, j.id), 200)
        c.igual(await c.statusDe(j.id, A6), 'CONFIRMED', 'A6 subiu')
      })
    },
  },

  {
    id: 'DEB-102-C', feature: 'presenca', critico: true,
    titulo: 'App antigo (sem position) em grupo com posições: principal do perfil, depois a mais livre; sem posições nada muda',
    async rodar(c) {
      const { ORG, A1, A2, A3 } = await c.atores('ORG', 'A1', 'A2', 'A3')
      const g = await c.passo('ORG cria o grupo, desliga a prioridade; A1 tem perfil CENTRAL; configura PONTA 2 + CENTRAL 2', async () => {
        const grupo = await c.grupo(ORG, [A1, A2, A3])
        await c.configurar(ORG, grupo.id, { mensalistaPriority: false })
        c.status(await A1.meuPerfil(grupo.id, { position: 'CENTRAL' }), 200)
        c.status(await ORG.configurarPosicoes(grupo.id, PONTA2_CENTRAL2), 200)
        return grupo
      })
      const j = await c.passo('ORG publica o jogo', () => c.jogo(ORG, g.id, { vagas: 4 }))
      await c.passo('A1 diz Vou como app antigo (sem posição): entra na CENTRAL, a principal do perfil', async () => {
        c.status(await A1.vou(g.id, j.id), 200)
        c.igual(await posicaoDe(c, j.id, A1), 'CENTRAL', 'posição de A1')
        c.igual(await c.statusDe(j.id, A1), 'CONFIRMED', 'A1')
      })
      await c.passo('A2 (sem perfil) diz Vou sem posição: vai para a posição com mais vagas livres (PONTA, empate pela ordem)', async () => {
        c.status(await A2.vou(g.id, j.id), 200)
        c.igual(await posicaoDe(c, j.id, A2), 'PONTA', 'posição de A2')
      })
      await c.passo('posição fora do jogo (LIBERO sem vaga) é recusada com 400', async () => {
        c.status(await A3.vouEm(g.id, j.id, 'LIBERO'), 400)
      })
      await c.passo('grupo sem posições: a posição enviada é ignorada e o comportamento é o de sempre', async () => {
        const g2 = await c.grupo(ORG, [A3])
        await c.configurar(ORG, g2.id, { mensalistaPriority: false })
        const j2 = await c.jogo(ORG, g2.id, { vagas: 4 })
        c.status(await A3.vouEm(g2.id, j2.id, 'PONTA'), 200)
        c.igual(await c.statusDe(j2.id, A3), 'CONFIRMED', 'A3')
        c.igual(await posicaoDe(c, j2.id, A3), '-', 'sem posição gravada')
        c.afirmar(!(await A3.presenca(g2.id, j2.id)).corpo.positions, 'sem resumo por posição')
      })
    },
  },

  {
    id: 'DEB-102-D', feature: 'presenca',
    titulo: 'Organizador força a entrada em posição cheia (fica acima das vagas, sinalizado) e quem convida escolhe a posição do convidado',
    async rodar(c) {
      const { ORG, A1, A2, A3 } = await c.atores('ORG', 'A1', 'A2', 'A3')
      const g = await c.passo('ORG cria o grupo, desliga a prioridade e configura PONTA 2 + CENTRAL 2', async () => {
        const grupo = await c.grupo(ORG, [A1, A2, A3])
        await c.configurar(ORG, grupo.id, { mensalistaPriority: false })
        c.status(await ORG.configurarPosicoes(grupo.id, PONTA2_CENTRAL2), 200)
        return grupo
      })
      const j = await c.passo('ORG publica o jogo; A1 e A2 enchem a ponta', async () => {
        const jogo = await c.jogo(ORG, g.id, { vagas: 4 })
        c.status(await A1.vouEm(g.id, jogo.id, 'PONTA'), 200)
        c.status(await A2.vouEm(g.id, jogo.id, 'PONTA'), 200)
        return jogo
      })
      await c.passo('ORG coloca A3 de ponta sem forçar: vai para a espera; forçando: entra e a resposta vem com forced', async () => {
        const sem = c.status(await ORG.porOutro(g.id, j.id, A3.id, 'CONFIRM', 'Chegou agora', { posicao: 'PONTA' }), 200).corpo
        c.igual(sem.attendance.status, 'WAITLISTED', 'sem force')
        const com = c.status(await ORG.porOutro(g.id, j.id, A3.id, 'CONFIRM', 'Chegou agora', { posicao: 'PONTA', forcar: true }), 200).corpo
        c.igual(com.attendance.status, 'CONFIRMED', 'com force')
        c.igual(com.attendance.forced, true, 'forced na resposta')
        const pontas = com.detail.positions.find((p) => p.position === 'PONTA')
        c.igual(pontas.confirmed, 3, 'ponta acima das 2 vagas')
        c.igual(pontas.missing, 0, 'nada falta na ponta')
      })
      await c.passo('A1 convida escolhendo CENTRAL; convite sem posição (app antigo) cai na posição com mais vagas livres', async () => {
        c.status(await A1.convidado(g.id, j.id, 'Convidada Ana', 'CENTRAL'), 201)
        c.igual(await posicaoDe(c, j.id, A1, 1), 'CENTRAL', 'convidada com posição')
        c.status(await A1.convidado(g.id, j.id, 'Convidado Beto'), 201)
        c.igual(await posicaoDe(c, j.id, A1, 2), 'CENTRAL', 'convidado sem posição')
      })
    },
  },

  {
    id: 'DEB-102-E', feature: 'presenca',
    titulo: 'Disputa pela última vaga de uma posição: em paralelo, só um confirma e os outros esperam',
    async rodar(c) {
      const { ORG, A1, A2, A3 } = await c.atores('ORG', 'A1', 'A2', 'A3')
      const g = await c.passo('ORG cria o grupo, desliga a prioridade e configura PONTA 1 + CENTRAL 3', async () => {
        const grupo = await c.grupo(ORG, [A1, A2, A3])
        await c.configurar(ORG, grupo.id, { mensalistaPriority: false })
        c.status(await ORG.configurarPosicoes(grupo.id, [{ position: 'PONTA', slots: 1 }, { position: 'CENTRAL', slots: 3 }]), 200)
        return grupo
      })
      const j = await c.passo('ORG publica o jogo', () => c.jogo(ORG, g.id, { vagas: 4 }))
      await c.passo('A1, A2 e A3 dizem Vou de ponta ao mesmo tempo: 1 confirmado e 2 na espera', async () => {
        const rs = await Promise.all([A1, A2, A3].map((a) => a.vouEm(g.id, j.id, 'PONTA')))
        rs.forEach((r) => c.status(r, 200))
        const estados = await Promise.all([A1, A2, A3].map((a) => c.statusDe(j.id, a)))
        c.igual(estados.filter((e) => e === 'CONFIRMED').length, 1, 'confirmados')
        c.igual(estados.filter((e) => e === 'WAITLISTED').length, 2, 'na espera')
      })
    },
  },
]
