package br.com.saqz.identity.adapter.output.firebase

import br.com.saqz.identity.application.RawIdentityToken
import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.sharedkernel.RequestIdentity
import com.google.firebase.auth.AuthErrorCode
import org.junit.jupiter.api.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class FirebaseAdminTokenVerifierTest {
    @Test
    fun `propagates verified authentication time without substituting token issuance time`() {
        val verifier = verifierReturning(
            DecodedFirebaseToken("subject-1", null, true, authenticatedAtEpochSeconds = 1789336800L),
        )
        val result = verifier.verify(RawIdentityToken("token")) as TokenVerification.Verified

        assertEquals(1789336800L, result.principal.authenticatedAtEpochSeconds)
    }

    @Test
    fun `missing authentication time keeps identity usable but cannot establish recent authentication`() {
        val verifier = verifierReturning(DecodedFirebaseToken("subject-1", null, true))
        val result = verifier.verify(RawIdentityToken("token")) as TokenVerification.Verified

        assertEquals(null, result.principal.authenticatedAtEpochSeconds)
    }

    @Test
    fun `accepts only positive integer seconds in verified authentication claim`() {
        assertEquals(1789336800L, authenticationTimeClaim(1789336800L))
        assertEquals(1789336800L, authenticationTimeClaim(1789336800))
        listOf(null, 0L, -1L, "1789336800", 1789336800.5, true).forEach {
            assertEquals(null, authenticationTimeClaim(it))
        }
    }

    @Test
    fun `maps display name claim to request identity`() {
        val verifier = verifierReturning(
            DecodedFirebaseToken("subject-name", null, true, "Person Name"),
        )

        assertEquals(
            "Person Name",
            (verifier.verify(RawIdentityToken("token")) as TokenVerification.Verified)
                .principal.displayName,
        )
    }

    @Test
    fun `maps absent display name claim to null`() {
        val verifier = verifierReturning(
            DecodedFirebaseToken("subject-no-name", "person@example.test", true, null),
        )

        assertEquals(
            null,
            (verifier.verify(RawIdentityToken("token")) as TokenVerification.Verified)
                .principal.displayName,
        )
    }

    @Test
    fun `maps complete claims exactly`() {
        val verifier = verifierReturning(DecodedFirebaseToken("subject-1", "person@example.test", true))

        assertEquals(
            TokenVerification.Verified(RequestIdentity("subject-1", "person@example.test", true)),
            verifier.verify(RawIdentityToken("token")),
        )
    }

    @Test
    fun `maps absent optional claims to null`() {
        val verifier = verifierReturning(DecodedFirebaseToken("subject-2", null, null))

        assertEquals(
            TokenVerification.Verified(RequestIdentity("subject-2", null, null)),
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
    fun `maps a deleted or disabled identity to rejection`() {
        assertIs<InvalidFirebaseToken>(mapAuthErrorCode(AuthErrorCode.USER_NOT_FOUND))
        assertIs<InvalidFirebaseToken>(mapAuthErrorCode(AuthErrorCode.USER_DISABLED))
        assertIs<InvalidFirebaseToken>(mapAuthErrorCode(AuthErrorCode.INVALID_ID_TOKEN))
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
