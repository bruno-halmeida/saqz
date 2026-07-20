package br.com.saqz.network

import io.ktor.http.HttpMethod
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

sealed interface TokenResult {
    data class Available(val value: String) : TokenResult

    data object Unavailable : TokenResult
}

interface IdTokenProvider {
    fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit)
}

interface SessionInvalidator {
    fun invalidate()
}

class AuthenticatedNetworkClient(
    private val network: NetworkClient,
    private val tokenProvider: IdTokenProvider,
    private val sessionInvalidator: SessionInvalidator,
) {
    private val refreshMutex = Mutex()
    private var lastFailedToken: String? = null
    private var lastRefreshedToken: String? = null

    suspend fun <T> execute(
        method: HttpMethod,
        path: String,
        responseSerializer: KSerializer<T>,
        request: NetworkRequest = NetworkRequest(),
    ): NetworkResult<T> = authenticated { token ->
        network.execute(method, path, responseSerializer, token, request)
    }

    suspend fun executeNoContent(
        method: HttpMethod,
        path: String,
        request: NetworkRequest = NetworkRequest(),
    ): NetworkResult<Unit> = authenticated { token ->
        network.executeNoContent(method, path, token, request)
    }

    suspend fun uploadMedia(
        method: HttpMethod,
        path: String,
        upload: NetworkMediaUpload,
        request: NetworkRequest = NetworkRequest(),
    ): NetworkResult<Unit> = authenticated { token ->
        network.uploadMedia(method, path, upload, token, request)
    }

    suspend fun readBinary(
        path: String,
        request: NetworkRequest = NetworkRequest(),
    ): NetworkResult<NetworkBinaryBody> = authenticated { token ->
        network.readBinary(path, token, request)
    }

    private suspend fun <T> authenticated(
        execute: suspend (String) -> NetworkResult<T>,
    ): NetworkResult<T> {
        val current = token(forceRefresh = false)
        if (current !is TokenResult.Available) return NetworkResult.Failure(NetworkError.Unavailable)
        val first = execute(current.value)
        if (!first.isUnauthorized()) return first

        val refreshed = refresh(current.value)
        if (refreshed !is TokenResult.Available) return NetworkResult.Failure(NetworkError.Unavailable)
        val second = execute(refreshed.value)
        if (second.isUnauthorized()) sessionInvalidator.invalidate()
        return second
    }

    private suspend fun refresh(failedToken: String): TokenResult = refreshMutex.withLock {
        if (lastFailedToken == failedToken && lastRefreshedToken != null) {
            return@withLock TokenResult.Available(lastRefreshedToken!!)
        }
        val refreshed = token(forceRefresh = true)
        if (refreshed is TokenResult.Available) {
            lastFailedToken = failedToken
            lastRefreshedToken = refreshed.value
        }
        refreshed
    }

    private suspend fun token(forceRefresh: Boolean): TokenResult = suspendCoroutine { continuation ->
        tokenProvider.token(forceRefresh) { continuation.resume(it) }
    }
}

private fun NetworkResult<*>.isUnauthorized(): Boolean {
    val error = (this as? NetworkResult.Failure)?.error ?: return false
    return when (error) {
        is NetworkError.ApiProblemError -> error.problem.status == 401
        is NetworkError.HttpStatus -> error.status == 401
        else -> false
    }
}
