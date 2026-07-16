package br.com.saqz.sharedkernel

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RequestIdentityTest {
    @Test
    fun `exposes all provider neutral identity attributes`() {
        val identity = RequestIdentity(
            subject = "firebase-uid",
            email = "person@example.test",
            emailVerified = true,
            displayName = "Person Name",
        )

        assertEquals("firebase-uid", identity.subject)
        assertEquals("person@example.test", identity.email)
        assertEquals(true, identity.emailVerified)
        assertEquals("Person Name", identity.displayName)
    }

    @Test
    fun `allows every optional identity attribute to be absent`() {
        val identity = RequestIdentity(subject = "firebase-uid")

        assertNull(identity.email)
        assertNull(identity.emailVerified)
        assertNull(identity.displayName)
    }
}
