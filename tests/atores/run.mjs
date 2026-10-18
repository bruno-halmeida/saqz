#!/usr/bin/env node
// Executor dos cenários multi-pessoa por API (sem LLM, sem aparelho). Ver README.md desta pasta.
import { readdirSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { fileURLToPath, pathToFileURL } from 'node:url'
import path from 'node:path'
import { API } from './lib/config.mjs'
import { Contexto, Falha, Bloqueio } from './lib/cenario.mjs'
import { rastro } from './lib/http.mjs'
import { gravarRelatorio, linhaTerminal } from './lib/relatorio.mjs'

const AQUI = path.dirname(fileURLToPath(import.meta.url))
const STACK = path.join(AQUI, '..', 'multi-aparelhos', 'stack.sh')
const TETO_CENARIO_MS = Number(process.env.SAQZ_ATORES_TETO_MS ?? 240_000)

const USO = `Uso: node tests/atores/run.mjs [--criticos | --todos | --regra ID[,ID…] | --lista] [--subir] [--derrubar]

  --criticos   só as regras críticas do cofre
  --todos      todas as regras com cenário
  --regra ID   uma ou mais regras (ex.: --regra PRES-005,PRES-009)
  --lista      lista os cenários e sai
  --subir      sobe o stack (stack.sh up) se não estiver de pé
  --derrubar   com --subir: derruba no fim o stack que este comando subiu (nunca derruba stack alheio)

O stack local (Postgres, Auth Emulator, Mailpit, backend em ${API}) precisa estar de pé; sem --subir o
comando para com uma mensagem se não estiver.`

async function carregar() {
  const dir = path.join(AQUI, 'cenarios')
  const todos = []
  for (const arq of readdirSync(dir).filter((f) => f.endsWith('.mjs') && !f.startsWith('_')).sort()) {
    const mod = await import(pathToFileURL(path.join(dir, arq)))
    for (const c of mod.default) todos.push({ ...c, arquivo: arq })
  }
  return todos.sort((a, b) => a.id.localeCompare(b.id, 'pt', { numeric: true }))
}

async function saudavel() {
  try { return (await fetch(`${API}/actuator/health`)).ok } catch { return false }
}

async function rodar(cenario) {
  const c = new Contexto(cenario)
  const inicio = Date.now()
  let timer
  const estouro = new Promise((_, rej) => { timer = setTimeout(() => rej(new Falha(`estourou o teto de ${TETO_CENARIO_MS / 1000} s`)), TETO_CENARIO_MS) })
  rastro.ultima = null
  try {
    await Promise.race([cenario.rodar(c), estouro])
    if (c.divergencias.length) { c.atual = 'divergências registradas durante o cenário'; const f = new Falha(`divergências: ${c.divergencias.join(' | ')}`); f.resposta = null; throw f }
    return { estado: 'passou', ms: Date.now() - inicio }
  } catch (e) {
    const base = { ms: Date.now() - inicio, passo: c.atual ?? '(antes do primeiro passo)', motivo: e.message }
    if (e instanceof Bloqueio) return { ...base, estado: 'bloqueado' }
    const resposta = (e instanceof Falha ? e.resposta : rastro.ultima)?.resumo?.()
    return { ...base, estado: 'falhou', resposta, detalhe: e instanceof Falha ? null : e.stack }
  } finally { clearTimeout(timer) }
}

async function main() {
  const args = process.argv.slice(2)
  if (args.includes('--ajuda') || args.includes('-h') || args.length === 0) { console.log(USO); return args.length === 0 ? 2 : 0 }

  const todos = await carregar()
  if (args.includes('--lista')) {
    for (const c of todos) console.log(`${c.id.padEnd(9)} ${c.critico ? 'CRÍTICO' : '       '}  ${c.feature.padEnd(12)} ${c.titulo}`)
    console.log(`\n${todos.length} cenários, ${todos.filter((c) => c.critico).length} críticos`)
    return 0
  }

  let escolhidos, modo
  const iRegra = args.indexOf('--regra')
  if (iRegra >= 0) {
    const ids = (args[iRegra + 1] ?? '').split(',').filter(Boolean).map((s) => s.toUpperCase())
    const faltam = ids.filter((id) => !todos.some((c) => c.id === id))
    if (!ids.length || faltam.length) { console.error(`Regra sem cenário: ${faltam.join(', ') || '(nenhuma informada)'}. Use --lista.`); return 2 }
    escolhidos = todos.filter((c) => ids.includes(c.id)); modo = `--regra ${ids.join(',')}`
  } else if (args.includes('--criticos')) { escolhidos = todos.filter((c) => c.critico); modo = '--criticos' }
  else if (args.includes('--todos')) { escolhidos = todos; modo = '--todos' }
  else { console.log(USO); return 2 }

  let subiu = false
  if (!(await saudavel())) {
    if (!args.includes('--subir')) {
      console.error(`Backend fora do ar em ${API}.\nSuba o stack com: tests/multi-aparelhos/stack.sh up   (ou rode de novo com --subir)`)
      return 2
    }
    console.log('Subindo o stack local (stack.sh up)…')
    execFileSync(STACK, ['up'], { stdio: 'inherit' })
    subiu = true
  }

  const inicio = new Date()
  const resultados = []
  console.log(`${escolhidos.length} cenário(s) · ${modo} · ${API}\n`)
  for (const cenario of escolhidos) {
    const r = { id: cenario.id, titulo: cenario.titulo, critico: !!cenario.critico, achado: cenario.achado, ...(await rodar(cenario)) }
    resultados.push(r)
    console.log(linhaTerminal(r))
    if (r.detalhe && process.env.SAQZ_ATORES_PILHA) console.log(r.detalhe)
  }
  const ms = Date.now() - inicio.getTime()
  gravarRelatorio(path.join(AQUI, 'ultimo-resultado.md'), { resultados, modo, inicio, ms })

  const n = (e) => resultados.filter((r) => r.estado === e).length
  console.log(`\n${n('passou')} passaram · ${n('falhou')} falharam · ${n('bloqueado')} bloqueados · ${(ms / 1000).toFixed(0)} s · tests/atores/ultimo-resultado.md`)
  if (subiu && args.includes('--derrubar')) execFileSync(STACK, ['down'], { stdio: 'inherit' })
  return n('falhou') + n('bloqueado') > 0 ? 1 : 0
}

process.exit(await main())
