package br.com.saqz.bootstrap

import br.com.saqz.access.application.admin.PlatformAdminLookup
import br.com.saqz.access.application.admin.PlatformAdminView
import br.com.saqz.adminweb.http.AdminRevenueController
import br.com.saqz.adminweb.http.AdminSubscriptionsController
import br.com.saqz.subscriptions.application.AdminRevenueCourtesyRow
import br.com.saqz.subscriptions.application.AdminRevenueReport
import br.com.saqz.subscriptions.application.AdminSubscriptionCourtesy
import br.com.saqz.subscriptions.application.AdminRevenueRow
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
        assertEquals("SITE", body["items"][0]["source"].stringValue())
        assertEquals("REAL", body["items"][0]["billing"].stringValue())
        assertEquals("ILIMITADO", body["items"][0]["courtesy"]["plan"].stringValue())
        assertEquals(true, body["items"][0]["courtesy"]["endsAt"].isNull)
    }

    @Test
    fun `filtro por origem chega ao diretorio e a cortesia vem sem ciclo nem preco`() {
        val response = exchange("GET", "/admin/subscriptions?source=COURTESY")
        val item = objectMapper.readTree(response.body())["items"][0]

        assertEquals(200, response.statusCode())
        assertEquals("COURTESY", item["source"].stringValue())
        assertEquals("NO_CHARGE", item["billing"].stringValue())
        assertEquals(true, item["cycle"].isNull)
        assertEquals(true, item["priceCents"].isNull)
        assertEquals(400, exchange("GET", "/admin/subscriptions?source=PIX").statusCode())
    }

    @Test
    fun `receita traz real e teste separados e recusa meses fora do limite`() {
        val response = exchange("GET", "/admin/revenue?months=3")
        val body = objectMapper.readTree(response.body())

        assertEquals(200, response.statusCode())
        assertEquals("2026-10", body["real"][0]["month"].stringValue())
        assertEquals("APP_STORE", body["real"][0]["source"].stringValue())
        assertEquals(5_990, body["real"][0]["grossCents"].intValue())
        assertEquals(true, body["real"][0]["netCents"].isNull)
        assertEquals(46, body["test"][0]["payments"].intValue())
        assertEquals("2026-10", body["courtesy"][0]["month"].stringValue())
        assertEquals(2, body["courtesy"][0]["active"].intValue())
        assertEquals(15_980, body["courtesy"][0]["cededCents"].intValue())
        assertEquals(400, exchange("GET", "/admin/revenue?months=0").statusCode())
        assertEquals(400, exchange("GET", "/admin/revenue?months=61").statusCode())
        assertEquals(403, exchange("GET", "/admin/revenue", token = "user-token").statusCode())
    }

    @Test
    fun `detalhe traz recibos e inexistente da 404`() {
        val ok = exchange("GET", "/admin/subscriptions/$OWNER_ID")
        val body = objectMapper.readTree(ok.body())

        assertEquals(200, ok.statusCode())
        assertEquals("ILIMITADO", body["summary"]["courtesy"]["plan"].stringValue())
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
        assertEquals(400, exchange("GET", "/admin/subscriptions?sort=price").statusCode())
        assertEquals(400, exchange("GET", "/admin/subscriptions?sort=plan&dir=x").statusCode())
        assertEquals(200, exchange("GET", "/admin/subscriptions?sort=owner&dir=asc").statusCode())
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
            courtesy = AdminSubscriptionCourtesy("ILIMITADO", null),
        )

        private val courtesy = summary.copy(
            plan = "ILIMITADO", cycle = null, couponCode = null, priceCents = null, currentPeriodEnd = null,
            source = "COURTESY", billing = "NO_CHARGE",
        )

        override fun list(
            query: String?,
            plan: String?,
            status: String?,
            page: Int,
            size: Int,
            source: String?,
            sort: String?,
            descending: Boolean,
        ) =
            AdminSubscriptionPage(listOf(if (source == "COURTESY") courtesy else summary), 1, page, size)

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
        fun adminRevenueController() = AdminRevenueController {
            AdminRevenueReport(
                real = listOf(AdminRevenueRow("2026-10", "APP_STORE", "BRL", 1, 5_990, null)),
                test = listOf(AdminRevenueRow("2026-10", "APP_STORE", "BRL", 46, 276_000, null)),
                courtesy = listOf(AdminRevenueCourtesyRow("2026-10", 2, 15_980)),
            )
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
