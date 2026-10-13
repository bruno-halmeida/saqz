package br.com.saqz.androidapp

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.subscriptions.domain.trial.TrialAccess
import br.com.saqz.subscriptions.domain.trial.TrialStatus
import br.com.saqz.subscriptions.presentation.trial.*
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import org.junit.Assert.assertEquals

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class TrialEntryScreenshotTest {
    @get:Rule val compose = createComposeRule()
    @Test fun trialEntryStatesAndActions() {
        val state = mutableStateOf(TrialEntryState())
        val intents = mutableListOf<TrialEntryIntent>()
        compose.setContent { SaqzTheme { TrialEntryScreen(state.value, intents::add, {}, {}) } }
        capture("loading")
        val access = TrialAccess(TrialStatus.Available, null, null, "2026-09-15T12:00:00Z",
            false, true, 3, null, true, null, canRedeemCoupon = true)
        compose.runOnIdle { state.value = TrialEntryState(loading = false, failure = TrialEntryFailure.Load) }
        compose.onNodeWithTag(TrialEntryTags.Error).assertExists()
        compose.onNodeWithTag(TrialEntryTags.Refresh).performClick()
        assertEquals(TrialEntryIntent.Refresh, intents.last())
        capture("network-error")
        compose.runOnIdle { state.value = TrialEntryState(loading = false, access = access.copy(
            status = TrialStatus.Ineligible, canCreateGroup = false, offerMode = "COUPON_ONLY")) }
        compose.onNodeWithText("EXPERIMENTE O ORGANIZADOR").assertDoesNotExist()
        compose.onNodeWithTag(TrialEntryTags.Refresh).assertExists()
        capture("web-access-required")
    }
    private fun capture(name:String) {
        compose.waitForIdle()
        val file=File("../build/reports/trial-entry/$name.png");file.parentFile.mkdirs()
        compose.onRoot().captureRoboImage(file.absolutePath)
    }
}
