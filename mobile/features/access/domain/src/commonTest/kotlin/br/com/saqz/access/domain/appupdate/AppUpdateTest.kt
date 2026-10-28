package br.com.saqz.access.domain.appupdate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppUpdateTest {
    private val nothingDismissed: (String) -> Boolean = { false }

    @Test fun `compares each part as a number`() {
        assertTrue(AppVersionNumber.parse("1.0.10")!! > AppVersionNumber.parse("1.0.9")!!)
        assertTrue(AppVersionNumber.parse("2.0.0")!! > AppVersionNumber.parse("1.99.99")!!)
        assertEquals(0, AppVersionNumber.parse("1.0.15")!!.compareTo(AppVersionNumber.parse("1.0.15")!!))
    }

    @Test fun `rejects anything that is not N_N_N`() {
        listOf("", "1.0", "1.0.0.1", "1.0.x", "v1.0.0", "1.0.0-dev").forEach { assertNull(AppVersionNumber.parse(it), it) }
    }

    @Test fun `below the minimum is required with the minimum compared numerically`() {
        assertEquals(AppUpdatePrompt.Required, decideAppUpdate("1.0.9", "1.0.10", null, nothingDismissed))
    }

    @Test fun `equal to the minimum does not block`() {
        assertEquals(AppUpdatePrompt.None, decideAppUpdate("1.0.16", "1.0.16", null, nothingDismissed))
    }

    @Test fun `an installed version out of format never shows anything`() {
        assertEquals(AppUpdatePrompt.None, decideAppUpdate("1.0.15-dev", "9.9.9", "9.9.9", nothingDismissed))
    }

    @Test fun `a minimum or recommended out of format counts as absent`() {
        assertEquals(AppUpdatePrompt.None, decideAppUpdate("1.0.15", "1.0", "abc", nothingDismissed))
        assertEquals(AppUpdatePrompt.Recommended("1.0.16"), decideAppUpdate("1.0.15", "x", "1.0.16", nothingDismissed))
    }

    @Test fun `required wins over recommended`() {
        assertEquals(AppUpdatePrompt.Required, decideAppUpdate("1.0.15", "1.0.16", "1.0.17", nothingDismissed))
    }

    @Test fun `a dismissed recommended does not come back`() {
        assertEquals(AppUpdatePrompt.None, decideAppUpdate("1.0.15", null, "1.0.16") { it == "1.0.16" })
    }

    @Test fun `a higher recommended comes back once`() {
        assertEquals(AppUpdatePrompt.Recommended("1.0.17"), decideAppUpdate("1.0.15", null, "1.0.17") { it == "1.0.16" })
    }

    @Test fun `at or above the recommended shows nothing`() {
        assertEquals(AppUpdatePrompt.None, decideAppUpdate("1.0.16", "1.0.12", "1.0.16", nothingDismissed))
    }
}
