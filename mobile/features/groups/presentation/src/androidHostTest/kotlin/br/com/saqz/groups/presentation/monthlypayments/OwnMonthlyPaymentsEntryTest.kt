package br.com.saqz.groups.presentation.monthlypayments

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
            val folder = File("../../../build/reports/member-payment-entry").apply { mkdirs() }
            compose.onRoot().captureRoboImage(File(folder, "entry-$index.png").path)
        }
    }
}
