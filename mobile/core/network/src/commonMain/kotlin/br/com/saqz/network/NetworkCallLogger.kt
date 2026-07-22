package br.com.saqz.network

import io.ktor.http.HttpMethod
import kotlin.time.TimeMark

fun interface NetworkCallLogger {
    fun log(message: String)
}

typealias NetworkLogger = NetworkCallLogger

internal fun NetworkCallLogger.safeLog(message: String) {
    runCatching { log(message) }
}

internal fun logMediaResponse(
    logger: NetworkCallLogger,
    method: HttpMethod,
    path: String,
    status: Int?,
    started: TimeMark,
) {
    logger.safeLog(
        "response ${method.value} $path status=${status ?: "none"} durationMs=${started.elapsedNow().inWholeMilliseconds}",
    )
}

internal fun logResponse(
    logger: NetworkCallLogger,
    requestDescription: String,
    status: Int?,
    started: TimeMark,
    result: NetworkResult<*>,
    cause: String? = null,
) {
    val statusDescription = status?.toString() ?: "none"
    val causeDescription = cause?.let { " cause=$it" }.orEmpty()
    logger.safeLog(
        "response $requestDescription status=$statusDescription " +
            "durationMs=${started.elapsedNow().inWholeMilliseconds} " +
            "result=${result.logDescription()}$causeDescription",
    )
}

private fun NetworkResult<*>.logDescription(): String = when (this) {
    is NetworkResult.Success -> "success"
    is NetworkResult.Failure -> error.logDescription()
}

private fun NetworkError.logDescription(): String = when (this) {
    is NetworkError.ApiProblemError -> buildString {
        append("api-error")
        append(" code=${problem.code ?: "none"}")
        append(" correlationId=${problem.correlationId}")
    }
    is NetworkError.HttpStatus -> "http-error status=$status"
    NetworkError.InvalidResponse -> "invalid-response"
    NetworkError.PayloadTooLarge -> "payload-too-large"
    NetworkError.Timeout -> "timeout"
    NetworkError.Unavailable -> "unavailable"
}
