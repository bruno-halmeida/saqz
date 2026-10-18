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

/**
 * Token do emulador (JWT sem assinatura) com `auth_time` recuado: simula quem entrou há muito tempo e ainda
 * não confirmou a identidade de novo (a exclusão de conta exige `auth_time` de até 5 minutos).
 */
export function tokenAntigo(token, segundosAtras) {
  const [cab, corpo, assinatura] = token.split('.')
  const payload = JSON.parse(Buffer.from(corpo, 'base64url').toString())
  payload.auth_time -= segundosAtras
  return [cab, Buffer.from(JSON.stringify(payload)).toString('base64url'), assinatura].join('.')
}

/** O emulador responde 400 EMAIL_EXISTS quando o e-mail já tem conta (o app traduz para "Esse e-mail já tem conta"). */
export async function tentarCriarConta({ email, nome, senha = SENHA }) {
  const r = await fetch(`${AUTH}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=${KEY}`, {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ email, password: senha, displayName: nome, returnSecureToken: true }),
  })
  return { status: r.status, corpo: await r.json() }
}

/** A conta ainda existe no emulador? (a exclusão apaga a identidade em segundo plano) */
export async function contaExiste({ email, senha = SENHA }) {
  const r = await fetch(`${AUTH}/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${KEY}`, {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ email, password: senha, returnSecureToken: true }),
  })
  return r.ok
}

/** Marca o e-mail como confirmado direto no emulador (API de administração: `Bearer owner`); o token novo traz `email_verified`. */
export async function marcarEmailConfirmado(uid) {
  const r = await fetch(`${AUTH}/identitytoolkit.googleapis.com/v1/projects/saqz-local/accounts:update`, {
    method: 'POST', headers: { 'content-type': 'application/json', authorization: 'Bearer owner' }, body: JSON.stringify({ localId: uid, emailVerified: true }),
  })
  if (!r.ok) throw new Error(`Auth Emulator accounts:update ${r.status}: ${await r.text()}`)
}
