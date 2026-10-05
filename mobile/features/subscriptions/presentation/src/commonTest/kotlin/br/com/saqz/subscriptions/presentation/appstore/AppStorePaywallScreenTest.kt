package br.com.saqz.subscriptions.presentation.appstore

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.presentation.ui.appstore.AppStorePaywallPreviewData
import br.com.saqz.subscriptions.presentation.ui.appstore.AppStorePaywallScreen
import br.com.saqz.subscriptions.presentation.ui.appstore.AppStorePaywallTags
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AppStorePaywallScreenTest {
    @Test
    fun `annual plan shows the total yearly price with renewal terms and links`() = runComposeUiTest {
        var terms = 0
        var privacy = 0
        setContent {
            SaqzTheme {
                AppStorePaywallScreen(
                    state = AppStorePaywallPreviewData.ready.copy(cycle = SubscriptionCycle.Annual),
                    onIntent = {},
                    onBack = {},
                    onOpenTerms = { terms++ },
                    onOpenPrivacy = { privacy++ },
                )
            }
        }
        onNodeWithText("R$ 539,90 por ano").assertExists()
        assertEquals(3, onAllNodesWithText("Assinatura anual · renovação automática").fetchSemanticsNodes().size)
        onNodeWithTag(AppStorePaywallTags.Disclosure).performScrollTo().assertIsDisplayed()
        onNodeWithTag(AppStorePaywallTags.Terms).performScrollTo().performClick()
        onNodeWithTag(AppStorePaywallTags.Privacy).performScrollTo().performClick()
        onNodeWithTag(AppStorePaywallTags.Restore).performScrollTo().assertIsDisplayed()
        assertEquals(1, terms)
        assertEquals(1, privacy)
    }

    @Test
    fun `buying and restoring dispatch their intents`() = runComposeUiTest {
        val intents = mutableListOf<AppStorePaywallIntent>()
        setContent {
            SaqzTheme {
                AppStorePaywallScreen(
                    state = AppStorePaywallPreviewData.ready,
                    onIntent = intents::add,
                    onBack = {},
                    onOpenTerms = {},
                    onOpenPrivacy = {},
                )
            }
        }
        onNodeWithTag(AppStorePaywallTags.PurchasePrefix + "app.saqz.organizador.mensal").performScrollTo().performClick()
        onNodeWithTag(AppStorePaywallTags.Restore).performScrollTo().performClick()

        assertEquals(
            listOf(
                AppStorePaywallIntent.Purchase("app.saqz.organizador.mensal"),
                AppStorePaywallIntent.Restore,
            ),
            intents,
        )
    }

    @Test
    fun `pending confirmation offers to confirm again`() = runComposeUiTest {
        val intents = mutableListOf<AppStorePaywallIntent>()
        setContent {
            SaqzTheme {
                AppStorePaywallScreen(
                    state = AppStorePaywallPreviewData.ready.copy(notice = AppStorePaywallNotice.ConfirmationPending),
                    onIntent = intents::add,
                    onBack = {},
                    onOpenTerms = {},
                    onOpenPrivacy = {},
                )
            }
        }
        onNodeWithTag(AppStorePaywallTags.ConfirmRetry).performScrollTo().performClick()

        assertEquals(listOf<AppStorePaywallIntent>(AppStorePaywallIntent.RetryConfirmation), intents)
    }
}
