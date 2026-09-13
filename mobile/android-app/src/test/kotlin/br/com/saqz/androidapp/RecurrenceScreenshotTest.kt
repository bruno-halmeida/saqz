package br.com.saqz.androidapp

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.receivables.domain.*
import br.com.saqz.receivables.presentation.*
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = Application::class)
class RecurrenceScreenshotTest {
    @get:Rule val compose = createComposeRule()
    @Test fun recurringJourneyStates() {
        val state = mutableStateOf(RecurrenceState("account", "group"))
        compose.setContent { SaqzTheme { RecurrenceScreen(state.value, {}, {}) } }
        capture("recorrencia-carregando")
        compose.runOnIdle { state.value = RecurrenceState("account", "group", loading = false, firstDueDate = "2026-10-10") }
        capture("recorrencia-vazia")
        compose.runOnIdle { state.value = state.value.copy(review = review, terms = ReceiptTerms("terms", "Termos publicados"),
            name = "Pessoa Teste", document = "12345678909") }
        compose.onNodeWithTag(RecurrenceTags.Submit).performScrollTo(); capture("recorrencia-revisao")
        compose.runOnIdle { state.value = state.value.copy(accepted = true) }; capture("recorrencia-aceite")
        compose.runOnIdle { state.value = RecurrenceState("account", "group", loading = false, pending = true,
            recurrence = recurrence.copy(status = "STOP_PENDING", cutoffAt = "2026-09-13T12:00:00Z"),
            error = ReceiptError.UNCERTAIN) }
        compose.onNodeWithTag(RecurrenceTags.Status).performScrollTo(); capture("recorrencia-cancelamento-pendente")
        compose.runOnIdle { state.value = state.value.copy(pending = false, error = null,
            recurrence = recurrence.copy(status = "STOPPED", cutoffAt = "2026-09-13T12:00:00Z")) }
        capture("recorrencia-encerrada-retomada")
        compose.runOnIdle { state.value = RecurrenceState("account", "group", loading = false,
            recurrence = recurrence.copy(status = "AUTHORIZING", method = ReceiptMethod.CARD,
                hostedCheckoutUrl = "https://asaas.com/c/demo")) }
        compose.onNodeWithTag(RecurrenceTags.Checkout).performScrollTo(); capture("recorrencia-cartao-checkout")
    }
    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(400); compose.waitForIdle()
        val directory = File("../build/reports/recurrence-ui").apply { mkdirs() }
        compose.onNodeWithTag(RecurrenceTags.Screen).captureRoboImage(File(directory, "$name.png").path)
    }
    private val review = RecurrenceReview("account", "group", "payer", ReceiptMethod.PIX, 10_000, 490, 10_490, 300, 190,
        10_000, "schedule", "terms", "2026-10-10", "MONTHLY", "a".repeat(64))
    private val recurrence = PaymentRecurrence("recurrence", "account", "group", "payer", ReceiptMethod.PIX, 10_000, 490,
        10_490, "2026-10-10", "ACTIVE", "subscription", null, null)
}
