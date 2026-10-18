/** Repete `fn` até devolver algo verdadeiro ou estourar o teto (ms). Devolve o valor, ou lança com `descricao`. */
export async function esperar(descricao, fn, { teto = 90_000, passo = 1_000 } = {}) {
  const limite = Date.now() + teto
  let ultimo
  for (;;) {
    try { ultimo = await fn(); if (ultimo) return ultimo } catch (e) { ultimo = e }
    if (Date.now() >= limite) throw new Error(`Tempo esgotado esperando: ${descricao}${ultimo instanceof Error ? ` (${ultimo.message})` : ''}`)
    await new Promise((r) => setTimeout(r, passo))
  }
}

export const dormir = (ms) => new Promise((r) => setTimeout(r, ms))
