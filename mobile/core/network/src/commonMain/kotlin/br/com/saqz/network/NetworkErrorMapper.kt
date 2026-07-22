package br.com.saqz.network

import kotlinx.serialization.json.Json

fun interface NetworkErrorMapper {
    fun map(status: Int, body: String): NetworkError
}

class ApiProblemErrorMapper(
    private val json: Json,
) : NetworkErrorMapper {
    override fun map(status: Int, body: String): NetworkError {
        val problem = runCatching { json.decodeFromString(ApiProblem.serializer(), body) }.getOrNull()
        return if (problem == null) NetworkError.HttpStatus(status) else NetworkError.ApiProblemError(problem)
    }
}

internal fun defaultNetworkJson() = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
