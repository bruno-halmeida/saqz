package br.com.saqz.groups.presentation.monthlygeneration

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.finance.FinanceError
import br.com.saqz.groups.presentation.FakeFinanceStatementGateway
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.FakeGroupMembershipGateway
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.ui.finance.groupcash.GroupCashboxRoot
import br.com.saqz.groups.presentation.ui.finance.groupcash.GroupCashboxTags
import br.com.saqz.groups.presentation.ui.finance.groupcash.GroupCashboxViewModel
import br.com.saqz.groups.port.GroupNowPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class MonthlyGenerationScreenTest {
    @Test fun `empty form cannot review or confirm`() = runComposeUiTest {
        setContent { SaqzTheme { MonthlyGenerationScreen(MonthlyGenerationState(isLoading = false), {}, {}) } }
        onNodeWithText("Não há mensalistas ativos neste grupo.").assertExists()
        onNodeWithTag(MonthlyGenerationTags.Review).assertIsNotEnabled()
        onNodeWithTag(MonthlyGenerationTags.Confirm).assertDoesNotExist()
    }

    @Test fun `review displays only selected names exact money and saving disables actions`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                MonthlyGenerationScreen(
                    MonthlyGenerationState(
                        isLoading = false, members = listOf(MonthlyMemberUi("ana", "Ana"), MonthlyMemberUi("bia", "Bia")),
                        form = validForm, reviewing = true, isSaving = true,
                    ), {}, {},
                )
            }
        }
        onNodeWithText("Mês 2026-08 · Vencimento 12/08/2026\nR$\u00A0123,45 por pessoa · Pessoas selecionadas: 1").assertExists()
        onNodeWithText("Ana").assertExists()
        onNodeWithText("Bia").assertDoesNotExist()
        onNodeWithTag(MonthlyGenerationTags.Confirm).assertIsNotEnabled()
        onNodeWithTag(MonthlyGenerationTags.Edit).assertIsNotEnabled()
    }

    @Test fun `load failure exposes retry but no generation controls`() = runComposeUiTest {
        val intents = mutableListOf<MonthlyGenerationIntent>()
        setContent {
            SaqzTheme {
                MonthlyGenerationScreen(
                    MonthlyGenerationState(isLoading = false, loadFailed = true, error = GroupUiError.AccessDenied),
                    {}, { intents += it },
                )
            }
        }
        onNodeWithText("Você não tem permissão para criar cobranças do mês neste grupo.").assertExists()
        onNodeWithTag(MonthlyGenerationTags.Review).assertDoesNotExist()
        onNodeWithTag(MonthlyGenerationTags.Retry).performClick()
        assertEquals(listOf<MonthlyGenerationIntent>(MonthlyGenerationIntent.Retry), intents)
    }

    @Test fun `real root connects fields review failure retry and success navigation`() = runComposeUiTest {
        val gateway = MonthlyRecordingGateway().apply { result = SaqzResult.Failure(FinanceError.Data(DataError.Unknown)) }
        val vm = monthlyVm(gateway)
        var generated = 0
        var backed = 0
        setContent { SaqzTheme { MonthlyGenerationRoot("selected-group", { backed++ }, { generated++ }, vm) } }
        onNodeWithText("80,00").performTextReplacement("123,45")
        onNodeWithText("31/08/2026").performTextReplacement("12/08/2026")
        onNodeWithTag(MonthlyGenerationTags.member("ana")).performScrollTo().performClick()
        onNodeWithTag(MonthlyGenerationTags.Review).performScrollTo().performClick()
        onNodeWithTag(MonthlyGenerationTags.Summary).assertExists()
        assertTrue(gateway.commands.isEmpty())
        onNodeWithTag(MonthlyGenerationTags.Edit).performScrollTo().performClick()
        assertTrue(gateway.commands.isEmpty())
        onNodeWithTag(MonthlyGenerationTags.Review).performScrollTo().performClick()
        onNodeWithTag(MonthlyGenerationTags.Confirm).performScrollTo().performClick()
        waitForIdle()
        assertEquals(0, generated)
        onNodeWithText("Não foi possível criar as cobranças. Mantivemos os dados que você preencheu. Tente novamente.").assertExists()
        gateway.result = SaqzResult.Success(br.com.saqz.groups.domain.finance.ChargeList(emptyList()))
        onNodeWithTag(MonthlyGenerationTags.Confirm).performScrollTo().performClick()
        waitForIdle()
        assertEquals(1, generated)
        assertEquals(0, backed)
        assertEquals(gateway.commands[0], gateway.commands[1])
        assertEquals(setOf("ana"), gateway.commands[1].second.memberIds)
        assertEquals(12345L, gateway.commands[1].second.amountCents)
    }

    @Test fun `cashbox button opens monthly generation for selected group without writes`() = runComposeUiTest {
        val gateway = MonthlyRecordingGateway()
        val cashbox = GroupCashboxViewModel(
            "selected-group", FakeGroupGateway(), FakeGroupMembershipGateway(), FakeFinanceStatementGateway(), gateway,
            GroupNowPort { Instant.parse("2026-08-12T12:00:00Z") },
        )
        var opened: String? = null
        setContent {
            SaqzTheme {
                GroupCashboxRoot(
                    "selected-group", {}, {}, onOpenMonthlyGeneration = { opened = it }, viewModel = cashbox,
                )
            }
        }
        onNodeWithTag(GroupCashboxTags.GenerateMonthly).performScrollTo().performClick()
        waitForIdle()
        assertEquals("selected-group", opened)
        assertTrue(gateway.commands.isEmpty())
    }
}
