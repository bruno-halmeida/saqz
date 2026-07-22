package br.com.saqz.domain

data class ValidationDetails(
    val globalMessages: List<String>,
    val fieldMessages: Map<String, List<String>>,
)

sealed interface DataError : SaqzError {
    data object Connectivity : DataError
    data object Timeout : DataError
    data object Unauthenticated : DataError
    data object Forbidden : DataError
    data class Validation(val details: ValidationDetails) : DataError
    data object Conflict : DataError
    data object NotFound : DataError
    data object InvalidResponse : DataError
    data object PayloadTooLarge : DataError
    data object Server : DataError
    data object Unknown : DataError
}
