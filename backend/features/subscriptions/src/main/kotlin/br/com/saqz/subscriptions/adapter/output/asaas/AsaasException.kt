package br.com.saqz.subscriptions.adapter.output.asaas

class AsaasException(
    val statusCode: Int,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
