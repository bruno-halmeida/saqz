package br.com.saqz.identity.application

import br.com.saqz.sharedkernel.RequestIdentity

sealed interface TokenVerification {
    data class Verified(val principal: RequestIdentity) : TokenVerification

    data object Rejected : TokenVerification

    data object ProviderUnavailable : TokenVerification
}
