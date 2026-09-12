package br.com.saqz.bootstrap.configuration

import br.com.saqz.access.application.session.AppOnboardingOwner
import br.com.saqz.access.application.session.AppOnboardingIdentityUnavailable
import br.com.saqz.access.domain.AccessName
import com.google.firebase.auth.AuthErrorCode
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.UserRecord
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FirebaseAppOnboardingSessionsTest {
    private val owner = AppOnboardingOwner(UUID.randomUUID(), "provider-subject", AccessName.from("Owner"))

    @Test
    fun `looks up existing enabled provider identity before minting custom token`() {
        var mintedSubject: String? = null
        val sessions = FirebaseAppOnboardingSessions(
            findUser = { subject ->
                assertEquals("provider-subject", subject)
                false
            },
            mint = { subject -> mintedSubject = subject; "custom-token" },
        )

        assertEquals("custom-token", sessions.customTokenFor(owner))
        assertEquals("provider-subject", mintedSubject)
    }

    @Test
    fun `missing and disabled provider identities do not mint`() {
        listOf<Boolean?>(null, true).forEach { disabled ->
            var minted = false
            val sessions = FirebaseAppOnboardingSessions({ disabled }, { minted = true; "forbidden" })

            assertEquals(null, sessions.customTokenFor(owner))
            assertEquals(false, minted)
        }
    }

    @Test
    fun `provider mint failure is unavailable and is never exposed`() {
        val sessions = FirebaseAppOnboardingSessions({ false }) { error("private provider failure") }

        val failure = assertFailsWith<AppOnboardingIdentityUnavailable> { sessions.customTokenFor(owner) }

        assertEquals("AppOnboardingIdentityUnavailable", failure::class.simpleName)
        assertEquals(null, failure.message)
    }

    @Test
    fun `sdk binding looks up provider uid and mints for enabled identity`() {
        val auth = mock(FirebaseAuth::class.java)
        val user = mock(UserRecord::class.java)
        doReturn(user).`when`(auth).getUser("provider-subject")
        doReturn(false).`when`(user).isDisabled
        doReturn("custom-token").`when`(auth).createCustomToken("provider-subject")

        val sessions = FirebaseAppOnboardingSessions(auth)

        assertEquals("custom-token", sessions.customTokenFor(owner))
        verify(auth).getUser("provider-subject")
        verify(auth).createCustomToken("provider-subject")
    }

    @Test
    fun `sdk binding denies missing and disabled provider identities without minting`() {
        val missingAuth = mock(FirebaseAuth::class.java)
        val missingFailure = mock(FirebaseAuthException::class.java)
        doReturn(AuthErrorCode.USER_NOT_FOUND).`when`(missingFailure).authErrorCode
        doThrow(missingFailure).`when`(missingAuth).getUser("provider-subject")
        val missing = FirebaseAppOnboardingSessions(missingAuth)
        assertEquals(null, missing.customTokenFor(owner))
        verify(missingAuth, org.mockito.Mockito.never()).createCustomToken("provider-subject")

        val disabledAuth = mock(FirebaseAuth::class.java)
        val disabledFailure = mock(FirebaseAuthException::class.java)
        doReturn(AuthErrorCode.USER_DISABLED).`when`(disabledFailure).authErrorCode
        doThrow(disabledFailure).`when`(disabledAuth).getUser("provider-subject")
        val disabled = FirebaseAppOnboardingSessions(disabledAuth)
        assertEquals(null, disabled.customTokenFor(owner))
        verify(disabledAuth, org.mockito.Mockito.never()).createCustomToken("provider-subject")

        val flaggedAuth = mock(FirebaseAuth::class.java)
        val flaggedUser = mock(UserRecord::class.java)
        doReturn(flaggedUser).`when`(flaggedAuth).getUser("provider-subject")
        doReturn(true).`when`(flaggedUser).isDisabled
        val flagged = FirebaseAppOnboardingSessions(flaggedAuth)
        assertEquals(null, flagged.customTokenFor(owner))
        verify(flaggedAuth, org.mockito.Mockito.never()).createCustomToken("provider-subject")
    }
}
