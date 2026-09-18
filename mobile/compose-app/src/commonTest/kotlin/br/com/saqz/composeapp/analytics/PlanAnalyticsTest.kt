package br.com.saqz.composeapp.analytics

import br.com.saqz.core.common.analytics.SaqzAnalytics
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.trial.TrialAccess
import br.com.saqz.subscriptions.domain.trial.TrialError
import br.com.saqz.subscriptions.domain.trial.TrialGateway
import br.com.saqz.subscriptions.domain.trial.TrialStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlanAnalyticsTest {
    private val events = mutableListOf<Pair<String, Map<String, String>>>()
    private val properties = mutableListOf<Pair<String, String?>>()

    @BeforeTest
    fun install() {
        SaqzAnalytics.track = { name, params -> events += name to params }
        SaqzAnalytics.setProperty = { name, value -> properties += name to value }
    }

    @AfterTest
    fun reset() = SaqzAnalytics.reset()

    @Test
    fun `refresh publishes plan state and reports trial_started on the available to active transition`() = runTest {
        val gateway = FakeTrialGateway(access(TrialStatus.Available, coupon = "VOLEI30"))
        val analytics = PlanAnalytics(gateway, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        analytics.refresh()
        gateway.next = access(TrialStatus.Active, startedAt = "2026-09-01T12:00:00Z", coupon = "VOLEI30")
        analytics.refresh()
        analytics.refresh()

        assertEquals(
            listOf("trial_started" to mapOf("trial_days" to "14", "coupon" to "VOLEI30")),
            events,
        )
        assertEquals("plan_state" to "available", properties.first())
        assertEquals("plan_state" to "active", properties.last { it.first == "plan_state" })
        assertEquals("VOLEI30", properties.last { it.first == "acquisition_coupon" }.second)
    }

    @Test
    fun `purchased reports days in trial and the coupon`() = runTest {
        val gateway = FakeTrialGateway(
            access(TrialStatus.Subscribed, startedAt = "2026-09-01T12:00:00Z", serverTime = "2026-09-04T18:00:00Z", coupon = "VOLEI30"),
        )
        val analytics = PlanAnalytics(gateway, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        analytics.purchased()

        assertEquals(listOf("purchase" to mapOf("coupon" to "VOLEI30", "days_in_trial" to "3")), events)
        assertEquals("plan_state" to "subscribed", properties.first())
    }

    @Test
    fun `purchased still reports when the trial lookup fails`() = runTest {
        val analytics = PlanAnalytics(FakeTrialGateway(null), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

        analytics.purchased()

        assertEquals(listOf("purchase" to emptyMap<String, String>()), events)
    }

    @Test
    fun `daysInTrial is null without a start or with an unreadable date`() {
        assertNull(daysInTrial(access(TrialStatus.Available)))
        assertNull(daysInTrial(access(TrialStatus.Active, startedAt = "ontem")))
    }

    private fun access(
        status: TrialStatus,
        startedAt: String? = null,
        serverTime: String = "2026-09-02T12:00:00Z",
        coupon: String? = null,
    ) = TrialAccess(
        status = status,
        startedAt = startedAt,
        endsAt = null,
        serverTime = serverTime,
        readOnly = false,
        canCreateGroup = true,
        maxGroups = 1,
        maxAthletes = null,
        isOwner = true,
        appUrl = null,
        selectedCouponCode = coupon,
    )

    private class FakeTrialGateway(var next: TrialAccess?) : TrialGateway {
        override suspend fun ownerTrial(): SaqzResult<TrialAccess, TrialError> =
            next?.let { SaqzResult.Success(it) } ?: SaqzResult.Failure(TrialError.NotFound)

        override suspend fun groupTrial(groupId: GroupId): SaqzResult<TrialAccess, TrialError> =
            SaqzResult.Failure(TrialError.NotFound)
    }
}
