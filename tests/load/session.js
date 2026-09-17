// Teste de carga do Saqz: uma "sessão" que repete o que o app faz de verdade.
//
//   k6 run -e BASE_URL=http://localhost:8080 -e PROFILE=smoke tests/load/session.js
//
// Perfis (PROFILE): smoke | ramp | spike | soak. Detalhes e cuidados no README.md desta pasta.
// O alvo é OBRIGATÓRIO e o script se recusa a rodar contra o Server Dev sem ALLOW_SHARED=1.
import http from 'k6/http'
import { check, group, sleep } from 'k6'
import exec from 'k6/execution'

const BASE = (__ENV.BASE_URL || '').replace(/\/$/, '')
const PROFILE = __ENV.PROFILE || 'smoke'
const WRITES = __ENV.WRITES === '1'
const FIREBASE_KEY = __ENV.FIREBASE_API_KEY || 'AIzaSyC_7NhdA7NOnL0SXzzNlcI2nAbBmwVodB4' // pública, a mesma do seed-usuarios.sh
const PASSWORD = __ENV.SEED_PASSWORD || 'saqz12345'
const GROUP = __ENV.GROUP_ID || '9a000000-0000-4000-8000-000000000001'
const MAX_VUS = Number(__ENV.MAX_VUS || 300)

if (!BASE) throw new Error('BASE_URL é obrigatório: o alvo nunca é implícito.')
if (/saqz-api\.brunoalmeida\.dev/.test(BASE) && __ENV.ALLOW_SHARED !== '1') {
  throw new Error('Este é o Server Dev compartilhado. Use um ambiente isolado, ou ALLOW_SHARED=1 só para o perfil smoke.')
}

const ramp = (stages) => ({ executor: 'ramping-vus', startVUs: 0, stages, gracefulRampDown: '30s' })
const PROFILES = {
  // 1 usuário, 1 sessão completa: prova que o roteiro funciona contra o alvo.
  smoke: { executor: 'per-vu-iterations', vus: 1, iterations: 1, maxDuration: '3m' },
  // Degraus até MAX_VUS: o degrau em que p95 ou erro estouram é a capacidade.
  ramp: ramp([10, 25, 50, 100, 150, 200, 300, 450, 600, 900, 1200].filter((v) => v <= MAX_VUS)
    .flatMap((v) => [{ duration: '1m', target: v }, { duration: '2m', target: v }])),
  // Hora do jogo: todo mundo abre o app em 1 minuto, cada um com token NOVO (fura o cache de 3 min).
  spike: ramp([{ duration: '1m', target: MAX_VUS }, { duration: '3m', target: MAX_VUS }, { duration: '30s', target: 0 }]),
  // Metade da capacidade por 1h: vazamento de memória, conexão presa, cache de token crescendo.
  soak: ramp([{ duration: '2m', target: Math.ceil(MAX_VUS / 2) }, { duration: '60m', target: Math.ceil(MAX_VUS / 2) }]),
}

export const options = {
  scenarios: { session: PROFILES[PROFILE] },
  thresholds: {
    // O abort é rede de segurança, não o critério de capacidade: 1% de erro acumulado julgado
    // cedo mata o teste por ruído (foi o que aconteceu com p95 em 131 ms, longe de saturar).
    // Quem responde "qual foi o último degrau bom" é a série temporal, degrau por degrau —
    // ver `analise.mjs`. Aqui só paramos quando já não há o que medir.
    // A taxa geral inclui o login no Firebase, que tem cota própria: não aborta por isso.
    'http_req_failed{kind:api}': [{ threshold: 'rate<0.10', abortOnFail: PROFILE === 'ramp', delayAbortEval: '2m' }],
    http_req_failed: ['rate<0.10'],
    'http_req_duration{kind:api}': [{ threshold: 'p(95)<3000', abortOnFail: PROFILE === 'ramp', delayAbortEval: '2m' }],
  },
  summaryTrendStats: ['med', 'p(95)', 'p(99)', 'max'],
}

// 20 das 21 contas do seed: a 0 é o dono (vê as telas de admin), 1..19 são atletas ativos.
// A atleta20 fica de fora de propósito: é o membro INATIVO do seed e o grupo responde 403 para ela.
const CANDIDATAS = ['owner@saqz.local']
  .concat(Array.from({ length: 20 }, (_, i) => `atleta${String(i + 1).padStart(2, '0')}@saqz.local`))

// Quem entra no grupo NÃO se deduz do seed: `seed-exploracao.sql` só matricula atleta que já tem
// linha em access_users, e essa linha só nasce no primeiro login. Contas que nunca logaram ficam
// de fora, e o membro inativo do seed também. Chutar a lista custou dois ramps abortados por 404,
// então o setup pergunta: quem consegue abrir o grupo, entra no rateio.
export function setup() {
  if (__ENV.ACCOUNT) return { contas: [__ENV.ACCOUNT] }
  const contas = CANDIDATAS.filter((email) => {
    const login = http.post(
      `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${FIREBASE_KEY}`,
      JSON.stringify({ email, password: PASSWORD, returnSecureToken: true }),
      { headers: { 'Content-Type': 'application/json' }, tags: { kind: 'setup' } },
    )
    if (login.status !== 200) return false
    const headers = { Authorization: `Bearer ${login.json('idToken')}`, 'Content-Type': 'application/json' }
    // PUT session primeiro: é ele que cria a linha em access_users de quem nunca logou. Sem isso
    // o grupo responderia 404 aqui e a conta seria descartada por um motivo que não é o dela.
    http.put(`${BASE}/api/session`, null, { headers, tags: { kind: 'setup' } })
    return http.get(`${BASE}/api/groups/${GROUP}`, { headers, tags: { kind: 'setup' } }).status === 200
  })
  if (!contas.length) throw new Error(`nenhuma conta do seed abre o grupo ${GROUP}: rode o seed-exploracao.sh no alvo`)
  console.log(`contas no rateio (${contas.length}): ${contas.join(', ')}`)
  return { contas }
}

let session = null // por VU: { token, expiresAt, owner }
function signIn(contas) {
  const email = contas[exec.vu.idInTest % contas.length]
  const res = http.post(
    `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${FIREBASE_KEY}`,
    JSON.stringify({ email, password: PASSWORD, returnSecureToken: true }),
    { headers: { 'Content-Type': 'application/json' }, tags: { kind: 'firebase', name: 'firebase signIn' } },
  )
  if (res.status !== 200) throw new Error(`login ${email}: ${res.status} ${res.body}`)
  return { token: res.json('idToken'), expiresAt: Date.now() + 50 * 60 * 1000, owner: email.startsWith('owner') }
}

const think = (min, max) => sleep(min + Math.random() * (max - min))

export default function (dados) {
  // No spike cada iteração é um "cold start" com token novo; nos outros o token vive 50 min, como no app.
  if (!session || PROFILE === 'spike' || Date.now() > session.expiresAt) session = signIn(dados.contas)
  const headers = { Authorization: `Bearer ${session.token}`, 'Content-Type': 'application/json' }
  const call = (method, path, name, body) => {
    const res = http.request(method, BASE + path, body ? JSON.stringify(body) : null, { headers, tags: { kind: 'api', name } })
    const ok = res.status >= 200 && res.status < 300
    check(res, { [`${name} 2xx`]: () => ok })
    // Sem o status na mão não dá para separar "servidor saturou" de "Cloudflare barrou como
    // ataque": os dois aparecem igual no resumo, como taxa de erro.
    if (!ok) {
      console.error(`FALHA ${name} status=${res.status} vu=${exec.vu.idInTest} ` +
        `cf-ray=${res.headers['Cf-Ray'] || '-'} retry-after=${res.headers['Retry-After'] || '-'} ` +
        `err=${res.error || '-'} body=${String(res.body).slice(0, 200)}`)
    }
    return res
  }
  const get = (path, name) => call('GET', path, name)

  group('abrir o app', () => {
    call('PUT', '/api/session', 'PUT session')
    // A Home dispara os dois em paralelo.
    http.batch([
      ['GET', `${BASE}/api/me/home`, null, { headers, tags: { kind: 'api', name: 'GET home' } }],
      ['GET', `${BASE}/api/athletes/me`, null, { headers, tags: { kind: 'api', name: 'GET athletes/me' } }],
    ])
  })
  think(3, 8)

  let nextGame = null
  group('tela do grupo', () => {
    // Em série, na mesma ordem do GroupDetailsViewModel.load().
    get(`/api/groups/${GROUP}`, 'GET group')
    const games = get(`/api/groups/${GROUP}/games`, 'GET games')
    const now = Date.now()
    const list = games.status === 200 ? games.json() : []
    nextGame = (Array.isArray(list) ? list : [])
      .filter((g) => g.status === 'PUBLISHED' && Date.parse(g.startsAt) > now)
      .sort((a, b) => Date.parse(a.startsAt) - Date.parse(b.startsAt))[0]
    if (nextGame) {
      get(`/api/groups/${GROUP}/games/${nextGame.id}/attendance`, 'GET attendance')
      get(`/api/groups/${GROUP}/games/${nextGame.id}/attendance/roster`, 'GET roster')
      get('/api/athletes/me', 'GET athletes/me')
    }
    if (session.owner) {
      get(`/api/groups/${GROUP}/finance/statement?month=${new Date().toISOString().slice(0, 7)}`, 'GET statement')
      get(`/api/groups/${GROUP}/charges`, 'GET charges')
    }
    get(`/api/groups/${GROUP}/charges/me`, 'GET charges/me')
    get(`/api/groups/${GROUP}/messages?channel=NOTICE`, 'GET messages')
  })
  think(5, 15)

  // 30% das sessões abrem a lista de membros; o smoke abre sempre, para não deixar requisição
  // nenhuma sem prova antes de um ramp de 21 minutos.
  if (PROFILE === 'smoke' || Math.random() < 0.3) {
    group('lista de membros', () => {
      get(`/api/groups/${GROUP}/athletes`, 'GET athletes')
      // Papéis são gestão de elenco (MANAGE_ATHLETES): atleta leva 403 aqui, é o esperado.
      if (session.owner) get(`/api/groups/${GROUP}/memberships`, 'GET memberships')
    })
    think(3, 8)
  }

  // Escrita só com WRITES=1 e só em ambiente isolado: confirma/recusa presença no próximo jogo.
  if (WRITES && nextGame && !session.owner && Math.random() < 0.5) {
    group('responder presença', () => {
      call('PUT', `/api/groups/${GROUP}/games/${nextGame.id}/attendance`, 'PUT attendance',
        { intent: Math.random() < 0.7 ? 'CONFIRM' : 'DECLINE' })
    })
  }
  think(10, 30) // o usuário fica parado olhando a tela antes da próxima rodada
}
