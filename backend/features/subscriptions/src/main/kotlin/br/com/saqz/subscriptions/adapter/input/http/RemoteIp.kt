package br.com.saqz.subscriptions.adapter.input.http

import jakarta.servlet.http.HttpServletRequest

/**
 * Backend fica atrás do Traefik: `request.remoteAddr` sozinho devolve o IP do proxy, não do
 * dispositivo do pagador (VUL-194). A Asaas exige o IP real do cliente para antifraude no
 * cartão — lê X-Forwarded-For explicitamente (primeiro IP da lista é o do cliente original;
 * cada proxy no caminho só acrescenta o próprio ao final) e cai para remoteAddr quando ausente.
 */
fun resolveRemoteIp(request: HttpServletRequest): String {
    val forwardedFor = request.getHeader("X-Forwarded-For")
    val firstHop = forwardedFor
        ?.substringBefore(',')
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    return firstHop ?: request.remoteAddr
}
