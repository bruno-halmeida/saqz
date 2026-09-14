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
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.*

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AdminCouponAnalyticsIntegrationTest.Fixture::class)
@ActiveProfiles("test")
class AdminCouponAnalyticsIntegrationTest {
    @LocalServerPort private var port = 0
    @Autowired private lateinit var ds: DataSource
    @Autowired private lateinit var mapper: ObjectMapper
    private val client = HttpClient.newHttpClient()
    private val jdbc get() = JdbcClient.create(ds)
    @BeforeEach fun reset() {
        jdbc.sql("TRUNCATE coupons, trial_coupons, subscriptions, subscription_events, organizer_trials, coupon_redemptions, trial_coupon_selections CASCADE").update()
    }
    private fun request(token: String? = "admin"): HttpResponse<String> {
        val b = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/admin/coupon-analytics")).GET()
        token?.let { b.header("Authorization", "Bearer $it") }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString())
    }
    private fun report(): JsonNode {
        val response = request()
        assertEquals(200, response.statusCode(), response.body())
        return mapper.readTree(response.body())
    }
    private fun user(): UUID = UUID.randomUUID().also {
        jdbc.sql("INSERT INTO access_users(id, firebase_subject, email, email_verified, display_name, created_at, updated_at) VALUES (:id,:subject,:email,true,'Owner',now(),now())")
            .param("id", it).param("subject", it.toString()).param("email", "$it@test.invalid").update()
    }
    private fun coupon(trial: Boolean, code: String = "ARENA", active: Boolean = true, expired: Boolean = false): UUID = UUID.randomUUID().also {
        val sql = if (trial) "INSERT INTO trial_coupons(id,code,campaign,trial_days,active,valid_until,created_at) VALUES (:id,:code,'Quadra A',45,:active,:until,now())"
        else "INSERT INTO coupons(id,code,discount_percent,valid_until,created_at) VALUES (:id,:code,20,:until,now())"
        val q = jdbc.sql(sql).param("id",it).param("code",code).param("until", if (expired) NOW.atOffset(ZoneOffset.UTC) else null)
        if(trial) q.param("active",active)
        q.update()
    }
    private fun use(id: UUID, owner: UUID, trial: Boolean, at: Instant = NOW.minusSeconds(86400), end: Instant = NOW) {
        if (trial) jdbc.sql("INSERT INTO organizer_trials(owner_user_id,coupon_id,coupon_code,started_at,ends_at) VALUES (:owner,:coupon,'ARENA',:at,:end)")
            .param("owner",owner).param("coupon",id).param("at",at.atOffset(ZoneOffset.UTC)).param("end",end.atOffset(ZoneOffset.UTC)).update()
        else jdbc.sql("INSERT INTO coupon_redemptions(coupon_id,user_id,redeemed_at) VALUES (:coupon,:owner,:at)")
            .param("owner",owner).param("coupon",id).param("at",at.atOffset(ZoneOffset.UTC)).update()
    }
    private fun event(owner: UUID?, payment: String?, value: String = "10.25", at: Instant = NOW.minusSeconds(10), type: String = "PAYMENT_CONFIRMED", processed: Boolean = true, raw: String? = null) {
        val id=UUID.randomUUID()
        jdbc.sql("INSERT INTO subscription_events(id,asaas_event_id,type,payload,owner_user_id,processed_at,created_at) VALUES (:id,:event,:type,:payload,:owner,:at,now())")
            .param("id",id).param("event",id.toString()).param("type",type)
            .param("payload",raw ?: """{"payment":{"id":${payment?.let { "\"$it\"" } ?: "null"},"value":$value}}""")
            .param("owner",owner).param("at",if(processed) at.atOffset(ZoneOffset.UTC) else null).update()
    }
    @Test fun `all coupons include unused disabled expired and duplicate code across types`() {
        val trial=coupon(true,active=false); val discount=coupon(false,expired=true)
        val owner=user()
        jdbc.sql("INSERT INTO trial_coupon_selections(owner_user_id,coupon_id,selected_at) VALUES (:owner,:coupon,now())").param("owner",owner).param("coupon",trial).update()
        val r=report(); assertEquals(2,r["coupons"].size()); assertEquals(NOW.toString(),r["asOf"].stringValue())
        val t=r["coupons"].first { it["type"].stringValue()=="TRIAL" };val d=r["coupons"].first { it["type"].stringValue()=="DISCOUNT" }
        assertEquals(trial.toString(),t["id"].stringValue());assertEquals(discount.toString(),d["id"].stringValue())
        assertEquals("ARENA",t["code"].stringValue());assertEquals("ARENA",d["code"].stringValue())
        assertEquals("Quadra A",t["campaign"].stringValue());assertEquals(45,t["trialDays"].intValue());assertEquals(20,d["discountPercent"].intValue())
        assertEquals("INACTIVE",t["status"].stringValue());assertEquals("EXPIRED",d["status"].stringValue())
        assertEquals(0,t["metrics"]["users"].intValue());assertTrue(t["metrics"]["conversionPercent"].isNull)
        assertEquals(0,r["summary"]["revenueCents"].longValue());assertTrue(d["endedTrials"].isNull)
    }
    @Test fun `trial conversion separates maturity deduplicates invoices and preserves campaign after deactivation`() {
        val c=coupon(true);val a=user();val b=user();val ongoing=user()
        use(c,a,true);use(c,b,true);use(c,ongoing,true,end=NOW.plusSeconds(86400))
        event(a,"old",at=NOW.minusSeconds(90000));event(a,"invoice");event(a,"invoice",type="PAYMENT_RECEIVED")
        event(a,"renewal",value="4.75");event(ongoing,"paid",value="5")
        event(b,"pending",processed=false);event(b,"overdue",type="PAYMENT_OVERDUE");event(b,"future",at=NOW.plusSeconds(1))
        jdbc.sql("UPDATE trial_coupons SET active=false,code='RENAMED' WHERE id=:id").param("id",c).update()
        val row=report()["coupons"][0]; val m=row["metrics"]
        assertEquals("RENAMED",row["code"].stringValue());assertEquals("INACTIVE",row["status"].stringValue())
        assertEquals(3,m["users"].intValue());assertEquals(2,m["payingUsers"].intValue());assertEquals(66.67,m["conversionPercent"].doubleValue())
        assertEquals(2000,m["revenueCents"].longValue());assertEquals(3,m["payments"].intValue())
        assertEquals(1,row["ongoingTrials"].intValue());assertEquals(2,row["endedTrials"].intValue());assertEquals(50.0,row["endedConversionPercent"].doubleValue())
    }
    @Test fun `discounts track later payment without current coupon and global totals deduplicate shared participants`() {
        val t=coupon(true);val d=coupon(false);val second=coupon(false,"SECOND");val owner=user()
        use(t,owner,true);use(d,owner,false);use(second,owner,false)
        event(owner,"unique",value="29.90");event(owner,"unique",value="29.90",type="PAYMENT_RECEIVED")
        val r=report();assertEquals(3,r["coupons"].size())
        r["coupons"].forEach { assertEquals(1,it["metrics"]["payingUsers"].intValue());assertEquals(100.0,it["metrics"]["conversionPercent"].doubleValue());assertEquals(2990,it["metrics"]["revenueCents"].longValue()) }
        assertEquals(1,r["summary"]["users"].intValue());assertEquals(1,r["summary"]["payingUsers"].intValue());assertEquals(1,r["summary"]["payments"].intValue());assertEquals(2990,r["summary"]["revenueCents"].longValue())
    }
    @Test fun `late duplicate cannot convert a later redemption and invalid receipts never invent revenue`() {
        val d=coupon(false);val owner=user();use(d,owner,false)
        event(owner,"before",at=NOW.minusSeconds(90000));event(owner,"before",type="PAYMENT_RECEIVED")
        event(null,"unowned");event(owner,null);event(owner,"invalid",value="\"oops\"");event(owner,"broken",raw="not JSON")
        event(owner,"zero",value="0");event(owner,"negative",value="-4")
        val r=report();val m=r["coupons"][0]["metrics"]
        assertEquals(0,m["payingUsers"].intValue());assertEquals(0,m["revenueCents"].longValue());assertEquals(0,m["payments"].intValue());assertEquals(1,m["incompleteUsers"].intValue())
        assertEquals(0.0,m["conversionPercent"].doubleValue())
    }
    @Test fun `missing receipts remain explicit and active status alone is not payment evidence`() {
        val c=coupon(false);val recovered=user();val unpaid=user();val zero=user()
        listOf(recovered,unpaid,zero).forEach { use(c,it,false) }
        listOf(recovered,unpaid,zero).forEach { owner ->
            jdbc.sql("""INSERT INTO subscriptions(owner_user_id,plan,cycle,status,asaas_customer_id,asaas_subscription_id,current_period_end,created_at,updated_at,first_confirmed_at)
                VALUES (:owner,'TITULAR','MONTHLY','ACTIVE',:sub,:sub,now(),now(),now(),:confirmed)""")
                .param("owner",owner).param("sub",owner.toString()).param("confirmed",if(owner==unpaid) null else NOW.minusSeconds(5).atOffset(ZoneOffset.UTC)).update()
        }
        event(zero,"free",value="0")
        val m=report()["summary"]
        assertEquals(3,m["users"].intValue());assertEquals(0,m["payingUsers"].intValue());assertEquals(1,m["incompleteUsers"].intValue());assertEquals(0,m["revenueCents"].longValue())
    }
    @Test fun `exhausted ongoing trial has no mature rate and exact current confirmation rounds cents`() {
        val c=coupon(true);val owner=user()
        use(c,owner,true,at=NOW,end=NOW.plusSeconds(86400))
        jdbc.sql("UPDATE trial_coupons SET max_uses=1 WHERE id=:id").param("id",c).update()
        event(owner,"rounded",value="\"12.345\"",at=NOW)
        event(owner,"unknown",value="null",at=NOW)
        val row=report()["coupons"][0];val m=row["metrics"]
        assertEquals("EXHAUSTED",row["status"].stringValue());assertEquals(1,row["ongoingTrials"].intValue())
        assertEquals(0,row["endedTrials"].intValue());assertTrue(row["endedConversionPercent"].isNull)
        assertEquals(100.0,m["conversionPercent"].doubleValue());assertEquals(1235,m["revenueCents"].longValue())
        assertEquals(1,m["incompleteUsers"].intValue());assertEquals(1,m["payments"].intValue())
    }
    @Test fun `empty report and authorization are precise and response excludes personal data`() {
        val r=report();assertEquals(0,r["coupons"].size());assertTrue(r["summary"]["conversionPercent"].isNull)
        assertEquals(403,request("user").statusCode());assertEquals(401,request(null).statusCode())
        val c=coupon(false);val owner=user();use(c,owner,false);event(owner,"secret-payment")
        val body=request().body();assertFalse(body.contains(owner.toString()));assertFalse(body.contains("secret-payment"));assertFalse(body.contains("payload"))
    }
    @TestConfiguration(proxyBeanMethods=false) class Fixture {
        @Bean @Primary fun clock(): Clock = Clock.fixed(NOW,ZoneOffset.UTC)
        @Bean @Primary fun verifier() = VerifyRequestIdentity { TokenVerification.Verified(RequestIdentity(it.value,"${it.value}@test.invalid",true)) }
        @Bean @Primary fun admins() = PlatformAdminLookup { if(it=="admin") PlatformAdminView(UUID.randomUUID(),"admin@test.invalid","Admin") else null }
    }
    companion object {
        private val NOW=Instant.parse("2026-09-13T12:00:00Z")
        private val database=TestPostgres.empty()
        @JvmStatic @DynamicPropertySource fun properties(r: DynamicPropertyRegistry) {
            r.add("spring.datasource.url") { database.jdbcUrl };r.add("spring.datasource.username") { database.username };r.add("spring.datasource.password") { database.password }
            r.add("saqz.firebase.emulator.enabled") { "true" };r.add("saqz.branch.domain") { "https://join.test" };r.add("saqz.password-reset.secret") { "segredo-de-teste-com-trinta-e-dois" }
        }
    }
}
