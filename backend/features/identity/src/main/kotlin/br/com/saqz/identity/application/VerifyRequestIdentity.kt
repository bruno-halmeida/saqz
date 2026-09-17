package br.com.saqz.identity.application

fun interface VerifyRequestIdentity {
    fun execute(token: RawIdentityToken): TokenVerification

    /** Verificação que nunca usa cache: para requisição sensível (pagamento, exclusão). */
    fun executeFresh(token: RawIdentityToken): TokenVerification = execute(token)
}

class DefaultVerifyRequestIdentity(
    private val verifier: IdentityTokenVerifier,
) : VerifyRequestIdentity {
    override fun execute(token: RawIdentityToken): TokenVerification = verifier.verify(token)
}
