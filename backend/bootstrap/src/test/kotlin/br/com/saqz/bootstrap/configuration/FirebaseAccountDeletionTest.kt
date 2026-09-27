package br.com.saqz.bootstrap.configuration

import com.google.firebase.auth.AuthErrorCode
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class FirebaseAccountDeletionTest {
    @Test fun `SDK revokes sessions then deletes the exact provider identity`() {
        val auth = mock(FirebaseAuth::class.java)
        deleteFirebaseIdentity(auth, "subject-to-delete")
        inOrder(auth).apply {
            verify(auth).revokeRefreshTokens("subject-to-delete")
            verify(auth).deleteUser("subject-to-delete")
            verifyNoMoreInteractions()
        }
    }

    @Test fun `already missing identity is an idempotent success at either SDK step`() {
        for (failAtDelete in listOf(false, true)) {
            val auth = mock(FirebaseAuth::class.java)
            val failure = mock(FirebaseAuthException::class.java)
            doReturn(AuthErrorCode.USER_NOT_FOUND).`when`(failure).authErrorCode
            if (failAtDelete) doThrow(failure).`when`(auth).deleteUser("missing")
            else doThrow(failure).`when`(auth).revokeRefreshTokens("missing")
            deleteFirebaseIdentity(auth, "missing")
            verify(auth).revokeRefreshTokens("missing")
            verify(auth, times(if (failAtDelete) 1 else 0)).deleteUser("missing")
        }
    }

    @Test fun `other SDK failures propagate for durable retry`() {
        for (failAtDelete in listOf(false, true)) {
            val auth = mock(FirebaseAuth::class.java)
            val failure = mock(FirebaseAuthException::class.java)
            doReturn(AuthErrorCode.USER_DISABLED).`when`(failure).authErrorCode
            if (failAtDelete) doThrow(failure).`when`(auth).deleteUser("retry")
            else doThrow(failure).`when`(auth).revokeRefreshTokens("retry")
            assertSame(failure, assertFailsWith<FirebaseAuthException> { deleteFirebaseIdentity(auth, "retry") })
            verify(auth, times(if (failAtDelete) 1 else 0)).deleteUser("retry")
        }
    }
}
