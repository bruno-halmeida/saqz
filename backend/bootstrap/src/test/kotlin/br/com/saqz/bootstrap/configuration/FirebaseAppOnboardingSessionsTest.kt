package br.com.saqz.bootstrap.configuration

import br.com.saqz.access.application.session.AppOnboardingOwner
import br.com.saqz.access.application.session.AppOnboardingIdentityUnavailable
import br.com.saqz.access.domain.AccessName
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
}
