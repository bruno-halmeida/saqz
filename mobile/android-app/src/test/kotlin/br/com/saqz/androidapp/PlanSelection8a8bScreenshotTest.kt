package br.com.saqz.androidapp

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.designsystem.UiText
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.presentation.planselection.CouponUiState
import br.com.saqz.subscriptions.presentation.planselection.PlanSelectionState
import br.com.saqz.subscriptions.presentation.planselection.PlanUi
import br.com.saqz.subscriptions.presentation.planselection.ui.PlanSelectionScreen
import br.com.saqz.subscriptions.resources.Res as SubscriptionsRes
import br.com.saqz.subscriptions.resources.plan_selection_badge_free
import br.com.saqz.subscriptions.resources.plan_selection_badge_highlighted
import br.com.saqz.subscriptions.resources.plan_selection_feature_athletes_limited
import br.com.saqz.subscriptions.resources.plan_selection_feature_athletes_unlimited
import br.com.saqz.subscriptions.resources.plan_selection_feature_groups_limited
import br.com.saqz.subscriptions.resources.plan_selection_feature_groups_unlimited
import br.com.saqz.subscriptions.resources.plan_selection_feature_multi_admin
import br.com.saqz.subscriptions.resources.plan_selection_feature_reports
import br.com.saqz.subscriptions.resources.plan_selection_feature_whatsapp_sla
import br.com.saqz.subscriptions.resources.plan_selection_load_error
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * As cenas da 8a/8b (VUL-109). Arquivo próprio, e não mais um bloco do `SaqzScreenshotTest`:
 * aquele é o catálogo do design system e os quatro tickets da onda 6 rodam em paralelo.
 *
 * Gravar: ./gradlew :android-app:recordRoborazziDevDebug
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = android.app.Application::class,
)
class PlanSelection8a8bScreenshotTest {

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

    private fun scene(name: String, state: PlanSelectionState) = capture(name) {
        PlanSelectionScreen(state = state, onIntent = {}, onBack = {})
    }

    @Test
    fun loading() = scene("8a-escolher-plano-carregando", PlanSelectionState(isLoading = true))

    @Test
    fun loadError() = scene(
        "8a-escolher-plano-erro",
        PlanSelectionState(isLoading = false, loadError = UiText.Res(SubscriptionsRes.string.plan_selection_load_error)),
    )

    @Test
    fun monthlyDefault() = scene("8a-escolher-plano-mensal", defaultState)

    @Test
    fun annualCycle() = scene(
        "8a-escolher-plano-anual",
        defaultState.copy(cycle = SubscriptionCycle.Annual),
    )

    @Test
    fun freePlanSelected() = scene(
        "8a-escolher-plano-gratuito-selecionado",
        defaultState.copy(selectedPlanId = Plan.Titular),
    )

    @Test
    fun topPlanSelected() = scene(
        "8a-escolher-plano-sem-badge-selecionado",
        defaultState.copy(selectedPlanId = Plan.Ilimitado),
    )

    @Test
    fun couponValidating() = scene(
        "8b-cupom-validando",
        defaultState.copy(couponCode = "GALERA10", isValidatingCoupon = true),
    )

    @Test
    fun couponApplied() = scene(
        "8b-cupom-aplicado",
        defaultState.copy(
            couponCode = "GALERA10",
            coupon = CouponUiState.Applied(
                code = "GALERA10",
                discountPercent = 10,
                listPriceCents = 1_990,
                finalPriceCents = 1_791,
            ),
        ),
    )

    @Test
    fun couponNotFound() = scene(
        "8b-cupom-nao-encontrado",
        defaultState.copy(couponCode = "VOLEI99", coupon = CouponUiState.NotFound),
    )

    @Test
    fun couponExpired() = scene(
        "8b-cupom-expirado",
        defaultState.copy(couponCode = "SAQUE20", coupon = CouponUiState.Expired(code = "SAQUE20")),
    )

    private val defaultState: PlanSelectionState
        get() = PlanSelectionState(isLoading = false, plans = samplePlans, selectedPlanId = Plan.Organizador)

    private val samplePlans: List<PlanUi>
        get() = listOf(
            PlanUi(
                id = Plan.Titular,
                name = "Titular",
                monthlyPriceCents = 0,
                annualPriceCents = 0,
                isFree = true,
                isHighlighted = false,
                badge = UiText.Res(SubscriptionsRes.string.plan_selection_badge_free),
                features = listOf(
                    UiText.Res(SubscriptionsRes.string.plan_selection_feature_groups_limited, listOf(1)),
                    UiText.Res(SubscriptionsRes.string.plan_selection_feature_athletes_limited, listOf(20)),
                ),
            ),
            PlanUi(
                id = Plan.Organizador,
                name = "Organizador",
                monthlyPriceCents = 1_990,
                annualPriceCents = 19_900,
                isFree = false,
                isHighlighted = true,
                badge = UiText.Res(SubscriptionsRes.string.plan_selection_badge_highlighted),
                features = listOf(
                    UiText.Res(SubscriptionsRes.string.plan_selection_feature_groups_limited, listOf(3)),
                    UiText.Res(SubscriptionsRes.string.plan_selection_feature_athletes_unlimited),
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
                    UiText.Res(SubscriptionsRes.string.plan_selection_feature_groups_unlimited),
                    UiText.Res(SubscriptionsRes.string.plan_selection_feature_athletes_unlimited),
                    UiText.Res(SubscriptionsRes.string.plan_selection_feature_multi_admin),
                    UiText.Res(SubscriptionsRes.string.plan_selection_feature_reports),
                    UiText.Res(SubscriptionsRes.string.plan_selection_feature_whatsapp_sla),
                ),
            ),
        )
}
