package br.com.saqz.identity.application

import br.com.saqz.identity.api.AuthenticatedPrincipal
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class DefaultVerifyRequestIdentityTest {
    @Test
    fun `preserves verified principal and delegates once`() {
        val expected = TokenVerification.Verified(
            AuthenticatedPrincipal("subject-1", "person@example.test", true),
        )
        val verifier = RecordingVerifier(expected)
        val useCase = DefaultVerifyRequestIdentity(verifier)

        val result = useCase.execute(RawIdentityToken("valid-token"))

        assertEquals(expected, result)
        assertEquals(1, verifier.calls)
    }

    @Test
    fun `preserves rejected result and delegates once`() {
        val verifier = RecordingVerifier(TokenVerification.Rejected)
        val useCase = DefaultVerifyRequestIdentity(verifier)

        val result = useCase.execute(RawIdentityToken("invalid-token"))

        assertSame(TokenVerification.Rejected, result)
        assertEquals(1, verifier.calls)
    }

    @Test
    fun `preserves provider unavailable result and delegates once`() {
        val verifier = RecordingVerifier(TokenVerification.ProviderUnavailable)
        val useCase = DefaultVerifyRequestIdentity(verifier)

        val result = useCase.execute(RawIdentityToken("unavailable-token"))

        assertSame(TokenVerification.ProviderUnavailable, result)
        assertEquals(1, verifier.calls)
    }

    @Test
    fun `repeating the same token produces equal provider-neutral results`() {
        val expected = TokenVerification.Verified(
            AuthenticatedPrincipal("subject-2", null, null),
        )
        val verifier = RecordingVerifier(expected)
        val useCase = DefaultVerifyRequestIdentity(verifier)
        val token = RawIdentityToken("repeated-token")

        val first = useCase.execute(token)
        val second = useCase.execute(token)

        assertEquals(first, second)
        assertEquals(expected, first)
        assertEquals(2, verifier.calls)
        assertEquals(listOf(token, token), verifier.tokens)
    }

    private class RecordingVerifier(
        private val result: TokenVerification,
    ) : IdentityTokenVerifier {
        val tokens = mutableListOf<RawIdentityToken>()
        val calls: Int
            get() = tokens.size

        override fun verify(token: RawIdentityToken): TokenVerification {
            tokens += token
            return result
        }
    }
}
