package br.com.saqz.androidapp

import org.junit.Assert.assertEquals
import org.junit.Test

class FirebaseAuthBootstrapTest {
    @Test
    fun configuresTheAndroidAuthEmulatorBeforeReturningAuth() {
        val calls = mutableListOf<String>()
        val auth = FirebaseAuthClient { host, port -> calls += "emulator:$host:$port" }

        val initialized = FirebaseAuthBootstrap(
            factory = {
                calls += "create"
                auth
            },
            configuration = AndroidFirebaseConfiguration(
                projectId = LocalFirebaseConfiguration.projectId,
                apiKey = LocalFirebaseConfiguration.apiKey,
                messagingSenderId = LocalFirebaseConfiguration.messagingSenderId,
                applicationId = LocalFirebaseConfiguration.applicationId,
                useAuthEmulator = true,
            ),
        ).initialize()

        assertEquals(listOf("create", "emulator:10.0.2.2:9099"), calls)
        assertEquals(auth, initialized)
        assertEquals("saqz-local", LocalFirebaseConfiguration.projectId)
        assertEquals("fake-saqz-local-api-key", LocalFirebaseConfiguration.apiKey)
        assertEquals("123456789000", LocalFirebaseConfiguration.messagingSenderId)
        assertEquals("1:123456789000:android:saqzlocal", LocalFirebaseConfiguration.applicationId)
    }

    @Test
    fun releaseFirebaseConfigurationDoesNotUseAuthEmulator() {
        val calls = mutableListOf<String>()
        val auth = FirebaseAuthClient { host, port -> calls += "emulator:$host:$port" }

        val initialized = FirebaseAuthBootstrap(
            factory = {
                calls += "create"
                auth
            },
            configuration = AndroidFirebaseConfiguration(
                projectId = "saquz-app",
                apiKey = "prod-api-key",
                messagingSenderId = "641280788616",
                applicationId = "1:641280788616:android:b65bab26ed1ed3674bb73d",
                useAuthEmulator = false,
            ),
        ).initialize()

        assertEquals(listOf("create"), calls)
        assertEquals(auth, initialized)
    }

    @Test
    fun firebaseInitializesOnce() {
        // A single bootstrap creates the auth client exactly once — never once per
        // recomposition. The named-app process dedup lives in AndroidFirebaseBootstrap
        // and is exercised by the instrumented suite launching MainActivity repeatedly.
        var creates = 0
        val auth = FirebaseAuthClient { _, _ -> }

        FirebaseAuthBootstrap(
            factory = {
                creates++
                auth
            },
            configuration = AndroidFirebaseConfiguration(
                projectId = LocalFirebaseConfiguration.projectId,
                apiKey = LocalFirebaseConfiguration.apiKey,
                messagingSenderId = LocalFirebaseConfiguration.messagingSenderId,
                applicationId = LocalFirebaseConfiguration.applicationId,
                useAuthEmulator = true,
            ),
        ).initialize()

        assertEquals(1, creates)
    }
}
