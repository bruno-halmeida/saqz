package br.com.saqz.composeapp.subscriptiongate

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SubscriptionGateScreenTest {

    @Test
    fun initialStateExposesTheThreeActionsAndEmitsTheirCallbacks() = runComposeUiTest {
        val intents = mutableListOf<SubscriptionGateIntent>()
        var backPresses = 0
        setContent {
            SaqzTheme {
                SubscriptionGateScreen(
                    state = SubscriptionGateState(),
                    onIntent = intents::add,
                    onBack = { backPresses++ },
                )
            }
        }

        onNodeWithTag(SubscriptionGateTags.Root).assertIsDisplayed()
        onNodeWithTag(SubscriptionGateTags.Request).assertTextEquals("Receber por e-mail").performClick()
        onNodeWithTag(SubscriptionGateTags.Refresh).assertTextEquals("Já assinei — atualizar").performClick()
        onNodeWithContentDescription("Voltar").performClick()

        assertEquals(
            listOf(
                SubscriptionGateIntent.RequestPurchaseInformation,
                SubscriptionGateIntent.RefreshAuthorization,
            ),
            intents,
        )
        assertEquals(1, backPresses)
    }

    @Test
    fun everyMandatoryStateHasAReadableStatusAndExpectedRecovery() = runComposeUiTest {
        val cases = listOf(
            SubscriptionGateState() to ("Assinatura necessária" to "Para continuar, confirme sua assinatura ou receba as informações por e-mail."),
            SubscriptionGateState(status = SubscriptionGateStatus.Sending) to ("Enviando informações" to "Estamos enviando as informações para o e-mail associado à sua conta."),
            SubscriptionGateState(status = SubscriptionGateStatus.Sent, maskedEmail = "a***a@exemplo.com") to ("Informações enviadas" to "As informações foram enviadas para o e-mail associado à sua conta."),
            SubscriptionGateState(
                status = SubscriptionGateStatus.Failed,
                failure = SubscriptionGateFailure.PurchaseInformation,
            ) to ("Enviando informações" to "Não foi possível enviar as informações agora. Tente novamente."),
            SubscriptionGateState(
                status = SubscriptionGateStatus.Failed,
                failure = SubscriptionGateFailure.EmailMissing,
            ) to ("E-mail não cadastrado" to "Sua conta não tem e-mail cadastrado. Adicione um e-mail no seu perfil e tente de novo."),
            SubscriptionGateState(
                status = SubscriptionGateStatus.Failed,
                failure = SubscriptionGateFailure.Authorization,
            ) to ("Verificando assinatura" to "Não foi possível confirmar sua assinatura agora. Tente novamente."),
            SubscriptionGateState(status = SubscriptionGateStatus.Verifying) to ("Verificando assinatura" to "Aguarde enquanto confirmamos sua assinatura."),
            SubscriptionGateState(status = SubscriptionGateStatus.NotAuthorized) to ("Assinatura necessária" to "Ainda não encontramos uma assinatura autorizada. Você pode receber as informações por e-mail ou atualizar a confirmação."),
            SubscriptionGateState(status = SubscriptionGateStatus.Authorized) to ("Assinatura confirmada" to "Acesso confirmado. Finalizando."),
        )
        val renderedState = mutableStateOf(cases.first().first)
        setContent {
            SaqzTheme {
                SubscriptionGateScreen(state = renderedState.value, onIntent = {}, onBack = {})
            }
        }

        cases.forEach { (state, expectedCopy) ->
            runOnIdle {
                renderedState.value = state
            }
            waitForIdle()
            onNodeWithTag(SubscriptionGateTags.Status).assertIsDisplayed()
            onNodeWithTag(SubscriptionGateTags.Status).fetchSemanticsNode().config
                .getOrElseNullable(SemanticsProperties.LiveRegion) { null }
                .also { assertEquals(LiveRegionMode.Polite, it) }
            onNodeWithTag(SubscriptionGateTags.Title).assertTextEquals(expectedCopy.first)
            onNodeWithTag(SubscriptionGateTags.Status).assertTextEquals(expectedCopy.second)
        }

        runOnIdle {
            renderedState.value = SubscriptionGateState(status = SubscriptionGateStatus.Sent)
        }
        waitForIdle()
        assertTrue(onAllNodesWithTag(SubscriptionGateTags.Email).fetchSemanticsNodes().isEmpty())

        runOnIdle {
            renderedState.value = SubscriptionGateState(
                status = SubscriptionGateStatus.Sent,
                maskedEmail = "a***a@exemplo.com",
            )
        }
        waitForIdle()
        onNodeWithTag(SubscriptionGateTags.Email).assertTextEquals("E-mail associado: a***a@exemplo.com")
        onNodeWithTag(SubscriptionGateTags.Request).assertIsDisplayed()
        onNodeWithTag(SubscriptionGateTags.Refresh).assertIsDisplayed()

        runOnIdle {
            renderedState.value = SubscriptionGateState(status = SubscriptionGateStatus.Sending)
        }
        waitForIdle()
        onNodeWithTag(SubscriptionGateTags.Progress).assertIsDisplayed()
        onNodeWithTag(SubscriptionGateTags.Request).assertIsNotEnabled()
        onNodeWithTag(SubscriptionGateTags.Refresh).assertIsNotEnabled()

        runOnIdle {
            renderedState.value = SubscriptionGateState(status = SubscriptionGateStatus.Verifying)
        }
        waitForIdle()
        onNodeWithTag(SubscriptionGateTags.Progress).assertIsDisplayed()
        onNodeWithTag(SubscriptionGateTags.Request).assertIsNotEnabled()
        onNodeWithTag(SubscriptionGateTags.Refresh).assertIsNotEnabled()

        runOnIdle {
            renderedState.value = SubscriptionGateState(status = SubscriptionGateStatus.Authorized)
        }
        waitForIdle()
        onNodeWithTag(SubscriptionGateTags.Progress).assertIsDisplayed()
        assertTrue(onAllNodesWithTag(SubscriptionGateTags.Request).fetchSemanticsNodes().isEmpty())
        assertTrue(onAllNodesWithTag(SubscriptionGateTags.Refresh).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun recoverableFailuresKeepTheRelevantRetryAction() = runComposeUiTest {
        val intents = mutableListOf<SubscriptionGateIntent>()
        val renderedState = mutableStateOf(
            SubscriptionGateState(
                status = SubscriptionGateStatus.Failed,
                failure = SubscriptionGateFailure.PurchaseInformation,
            ),
        )
        setContent {
            SaqzTheme {
                SubscriptionGateScreen(state = renderedState.value, onIntent = intents::add, onBack = {})
            }
        }
        onNodeWithTag(SubscriptionGateTags.Request).assertTextEquals("Tentar novamente").performClick()

        runOnIdle {
            renderedState.value = SubscriptionGateState(
                status = SubscriptionGateStatus.Failed,
                failure = SubscriptionGateFailure.Authorization,
            )
        }
        waitForIdle()
        onNodeWithTag(SubscriptionGateTags.Refresh).assertTextEquals("Tentar novamente").performClick()

        assertEquals(
            listOf(
                SubscriptionGateIntent.RequestPurchaseInformation,
                SubscriptionGateIntent.RefreshAuthorization,
            ),
            intents,
        )
    }

    @Test
    fun accountWithoutEmailDropsTheSendActionAndKeepsTheRefresh() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SubscriptionGateScreen(
                    state = SubscriptionGateState(
                        status = SubscriptionGateStatus.Failed,
                        failure = SubscriptionGateFailure.EmailMissing,
                    ),
                    onIntent = {},
                    onBack = {},
                )
            }
        }

        assertTrue(onAllNodesWithTag(SubscriptionGateTags.Request).fetchSemanticsNodes().isEmpty())
        onNodeWithTag(SubscriptionGateTags.Refresh).assertIsDisplayed()
    }
}
