package br.com.saqz.bootstrap

import br.com.saqz.access.adapter.input.http.AccessSessionController
import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.application.session.CompleteSessionProfile
import br.com.saqz.access.application.session.SessionRepository
import br.com.saqz.access.application.session.SessionUpsert
import br.com.saqz.access.application.session.SessionView
import br.com.saqz.access.application.session.UserAccount
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Tag("emulator")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(EmulatorSessionIntegrationTest.EmulatorSessionTestConfiguration::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["saqz.firebase.emulator.enabled=true"])
class EmulatorSessionIntegrationTest {
    @LocalServerPort
    private var port: Int = 0

    private lateinit var state: Path
    private lateinit var process: Process

    @BeforeAll
    fun startFixture() {
        state = Files.createTempDirectory("saqz-session-fixture-")
        process = ProcessBuilder(System.getProperty("session.fixture"))
            .redirectErrorStream(true)
            .redirectOutput(state.resolve("fixture.log").toFile())
            .apply {
                environment()["SAQZ_FIXTURE_STATE_DIR"] = state.toString()
                environment()["SAQZ_FIXTURE_HOLD"] = "true"
            }
            .start()
        awaitReady()
    }

    @AfterAll
    fun stopFixture() {
        process.destroy()
        assertTrue(process.waitFor(30, TimeUnit.SECONDS), fixtureLog())
        assertTrue(Files.exists(state.resolve("account-deleted")), fixtureLog())
        assertTrue(Files.exists(state.resolve("port-bindable")), fixtureLog())
        assertFalse(Files.exists(state.resolve("id-token")))
        state.resolve("pids").readLines().forEach { pid ->
            assertFalse(ProcessHandle.of(pid.toLong()).map(ProcessHandle::isAlive).orElse(false), pid)
        }
        state.toFile().deleteRecursively()
    }

    @Test
    fun `real verified emulator token returns internal user mirrors without Firebase UID`() {
        val response = session()

        assertEquals(200, response.statusCode(), response.body())
        assertTrue(response.body().contains("\"email\":\"${state.resolve("email").readText().trim()}\""))
        assertTrue(response.body().contains("\"displayName\":\"Session Fixture\""))
        assertTrue(response.body().contains("\"memberships\":[]"))
        assertFalse(response.body().contains(state.resolve("subject").readText().trim()))
    }

    @Test
    fun `reusing the same emulator token returns equal session fields`() {
        val first = session()
        val second = session()

        assertEquals(200, first.statusCode(), first.body())
        assertEquals(200, second.statusCode(), second.body())
        assertEquals(first.body(), second.body())
    }

    private fun session(): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI("http://127.0.0.1:$port/api/session"))
            .header("Authorization", "Bearer ${state.resolve("id-token").readText().trim()}")
            .PUT(HttpRequest.BodyPublishers.noBody())
            .build()
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun awaitReady() {
        val deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos()
        while (System.nanoTime() < deadline) {
            if (Files.exists(state.resolve("ready"))) return
            if (!process.isAlive) error(fixtureLog())
            Thread.sleep(200)
        }
        error("Fixture did not become ready:\n${fixtureLog()}")
    }

    private fun fixtureLog(): String =
        state.resolve("fixture.log").takeIf(Files::exists)?.readText().orEmpty()

    @TestConfiguration(proxyBeanMethods = false)
    class EmulatorSessionTestConfiguration {
        @Bean
        fun emulatorSessionRepository() = object : SessionRepository {
            private val ids = mutableMapOf<String, UUID>()

            override fun upsertAndLoad(command: SessionUpsert) = SessionView(
                UserAccount(
                    ids.getOrPut(command.subject) { UUID.randomUUID() },
                    command.subject,
                    command.email,
                    command.displayName,
                ),
                emptyList(),
            )
        }

        @Bean
        fun emulatorBootstrapSession(repository: SessionRepository) = BootstrapSession(repository)

        @Bean
        fun emulatorCompleteSessionProfile(repository: SessionRepository) = CompleteSessionProfile(repository)

        @Bean
        fun emulatorAccessSessionController(useCase: BootstrapSession, profile: CompleteSessionProfile) =
            AccessSessionController(useCase, profile)
    }
}
