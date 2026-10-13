package br.com.saqz.webapp.groups

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebAppDownloadAdapterTest {
    private val android = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/130 Mobile Safari/537.36"
    private val iPhone = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_2 like Mac OS X) AppleWebKit/605.1.15 Safari/604.1"
    private val desktop = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Safari/605.1.15"

    @Test
    fun androidBrowserGetsTheGooglePlayPage() {
        assertEquals("https://play.google.com/store/apps/details?id=app.saqz", appDownloadUrlFor(android))
        assertEquals(GOOGLE_PLAY_URL, WebAppDownloadAdapter { android }.appDownloadUrl())
    }

    @Test
    fun iPhoneHasNothingToOfferUntilTheAppStoreIsSet() {
        assertNull(appDownloadUrlFor(iPhone))
        assertNull(WebAppDownloadAdapter { iPhone }.appDownloadUrl())
        assertEquals("https://apps.apple.com/br/app/id1", appDownloadUrlFor(iPhone, "https://apps.apple.com/br/app/id1"))
    }

    @Test
    fun desktopNeverGetsAnOffer() {
        assertNull(appDownloadUrlFor(desktop))
        assertNull(appDownloadUrlFor(desktop, "https://apps.apple.com/br/app/id1"))
        assertNull(appDownloadUrlFor(""))
    }
}
