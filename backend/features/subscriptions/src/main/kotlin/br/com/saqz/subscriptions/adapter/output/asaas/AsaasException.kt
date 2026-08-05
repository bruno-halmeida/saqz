package br.com.saqz.subscriptions.adapter.output.asaas

open class AsaasException(
    val statusCode: Int,
    message: String,
    cause: Throwable? = null,
    /** First `errors[].code` / `.description` from the Asaas response, when parseable. */
    val errorCode: String? = null,
    val errorDescription: String? = null,
) : RuntimeException(message, cause)
