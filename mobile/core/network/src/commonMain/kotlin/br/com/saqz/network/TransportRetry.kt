package br.com.saqz.network

import kotlinx.coroutines.delay

enum class RetrySafety {
    Never,
    Read,
    IdempotentWrite,
}

data class TransportRetryPolicy(
    val retryDelaysMillis: List<Long> = listOf(500L, 1_000L, 2_000L),
)

suspend fun <T> retryTransport(
    safety: RetrySafety,
    policy: TransportRetryPolicy = TransportRetryPolicy(),
    delayMillis: suspend (Long) -> Unit = { delay(it) },
    call: suspend () -> NetworkResult<T>,
): NetworkResult<T> {
    var result = call()
    if (safety == RetrySafety.Never) return result

    for (backoff in policy.retryDelaysMillis) {
        if (!result.isRetryableFailure()) return result
        delayMillis(backoff)
        result = call()
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
