package br.com.saqz.androidapp

import org.junit.Assert.assertEquals
import org.junit.Test

class FirebaseAuthBootstrapTest {
    @Test
    fun configuresTheAndroidAuthEmulatorBeforeReturningAuth() {
        val calls = mutableListOf<String>()
        val auth = FirebaseAuthClient { host, port -> calls += "emulator:$host:$port" }

        val initialized = FirebaseAuthBootstrap {
            calls += "create"
            auth
        }.initialize()

        assertEquals(listOf("create", "emulator:10.0.2.2:9099"), calls)
        assertEquals(auth, initialized)
        assertEquals("saqz-local", LocalFirebaseConfiguration.projectId)
        assertEquals("fake-saqz-local-api-key", LocalFirebaseConfiguration.apiKey)
        assertEquals("123456789000", LocalFirebaseConfiguration.messagingSenderId)
        assertEquals("1:123456789000:android:saqzlocal", LocalFirebaseConfiguration.applicationId)
    }
}
