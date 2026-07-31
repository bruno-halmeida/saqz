package br.com.saqz.androidapp

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.designsystem.UiText
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.subscriptions.domain.subscription.BillingType
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.presentation.myplan.MyPlanCardUi
import br.com.saqz.subscriptions.presentation.myplan.MyPlanChangeOptionUi
import br.com.saqz.subscriptions.presentation.myplan.MyPlanState
import br.com.saqz.subscriptions.presentation.myplan.MyPlanStatusTone
import br.com.saqz.subscriptions.presentation.myplan.MyPlanUsageUi
import br.com.saqz.subscriptions.presentation.planselection.CouponUiState
import br.com.saqz.subscriptions.presentation.planselection.PlanSelectionState
import br.com.saqz.subscriptions.presentation.planselection.PlanUi
import br.com.saqz.subscriptions.presentation.planselection.ui.PlanSelectionScreen
import br.com.saqz.subscriptions.presentation.payment.PaymentState
import br.com.saqz.subscriptions.presentation.payment.ui.PaymentScreen
import br.com.saqz.subscriptions.presentation.ui.myplan.MyPlanScreen
import br.com.saqz.subscriptions.presentation.ui.planactive.PlanActiveScreen
import br.com.saqz.subscriptions.presentation.planactive.PlanActiveState
import br.com.saqz.subscriptions.resources.Res
import br.com.saqz.subscriptions.resources.plan_selection_badge_free
import br.com.saqz.subscriptions.resources.plan_selection_badge_highlighted
import br.com.saqz.subscriptions.resources.plan_selection_feature_athletes_limited
import br.com.saqz.subscriptions.resources.plan_selection_feature_athletes_unlimited
import br.com.saqz.subscriptions.resources.plan_selection_feature_groups_limited
import br.com.saqz.subscriptions.resources.plan_selection_feature_groups_unlimited
import br.com.saqz.subscriptions.resources.plan_selection_feature_multi_admin
import br.com.saqz.subscriptions.resources.plan_selection_feature_reports
import br.com.saqz.subscriptions.resources.plan_selection_feature_whatsapp_sla
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * VUL-113 — o fecho. Não é cobertura de estado por estado (as quatro telas já têm arquivo
 * próprio e exaustivo: [PlanSelection8a8bScreenshotTest], [Payment8cScreenshotTest],
 * [PlanActive8dScreenshotTest], [MyPlan8eScreenshotTest]); é a sequência da jornada inteira,
 * numerada, para o print do corpo do PR provar contra `Saqz - Fluxo 8 Planos.html` que o
 * `SaqzNavHost` liga 8a→8b→8c→confirmação→8d e 8d→8e de ponta a ponta. Nome de cena com
 * prefixo `flow8-` pra não colidir com as PNGs das quatro telas.
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
class SubscriptionsFlow8ScreenshotTest {

    private companion object {
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

    // 8a — o "+" da lista de grupos (2n) abre aqui, incondicional (GroupListContract).
    @Test
    fun step1PlanSelection() = capture("flow8-01-plan-selection") {
        PlanSelectionScreen(state = planSelectionState, onIntent = {}, onBack = {})
    }

    // 8b — cupom aplicado.
    @Test
    fun step2CouponApplied() = capture("flow8-02-coupon-applied") {
        PlanSelectionScreen(
            state = planSelectionState.copy(
                couponCode = "GALERA10",
                coupon = CouponUiState.Applied(
                    code = "GALERA10",
                    discountPercent = 10,
                    listPriceCents = 1_990,
                    finalPriceCents = 1_791,
                ),
            ),
            onIntent = {},
            onBack = {},
        )
    }

    // 8b — cupom não encontrado.
    @Test
    fun step3CouponNotFound() = capture("flow8-03-coupon-not-found") {
        PlanSelectionScreen(
            state = planSelectionState.copy(couponCode = "VOLEI99", coupon = CouponUiState.NotFound),
            onIntent = {},
            onBack = {},
        )
    }

    // 8b — cupom expirado.
    @Test
    fun step4CouponExpired() = capture("flow8-04-coupon-expired") {
        PlanSelectionScreen(
            state = planSelectionState.copy(couponCode = "SAQUE20", coupon = CouponUiState.Expired(code = "SAQUE20")),
            onIntent = {},
            onBack = {},
        )
    }

    // 8c — `onContinue` empilhou `SubscriptionsRoute.Payment(planId.name, cycle.name, ...)`.
    @Test
    fun step5PaymentPix() = capture("flow8-05-payment-pix") {
        PaymentScreen(state = paymentState, onIntent = {}, onBack = {})
    }

    @Test
    fun step6PaymentCard() = capture("flow8-06-payment-card") {
        PaymentScreen(state = paymentState.copy(billingType = BillingType.CreditCard), onIntent = {}, onBack = {})
    }

    // Confirmação assíncrona via webhook: aguardando enquanto `receipts()` ainda não trouxe
    // o recibo que dispara `PaymentEffect.NavigateToPlanActive`.
    @Test
    fun step7PaymentWaitingConfirmation() = capture("flow8-07-payment-waiting") {
        PaymentScreen(
            state = paymentState.copy(
                pixCopyPaste = "00020126580014BR.GOV.BCB.PIX0136chave-fake-1234",
                isWaitingConfirmation = true,
            ),
            onIntent = {},
            onBack = {},
        )
    }

    // 8d — `PaymentEffect.NavigateToPlanActive` empilhou `SubscriptionsRoute.PlanActive`.
    @Test
    fun step8PlanActive() = capture("flow8-08-plan-active") {
        PlanActiveScreen(state = planActiveState, onIntent = {})
    }

    // 8d → "Ver meu plano" empilhou `SubscriptionsRoute.MyPlan`.
    @Test
    fun step9MyPlan() = capture("flow8-09-my-plan") {
        MyPlanScreen(state = myPlanState, onBack = {}, onIntent = {})
    }

    // 8e — troca recusada por limite (VUL-112), alcançável pelo mesmo caminho.
    @Test
    fun step10MyPlanChangeRefusedByLimit() = capture("flow8-10-my-plan-change-refused") {
        MyPlanScreen(
            state = myPlanState.copy(
                isChangeSheetOpen = true,
                changeError = UiText.Raw("Você tem 3 grupos, o plano Amador permite 1. Reduza antes de trocar."),
            ),
            onBack = {},
            onIntent = {},
        )
    }

    private val planSelectionState = PlanSelectionState(
        isLoading = false,
        plans = listOf(
            PlanUi(
                id = Plan.Titular,
                name = "Titular",
                monthlyPriceCents = 0,
                annualPriceCents = 0,
                isFree = true,
                isHighlighted = false,
                badge = UiText.Res(Res.string.plan_selection_badge_free),
                features = listOf(
                    UiText.Res(Res.string.plan_selection_feature_groups_limited, listOf(1)),
                    UiText.Res(Res.string.plan_selection_feature_athletes_limited, listOf(20)),
                ),
            ),
            PlanUi(
                id = Plan.Organizador,
                name = "Organizador",
                monthlyPriceCents = 1_990,
                annualPriceCents = 19_900,
                isFree = false,
                isHighlighted = true,
                badge = UiText.Res(Res.string.plan_selection_badge_highlighted),
                features = listOf(
                    UiText.Res(Res.string.plan_selection_feature_groups_limited, listOf(3)),
                    UiText.Res(Res.string.plan_selection_feature_athletes_unlimited),
                ),
            ),
            PlanUi(
                id = Plan.Ilimitado,
                name = "Ilimitado",
                monthlyPriceCents = 3_990,
                annualPriceCents = 39_900,
                isFree = false,
                isHighlighted = false,
                badge = null,
                features = listOf(
                    UiText.Res(Res.string.plan_selection_feature_groups_unlimited),
                    UiText.Res(Res.string.plan_selection_feature_athletes_unlimited),
                    UiText.Res(Res.string.plan_selection_feature_multi_admin),
                    UiText.Res(Res.string.plan_selection_feature_reports),
                    UiText.Res(Res.string.plan_selection_feature_whatsapp_sla),
                ),
            ),
        ),
        selectedPlanId = Plan.Organizador,
    )

    private val paymentState = PaymentState(
        plan = Plan.Organizador,
        cycle = SubscriptionCycle.Monthly,
        planName = "Organizador",
        priceCents = 1_990L,
    )

    private val planActiveState = PlanActiveState(
        isLoading = false,
        planName = "Organizador",
        priceLabel = "R$ 19,90/mês",
        nextBillingLabel = "24 de agosto",
        groupsAvailableLabel = "3",
    )

    private val myPlanState = MyPlanState(
        isLoading = false,
        plan = MyPlanCardUi(
            name = "Organizador",
            statusLabel = UiText.Raw("Ativo"),
            statusTone = MyPlanStatusTone.Active,
            priceLine = UiText.Raw("R$ 19,90/mês"),
            nextChargeDate = "24/08/2026",
            paymentMethodLabel = UiText.Raw("Pix"),
            pendingChangeLine = null,
        ),
        usage = MyPlanUsageUi(
            ratioLabel = UiText.Raw("2 de 3 grupos"),
            progress = 2f / 3f,
            helperText = UiText.Raw("Perto do limite? Suba de plano quando precisar de mais grupos."),
        ),
        receipts = emptyList(),
        changeOptions = listOf(
            MyPlanChangeOptionUi(Plan.Titular, "Amador", UiText.Raw("Grátis"), isCurrent = false),
            MyPlanChangeOptionUi(Plan.Organizador, "Organizador", UiText.Raw("R$ 19,90/mês"), isCurrent = true),
        ),
    )
}
