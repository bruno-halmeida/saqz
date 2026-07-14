package br.com.saqz.identity.application

fun interface IdentityTokenVerifier {
    fun verify(token: RawIdentityToken): TokenVerification
}
