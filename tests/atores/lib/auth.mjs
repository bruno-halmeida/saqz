import { AUTH, SENHA } from './config.mjs'

const KEY = 'qualquer-chave'  // o Auth Emulator ignora a chave

async function identitytoolkit(acao, corpo) {
  const r = await fetch(`${AUTH}/identitytoolkit.googleapis.com/v1/accounts:${acao}?key=${KEY}`, {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(corpo),
  })
  const json = await r.json()
  if (!r.ok) throw new Error(`Auth Emulator ${acao}: ${r.status} ${JSON.stringify(json)}`)
  return json
}

/** Cria a conta no Auth Emulator (REST do emulador) e devolve o token já válido para o backend. */
export async function criarConta({ email, nome, senha = SENHA }) {
  const r = await identitytoolkit('signUp', { email, password: senha, displayName: nome, returnSecureToken: true })
  return { email, uid: r.localId, token: r.idToken }
}

/** Login de uma conta que já existe: o token novo tem `auth_time` de agora (exclusão de conta exige < 5 min). */
export async function entrar({ email, senha = SENHA }) {
  const r = await identitytoolkit('signInWithPassword', { email, password: senha, returnSecureToken: true })
  return { email, uid: r.localId, token: r.idToken }
}

/** O emulador guarda os links de confirmação de e-mail (`oobCodes`); abrir o `oobLink` confirma. */
export async function confirmarEmail(email) {
  const r = await fetch(`${AUTH}/emulator/v1/projects/saqz-local/oobCodes`)
  const { oobCodes } = await r.json()
  const codigo = [...oobCodes].reverse().find((c) => c.email === email && c.requestType === 'VERIFY_EMAIL')
  if (!codigo) return false
  await fetch(codigo.oobLink)
  return true
}
