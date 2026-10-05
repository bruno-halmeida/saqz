package br.com.saqz.androidapp

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.designsystem.UiText
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.presentation.appstore.AppStorePaywallOfferUi
import br.com.saqz.subscriptions.presentation.appstore.AppStorePaywallPhase
import br.com.saqz.subscriptions.presentation.appstore.AppStorePaywallPlanUi
import br.com.saqz.subscriptions.presentation.appstore.AppStorePaywallState
import br.com.saqz.subscriptions.presentation.ui.appstore.AppStorePaywallScreen
import br.com.saqz.subscriptions.resources.Res
import br.com.saqz.subscriptions.resources.changeplan_benefit_admins
import br.com.saqz.subscriptions.resources.changeplan_benefit_athletes
import br.com.saqz.subscriptions.resources.changeplan_benefit_athletes_unlimited
import br.com.saqz.subscriptions.resources.changeplan_benefit_groups
import br.com.saqz.subscriptions.resources.changeplan_benefit_groups_one
import br.com.saqz.subscriptions.resources.changeplan_benefit_groups_unlimited
import br.com.saqz.subscriptions.resources.changeplan_benefit_reports
import br.com.saqz.subscriptions.resources.changeplan_benefit_whatsapp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tela de compra pela App Store, no tamanho de um iPhone (393×852 pt a 3x = 1179×2556 px): é o
 * print que cada assinatura pede em "Review Information" no App Store Connect.
 *
 * Gravar: `./gradlew :android-app:recordRoborazziDevDebug --tests '*AppStorePaywallScreenshotTest'`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-port-xxhdpi", application = android.app.Application::class)
class AppStorePaywallScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun monthly() = capture("apple-review/paywall-mensal", SubscriptionCycle.Monthly)

    @Test
    fun annual() = capture("apple-review/paywall-anual", SubscriptionCycle.Annual)

    private fun capture(name: String, cycle: SubscriptionCycle) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            SaqzTheme {
                AppStorePaywallScreen(
                    state = AppStorePaywallState(phase = AppStorePaywallPhase.Ready, cycle = cycle, plans = PLANS),
                    onIntent = {},
                    onBack = {},
                    onOpenTerms = {},
                    onOpenPrivacy = {},
                )
            }
        }
        compose.mainClock.advanceTimeBy(SHUTTER_MILLIS)
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    private companion object {
        const val SHUTTER_MILLIS = 600L

        // Mesmos benefícios que o ViewModel monta a partir de GET /plans.
        val PLANS = listOf(
            plan(
                Plan.Titular, "R$ 39,90", "R$ 359,90",
                UiText.Res(Res.string.changeplan_benefit_groups_one),
                UiText.Res(Res.string.changeplan_benefit_athletes, listOf(25)),
            ),
            plan(
                Plan.Organizador, "R$ 59,90", "R$ 539,90",
                UiText.Res(Res.string.changeplan_benefit_groups, listOf(3)),
                UiText.Res(Res.string.changeplan_benefit_athletes_unlimited),
            ),
            plan(
                Plan.Ilimitado, "R$ 89,90", "R$ 809,90",
                UiText.Res(Res.string.changeplan_benefit_groups_unlimited),
                UiText.Res(Res.string.changeplan_benefit_athletes_unlimited),
                UiText.Res(Res.string.changeplan_benefit_admins),
                UiText.Res(Res.string.changeplan_benefit_reports),
                UiText.Res(Res.string.changeplan_benefit_whatsapp),
            ),
        )

        fun plan(plan: Plan, monthly: String, annual: String, vararg benefits: UiText) = AppStorePaywallPlanUi(
            plan = plan,
            name = plan.name,
            benefits = benefits.toList(),
            monthly = AppStorePaywallOfferUi("app.saqz.${plan.name.lowercase()}.mensal", monthly, isCurrent = false),
            annual = AppStorePaywallOfferUi("app.saqz.${plan.name.lowercase()}.anual", annual, isCurrent = false),
        )
    }
}
