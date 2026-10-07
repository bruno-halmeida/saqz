package br.com.saqz.androidapp.access

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
        AndroidInstallReferrer.inviteLink(referrer, now - installedSecondsAgo, now, domain)

    @Test fun inviteFromTheLinksPageBecomesTheSameLinkTheAppAlreadyHandles() {
        val url = link("saqz_invite=$code")
        assertEquals("https://links.saqz.app/?saqz_invite=$code", url)
        val events = mutableListOf<GroupLinkEvent>()
        val adapter = AndroidLinkAdapter(allowedHosts = setOf(domain))
        adapter.start(object : GroupLinkEventListener {
            override fun onEvent(event: GroupLinkEvent) {
                events += event
            }
        })
        adapter.onWarmIntent(url)
        assertEquals(listOf<GroupLinkEvent>(GroupLinkEvent.Invite(code)), events)
    }

    @Test fun encodedReferrerAndOnboardingAreAccepted() {
        assertEquals("https://links.saqz.app/?saqz_invite=$code", link("saqz_invite%3D$code"))
        assertEquals("https://links.saqz.app/?saqz_onboarding=$code", link("saqz_onboarding=$code"))
    }

    @Test fun organicOrMixedReferrerIsIgnored() {
        assertNull(link("utm_source=google-play&utm_medium=organic"))
        assertNull(link("saqz_invite=$code&utm_source=google-play"))
        assertNull(link("saqz_attendance=$code"))
        assertNull(link(""))
        assertNull(link(null))
    }

    @Test fun oldInstallsDoNotReplayTheInvite() {
        assertNull(link("saqz_invite=$code", installedSecondsAgo = 8L * 24 * 60 * 60))
        assertNull(AndroidInstallReferrer.inviteLink("saqz_invite=$code", 0, now, domain))
    }
}
