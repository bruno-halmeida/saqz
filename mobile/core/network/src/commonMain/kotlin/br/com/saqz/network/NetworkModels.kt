package br.com.saqz.network

import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.ContentType
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.Serializable

enum class NetworkEnvironment {
    Dev,
    Prod,
    Test,
    Unconfigured,
}

fun String.toNetworkEnvironment(): NetworkEnvironment = when (lowercase()) {
    "prod" -> NetworkEnvironment.Prod
    "test" -> NetworkEnvironment.Test
    "unconfigured" -> NetworkEnvironment.Unconfigured
    else -> NetworkEnvironment.Dev
}

data class NetworkConfig(
    val environment: NetworkEnvironment,
    val baseUrl: String,
    val requestTimeoutMillis: Long = 10_000,
    val maxErrorBodyBytes: Int = 16_384,
    val maxBinaryBodyBytes: Int = 5 * 1024 * 1024,
) {
    init {
        val protocol = Url(baseUrl).protocol
        require(protocol == URLProtocol.HTTP || protocol == URLProtocol.HTTPS) {
            "base URL must use HTTP or HTTPS"
        }
        require(requestTimeoutMillis > 0) { "request timeout must be positive" }
        require(maxErrorBodyBytes > 0) { "error body limit must be positive" }
        require(maxBinaryBodyBytes > 0) { "binary body limit must be positive" }
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

    data object Connectivity : NetworkError

    data object Unavailable : NetworkError

    data object Unknown : NetworkError

    data object InvalidResponse : NetworkError

    data object PayloadTooLarge : NetworkError
}

// Kept as a plain top-level function because the same extension was not visible
// across KMP modules to consumers (unresolved reference in :features:groups).
fun isProblem(error: NetworkError, status: Int, code: String): Boolean =
    error is NetworkError.ApiProblemError && error.problem.status == status && error.problem.code == code

sealed interface NetworkResult<out T> {
    data class Success<T>(
        val value: T,
        val metadata: NetworkResponseMetadata = NetworkResponseMetadata(),
    ) : NetworkResult<T>

    data class Failure(val error: NetworkError) : NetworkResult<Nothing>
}

data class NetworkRequest(
    val body: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

data class NetworkMediaUpload(
    val fieldName: String = "file",
    val fileName: String,
    val contentType: ContentType,
    val contentLength: Long,
    val etag: String? = null,
    val openChannel: () -> ByteReadChannel,
) {
    init {
        require(fieldName.matches(Regex("[A-Za-z0-9_-]+"))) { "invalid multipart field name" }
        require(fileName.isNotBlank() && fileName.none { it == '\r' || it == '\n' || it == '"' }) {
            "invalid multipart filename"
        }
        require(contentLength >= 0) { "content length must not be negative" }
    }
}

data class NetworkBinaryBody(
    val bytes: ByteArray,
    val contentType: String,
    val etag: String?,
    val cacheControl: String?,
) {
    override fun equals(other: Any?): Boolean = other is NetworkBinaryBody &&
        bytes.contentEquals(other.bytes) && contentType == other.contentType &&
        etag == other.etag && cacheControl == other.cacheControl

    override fun hashCode(): Int = bytes.contentHashCode()
}

data class NetworkResponseMetadata(
    val headers: Map<String, List<String>> = emptyMap(),
) {
    fun header(name: String): String? = headers.entries
        .firstOrNull { (key) -> key.equals(name, ignoreCase = true) }
        ?.value
        ?.firstOrNull()
}
