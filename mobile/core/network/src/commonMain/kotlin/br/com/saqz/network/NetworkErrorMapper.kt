package br.com.saqz.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

fun interface NetworkErrorMapper {
    fun map(status: Int, body: String): NetworkError
}

/**
 * VUL-196: `{"error":"card_declined","reason":"...","message":"..."}` (402 do Asaas) é um
 * shape à parte do `ApiProblem` padrão — sem `status`/`code`/`correlationId`. Tentar decodificar
 * como `ApiProblem` primeiro falha nesse corpo (faltam campos obrigatórios) e cai aqui.
 */
@Serializable
private data class DeclinedProblemTransport(val error: String, val reason: String, val message: String)

class ApiProblemErrorMapper(
    private val json: Json,
) : NetworkErrorMapper {
    override fun map(status: Int, body: String): NetworkError {
        val problem = runCatching { json.decodeFromString(ApiProblem.serializer(), body) }.getOrNull()
        if (problem != null) return NetworkError.ApiProblemError(problem)
        val declined = runCatching { json.decodeFromString(DeclinedProblemTransport.serializer(), body) }.getOrNull()
            ?: return NetworkError.HttpStatus(status)
        // `status` vem do transporte, nunca do corpo — essa forma não carrega um `status`
        // próprio, e usar o HTTP real (em vez de inventar um) é o que mantém
        // `problem.status.toDataError()`/`isRetryableFailure` corretos para quem só olha o
        // status e não o `error` (nenhum outro gateway do app conhece "card_declined").
        return NetworkError.ApiProblemError(
            ApiProblem(status = status, error = declined.error, reason = declined.reason, message = declined.message),
        )
    }
}

internal fun defaultNetworkJson() = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
