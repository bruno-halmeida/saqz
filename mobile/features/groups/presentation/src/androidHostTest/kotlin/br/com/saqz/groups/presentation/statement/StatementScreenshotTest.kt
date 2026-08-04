package br.com.saqz.groups.presentation.statement

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.finance.FinanceDirection
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
class StatementScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun statementWithFiltersAndEntries() {
        compose.setContent {
            SaqzTheme {
                StatementScreen(
                    state = StatementState(
                        isLoading = false,
                        items = listOf(
                            StatementItemUi(
                                id = "in-1",
                                direction = FinanceDirection.In,
                                title = "Mensalidade · Bia",
                                meta = "Pix · 04/08/2026",
                                amountLabel = "+R$ 80,00",
                            ),
                            StatementItemUi(
                                id = "out-1",
                                direction = FinanceDirection.Out,
                                title = "Aluguel da quadra",
                                meta = "Quadra · 03/08/2026",
                                amountLabel = "−R$ 320,00",
                            ),
                        ),
                        summary = StatementSummaryUi(periodBalanceCents = 64_000L),
                    ),
                    onBack = {},
                    onIntent = {},
                    modifier = Modifier.fillMaxSize().background(SaqzTheme.colors.background),
                )
            }
        }
        compose.onRoot().captureRoboImage("screenshots/vul-180/finance-statement.png")
    }
}
