package br.com.saqz.bootstrap

import br.com.saqz.access.application.admin.AdminAccessStats
import br.com.saqz.access.application.admin.CohortWeek
import br.com.saqz.access.application.admin.PlatformAdminLookup
import br.com.saqz.access.application.admin.PlatformAdminView
import br.com.saqz.adminweb.http.AdminOverviewController
import br.com.saqz.groups.application.admin.AdminGroupStats
import br.com.saqz.identity.application.TokenVerification
import br.com.saqz.identity.application.VerifyRequestIdentity
import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.subscriptions.application.AdminRevenueStats
import br.com.saqz.subscriptions.application.ChurnStats
import br.com.saqz.subscriptions.application.PlanSplitEntry
import br.com.saqz.subscriptions.application.SubscribedCohortWeek
import br.com.saqz.subscriptions.domain.Plan
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.beans.factory.annotation.Autowired
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
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AdminOverviewEndpointIntegrationTest.OverviewTestConfiguration::class)
@ActiveProfiles("test")
@TestPropertySource(properties = ["saqz.firebase.emulator.enabled=true"])
class AdminOverviewEndpointIntegrationTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val client: HttpClient = HttpClient.newHttpClient()

    @Test
    fun `overview compoe os numeros das tres portas com comparativo`() {
        val response = get("/admin/overview?period=30d", token = "admin-token")
        val body = objectMapper.readTree(response.body())

        assertEquals(200, response.statusCode())
        assertEquals("30d", body["period"].stringValue())
        assertEquals(642, body["totalUsers"].intValue())
        assertEquals(418, body["activeUsers30d"].intValue())
        assertEquals(61, body["activeGroups"].intValue())
        assertEquals(58, body["newUsers"]["current"].intValue())
        assertEquals(58, body["newUsers"]["previous"].intValue())
        assertEquals(236, body["gamesPlayed"]["current"].intValue())
        assertEquals(183_600, body["revenueCents"]["current"].intValue())
        assertEquals(3, body["churn"]["canceled"].intValue())
        assertEquals(55, body["churn"]["activeAtStart"].intValue())
        assertEquals(5, body["cohort"].size())
        assertEquals("2026-07-06", body["cohort"][0]["weekStart"].stringValue())
        assertEquals(16, body["cohort"][0]["signups"].intValue())
        assertEquals(3, body["cohort"][0]["joinedGroup"].intValue())
        assertEquals(2, body["cohort"][0]["subscribed"].intValue())
        assertEquals("ORGANIZADOR", body["planSplit"][0]["plan"].stringValue())
        assertEquals(41, body["planSplit"][0]["subscribers"].intValue())
        assertEquals(245_590, body["planSplit"][0]["mrrCents"].intValue())
    }

    @Test
    fun `periodo all nao carrega comparativo`() {
        val response = get("/admin/overview?period=all", token = "admin-token")
        val body = objectMapper.readTree(response.body())

        assertEquals(200, response.statusCode())
        assertTrue(body["newUsers"]["previous"].isNull)
        assertTrue(body["revenueCents"]["previous"].isNull)
    }

    @Test
    fun `periodo desconhecido retorna 400`() {
        assertEquals(400, get("/admin/overview?period=7d", token = "admin-token").statusCode())
    }

    @Test
    fun `usuario comum nao passa da guarda`() {
        assertEquals(403, get("/admin/overview", token = "user-token").statusCode())
    }

    private fun get(path: String, token: String?): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET()
        if (token != null) builder.header("Authorization", "Bearer $token")
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    @TestConfiguration(proxyBeanMethods = false)
    class OverviewTestConfiguration {
        @Bean
        @Primary
        fun overviewVerifier(): VerifyRequestIdentity = VerifyRequestIdentity {
            when (it.value) {
                "admin-token" -> TokenVerification.Verified(RequestIdentity("admin-subject", "admin@saqz.test", true))
                "user-token" -> TokenVerification.Verified(RequestIdentity("user-subject", "user@saqz.test", true))
                else -> TokenVerification.Rejected
            }
        }

        @Bean
        fun overviewLookup(): PlatformAdminLookup = PlatformAdminLookup { subject ->
            if (subject == "admin-subject") PlatformAdminView(UUID.randomUUID(), "admin@saqz.test", "Ana Admin") else null
        }

        @Bean
        fun overviewController(): AdminOverviewController = AdminOverviewController(
            accessStats = StubAccessStats(),
            groupStats = StubGroupStats(),
            revenueStats = StubRevenueStats(),
            now = { Instant.parse("2026-08-03T12:00:00Z") },
        )
    }

    class StubAccessStats : AdminAccessStats {
        override fun totalUsers() = 642L
        override fun newUsers(from: Instant?, to: Instant) = 58L
        override fun activeUsers(since: Instant) = 418L
        override fun signupCohort(weeksBack: Int, now: Instant): List<CohortWeek> =
            weeks(weeksBack, now).map { CohortWeek(it, signups = 16, joinedGroup = 3) }
    }

    class StubGroupStats : AdminGroupStats {
        override fun activeGroups() = 61L
        override fun groupsCreated(from: Instant?, to: Instant) = 7L
        override fun gamesPlayed(from: Instant?, to: Instant) = 236L
    }

    class StubRevenueStats : AdminRevenueStats {
        override fun revenueCents(from: Instant?, to: Instant) = 183_600L
        override fun churn(from: Instant?, to: Instant) = ChurnStats(canceled = 3, activeAtStart = 55)
        override fun planSplit() = listOf(PlanSplitEntry(Plan.ORGANIZADOR, subscribers = 41, mrrCents = 245_590))
        override fun subscribedCohort(weeksBack: Int, now: Instant): List<SubscribedCohortWeek> =
            weeks(weeksBack, now).map { SubscribedCohortWeek(it, subscribed = 2) }
    }

    private companion object {
        fun weeks(weeksBack: Int, now: Instant): List<LocalDate> {
            val monday = LocalDate.ofInstant(now, java.time.ZoneOffset.UTC).with(java.time.DayOfWeek.MONDAY)
            return ((weeksBack - 1).toLong() downTo 0L).map { monday.minusWeeks(it) }
        }
    }
}
