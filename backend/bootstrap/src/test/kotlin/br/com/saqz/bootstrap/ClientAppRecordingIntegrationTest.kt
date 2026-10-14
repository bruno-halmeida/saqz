package br.com.saqz.bootstrap

import br.com.saqz.access.adapter.output.jdbc.clientapp.JdbcClientAppVersionStore
import br.com.saqz.access.application.clientapp.ClientAppRecordResult
import br.com.saqz.access.application.clientapp.ClientAppVersionStore
import br.com.saqz.identity.application.RawIdentityToken
import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.sharedkernel.client.ClientApp
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A última versão do app por usuário e plataforma, ponta a ponta: a cadeia de segurança real, o banco real
 * (Flyway real) e o `RecordClientApp` da aplicação. O que o filtro lê dos cabeçalhos X-Saqz-* só vira linha
 * para quem se autenticou, e só quando algo mudou.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ClientAppRecordingIntegrationTest.Configuration::class)
@ExtendWith(OutputCaptureExtension::class)
@ActiveProfiles("test")
class ClientAppRecordingIntegrationTest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var dataSource: DataSource
    @Autowired private lateinit var json: ObjectMapper
    @Autowired private lateinit var store: FlakyStore
    private val jdbc get() = JdbcClient.create(dataSource)

    @AfterEach
    fun heal() {
        store.failing = false
    }

    @Test
    fun `the first session call of a new account already records the app`() {
        val subject = subject()

        val response = session(subject, Headers("ios", "1.0.3", "14"))

        assertEquals(200, response.statusCode(), response.body())
        val row = rows(subject).single()
        assertEquals(json.readTree(response.body())["user"]["id"].stringValue(), row.userId.toString())
        assertEquals(Row(row.userId, "IOS", "1.0.3", 14, row.firstSeenAt, row.firstSeenAt), row)
    }

    @Test
    fun `an app that sends no headers leaves no row`() {
        val subject = subject()

        assertEquals(200, session(subject).statusCode())

        assertEquals(1, count("SELECT count(*) FROM access_users WHERE firebase_subject = '$subject'"))
        assertTrue(rows(subject).isEmpty())
    }

    @Test
    fun `an unknown platform leaves no row`() {
        val subject = subject()

        assertEquals(200, session(subject, Headers("symbian", "1.0.3", "14")).statusCode())

        assertTrue(rows(subject).isEmpty())
    }

    @Test
    fun `a call without a token leaves no row even with the headers`() {
        val response = request("PUT", "/api/session", token = null, app = Headers("ios", "1.0.3", "14"))

        assertEquals(401, response.statusCode())
        assertEquals(0, count("SELECT count(*) FROM access_user_app_versions"))
    }

    @Test
    fun `repeated calls with the same app do not write again`() {
        val subject = subject()
        session(subject, Headers("android", "1.0.3", "14"))
        val first = rows(subject).single()

        repeat(5) { assertEquals(200, session(subject, Headers("android", "1.0.3", "14")).statusCode()) }

        assertEquals(first, rows(subject).single())
    }

    @Test
    fun `a new version overwrites the row and keeps the first sighting`() {
        val subject = subject()
        session(subject, Headers("android", "1.0.2", "13"))
        val first = rows(subject).single()

        session(subject, Headers("android", "1.0.3", "14"))

        val row = rows(subject).single()
        assertEquals("1.0.3", row.appVersion)
        assertEquals(14, row.appBuild)
        assertEquals(first.firstSeenAt, row.firstSeenAt)
        assertTrue(row.lastSeenAt >= first.lastSeenAt)
    }

    @Test
    fun `the same user on two platforms has two rows`() {
        val subject = subject()

        session(subject, Headers("ios", "1.0.3", "14"))
        session(subject, Headers("web", "1.0.3", null))

        assertEquals(listOf("IOS" to "1.0.3", "WEB" to "1.0.3"), rows(subject).map { it.platform to it.appVersion })
        assertNull(rows(subject).single { it.platform == "WEB" }.appBuild)
    }

    @Test
    fun `a version outside the pattern is stored as unknown, never as garbage`() {
        val subject = subject()

        assertEquals(200, session(subject, Headers("web", "web (dev)", "build-9")).statusCode())

        val row = rows(subject).single()
        assertEquals("WEB", row.platform)
        assertNull(row.appVersion)
        assertNull(row.appBuild)
    }

    @Test
    fun `a store that fails never breaks the call and is only logged by class`(output: CapturedOutput) {
        val subject = subject()
        store.failing = true

        val response = session(subject, Headers("ios", "1.0.3", "14"))

        assertEquals(200, response.statusCode(), response.body())
        assertTrue(rows(subject).isEmpty())
        val log = waitFor(output, "client_app_record_failed")
        assertTrue(log.contains("app=ios/1.0.3(14)"), log)
        assertTrue(log.contains("cause=IllegalStateException"), log)
        assertTrue(!output.out.contains("store is down on purpose"))
    }

    private class Headers(val platform: String, val version: String, val build: String?)

    private data class Row(
        val userId: UUID,
        val platform: String,
        val appVersion: String?,
        val appBuild: Int?,
        val firstSeenAt: Instant,
        val lastSeenAt: Instant,
    )

    private fun subject() = "app-version-${UUID.randomUUID()}"

    private fun session(subject: String, app: Headers? = null) = request("PUT", "/api/session", subject, app)

    private fun request(method: String, path: String, token: String?, app: Headers? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
        if (token != null) builder.header("Authorization", "Bearer $token")
        if (app != null) {
            builder.header("X-Saqz-Platform", app.platform)
            builder.header("X-Saqz-App-Version", app.version)
            app.build?.let { builder.header("X-Saqz-App-Build", it) }
        }
        return HttpClient.newHttpClient().send(
            builder.method(method, HttpRequest.BodyPublishers.noBody()).build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    }

    private fun rows(subject: String): List<Row> = jdbc.sql(
        """SELECT v.user_id, v.platform, v.app_version, v.app_build, v.first_seen_at, v.last_seen_at
           FROM access_user_app_versions v JOIN access_users u ON u.id = v.user_id
           WHERE u.firebase_subject = :subject ORDER BY v.platform""",
    ).param("subject", subject).query { result, _ ->
        Row(
            userId = result.getObject("user_id", UUID::class.java),
            platform = result.getString("platform"),
            appVersion = result.getString("app_version"),
            appBuild = result.getObject("app_build") as Int?,
            firstSeenAt = result.getTimestamp("first_seen_at").toInstant(),
            lastSeenAt = result.getTimestamp("last_seen_at").toInstant(),
        )
    }.list()

    private fun count(sql: String): Int = jdbc.sql(sql).query(Int::class.java).single()

    private fun waitFor(output: CapturedOutput, text: String): String {
        val deadline = System.currentTimeMillis() + 2_000
        while (System.currentTimeMillis() < deadline && output.out.lines().none { it.contains(text) }) Thread.sleep(25)
        return output.out.lines().first { it.contains(text) }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Configuration {
        @Bean @Primary fun clientAppTestVerifier() = object : VerifyRequestIdentity {
            override fun execute(token: RawIdentityToken) = TokenVerification.Verified(
                RequestIdentity(token.value, "${token.value}@example.test", true, "Pessoa Teste"),
            )
        }

        /** O store de verdade, com um interruptor para simular o banco indisponível só no registro da versão. */
        @Bean @Primary fun flakyClientAppStore(dataSource: DataSource) = FlakyStore(JdbcClientAppVersionStore(dataSource))
    }

    class FlakyStore(private val delegate: ClientAppVersionStore) : ClientAppVersionStore {
        @Volatile var failing = false

        override fun record(
            subject: String,
            app: ClientApp.Identified,
            now: Instant,
            refreshAfter: Duration,
        ): ClientAppRecordResult {
            if (failing) throw IllegalStateException("store is down on purpose")
            return delegate.record(subject, app, now, refreshAfter)
        }
    }

    companion object {
        // O banco vazio é migrado pelo Flyway real na subida do contexto, como em produção.
        private val database = TestPostgres.empty()

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { database.jdbcUrl }
            registry.add("spring.datasource.username") { database.username }
            registry.add("spring.datasource.password") { database.password }
            registry.add("saqz.firebase.emulator.enabled") { "true" }
            registry.add("saqz.links.domain") { "https://links.saqz.app" }
            registry.add("saqz.password-reset.secret") { "segredo-de-teste-com-trinta-e-dois" }
        }
    }
}
