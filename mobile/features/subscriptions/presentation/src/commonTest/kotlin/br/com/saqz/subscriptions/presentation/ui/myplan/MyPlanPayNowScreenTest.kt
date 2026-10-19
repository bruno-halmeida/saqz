package br.com.saqz.subscriptions.presentation.ui.myplan

import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.UiText
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.subscriptions.presentation.myplan.MyPlanCardUi
import br.com.saqz.subscriptions.presentation.myplan.MyPlanState
import br.com.saqz.subscriptions.presentation.myplan.MyPlanStatusTone
import kotlin.test.Test
import kotlin.test.assertEquals

/** "Pagar agora" no site em atraso: abre a fatura vencida; sem fatura, sem botão. */
@OptIn(ExperimentalTestApi::class)
class MyPlanPayNowScreenTest {
    private val link = "https://www.asaas.com/i/atrasada"

    @Test
    fun pastDueWithInvoiceOffersPayNowAndOpensTheInvoice() = runComposeUiTest {
        val opened = mutableListOf<String>()
        val handler = object : UriHandler {
            override fun openUri(uri: String) {
                opened += uri
            }
        }
        setContent {
            CompositionLocalProvider(LocalUriHandler provides handler) {
                SaqzTheme { MyPlanScreen(pastDue(link), {}, {}, purchasesAvailable = true) }
            }
        }

        onNodeWithTag(MyPlanTags.PayNow).performClick()

        assertEquals(listOf(link), opened)
    }

    @Test
    fun pastDueWithoutInvoiceHasNoPayNow() = runComposeUiTest {
        setContent { SaqzTheme { MyPlanScreen(pastDue(null), {}, {}, purchasesAvailable = true) } }
        onNodeWithTag(MyPlanTags.PayNow).assertDoesNotExist()
    }

    private fun pastDue(payUrl: String?) = MyPlanState(
        isLoading = false,
        plan = MyPlanCardUi(
            name = "Organizador",
            statusLabel = UiText.Raw("Em atraso"),
            statusTone = MyPlanStatusTone.PastDue,
            nextChargeDate = "10/10/2026",
        ),
        payUrl = payUrl,
    )
}
