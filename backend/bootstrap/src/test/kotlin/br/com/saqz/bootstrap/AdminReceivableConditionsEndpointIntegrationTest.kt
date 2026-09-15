package br.com.saqz.bootstrap

import br.com.saqz.access.application.admin.PlatformAdminLookup
import br.com.saqz.access.application.admin.PlatformAdminView
import br.com.saqz.adminweb.http.AdminReceivableConditionsController
import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
import br.com.saqz.receivables.application.*
import br.com.saqz.receivables.domain.FeeSchedule
import br.com.saqz.receivables.domain.PaymentMethod
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
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.*

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AdminReceivableConditionsEndpointIntegrationTest.Config::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["saqz.firebase.emulator.enabled=true"])
class AdminReceivableConditionsEndpointIntegrationTest {
    @LocalServerPort private var port = 0
    @Autowired private lateinit var publisher: RecordingPublisher
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var provider: RecordingProvider
    private val client = HttpClient.newHttpClient()
    @BeforeEach fun reset() { publisher.requests.clear(); publisher.schedules.clear(); provider.unavailable = false; provider.accounts.clear() }

    @Test
    fun `financial conditions require platform admin and never grant money authority`() {
        assertEquals(401, post("/terms", "{}", null).statusCode())
        assertEquals(403, post("/terms", "{}", "user-token").statusCode())
        assertEquals(403, post("/fees", "{}", "user-token").statusCode())
        assertEquals(403, post("/fees/simulate", "{}", "user-token").statusCode())
        assertTrue(publisher.requests.isEmpty())
        // There is no admin withdrawal endpoint in this controller.
        assertEquals(404, post("/withdraw", "{}").statusCode())
    }

    @Test
    fun `publication binds exact money amounts and attributes changes to authenticated admin`() {
        val id = UUID.randomUUID()
        val terms = post("/terms", """{"requestId":"$id","version":"v1","content":"Terms","effectiveAt":"2026-09-13T00:00:00Z"}""")
        assertEquals(200, terms.statusCode())
        assertEquals(id.toString(), mapper.readTree(terms.body())["requestId"].stringValue())
        assertEquals(FinancialRequest(id, ADMIN_ID), publisher.requests.single())
        val feeId = UUID.randomUUID()
        assertEquals(200, post("/fees", fees(feeId)).statusCode())
        assertEquals(FinancialRequest(feeId, ADMIN_ID), publisher.requests.last())
        assertEquals(BigDecimal.ZERO, publisher.schedules.single().providerRate)
        assertEquals(0, publisher.schedules.single().providerFixedCents)
        val count = publisher.requests.size
        for (invalid in listOf("{}", fees(UUID.randomUUID()).replace("\"commissionFixedCents\":100", "\"commissionFixedCents\":100.5"),
            fees(UUID.randomUUID()).replace("\"commissionRate\":0.02", "\"commissionRate\":1.01"))) {
            assertEquals(400, post("/fees", invalid).statusCode())
        }
        assertEquals(count, publisher.requests.size)
    }

    @Test
    fun `admin previews unpublished costs without publishing and rejects fractional base`() {
        val id = UUID.randomUUID()
        val preview = post("/fees/simulate", fees(id))
        assertEquals(200, preview.statusCode())
        assertEquals(10658, mapper.readTree(preview.body())["value"]["totalCents"].longValue())
        assertEquals(400, post("/fees/simulate", fees(id).replace("\"baseCents\":10000", "\"baseCents\":10000.5")).statusCode())
        assertTrue(publisher.requests.isEmpty())
    }

    private fun fees(id: UUID) = """{"requestId":"$id","method":"PIX","commissionRate":0.02,"commissionFixedCents":100,"termsVersion":"v1","effectiveAt":"2026-09-13T00:00:00Z","baseCents":10000}"""
    private fun post(path: String, body: String, token: String? = "admin-token"): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI.create("http://localhost:$port/admin/receivables$path"))
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body))
        if (token != null) builder.header("Authorization", "Bearer $token")
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    @Test
    fun `provider fees are read only and unavailable fees cannot create an estimate`() {
        for (field in listOf("providerRate", "providerFixedCents")) {
            val body = fees(UUID.randomUUID()).replace("{", "{\"$field\":0,")
            assertEquals(400, post("/fees", body).statusCode())
            assertEquals(400, post("/fees/simulate", body).statusCode())
        }
        assertTrue(publisher.requests.isEmpty())
        assertTrue(provider.accounts.isEmpty())
        provider.unavailable = true
        assertEquals(503, post("/fees/simulate", fees(UUID.randomUUID())).statusCode())
        assertEquals(200, post("/fees", fees(UUID.randomUUID())).statusCode())
        assertEquals(listOf<UUID?>(null), provider.accounts)
    }

    class RecordingProvider : FinancialFeeProvider {
        var unavailable = false
        val accounts = mutableListOf<UUID?>()
        override fun current(methods: Set<PaymentMethod>, at: Instant, accountId: UUID?): Map<PaymentMethod, ProviderPaymentFee> {
            accounts += accountId
            if (unavailable) throw FinancialFeesUnavailable()
            return methods.associateWith { ProviderPaymentFee(BigDecimal("0.0299"), 39) }
        }
    }
    class RecordingPublisher : FinancialConditionsPublisher {
        val requests = mutableListOf<FinancialRequest>()
        val schedules = mutableListOf<FeeSchedule>()
        override fun terms(request: FinancialRequest, version: String, content: String, effectiveAt: Instant, now: Instant): FinancialResult<PublishedFinancialCondition> {
            requests += request
            return FinancialResult.Success(PublishedFinancialCondition("TERMS", version, effectiveAt, now), request.requestId)
        }
        override fun fees(request: FinancialRequest, schedule: FeeSchedule, effectiveAt: Instant, now: Instant): FinancialResult<PublishedFinancialCondition> {
            requests += request
            schedules += schedule
            return FinancialResult.Success(PublishedFinancialCondition("FEE_SCHEDULE", schedule.id.toString(), effectiveAt, now), request.requestId)
        }
    }
    @TestConfiguration(proxyBeanMethods = false)
    class Config {
        @Bean @Primary fun verifier() = VerifyRequestIdentity {
            when (it.value) {
                "admin-token" -> TokenVerification.Verified(RequestIdentity("admin"))
                "user-token" -> TokenVerification.Verified(RequestIdentity("user"))
                else -> TokenVerification.Rejected
            }
        }
        @Bean fun admins(): PlatformAdminLookup = PlatformAdminLookup {
            if (it == "admin") PlatformAdminView(ADMIN_ID, null, null) else null
        }
        @Bean fun publisher() = RecordingPublisher()
        @Bean fun provider() = RecordingProvider()
        @Bean fun controller(admins: PlatformAdminLookup, publisher: RecordingPublisher, provider: RecordingProvider) =
            AdminReceivableConditionsController(admins, publisher,
                Clock.fixed(Instant.parse("2026-09-12T12:00:00Z"), ZoneOffset.UTC), provider)
    }
    private companion object { val ADMIN_ID: UUID = UUID.fromString("343b2029-cc33-4108-9224-42e22d75c3f9") }
}
