import { writeFileSync } from 'node:fs'

const seg = (ms) => `${(ms / 1000).toFixed(1)} s`
const limpar = (t) => String(t ?? '').replaceAll('|', '\\|').replaceAll('\n', ' ')

/** Linha do terminal para um resultado. */
export function linhaTerminal(r) {
  const marca = { passou: 'PASSOU ', falhou: 'FALHOU ', bloqueado: 'BLOQUEADO' }[r.estado]
  const extra = r.estado === 'passou' ? '' : `\n      passo: ${r.passo}\n      ${r.motivo}${r.resposta ? `\n      servidor: ${r.resposta}` : ''}`
  return `  ${marca} ${r.id.padEnd(9)} ${seg(r.ms).padStart(7)}  ${r.titulo}${extra}`
}

/** `tests/atores/ultimo-resultado.md`: regra, passou/falhou, tempo, passo que falhou, resposta do servidor. */
export function gravarRelatorio(caminho, { resultados, modo, inicio, ms }) {
  const total = resultados.length
  const n = (e) => resultados.filter((r) => r.estado === e).length
  const linhas = [
    '# Resultado dos atores por API',
    '',
    `Gerado por \`node tests/atores/run.mjs ${modo}\` em ${inicio.toISOString()} (${seg(ms)}).`,
    '',
    `**${n('passou')} passaram · ${n('falhou')} falharam · ${n('bloqueado')} bloqueados** de ${total}.`,
    '',
    '| Regra | Crítica | Resultado | Tempo | Passo que falhou | Resposta do servidor |',
    '| --- | :---: | --- | ---: | --- | --- |',
    ...resultados.map((r) => `| ${r.id} · ${limpar(r.titulo)} | ${r.critico ? 'sim' : ''} | ${{ passou: 'passou', falhou: '**falhou**', bloqueado: 'bloqueado' }[r.estado]} | ${seg(r.ms)} | ${r.estado === 'passou' ? '' : limpar(`${r.passo}: ${r.motivo}`)} | ${r.resposta ? `\`${limpar(r.resposta).slice(0, 300)}\`` : ''} |`),
    '',
  ]
  writeFileSync(caminho, linhas.join('\n'))
}
