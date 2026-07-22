package br.com.saqz.domain

interface SaqzError

sealed interface SaqzResult<out T, out E : SaqzError> {
    data class Success<out T>(val value: T) : SaqzResult<T, Nothing>

    data class Failure<out E : SaqzError>(val error: E) : SaqzResult<Nothing, E>
}

typealias EmptyResult<E> = SaqzResult<Unit, E>

inline fun <T, E : SaqzError, R> SaqzResult<T, E>.map(
    transform: (T) -> R,
): SaqzResult<R, E> = when (this) {
    is SaqzResult.Success -> SaqzResult.Success(transform(value))
    is SaqzResult.Failure -> this
}

inline fun <T, E : SaqzError, R : SaqzError> SaqzResult<T, E>.mapError(
    transform: (E) -> R,
): SaqzResult<T, R> = when (this) {
    is SaqzResult.Success -> this
    is SaqzResult.Failure -> SaqzResult.Failure(transform(error))
}

inline fun <T, E : SaqzError> SaqzResult<T, E>.onSuccess(
    action: (T) -> Unit,
): SaqzResult<T, E> = when (this) {
    is SaqzResult.Success -> apply { action(value) }
    is SaqzResult.Failure -> this
}

inline fun <T, E : SaqzError> SaqzResult<T, E>.onFailure(
    action: (E) -> Unit,
): SaqzResult<T, E> = when (this) {
    is SaqzResult.Success -> this
    is SaqzResult.Failure -> apply { action(error) }
}

fun <T, E : SaqzError> SaqzResult<T, E>.asEmptyResult(): EmptyResult<E> = map { }
