package br.com.saqz.subscriptions.presentation.payment.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.backhandler.LocalCompatNavigationEventDispatcherOwner
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInput
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.presentation.payment.PaymentIntent
import br.com.saqz.subscriptions.presentation.payment.PaymentState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Achado do Codex no PR #100: a seta do topo (`SaqzTopAppBar.onBack`) só cobre o toque — o
 * back do sistema/gesto ia direto pro `NavDisplay.onBack = pop` de `SaqzNavHost.kt`, sem
 * passar pela confirmação de checkout pendente. Mesmo idioma de teste do
 * `SaqzBottomSheetBackTest`/`SaqzCatalogBackTest`: monta o próprio dispatcher pra poder
 * *disparar* o back e observar o que ninguém interceptou (`onBackCompletedFallback`).
 */
@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class)
class PaymentScreenBackTest {

    private class TestBackInput : NavigationEventInput() {
        fun pressBack() = dispatchOnBackCompleted()
    }

    private class TestOwner(
        override val navigationEventDispatcher: NavigationEventDispatcher,
    ) : NavigationEventDispatcherOwner

    private val baseState = PaymentState(
        plan = Plan.Organizador,
        cycle = SubscriptionCycle.Monthly,
        planName = "Organizador",
        priceCents = 4_990L,
    )

    @Test
    fun systemBackWithPendingCheckoutOpensConfirmationInsteadOfLeaving() = runComposeUiTest {
        var unhandledBacks = 0
        val dispatcher = NavigationEventDispatcher { unhandledBacks++ }
        val input = TestBackInput().also(dispatcher::addInput)
        val intents = mutableListOf<PaymentIntent>()
        var backCalled = false

        setContent {
            CompositionLocalProvider(LocalCompatNavigationEventDispatcherOwner provides TestOwner(dispatcher)) {
                SaqzTheme {
                    PaymentScreen(
                        state = baseState.copy(pixCopyPaste = "00020126chavepix", isWaitingConfirmation = true),
                        onIntent = { intents += it },
                        onBack = { backCalled = true },
                    )
                }
            }
        }

        input.pressBack()
        waitForIdle()

        assertEquals(listOf<PaymentIntent>(PaymentIntent.RequestBack), intents)
        assertTrue(!backCalled)
        assertEquals(0, unhandledBacks)
    }

    @Test
    fun systemBackWithoutACheckoutIsNotIntercepted() = runComposeUiTest {
        var unhandledBacks = 0
        val dispatcher = NavigationEventDispatcher { unhandledBacks++ }
        val input = TestBackInput().also(dispatcher::addInput)
        val intents = mutableListOf<PaymentIntent>()

        setContent {
            CompositionLocalProvider(LocalCompatNavigationEventDispatcherOwner provides TestOwner(dispatcher)) {
                SaqzTheme {
                    PaymentScreen(state = baseState, onIntent = { intents += it }, onBack = {})
                }
            }
        }

        input.pressBack()
        waitForIdle()

        // Sem checkout pendente não há nada pra confirmar — o back segue pro NavDisplay.
        assertEquals(1, unhandledBacks)
        assertTrue(intents.isEmpty())
    }

    @Test
    fun systemBackWithTheConfirmationAlreadyOpenClosesItInsteadOfRequestingItAgain() = runComposeUiTest {
        var unhandledBacks = 0
        val dispatcher = NavigationEventDispatcher { unhandledBacks++ }
        val input = TestBackInput().also(dispatcher::addInput)
        val intents = mutableListOf<PaymentIntent>()

        setContent {
            CompositionLocalProvider(LocalCompatNavigationEventDispatcherOwner provides TestOwner(dispatcher)) {
                SaqzTheme {
                    PaymentScreen(
                        state = baseState.copy(
                            pixCopyPaste = "00020126chavepix",
                            isWaitingConfirmation = true,
                            isBackConfirmationOpen = true,
                        ),
                        onIntent = { intents += it },
                        onBack = {},
                    )
                }
            }
        }

        input.pressBack()
        waitForIdle()

        // `enabled = ... && !isBackConfirmationOpen` desliga o handler da tela enquanto o
        // sheet está aberto — só o `BackHandler` interno do `SaqzBottomSheet` responde, sem
        // um segundo `RequestBack` empilhando por cima do sheet já aberto.
        assertEquals(listOf<PaymentIntent>(PaymentIntent.DismissBackConfirmation), intents)
        assertEquals(0, unhandledBacks)
    }
}
