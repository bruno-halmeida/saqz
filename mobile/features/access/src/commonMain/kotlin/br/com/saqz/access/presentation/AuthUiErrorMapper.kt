package br.com.saqz.access.presentation

import br.com.saqz.access.domain.port.NativeFailureCode
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.auth_error_email_in_use
import br.com.saqz.access.resources.auth_error_invalid_credentials
import br.com.saqz.access.resources.auth_error_method_conflict
import br.com.saqz.access.resources.auth_error_network
import br.com.saqz.access.resources.auth_error_provider
import br.com.saqz.access.resources.auth_error_unknown
import br.com.saqz.access.resources.auth_error_weak_password
import br.com.saqz.designsystem.text.UiText

fun AuthUiError.message(): UiText = when (this) {
    AuthUiError.INVALID_CREDENTIALS -> UiText.Res(Res.string.auth_error_invalid_credentials)
    AuthUiError.EMAIL_IN_USE -> UiText.Res(Res.string.auth_error_email_in_use)
    AuthUiError.WEAK_PASSWORD -> UiText.Res(Res.string.auth_error_weak_password)
    AuthUiError.AUTH_METHOD_CONFLICT -> UiText.Res(Res.string.auth_error_method_conflict)
    AuthUiError.NETWORK_UNAVAILABLE -> UiText.Res(Res.string.auth_error_network)
    AuthUiError.PROVIDER_UNAVAILABLE -> UiText.Res(Res.string.auth_error_provider)
    AuthUiError.UNKNOWN -> UiText.Res(Res.string.auth_error_unknown)
}

fun NativeFailureCode.toUiError(): AuthUiError = when (this) {
    NativeFailureCode.INVALID_CREDENTIALS -> AuthUiError.INVALID_CREDENTIALS
    NativeFailureCode.EMAIL_IN_USE -> AuthUiError.EMAIL_IN_USE
    NativeFailureCode.WEAK_PASSWORD -> AuthUiError.WEAK_PASSWORD
    NativeFailureCode.AUTH_METHOD_CONFLICT -> AuthUiError.AUTH_METHOD_CONFLICT
    NativeFailureCode.NETWORK_UNAVAILABLE -> AuthUiError.NETWORK_UNAVAILABLE
    NativeFailureCode.PROVIDER_UNAVAILABLE -> AuthUiError.PROVIDER_UNAVAILABLE
    NativeFailureCode.UNKNOWN -> AuthUiError.UNKNOWN
}
