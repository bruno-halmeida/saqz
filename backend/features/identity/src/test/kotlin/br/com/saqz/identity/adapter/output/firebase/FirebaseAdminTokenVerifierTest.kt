package br.com.saqz.identity.adapter.output.firebase

import br.com.saqz.identity.api.AuthenticatedPrincipal
import br.com.saqz.identity.application.RawIdentityToken
import br.com.saqz.identity.application.TokenVerification
import org.junit.jupiter.api.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertSame

class FirebaseAdminTokenVerifierTest {
    @Test
    fun `maps complete claims exactly`() {
        val verifier = verifierReturning(DecodedFirebaseToken("subject-1", "person@example.test", true))

        assertEquals(
            TokenVerification.Verified(AuthenticatedPrincipal("subject-1", "person@example.test", true)),
            verifier.verify(RawIdentityToken("token")),
        )
    }

    @Test
    fun `maps absent optional claims to null`() {
        val verifier = verifierReturning(DecodedFirebaseToken("subject-2", null, null))

        assertEquals(
            TokenVerification.Verified(AuthenticatedPrincipal("subject-2", null, null)),
            verifier.verify(RawIdentityToken("token")),
        )
    }

    @Test
    fun `rejects invalid signature without exposing provider failure`() {
        assertSame(TokenVerification.Rejected, verifierThrowing(InvalidFirebaseToken()).verify(RawIdentityToken("secret")))
    }

    @Test
    fun `rejects expired token without exposing provider failure`() {
        assertSame(TokenVerification.Rejected, verifierThrowing(ExpiredFirebaseToken()).verify(RawIdentityToken("secret")))
    }

    @Test
    fun `rejects revoked token without exposing provider failure`() {
        assertSame(TokenVerification.Rejected, verifierThrowing(RevokedFirebaseToken()).verify(RawIdentityToken("secret")))
    }

    @Test
    fun `maps timeout to provider unavailable`() {
        assertSame(TokenVerification.ProviderUnavailable, verifierThrowing(SocketTimeoutException()).verify(RawIdentityToken("secret")))
    }

    @Test
    fun `maps connection refusal to provider unavailable`() {
        assertSame(TokenVerification.ProviderUnavailable, verifierThrowing(ConnectException()).verify(RawIdentityToken("secret")))
    }

    @Test
    fun `maps provider service failure to provider unavailable`() {
        assertSame(TokenVerification.ProviderUnavailable, verifierThrowing(FirebaseProviderFailure()).verify(RawIdentityToken("secret")))
    }

    private fun verifierReturning(token: DecodedFirebaseToken) = FirebaseAdminTokenVerifier { token }

    private fun verifierThrowing(failure: Exception) = FirebaseAdminTokenVerifier { throw failure }
}
