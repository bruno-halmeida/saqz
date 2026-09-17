// Lê o `--out json` do k6 e diz, degrau por degrau, se ele passou.
//
//   node tests/load/analise.mjs /tmp/saqz-load/ramp.json
//
// O resumo do k6 é acumulado: um degrau ruim no fim fica diluído e um erro de ruído no começo
// parece grave. A capacidade é o último degrau com p95 < 1 s e erro < 1%, e isso só se vê
// fatiando por tempo. Agrupa por minuto de VUs estável, que é o formato dos degraus do ramp.
import { createReadStream } from 'node:fs'
import { createInterface } from 'node:readline'

const file = process.argv[2]
if (!file) throw new Error('uso: node analise.mjs <saida-json-do-k6>')

const P95 = 1000
const ERR = 0.01

// Bucket de 30 s: fino para achar o momento da virada, grosso para ter amostra.
const BUCKET_MS = 30_000
const buckets = new Map()
let t0 = null

const bucket = (ts) => {
  const at = Date.parse(ts)
  if (t0 === null || at < t0) t0 = at
  const key = Math.floor((at - t0) / BUCKET_MS)
  if (!buckets.has(key)) buckets.set(key, { dur: [], falhas: 0, reqs: 0, vus: 0, porRota: new Map() })
  return buckets.get(key)
}

const rl = createInterface({ input: createReadStream(file), crlfDelay: Infinity })
for await (const linha of rl) {
  if (!linha.startsWith('{')) continue
  let p
  try { p = JSON.parse(linha) } catch { continue }
  if (p.type !== 'Point') continue
  const { metric, data } = p
  if (metric === 'vus') {
    const b = bucket(data.time)
    b.vus = Math.max(b.vus, data.value)
  } else if (metric === 'http_req_duration' && data.tags?.kind === 'api') {
    const b = bucket(data.time)
    b.dur.push(data.value)
    const rota = data.tags.name || '?'
    if (!b.porRota.has(rota)) b.porRota.set(rota, [])
    b.porRota.get(rota).push(data.value)
  } else if (metric === 'http_req_failed' && data.tags?.kind === 'api') {
    const b = bucket(data.time)
    b.reqs++
    if (data.value === 1) b.falhas++
  }
}

const quantil = (xs, q) => {
  if (!xs.length) return 0
  const s = [...xs].sort((a, b) => a - b)
  return s[Math.min(s.length - 1, Math.floor(q * s.length))]
}

console.log('min:seg   VUs    req/min   erro     med      p95      veredito')
const linhas = []
for (const [key, b] of [...buckets.entries()].sort((a, b) => a[0] - b[0])) {
  const taxa = b.reqs ? b.falhas / b.reqs : 0
  const p95 = quantil(b.dur, 0.95)
  const porMin = b.reqs * (60_000 / BUCKET_MS)
  // Bucket com pouca amostra não condena nem absolve: sem amostra não há veredito.
  const magro = b.reqs < 20
  const ok = !magro && p95 < P95 && taxa < ERR
  const veredito = magro ? '(amostra pequena)' : ok ? 'ok' : [p95 >= P95 && `p95 ${Math.round(p95)}ms`, taxa >= ERR && `erro ${(taxa * 100).toFixed(1)}%`].filter(Boolean).join(' + ')
  const t = key * BUCKET_MS / 1000
  console.log(
    `${String(Math.floor(t / 60)).padStart(2)}:${String(t % 60).padStart(2, '0')}   ` +
    `${String(b.vus).padStart(4)}   ${String(porMin).padStart(6)}   ` +
    `${(taxa * 100).toFixed(1).padStart(5)}%   ${String(Math.round(quantil(b.dur, 0.5))).padStart(5)}ms   ` +
    `${String(Math.round(p95)).padStart(5)}ms   ${veredito}`,
  )
  linhas.push({ vus: b.vus, ok, magro, p95, taxa, porMin, porRota: b.porRota })
}

// Capacidade: o maior número de VUs com bucket bom, sem nenhum bucket ruim em VUs iguais ou menores.
const ruimAte = Math.min(...linhas.filter((l) => !l.ok && !l.magro).map((l) => l.vus), Infinity)
const capacidade = Math.max(...linhas.filter((l) => l.ok && l.vus < ruimAte).map((l) => l.vus), 0)
console.log(`\nCapacidade: ${capacidade} VUs simultâneos` + (ruimAte === Infinity
  ? ' — nada degradou; o teto está ACIMA do que este ramp mediu, suba MAX_VUS.'
  : ` (o primeiro degrau ruim foi ${ruimAte}).`))

// Vazão máxima: o platô. Se ele chega antes do último degrau, a vazão é o teto do servidor;
// se o pico está no último degrau, quem limitou foi o teste, não o servidor.
const gordas = linhas.filter((l) => !l.magro)
const pico = gordas.reduce((a, l) => (l.porMin > a.porMin ? l : a), { porMin: 0, vus: 0 })
const vusMax = Math.max(...gordas.map((l) => l.vus), 0)
console.log(`Vazão máxima: ${pico.porMin} req/min (${Math.round(pico.porMin / 60)} req/s) em ${pico.vus} VUs` +
  (pico.vus >= vusMax ? ' — no ÚLTIMO degrau, ou seja o platô ainda não apareceu.' : ' — depois disso a vazão parou de subir: é o platô.'))

// Joelho da latência: onde p95 dobra. Aparece antes do erro e é o primeiro sinal de fila (no pool,
// na CPU, no que for), mesmo com p95 ainda dentro do limite.
//
// Só vale o patamar, nunca o bucket de subida: enquanto os VUs mudam, a amostra mistura dois
// degraus e o primeiro bucket do teste ainda paga cache de token frio (336 ms contra 85 ms de
// regime). Julgar bucket a bucket apontava "joelho em 7 VUs", que era o aquecimento.
const porDegrau = new Map()
for (const l of gordas) {
  if (!porDegrau.has(l.vus)) porDegrau.set(l.vus, [])
  porDegrau.get(l.vus).push(l.p95)
}
const patamares = [...porDegrau.entries()]
  .filter(([, ps]) => ps.length >= 2) // 1 bucket só = degrau de passagem, não patamar
  .map(([vus, ps]) => ({ vus, p95: quantil(ps, 0.5) }))
  .sort((a, b) => a.vus - b.vus)
// E o joelho só pode estar ACIMA do degrau mais rápido. O primeiro degrau de qualquer ramp roda
// com cache de token frio (todos os VUs acabaram de logar, cada requisição paga o checkRevoked
// de ~270 ms), então ele é lento por aquecimento, não por fila: 327 ms em 10 VUs contra 84 ms em
// 300. Ancorar no degrau mais rápido e olhar só para a frente descarta isso de graça.
const base = patamares.reduce((a, p) => (p.p95 < a.p95 ? p : a), patamares[0] ?? { p95: Infinity, vus: 0 })
const melhor = base.p95
const joelho = patamares.find((p) => p.vus > base.vus && p.p95 > melhor * 2)
const maiorDegrau = patamares.at(-1)
console.log(joelho
  ? `Joelho: p95 dobra (${Math.round(melhor)}ms em ${base.vus} VUs -> ${Math.round(joelho.p95)}ms em ${joelho.vus} VUs).`
  : `Joelho: não apareceu acima de ${base.vus} VUs — melhor patamar ${Math.round(melhor)}ms ` +
    `(${base.vus} VUs), último ${Math.round(maiorDegrau?.p95 ?? 0)}ms (${maiorDegrau?.vus} VUs).`)

// As rotas mais lentas do último bucket com amostra, que é onde a pressão foi maior.
const ultimo = [...linhas].reverse().find((l) => !l.magro)
if (ultimo) {
  console.log(`\nRotas mais lentas em ${ultimo.vus} VUs (p95):`)
  for (const [rota, xs] of [...ultimo.porRota.entries()]
    .map(([r, xs]) => [r, quantil(xs, 0.95)]).sort((a, b) => b[1] - a[1]).slice(0, 8)) {
    console.log(`  ${String(Math.round(xs)).padStart(5)}ms  ${rota}`)
  }
}
