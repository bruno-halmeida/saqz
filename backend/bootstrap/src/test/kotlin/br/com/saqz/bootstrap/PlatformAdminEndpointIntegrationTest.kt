package br.com.saqz.bootstrap

import br.com.saqz.access.adapter.input.http.PlatformAdminMeController
import br.com.saqz.access.application.admin.PlatformAdminLookup
import br.com.saqz.access.application.admin.PlatformAdminView
import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
import br.com.saqz.sharedkernel.RequestIdentity
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PlatformAdminEndpointIntegrationTest.PlatformAdminTestConfiguration::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["saqz.firebase.emulator.enabled=true"])
class PlatformAdminEndpointIntegrationTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var lookup: RecordingPlatformAdminLookup

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val client: HttpClient = HttpClient.newHttpClient()

    @BeforeEach
    fun reset() {
        lookup.subjects.clear()
    }

    @Test
    fun `sem credencial retorna 401 sem consultar a lista de admins`() {
        val response = get("/admin/me", token = null)

        assertEquals(401, response.statusCode())
        assertEquals("AUTHENTICATION_REQUIRED", code(response))
        assertTrue(lookup.subjects.isEmpty())
    }

    @Test
    fun `token invalido retorna 401 sem consultar a lista de admins`() {
        val response = get("/admin/me", token = "invalid-token")

        assertEquals(401, response.statusCode())
        assertTrue(lookup.subjects.isEmpty())
    }

    @Test
    fun `usuario comum recebe 403 com problema exato`() {
        val response = get("/admin/me", token = "user-token")

        assertEquals(403, response.statusCode())
        assertEquals("ACCESS_FORBIDDEN", code(response))
        assertEquals(listOf("user-subject"), lookup.subjects)
    }

    @Test
    fun `usuario comum recebe 403 em qualquer rota sob admin`() {
        val response = get("/admin/ping", token = "user-token")

        assertEquals(403, response.statusCode())
    }

    @Test
    fun `admin recebe os campos exatos em admin me`() {
        val response = get("/admin/me", token = "admin-token")
        val body = objectMapper.readTree(response.body())

        assertEquals(200, response.statusCode())
        assertEquals(ADMIN_USER_ID.toString(), body["userId"].stringValue())
        assertEquals("admin@saqz.test", body["email"].stringValue())
        assertEquals("Ana Admin", body["displayName"].stringValue())
    }

    @Test
    fun `admin passa pela guarda em outra rota sob admin`() {
        val response = get("/admin/ping", token = "admin-token")

        assertEquals(200, response.statusCode())
        assertEquals(listOf("admin-subject"), lookup.subjects)
    }

    @Test
    fun `rota fora de admin nao consulta a lista de admins`() {
        get("/api/nao-existe", token = "user-token")

        assertTrue(lookup.subjects.isEmpty())
    }

    private fun get(path: String, token: String?): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET()
        if (token != null) builder.header("Authorization", "Bearer $token")
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun code(response: HttpResponse<String>): String =
        objectMapper.readTree(response.body())["code"].stringValue()

    class RecordingPlatformAdminLookup : PlatformAdminLookup {
        val subjects = mutableListOf<String>()

        override fun findBySubject(subject: String): PlatformAdminView? {
            subjects += subject
            return if (subject == "admin-subject") {
                PlatformAdminView(ADMIN_USER_ID, "admin@saqz.test", "Ana Admin")
            } else {
                null
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class PlatformAdminTestConfiguration {
        @Bean
        @Primary
        fun platformAdminVerifier(): VerifyRequestIdentity = VerifyRequestIdentity {
            when (it.value) {
                "admin-token" -> TokenVerification.Verified(
                    RequestIdentity("admin-subject", "admin@saqz.test", true),
                )
                "user-token" -> TokenVerification.Verified(
                    RequestIdentity("user-subject", "user@saqz.test", true),
                )
                else -> TokenVerification.Rejected
            }
        }

        @Bean
        fun recordingPlatformAdminLookup() = RecordingPlatformAdminLookup()

        @Bean
        fun platformAdminMeController(lookup: RecordingPlatformAdminLookup) =
            PlatformAdminMeController(lookup)

        @Bean
        fun adminPingController() = AdminPingController()
    }

    @RestController
    class AdminPingController {
        @GetMapping("/admin/ping")
        fun ping(): Map<String, Boolean> = mapOf("pong" to true)
    }

    private companion object {
        val ADMIN_USER_ID: UUID = UUID.fromString("7f9a1c8e-2d34-4b56-9a78-123456789abc")
    }
}
