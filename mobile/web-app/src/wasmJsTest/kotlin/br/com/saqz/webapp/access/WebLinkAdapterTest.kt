package br.com.saqz.webapp.access

import br.com.saqz.access.domain.port.AppOnboardingCodeListener
import br.com.saqz.access.domain.port.InviteCodeListener
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.port.GroupLinkEvent
import br.com.saqz.groups.port.GroupLinkEventListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Suppress("TooManyFunctions") // um caso por regra do AndroidLinkAdapterTest
class WebLinkAdapterTest {
    @Test
    fun inviteInQueryWorksOnAnyPathOfTheAppAndOfTheLinksHost() {
        val fixture = Fixture().apply { listen() }

        fixture.adapter.onColdStart("https://saqz.app/app/?saqz_invite=$CODE_A")
        fixture.adapter.onColdStart("https://saqz.app/app/index.html?saqz_invite=$CODE_B")
        fixture.adapter.onColdStart("https://links.saqz.app/invite?saqz_invite=$CODE_A&groupId=secret")

        assertEquals(listOf(CODE_A, CODE_B, CODE_A), fixture.inviteCodes)
        assertEquals<List<GroupLinkEvent>>(
            listOf(GroupLinkEvent.Invite(CODE_A), GroupLinkEvent.Invite(CODE_B), GroupLinkEvent.Invite(CODE_A)),
            fixture.groupEvents,
        )
    }

    @Test
    fun shortGroupCodeIsDeliveredNormalizedAndAmbiguousOnesAreRejected() {
        val fixture = Fixture().apply { listen() }

        listOf("44DF8SA8", "44DF-8SA8", "44df-8sa8").forEach {
            fixture.adapter.onColdStart("https://saqz.app/app/?saqz_invite=$it")
        }
        listOf("44DF8SA0", "44DF8SAO", "44DF8SA", "44DF8SA88").forEach {
            fixture.adapter.onColdStart("https://saqz.app/app/?saqz_invite=$it")
        }

        assertEquals(listOf("44DF8SA8", "44DF8SA8", "44DF8SA8"), fixture.inviteCodes)
    }

    @Test
    fun attendanceInQueryWorksAndDeclineSelectsTheDeclineIntent() {
        val fixture = Fixture().apply { listen() }

        fixture.adapter.onColdStart("https://saqz.app/app/?saqz_attendance=$CODE_A")
        fixture.adapter.onColdStart("https://saqz.app/app/?saqz_attendance=$CODE_A&saqz_intent=decline")
        fixture.adapter.onColdStart("https://saqz.app/app/?saqz_attendance=$CODE_A&saqz_intent=maybe")

        assertEquals<List<GroupLinkEvent>>(
            listOf(
                GroupLinkEvent.Attendance(CODE_A, AttendanceIntent.Confirm),
                GroupLinkEvent.Attendance(CODE_A, AttendanceIntent.Decline),
                GroupLinkEvent.Attendance(CODE_A, AttendanceIntent.Confirm),
            ),
            fixture.groupEvents,
        )
        assertTrue(fixture.inviteCodes.isEmpty())
    }

    @Test
    fun attendanceInThePathOnlyCountsOnTheLinksHost() {
        val fixture = Fixture().apply { listen() }

        fixture.adapter.onColdStart("https://links.saqz.app/attendance/$CODE_A")
        fixture.adapter.onColdStart("https://links.saqz.app/attendance/$CODE_B?saqz_intent=decline")
        fixture.adapter.onColdStart("https://saqz.app/attendance/$CODE_A")
        fixture.adapter.onColdStart("https://saqz.app/app/attendance/$CODE_A")

        assertEquals<List<GroupLinkEvent>>(
            listOf(
                GroupLinkEvent.Attendance(CODE_A, AttendanceIntent.Confirm),
                GroupLinkEvent.Attendance(CODE_B, AttendanceIntent.Decline),
            ),
            fixture.groupEvents,
        )
    }

    @Test
    fun attendanceInThePathRejectsACompetingCodeInTheQuery() {
        val fixture = Fixture().apply { listen() }

        fixture.adapter.onColdStart("https://links.saqz.app/attendance/$CODE_A?saqz_invite=$CODE_B")
        fixture.adapter.onColdStart("https://links.saqz.app/attendance/$CODE_A?saqz_attendance=$CODE_B")

        assertTrue(fixture.groupEvents.isEmpty())
    }

    @Test
    fun onboardingUsesItsOwnListenerAndNeverTheInviteOne() {
        val fixture = Fixture().apply { listen() }

        fixture.adapter.onColdStart("https://saqz.app/app/?%24deeplink_path=onboarding&saqz_onboarding=$CODE_A")

        assertEquals(listOf(CODE_A), fixture.onboardingCodes)
        assertTrue(fixture.inviteCodes.isEmpty())
        assertTrue(fixture.groupEvents.isEmpty())
    }

    @Test
    fun onboardingRejectsMixedAndDuplicatedParameters() {
        val fixture = Fixture().apply { listen() }

        fixture.adapter.onColdStart("https://saqz.app/app/?saqz_onboarding=$CODE_A&saqz_invite=$CODE_B")
        fixture.adapter.onColdStart("https://saqz.app/app/?saqz_onboarding=$CODE_A&saqz_attendance=$CODE_B")
        fixture.adapter.onColdStart("https://saqz.app/app/?saqz_onboarding=$CODE_A&saqz_onboarding=$CODE_A")

        assertTrue(fixture.onboardingCodes.isEmpty())
        assertTrue(fixture.inviteCodes.isEmpty())
    }

    @Test
    fun foreignHostsSchemesPortsAndUserinfoAreRejected() {
        val fixture = Fixture().apply { listen() }
        val query = "saqz_invite=$CODE_A"

        listOf(
            "https://evil.example/app/?$query",
            "https://saqz.app.evil.example/app/?$query",
            "https://evil.example/@saqz.app/?$query",
            "http://saqz.app/app/?$query",
            "http://links.saqz.app/?$query",
            "saqz://invite?$query",
            "https://user:secret@saqz.app/app/?$query",
            "https://saqz.app:8443/app/?$query",
            "https://saqz.app/app/ ?$query",
            "not a url",
            "",
        ).forEach { fixture.adapter.onColdStart(it) }
        fixture.adapter.onColdStart(null)

        assertTrue(fixture.inviteCodes.isEmpty())
        assertTrue(fixture.groupEvents.isEmpty())
        assertTrue(fixture.addressChanges.isEmpty())
    }

    @Test
    fun hostMatchingIgnoresCase() {
        val fixture = Fixture().apply { listen() }

        fixture.adapter.onColdStart("HTTPS://SAQZ.APP/app/?saqz_invite=$CODE_A")

        assertEquals(listOf(CODE_A), fixture.inviteCodes)
    }

    @Test
    fun httpIsOnlyAcceptedOnLoopbackAndOnlyForTheAllowedAuthority() {
        val fixture = Fixture(setOf("links.saqz.app", "localhost:8787")).apply { listen() }

        fixture.adapter.onColdStart("http://localhost:8787/?saqz_invite=$CODE_A")
        fixture.adapter.onColdStart("http://localhost:9999/?saqz_invite=$CODE_B")
        fixture.adapter.onColdStart("http://localhost/?saqz_invite=$CODE_B")
        fixture.adapter.onColdStart("http://links.saqz.app/?saqz_invite=$CODE_B")

        assertEquals(listOf(CODE_A), fixture.inviteCodes)
    }

    @Test
    fun loopbackAddressAlsoWorksOverHttp() {
        val fixture = Fixture(setOf("127.0.0.1:8787")).apply { listen() }

        fixture.adapter.onColdStart("http://127.0.0.1:8787/?saqz_onboarding=$CODE_A")

        assertEquals(listOf(CODE_A), fixture.onboardingCodes)
    }

    @Test
    fun duplicatedMixedAndMalformedCodesAreRejected() {
        val fixture = Fixture().apply { listen() }
        val base = "https://saqz.app/app/?"

        listOf(
            "saqz_invite=$CODE_A&saqz_invite=$CODE_A",
            "saqz_attendance=$CODE_A&saqz_attendance=$CODE_B",
            "saqz_attendance=$CODE_A&saqz_intent=decline&saqz_intent=decline",
            "saqz_invite=$CODE_A&saqz_attendance=$CODE_B",
            "saqz_invite=short",
            "saqz_invite=${"A".repeat(42)}+",
            "saqz_invite=${"A".repeat(42)}=",
            "saqz_invite=${"A".repeat(42)}B",
            "saqz_invite=%ZZ",
            "groupId=$CODE_A&email=person%40example.test",
            "saqz_invite",
        ).forEach { fixture.adapter.onColdStart(base + it) }

        assertTrue(fixture.inviteCodes.isEmpty())
        assertTrue(fixture.groupEvents.isEmpty())
        assertTrue(fixture.addressChanges.isEmpty())
    }

    @Test
    fun percentEncodedCodesAreDecodedBeforeValidation() {
        val fixture = Fixture().apply { listen() }

        fixture.adapter.onColdStart("https://saqz.app/app/?saqz_invite=${CODE_B.dropLast(1)}%45")

        assertEquals(listOf(CODE_B), fixture.inviteCodes)
    }

    @Test
    fun eventsWaitForAListenerAndTheLatestOneWins() {
        val fixture = Fixture()

        fixture.adapter.onColdStart("https://saqz.app/app/?saqz_invite=$CODE_A")
        fixture.adapter.onColdStart("https://saqz.app/app/?saqz_invite=$CODE_B")
        fixture.adapter.onColdStart("https://saqz.app/app/?saqz_onboarding=$CODE_A")
        fixture.listen()

        assertEquals(listOf(CODE_B), fixture.inviteCodes)
        assertEquals<List<GroupLinkEvent>>(listOf(GroupLinkEvent.Invite(CODE_B)), fixture.groupEvents)
        assertEquals(listOf(CODE_A), fixture.onboardingCodes)
    }

    @Test
    fun cancellingStopsDelivery() {
        val fixture = Fixture()
        val subscriptions = fixture.listen()
        subscriptions.forEach { it() }

        fixture.adapter.onColdStart("https://saqz.app/app/?saqz_invite=$CODE_A")
        fixture.adapter.onColdStart("https://saqz.app/app/?saqz_onboarding=$CODE_A")

        assertTrue(fixture.inviteCodes.isEmpty())
        assertTrue(fixture.onboardingCodes.isEmpty())
        assertTrue(fixture.groupEvents.isEmpty())
    }

    @Test
    fun consumedParametersAreRemovedFromTheAddressKeepingPathAndTheRest() {
        val fixture = Fixture().apply { listen() }

        fixture.adapter.onColdStart("https://saqz.app/app/?saqz_invite=$CODE_A")
        fixture.adapter.onColdStart("https://saqz.app/app/index.html?utm_source=wa&saqz_invite=$CODE_A&x=1#top")
        fixture.adapter.onColdStart("https://saqz.app/app/?saqz_attendance=$CODE_A&saqz_intent=decline")
        fixture.adapter.onColdStart("https://saqz.app/app/?%24deeplink_path=onboarding&saqz_onboarding=$CODE_A")

        assertEquals(
            listOf(
                "/app/",
                "/app/index.html?utm_source=wa&x=1#top",
                "/app/",
                "/app/?%24deeplink_path=onboarding",
            ),
            fixture.addressChanges,
        )
    }

    @Test
    fun pathCodeOnTheLinksHostGoesBackToTheRoot() {
        val fixture = Fixture().apply { listen() }

        fixture.adapter.onColdStart("https://links.saqz.app/attendance/$CODE_A?saqz_intent=decline&keep=1")

        assertEquals(listOf("/?keep=1"), fixture.addressChanges)
    }

    @Test
    fun addressIsCleanedEvenWhenNoOneListensYet() {
        val fixture = Fixture()

        fixture.adapter.onColdStart("https://saqz.app/app/?saqz_invite=$CODE_A")

        assertEquals(listOf("/app/"), fixture.addressChanges)
    }

    @Test
    fun parserBuildsTheCleanedAddressOnlyForTrustedLinks() {
        val parser = WebLinkParser(setOf("links.saqz.app", "saqz.app"), "links.saqz.app")

        assertEquals("/app/", parser.cleanedAddress("https://saqz.app/app/?saqz_invite=$CODE_A"))
        assertNull(parser.cleanedAddress("https://evil.example/app/?saqz_invite=$CODE_A"))
        assertNull(parser.cleanedAddress(null))
        assertNull(parser.directEvent("https://saqz.app/app/"))
        assertNull(parser.directOnboardingCode("https://saqz.app/app/?saqz_invite=$CODE_A"))
    }

    private class Fixture(allowedHosts: Set<String> = setOf("links.saqz.app", "saqz.app")) {
        val inviteCodes = mutableListOf<String>()
        val onboardingCodes = mutableListOf<String>()
        val groupEvents = mutableListOf<GroupLinkEvent>()
        val addressChanges = mutableListOf<String>()
        val adapter = WebLinkAdapter(allowedHosts, "links.saqz.app") { addressChanges += it }

        /** Devolve os cancelamentos, na ordem convite, onboarding, grupo. */
        fun listen(): List<() -> Unit> {
            val invite = adapter.start(object : InviteCodeListener {
                override fun onInviteCode(code: String) {
                    inviteCodes += code
                }
            })
            val onboarding = adapter.startAppOnboarding(object : AppOnboardingCodeListener {
                override fun onAppOnboardingCode(code: String) {
                    onboardingCodes += code
                }
            })
            val group = adapter.start(object : GroupLinkEventListener {
                override fun onEvent(event: GroupLinkEvent) {
                    groupEvents += event
                }
            })
            return listOf(invite::cancel, onboarding::cancel, group::cancel)
        }
    }

    private companion object {
        const val CODE_A = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        const val CODE_B = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBE"
    }
}
