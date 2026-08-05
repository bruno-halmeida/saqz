package br.com.saqz.groups.presentation.ui.finance.sheets

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.ui.finance.groupcash.DebtorUi
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
class FinanceSheetsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun chargeSheet() = capture("finance-charge-sheet") {
        ChargeSheet(
            open = true,
            debtors = debtors,
            pixKey = "pix@saqz.com",
            pixLabel = "Vôlei do CERET",
            onClose = {},
            onCopyPix = {},
            onSend = { _, _ -> },
        )
    }

    @Test
    fun receiptSheet() = capture("finance-receipt-sheet") {
        ReceiptSheet(
            open = true,
            debtor = debtors.first().copy(referenceLabel = "Mensalista · julho"),
            onClose = {},
            onConfirm = {},
        )
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            SaqzTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SaqzTheme.colors.background),
                ) {
                    content()
                }
            }
        }
        compose.onRoot().captureRoboImage("screenshots/vul-183/$name.png")
    }

    private companion object {
        val debtors = listOf(
            DebtorUi(
                chargeId = "charge-camila",
                memberId = "member-camila",
                name = "Camila Alves",
                dueLabel = "Venceu em 10/07",
                amountLabel = "R$\u00A07.000,00",
                amountCents = 700000L,
                chargeVersion = 3L,
                month = "2026-07",
                referenceLabel = "Mensalista · julho",
            ),
            DebtorUi(
                chargeId = "charge-pedro",
                memberId = "member-pedro",
                name = "Pedro Henrique",
                dueLabel = "Venceu em 10/07",
                amountLabel = "R$\u00A07.000,00",
                amountCents = 700000L,
                chargeVersion = 4L,
                month = "2026-07",
                referenceLabel = "Mensalista · julho",
            ),
        )
    }
}
