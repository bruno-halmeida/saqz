package br.com.saqz.bootstrap

import br.com.saqz.access.application.admin.PlatformAdminLookup
import br.com.saqz.access.application.admin.PlatformAdminView
import br.com.saqz.adminweb.http.AdminGroupsController
import br.com.saqz.groups.application.admin.AdminGroupDetail
import br.com.saqz.groups.application.admin.AdminGroupDirectory
import br.com.saqz.groups.application.admin.AdminGroupGame
import br.com.saqz.groups.application.admin.AdminGroupPage
import br.com.saqz.groups.application.admin.AdminGroupSummary
import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
import br.com.saqz.sharedkernel.RequestIdentity
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
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AdminGroupsEndpointIntegrationTest.AdminGroupsTestConfiguration::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["saqz.firebase.emulator.enabled=true"])
class AdminGroupsEndpointIntegrationTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val client: HttpClient = HttpClient.newHttpClient()

    @Test
    fun `lista devolve pagina exata com plano do organizador`() {
        val response = get("/admin/groups?query=ceret", token = "admin-token")
        val body = objectMapper.readTree(response.body())

        assertEquals(200, response.statusCode())
        assertEquals(1, body["total"].intValue())
        assertEquals("Volei do CERET", body["items"][0]["name"].stringValue())
        assertEquals("ORGANIZADOR", body["items"][0]["ownerPlan"].stringValue())
        assertEquals(38, body["items"][0]["members"].intValue())
    }

    @Test
    fun `detalhe traz ultimos jogos e inexistente da 404`() {
        val ok = get("/admin/groups/$GROUP_ID", token = "admin-token")
        val body = objectMapper.readTree(ok.body())

        assertEquals(200, ok.statusCode())
        assertEquals(1, body["lastGames"].size())
        assertEquals(12, body["lastGames"][0]["confirmed"].intValue())
        assertEquals(404, get("/admin/groups/${UUID.randomUUID()}", token = "admin-token").statusCode())
    }

    @Test
    fun `parametros invalidos retornam 400 e comum recebe 403`() {
        assertEquals(400, get("/admin/groups?status=arquivado", token = "admin-token").statusCode())
        assertEquals(400, get("/admin/groups?page=0", token = "admin-token").statusCode())
        assertEquals(403, get("/admin/groups", token = "user-token").statusCode())
    }

    private fun get(path: String, token: String): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET()
            .header("Authorization", "Bearer $token")
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    class StubGroupDirectory : AdminGroupDirectory {
        override fun list(query: String?, status: String?, page: Int, size: Int) = AdminGroupPage(
            items = listOf(
                AdminGroupSummary(
                    groupId = GROUP_ID,
                    name = "Volei do CERET",
                    ownerUserId = OWNER_ID,
                    ownerName = "Camila Rocha",
                    ownerPlan = "ORGANIZADOR",
                    members = 38,
                    gamesPlayed = 9,
                    deleted = false,
                    createdAt = Instant.parse("2026-02-20T00:00:00Z"),
                ),
            ),
            total = 1,
            page = page,
            size = size,
        )

        override fun find(groupId: UUID): AdminGroupDetail? {
            if (groupId != GROUP_ID) return null
            return AdminGroupDetail(
                groupId = GROUP_ID,
                name = "Volei do CERET",
                timeZone = "America/Sao_Paulo",
                ownerUserId = OWNER_ID,
                ownerName = "Camila Rocha",
                ownerPlan = "ORGANIZADOR",
                members = 38,
                gamesPlayed = 9,
                deletedAt = null,
                createdAt = Instant.parse("2026-02-20T00:00:00Z"),
                lastGames = listOf(
                    AdminGroupGame(
                        gameId = UUID.randomUUID(),
                        title = "Jogo da Semana",
                        startsAt = Instant.parse("2026-07-31T22:30:00Z"),
                        status = "COMPLETED",
                        confirmed = 12,
                    ),
                ),
            )
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class AdminGroupsTestConfiguration {
        @Bean
        @Primary
        fun adminGroupsVerifier(): VerifyRequestIdentity = VerifyRequestIdentity {
            when (it.value) {
                "admin-token" -> TokenVerification.Verified(RequestIdentity("admin-subject", "admin@saqz.test", true))
                "user-token" -> TokenVerification.Verified(RequestIdentity("user-subject", "user@saqz.test", true))
                else -> TokenVerification.Rejected
            }
        }

        @Bean
        fun adminGroupsLookup(): PlatformAdminLookup = PlatformAdminLookup { subject ->
            if (subject == "admin-subject") PlatformAdminView(UUID.randomUUID(), "admin@saqz.test", "Ana Admin") else null
        }

        @Bean
        fun adminGroupsController() = AdminGroupsController(StubGroupDirectory())
    }

    private companion object {
        val GROUP_ID: UUID = UUID.fromString("9c8b7a6d-5e4f-4321-9876-abcdef012345")
        val OWNER_ID: UUID = UUID.fromString("1a2b3c4d-5e6f-4a5b-8c7d-9e0f1a2b3c4d")
    }
}
