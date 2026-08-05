package br.com.saqz.groups.presentation.ui.finance.settlement

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.finance.ChargeStatus
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
class GameSettlementScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `5g settlement`() = capture("acerto-5g") {
        GameSettlementScreen(progressState, {}, {})
    }

    @Test
    fun `5i settlement`() = capture("acerto-5i") {
        GameSettlementScreen(summaryState, {}, {})
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            SaqzTheme {
                Box(Modifier.fillMaxSize().background(SaqzTheme.colors.background)) { content() }
            }
        }
        compose.onRoot().captureRoboImage("screenshots/vul-184/$name.png")
    }

    private companion object {
        val header = GameSettlementHeaderUi("04/08/2026 · 19:30", "CERET — Quadra 2", 8)
        val progressState = GameSettlementState(
            isLoading = false,
            groupName = "Vôlei do CERET",
            header = header,
            monthlyMemberCount = 3,
            paidDiaristCount = 2,
            totalDiaristCount = 6,
            pendingDiaristCount = 4,
            progress = 2f / 6f,
            unitDiaristCents = 2_000L,
            receivedDiaristCents = 4_000L,
            pendingDiaristCents = 8_000L,
            diarists = listOf(
                diarist("paid-1", "Bia", ChargeStatus.Paid),
                diarist("pending-1", "Ana", ChargeStatus.Pending),
            ),
            debtors = emptyList(),
            pix = null,
        )
        val summaryState = GameSettlementState(
            isLoading = false,
            header = header,
            monthlyMemberCount = 3,
            paidDiaristCount = 2,
            totalDiaristCount = 2,
            progress = 1f,
            unitDiaristCents = 7_000L,
            receivedDiaristCents = 14_000L,
            costCents = 16_000L,
            resultCents = -2_000L,
            diarists = listOf(
                diarist("paid-1", "Bia", ChargeStatus.Paid),
                diarist("paid-2", "Caio", ChargeStatus.Paid),
            ),
        )

        fun diarist(id: String, name: String, status: ChargeStatus) = GameSettlementDiaristUi(
            chargeId = id,
            memberId = id,
            name = name,
            meta = "Diarista",
            amountLabel = "R$\u00A07.000,00",
            amountCents = 7_000L,
            dueDate = "2026-08-04",
            chargeVersion = 1L,
            status = status,
            referenceLabel = "Diarista · jogo de 04/08",
        )
    }
}
