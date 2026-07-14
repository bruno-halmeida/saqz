package br.com.saqz.identity.application

import br.com.saqz.identity.api.AuthenticatedPrincipal

sealed interface TokenVerification {
    data class Verified(val principal: AuthenticatedPrincipal) : TokenVerification

    data object Rejected : TokenVerification

    data object ProviderUnavailable : TokenVerification
}
