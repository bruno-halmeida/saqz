package br.com.saqz.bootstrap

import br.com.saqz.access.adapter.input.http.AccessSessionController
import br.com.saqz.access.application.admin.AdminUserDetail
import br.com.saqz.access.application.admin.AdminUserDirectory
import br.com.saqz.access.application.admin.AdminUserPage
import br.com.saqz.access.application.admin.AdminUserSummary
import br.com.saqz.access.application.admin.PlatformAdminLookup
import br.com.saqz.access.application.admin.PlatformAdminView
import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.application.session.CompleteSessionProfile
import br.com.saqz.access.application.session.ProfileCompletion
import br.com.saqz.access.application.session.SessionMembership
import br.com.saqz.access.application.session.SessionRepository
import br.com.saqz.access.application.session.SessionUpsert
import br.com.saqz.access.application.session.SessionView
import br.com.saqz.access.application.session.UserAccount
import br.com.saqz.access.domain.AccessName
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
@Import(AdminUsersEndpointIntegrationTest.AdminUsersTestConfiguration::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["saqz.firebase.emulator.enabled=true"])
class AdminUsersEndpointIntegrationTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val client: HttpClient = HttpClient.newHttpClient()

    @Test
    fun `lista devolve pagina exata do diretorio`() {
        val response = exchange("GET", "/admin/users?query=bi&plan=ORGANIZADOR&status=active", "admin-token")
        val body = objectMapper.readTree(response.body())

        assertEquals(200, response.statusCode())
        assertEquals(1, body["total"].intValue())
        assertEquals(USER_ID.toString(), body["items"][0]["userId"].stringValue())
        assertEquals("Bianca Souza", body["items"][0]["displayName"].stringValue())
        assertEquals("ORGANIZADOR", body["items"][0]["plan"].stringValue())
    }

    @Test
    fun `parametros invalidos retornam 400`() {
        assertEquals(400, exchange("GET", "/admin/users?page=0", "admin-token").statusCode())
        assertEquals(400, exchange("GET", "/admin/users?size=101", "admin-token").statusCode())
        assertEquals(400, exchange("GET", "/admin/users?plan=QUADRA_CHEIA", "admin-token").statusCode())
        assertEquals(400, exchange("GET", "/admin/users?status=banido", "admin-token").statusCode())
    }

    @Test
    fun `detalhe inexistente retorna 404 e suspensao responde 204 ou 404`() {
        assertEquals(404, exchange("GET", "/admin/users/${UUID.randomUUID()}", "admin-token").statusCode())
        assertEquals(204, exchange("POST", "/admin/users/$USER_ID/suspend", "admin-token").statusCode())
        assertEquals(204, exchange("POST", "/admin/users/$USER_ID/reactivate", "admin-token").statusCode())
        assertEquals(404, exchange("POST", "/admin/users/${UUID.randomUUID()}/suspend", "admin-token").statusCode())
    }

    @Test
    fun `usuario comum nao passa da guarda`() {
        assertEquals(403, exchange("GET", "/admin/users", "user-token").statusCode())
    }

    @Test
    fun `sessao de conta suspensa responde 403 com codigo proprio`() {
        val response = exchange("PUT", "/api/session", "suspended-token")

        assertEquals(403, response.statusCode())
        assertEquals("ACCOUNT_SUSPENDED", objectMapper.readTree(response.body())["code"].stringValue())
    }

    private fun exchange(method: String, path: String, token: String): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI.create("http://localhost:$port$path"))
            .method(method, HttpRequest.BodyPublishers.noBody())
            .header("Authorization", "Bearer $token")
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    class StubDirectory : AdminUserDirectory {
        override fun list(query: String?, plan: String?, status: String?, page: Int, size: Int) = AdminUserPage(
            items = listOf(
                AdminUserSummary(
                    userId = USER_ID,
                    displayName = "Bianca Souza",
                    email = "bianca@saqz.test",
                    city = "São Paulo",
                    plan = "ORGANIZADOR",
                    suspended = false,
                    memberships = 1,
                    ownedGroups = 1,
                    createdAt = Instant.parse("2026-05-12T00:00:00Z"),
                    lastSeenAt = Instant.parse("2026-08-03T10:00:00Z"),
                ),
            ),
            total = 1,
            page = page,
            size = size,
        )

        override fun find(userId: UUID): AdminUserDetail? = null

        override fun suspend(userId: UUID) = userId == USER_ID

        override fun reactivate(userId: UUID) = userId == USER_ID
    }

    class SuspendedAwareSessionRepository : SessionRepository {
        override fun upsertAndLoad(command: SessionUpsert): SessionView = SessionView(
            UserAccount(USER_ID, command.subject, command.email, command.displayName),
            emptyList<SessionMembership>(),
        )

        override fun updateProfile(command: ProfileCompletion): SessionView? = null

        override fun suspendedAt(subject: String): Instant? =
            if (subject == "suspended-subject") Instant.parse("2026-08-01T00:00:00Z") else null
    }

    @TestConfiguration(proxyBeanMethods = false)
    class AdminUsersTestConfiguration {
        @Bean
        @Primary
        fun adminUsersVerifier(): VerifyRequestIdentity = VerifyRequestIdentity {
            when (it.value) {
                "admin-token" -> TokenVerification.Verified(
                    RequestIdentity("admin-subject", "admin@saqz.test", true, "Ana Admin"),
                )
                "user-token" -> TokenVerification.Verified(
                    RequestIdentity("user-subject", "user@saqz.test", true, "Uso Comum"),
                )
                "suspended-token" -> TokenVerification.Verified(
                    RequestIdentity("suspended-subject", "sus@saqz.test", true, "Pessoa Suspensa"),
                )
                else -> TokenVerification.Rejected
            }
        }

        @Bean
        fun adminUsersLookup(): PlatformAdminLookup = PlatformAdminLookup { subject ->
            if (subject == "admin-subject") PlatformAdminView(UUID.randomUUID(), "admin@saqz.test", "Ana Admin") else null
        }

        @Bean
        fun adminUsersController() = br.com.saqz.adminweb.http.AdminUsersController(StubDirectory())

        @Bean
        fun suspendedSessionRepository() = SuspendedAwareSessionRepository()

        @Bean
        fun suspendedBootstrapSession(repository: SuspendedAwareSessionRepository) = BootstrapSession(repository)

        @Bean
        fun suspendedSessionController(bootstrap: BootstrapSession) = AccessSessionController(
            bootstrap,
            CompleteSessionProfile(SuspendedAwareSessionRepository()),
            deleteAccount = testDeleteAccountUnsupported(),
        )

        private fun testDeleteAccountUnsupported() = br.com.saqz.access.application.session.DeleteAccount(
            transactionRunner = object : br.com.saqz.access.application.session.AccountTransactionRunner {
                override fun <T> inTransaction(block: () -> T): T = block()
            },
            repository = object : br.com.saqz.access.application.session.AccountDeletionRepository {
                override fun softDelete(subject: String): UUID? = null
            },
            groupCleanup = object : br.com.saqz.access.application.session.AccountGroupCleanup {
                override fun deleteOwnedGroups(ownerUserId: UUID) = Unit

                override fun removeMemberships(userId: UUID) = Unit
            },
        )
    }

    private companion object {
        val USER_ID: UUID = UUID.fromString("3d2a9c4e-1f2b-4a5c-8d6e-0f1a2b3c4d5e")
    }
}
