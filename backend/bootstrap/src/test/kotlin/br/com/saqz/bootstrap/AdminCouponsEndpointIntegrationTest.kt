package br.com.saqz.bootstrap

import br.com.saqz.access.application.admin.PlatformAdminLookup
import br.com.saqz.access.application.admin.PlatformAdminView
import br.com.saqz.adminweb.http.AdminCouponsController
import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.subscriptions.application.AdminCoupon
import br.com.saqz.subscriptions.application.AdminCouponCreateResult
import br.com.saqz.subscriptions.application.AdminCouponDirectory
import br.com.saqz.subscriptions.domain.Coupon
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
@Import(AdminCouponsEndpointIntegrationTest.AdminCouponsTestConfiguration::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["saqz.firebase.emulator.enabled=true"])
class AdminCouponsEndpointIntegrationTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val client: HttpClient = HttpClient.newHttpClient()

    @Test
    fun `lista devolve cupons com usos`() {
        val response = get("/admin/coupons")
        val body = objectMapper.readTree(response.body())

        assertEquals(200, response.statusCode())
        assertEquals("GALERA10", body[0]["code"].stringValue())
        assertEquals(9, body[0]["redemptions"].intValue())
    }

    @Test
    fun `criacao normaliza o codigo e mapeia duplicado para 409`() {
        val created = post("/admin/coupons", "{\"code\": \" novo10 \", \"discountPercent\": 10}")
        val body = objectMapper.readTree(created.body())

        assertEquals(201, created.statusCode())
        assertEquals("NOVO10", body["code"].stringValue())
        assertEquals(409, post("/admin/coupons", "{\"code\": \"GALERA10\", \"discountPercent\": 10}").statusCode())
    }

    @Test
    fun `criacao invalida retorna 400`() {
        assertEquals(400, post("/admin/coupons", "{}").statusCode())
        assertEquals(400, post("/admin/coupons", "{\"code\": \"OK\", \"discountPercent\": 0}").statusCode())
        assertEquals(400, post("/admin/coupons", "{\"code\": \"OK\", \"discountPercent\": 101}").statusCode())
        assertEquals(400, post("/admin/coupons", "{\"code\": \"COM ESPACO\", \"discountPercent\": 10}").statusCode())
        assertEquals(
            400,
            post(
                "/admin/coupons",
                "{\"code\": \"OK\", \"discountPercent\": 10, \"validUntil\": \"2020-01-01T00:00:00Z\"}",
            ).statusCode(),
        )
    }

    @Test
    fun `desativar responde 204 ou 404 e comum recebe 403`() {
        assertEquals(204, post("/admin/coupons/$COUPON_ID/deactivate", "").statusCode())
        assertEquals(404, post("/admin/coupons/${UUID.randomUUID()}/deactivate", "").statusCode())
        assertEquals(403, get("/admin/coupons", token = "user-token").statusCode())
    }

    private fun get(path: String, token: String = "admin-token"): HttpResponse<String> =
        client.send(
            HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET()
                .header("Authorization", "Bearer $token").build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun post(path: String, body: String, token: String = "admin-token"): HttpResponse<String> =
        client.send(
            HttpRequest.newBuilder(URI.create("http://localhost:$port$path"))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $token").build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    class StubCouponDirectory : AdminCouponDirectory {
        override fun list() = listOf(
            AdminCoupon(
                id = COUPON_ID,
                code = "GALERA10",
                discountPercent = 10,
                durationCycles = 6,
                validUntil = Instant.parse("2026-08-15T00:00:00Z"),
                redemptions = 9,
                activeSubscriptions = 8,
            ),
        )

        override fun create(
            code: String,
            discountPercent: Int,
            durationCycles: Int?,
            validUntil: Instant?,
        ): AdminCouponCreateResult =
            if (code == "GALERA10") {
                AdminCouponCreateResult.DuplicateCode
            } else {
                AdminCouponCreateResult.Created(Coupon(UUID.randomUUID(), code, discountPercent, durationCycles, validUntil))
            }

        override fun deactivate(couponId: UUID, now: Instant) = couponId == COUPON_ID
    }

    @TestConfiguration(proxyBeanMethods = false)
    class AdminCouponsTestConfiguration {
        @Bean
        @Primary
        fun adminCouponsVerifier(): VerifyRequestIdentity = VerifyRequestIdentity {
            when (it.value) {
                "admin-token" -> TokenVerification.Verified(RequestIdentity("admin-subject", "admin@saqz.test", true))
                "user-token" -> TokenVerification.Verified(RequestIdentity("user-subject", "user@saqz.test", true))
                else -> TokenVerification.Rejected
            }
        }

        @Bean
        fun adminCouponsLookup(): PlatformAdminLookup = PlatformAdminLookup { subject ->
            if (subject == "admin-subject") PlatformAdminView(UUID.randomUUID(), "admin@saqz.test", "Ana Admin") else null
        }

        @Bean
        fun adminCouponsController() = AdminCouponsController(
            StubCouponDirectory(),
            now = { Instant.parse("2026-08-03T12:00:00Z") },
        )
    }

    private companion object {
        val COUPON_ID: UUID = UUID.fromString("4e5f6a7b-8c9d-4e0f-8a1b-2c3d4e5f6a7b")
    }
}
