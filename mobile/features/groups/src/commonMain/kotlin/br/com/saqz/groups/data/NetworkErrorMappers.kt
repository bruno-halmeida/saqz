package br.com.saqz.groups.data

import br.com.saqz.network.ApiProblem
import br.com.saqz.network.NetworkError

sealed interface SetupFailure {
    data object Conflict : SetupFailure
    data object Forbidden : SetupFailure
    data object NotFound : SetupFailure
    data class Validation(val fieldErrors: Map<String, List<String>>) : SetupFailure
    data object Unavailable : SetupFailure
}

fun NetworkError.toSetupFailure(): SetupFailure = when (this) {
    is NetworkError.ApiProblemError -> problem.toSetupFailure()
    else -> SetupFailure.Unavailable
}

private fun ApiProblem.toSetupFailure(): SetupFailure = when {
    status == 400 -> SetupFailure.Validation(fieldErrors.orEmpty())
    status == 403 -> SetupFailure.Forbidden
    status == 404 -> SetupFailure.NotFound
    code == "VERSION_CONFLICT" -> SetupFailure.Conflict
    else -> SetupFailure.Unavailable
}

sealed interface AdministrationFailure {
    data object Conflict : AdministrationFailure
    data object Forbidden : AdministrationFailure
    data object NotFound : AdministrationFailure
    data class Validation(val fieldErrors: Map<String, List<String>>) : AdministrationFailure
    data object Unavailable : AdministrationFailure
}

fun NetworkError.toAdministrationFailure(): AdministrationFailure = when (this) {
    is NetworkError.ApiProblemError -> problem.toAdministrationFailure()
    else -> AdministrationFailure.Unavailable
}

private fun ApiProblem.toAdministrationFailure(): AdministrationFailure = when {
    status == 400 -> AdministrationFailure.Validation(fieldErrors.orEmpty())
    status == 403 -> AdministrationFailure.Forbidden
    status == 404 -> AdministrationFailure.NotFound
    code == "VERSION_CONFLICT" -> AdministrationFailure.Conflict
    else -> AdministrationFailure.Unavailable
}

sealed interface PhotoFailure {
    data object NotFound : PhotoFailure
    data object StaleVersion : PhotoFailure
    data object Failed : PhotoFailure
}

fun NetworkError.toPhotoFailure(): PhotoFailure = when (this) {
    is NetworkError.HttpStatus -> if (status == 404) PhotoFailure.NotFound else PhotoFailure.Failed
    is NetworkError.ApiProblemError -> problem.toPhotoFailure()
    else -> PhotoFailure.Failed
}

private fun ApiProblem.toPhotoFailure(): PhotoFailure = when {
    status == 404 -> PhotoFailure.NotFound
    code == "VERSION_CONFLICT" -> PhotoFailure.StaleVersion
    else -> PhotoFailure.Failed
}

sealed interface DeferredLinkFailure {
    data object InvalidOrExpired : DeferredLinkFailure
    data class AttemptLimit(val retryAfterSeconds: Int?) : DeferredLinkFailure
    data object Unavailable : DeferredLinkFailure
}

fun NetworkError.toDeferredLinkFailure(
    invalidCode: String,
    attemptLimitCode: String,
): DeferredLinkFailure = when (this) {
    is NetworkError.ApiProblemError -> problem.toDeferredLinkFailure(invalidCode, attemptLimitCode)
    else -> DeferredLinkFailure.Unavailable
}

private fun ApiProblem.toDeferredLinkFailure(
    invalidCode: String,
    attemptLimitCode: String,
): DeferredLinkFailure = when {
    code == invalidCode -> DeferredLinkFailure.InvalidOrExpired
    code == attemptLimitCode -> DeferredLinkFailure.AttemptLimit(retryAfterSeconds)
    else -> DeferredLinkFailure.Unavailable
}
