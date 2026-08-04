package br.com.saqz.adminweb.http

import br.com.saqz.access.application.admin.AdminAccessStats
import br.com.saqz.groups.application.admin.AdminGroupStats
import br.com.saqz.subscriptions.application.AdminRevenueStats
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

data class DeltaResponse(val current: Long, val previous: Long?)

data class ChurnResponse(val canceled: Long, val activeAtStart: Long)

data class CohortWeekResponse(
    val weekStart: LocalDate,
    val signups: Long,
    val joinedGroup: Long,
    val subscribed: Long,
)

data class PlanSplitResponse(val plan: String, val subscribers: Long, val mrrCents: Long)

data class AdminOverviewResponse(
    val period: String,
    val totalUsers: Long,
    val activeUsers30d: Long,
    val activeGroups: Long,
    val newUsers: DeltaResponse,
    val groupsCreated: DeltaResponse,
    val gamesPlayed: DeltaResponse,
    val revenueCents: DeltaResponse,
    val churn: ChurnResponse,
    val cohort: List<CohortWeekResponse>,
    val planSplit: List<PlanSplitResponse>,
)

/**
 * Composição da visão geral do adm-web sobre as portas administrativas das três
 * features. Vive no módulo bootstrap porque é o único que enxerga todas — e fora
 * do pacote br.com.saqz.bootstrap para não entrar no component scan (a fiação é
 * explícita, via bean em PlatformAdminConfiguration).
 */
@RestController
class AdminOverviewController(
    private val accessStats: AdminAccessStats,
    private val groupStats: AdminGroupStats,
    private val revenueStats: AdminRevenueStats,
    private val now: () -> Instant = Instant::now,
) {
    @GetMapping("/admin/overview")
    fun overview(
        @RequestParam(defaultValue = "30d") period: String,
    ): ResponseEntity<AdminOverviewResponse> {
        val window = Duration.ofDays(
            when (period) {
                "30d" -> 30L
                "90d" -> 90L
                "all" -> 0L
                else -> return ResponseEntity.badRequest().build()
            },
        )
        val to = now()
        val from = if (window.isZero) null else to.minus(window)
        val previousFrom = from?.minus(window)

        fun delta(count: (Instant?, Instant) -> Long) = DeltaResponse(
            current = count(from, to),
            previous = if (from == null) null else count(previousFrom, from),
        )

        val signupCohort = accessStats.signupCohort(COHORT_WEEKS, to)
        val subscribedByWeek = revenueStats.subscribedCohort(COHORT_WEEKS, to).associateBy { it.weekStart }

        return ResponseEntity.ok(
            AdminOverviewResponse(
                period = period,
                totalUsers = accessStats.totalUsers(),
                activeUsers30d = accessStats.activeUsers(to.minus(Duration.ofDays(30))),
                activeGroups = groupStats.activeGroups(),
                newUsers = delta(accessStats::newUsers),
                groupsCreated = delta(groupStats::groupsCreated),
                gamesPlayed = delta(groupStats::gamesPlayed),
                revenueCents = delta(revenueStats::revenueCents),
                churn = revenueStats.churn(from, to).let { ChurnResponse(it.canceled, it.activeAtStart) },
                cohort = signupCohort.map { week ->
                    CohortWeekResponse(
                        weekStart = week.weekStart,
                        signups = week.signups,
                        joinedGroup = week.joinedGroup,
                        subscribed = subscribedByWeek[week.weekStart]?.subscribed ?: 0,
                    )
                },
                planSplit = revenueStats.planSplit().map {
                    PlanSplitResponse(it.plan.name, it.subscribers, it.mrrCents)
                },
            ),
        )
    }

    private companion object {
        const val COHORT_WEEKS = 5
    }
}
