// Avisos e notificações (NOT-*). Sem push no stack local: tudo se confere na central (GET /api/me/notifications).
import { randomUUID } from 'node:crypto'
import { local, daqui, semSegundos } from '../lib/tempo.mjs'

const MIN = 60_000
const canais = (avisos) => avisos.map((a) => a.canal)

export default [
  {
    id: 'NOT-003', feature: 'avisos', critico: true,
    titulo: 'Avisos do jogo',
    async rodar(c) {
      const { ORG, ATL1, ATL2, ATL3 } = await c.atores('ORG', 'ATL1', 'ATL2', 'ATL3')
      const g = await c.passo('ORG cria o grupo; ATL1, ATL2 e ATL3 entram', () => c.grupo(ORG, [ATL1, ATL2, ATL3]))
      await c.passo('rascunho: ORG cria e edita o jogo; ninguém recebe nada', async () => {
        const rasc = await c.jogo(ORG, g.id, { vagas: 4, publicar: false })
        const lido = await ORG.jogo(g.id, rasc.id)
        c.status(await ORG.editarJogo(g.id, lido.corpo, lido.etag, { title: 'Jogo QA rascunho' }), 200)
        await c.dormir(2000)
        for (const a of [ATL1, ATL2, ATL3]) c.igual(canais(await a.avisosDoGrupo(g.id)), [], `${a.papel}: rascunho não avisa`)
        c.igual(await c.valor(`select count(*) from group_messages where group_id='${g.id}' and channel not in ('FIRST_MEMBER','FIRST_CONFIRMATION')`), '0', 'mensagens do grupo (fora o aviso de primeiro membro)')
        c.status(await ORG.cancelarJogo(g.id, rasc.id), 200)
      })
      const j = await c.passo('ORG publica o jogo (o próximo do grupo): todo o grupo recebe "O jogo está liberado" na central', async () => {
        const jogo = await c.jogo(ORG, g.id, { vagas: 4 })
        for (const a of [ATL1, ATL2, ATL3]) {
          const x = await c.esperar(`GAME_OPEN de ${a.papel}`, async () => { const v = await a.avisosDoJogo(jogo.id); return v.length ? v : null }, { teto: 20_000 })
          c.igual(canais(x), ['GAME_OPEN'], `${a.papel}: aviso de jogo liberado`)
        }
        return jogo
      })
      await c.passo('ATL1 diz Vou e ATL3 diz Não vou; ORG toca em "Avisar": só quem não respondeu (ATL2) recebe o lembrete', async () => {
        c.status(await ATL1.vou(g.id, j.id), 200)
        c.status(await ATL3.naoVou(g.id, j.id), 200)
        const r = c.status(await ORG.avisarPendentes(g.id, j.id), 200)
        c.igual(r.corpo.channel, 'REMINDER', 'canal do lembrete')
        c.igual(r.corpo.recipientCount, 1, 'destinatários do lembrete')
        const lembretes = async (a) => (await a.avisosDoJogo(j.id)).filter((n) => n.canal === 'REMINDER').length
        c.igual(await lembretes(ATL2), 1, 'ATL2 (sem resposta) recebe')
        c.igual(await lembretes(ATL1), 0, 'ATL1 (já respondeu Vou) não recebe')
        c.igual(await lembretes(ATL3), 0, 'ATL3 (já respondeu Não vou) não recebe')
      })
      await c.passo('mudou o horário: ORG edita o jogo publicado; todos recebem o aviso de mudança, menos ATL3 ("Não vou") e o próprio ORG', async () => {
        const lido = await ORG.jogo(g.id, j.id)
        const novo = semSegundos(daqui(60 * 60 * 3))
        const r = await ORG.editarJogo(g.id, lido.corpo, lido.etag, { ...local(novo), startsAt: novo.toISOString(), confirmationDeadline: daqui(60 * 24).toISOString() })
        c.status(r, 200)
        const mudou = async (a) => (await a.avisosDoGrupo(g.id)).filter((n) => / mudou: /.test(n.corpo))
        for (const a of [ATL1, ATL2]) {
          const x = await c.esperar(`aviso de mudança de ${a.papel}`, async () => { const v = await mudou(a); return v.length ? v : null }, { teto: 20_000 })
          c.afirmar(x[0].corpo.includes('às'), `o aviso traz a nova hora: ${x[0].corpo}`)
        }
        c.igual((await mudou(ATL3)).length, 0, 'ATL3 (Não vou) não recebe')
        c.igual((await mudou(ORG)).length, 0, 'quem editou não recebe')
      })
      await c.passo('mudar só as vagas (não é dia, hora nem local) não gera aviso de mudança', async () => {
        const mudancas = async () => (await ATL2.avisosDoGrupo(g.id)).filter((n) => / mudou: /.test(n.corpo)).length
        const antes = await mudancas()
        c.status(await ORG.ajustarVagas(g.id, j.id, 6), 200)
        await c.dormir(2000)
        c.igual(await mudancas(), antes, 'avisos de mudança de ATL2')
      })
      await c.passo('jogo cancelado: confirmados e espera recebem; ATL3 (Não vou) não recebe', async () => {
        c.status(await ORG.cancelarJogo(g.id, j.id), 200)
        const cancelou = async (a) => (await a.avisosDoJogo(j.id)).concat(await a.avisosDoGrupo(g.id)).filter((n) => /cancelad/i.test(n.corpo))
        for (const a of [ATL1]) {
          const x = await c.esperar(`aviso de cancelamento de ${a.papel}`, async () => { const v = await cancelou(a); return v.length ? v : null }, { teto: 20_000 })
          c.afirmar(x.length >= 1, 'recebe o cancelamento')
        }
        c.igual((await cancelou(ATL3)).length, 0, 'ATL3 não recebe o cancelamento')
      })
      await c.passo('jogo que já começou: "Avisar" não manda lembrete', async () => {
        const andando = await c.jogo(ORG, g.id, { vagas: 4 })
        await c.inicioPara(andando.id, new Date(Date.now() - 10 * MIN))
        const r = await ORG.avisarPendentes(g.id, andando.id)
        const lembretes = (await ATL2.avisosDoJogo(andando.id)).filter((n) => n.canal === 'REMINDER')
        c.afirmar(r.status >= 400 || lembretes.length === 0, `jogo iniciado não pode gerar lembrete (HTTP ${r.status}, ${lembretes.length} lembretes)`, r)
      })
    },
  },
{
    id: 'NOT-001', feature: 'avisos', critico: false, achado: 'DEB-064',
    titulo: 'Como um aviso chega: central, push e WhatsApp',
    async rodar(c) {
      const { ORG, ADM, ATL1, ATL2 } = await c.atores('ORG', 'ADM', 'ATL1', 'ATL2')
      const g = await c.passo('ORG cria o grupo; ADM vira admin; ATL1 e ATL2 entram', async () => {
        const grupo = await c.grupo(ORG, [ADM, ATL1, ATL2])
        c.status(await ORG.mudarPapel(grupo.id, ADM.id, 'ADMIN'), 200)
        return grupo
      })
      const mural = async (a) => (await a.avisosDoGrupo(g.id)).filter((n) => n.canal === 'NOTICE')
      await c.passo('ORG publica no mural: os membros ativos recebem na central e o autor não recebe o próprio aviso', async () => {
        c.status(await ORG.publicar(g.id, 'NOTICE', 'Jogo de terça confirmado, levem água.'), 200)
        for (const a of [ADM, ATL1, ATL2]) {
          const x = await c.esperar(`aviso do mural de ${a.papel}`, async () => { const v = await mural(a); return v.length ? v : null }, { teto: 15_000 })
          c.igual(x.length, 1, `${a.papel}: avisos do mural`)
        }
        c.igual((await mural(ORG)).length, 0, 'o autor não recebe o próprio aviso')
      })
      await c.passo('toque duplo (mesmo requestId): um aviso só', async () => {
        const id = randomUUID()
        const [a, b] = await Promise.all([ORG.publicar(g.id, 'NOTICE', 'Segundo aviso do mural', id), ORG.publicar(g.id, 'NOTICE', 'Segundo aviso do mural', id)])
        c.status(a, 200); c.status(b, 200)
        await c.dormir(1500)
        c.igual((await mural(ATL1)).length, 2, 'avisos do mural de ATL1 (o primeiro e o segundo, uma vez cada)')
      })
      await c.passo('quem saiu do grupo deixa de receber', async () => {
        c.status(await ATL2.sairDoGrupo(g.id), 204)
        const antesDeAtl2 = (await mural(ATL2)).length
        c.status(await ORG.publicar(g.id, 'NOTICE', 'Terceiro aviso depois da saída do ATL2'), 200)
        await c.dormir(1500)
        c.igual((await mural(ATL2)).length, antesDeAtl2, 'ATL2 não recebe o aviso depois de sair')
        c.igual((await mural(ATL1)).length, 3, 'ATL1 recebe')
      })
      await c.passo('preferências desligadas só controlam push e WhatsApp: o aviso continua na central (DEB-064 — hoje desligar a chave tira o aviso)', async () => {
        const atual = c.status(await ATL1.preferencias(), 200).corpo
        c.status(await ATL1.salvarPreferencias({ ...atual, notices: false, push: { ...atual.push, notices: false } }), 200)
        const antes = (await mural(ATL1)).length
        c.status(await ORG.publicar(g.id, 'NOTICE', 'Quarto aviso com a chave desligada'), 200)
        await c.dormir(1500)
        c.divergir((await mural(ATL1)).length === antes + 1, `com a chave "Avisos dos grupos" desligada o aviso deveria continuar na central (tinha ${antes}, tem ${(await mural(ATL1)).length}) — DEB-064`)
        c.status(await ATL1.salvarPreferencias(atual), 200)
      })
    },
  },
  {
    id: 'NOT-002', feature: 'avisos', critico: false,
    titulo: 'As preferências de cada pessoa',
    async rodar(c) {
      const { ORG, ATL1, ATL2 } = await c.atores('ORG', 'ATL1', 'ATL2')
      const g = await c.passo('ORG cria o grupo; ATL1 e ATL2 entram', () => c.grupo(ORG, [ATL1, ATL2]))
      await c.passo('pessoa nova: todas as chaves de push ligadas e o WhatsApp de cobrança ligado', async () => {
        const p = c.status(await ATL1.preferencias(), 200).corpo
        c.igual([p.push.notices, p.push.messages, p.push.reminders, p.push.charges, p.whatsapp.charges], [true, true, true, true, true], 'padrões')
      })
      await c.passo('desligar "Lembretes de cobrança" muda só o push de cobrança; o WhatsApp de cobrança segue a chave própria', async () => {
        const p = c.status(await ATL1.preferencias(), 200).corpo
        c.status(await ATL1.salvarPreferencias({ ...p, push: { ...p.push, charges: false } }), 200)
        const depois = c.status(await ATL1.preferencias(), 200).corpo
        c.igual([depois.push.charges, depois.push.notices, depois.push.messages, depois.push.reminders, depois.whatsapp.charges], [false, true, true, true, true], 'só a chave de push de cobrança caiu')
      })
      await c.passo('desligar o WhatsApp de cobrança não mexe no push', async () => {
        const p = c.status(await ATL1.preferencias(), 200).corpo
        c.status(await ATL1.salvarPreferencias({ ...p, whatsapp: { ...p.whatsapp, charges: false } }), 200)
        const depois = c.status(await ATL1.preferencias(), 200).corpo
        c.igual([depois.whatsapp.charges, depois.push.charges], [false, false], 'WhatsApp desligado; push segue como estava')
      })
      await c.passo('as chaves valem para todos os grupos e são individuais: ATL2 segue com tudo ligado', async () => {
        const p = c.status(await ATL2.preferencias(), 200).corpo
        c.igual([p.push.charges, p.whatsapp.charges], [true, true], 'ATL2')
        const g2 = await c.grupo(ORG, [ATL1], { nome: `QA Outro grupo ${c.tag}` })
        c.igual(c.status(await ATL1.preferencias(), 200).corpo.push.charges, false, 'a chave vale também no outro grupo')
      })
      await c.passo('dados inválidos (campos faltando) são recusados e nada muda', async () => {
        c.status(await ATL1.chamar('PUT', '/api/me/notification-preferences', { notices: true }), [400, 422])
        c.igual(c.status(await ATL1.preferencias(), 200).corpo.push.charges, false, 'preferência de ATL1 segue')
      })
    },
  },
  {
    id: 'NOT-004', feature: 'avisos', critico: false, achado: 'FIRST_MEMBER-NA-APROVAÇÃO',
    titulo: 'Avisos de pessoas',
    async rodar(c) {
      const { ORG, ADM, ATL1, ATL2 } = await c.atores('ORG', 'ADM', 'ATL1', 'ATL2')
      const g = await c.passo('ORG cria o grupo com aprovação', async () => {
        const grupo = await c.grupo(ORG, [])
        await c.configurar(ORG, grupo.id, { entryRequiresApproval: true, mensalistaPriority: false })
        return grupo
      })
      const avisos = async (a, canal) => (await a.avisosDoGrupo(g.id)).filter((n) => (canal ? n.canal === canal : true))
      await c.passo('ADM pede para entrar: o dono recebe "pediu para entrar"; quem pediu não recebe o próprio aviso', async () => {
        c.igual(c.status(await ADM.entrarPorConvite(g.codigo), 200).corpo.status, 'PENDING', 'pedido')
        const x = await c.esperar('aviso de pedido ao dono', async () => { const v = await avisos(ORG, 'ENTRY_REQUEST'); return v.length ? v : null }, { teto: 15_000 })
        c.afirmar(/pediu para entrar/.test(x[0].corpo), `texto: ${x[0].corpo}`)
        c.igual((await avisos(ADM, 'ENTRY_REQUEST')).length, 0, 'quem pediu não recebe')
      })
      await c.passo('ORG aprova ADM: a pessoa recebe "Você entrou no <Grupo>"; quem aprovou não recebe; ORG promove ADM e ADM recebe "Você agora é administrador"', async () => {
        c.status(await ORG.aprovarPedido(g.id, ADM.id), 200)
        const x = await c.esperar('aviso de entrada aprovada', async () => { const v = (await avisos(ADM)).filter((n) => /Você entrou no/.test(n.corpo)); return v.length ? v : null }, { teto: 15_000 })
        c.afirmar(x[0].corpo.includes(g.name), `texto: ${x[0].corpo}`)
        c.igual((await avisos(ORG)).filter((n) => /Você entrou no/.test(n.corpo)).length, 0, 'o dono (quem aprovou) não recebe')
        c.status(await ORG.mudarPapel(g.id, ADM.id, 'ADMIN'), 200)
        await c.esperar('aviso de promoção', async () => (await avisos(ADM)).some((n) => /Você agora é administrador/.test(n.corpo)), { teto: 15_000 })
      })
      await c.passo('entrada direta (grupo sem aprovação): o primeiro atleta gera um aviso para dono e admin, uma vez só — "A galera começou a chegar"', async () => {
        const gd = await c.grupo(ORG, [], { nome: `QA Entrada direta ${c.tag}` })
        const direto = async (a) => (await a.avisosDoGrupo(gd.id)).filter((n) => n.canal === 'FIRST_MEMBER')
        c.status(await ATL1.entrarPorConvite(gd.codigo), 200)
        const x = await c.esperar('aviso de primeiro atleta ao dono', async () => { const v = await direto(ORG); return v.length ? v : null }, { teto: 15_000 })
        c.afirmar(/A galera começou a chegar/.test(x[0].corpo), `texto: ${x[0].corpo}`)
        c.status(await ATL2.entrarPorConvite(gd.codigo), 200)
        await c.dormir(1500)
        c.igual((await direto(ORG)).length, 1, 'o segundo atleta não repete o aviso')
      })
      await c.passo('grupo com aprovação: o primeiro atleta aprovado também deveria gerar o aviso de primeiro atleta para o dono', async () => {
        for (const a of [ATL1, ATL2]) { c.igual((await a.entrarPorConvite(g.codigo)).corpo.status, 'PENDING', `${a.papel} pendente`); c.status(await ADM.aprovarPedido(g.id, a.id), 200) }
        await c.dormir(2000)
        c.divergir((await avisos(ORG, 'FIRST_MEMBER')).length === 1, `aprovar o primeiro atleta de um grupo com aprovação deveria avisar o dono ("A galera começou a chegar"); recebeu ${(await avisos(ORG, 'FIRST_MEMBER')).length} aviso(s) — o aviso só é disparado na entrada direta (RedeemInvite)`)
      })
      await c.passo('pedido recusado: ninguém recebe aviso; rebaixar um admin: ninguém recebe', async () => {
        const { NOVO } = await c.atores('NOVO')
        c.igual((await NOVO.entrarPorConvite(g.codigo)).corpo.status, 'PENDING', 'pedido de NOVO')
        const antes = (await avisos(NOVO)).length
        c.status(await ORG.recusarPedido(g.id, NOVO.id), 204)
        c.status(await ORG.mudarPapel(g.id, ADM.id, 'ATHLETE'), 200)
        await c.dormir(1500)
        c.igual((await avisos(NOVO)).length, antes, 'NOVO não recebe aviso de recusa')
        c.igual((await avisos(ADM)).filter((n) => /rebaix|deixou de ser/i.test(n.corpo)).length, 0, 'rebaixado não recebe aviso')
      })
      await c.passo('primeira confirmação de presença: dono e admin recebem "confirmou presença no jogo", uma vez só', async () => {
        c.status(await ORG.mudarPapel(g.id, ADM.id, 'ADMIN'), 200)
        const j = await c.jogo(ORG, g.id, { vagas: 4 })
        c.status(await ATL1.vou(g.id, j.id), 200)
        const x = await c.esperar('aviso de primeira confirmação', async () => { const v = await avisos(ORG, 'FIRST_CONFIRMATION'); return v.length ? v : null }, { teto: 15_000 })
        c.afirmar(/confirmou presença no jogo de/.test(x[0].corpo), `texto: ${x[0].corpo}`)
        c.status(await ATL2.vou(g.id, j.id), 200)
        await c.dormir(1500)
        c.igual((await avisos(ORG, 'FIRST_CONFIRMATION')).length, 1, 'só a primeira confirmação avisa')
      })
      await c.passo('o dono apaga o grupo: todos os membros recebem o aviso de grupo excluído', async () => {
        c.status(await ORG.excluirGrupo(g.id), 204)
        const destinatarios = await c.sql(`select n.recipient_id from group_notifications n join group_messages m on m.id=n.message_id where m.group_id='${g.id}' and m.body like '%excluído%'`)
        const ids = destinatarios.map(([id]) => id)
        for (const a of [ADM, ATL1, ATL2]) c.afirmar(ids.includes(a.id), `${a.papel} deveria receber o aviso de grupo excluído`)
        c.afirmar(!ids.includes(ORG.id), 'o dono, que apagou, não recebe')
      })
    },
  },
  {
    id: 'NOT-005', feature: 'avisos', critico: false,
    titulo: 'Avisos de dinheiro',
    async rodar(c) {
      const { ORG, ATL1, ATL2, ATL3 } = await c.atores('ORG', 'ATL1', 'ATL2', 'ATL3')
      const g = await c.passo('ORG cria o grupo com chave Pix; jogo de R$ 20; ATL1, ATL2 e ATL3 confirmam; ATL1 leva um convidado', async () => {
        const grupo = await c.grupo(ORG, [ATL1, ATL2, ATL3], { extra: { pixKey: 'racha@saqz.test', pixLabel: 'Tesoureiro' } })
        await c.configurar(ORG, grupo.id, { mensalistaPriority: false })
        grupo.jogo = await c.jogo(ORG, grupo.id, { vagas: 6, valorCents: 2000 })
        for (const a of [ATL1, ATL2, ATL3]) c.status(await a.vou(grupo.id, grupo.jogo.id), 200)
        return grupo
      })
      const cob = async (a) => (await ORG.cobrancas(g.id)).corpo.charges.find((x) => x.memberId === a.id)
      const cobrancas = async (a) => (await a.avisosDoGrupo(g.id)).filter((n) => n.canal === 'CHARGE')
      await c.passo('Cobrar em 3 pendentes: 3 avisos, um para cada devedor, na central', async () => {
        const ids = [await cob(ATL1), await cob(ATL2), await cob(ATL3)].map((x) => x.id)
        const r = c.status(await ORG.cobrar(g.id, ids), 200)
        c.igual(r.corpo.notificationCount, 3, 'avisos que saíram')
        for (const a of [ATL1, ATL2, ATL3]) {
          const x = await c.esperar(`aviso de cobrança de ${a.papel}`, async () => { const v = await cobrancas(a); return v.length ? v : null }, { teto: 15_000 })
          c.igual(x.length, 1, `${a.papel}: avisos de cobrança`)
        }
      })
      await c.passo('uma cobrada há pouco é pulada (informada ao organizador); o mesmo pedido repetido não cobra em dobro', async () => {
        const id = randomUUID()
        const ids = [(await cob(ATL1)).id]
        const a = c.status(await ORG.cobrar(g.id, ids, id), 200)
        const b = c.status(await ORG.cobrar(g.id, ids, id), 200)
        c.igual([a.corpo.notificationCount, a.corpo.alreadyRemindedCount], [0, 1], 'pulada por ter sido cobrada há menos de 12 h')
        c.igual(b.corpo, a.corpo, 'mesmo pedido, mesmo resultado')
        await c.dormir(1200)
        c.igual((await cobrancas(ATL1)).length, 1, 'avisos de ATL1')
      })
      await c.passo('paga antes da entrega: cobrança recebida não gera aviso (recusada)', async () => {
        const paga = await cob(ATL2)
        c.status(await ORG.baixa(g.id, paga, 'PAID', { paidMethod: 'PIX' }), 200)
        c.status(await ORG.cobrar(g.id, [paga.id]), [409, 422])
        c.igual((await cobrancas(ATL2)).length, 1, 'ATL2 só tem o aviso de antes da baixa')
      })
      await c.passo('devedor com tudo desligado: o aviso continua na central (preferência só controla push e WhatsApp)', async () => {
        const p = c.status(await ATL3.preferencias(), 200).corpo
        c.status(await ATL3.salvarPreferencias({ ...p, push: { ...p.push, charges: false }, whatsapp: { ...p.whatsapp, charges: false } }), 200)
        await c.sql(`update group_messages set created_at = created_at - interval '13 hours' where charge_id = '${(await cob(ATL3)).id}'`)
        c.status(await ORG.cobrar(g.id, [(await cob(ATL3)).id]), 200)
        await c.esperar('segundo aviso na central de ATL3', async () => (await cobrancas(ATL3)).length === 2, { teto: 15_000 })
      })
      await c.passo('convidado com cobrança pendente: o aviso vai ao anfitrião', async () => {
        const j2 = await c.jogo(ORG, g.id, { vagas: 4, valorCents: 3000 })
        c.status(await ATL1.vou(g.id, j2.id), 200)
        c.status(await ATL3.vou(g.id, j2.id), 200)
        const conv = c.status(await ATL1.convidado(g.id, j2.id, 'Amigo Do ATL1'), 201)
        c.status(await ATL3.naoVou(g.id, j2.id), 200)
        const dele = (await ORG.cobrancas(g.id)).corpo.charges.find((x) => x.gameId === j2.id && x.guestDisplayName === 'Amigo Do ATL1')
        c.afirmar(dele, 'deveria existir a cobrança do convidado (nome do anfitrião)')
        const antes = (await cobrancas(ATL1)).length
        c.status(await ORG.cobrar(g.id, [dele.id]), 200)
        await c.esperar('aviso ao anfitrião', async () => (await cobrancas(ATL1)).length === antes + 1, { teto: 15_000 })
      })
    },
  },
  {
    id: 'NOT-006', feature: 'avisos', critico: false,
    titulo: 'Mural e conversas',
    async rodar(c) {
      const { ORG, ATL1, ATL2, FORA } = await c.atores('ORG', 'ATL1', 'ATL2', 'FORA')
      const g = await c.passo('ORG cria o grupo; ATL1 e ATL2 entram', () => c.grupo(ORG, [ATL1, ATL2]))
      await c.passo('atleta publica no mural: 403; atleta escreve na conversa: publicado; dono publica no mural: publicado', async () => {
        c.status(await ATL1.publicar(g.id, 'NOTICE', 'Quero avisar todo mundo'), 403)
        c.status(await ATL1.publicar(g.id, 'CHAT', 'Bom dia, pessoal'), 200)
        c.status(await ORG.publicar(g.id, 'NOTICE', 'Aviso oficial do dono'), 200)
      })
      await c.passo('texto vazio, só espaços, com 2.001 caracteres ou com caractere de controle: recusado', async () => {
        for (const ruim of ['', '   ', 'x'.repeat(2001), 'texto\u0007com controle']) c.status(await ATL1.publicar(g.id, 'CHAT', ruim), 422)
        c.status(await ATL1.publicar(g.id, 'CHAT', 'x'.repeat(2000)), 200)
      })
      await c.passo('palavra proibida: recusado (body objectionable)', async () => {
        const r = c.status(await ATL1.publicar(g.id, 'CHAT', 'Vai tomar no cu, galera'), 422)
        c.afirmar(r.corpo.fieldErrors?.body, 'o erro deve apontar o campo body', r)
      })
      await c.passo('mesma mensagem duas vezes (mesmo requestId): uma só; mesmo requestId com texto diferente: conflito', async () => {
        const id = randomUUID()
        c.status(await ATL1.publicar(g.id, 'CHAT', 'Mensagem repetida', id), 200)
        c.status(await ATL1.publicar(g.id, 'CHAT', 'Mensagem repetida', id), 200)
        const lista = c.status(await ATL2.mensagens(g.id, 'CHAT'), 200).corpo
        const itens = lista.items ?? lista.messages ?? lista
        c.igual(itens.filter((m) => m.body === 'Mensagem repetida').length, 1, 'mensagens iguais na conversa')
        c.status(await ATL1.publicar(g.id, 'CHAT', 'Outro texto com o mesmo id', id), 409)
      })
      await c.passo('A bloqueia B: A deixa de ver as mensagens de B na conversa; B não percebe', async () => {
        c.status(await ATL2.publicar(g.id, 'CHAT', 'Mensagem do ATL2 para todos'), 200)
        const visivel = async (viewer, texto) => { const l = c.status(await viewer.mensagens(g.id, 'CHAT'), 200).corpo; return (l.items ?? l.messages ?? l).some((m) => m.body === texto) }
        c.igual(await visivel(ATL1, 'Mensagem do ATL2 para todos'), true, 'antes do bloqueio')
        c.status(await ATL1.bloquear(g.id, ATL2.id), 204)
        c.igual(await visivel(ATL1, 'Mensagem do ATL2 para todos'), false, 'depois do bloqueio, ATL1 não vê')
        c.igual(await visivel(ORG, 'Mensagem do ATL2 para todos'), true, 'o dono ainda vê')
        c.status(await ATL2.publicar(g.id, 'CHAT', 'ATL2 continua escrevendo'), 200)
        c.status(await ATL1.desbloquear(ATL2.id), 204)
      })
      await c.passo('denúncia: qualquer membro denuncia uma mensagem; o próprio texto não pode ser denunciado; quem saiu ou não é membro recebe 404', async () => {
        const lista = c.status(await ATL1.mensagens(g.id, 'CHAT'), 200).corpo
        const itens = lista.items ?? lista.messages ?? lista
        const deATL2 = itens.find((m) => m.authorId === ATL2.id)
        const propria = itens.find((m) => m.authorId === ATL1.id)
        c.afirmar(deATL2 && propria, 'precisa existir mensagem de ATL2 e de ATL1 na conversa')
        c.status(await ATL1.denunciar(g.id, 'MESSAGE', deATL2.id), 204)
        c.status(await ATL1.denunciar(g.id, 'MESSAGE', propria.id), [404, 422])
        c.status(await FORA.denunciar(g.id, 'MESSAGE', deATL2.id), 404)
      })
    },
  },
]
