package br.com.saqz.bootstrap

import br.com.saqz.access.application.admin.PlatformAdminLookup
import br.com.saqz.access.application.admin.PlatformAdminView
import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
import br.com.saqz.postgrestesting.TestPostgres
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
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.*
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.*

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TrialCampaignEndpointIntegrationTest.Fixture::class)
@ActiveProfiles("test")
class TrialCampaignEndpointIntegrationTest {
    @LocalServerPort private var port = 0
    @Autowired private lateinit var ds: DataSource
    @Autowired private lateinit var mapper: ObjectMapper
    private val client = HttpClient.newHttpClient()
    private lateinit var user: String
    @BeforeEach fun reset() {
        user = UUID.randomUUID().toString()
        JdbcClient.create(ds).sql("UPDATE trial_offer_settings SET mode='ON'").update()
    }
    private fun request(method: String, path: String, body: String = "", token: String? = "admin"): HttpResponse<String> {
        val b = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path")).header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(body))
        token?.let { b.header("Authorization", "Bearer $it") }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString())
    }
    private fun code() = UUID.randomUUID().toString().replace("-", "").uppercase()
    private fun create(c: String = code(), days: Int = 30) = request("POST", "/admin/trial-coupons", """{"code":"$c","trialDays":$days,"campaign":"Quadra","maxUses":10}""")

    @Test fun `admin creates custom duration coupon and user applies without starting trial`() {
        val c = code()
        val created = create(c.lowercase(), 45)
        assertEquals(201, created.statusCode(), created.body())
        val coupon = mapper.readTree(created.body())
        assertEquals(c, coupon["code"].stringValue())
        assertEquals(45, coupon["trialDays"].intValue())
        assertEquals("Quadra", coupon["campaign"].stringValue())
        assertEquals(10, coupon["maxUses"].intValue())
        assertEquals(409, create(c).statusCode())
        assertEquals(200, request("PUT", "/admin/trial-offer", """{"mode":"COUPON_ONLY"}""").statusCode())
        val before = mapper.readTree(request("GET", "/subscriptions/trial", token=user).body())
        assertEquals("INELIGIBLE", before["status"].stringValue())
        assertTrue(before["canRedeemCoupon"].booleanValue())
        val applied = request("POST", "/subscriptions/trial/coupon", """{"code":" $c "}""", user)
        assertEquals(200, applied.statusCode(), applied.body())
        val after = mapper.readTree(applied.body())
        assertEquals("AVAILABLE", after["status"].stringValue())
        assertEquals(c, after["selectedCouponCode"].stringValue())
        assertEquals(45, after["trialDays"].intValue())
        assertTrue(after["startedAt"].isNull)
        assertTrue(after["canCreateGroup"].booleanValue())
        assertEquals(200, request("POST", "/subscriptions/trial/coupon", """{"code":"$c"}""", user).statusCode())
        val listed = mapper.readTree(request("GET", "/admin/trial-coupons").body()).first { it["code"].stringValue() == c }
        assertEquals(0, listed["uses"].intValue())
        assertEquals(204, request("POST", "/admin/trial-coupons/${coupon["id"].stringValue()}/deactivate").statusCode())
        val deactivated = mapper.readTree(request("GET", "/subscriptions/trial", token=user).body())
        assertFalse(deactivated["canCreateGroup"].booleanValue())
        assertTrue(deactivated["selectedCouponCode"].isNull)
        assertEquals(400, request("POST", "/subscriptions/trial/coupon", """{"code":"$c"}""", user).statusCode())
    }
    @Test fun `off mode is persisted blocks selected coupon and all routes enforce authorization`() {
        val c = code(); create(c)
        assertEquals(200, request("POST", "/subscriptions/trial/coupon", """{"code":"$c"}""", user).statusCode())
        assertEquals(200, request("PUT", "/admin/trial-offer", """{"mode":"OFF"}""").statusCode())
        assertEquals("OFF", mapper.readTree(request("GET", "/admin/trial-offer").body())["mode"].stringValue())
        val trial = mapper.readTree(request("GET", "/subscriptions/trial", token=user).body())
        assertFalse(trial["canRedeemCoupon"].booleanValue())
        assertFalse(trial["canCreateGroup"].booleanValue())
        assertEquals(409, request("POST", "/subscriptions/trial/coupon", """{"code":"$c"}""", user).statusCode())
        listOf("GET" to "/admin/trial-offer", "PUT" to "/admin/trial-offer", "GET" to "/admin/trial-coupons", "POST" to "/admin/trial-coupons", "POST" to "/admin/trial-coupons/${UUID.randomUUID()}/deactivate").forEach { (method,path) ->
            assertEquals(403, request(method,path,"{}", user).statusCode(), path)
            assertEquals(401, request(method,path,"{}", null).statusCode(), path)
        }
        assertEquals(401, request("POST", "/subscriptions/trial/coupon", "{}", null).statusCode())
    }
    @Test fun `invalid fields and unavailable coupons return precise errors`() {
        listOf("{}", """{"mode":"INVALID"}""").forEach { assertEquals(400, request("PUT","/admin/trial-offer",it).statusCode()) }
        listOf("{}", """{"code":"A","trialDays":0}""", """{"code":"A","trialDays":366}""", """{"code":"A","trialDays":1.5}""", """{"code":"A","trialDays":14,"maxUses":0}""", """{"code":"A B","trialDays":14}""", """{"code":"A","trialDays":14,"validUntil":"2020-01-01T00:00:00Z"}""").forEach {
            assertEquals(400, request("POST","/admin/trial-coupons",it).statusCode(), it)
        }
        assertEquals(404, request("POST", "/admin/trial-coupons/${UUID.randomUUID()}/deactivate").statusCode())
        assertEquals(400, request("POST", "/subscriptions/trial/coupon", "{}", user).statusCode())
        assertEquals(400, request("POST", "/subscriptions/trial/coupon", """{"code":"UNKNOWN"}""", user).statusCode())
        assertEquals(201, create(days=1).statusCode())
        assertEquals(201, create(days=365).statusCode())
    }
    @Test fun `applied coupon creates group with configured duration and keeps it after switching off`() {
        val c=code();create(c,45)
        request("PUT","/admin/trial-offer", """{"mode":"COUPON_ONLY"}""")
        assertEquals(200, request("POST","/subscriptions/trial/coupon", """{"code":"$c"}""",user).statusCode())
        val body="""{"requestId":"${UUID.randomUUID()}","name":"Campaign Group","modality":"COURT_VOLLEYBALL","composition":"MIXED","timeZone":"America/Sao_Paulo"}"""
        val group=request("POST","/api/groups",body,user)
        assertEquals(201,group.statusCode(),group.body())
        val trial=mapper.readTree(request("GET","/subscriptions/trial",token=user).body())
        assertEquals("ACTIVE",trial["status"].stringValue())
        assertEquals(45,trial["trialDays"].intValue())
        val start=java.time.Instant.parse(trial["startedAt"].stringValue())
        assertEquals(start.plusSeconds(45 * 86400L),java.time.Instant.parse(trial["endsAt"].stringValue()))
        assertEquals(200,request("PUT","/admin/trial-offer", """{"mode":"OFF"}""").statusCode())
        val preserved=mapper.readTree(request("GET","/subscriptions/trial",token=user).body())
        assertEquals(trial["endsAt"].stringValue(),preserved["endsAt"].stringValue())
        assertEquals(45,preserved["trialDays"].intValue())
        assertEquals(409,request("POST","/subscriptions/trial/coupon", """{"code":"$c"}""",user).statusCode())
    }

    @TestConfiguration(proxyBeanMethods=false) class Fixture {
        @Bean @Primary fun verifier() = VerifyRequestIdentity {
            TokenVerification.Verified(RequestIdentity(it.value, "${it.value}@example.test", true, "Trial User"))
        }
        @Bean @Primary fun admins() = PlatformAdminLookup { subject ->
            if (subject == "admin") PlatformAdminView(UUID.randomUUID(), "admin@test", "Admin") else null
        }
    }
    companion object {
        private val database = TestPostgres.empty()
        @JvmStatic @DynamicPropertySource fun properties(r: DynamicPropertyRegistry) {
            r.add("spring.datasource.url") { database.jdbcUrl }; r.add("spring.datasource.username") { database.username }
            r.add("spring.datasource.password") { database.password }; r.add("saqz.firebase.emulator.enabled") { "true" }
            r.add("saqz.branch.domain") { "https://join.test" }; r.add("saqz.password-reset.secret") { "segredo-de-teste-com-trinta-e-dois" }
        }
    }
}
