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
    @Test fun couponEntryStatesAndActions() {
        val state=mutableStateOf(TrialEntryState())
        val intents=mutableListOf<TrialEntryIntent>()
        compose.setContent { SaqzTheme { TrialEntryScreen(state.value,intents::add,{},{}) } }
        capture("loading")
        val access=TrialAccess(TrialStatus.Ineligible,null,null,"2026-09-13T12:00:00Z",false,false,1,25,true,null,
            offerMode="COUPON_ONLY",canRedeemCoupon=true)
        compose.runOnIdle { state.value=TrialEntryState(loading=false,access=access) }
        compose.onNodeWithText("Tem um cupom de teste grátis?").assertExists()
        compose.onNodeWithTag(TrialEntryTags.Continue).assertDoesNotExist()
        compose.onNodeWithTag(TrialEntryTags.Apply).assertIsNotEnabled()
        capture("coupon-only")
        compose.runOnIdle { state.value=state.value.copy(code="ARENA") }
        compose.onNodeWithTag(TrialEntryTags.Apply).performClick()
        assertEquals(listOf<TrialEntryIntent>(TrialEntryIntent.Apply),intents)
        compose.runOnIdle { state.value=state.value.copy(access=access.copy(status=TrialStatus.Available,
            canCreateGroup=true,selectedCouponCode="ARENA",trialDays=45)) }
        compose.onNodeWithText("45 dias para experimentar").assertExists()
        compose.onNodeWithTag(TrialEntryTags.Continue).performClick()
        assertEquals(TrialEntryIntent.Continue,intents.last())
        capture("coupon-45-days")
        compose.runOnIdle { state.value=state.value.copy(loading=true) }
        compose.onNodeWithTag(TrialEntryTags.Apply).assertIsNotEnabled()
        compose.onNodeWithTag(TrialEntryTags.Continue).assertIsNotEnabled()
        capture("applying")
        compose.runOnIdle { state.value=state.value.copy(loading=false,failure=TrialEntryFailure.Coupon) }
        compose.onNodeWithTag(TrialEntryTags.Error).assertExists()
        compose.onNodeWithTag(TrialEntryTags.Continue).assertIsNotEnabled()
        capture("invalid-coupon")
        compose.runOnIdle { state.value=TrialEntryState(loading=false,access=access.copy(offerMode="OFF",canRedeemCoupon=false)) }
        compose.onNodeWithTag(TrialEntryTags.Code).assertDoesNotExist()
        compose.onNodeWithTag(TrialEntryTags.Continue).assertDoesNotExist()
        compose.onNodeWithText("O teste grátis está indisponível no momento.",substring=true).assertExists()
        capture("off")
        compose.runOnIdle { state.value=TrialEntryState(loading=false,failure=TrialEntryFailure.Load) }
        capture("network-error")
        compose.runOnIdle { state.value=TrialEntryState(loading=false,access=access.copy(status=TrialStatus.Available,
            offerMode="ON",canCreateGroup=true,trialDays=14)) }
        compose.onNodeWithText("14 dias para experimentar").assertExists()
        capture("public-14-days")
    }
    private fun capture(name:String) {
        compose.waitForIdle()
        val file=File("../build/reports/trial-entry/$name.png");file.parentFile.mkdirs()
        compose.onRoot().captureRoboImage(file.absolutePath)
    }
}
