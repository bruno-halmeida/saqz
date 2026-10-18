import { API } from './config.mjs'

// Resposta de uma chamada: status, corpo (JSON ou texto), cabeçalhos e o texto bruto para o relatório.
export class Resposta {
  constructor(status, headers, texto, pedido) {
    this.status = status
    this.headers = headers
    this.texto = texto
    this.pedido = pedido
    try { this.corpo = texto ? JSON.parse(texto) : null } catch { this.corpo = texto }
  }
  get ok() { return this.status >= 200 && this.status < 300 }
  get etag() { return this.headers.get('etag') }
  /** Código de erro do servidor (`code` do problem+json). */
  get codigo() { return this.corpo?.code ?? null }
  resumo() { return `${this.pedido} → HTTP ${this.status} ${this.texto.slice(0, 500)}` }
}

// Última resposta recebida: o relatório mostra a do passo que falhou.
export const rastro = { ultima: null }

export async function chamar(metodo, caminho, { token, corpo, headers = {} } = {}) {
  const h = { ...headers }
  if (token) h.authorization = `Bearer ${token}`
  let body
  if (corpo !== undefined) { h['content-type'] = 'application/json'; body = JSON.stringify(corpo) }
  const r = await fetch(`${API}${caminho}`, { method: metodo, headers: h, body })
  const resposta = new Resposta(r.status, r.headers, await r.text(), `${metodo} ${caminho}`)
  rastro.ultima = resposta
  return resposta
}
