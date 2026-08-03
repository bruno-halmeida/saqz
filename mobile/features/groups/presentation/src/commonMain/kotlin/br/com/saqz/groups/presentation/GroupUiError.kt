package br.com.saqz.groups.presentation

import br.com.saqz.domain.DataError
import br.com.saqz.groups.domain.athlete.AthleteError
import br.com.saqz.groups.domain.game.GameError
import br.com.saqz.groups.domain.group.GroupProfileError
import br.com.saqz.groups.domain.membership.GroupMembershipError

/** Erros que a apresentação conhece; o detalhe de transporte morre antes de chegar à UI. */
enum class GroupUiError {
    AccessDenied,
    NotFound,
    Conflict,
    Validation,
    Network,
    Unknown,
}

fun GroupProfileError.toUiError(): GroupUiError = when (this) {
    is GroupProfileError.Validation -> GroupUiError.Validation
    is GroupProfileError.Conflict -> GroupUiError.Conflict
    is GroupProfileError.DataFailure -> error.toUiError()
}

fun AthleteError.toUiError(): GroupUiError = when (this) {
    is AthleteError.Validation -> GroupUiError.Validation
    is AthleteError.DataFailure -> error.toUiError()
}

fun GroupMembershipError.toUiError(): GroupUiError = when (this) {
    GroupMembershipError.InvalidOrExpired -> GroupUiError.NotFound
    is GroupMembershipError.AttemptLimit -> GroupUiError.Network
    is GroupMembershipError.Validation -> GroupUiError.Validation
    is GroupMembershipError.DataFailure -> error.toUiError()
}

fun GameError.toUiError(): GroupUiError = when (this) {
    is GameError.Validation -> GroupUiError.Validation
    GameError.HiddenResource -> GroupUiError.NotFound
    is GameError.Conflict -> GroupUiError.Conflict
    GameError.VersionConflict -> GroupUiError.Conflict
    GameError.InvalidLifecycle -> GroupUiError.Validation
    GameError.Authentication -> GroupUiError.AccessDenied
    is GameError.Data -> error.toUiError()
}

private fun DataError.toUiError(): GroupUiError = when (this) {
    DataError.Forbidden -> GroupUiError.AccessDenied
    DataError.NotFound -> GroupUiError.NotFound
    DataError.Conflict -> GroupUiError.Conflict
    is DataError.Validation -> GroupUiError.Validation
    DataError.Unauthenticated -> GroupUiError.AccessDenied
    DataError.Connectivity,
    DataError.Timeout,
    DataError.InvalidResponse,
    DataError.PayloadTooLarge,
    DataError.Server,
    DataError.Unknown,
    -> GroupUiError.Network
}
