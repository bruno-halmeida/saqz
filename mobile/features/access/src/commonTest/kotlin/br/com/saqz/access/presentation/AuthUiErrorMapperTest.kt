package br.com.saqz.access.presentation

import br.com.saqz.access.port.NativeFailureCode
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.auth_error_email_in_use
import br.com.saqz.access.resources.auth_error_invalid_credentials
import br.com.saqz.access.resources.auth_error_method_conflict
import br.com.saqz.access.resources.auth_error_network
import br.com.saqz.access.resources.auth_error_provider
import br.com.saqz.access.resources.auth_error_unknown
import br.com.saqz.access.resources.auth_error_weak_password
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthUiErrorMapperTest {
    @Test
    fun `messageRes covers every AuthUiError value`() {
        assertEquals(Res.string.auth_error_invalid_credentials, AuthUiError.INVALID_CREDENTIALS.messageRes())
        assertEquals(Res.string.auth_error_email_in_use, AuthUiError.EMAIL_IN_USE.messageRes())
        assertEquals(Res.string.auth_error_weak_password, AuthUiError.WEAK_PASSWORD.messageRes())
        assertEquals(Res.string.auth_error_method_conflict, AuthUiError.AUTH_METHOD_CONFLICT.messageRes())
        assertEquals(Res.string.auth_error_network, AuthUiError.NETWORK_UNAVAILABLE.messageRes())
        assertEquals(Res.string.auth_error_provider, AuthUiError.PROVIDER_UNAVAILABLE.messageRes())
        assertEquals(Res.string.auth_error_unknown, AuthUiError.UNKNOWN.messageRes())
    }

    @Test
    fun `toUiError covers every NativeFailureCode value`() {
        assertEquals(AuthUiError.INVALID_CREDENTIALS, NativeFailureCode.INVALID_CREDENTIALS.toUiError())
        assertEquals(AuthUiError.EMAIL_IN_USE, NativeFailureCode.EMAIL_IN_USE.toUiError())
        assertEquals(AuthUiError.WEAK_PASSWORD, NativeFailureCode.WEAK_PASSWORD.toUiError())
        assertEquals(AuthUiError.AUTH_METHOD_CONFLICT, NativeFailureCode.AUTH_METHOD_CONFLICT.toUiError())
        assertEquals(AuthUiError.NETWORK_UNAVAILABLE, NativeFailureCode.NETWORK_UNAVAILABLE.toUiError())
        assertEquals(AuthUiError.PROVIDER_UNAVAILABLE, NativeFailureCode.PROVIDER_UNAVAILABLE.toUiError())
        assertEquals(AuthUiError.UNKNOWN, NativeFailureCode.UNKNOWN.toUiError())
    }
}
