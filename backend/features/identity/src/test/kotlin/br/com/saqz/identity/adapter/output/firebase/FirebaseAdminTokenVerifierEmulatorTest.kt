package br.com.saqz.identity.adapter.output.firebase

import br.com.saqz.identity.application.RawIdentityToken
import br.com.saqz.identity.application.TokenVerification
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.Date
import java.util.UUID
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Tag("emulator")
class FirebaseAdminTokenVerifierEmulatorTest {
    @Test
    fun `verifies a real emulator token without starting backend`() {
        val state = Files.createTempDirectory("saqz-identity-fixture-")
        val process = ProcessBuilder(System.getProperty("session.fixture"))
            .redirectErrorStream(true)
            .redirectOutput(state.resolve("fixture.log").toFile())
            .apply {
                environment()["SAQZ_FIXTURE_STATE_DIR"] = state.toString()
                environment()["SAQZ_FIXTURE_HOLD"] = "true"
            }
            .start()

        try {
            awaitReady(process, state)
            val email = state.resolve("email").readText().trim()
            val token = state.resolve("id-token").readText().trim()
            val app = FirebaseApp.initializeApp(
                FirebaseOptions.builder()
                    .setProjectId("saqz-local")
                    .setCredentials(GoogleCredentials.create(AccessToken("owner", Date(Long.MAX_VALUE))))
                    .build(),
                "adapter-${UUID.randomUUID()}",
            )
            try {
                val result = FirebaseAdminTokenVerifier(firebaseTokenDecoder(app)).verify(RawIdentityToken(token))
                assertTrue(result is TokenVerification.Verified)
                assertEquals(email, result.principal.email)
                assertTrue(result.principal.emailVerified == true)
                assertTrue(result.principal.subject.isNotBlank())
            } finally {
                app.delete()
            }
        } finally {
            stopFixture(process, state)
        }
    }

    private fun awaitReady(process: Process, state: Path) {
        val deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos()
        while (System.nanoTime() < deadline) {
            if (Files.exists(state.resolve("ready"))) return
            if (!process.isAlive) error(fixtureLog(state))
            Thread.sleep(200)
        }
        error("Firebase fixture did not become ready:\n${fixtureLog(state)}")
    }

    private fun stopFixture(process: Process, state: Path) {
        process.destroy()
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), fixtureLog(state))
            assertTrue(Files.exists(state.resolve("account-deleted")), fixtureLog(state))
            assertTrue(Files.exists(state.resolve("port-bindable")), fixtureLog(state))
            assertFalse(Files.exists(state.resolve("id-token")))
            state.resolve("pids").readLines().forEach { pid ->
                assertFalse(ProcessHandle.of(pid.toLong()).map(ProcessHandle::isAlive).orElse(false), pid)
            }
        } finally {
            state.toFile().deleteRecursively()
        }
    }

    private fun fixtureLog(state: Path): String =
        state.resolve("fixture.log").takeIf(Files::exists)?.readText().orEmpty()
}
