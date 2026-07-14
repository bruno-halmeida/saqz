package br.com.saqz.bootstrap

import com.google.firebase.FirebaseApp
import org.junit.jupiter.api.Test
import org.springframework.boot.builder.SpringApplicationBuilder
import java.net.ServerSocket
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class FirebaseEnvironmentIsolationIntegrationTest {
    @Test
    fun `local profile accepts environment emulator switch without service account`() {
        start("local", "FIREBASE_AUTH_EMULATOR_HOST" to "127.0.0.1:9099").use { context ->
            val app = context.getBean(FirebaseApp::class.java)
            assertEquals("saqz-local", app.options.projectId)
        }
    }

    @Test
    fun `test profile accepts property emulator switch without service account`() {
        start("test", "saqz.firebase.emulator.enabled" to "true").use { context ->
            val app = context.getBean(FirebaseApp::class.java)
            assertEquals("saqz-local", app.options.projectId)
        }
    }

    @Test
    fun `dev profile rejects environment emulator switch before bind`() =
        assertFailsBeforeBind("dev", "FIREBASE_AUTH_EMULATOR_HOST" to "127.0.0.1:9099")

    @Test
    fun `staging profile rejects environment emulator switch before bind`() =
        assertFailsBeforeBind("staging", "FIREBASE_AUTH_EMULATOR_HOST" to "127.0.0.1:9099")

    @Test
    fun `production profile rejects environment emulator switch before bind`() =
        assertFailsBeforeBind("production", "FIREBASE_AUTH_EMULATOR_HOST" to "127.0.0.1:9099")

    @Test
    fun `dev profile rejects property emulator switch before bind`() =
        assertFailsBeforeBind("dev", "saqz.firebase.emulator.enabled" to "true")

    @Test
    fun `staging profile rejects property emulator switch before bind`() =
        assertFailsBeforeBind("staging", "saqz.firebase.emulator.enabled" to "true")

    @Test
    fun `production profile rejects property emulator switch before bind`() =
        assertFailsBeforeBind("production", "saqz.firebase.emulator.enabled" to "true")

    @Test
    fun `non-local startup without emulator or credentials fails closed before bind`() =
        assertFailsBeforeBind("dev")

    private fun assertFailsBeforeBind(profile: String, vararg properties: Pair<String, String>) {
        val port = ServerSocket(0).use { it.localPort }

        val failure = assertFails { start(profile, "server.port" to port.toString(), *properties) }

        assertTrue(generateSequence<Throwable>(failure) { it.cause }.any {
            it.message?.contains("Firebase", ignoreCase = true) == true
        })
        ServerSocket(port).use { assertTrue(it.isBound) }
    }

    private fun start(profile: String, vararg properties: Pair<String, String>) =
        SpringApplicationBuilder(SaqzApplication::class.java)
            .profiles(profile)
            .properties(
                mapOf(
                    "server.port" to "0",
                    "spring.main.banner-mode" to "off",
                    "logging.level.root" to "ERROR",
                ) + properties.toMap(),
            )
            .run()
}
