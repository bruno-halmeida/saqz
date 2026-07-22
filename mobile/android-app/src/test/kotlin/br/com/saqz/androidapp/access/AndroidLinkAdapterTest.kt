package br.com.saqz.androidapp.access

import br.com.saqz.access.port.InviteCodeListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidLinkAdapterTest {
    @Test
    fun coldAppLinkDeliversOnlyOpaqueInviteCode() {
        val fixture = Fixture()
        fixture.start()

        fixture.adapter.onColdStart("https://saqz.test-app.link/invite?saqz_invite=$CODE_A&groupId=secret")

        assertEquals(listOf(CODE_A), fixture.received)
        assertEquals(listOf("cold:https://saqz.test-app.link/invite?saqz_invite=$CODE_A&groupId=secret"), fixture.branch.calls)
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

        fixture.adapter.onWarmIntent("https://saqz.test-app.link/invite?saqz_invite=$CODE_B")

        assertEquals(listOf(CODE_B), fixture.received)
        assertEquals(listOf("warm:https://saqz.test-app.link/invite?saqz_invite=$CODE_B"), fixture.branch.calls)
    }

    @Test
    fun warmBranchResultDeliversCodeWhenUrlHasNoQuery() {
        val fixture = Fixture()
        fixture.start()

        fixture.adapter.onWarmIntent("https://saqz.test-app.link/opaque-route")
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

        fixture.adapter.onColdStart("https://saqz.test-app.link/invite?groupId=$CODE_A&email=person%40example.test")
        fixture.branch.complete(mapOf("groupId" to CODE_A, "email" to "person@example.test"))

        assertTrue(fixture.received.isEmpty())
    }

    @Test
    fun invalidBase64UrlAlphabetPaddingAndLengthAreRejected() {
        val fixture = Fixture()
        fixture.start()

        listOf("short", "${"A".repeat(42)}+", "${"A".repeat(42)}=", "${"A".repeat(42)}B")
            .forEach { fixture.adapter.onWarmIntent("https://saqz.test-app.link/invite?saqz_invite=$it") }

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

        fixture.adapter.onColdStart("https://saqz.test-app.link/invite?saqz_invite=$CODE_A")
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

        fixture.adapter.onWarmIntent("https://saqz.test-app.link/invite?saqz_invite=$CODE_A")
        fixture.branch.complete(mapOf("saqz_invite" to CODE_A))
        fixture.adapter.onWarmIntent("https://saqz.test-app.link/invite?saqz_invite=$CODE_B")

        assertEquals(listOf(CODE_A, CODE_B), fixture.received)
    }

    @Test
    fun latestEventBeforeListenerWins() {
        val fixture = Fixture()

        fixture.adapter.onColdStart("https://saqz.test-app.link/invite?saqz_invite=$CODE_A")
        fixture.adapter.onWarmIntent("https://saqz.test-app.link/invite?saqz_invite=$CODE_B")
        fixture.start()

        assertEquals(listOf(CODE_B), fixture.received)
    }

    @Test
    fun cancellationStopsDeliveryWithoutStoppingBranchLifecycle() {
        val fixture = Fixture()
        val subscription = fixture.start()
        subscription.cancel()

        fixture.adapter.onWarmIntent("https://saqz.test-app.link/invite?saqz_invite=$CODE_A")

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

    private class Fixture {
        val branch = FakeBranchSessionClient()
        val adapter = AndroidLinkAdapter(branch)
        val received = mutableListOf<String>()

        fun start() = adapter.start(object : InviteCodeListener {
            override fun onInviteCode(code: String) {
                received += code
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
