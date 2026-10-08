package br.com.saqz.androidapp.access

import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.port.GroupLinkEvent
import br.com.saqz.groups.port.GroupLinkEventListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidInstallReferrerTest {
    private val code = "A".repeat(43)
    private val now = 1_800_000_000L
    private val domain = "links.saqz.app"

    private fun link(referrer: String?, installedSecondsAgo: Long = 60) =
        AndroidInstallReferrer.deferredLink(referrer, now - installedSecondsAgo, now, domain)

    /** O que o app faria com o link, pelo mesmo caminho de um link tocado. */
    private fun deliver(url: String?): List<GroupLinkEvent> {
        val events = mutableListOf<GroupLinkEvent>()
        val adapter = AndroidLinkAdapter(allowedHosts = setOf(domain))
        adapter.start(object : GroupLinkEventListener {
            override fun onEvent(event: GroupLinkEvent) {
                events += event
            }
        })
        adapter.onWarmIntent(url)
        return events
    }

    @Test fun inviteFromTheLinksPageBecomesTheSameLinkTheAppAlreadyHandles() {
        val url = link("saqz_invite=$code")
        assertEquals("https://links.saqz.app/?saqz_invite=$code", url)
        assertEquals(listOf<GroupLinkEvent>(GroupLinkEvent.Invite(code)), deliver(url))
    }

    @Test fun attendanceFromTheLinksPageConfirmsOrDeclinesLikeATappedLink() {
        val confirm = link("saqz_attendance=$code")
        assertEquals("https://links.saqz.app/?saqz_attendance=$code", confirm)
        assertEquals(listOf<GroupLinkEvent>(GroupLinkEvent.Attendance(code, AttendanceIntent.Confirm)), deliver(confirm))

        val decline = link("saqz_attendance%3D$code%26saqz_intent%3Ddecline")
        assertEquals("https://links.saqz.app/?saqz_attendance=$code&saqz_intent=decline", decline)
        assertEquals(listOf<GroupLinkEvent>(GroupLinkEvent.Attendance(code, AttendanceIntent.Decline)), deliver(decline))
    }

    @Test fun encodedReferrerAndOnboardingAreAccepted() {
        assertEquals("https://links.saqz.app/?saqz_invite=$code", link("saqz_invite%3D$code"))
        assertEquals("https://links.saqz.app/?saqz_onboarding=$code", link("saqz_onboarding=$code"))
    }

    @Test fun organicOrMixedReferrerIsIgnored() {
        assertNull(link("utm_source=google-play&utm_medium=organic"))
        assertNull(link("saqz_invite=$code&utm_source=google-play"))
        assertNull(link("saqz_invite=$code&saqz_intent=decline"))
        assertNull(link("saqz_attendance=$code&saqz_intent=maybe"))
        assertNull(link("saqz_attendance=$code&saqz_intent=decline&utm_source=google-play"))
        assertNull(link(""))
        assertNull(link(null))
    }

    @Test fun oldInstallsDoNotReplayTheInvite() {
        assertNull(link("saqz_invite=$code", installedSecondsAgo = 8L * 24 * 60 * 60))
        assertNull(AndroidInstallReferrer.deferredLink("saqz_invite=$code", 0, now, domain))
    }
}
