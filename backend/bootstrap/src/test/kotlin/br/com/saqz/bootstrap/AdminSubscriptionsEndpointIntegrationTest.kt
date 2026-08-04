package br.com.saqz.bootstrap

import br.com.saqz.access.application.admin.PlatformAdminLookup
import br.com.saqz.access.application.admin.PlatformAdminView
import br.com.saqz.adminweb.http.AdminSubscriptionsController
import br.com.saqz.subscriptions.application.AdminReceipt
import br.com.saqz.subscriptions.application.AdminSubscriptionCanceler
import br.com.saqz.subscriptions.application.AdminSubscriptionDetail
import br.com.saqz.subscriptions.application.AdminSubscriptionDirectory
import br.com.saqz.subscriptions.application.AdminSubscriptionPage
import br.com.saqz.subscriptions.application.AdminSubscriptionSummary
import br.com.saqz.subscriptions.application.CancelSubscriptionResult
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
@Import(AdminSubscriptionsEndpointIntegrationTest.AdminSubscriptionsTestConfiguration::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["saqz.firebase.emulator.enabled=true"])
class AdminSubscriptionsEndpointIntegrationTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val client: HttpClient = HttpClient.newHttpClient()

    @Test
    fun `lista devolve assinatura com preco efetivo e cupom`() {
        val response = exchange("GET", "/admin/subscriptions?plan=ORGANIZADOR&status=ACTIVE")
        val body = objectMapper.readTree(response.body())

        assertEquals(200, response.statusCode())
        assertEquals("Camila Rocha", body["items"][0]["ownerName"].stringValue())
        assertEquals(5_391, body["items"][0]["priceCents"].intValue())
        assertEquals("GALERA10", body["items"][0]["couponCode"].stringValue())
    }

    @Test
    fun `detalhe traz recibos e inexistente da 404`() {
        val ok = exchange("GET", "/admin/subscriptions/$OWNER_ID")
        val body = objectMapper.readTree(ok.body())

        assertEquals(200, ok.statusCode())
        assertEquals(1, body["receipts"].size())
        assertEquals(5_391, body["receipts"][0]["valueCents"].intValue())
        assertEquals(404, exchange("GET", "/admin/subscriptions/${UUID.randomUUID()}").statusCode())
    }

    @Test
    fun `cancelamento mapeia sucesso conflito e inexistente`() {
        assertEquals(204, exchange("POST", "/admin/subscriptions/$OWNER_ID/cancel").statusCode())
        assertEquals(409, exchange("POST", "/admin/subscriptions/$CANCELED_ID/cancel").statusCode())
        assertEquals(404, exchange("POST", "/admin/subscriptions/${UUID.randomUUID()}/cancel").statusCode())
    }

    @Test
    fun `parametros invalidos retornam 400 e comum recebe 403`() {
        assertEquals(400, exchange("GET", "/admin/subscriptions?plan=QUADRA_CHEIA").statusCode())
        assertEquals(400, exchange("GET", "/admin/subscriptions?status=atrasada").statusCode())
        assertEquals(403, exchange("GET", "/admin/subscriptions", token = "user-token").statusCode())
    }

    private fun exchange(method: String, path: String, token: String = "admin-token"): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI.create("http://localhost:$port$path"))
            .method(method, HttpRequest.BodyPublishers.noBody())
            .header("Authorization", "Bearer $token")
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    class StubSubscriptionDirectory : AdminSubscriptionDirectory {
        private val summary = AdminSubscriptionSummary(
            ownerUserId = OWNER_ID,
            ownerName = "Camila Rocha",
            ownerEmail = "camila@saqz.test",
            plan = "ORGANIZADOR",
            cycle = "MONTHLY",
            status = "ACTIVE",
            couponCode = "GALERA10",
            priceCents = 5_391,
            currentPeriodEnd = Instant.parse("2026-09-01T00:00:00Z"),
            canceledAt = null,
            pastDueSince = null,
            createdAt = Instant.parse("2026-02-20T00:00:00Z"),
        )

        override fun list(query: String?, plan: String?, status: String?, page: Int, size: Int) =
            AdminSubscriptionPage(listOf(summary), 1, page, size)

        override fun find(ownerUserId: UUID): AdminSubscriptionDetail? {
            if (ownerUserId != OWNER_ID) return null
            return AdminSubscriptionDetail(
                summary,
                listOf(AdminReceipt("evt-1", 5_391, Instant.parse("2026-08-01T03:00:00Z"))),
            )
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class AdminSubscriptionsTestConfiguration {
        @Bean
        @Primary
        fun adminSubscriptionsVerifier(): VerifyRequestIdentity = VerifyRequestIdentity {
            when (it.value) {
                "admin-token" -> TokenVerification.Verified(RequestIdentity("admin-subject", "admin@saqz.test", true))
                "user-token" -> TokenVerification.Verified(RequestIdentity("user-subject", "user@saqz.test", true))
                else -> TokenVerification.Rejected
            }
        }

        @Bean
        fun adminSubscriptionsLookup(): PlatformAdminLookup = PlatformAdminLookup { subject ->
            if (subject == "admin-subject") PlatformAdminView(UUID.randomUUID(), "admin@saqz.test", "Ana Admin") else null
        }

        @Bean
        fun adminSubscriptionsController() = AdminSubscriptionsController(
            StubSubscriptionDirectory(),
            { ownerUserId ->
                when (ownerUserId) {
                    OWNER_ID -> CancelSubscriptionResult.Success(
                        // O controller não usa o corpo do Success; qualquer subscription serve,
                        // mas construir o domínio aqui exigiria fixture — devolvemos via stub.
                        stubSuccess(),
                    )
                    CANCELED_ID -> CancelSubscriptionResult.AlreadyCanceled
                    else -> CancelSubscriptionResult.NotFound
                }
            },
        )

        private fun stubSuccess(): br.com.saqz.subscriptions.domain.Subscription =
            br.com.saqz.subscriptions.domain.Subscription(
                ownerUserId = OWNER_ID,
                plan = br.com.saqz.subscriptions.domain.Plan.ORGANIZADOR,
                cycle = br.com.saqz.subscriptions.domain.SubscriptionCycle.MONTHLY,
                asaasCustomerId = "cus-1",
                asaasSubscriptionId = "sub-1",
                billingType = null,
                currentPeriodEnd = Instant.parse("2026-09-01T00:00:00Z"),
            )
    }

    private companion object {
        val OWNER_ID: UUID = UUID.fromString("5f6e7d8c-9a0b-4c1d-8e2f-3a4b5c6d7e8f")
        val CANCELED_ID: UUID = UUID.fromString("6a7b8c9d-0e1f-4a2b-9c3d-4e5f6a7b8c9d")
    }
}
