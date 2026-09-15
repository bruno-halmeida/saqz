package br.com.saqz.groups.presentation.monthlypayments

import br.com.saqz.groups.presentation.details.OwnChargesUi
import br.com.saqz.groups.presentation.details.OwnChargeUi
import br.com.saqz.groups.presentation.details.OwnChargeStatusUi
import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.GroupUiError
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class OwnMonthlyPaymentsEntryTest {
    @get:Rule val compose = createComposeRule()
    @Test fun unreleasedPaymentsStayHiddenWhileMonthlyChargesLoadOrFail() {
        val state = mutableStateOf(OwnMonthlyPaymentsState())
        compose.setContent { SaqzTheme { OwnMonthlyPaymentsScreen(state.value, {}, {}) } }
        listOf(OwnMonthlyPaymentsState(), OwnMonthlyPaymentsState(loading = false),
            OwnMonthlyPaymentsState(loading = false, error = GroupUiError.Network)).forEachIndexed { index, next ->
            compose.runOnIdle { state.value = next }
            compose.onNodeWithTag(OwnMonthlyPaymentsTags.PayInApp).assertDoesNotExist()
            if (next.loading) assertLoadingCentered()
            val folder = File("../../../build/reports/member-payment-entry").apply { mkdirs() }
            compose.onRoot().captureRoboImage(File(folder, "entry-hidden-$index.png").path)
        }
    }

    @Test fun paymentEntryRemainsAvailableWithoutLoadedOrCurrentGroups() {
        val state = mutableStateOf(OwnMonthlyPaymentsState()); var opened = 0
        compose.setContent { SaqzTheme { OwnMonthlyPaymentsScreen(state.value, {}, {}, { opened++ }) } }
        listOf(OwnMonthlyPaymentsState(), OwnMonthlyPaymentsState(loading = false),
            OwnMonthlyPaymentsState(loading = false, error = GroupUiError.Network)).forEachIndexed { index, next ->
            compose.runOnIdle { state.value = next }
            compose.onNodeWithTag(OwnMonthlyPaymentsTags.PayInApp).assertIsDisplayed().assertIsEnabled().performClick()
            assertEquals(index + 1, opened)
            if (next.loading) assertLoadingCentered()
            val folder = File("../../../build/reports/member-payment-entry").apply { mkdirs() }
            compose.onRoot().captureRoboImage(File(folder, "entry-$index.png").path)
        }
    }
    private fun assertLoadingCentered() {
        val content = compose.onNodeWithTag(OwnMonthlyPaymentsTags.Content).fetchSemanticsNode().boundsInRoot
        val spinner = compose.onNodeWithTag(OwnMonthlyPaymentsTags.Loading).fetchSemanticsNode().boundsInRoot
        assertEquals(content.center.x, spinner.center.x, 1f)
        assertEquals(content.center.y, spinner.center.y, 1f)
    }

    @Test fun loadedMonthlyPaymentsShowPendingAndHistoryAndOpenCorrectGroup() {
        val intents = mutableListOf<OwnMonthlyPaymentsIntent>()
        val state = OwnMonthlyPaymentsState(loading = false, groups = listOf(
            MonthlyPaymentsGroupUi("quinta", "Futebol de quinta", OwnChargesUi(
                pending = listOf(OwnChargeUi("sep", "Setembro de 2026", "Vence em 10/09/2026", "R$ 80,00", OwnChargeStatusUi.Pending)),
                history = listOf(OwnChargeUi("aug", "Agosto de 2026", "Vence em 10/08/2026", "R$ 80,00", OwnChargeStatusUi.Paid)),
            )),
        ))
        compose.setContent { SaqzTheme { OwnMonthlyPaymentsScreen(state, {}, intents::add) } }
        compose.onNodeWithText("Setembro de 2026").assertExists()
        compose.onNodeWithText("Agosto de 2026").assertExists()
        val folder = File("../../../build/reports/member-payment-entry").apply { mkdirs() }
        compose.onRoot().captureRoboImage(File(folder, "loaded.png").path)
        compose.onNodeWithTag(OwnMonthlyPaymentsTags.group("quinta")).performScrollTo().performClick()
        assertEquals(listOf(OwnMonthlyPaymentsIntent.OpenGroup("quinta")), intents)
    }

}
