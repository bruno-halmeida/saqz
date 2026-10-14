package br.com.saqz.network

/**
 * Como uma exceção do engine HTTP se traduz em [NetworkError]. Cada plataforma lança coisas
 * diferentes quando não há rede, e só `UnresolvedAddressException` era reconhecida; o resto
 * caía em `Unknown` e a UI não oferecia "Sem conexão" nem recarregava sozinha.
 *
 * A inspeção é pelo nome da classe e pela mensagem ao longo da cadeia de causas, porque os
 * tipos de cada engine (`java.net.*`, `NSError`, `TypeError` do fetch) não existem no common.
 * Devolve nulo quando a falha não é de transporte reconhecível (fica `Unknown`).
 */
internal fun Throwable.toTransportFailure(): NetworkError? {
    var current: Throwable? = this
    var depth = 0
    var connectivity = false
    while (current != null && depth < MAX_CAUSE_DEPTH) {
        val name = current::class.simpleName.orEmpty()
        val message = current.message.orEmpty()
        if (name in TIMEOUT_NAMES || NSURL_TIMEOUT in message) return NetworkError.Timeout
        if (name in CONNECTIVITY_NAMES || CONNECTIVITY_MARKERS.any { message.contains(it, ignoreCase = true) }) {
            connectivity = true
        }
        current = current.cause?.takeIf { it !== current }
        depth++
    }
    return if (connectivity) NetworkError.Connectivity else null
}

private const val MAX_CAUSE_DEPTH = 8
private const val NSURL_TIMEOUT = "Code=-1001"

private val TIMEOUT_NAMES = setOf("SocketTimeoutException", "ConnectTimeoutException", "HttpRequestTimeoutException")

private val CONNECTIVITY_NAMES = setOf(
    "ConnectException",
    "UnknownHostException",
    "NoRouteToHostException",
    "SocketException",
    "UnresolvedAddressException",
)

/** NSURLError -1009 sem rede, -1004 sem conexão com o servidor, -1005 conexão perdida, -1020 dado não permitido. */
private val CONNECTIVITY_MARKERS = listOf(
    "Code=-1009",
    "Code=-1004",
    "Code=-1005",
    "Code=-1020",
    "Failed to fetch",
    "Load failed",
    "NetworkError when attempting to fetch",
    "Network is unreachable",
)
