package br.com.saqz.network

import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.serialization.Serializable

data class NetworkConfig(
    val environment: String,
    val baseUrl: String,
    val requestTimeoutMillis: Long = 10_000,
    val maxErrorBodyBytes: Int = 16_384,
) {
    init {
        val protocol = Url(baseUrl).protocol
        require(protocol == URLProtocol.HTTP || protocol == URLProtocol.HTTPS) {
            "base URL must use HTTP or HTTPS"
        }
        require(requestTimeoutMillis > 0) { "request timeout must be positive" }
        require(maxErrorBodyBytes > 0) { "error body limit must be positive" }
    }
}

@Serializable
data class ApiProblem(
    val status: Int,
    val code: String? = null,
    val correlationId: String,
    val fieldErrors: Map<String, List<String>>? = null,
    val retryAfterSeconds: Int? = null,
)

sealed interface NetworkError {
    data class ApiProblemError(val problem: ApiProblem) : NetworkError

    data class HttpStatus(val status: Int) : NetworkError

    data object Timeout : NetworkError

    data object Unavailable : NetworkError

    data object InvalidResponse : NetworkError
}

sealed interface NetworkResult<out T> {
    data class Success<T>(val value: T) : NetworkResult<T>

    data class Failure(val error: NetworkError) : NetworkResult<Nothing>
}
