package br.com.saqz.identity.adapter.output.firebase

import br.com.saqz.identity.application.RawIdentityToken
import br.com.saqz.identity.application.TokenVerification
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Date
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Tag("emulator")
class FirebaseAdminTokenVerifierEmulatorTest {
    @Test
    fun `verifies a real emulator token without starting backend`() {
        checkPortAvailable(9099)
        val process = ProcessBuilder(
            "npx", "--yes", "firebase-tools@15.23.0", "emulators:start",
            "--only", "auth", "--project", "saqz-local", "--config", System.getProperty("firebase.config"),
        ).redirectErrorStream(true).start()
        val output = StringBuilder()
        val outputThread = Thread { process.inputStream.bufferedReader().forEachLine { output.appendLine(it) } }.apply { start() }

        try {
            awaitEmulator(process, output)
            val email = "adapter-${UUID.randomUUID()}@example.test"
            val token = createUser(email)
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
                assertFalse(result.principal.emailVerified ?: true)
                assertTrue(result.principal.subject.isNotBlank())
            } finally {
                app.delete()
            }
        } finally {
            process.destroy()
            if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly()
            outputThread.join(5_000)
            checkPortAvailable(9099)
        }
    }

    private fun createUser(email: String): String {
        val body = """{"email":"$email","password":"local-password-123","returnSecureToken":true}"""
        val request = HttpRequest.newBuilder(
            URI("http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-saqz-local-api-key"),
        ).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build()
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(HttpURLConnection.HTTP_OK, response.statusCode(), response.body())
        return Regex("\"idToken\":\"([^\"]+)\"").find(response.body())!!.groupValues[1]
    }

    private fun awaitEmulator(process: Process, output: StringBuilder) {
        val deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos()
        while (System.nanoTime() < deadline) {
            if (!process.isAlive) error("Firebase emulator exited early:\n$output")
            runCatching { Socket("127.0.0.1", 9099).use { return } }
            Thread.sleep(200)
        }
        error("Firebase emulator did not start:\n$output")
    }

    private fun checkPortAvailable(port: Int) {
        ServerSocket(port).use { }
    }
}
