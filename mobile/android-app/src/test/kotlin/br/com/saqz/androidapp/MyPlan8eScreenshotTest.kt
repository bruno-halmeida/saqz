package br.com.saqz.androidapp

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.designsystem.UiText
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.subscriptions.presentation.myplan.MyPlanCardUi
import br.com.saqz.subscriptions.presentation.myplan.MyPlanReceiptUi
import br.com.saqz.subscriptions.presentation.myplan.MyPlanState
import br.com.saqz.subscriptions.presentation.myplan.MyPlanStatusTone
import br.com.saqz.subscriptions.presentation.myplan.MyPlanUsageUi
import br.com.saqz.subscriptions.presentation.ui.myplan.MyPlanScreen
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 8e — plano atual, status, uso, recibos, cancelamento, carregando e erro — mais os dois
 * estados que os 5 achados do Codex no PR #93 acrescentaram: assinatura cancelada (status
 * forçado + "acesso garantido até", em vez de "Ativo" com próxima cobrança falsa) e falha ao
 * carregar recibos (erro com retry, em vez de "nenhum recibo ainda").
 *
 * Arquivo próprio (AGENTS.md §11): `:features:subscriptions:presentation` ainda não tem
 * Roborazzi próprio (nenhum ticket da onda 6 precisou até agora), então a cena entra aqui,
 * como as telas do fluxo 1 — mesmo padrão do `Login1aScreenshotTest`.
 *
 * Gravar: `./gradlew :android-app:recordRoborazziDevDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = android.app.Application::class,
)
class MyPlan8eScreenshotTest {

    private companion object {
        // Cobre a entrada dos SaqzBottomSheet (320ms) além do repouso normal.
        const val SHUTTER_MILLIS = 600L
    }

    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { SaqzTheme { content() } }
        compose.mainClock.advanceTimeBy(SHUTTER_MILLIS)
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test
    fun myPlan8eAtivo() = capture("8e-myplan-ativo") {
        MyPlanScreen(state = ACTIVE, onBack = {}, onIntent = {})
    }

    @Test
    fun myPlan8eCancelarAssinatura() = capture("8e-myplan-cancelar") {
        MyPlanScreen(state = ACTIVE.copy(isCancelSheetOpen = true), onBack = {}, onIntent = {})
    }

    @Test
    fun myPlan8eCarregando() = capture("8e-myplan-carregando") {
        MyPlanScreen(state = MyPlanState(isLoading = true), onBack = {}, onIntent = {})
    }

    @Test
    fun myPlan8eErro() = capture("8e-myplan-erro") {
        MyPlanScreen(
            state = MyPlanState(isLoading = false, loadError = UiText.Raw("Não foi possível carregar seu plano agora.")),
            onBack = {},
            onIntent = {},
        )
    }

    // Achado do Codex no PR #93: `canceledAt` sem esperar o webhook migrar `status` — o
    // card mostra Cancelado e "acesso garantido até", nunca "Ativo" com próxima cobrança.
    @Test
    fun myPlan8eCancelado() = capture("8e-myplan-cancelado") {
        MyPlanScreen(
            state = ACTIVE.copy(
                plan = ACTIVE.plan?.copy(
                    statusLabel = UiText.Raw("Cancelado"),
                    statusTone = MyPlanStatusTone.Canceled,
                    nextChargeDate = null,
                    accessUntilDate = "30/08/2026",
                ),
            ),
            onBack = {},
            onIntent = {},
        )
    }

    @Test
    fun myPlan8eRecibosComCarregarMais() = capture("8e-myplan-recibos-carregar-mais") {
        MyPlanScreen(
            state = ACTIVE.copy(isReceiptsSheetOpen = true, hasMoreReceipts = true),
            onBack = {},
            onIntent = {},
        )
    }

    @Test
    fun myPlan8eRecibosDepoisDeCarregarMais() = capture("8e-myplan-recibos-carregados") {
        MyPlanScreen(
            state = ACTIVE.copy(
                isReceiptsSheetOpen = true,
                hasMoreReceipts = true,
                receipts = ACTIVE.receipts + listOf(
                    MyPlanReceiptUi("evt-4", "01/04/2026", "R$ 19,90"),
                ),
            ),
            onBack = {},
            onIntent = {},
        )
    }

    @Test
    fun myPlan8eRecibosUltimaPagina() = capture("8e-myplan-recibos-ultima-pagina") {
        MyPlanScreen(
            state = ACTIVE.copy(isReceiptsSheetOpen = true, hasMoreReceipts = false),
            onBack = {},
            onIntent = {},
        )
    }

    @Test
    fun myPlan8eRecibosErroAoCarregarMais() = capture("8e-myplan-recibos-erro-carregar-mais") {
        MyPlanScreen(
            state = ACTIVE.copy(
                isReceiptsSheetOpen = true,
                hasMoreReceipts = true,
                loadMoreReceiptsError = UiText.Raw("Não foi possível carregar mais recibos agora."),
            ),
            onBack = {},
            onIntent = {},
        )
    }

    // Achado do Codex no PR #93: falha em `receipts()` vira erro com retry, não "nenhum
    // recibo ainda".
    @Test
    fun myPlan8eRecibosErro() = capture("8e-myplan-recibos-erro") {
        MyPlanScreen(
            state = ACTIVE.copy(
                isReceiptsSheetOpen = true,
                receiptsError = UiText.Raw("Não foi possível carregar os recibos agora."),
            ),
            onBack = {},
            onIntent = {},
        )
    }
}

private val ACTIVE = MyPlanState(
    isLoading = false,
    plan = MyPlanCardUi(
        name = "Organizador",
        statusLabel = UiText.Raw("Ativo"),
        statusTone = MyPlanStatusTone.Active,
        priceLine = UiText.Raw("R$ 19,90/mês"),
        nextChargeDate = "24/08/2026",
        paymentMethodLabel = null,
        pendingChangeLine = null,
    ),
    usage = MyPlanUsageUi(
        ratioLabel = UiText.Raw("2 de 3 grupos"),
        progress = 2f / 3f,
        helperText = UiText.Raw("Perto do limite? Acompanhe seu uso para planejar os próximos grupos."),
    ),
    receipts = listOf(
        MyPlanReceiptUi("evt-1", "01/07/2026", "R$ 19,90"),
        MyPlanReceiptUi("evt-2", "01/06/2026", "R$ 19,90"),
        MyPlanReceiptUi("evt-3", "01/05/2026", "R$ 19,90"),
    ),
)
