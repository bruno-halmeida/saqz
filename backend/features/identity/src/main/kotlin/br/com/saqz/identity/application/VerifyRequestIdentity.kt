package br.com.saqz.identity.application

fun interface VerifyRequestIdentity {
    fun execute(token: RawIdentityToken): TokenVerification
}

class DefaultVerifyRequestIdentity(
    private val verifier: IdentityTokenVerifier,
) : VerifyRequestIdentity {
    override fun execute(token: RawIdentityToken): TokenVerification = verifier.verify(token)
}
