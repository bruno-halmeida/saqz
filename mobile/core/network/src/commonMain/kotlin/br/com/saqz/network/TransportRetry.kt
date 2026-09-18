package br.com.saqz.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Identidade de uma operação de rede: o mesmo [id] em todas as tentativas e o número da
 * [attempt]. Viaja pelo contexto da coroutine para que nenhum gateway precise carregá-lo: o
 * [retryTransport] instala, o `HttpTransport` lê. Sem ele, o transporte gera um id novo com
 * tentativa 1 (chamada sem retry).
 */
internal class CorrelationContext(val id: String, val attempt: Int) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<CorrelationContext>
}

enum class RetrySafety {
    Never,
    Read,
    IdempotentWrite,
}

data class TransportRetryPolicy(
    val retryDelaysMillis: List<Long> = listOf(500L, 1_000L, 2_000L),
)

@OptIn(ExperimentalUuidApi::class)
suspend fun <T> retryTransport(
    safety: RetrySafety,
    policy: TransportRetryPolicy = TransportRetryPolicy(),
    delayMillis: suspend (Long) -> Unit = { delay(it) },
    call: suspend () -> NetworkResult<T>,
): NetworkResult<T> {
    // Um id por operação: os retries compartilham, senão o rastro no backend e no Crashlytics
    // vira três requests sem parentesco.
    val correlationId = Uuid.random().toString()
    var attempt = 1
    var result = withContext(CorrelationContext(correlationId, attempt)) { call() }
    if (safety == RetrySafety.Never) return result

    for (backoff in policy.retryDelaysMillis) {
        if (!result.isRetryableFailure()) return result
        delayMillis(backoff)
        attempt += 1
        result = withContext(CorrelationContext(correlationId, attempt)) { call() }
    }
    return result
}

private fun NetworkResult<*>.isRetryableFailure(): Boolean {
    val failure = this as? NetworkResult.Failure ?: return false
    return when (val error = failure.error) {
        NetworkError.Connectivity,
        NetworkError.Timeout,
        -> true

        is NetworkError.ApiProblemError -> error.problem.status in 500..599
        is NetworkError.HttpStatus -> error.status in 500..599
        NetworkError.InvalidResponse,
        NetworkError.PayloadTooLarge,
        NetworkError.Unavailable,
        NetworkError.Unknown,
        -> false
    }
}
