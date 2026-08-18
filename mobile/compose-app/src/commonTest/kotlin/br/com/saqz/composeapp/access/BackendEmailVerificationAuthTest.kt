package br.com.saqz.composeapp.access

import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthResult
import br.com.saqz.access.domain.port.AuthStateListener
import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeFailureCode
import br.com.saqz.access.domain.port.NativeUser
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.port.TokenCallback
import br.com.saqz.access.domain.port.TokenResult
import br.com.saqz.access.domain.verification.EmailVerificationError
import br.com.saqz.access.domain.verification.EmailVerificationGateway
import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class BackendEmailVerificationAuthTest {
    @Test
    fun `reenvio pede o backend e nao o firebase`() = runTest {
        val firebase = RecordingAuth()
        val gateway = RecordingGateway(SaqzResult.Success(Unit))
        val auth = BackendEmailVerificationAuth(firebase, { gateway }, this)
        var result: OperationResult? = null

        auth.sendVerification(resultCallback { result = it })
        testScheduler.advanceUntilIdle()

        assertSame(OperationResult.Success, result)
        assertEquals(1, gateway.calls)
        assertEquals(0, firebase.verificationCalls)
    }

    @Test
    fun `cadastro nao confirmado dispara o e-mail pelo backend`() = runTest {
        val firebase = RecordingAuth(createResult = AuthResult.Success(unverified()))
        val gateway = RecordingGateway(SaqzResult.Success(Unit))
        val auth = BackendEmailVerificationAuth(firebase, { gateway }, this)

        auth.createAccount("Ana", "ana@example.test", "strong-pass", authCallback { })
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(Triple("Ana", "ana@example.test", "strong-pass")), firebase.creates)
        assertEquals(1, gateway.calls)
        assertEquals(0, firebase.verificationCalls)
    }

    @Test
    fun `cadastro ja confirmado nao manda e-mail`() = runTest {
        val firebase = RecordingAuth(createResult = AuthResult.Success(verified()))
        val gateway = RecordingGateway(SaqzResult.Success(Unit))
        val auth = BackendEmailVerificationAuth(firebase, { gateway }, this)

        auth.createAccount("Ana", "ana@example.test", "strong-pass", authCallback { })
        testScheduler.advanceUntilIdle()

        assertEquals(0, gateway.calls)
    }

    @Test
    fun `cadastro que falha nao manda e-mail`() = runTest {
        val firebase = RecordingAuth(createResult = AuthResult.Failure(NativeFailureCode.EMAIL_IN_USE))
        val gateway = RecordingGateway(SaqzResult.Success(Unit))
        val auth = BackendEmailVerificationAuth(firebase, { gateway }, this)

        auth.createAccount("Ana", "ana@example.test", "strong-pass", authCallback { })
        testScheduler.advanceUntilIdle()

        assertEquals(0, gateway.calls)
    }

    @Test
    fun `teto de reenvio chega como too many requests`() = runTest {
        val gateway = RecordingGateway(SaqzResult.Failure(EmailVerificationError.RateLimited(42)))
        val auth = BackendEmailVerificationAuth(RecordingAuth(), { gateway }, this)
        var result: OperationResult? = null

        auth.sendVerification(resultCallback { result = it })
        testScheduler.advanceUntilIdle()

        assertEquals(OperationResult.Failure(NativeFailureCode.TOO_MANY_REQUESTS), result)
    }

    @Test
    fun `rede fora do ar chega como network unavailable`() = runTest {
        val gateway = RecordingGateway(
            SaqzResult.Failure(EmailVerificationError.DataFailure(DataError.Connectivity)),
        )
        val auth = BackendEmailVerificationAuth(RecordingAuth(), { gateway }, this)
        var result: OperationResult? = null

        auth.sendVerification(resultCallback { result = it })
        testScheduler.advanceUntilIdle()

        assertEquals(OperationResult.Failure(NativeFailureCode.NETWORK_UNAVAILABLE), result)
    }

    @Test
    fun `o gateway so e resolvido na hora do envio`() = runTest {
        var resolutions = 0
        val auth = BackendEmailVerificationAuth(
            RecordingAuth(),
            {
                resolutions += 1
                RecordingGateway(SaqzResult.Success(Unit))
            },
            this,
        )

        assertEquals(0, resolutions)
        auth.sendVerification(resultCallback { })
        testScheduler.advanceUntilIdle()
        assertEquals(1, resolutions)
    }

    private fun unverified() = NativeUser("subject", "ana@example.test", emailVerified = false, "Ana")

    private fun verified() = NativeUser("subject", "ana@example.test", emailVerified = true, "Ana")

    private fun resultCallback(block: (OperationResult) -> Unit) = object : ResultCallback {
        override fun complete(result: OperationResult) = block(result)
    }

    private fun authCallback(block: (AuthResult) -> Unit) = object : AuthCallback {
        override fun complete(result: AuthResult) = block(result)
    }

    private class RecordingGateway(
        private val result: SaqzResult<Unit, EmailVerificationError>,
    ) : EmailVerificationGateway {
        var calls = 0
        override suspend fun request(): SaqzResult<Unit, EmailVerificationError> {
            calls += 1
            return result
        }
    }

    private class RecordingAuth(
        private val createResult: AuthResult = AuthResult.Failure(NativeFailureCode.UNKNOWN),
    ) : NativeAuthPort {
        val creates = mutableListOf<Triple<String, String, String>>()
        var verificationCalls = 0

        override fun observe(listener: AuthStateListener): Cancelable = object : Cancelable {
            override fun cancel() = Unit
        }

        override fun createAccount(name: String, email: String, password: String, done: AuthCallback) {
            creates += Triple(name, email, password)
            done.complete(createResult)
        }

        override fun signInWithPassword(email: String, password: String, done: AuthCallback) = Unit

        override fun signInWithGoogle(done: AuthCallback) = Unit

        override fun sendVerification(done: ResultCallback) {
            verificationCalls += 1
            done.complete(OperationResult.Success)
        }

        override fun reloadUser(done: AuthCallback) = Unit

        override fun updateDisplayName(name: String, done: AuthCallback) = Unit

        override fun idToken(forceRefresh: Boolean, done: TokenCallback) =
            done.complete(TokenResult.Failure(NativeFailureCode.UNKNOWN))

        override fun signOut(done: ResultCallback) = done.complete(OperationResult.Success)
    }
}
