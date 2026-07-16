package br.com.saqz.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.appendPathSegments
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.io.readByteArray
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import io.ktor.utils.io.readRemaining

class NetworkClient(
    engine: HttpClientEngine,
    private val config: NetworkConfig,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val client = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = config.requestTimeoutMillis
            connectTimeoutMillis = config.requestTimeoutMillis
            socketTimeoutMillis = config.requestTimeoutMillis
        }
    }

    suspend fun <T> execute(
        method: HttpMethod,
        path: String,
        responseSerializer: KSerializer<T>,
        bearerToken: String? = null,
    ): NetworkResult<T> = try {
        val response = client.request(config.baseUrl) {
            this.method = method
            url { appendPathSegments(path.trimStart('/')) }
            if (bearerToken != null) bearerAuth(bearerToken)
        }
        if (response.status.value in 200..299) {
            val body = response.bodyAsText()
            runCatching { json.decodeFromString(responseSerializer, body) }
                .fold(
                    onSuccess = { NetworkResult.Success(it) },
                    onFailure = { NetworkResult.Failure(NetworkError.InvalidResponse) },
                )
        } else {
            val bytes = response.bodyAsChannel()
                .readRemaining(config.maxErrorBodyBytes.toLong() + 1)
                .readByteArray()
            val error = if (bytes.size > config.maxErrorBodyBytes) {
                NetworkError.HttpStatus(response.status.value)
            } else {
                mapError(response.status.value, bytes.decodeToString())
            }
            NetworkResult.Failure(error)
        }
    } catch (_: HttpRequestTimeoutException) {
        NetworkResult.Failure(NetworkError.Timeout)
    } catch (_: SocketTimeoutException) {
        NetworkResult.Failure(NetworkError.Timeout)
    } catch (failure: CancellationException) {
        throw failure
    } catch (_: Throwable) {
        NetworkResult.Failure(NetworkError.Unavailable)
    }

    fun close() {
        client.close()
    }

    private fun mapError(status: Int, body: String): NetworkError {
        val problem = runCatching { json.decodeFromString(ApiProblem.serializer(), body) }.getOrNull()
        return if (problem == null) NetworkError.HttpStatus(status) else NetworkError.ApiProblemError(problem)
    }
}

expect fun createPlatformNetworkClient(config: NetworkConfig): NetworkClient
