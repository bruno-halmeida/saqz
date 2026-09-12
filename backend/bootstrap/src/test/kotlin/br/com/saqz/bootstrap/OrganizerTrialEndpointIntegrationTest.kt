package br.com.saqz.bootstrap

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
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(OrganizerTrialEndpointIntegrationTest.Fixture::class)
@ActiveProfiles("test")
class OrganizerTrialEndpointIntegrationTest {
    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var ds: DataSource
    @Autowired private lateinit var mapper: ObjectMapper
    @Autowired private lateinit var clock: TrialClock
    @Autowired private lateinit var monthlySchedule: br.com.saqz.groups.application.finance.charge.MonthlyChargeSchedule
    private val now = Instant.parse("2026-09-12T12:00:00Z")
    private lateinit var token: String
    private val client = HttpClient.newHttpClient()

    @BeforeEach
    fun reset() { token = UUID.randomUUID().toString(); clock.now = now }

    @Test
    fun `available lookup is free and returns exact offer and configured app link`() {
        val response = request("GET", "/subscriptions/trial")
        assertEquals(200, response.statusCode())
        val body = mapper.readTree(response.body())
        assertEquals("AVAILABLE", body["status"].stringValue())
        assertTrue(body["startedAt"].isNull)
        assertTrue(body["endsAt"].isNull)
        assertEquals(now.toString(), body["serverTime"].stringValue())
        assertTrue(body["canCreateGroup"].booleanValue())
        assertFalse(body["readOnly"].booleanValue())
        assertEquals(1, body["maxGroups"].intValue())
        assertEquals(25, body["maxAthletes"].intValue())
        assertEquals("https://join.test/?%24ios_nativelink=true", body["appUrl"].stringValue())
        assertEquals(404, request("GET", "/subscriptions/me").statusCode())
        assertEquals(0, jdbc().sql("SELECT count(*)::int FROM organizer_trials t JOIN access_users u ON t.owner_user_id=u.id WHERE u.firebase_subject=:token").param("token", token).query(Int::class.java).single())
    }

    @Test
    fun `group access exposes owner trial to members but never outsiders or anonymous`() {
        val group = createGroup()
        val body = mapper.readTree(request("GET", "/api/groups/$group/trial").body())
        assertEquals("ACTIVE", body["status"].stringValue())
        assertEquals(now.toString(), body["startedAt"].stringValue())
        assertEquals(now.plus(Duration.ofDays(14)).toString(), body["endsAt"].stringValue())
        assertTrue(body["isOwner"].booleanValue())
        assertFalse(body["canCreateGroup"].booleanValue())
        val member = UUID.randomUUID().toString()
        request("GET", "/subscriptions/trial", actor = member)
        jdbc().sql("INSERT INTO group_memberships (group_id,user_id,role,created_at,updated_at) SELECT :group,id,'ATHLETE',now(),now() FROM access_users WHERE firebase_subject=:subject")
            .param("group", group).param("subject", member).update()
        val athlete = mapper.readTree(request("GET", "/api/groups/$group/trial", actor = member).body())
        assertEquals("ACTIVE", athlete["status"].stringValue())
        assertFalse(athlete["isOwner"].booleanValue())
        assertFalse(athlete["canCreateGroup"].booleanValue())
        assertEquals(body["endsAt"], athlete["endsAt"])
        val outsider = request("GET", "/api/groups/$group/trial", actor = UUID.randomUUID().toString())
        assertEquals(404, outsider.statusCode())
        assertFalse(outsider.body().contains("endsAt"))
        assertEquals(401, request("GET", "/api/groups/$group/trial", actor = null).statusCode())
        assertEquals(401, request("GET", "/subscriptions/trial", actor = null).statusCode())
        assertEquals(404, request("GET", "/api/groups/${UUID.randomUUID()}/trial").statusCode())
    }

    @Test
    fun `exact expiry is read only while paid subscription removes trial restrictions`() {
        val group = createGroup()
        clock.now = now.plus(Duration.ofDays(14))
        val expired = mapper.readTree(request("GET", "/api/groups/$group/trial").body())
        assertEquals("EXPIRED", expired["status"].stringValue())
        assertTrue(expired["readOnly"].booleanValue())
        assertFalse(expired["canCreateGroup"].booleanValue())
        jdbc().sql("""INSERT INTO subscriptions (owner_user_id,plan,cycle,status,asaas_customer_id,asaas_subscription_id,current_period_end,first_confirmed_at,created_at,updated_at)
            SELECT id,'ORGANIZADOR','MONTHLY','ACTIVE','customer',:sub,now()+interval '30 days',now(),now(),now() FROM access_users WHERE firebase_subject=:subject""")
            .param("sub", UUID.randomUUID().toString()).param("subject", token).update()
        val subscribed = mapper.readTree(request("GET", "/api/groups/$group/trial").body())
        assertEquals("SUBSCRIBED", subscribed["status"].stringValue())
        assertFalse(subscribed["readOnly"].booleanValue())
        assertTrue(subscribed["canCreateGroup"].booleanValue())
    }

    private fun createGroup(): UUID {
        val response = request("POST", "/api/groups", """{"requestId":"${UUID.randomUUID()}","name":"Trial Group","modality":"COURT_VOLLEYBALL","composition":"MIXED","timeZone":"America/Sao_Paulo"}""")
        assertEquals(201, response.statusCode(), response.body())
        return UUID.fromString(mapper.readTree(response.body())["id"].stringValue())
    }

    @Test
    fun `expired trial blocks every group mutation category and preserves readable history`() {
        val group = createGroup()
        val id = UUID.randomUUID()
        clock.now = now.plus(Duration.ofDays(14))
        val base = "/api/groups/$group"
        val mutations = listOf(
            "PUT" to base, "DELETE" to base, "PUT" to "$base/settings",
            "DELETE" to "$base/photo", "POST" to "$base/invite", "DELETE" to "$base/invite",
            "PUT" to "$base/memberships/$id/role", "PATCH" to "$base/athletes/me",
            "PATCH" to "$base/athletes/$id", "DELETE" to "$base/athletes/$id",
            "PUT" to "$base/athletes/me/auto-confirmation",
            "POST" to "$base/entry-requests/$id/approve", "DELETE" to "$base/entry-requests/$id",
            "POST" to "$base/games", "PUT" to "$base/games/$id",
            "POST" to "$base/games/$id/publish", "POST" to "$base/games/$id/cancel", "POST" to "$base/games/$id/complete",
            "PUT" to "$base/games/$id/attendance", "POST" to "$base/games/$id/attendance/override",
            "POST" to "$base/games/$id/attendance/promote", "PUT" to "$base/games/$id/capacity",
            "POST" to "$base/games/$id/attendance-link", "POST" to "$base/games/$id/notify-pending",
            "POST" to "$base/game-series", "POST" to "$base/game-series/$id/boundaries",
            "POST" to "$base/charges/monthly", "POST" to "$base/charges/$id/status",
            "POST" to "$base/expenses", "PUT" to "$base/expenses/$id", "POST" to "$base/expenses/$id/void",
            "POST" to "$base/messages",
        )
        for ((method, path) in mutations) {
            val result = request(method, path, "{}")
            assertEquals(403, result.statusCode(), "$method $path: ${result.body()}")
            assertEquals("SUBSCRIPTION_REQUIRED", mapper.readTree(result.body())["code"].stringValue())
        }
        for (path in listOf(base, "$base/games", "$base/expenses", "$base/memberships", "$base/trial")) {
            assertEquals(200, request("GET", path).statusCode(), path)
        }
        assertEquals(1L, jdbc().sql("SELECT version FROM access_groups WHERE id=:group").param("group", group).query(Long::class.java).single())
        assertEquals(0, jdbc().sql("SELECT count(*)::int FROM games WHERE group_id=:group").param("group", group).query(Int::class.java).single())
    }

    @Test
    fun `athlete can leave an expired group without reopening management`() {
        val group = createGroup()
        val member = UUID.randomUUID().toString()
        request("GET", "/subscriptions/trial", actor = member)
        jdbc().sql("INSERT INTO group_memberships (group_id,user_id,role,created_at,updated_at) SELECT :group,id,'ATHLETE',now(),now() FROM access_users WHERE firebase_subject=:subject")
            .param("group", group).param("subject", member).update()
        clock.now = now.plus(Duration.ofDays(14))
        assertEquals(204, request("DELETE", "/api/groups/$group/memberships/me", actor = member).statusCode())
        assertEquals(0, jdbc().sql("SELECT count(*)::int FROM group_memberships m JOIN access_users u ON m.user_id=u.id WHERE group_id=:group AND firebase_subject=:subject")
            .param("group", group).param("subject", member).query(Int::class.java).single())
        assertEquals("EXPIRED", mapper.readTree(request("GET", "/api/groups/$group/trial").body())["status"].stringValue())
    }

    @Test
    fun `active trial cannot grant an additional administrator`() {
        val group = createGroup()
        val member = UUID.randomUUID().toString()
        request("GET", "/subscriptions/trial", actor = member)
        jdbc().sql("INSERT INTO group_memberships (group_id,user_id,role,created_at,updated_at) SELECT :group,id,'ATHLETE',now(),now() FROM access_users WHERE firebase_subject=:subject")
            .param("group", group).param("subject", member).update()
        val user = jdbc().sql("SELECT id FROM access_users WHERE firebase_subject=:subject").param("subject", member).query(UUID::class.java).single()
        val result = request("PUT", "/api/groups/$group/memberships/$user/role", """{"role":"ADMIN"}""")
        assertEquals(403, result.statusCode())
        assertEquals("SUBSCRIPTION_REQUIRED", mapper.readTree(result.body())["code"].stringValue())
        assertEquals("ATHLETE", jdbc().sql("SELECT role::text FROM group_memberships WHERE group_id=:group AND user_id=:user")
            .param("group", group).param("user", user).query(String::class.java).single())
    }

    private fun jdbc() = JdbcClient.create(ds)

    @Test
    fun `replacing a deleted trial group uses remaining time instead of a new fourteen days`() {
        val group = createGroup()
        val original = mapper.readTree(request("GET", "/subscriptions/trial").body())
        clock.now = now.plus(Duration.ofDays(2))
        assertEquals(204, request("DELETE", "/api/groups/$group").statusCode())
        val replacement = createGroup()
        assertFalse(group == replacement)
        val current = mapper.readTree(request("GET", "/api/groups/$replacement/trial").body())
        assertEquals(original["startedAt"], current["startedAt"])
        assertEquals(original["endsAt"], current["endsAt"])
        assertEquals("ACTIVE", current["status"].stringValue())
        assertEquals(1, jdbc().sql("SELECT count(*)::int FROM organizer_trials t JOIN access_users u ON t.owner_user_id=u.id WHERE firebase_subject=:subject")
            .param("subject", token).query(Int::class.java).single())
    }

    @Test
    fun `future game works during trial freezes attendance at expiry and resumes only after paid confirmation`() {
        val group = createGroup()
        val game = UUID.randomUUID()
        val gamePayload = """{
            "requestId":"$game","title":"Jogo depois do teste","venue":{"name":"Arena Central","address":"Rua Central 100"},
            "localDate":"2026-09-27","localTime":"12:00:00","zoneId":"UTC","startsAt":"2026-09-27T12:00:00Z",
            "durationMinutes":90,"capacity":25,"confirmationDeadline":"2026-09-27T11:00:00Z"}"""
        val created = request("POST", "/api/groups/$group/games", gamePayload)
        assertEquals(201, created.statusCode(), created.body())
        assertEquals("2026-09-27T12:00:00Z", mapper.readTree(created.body())["startsAt"].stringValue())
        assertEquals("2026-09-26T12:00:00Z", mapper.readTree(request("GET", "/api/groups/$group/trial").body())["endsAt"].stringValue())
        val published = request("POST", "/api/groups/$group/games/$game/publish", ifMatch = "\"1\"")
        assertEquals(200, published.statusCode(), published.body())
        jdbc().sql("UPDATE group_memberships SET membership_type='MENSALISTA' WHERE group_id=:group").param("group", group).update()
        val confirmed = request("PUT", "/api/groups/$group/games/$game/attendance", """{"requestId":"${UUID.randomUUID()}","intent":"CONFIRM"}""")
        assertEquals(200, confirmed.statusCode(), confirmed.body())
        val state = jdbc().sql("SELECT status::text FROM game_attendance WHERE game_id=:game").param("game", game).query(String::class.java).single()
        assertEquals("CONFIRMED", state)
        clock.now = now.plus(Duration.ofDays(14))
        val declined = request("PUT", "/api/groups/$group/games/$game/attendance", """{"requestId":"${UUID.randomUUID()}","intent":"DECLINE"}""")
        assertEquals(403, declined.statusCode(), declined.body())
        assertEquals("SUBSCRIPTION_REQUIRED", mapper.readTree(declined.body())["code"].stringValue())
        assertEquals("CONFIRMED", jdbc().sql("SELECT status::text FROM game_attendance WHERE game_id=:game").param("game", game).query(String::class.java).single())
        assertEquals(1, jdbc().sql("SELECT count(*)::int FROM attendance_events WHERE game_id=:game").param("game", game).query(Int::class.java).single())
        assertEquals(200, request("GET", "/api/groups/$group/games/$game/attendance").statusCode())
        // Explicit checkout exists but no confirmed payment: still expired, never implicitly paid.
        jdbc().sql("""INSERT INTO subscriptions (owner_user_id,plan,cycle,status,asaas_customer_id,asaas_subscription_id,current_period_end,created_at,updated_at)
            SELECT id,'TITULAR','MONTHLY','PAST_DUE','customer',:sub,now()+interval '30 days',now(),now() FROM access_users WHERE firebase_subject=:subject""")
            .param("sub", UUID.randomUUID().toString()).param("subject", token).update()
        assertEquals("EXPIRED", mapper.readTree(request("GET", "/api/groups/$group/trial").body())["status"].stringValue())
        assertEquals(403, request("PUT", "/api/groups/$group/games/$game/attendance", """{"requestId":"${UUID.randomUUID()}","intent":"DECLINE"}""").statusCode())
        // Mirror the persisted state emitted by the existing verified PAYMENT_CONFIRMED processor.
        jdbc().sql("UPDATE subscriptions SET status='ACTIVE', first_confirmed_at=now() WHERE owner_user_id=(SELECT id FROM access_users WHERE firebase_subject=:subject)")
            .param("subject", token).update()
        assertEquals("SUBSCRIBED", mapper.readTree(request("GET", "/api/groups/$group/trial").body())["status"].stringValue())
        val paidResponse = request("PUT", "/api/groups/$group/games/$game/attendance", """{"requestId":"${UUID.randomUUID()}","intent":"DECLINE"}""")
        assertEquals(200, paidResponse.statusCode(), paidResponse.body())
        assertEquals("DECLINED", jdbc().sql("SELECT status::text FROM game_attendance WHERE game_id=:game").param("game", game).query(String::class.java).single())
        assertEquals(2, jdbc().sql("SELECT count(*)::int FROM attendance_events WHERE game_id=:game").param("game", game).query(Int::class.java).single())
    }

    @Test
    fun `game fee transport keeps absent and true as default and honors explicit false override`() {
        val group = createGroup()
        jdbc().sql("UPDATE access_groups SET default_game_fee_cents=2500 WHERE id=:group").param("group", group).update()
        fun createGame(id: UUID, date: String, extra: String) = request(
            "POST", "/api/groups/$group/games", """{
                "requestId":"$id","title":"Fee $id","venue":{"name":"Arena","address":"Rua Central 100"},
                "localDate":"$date","localTime":"12:00:00","zoneId":"UTC","startsAt":"${date}T12:00:00Z",
                "durationMinutes":90,"capacity":25,"confirmationDeadline":"${date}T11:00:00Z"$extra}"""
        )
        val absent = createGame(UUID.randomUUID(), "2026-09-20", "")
        assertEquals(201, absent.statusCode(), absent.body())
        assertEquals(2500, mapper.readTree(absent.body())["gameFeeCents"].intValue())
        val explicitTrue = createGame(UUID.randomUUID(), "2026-09-21", ",\"useDefaultGameFee\":true")
        assertEquals(201, explicitTrue.statusCode(), explicitTrue.body())
        assertEquals(2500, mapper.readTree(explicitTrue.body())["gameFeeCents"].intValue())
        val explicitFalse = createGame(UUID.randomUUID(), "2026-09-22", ",\"useDefaultGameFee\":false,\"gameFeeCents\":4100")
        assertEquals(201, explicitFalse.statusCode(), explicitFalse.body())
        assertEquals(4100, mapper.readTree(explicitFalse.body())["gameFeeCents"].intValue())
    }

    @Test
    fun `expired trial refuses multipart group photo uploads`() {
        val group = createGroup()
        clock.now = now.plus(Duration.ofDays(14))
        val body = "--trial-boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"photo.png\"\r\nContent-Type: image/png\r\n\r\nimage\r\n--trial-boundary--\r\n"
        val result = request("PUT", "/api/groups/$group/photo", body, contentType = "multipart/form-data; boundary=trial-boundary")
        assertEquals(403, result.statusCode(), result.body())
        assertEquals("SUBSCRIPTION_REQUIRED", mapper.readTree(result.body())["code"].stringValue())
        assertEquals(1L, jdbc().sql("SELECT version FROM access_groups WHERE id=:group").param("group", group).query(Long::class.java).single())
    }

    @Test
    fun `monthly scheduler cannot generate charges for an expired trial`() {
        val group = createGroup()
        jdbc().sql("UPDATE access_groups SET monthly_fee_cents=2000, monthly_due_day=1 WHERE id=:group").param("group", group).update()
        jdbc().sql("UPDATE group_memberships SET membership_type='MENSALISTA' WHERE group_id=:group").param("group", group).update()
        clock.now = now.plus(Duration.ofDays(14))
        monthlySchedule.run()
        assertEquals(0, jdbc().sql("SELECT count(*)::int FROM group_charges WHERE group_id=:group").param("group", group).query(Int::class.java).single())
        assertEquals(0, jdbc().sql("SELECT count(*)::int FROM group_charge_events WHERE group_id=:group").param("group", group).query(Int::class.java).single())
    }

    @Test
    fun `pending invite request cannot be renewed after trial expiration`() {
        val group = createGroup()
        jdbc().sql("UPDATE access_groups SET entry_requires_approval=true WHERE id=:group").param("group", group).update()
        val invite = request("POST", "/api/groups/$group/invite")
        assertEquals(200, invite.statusCode(), invite.body())
        val url = URI(mapper.readTree(invite.body())["inviteUrl"].stringValue())
        val code = url.rawQuery.split('&').first { it.startsWith("saqz_invite=") }.substringAfter('=')
        val member = UUID.randomUUID().toString()
        val pending = request("POST", "/api/invites/redeem", """{"code":"$code"}""", actor = member)
        assertEquals(200, pending.statusCode(), pending.body())
        assertEquals("PENDING", mapper.readTree(pending.body())["status"].stringValue())
        val original = jdbc().sql("SELECT requested_at FROM group_entry_requests WHERE group_id=:group").param("group", group).query(java.sql.Timestamp::class.java).single()
        // Keep the invitation valid to isolate the trial guard, rather than the link's own expiry.
        jdbc().sql("UPDATE group_invites SET expires_at=:end WHERE group_id=:group").param("end", java.sql.Timestamp.from(now.plus(Duration.ofDays(30)))).param("group", group).update()
        clock.now = now.plus(Duration.ofDays(14))
        val retry = request("POST", "/api/invites/redeem", """{"code":"$code"}""", actor = member)
        assertEquals(403, retry.statusCode(), retry.body())
        assertEquals("SUBSCRIPTION_REQUIRED", mapper.readTree(retry.body())["code"].stringValue())
        assertEquals(original, jdbc().sql("SELECT requested_at FROM group_entry_requests WHERE group_id=:group").param("group", group).query(java.sql.Timestamp::class.java).single())
        assertEquals(1, jdbc().sql("SELECT count(*)::int FROM group_memberships WHERE group_id=:group").param("group", group).query(Int::class.java).single())
    }

    private fun request(method: String, path: String, body: String? = null, actor: String? = token, ifMatch: String? = null, contentType: String = "application/json"): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path"))
            .header("Content-Type", contentType)
            .method(method, body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody())
        actor?.let { builder.header("Authorization", "Bearer $it") }
        ifMatch?.let { builder.header("If-Match", it) }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    class TrialClock(var now: Instant = Instant.parse("2026-09-12T12:00:00Z")) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }

    @TestConfiguration(proxyBeanMethods = false)
    class Fixture {
        @Bean @Primary fun trialClock() = TrialClock()
        @Bean @Primary fun verifier() = VerifyRequestIdentity {
            TokenVerification.Verified(RequestIdentity(it.value, "${it.value}@example.test", true, "Trial User"))
        }
    }

    companion object {
        private val database = TestPostgres.empty()
        @JvmStatic @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { database.jdbcUrl }
            registry.add("spring.datasource.username") { database.username }
            registry.add("spring.datasource.password") { database.password }
            registry.add("saqz.firebase.emulator.enabled") { "true" }
            registry.add("saqz.branch.domain") { "https://join.test" }
            registry.add("saqz.password-reset.secret") { "segredo-de-teste-com-trinta-e-dois" }
        }
    }
}
