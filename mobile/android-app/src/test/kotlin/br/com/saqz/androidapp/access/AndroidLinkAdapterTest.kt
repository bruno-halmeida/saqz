package br.com.saqz.androidapp.access

import br.com.saqz.access.domain.port.InviteCodeListener
import br.com.saqz.access.domain.port.AppOnboardingCodeListener
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidLinkAdapterTest {
    @Test
    fun reopeningAttendanceLinkDeliversAgainButBranchCopyDoesNot() {
        val fixture = Fixture()
        val events = mutableListOf<br.com.saqz.groups.port.GroupLinkEvent>()
        fixture.adapter.start(object : br.com.saqz.groups.port.GroupLinkEventListener {
            override fun onEvent(event: br.com.saqz.groups.port.GroupLinkEvent) { events += event }
        })
        val url = "https://links.saqz.app/attendance/$CODE_A"
        fixture.adapter.onColdStart(url)
        fixture.branch.complete(mapOf("saqz_attendance" to CODE_A))
        fixture.adapter.onWarmIntent(url)
        fixture.branch.complete(mapOf("saqz_attendance" to CODE_A))
        assertEquals(List(2) { br.com.saqz.groups.port.GroupLinkEvent.Attendance(CODE_A) }, events)
    }

    @Test
    fun declineParameterSelectsTheDeclineIntentAndAbsenceConfirms() {
        val fixture = Fixture()
        val events = mutableListOf<br.com.saqz.groups.port.GroupLinkEvent>()
        fixture.adapter.start(object : br.com.saqz.groups.port.GroupLinkEventListener {
            override fun onEvent(event: br.com.saqz.groups.port.GroupLinkEvent) { events += event }
        })
        fixture.adapter.onColdStart("https://links.saqz.app/attendance/$CODE_A?saqz_intent=decline")
        fixture.adapter.onWarmIntent("https://links.saqz.app/?saqz_attendance=$CODE_A")
        assertEquals(
            listOf(
                br.com.saqz.groups.port.GroupLinkEvent.Attendance(CODE_A, AttendanceIntent.Decline),
                br.com.saqz.groups.port.GroupLinkEvent.Attendance(CODE_A, AttendanceIntent.Confirm),
            ),
            events,
        )
    }

    @Test
    fun unknownIntentConfirmsAndDuplicatedIntentIsRejected() {
        val fixture = Fixture()
        val events = mutableListOf<br.com.saqz.groups.port.GroupLinkEvent>()
        fixture.adapter.start(object : br.com.saqz.groups.port.GroupLinkEventListener {
            override fun onEvent(event: br.com.saqz.groups.port.GroupLinkEvent) { events += event }
        })
        fixture.adapter.onColdStart("https://links.saqz.app/attendance/$CODE_A?saqz_intent=maybe")
        fixture.adapter.onWarmIntent("https://links.saqz.app/attendance/$CODE_A?saqz_intent=decline&saqz_intent=decline")
        assertEquals(listOf(br.com.saqz.groups.port.GroupLinkEvent.Attendance(CODE_A)), events)
    }

    @Test
    fun coldAppLinkDeliversOnlyOpaqueInviteCode() {
        val fixture = Fixture()
        fixture.start()

        fixture.adapter.onColdStart("https://links.saqz.app/invite?saqz_invite=$CODE_A&groupId=secret")

        assertEquals(listOf(CODE_A), fixture.received)
        assertEquals(listOf("cold:https://links.saqz.app/invite?saqz_invite=$CODE_A&groupId=secret"), fixture.branch.calls)
    }

    @Test
    fun deferredBranchColdResultDeliversCodeWithoutIntentData() {
        val fixture = Fixture()
        fixture.start()

        fixture.adapter.onColdStart(null)
        fixture.branch.complete(mapOf("saqz_invite" to CODE_A, "groupId" to "secret"))

        assertEquals(listOf(CODE_A), fixture.received)
    }

    @Test
    fun warmAppLinkUsesReinitializedBranchSession() {
        val fixture = Fixture()
        fixture.start()

        fixture.adapter.onWarmIntent("https://links.saqz.app/invite?saqz_invite=$CODE_B")

        assertEquals(listOf(CODE_B), fixture.received)
        assertEquals(listOf("warm:https://links.saqz.app/invite?saqz_invite=$CODE_B"), fixture.branch.calls)
    }

    @Test
    fun warmBranchResultDeliversCodeWhenUrlHasNoQuery() {
        val fixture = Fixture()
        fixture.start()

        fixture.adapter.onWarmIntent("https://links.saqz.app/opaque-route")
        fixture.branch.complete(mapOf("saqz_invite" to CODE_B))

        assertEquals(listOf(CODE_B), fixture.received)
    }

    @Test
    fun intentWithoutBranchOrInviteCodeIsNoOp() {
        val fixture = Fixture()
        fixture.start()

        fixture.adapter.onColdStart(null)
        fixture.branch.complete(emptyMap())

        assertTrue(fixture.received.isEmpty())
    }

    @Test
    fun unrelatedParametersNeverBecomeInviteCodes() {
        val fixture = Fixture()
        fixture.start()

        fixture.adapter.onColdStart("https://links.saqz.app/invite?groupId=$CODE_A&email=person%40example.test")
        fixture.branch.complete(mapOf("groupId" to CODE_A, "email" to "person@example.test"))

        assertTrue(fixture.received.isEmpty())
    }

    @Test
    fun invalidBase64UrlAlphabetPaddingAndLengthAreRejected() {
        val fixture = Fixture()
        fixture.start()

        listOf("short", "${"A".repeat(42)}+", "${"A".repeat(42)}=", "${"A".repeat(42)}B")
            .forEach { fixture.adapter.onWarmIntent("https://links.saqz.app/invite?saqz_invite=$it") }

        assertTrue(fixture.received.isEmpty())
    }

    @Test
    fun nonHttpsDirectIntentIsRejected() {
        val fixture = Fixture()
        fixture.start()

        fixture.adapter.onWarmIntent("saqz://invite?saqz_invite=$CODE_A")

        assertTrue(fixture.received.isEmpty())
    }

    @Test
    fun directAndBranchCopiesOfSameEventAreDeliveredOnce() {
        val fixture = Fixture()
        fixture.start()

        fixture.adapter.onColdStart("https://links.saqz.app/invite?saqz_invite=$CODE_A")
        fixture.branch.complete(mapOf("saqz_invite" to CODE_A))

        assertEquals(listOf(CODE_A), fixture.received)
    }

    @Test
    fun repeatedBranchCopiesOfSameEventAreDeliveredOnce() {
        val fixture = Fixture()
        fixture.start()

        fixture.adapter.onColdStart(null)
        fixture.branch.complete(mapOf("saqz_invite" to CODE_A))
        fixture.branch.complete(mapOf("saqz_invite" to CODE_A))

        assertEquals(listOf(CODE_A), fixture.received)
    }

    @Test
    fun newerWarmLinkAfterDuplicateIsDelivered() {
        val fixture = Fixture()
        fixture.start()

        fixture.adapter.onWarmIntent("https://links.saqz.app/invite?saqz_invite=$CODE_A")
        fixture.branch.complete(mapOf("saqz_invite" to CODE_A))
        fixture.adapter.onWarmIntent("https://links.saqz.app/invite?saqz_invite=$CODE_B")

        assertEquals(listOf(CODE_A, CODE_B), fixture.received)
    }

    @Test
    fun latestEventBeforeListenerWins() {
        val fixture = Fixture()

        fixture.adapter.onColdStart("https://links.saqz.app/invite?saqz_invite=$CODE_A")
        fixture.adapter.onWarmIntent("https://links.saqz.app/invite?saqz_invite=$CODE_B")
        fixture.start()

        assertEquals(listOf(CODE_B), fixture.received)
    }

    @Test
    fun cancellationStopsDeliveryWithoutStoppingBranchLifecycle() {
        val fixture = Fixture()
        val subscription = fixture.start()
        subscription.cancel()

        fixture.adapter.onWarmIntent("https://links.saqz.app/invite?saqz_invite=$CODE_A")

        assertTrue(fixture.received.isEmpty())
        assertEquals(1, fixture.branch.calls.size)
    }

    @Test
    fun deferredBranchAttendanceResultDeliversAttendanceEvent() {
        val fixture = Fixture()
        val events = mutableListOf<br.com.saqz.groups.port.GroupLinkEvent>()
        fixture.adapter.start(object : br.com.saqz.groups.port.GroupLinkEventListener {
            override fun onEvent(event: br.com.saqz.groups.port.GroupLinkEvent) { events += event }
        })

        fixture.adapter.onColdStart(null)
        fixture.branch.complete(mapOf("saqz_attendance" to CODE_A))

        assertEquals(listOf(br.com.saqz.groups.port.GroupLinkEvent.Attendance(CODE_A)), events)
    }

    @Test
    fun branchParametersWithBothInviteAndAttendanceAreRejected() {
        val fixture = Fixture()
        val events = mutableListOf<br.com.saqz.groups.port.GroupLinkEvent>()
        fixture.adapter.start(object : br.com.saqz.groups.port.GroupLinkEventListener {
            override fun onEvent(event: br.com.saqz.groups.port.GroupLinkEvent) { events += event }
        })

        fixture.adapter.onColdStart(null)
        fixture.branch.complete(mapOf("saqz_invite" to CODE_A, "saqz_attendance" to CODE_B))

        assertTrue(events.isEmpty())
    }

    @Test
    fun onboardingCodeUsesSeparateListenerAndDeduplicatesDirectAndBranch() {
        val fixture = Fixture()
        fixture.startOnboarding()

        fixture.adapter.onColdStart("https://links.saqz.app/?%24deeplink_path=onboarding&saqz_onboarding=$CODE_A")
        fixture.branch.complete(mapOf("saqz_onboarding" to CODE_A))

        assertEquals(listOf(CODE_A), fixture.onboardingReceived)
        assertTrue(fixture.received.isEmpty())
    }

    @Test
    fun onboardingRejectsMixedParamsAndUntrustedHost() {
        val fixture = Fixture()
        fixture.startOnboarding()

        fixture.adapter.onWarmIntent("https://links.saqz.app/?saqz_onboarding=$CODE_A&saqz_invite=$CODE_B")
        fixture.adapter.onWarmIntent("https://evil.example/?saqz_onboarding=$CODE_A")

        assertTrue(fixture.onboardingReceived.isEmpty())
    }

    @Test
    fun deferredOnboardingBeforeListenerAndNewWarmCodeAreDelivered() {
        val fixture = Fixture()
        fixture.adapter.onColdStart(null)
        fixture.branch.complete(mapOf("saqz_onboarding" to CODE_A))
        fixture.startOnboarding()
        fixture.adapter.onWarmIntent("https://links.saqz.app/?saqz_onboarding=$CODE_B")
        assertEquals(listOf(CODE_A, CODE_B), fixture.onboardingReceived)
    }

    @Test
    fun onboardingUsesConfiguredHostAndRejectsDuplicateParameters() {
        val fixture = Fixture(setOf("configured.app.link"))
        fixture.startOnboarding()
        fixture.adapter.onWarmIntent("https://links.saqz.app/?saqz_onboarding=$CODE_A")
        fixture.adapter.onWarmIntent("https://configured.app.link/?saqz_onboarding=$CODE_A&saqz_onboarding=$CODE_A")
        assertTrue(fixture.onboardingReceived.isEmpty())
        fixture.adapter.onWarmIntent("https://configured.app.link/?saqz_onboarding=$CODE_A")
        assertEquals(listOf(CODE_A), fixture.onboardingReceived)
    }

    @Test
    fun notificationTapIsBufferedUntilListenerAndDeliveredOnEveryTap() {
        val fixture = Fixture()
        fixture.adapter.onNotificationOpen("group-1")
        val events = mutableListOf<br.com.saqz.groups.port.GroupLinkEvent>()
        fixture.adapter.start(object : br.com.saqz.groups.port.GroupLinkEventListener {
            override fun onEvent(event: br.com.saqz.groups.port.GroupLinkEvent) { events += event }
        })
        fixture.adapter.onNotificationOpen(null)

        assertEquals(
            listOf(
                br.com.saqz.groups.port.GroupLinkEvent.NotificationOpen("group-1"),
                br.com.saqz.groups.port.GroupLinkEvent.NotificationOpen(null),
            ),
            events,
        )
    }

    private class Fixture(allowedHosts: Set<String> = setOf("links.saqz.app")) {
        val branch = FakeBranchSessionClient()
        val adapter = AndroidLinkAdapter(branch, allowedHosts)
        val received = mutableListOf<String>()
        val onboardingReceived = mutableListOf<String>()

        fun start() = adapter.start(object : InviteCodeListener {
            override fun onInviteCode(code: String) {
                received += code
            }
        })

        fun startOnboarding() = adapter.startAppOnboarding(object : AppOnboardingCodeListener {
            override fun onAppOnboardingCode(code: String) {
                onboardingReceived += code
            }
        })
    }

    private class FakeBranchSessionClient : AndroidBranchSessionClient {
        val calls = mutableListOf<String>()
        private var callback: ((Map<String, String?>) -> Unit)? = null

        override fun initialize(url: String?, callback: (Map<String, String?>) -> Unit) {
            calls += "cold:$url"
            this.callback = callback
        }

        override fun reinitialize(url: String?, callback: (Map<String, String?>) -> Unit) {
            calls += "warm:$url"
            this.callback = callback
        }

        fun complete(parameters: Map<String, String?>) = callback!!.invoke(parameters)
    }

    private companion object {
        const val CODE_A = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        const val CODE_B = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBE"
    }
}
