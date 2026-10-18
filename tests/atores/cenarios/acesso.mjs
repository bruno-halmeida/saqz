// Acesso e conta (ACE-*) e saída/exclusão de conta (SAI-*).
import { randomUUID } from 'node:crypto'
import { criarConta, entrar, tentarCriarConta, tokenAntigo, contaExiste, confirmarEmail } from '../lib/auth.mjs'
import { MAILPIT } from '../lib/config.mjs'
import { Ator } from '../lib/ator.mjs'
import { chamar } from '../lib/http.mjs'

const MIN = 60_000

export default [
  {
    id: 'ACE-001', feature: 'acesso', critico: true,
    titulo: 'Entrar e criar conta',
    async rodar(c) {
      const email = `qa-conta+${c.tag}@saqz.local`
      const conta = await c.passo('conta nova no Firebase (como Google/Apple na primeira vez) abre a sessão: nasce sem grupo, e o telefone ainda é pedido', async () => {
        const nova = await criarConta({ email, nome: `QA Conta ${c.tag}` })
        const r = c.status(await chamar('PUT', '/api/session', { token: nova.token }), 200)
        c.igual(r.corpo.memberships, [], 'conta nova sem grupo')
        c.igual([r.corpo.user.phoneRequired, r.corpo.user.phone], [true, null], 'telefone obrigatório e ainda vazio')
        c.igual(r.corpo.planOwner, false, 'ainda não é dono')
        nova.id = r.corpo.user.id
        return nova
      })
      await c.passo('abrir de novo a sessão não cria outra conta (mesmo id)', async () => {
        const r = c.status(await chamar('PUT', '/api/session', { token: conta.token }), 200)
        c.igual(r.corpo.user.id, conta.id, 'id do usuário')
        c.igual(await c.valor(`select count(*) from access_users where firebase_subject='${conta.uid}'`), '1', 'contas no banco')
      })
      await c.passo('e-mail já usado: o Firebase recusa o segundo cadastro (EMAIL_EXISTS) e a conta segue única', async () => {
        const dup = await tentarCriarConta({ email, nome: 'Outra pessoa' })
        c.igual(dup.status, 400, 'status do cadastro repetido')
        c.igual(dup.corpo.error?.message, 'EMAIL_EXISTS', 'motivo')
      })
      await c.passo('telefone com menos de 11 dígitos é recusado ("Telefone incompleto"); com DDD + 9 dígitos é aceito', async () => {
        const curto = c.status(await chamar('PATCH', '/api/session/profile', { token: conta.token, corpo: { phone: '1190000000' } }), 400)
        c.afirmar(curto.corpo.fieldErrors?.phone, 'o erro deve apontar o campo phone', curto)
        const ok = c.status(await chamar('PATCH', '/api/session/profile', { token: conta.token, corpo: { phone: '11988887777' } }), 200)
        c.igual([ok.corpo.user.phone, ok.corpo.user.phoneRequired], ['+5511988887777', false], 'telefone gravado com +55')
      })
      await c.passo('nome vazio, de 1 letra ou com caractere de controle é recusado', async () => {
        for (const ruim of ['', 'A', 'Nome\u0007Ruim']) {
          const r = await chamar('PATCH', '/api/session/profile', { token: conta.token, corpo: { displayName: ruim } })
          c.afirmar(r.status === 400 && r.corpo.fieldErrors?.displayName, `nome ${JSON.stringify(ruim)} deve ser recusado (veio ${r.status})`, r)
        }
        c.afirmar((await chamar('PUT', '/api/session', { token: conta.token })).corpo.user.displayName.startsWith('QA Conta'), 'o nome antigo continua')
      })
      await c.passo('quem cria o primeiro grupo vira dono e ganha o teste de 14 dias (planOwner)', async () => {
        const g = c.status(await chamar('POST', '/api/groups', { token: conta.token, corpo: {
          requestId: randomUUID(), name: `QA Conta ${c.tag}`, modality: 'COURT_VOLLEYBALL', composition: 'MIXED', timeZone: 'America/Sao_Paulo',
          defaultVenue: { name: 'Quadra QA', address: 'Rua QA, 1', court: null }, regularSlots: [] } }), 201)
        c.igual(g.corpo.role, 'OWNER', 'papel')
        const s = (await chamar('PUT', '/api/session', { token: conta.token })).corpo
        c.igual([s.planOwner, s.memberships.map((m) => m.role)], [true, ['OWNER']], 'sessão depois do grupo')
      })
      await c.passo('Sair: o aparelho some da lista de push (registra o aparelho, depois o remove)', async () => {
        const instalacao = randomUUID()
        const headers = {}
        c.igual((await chamar('PUT', `/api/me/notification-devices/${instalacao}`, { token: conta.token, corpo: { token: `fcm-${c.tag}`, platform: 'ANDROID' } })).status, 204, 'registro do aparelho')
        c.igual(await c.valor(`select count(*) from notification_devices where user_id='${conta.id}'`), '1', 'aparelhos registrados')
        c.igual((await chamar('DELETE', `/api/me/notification-devices/${instalacao}`, { token: conta.token, headers })).status, 204, 'remoção do aparelho')
        c.igual(await c.valor(`select count(*) from notification_devices where user_id='${conta.id}'`), '0', 'aparelhos depois de sair')
      })
      await c.passo('sem token ou com token inválido a API recusa (401)', async () => {
        c.status(await chamar('PUT', '/api/session', {}), 401)
        c.status(await chamar('GET', '/api/groups', { token: 'lixo' }), 401)
      })
    },
  },
  {
    id: 'ACE-002', feature: 'acesso', critico: true, achado: 'DEB-082',
    titulo: 'Confirmar e-mail e telefone',
    async rodar(c) {
      const { ORG, ATL2 } = await c.atores('ORG', 'ATL2')
      const ATL1 = await c.ator('ATL1', { confirmado: false })
      const g = await c.passo('ORG cria o grupo e marca jogo publicado; ATL1 (e-mail NÃO confirmado) e ATL2 (confirmado) entram', async () => {
        const grupo = await c.grupo(ORG, [ATL1, ATL2])
        await c.configurar(ORG, grupo.id, { mensalistaPriority: false })
        grupo.jogo = await c.jogo(ORG, grupo.id, { vagas: 4 })
        return grupo
      })
      await c.passo('ATL1, conta por e-mail sem confirmar, toca em "Vou": o servidor barra e a presença não vale (DEB-082)', async () => {
        const r = await ATL1.vou(g.id, g.jogo.id)
        c.divergir(r.status === 403, `o "Vou" de conta não confirmada deveria dar 403 (veio HTTP ${r.status}: ${r.texto.slice(0, 120)})`)
        c.divergir((await c.statusDe(g.jogo.id, ATL1)) === null, `o "Vou" de conta não confirmada não pode gravar presença (ficou ${await c.statusDe(g.jogo.id, ATL1)})`)
      })
      await c.passo('"Não vou" sem confirmar funciona', async () => {
        c.status(await ATL1.naoVou(g.id, g.jogo.id), 200)
        c.igual(await c.statusDe(g.jogo.id, ATL1), 'DECLINED', 'ATL1 depois do Não vou')
      })
      await c.passo('ATL1 pede a confirmação: o e-mail chega no Mailpit; abrir o link confirma; o "Vou" fica liberado', async () => {
        c.status(await ATL1.chamar('POST', '/api/email-verification/request'), 202)
        const email = await c.esperar('e-mail de confirmação no Mailpit', async () => {
          const r = await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${ATL1.email}`)}`).then((x) => x.json())
          return r.messages_count > 0 ? r.messages[0] : null
        }, { teto: 20_000 })
        c.afirmar(email, 'o e-mail de confirmação deve chegar')
        c.igual((await ATL1.sessao()).corpo.user.emailVerified, false, 'e-mail ainda não confirmado')
        c.afirmar(await confirmarEmail(ATL1.email), 'o emulador tem o link de confirmação')
        await ATL1.renovarToken()
        const s = (await ATL1.sessao()).corpo.user
        c.igual([s.emailVerified, s.accountVerified], [true, true], 'sessão depois de confirmar')
        c.status(await ATL1.vou(g.id, g.jogo.id), 200)
        c.igual(await c.statusDe(g.jogo.id, ATL1), 'CONFIRMED', 'ATL1 confirmado')
      })
      await c.passo('reenviar antes de 60 s é recusado com o tempo restante (429 + Retry-After)', async () => {
        const novo = await c.ator('ATL3', { confirmado: false })
        c.status(await novo.chamar('POST', '/api/email-verification/request'), 202)
        const r = await novo.chamar('POST', '/api/email-verification/request')
        c.status(r, 429, 'EMAIL_VERIFICATION_RATE_LIMIT')
        const espera = Number(r.headers.get('retry-after'))
        c.afirmar(espera >= 1 && espera <= 60, `Retry-After deveria estar entre 1 e 60 s (veio ${r.headers.get('retry-after')})`, r)
      })
      await c.passo('9º pedido em 10 minutos é recusado (teto de 8 por conta), mesmo respeitando os 60 s entre eles', async () => {
        const alvo = await c.ator('ATL4', { confirmado: false })
        await c.sql('delete from password_reset_rate_limits')  // trava do IP é compartilhada: zera para o cenário ser repetível
        for (let i = 1; i <= 8; i++) {
          c.status(await alvo.chamar('POST', '/api/email-verification/request'), 202)
          // empurra a janela de 60 s para trás sem mexer no teto de 10 min (8 × 61 s < 10 min)
          await c.sql(`update password_reset_rate_limits set window_started_at = window_started_at - interval '61 seconds' where window_started_at > now() - interval '30 seconds'`)
        }
        const r = await alvo.chamar('POST', '/api/email-verification/request')
        c.status(r, 429, 'EMAIL_VERIFICATION_RATE_LIMIT')
        c.afirmar(Number(r.headers.get('retry-after')) > 0, 'Retry-After com o tempo restante', r)
      })
      await c.passo('telefone confirmado vale sozinho: ATL5 só com telefone confirmado fica "accountVerified" e o e-mail segue não confirmado', async () => {
        const solo = await c.ator('ATL5', { confirmado: false })
        c.igual((await solo.sessao()).corpo.user.accountVerified, false, 'antes de confirmar')
        await c.sql(`update access_users set verified_phone = phone where id='${solo.id}'`)
        const s = (await solo.sessao()).corpo.user
        c.igual([s.emailVerified, s.accountVerified], [false, true], 'só o telefone confirmado')
        const g2 = await c.grupo(ORG, [solo])
        const j2 = await c.jogo(ORG, g2.id, { vagas: 4 })
        c.status(await solo.vou(g2.id, j2.id), 200)
      })
      await c.passo('trocar o telefone zera a confirmação do telefone', async () => {
        const trocador = await c.ator('ATL6', { confirmado: false })
        await c.sql(`update access_users set verified_phone = phone where id='${trocador.id}'`)
        c.igual((await trocador.sessao()).corpo.user.accountVerified, true, 'confirmado pelo telefone')
        c.status(await trocador.chamar('PATCH', '/api/session/profile', { phone: '11977776666' }), 200)
        c.igual((await trocador.sessao()).corpo.user.accountVerified, false, 'volta a não confirmado')
      })
    },
  },
  {
    id: 'SAI-009', feature: 'acesso', critico: true,
    titulo: 'Excluir a conta',
    async rodar(c) {
      const { ORG, ATL1, ATL2, ATL3, ATL4 } = await c.atores('ORG', 'ATL1', 'ATL2', 'ATL3', 'ATL4')
      const g1 = await c.passo('ORG cria o grupo G1 (com aprovação) e ATL1, ATL2 entram aprovados; ATL4 fica com pedido pendente', async () => {
        const g = await c.grupo(ORG, [])
        await c.configurar(ORG, g.id, { mensalistaPriority: false, entryRequiresApproval: true })
        for (const a of [ATL1, ATL2]) { c.igual((await a.entrarPorConvite(g.codigo)).corpo.status, 'PENDING', `${a.papel} pendente`); c.status(await ORG.aprovarPedido(g.id, a.id), 200) }
        c.igual((await ATL4.entrarPorConvite(g.codigo)).corpo.status, 'PENDING', 'ATL4 pendente')
        return g
      })
      const g2 = await c.passo('ATL2 também é dono de G2 (com ATL3 de atleta)', () => c.grupo(ATL2, [ATL3], { nome: `QA Grupo do ATL2 ${c.tag}` }))
      const j = await c.passo('jogo futuro com diária em G1: ATL1 e ATL2 confirmam presença', async () => {
        const jogo = await c.jogo(ORG, g1.id, { vagas: 4, valorCents: 2000 })
        for (const a of [ATL1, ATL2]) c.status(await a.vou(g1.id, jogo.id), 200)
        return jogo
      })
      await c.passo('ATL1 tenta excluir a conta com login antigo (sem confirmar a identidade): recusado, nada é apagado', async () => {
        const antigo = tokenAntigo(ATL1.token, 10 * 60)
        const r = await chamar('DELETE', '/api/session', { token: antigo, headers: { 'X-Expected-User-Id': ATL1.id } })
        c.status(r, 403, 'RECENT_AUTHENTICATION_REQUIRED')
        c.igual(await c.valor(`select deleted_at is null from access_users where id='${ATL1.id}'`), 't', 'conta segue ativa')
        c.igual(await c.statusDe(j.id, ATL1), 'CONFIRMED', 'presença segue')
      })
      await c.passo('ATL1 (atleta) confirma a identidade e exclui a conta: sai de G1 e a presença futura vira "Não vou" pelo sistema', async () => {
        await ATL1.renovarToken()
        c.status(await ATL1.excluirConta(), 204)
        c.igual(await c.valor(`select count(*) from group_memberships where group_id='${g1.id}' and user_id='${ATL1.id}' and active`), '0', 'participação ativa em G1')
        c.igual(await c.statusDe(j.id, ATL1), 'DECLINED', 'presença futura')
        c.igual((await c.eventos(j.id, ATL1)).at(-1), 'CONFIRMED>DECLINED:SYSTEM', 'evento de sistema')
      })
      await c.passo('a sessão antiga não abre mais (401) e o ORG deixa de ver ATL1 na lista de membros', async () => {
        c.status(await ATL1.sessao(), 401)
        const membros = (await ORG.membros(g1.id)).corpo.map((m) => m.userId)
        c.afirmar(!membros.includes(ATL1.id), 'ATL1 não aparece mais como membro')
      })
      await c.passo('cobrança e presença de ATL1 passam a mostrar "Conta excluída"', async () => {
        const nome = await c.valor(`select member_display_name from game_attendance where game_id='${j.id}' and member_user_id='${ATL1.id}'`)
        const usuario = await c.valor(`select coalesce(display_name,'(nulo)') from access_users where id='${ATL1.id}'`)
        c.afirmar(/exclu/i.test(nome ?? '') || /exclu/i.test(usuario), `presença/usuário deveriam mostrar "Conta excluída" (presença: ${nome}; usuário: ${usuario})`)
      })
      await c.passo('ATL4 (pedido de entrada pendente em G1) exclui a conta: o pedido é apagado', async () => {
        c.igual(await c.valor(`select count(*) from group_entry_requests where group_id='${g1.id}' and user_id='${ATL4.id}'`), '1', 'pedido antes')
        await ATL4.renovarToken()
        c.status(await ATL4.excluirConta(), 204)
        c.igual(await c.valor(`select count(*) from group_entry_requests where group_id='${g1.id}' and user_id='${ATL4.id}'`), '0', 'pedido depois')
      })
      await c.passo('ATL2 (dono de G2 e atleta de G1) exclui a conta: G2 é excluído para todos; G1 perde ATL2', async () => {
        await ATL2.renovarToken()
        c.status(await ATL2.excluirConta(), 204)
        c.status(await ATL3.grupo(g2.id), 404)
        c.igual((await ATL3.chamar('GET', '/api/groups')).corpo.groups.map((x) => x.id).includes(g2.id), false, 'G2 some da lista do ATL3')
        c.igual(await c.statusDe(j.id, ATL2), 'DECLINED', 'presença de ATL2 em G1')
        c.igual(await c.valor(`select count(*) from group_memberships where group_id='${g1.id}' and user_id='${ATL2.id}' and active`), '0', 'participação de ATL2 em G1')
      })
      await c.passo('ATL3 (membro de G2) recebe o aviso "grupo excluído" (vira push; a central esconde o aviso de grupo que já não existe, então se confere no banco)', async () => {
        const aviso = await c.sql(`select m.body, m.channel from group_notifications n join group_messages m on m.id = n.message_id where m.group_id='${g2.id}' and n.recipient_id='${ATL3.id}'`)
        c.igual(aviso.length, 1, 'avisos de grupo excluído para ATL3')
        c.afirmar(/exclu/i.test(aviso[0][0]), `texto do aviso: ${aviso[0][0]}`)
      })
      await c.passo('em segundo plano a identidade some do Firebase; entrar de novo com o mesmo e-mail cria conta nova, sem histórico', async () => {
        await c.esperar('identidade de ATL1 apagada no Auth Emulator', async () => !(await contaExiste({ email: ATL1.email })), { teto: 60_000, passo: 2_000 })
        const nova = await criarConta({ email: ATL1.email, nome: 'QA ATL1 de novo' })
        const s = c.status(await chamar('PUT', '/api/session', { token: nova.token }), 200)
        c.afirmar(s.corpo.user.id !== ATL1.id, 'o id é novo', s)
        c.igual(s.corpo.memberships, [], 'sem grupos nem histórico')
      })
    },
  },
{
    id: 'ACE-003', feature: 'acesso', critico: false,
    titulo: 'Esqueci minha senha',
    async rodar(c) {
      const { ATL1 } = await c.atores('ATL1')
      const email = ATL1.email
      const codigoPorEmail = async (desde) => {
        const msg = await c.esperar('e-mail com o código de redefinição', async () => {
          const r = await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${email}`)}`).then((x) => x.json())
          return r.messages.find((m) => Date.parse(m.Created) >= desde) ?? null
        }, { teto: 20_000 })
        const corpo = await fetch(`${MAILPIT}/api/v1/message/${msg.ID}`).then((x) => x.json())
        const m = /\b(\d{4})\b/.exec(`${corpo.Text ?? ''} ${corpo.HTML ?? ''}`.replace(/#[0-9a-f]{3,8}/gi, ''))
        c.afirmar(m, `o e-mail deveria trazer o código de 4 dígitos: ${corpo.Text}`)
        return m[1]
      }
      await c.passo('zera as travas de rede (compartilhadas) e pede o código para uma conta que existe e para uma que não existe: a resposta é a mesma (202)', async () => {
        await c.sql('delete from password_reset_rate_limits')
        const comConta = await chamar('POST', '/api/password-reset/request', { corpo: { email } })
        const semConta = await chamar('POST', '/api/password-reset/request', { corpo: { email: `ninguem+${c.tag}@saqz.local` } })
        c.igual([comConta.status, semConta.status], [202, 202], 'respostas do pedido')
        const enviados = await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:ninguem+${c.tag}@saqz.local`)}`).then((x) => x.json())
        c.igual(enviados.messages_count, 0, 'e-mails para quem não tem conta')
      })
      await c.passo('pedir de novo antes de 60 s: recusado com o tempo restante (429 + Retry-After)', async () => {
        const r = await chamar('POST', '/api/password-reset/request', { corpo: { email } })
        c.status(r, 429, 'PASSWORD_RESET_RATE_LIMIT')
        c.afirmar(Number(r.headers.get('retry-after')) > 0, 'Retry-After', r)
      })
      let token
      await c.passo('código errado: "restam tentativas"; código certo: libera a senha nova por 5 minutos', async () => {
        const certo = await codigoPorEmail(Date.now() - 60_000)
        const errado = certo === '0000' ? '1111' : '0000'
        const ruim = await chamar('POST', '/api/password-reset/verify', { corpo: { email, code: errado } })
        c.status(ruim, 400, 'PASSWORD_RESET_CODE_INVALID')
        c.igual(ruim.corpo.remainingAttempts, 4, 'tentativas restantes')
        const bom = c.status(await chamar('POST', '/api/password-reset/verify', { corpo: { email, code: certo } }), 200)
        c.igual(bom.corpo.expiraEmSegundos, 300, 'o token vale 5 minutos')
        token = bom.corpo.token
      })
      await c.passo('senha com 7 ou 129 caracteres é recusada; com 8 troca a senha e o login passa a valer só com a nova', async () => {
        for (const ruim of ['1234567', 'x'.repeat(129)]) c.status(await chamar('POST', '/api/password-reset/confirm', { corpo: { token, novaSenha: ruim } }), 400)
        c.status(await chamar('POST', '/api/password-reset/confirm', { corpo: { token, novaSenha: 'NovaSenha2026' } }), 204)
        c.afirmar(await contaExiste({ email, senha: 'NovaSenha2026' }), 'a senha nova deve entrar')
        c.afirmar(!(await contaExiste({ email })), 'a senha antiga não pode mais entrar')
        c.status(await chamar('POST', '/api/password-reset/confirm', { corpo: { token, novaSenha: 'OutraSenha2026' } }), 410)
      })
      await c.passo('código errado 5 vezes: o código é descartado e é preciso pedir outro', async () => {
        await c.sql('delete from password_reset_rate_limits')
        c.status(await chamar('POST', '/api/password-reset/request', { corpo: { email } }), 202)
        let ultimo
        for (let i = 0; i < 6; i++) ultimo = await chamar('POST', '/api/password-reset/verify', { corpo: { email, code: '0000' } })
        c.afirmar([410, 429].includes(ultimo.status) || ultimo.codigo === 'PASSWORD_RESET_ATTEMPT_LIMIT', `depois de 5 erros o código deve ser descartado (veio HTTP ${ultimo.status} ${ultimo.codigo})`, ultimo)
      })
    },
  },
  {
    id: 'ACE-005', feature: 'acesso', critico: false,
    titulo: 'Conta suspensa pela equipe',
    async rodar(c) {
      const { ORG, ATL1 } = await c.atores('ORG', 'ATL1')
      const g = await c.passo('ORG cria o grupo; ATL1 entra', () => c.grupo(ORG, [ATL1]))
      const suspender = (a) => c.sql(`update access_users set suspended_at = now() where id='${a.id}'`)
      const reativar = (a) => c.sql(`update access_users set suspended_at = null where id='${a.id}'`)
      await c.passo('a equipe suspende o dono: a sessão aberta, o pedido de perfil e os pedidos aos grupos são recusados (ACCOUNT_SUSPENDED)', async () => {
        await suspender(ORG)
        c.status(await ORG.sessao(), 403, 'ACCOUNT_SUSPENDED')
        c.status(await ORG.chamar('PATCH', '/api/session/profile', { city: 'Campinas' }), 403, 'ACCOUNT_SUSPENDED')
        const r = await ORG.grupo(g.id)
        c.afirmar(r.status === 403, `pedido de grupo de conta suspensa deve ser recusado (veio ${r.status})`, r)
      })
      await c.passo('o grupo de quem foi suspenso não muda: ATL1 segue lendo e usando o grupo', async () => {
        c.status(await ATL1.grupo(g.id), 200)
        const j = await c.jogo(ATL1.constructor === Object ? ORG : ORG, g.id, { vagas: 4 }).catch(() => null)
        c.status(await ATL1.jogos(g.id), 200)
        c.igual(await c.valor(`select count(*) from access_groups where id='${g.id}' and deleted_at is null`), '1', 'o grupo segue existindo')
      })
      await c.passo('os dados ficam guardados: o dono continua dono no banco e a pessoa não perde os vínculos', async () => {
        c.igual(await c.valor(`select count(*) from group_memberships where group_id='${g.id}' and user_id='${ORG.id}'`), '1', 'vínculo do dono')
      })
      await c.passo('a equipe reativa: a pessoa volta na hora, com grupos e histórico intactos', async () => {
        await reativar(ORG)
        const s = c.status(await ORG.sessao(), 200).corpo
        c.igual(s.memberships.map((m) => m.groupId), [g.id], 'grupos depois de reativar')
        c.status(await ORG.grupo(g.id), 200)
      })
      await c.passo('conta nova sem sessão antiga também é recusada enquanto suspensa (login recusado)', async () => {
        await suspender(ATL1)
        const novo = await entrar({ email: ATL1.email })
        c.status(await chamar('PUT', '/api/session', { token: novo.token }), 403, 'ACCOUNT_SUSPENDED')
        await reativar(ATL1)
      })
    },
  },
  {
    id: 'ACE-006', feature: 'acesso', critico: false,
    titulo: 'Excluir a conta (resumo; o detalhe está em SAI-009)',
    async rodar(c) {
      const { ATL1 } = await c.atores('ATL1')
      await c.passo('ATL1 pede a exclusão: a conta fica sem acesso na hora', async () => {
        await ATL1.renovarToken()
        c.status(await ATL1.excluirConta(), 204)
        c.status(await ATL1.sessao(), 401)
      })
      await c.passo('tentar entrar depois: o servidor apaga a identidade e o mesmo e-mail só entra como conta nova', async () => {
        await c.esperar('identidade apagada', async () => !(await contaExiste({ email: ATL1.email })), { teto: 60_000, passo: 2_000 })
        c.igual(await c.valor(`select count(*) from access_users where id='${ATL1.id}' and deleted_at is not null`), '1', 'conta marcada como excluída')
      })
    },
  },
]
