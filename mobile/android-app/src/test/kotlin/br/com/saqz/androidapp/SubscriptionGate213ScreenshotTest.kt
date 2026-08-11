package br.com.saqz.androidapp

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.composeapp.subscriptiongate.SubscriptionGateFailure
import br.com.saqz.composeapp.subscriptiongate.SubscriptionGateScreen
import br.com.saqz.composeapp.subscriptiongate.SubscriptionGateState
import br.com.saqz.composeapp.subscriptiongate.SubscriptionGateStatus
import br.com.saqz.designsystem.theme.SaqzTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = android.app.Application::class,
)
class SubscriptionGate213ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun mandatoryStates() {
        val currentState = mutableStateOf(scenes.first().second)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SaqzTheme {
                SubscriptionGateScreen(state = currentState.value, onIntent = {}, onBack = {})
            }
        }
        compose.waitForIdle()
        scenes.forEach { (name, state) ->
            compose.runOnIdle {
                currentState.value = state
            }
            compose.waitForIdle()
            capture(name)
        }
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(SHUTTER_MILLIS)
        compose.onRoot().captureRoboImage("screenshots/vul-213/$name.png")
    }

    private companion object {
        const val SHUTTER_MILLIS = 600L
        val scenes = listOf(
            "inicial" to SubscriptionGateState(),
            "enviando" to SubscriptionGateState(status = SubscriptionGateStatus.Sending),
            "enviado" to SubscriptionGateState(
                status = SubscriptionGateStatus.Sent,
                maskedEmail = "a***a@exemplo.com",
            ),
            "falha-envio" to SubscriptionGateState(
                status = SubscriptionGateStatus.Failed,
                failure = SubscriptionGateFailure.PurchaseInformation,
            ),
            "falha-verificacao" to SubscriptionGateState(
                status = SubscriptionGateStatus.Failed,
                failure = SubscriptionGateFailure.Authorization,
            ),
            "verificando" to SubscriptionGateState(status = SubscriptionGateStatus.Verifying),
            "nao-autorizado" to SubscriptionGateState(status = SubscriptionGateStatus.NotAuthorized),
            "autorizado-finalizando" to SubscriptionGateState(status = SubscriptionGateStatus.Authorized),
        )
    }
}
