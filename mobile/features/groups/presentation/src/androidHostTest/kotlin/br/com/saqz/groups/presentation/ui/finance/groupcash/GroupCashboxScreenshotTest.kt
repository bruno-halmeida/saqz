package br.com.saqz.groups.presentation.ui.finance.groupcash

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
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
    application = Application::class,
)
class GroupCashboxScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun loaded() = capture("caixa-carregado") { GroupCashboxScreen(loadedState, {}, {}) }

    @Test
    fun empty() = capture("caixa-zerado") {
        GroupCashboxScreen(loadedState.copy(cashboxEmpty = true, debtors = emptyList(), overdueBanner = null), {}, {})
    }

    @Test
    fun overdue() = capture("caixa-vencidas") {
        GroupCashboxScreen(
            loadedState.copy(
                overdueBanner = OverdueBannerUi(
                    "Camila, Pedro e Thiago estão com julho em aberto",
                    "julho de 2026",
                ),
            ),
            {},
            {},
        )
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            SaqzTheme {
                Box(Modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
                    content()
                }
            }
        }
        compose.onRoot().captureRoboImage("screenshots/vul-179/$name.png")
    }

    private companion object {
        val loadedState = GroupCashboxState(
            isLoading = false,
            groupName = "Vôlei do CERET",
            monthKey = "2026-08",
            monthLabel = "agosto de 2026",
            monthlyMembersLabel = "18 mensalistas",
            balanceLabel = "R$\u00A0380,00",
            receivedLabel = "R$\u00A070.000,00",
            openLabel = "R$\u00A056.000,00",
            expensesLabel = "R$\u00A012.000,00",
            monthlyProgressLabel = "10/18",
            monthlyProgress = 10f / 18f,
            paidMonthlyCount = 10,
            openMonthlyCount = 8,
            monthlyTotalCount = 18,
            debtors = listOf(
                DebtorUi("charge-camila", "member-camila", "Camila Alves", "Venceu em 10/07", "R$\u00A07.000,00", 7000L, 3L, "2026-07"),
                DebtorUi("charge-pedro", "member-pedro", "Pedro Henrique", "Venceu em 10/07", "R$\u00A07.000,00", 7000L, 4L, "2026-07"),
                DebtorUi("charge-thiago", "member-thiago", "Thiago Melo", "Venceu em 10/07", "R$\u00A07.000,00", 7000L, 5L, "2026-07"),
            ),
            overdueBanner = null,
            pix = PixUi("pix@saqz.com", "Vôlei do CERET"),
        )
    }
}
