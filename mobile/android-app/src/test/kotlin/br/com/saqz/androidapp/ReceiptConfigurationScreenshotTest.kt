package br.com.saqz.androidapp

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
class ReceiptConfigurationScreenshotTest {
    @get:Rule val compose = createComposeRule()

    /** Gestor-Ativar v1: sem escolha de meio; cadastro, revisão (Pix e cartão juntos), aceite e desligar. */
    @Test fun configurationStates() {
        val state = mutableStateOf(ReceiptConfigurationState())
        compose.setContent { SaqzTheme { ReceiptConfigurationScreen(state.value, {}, {}) } }
        capture("carregando")
        show(state, ReceiptConfigurationState(loading = false), "sem-conta")
        compose.onNodeWithTag(ReceiptConfigurationTags.Registration).assertExists()
        AccountRegistration.entries.forEach { registration ->
            show(state, selected.copy(accounts = listOf(ReceiptAccount("00000000-0000-4000-8000-000000001234",
                registration, false))),
                "cadastro-${registration.name.lowercase()}")
        }
        ReceiptError.entries.forEach { error ->
            val failed = if (error == ReceiptError.SIGNED_OUT) ReceiptConfigurationState(loading = false, error = error)
                else selected.copy(error = error)
            show(state, failed, "erro-${error.name.lowercase()}")
        }
        show(state, selected.copy(pendingMutation = true, error = ReceiptError.UNCERTAIN), "operacao-pendente")
        show(state, selected.copy(discoveryAvailable = false), "rollout-off")
        show(state, selected, "pronto-para-revisar")
        compose.onNodeWithTag(ReceiptConfigurationTags.Preview).assertIsEnabled()
        compose.onNodeWithTag(ReceiptConfigurationTags.method(ReceiptMethod.PIX)).assertDoesNotExist()
        val reviewed = selected.copy(methods = ReceiptMethod.entries.toSet(), review = review,
            terms = listOf(ReceiptTerms("v1", "Termos de demonstração para revisão visual.")))
        show(state, reviewed, "revisao-sem-aceite")
        compose.onNodeWithTag(ReceiptConfigurationTags.Activate).assertIsNotEnabled()
        show(state, reviewed.copy(accepted = true), "aceite-explicito")
        compose.onNodeWithTag(ReceiptConfigurationTags.Activate).assertIsEnabled()
        val enabled = selected.copy(completed = true, status = selected.status!!.copy(state = config.copy(enabled = true,
            pixEnabled = true, cardEnabled = true)))
        show(state, enabled, "ativo")
        show(state, enabled.copy(completed = false, confirmingDeactivation = true), "confirmar-desligar")
        compose.onNodeWithText("Confirmar desativação").assertExists()
    }

    @Test fun reviewShowsBothMethodFees() {
        compose.setContent { SaqzTheme { ReceiptConfigurationScreen(selected.copy(methods = ReceiptMethod.entries.toSet(),
            review = review, terms = listOf(ReceiptTerms("v1", "Termos de teste"))), {}, {}) } }
        compose.onNodeWithText("Taxas de serviço e pagamento").performScrollTo().assertIsDisplayed()
        capture("taxas-pix-e-cartao")
    }

    @Test fun reviewButtonAsksForBothMethods() {
        val intents = mutableListOf<ReceiptConfigurationIntent>()
        compose.setContent { SaqzTheme { ReceiptConfigurationScreen(selected, intents::add, {}) } }
        compose.onNodeWithTag(ReceiptConfigurationTags.Preview).performClick()
        org.junit.Assert.assertEquals(listOf(ReceiptConfigurationIntent.PreviewAllMethods), intents)
    }

    private fun show(state: androidx.compose.runtime.MutableState<ReceiptConfigurationState>,
        next: ReceiptConfigurationState, name: String) {
        compose.runOnIdle { state.value = next }
        compose.onNodeWithTag(ReceiptConfigurationTags.Screen).assertExists()
        capture(name)
    }
    private fun capture(name: String) {
        val output = File("../build/reports/receivables-configuration").apply { mkdirs() }
        compose.onRoot().captureRoboImage(File(output, "$name.png").path)
    }
    private val config = ReceiptConfiguration("00000000-0000-4000-8000-000000001234", "group", false, false, false)
    private val selected = ReceiptConfigurationState(loading = false,
        accounts = listOf(ReceiptAccount("00000000-0000-4000-8000-000000001234", AccountRegistration.APPROVED, true)),
            accountId = "00000000-0000-4000-8000-000000001234",
        status = ReceiptStatus(config, mapOf("READ" to ReceiptPermission(true), "CANCEL" to ReceiptPermission(true))),
            discoveryAvailable = true)
    private val review = ReceiptReview(config, listOf(ReceiptSchedule(ReceiptMethod.PIX, "v1", "0.01", 10, "0.02", 20),
        ReceiptSchedule(ReceiptMethod.CARD, "v1", "0.0299", 49, "0.02", 20)),
        listOf(ReceiptPrice("GAME", ReceiptMethod.PIX, 1000, 61, 1061, 1000)),
        mapOf("ACTIVATE_GROUP" to ReceiptPermission(true)), null, "a".repeat(64))
}
