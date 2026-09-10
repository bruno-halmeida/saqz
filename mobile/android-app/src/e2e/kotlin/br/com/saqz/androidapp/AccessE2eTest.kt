package br.com.saqz.androidapp

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** APP-R01: login -> persisted session -> logout -> different account, real backend. */
@RunWith(AndroidJUnit4::class)
internal class AccessE2eTest : InstalledE2e("access") {
    @Test
    fun loginRecreationLogoutAndAccountIsolation() {
        login("athlete")
        groups()
        waitTag("group-list-group-$group")
        ui.onNodeWithTag("group-list-group-$group").assertIsDisplayed()
        ui.onNodeWithTag("group-list-group-$secondGroup").assertIsDisplayed()
        assertEquals(setOf(group, secondGroup), membershipIds("athlete"))

        ui.activityRule.scenario.recreate()
        waitTag("saqz-shell-content")
        assertEquals(actor("athlete").getString("uid"), auth.currentUser?.uid)
        groups()
        waitTag("group-list-group-$group")
        ui.onNodeWithTag("group-list-group-$group").assertIsDisplayed()

        logout()
        login("other")
        groups()
        waitTag("group-list-group-$secondGroup")
        ui.onNodeWithTag("group-list-group-$secondGroup").assertIsDisplayed()
        ui.onNodeWithTag("group-list-group-$group").assertDoesNotExist()
        assertEquals(setOf(secondGroup), membershipIds("other"))
    }

    /** APP-R02 credentials subset; network failure intentionally not claimed. */
    @Test
    fun invalidPasswordCannotOpenProtectedContentAndCorrectionWorks() {
        enterCredentials("athlete", "Definitely-wrong-password")
        ui.waitUntil(20_000) {
            ui.onAllNodesWithText("E-mail ou senha incorretos. Confira os dados e tente de novo.").fetchSemanticsNodes().size == 1
        }
        ui.onNodeWithText("E-mail ou senha incorretos. Confira os dados e tente de novo.").assertIsDisplayed()
        ui.onNodeWithTag("saqz-shell-content").assertDoesNotExist()
        assertNull(auth.currentUser)
        login("athlete")
        groups()
        waitTag("group-list-group-$group")
        ui.onNodeWithTag("group-list-group-$group").assertIsDisplayed()
        assertEquals(setOf(group, secondGroup), membershipIds("athlete"))
    }
}
