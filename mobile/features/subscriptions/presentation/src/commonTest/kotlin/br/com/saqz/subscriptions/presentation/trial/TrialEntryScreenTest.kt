package br.com.saqz.subscriptions.presentation.trial

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.subscriptions.domain.trial.TrialAccess
import br.com.saqz.subscriptions.domain.trial.TrialStatus
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class TrialEntryScreenTest {
    private fun access(status: TrialStatus, endsAt: String?) = TrialAccess(
        status, "2026-10-13T12:00:00Z", endsAt, "2026-10-29T12:00:00Z", false, false, 3, null, true, null,
    )

    @Test
    fun `expired trial says it ended and that the groups are kept and offers the plans`() = runComposeUiTest {
        var plans = 0
        setContent {
            SaqzTheme {
                TrialEntryScreen(TrialEntryState(loading = false, access = access(TrialStatus.Expired, "2026-10-27T15:00:00Z")),
                    {}, {}, { plans++ }, purchasesAvailable = true)
            }
        }
        onNodeWithText("Seu teste terminou").assertExists()
        onNodeWithText("Seus grupos e o histórico estão guardados. Assine para a galera voltar a confirmar presença.").assertExists()
        onNodeWithText("Seu próximo grupo começa aqui").assertDoesNotExist()
        onNodeWithText("Ver planos").performClick()
        assertEquals(1, plans)
    }

    @Test
    fun `expired trial without store plans does not ask to subscribe`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                TrialEntryScreen(TrialEntryState(loading = false, access = access(TrialStatus.Expired, "2026-10-27T15:00:00Z")),
                    {}, {}, {}, purchasesAvailable = false)
            }
        }
        onNodeWithTag(TrialEntryTags.Ended).assertExists()
        onNodeWithText("Ver planos").assertDoesNotExist()
    }

    @Test
    fun `other denied states keep the generic text`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                TrialEntryScreen(TrialEntryState(loading = false, access = access(TrialStatus.Ineligible, null)),
                    {}, {}, {}, purchasesAvailable = true)
            }
        }
        onNodeWithText("Seu próximo grupo começa aqui").assertExists()
        onNodeWithTag(TrialEntryTags.Ended).assertDoesNotExist()
    }
}
