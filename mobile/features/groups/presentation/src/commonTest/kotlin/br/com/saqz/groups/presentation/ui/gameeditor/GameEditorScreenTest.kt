package br.com.saqz.groups.presentation.ui.gameeditor

import br.com.saqz.groups.presentation.ui.components.GameFeeBlockTags
import br.com.saqz.groups.presentation.GameFeeChoice
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.domain.group.GroupRegularSlot
import br.com.saqz.groups.domain.group.GroupWeekday
import br.com.saqz.groups.presentation.gameeditor.GameEditorFieldError
import br.com.saqz.groups.presentation.gameeditor.GameEditorFields
import br.com.saqz.groups.presentation.gameeditor.GameEditorIntent
import br.com.saqz.groups.presentation.gameeditor.GameEditorState
import br.com.saqz.groups.presentation.ui.LocalOpenSubscriptionPlans
import br.com.saqz.groups.presentation.ui.SubscriptionRequiredTags
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GameEditorScreenTest {
    @Test
    fun `touch picker saves the tapped day hour and minute`() = runComposeUiTest {
        val intents = mutableListOf<GameEditorIntent>()
        setContent { SaqzTheme { GameEditorScreen(state = create(), onBack = {}, onIntent = { intents += it }) } }

        onNodeWithTag(GameEditorTags.Date).performScrollTo().performClick()
        // A data do formulário é a primeira do intervalo (já passou): 2026-08-06 é quinta.
        onNodeWithTag(GameDateTimePickerTags.day("2026-08-06")).assertIsSelected()
        onNodeWithTag(GameDateTimePickerTags.hour(20)).assertIsSelected()
        onNodeWithTag(GameDateTimePickerTags.day("2026-08-10")).performScrollTo().performClick()
        onNodeWithTag(GameDateTimePickerTags.hour(19)).performScrollTo().performClick()
        onNodeWithTag(GameDateTimePickerTags.minute(45)).performScrollTo().performClick()
        onNodeWithText("Segunda, 10 de agosto · 19h45").assertExists()
        onNodeWithText("Salvar data e horário").performClick()

        assertEquals(listOf<GameEditorIntent>(GameEditorIntent.SaveDateTime("2026-08-10", "19:45")), intents)
    }

    @Test
    fun `touch picker chips speak their full label and carry the selected state`() = runComposeUiTest {
        setContent { SaqzTheme { GameEditorScreen(state = create(), onBack = {}, onIntent = {}) } }

        onNodeWithTag(GameEditorTags.Date).performScrollTo().performClick()

        onNodeWithContentDescription("segunda, 10 de agosto").assertExists().assertIsNotSelected()
        onNodeWithContentDescription("quinta, 6 de agosto").assertIsSelected()
        onNodeWithContentDescription("19 horas").assertExists().assertIsNotSelected()
        onNodeWithContentDescription("20 horas").assertIsSelected()
        onNodeWithContentDescription("1 hora").assertExists()
    }

    @Test
    fun `the counter sends the neighbour on the grid`() = runComposeUiTest {
        val intents = mutableListOf<GameEditorIntent>()
        val state = create().let { it.copy(form = it.form.copy(durationMinutes = 120)) }
        setContent { SaqzTheme { GameEditorScreen(state = state, onBack = {}, onIntent = { intents += it }) } }

        onNodeWithText("2h").performScrollTo().assertExists()
        onNodeWithContentDescription("Aumentar a duração").performScrollTo().performClick()
        assertEquals(GameEditorIntent.SelectDuration(150), intents.last())
        onNodeWithContentDescription("Diminuir a duração").performScrollTo().performClick()
        assertEquals(GameEditorIntent.SelectDuration(90), intents.last())
    }

    @Test
    fun `invalid duration shows the error in the counter and not in the top card`() = runComposeUiTest {
        val state = create().let {
            it.copy(
                form = it.form.copy(durationMinutes = 45),
                validationErrors = setOf(GameEditorFieldError.DurationInvalid),
            )
        }
        setContent { SaqzTheme { GameEditorScreen(state = state, onBack = {}, onIntent = {}) } }

        onNodeWithText("De 1h a 8h. Até 3h, de 30 em 30 minutos.").performScrollTo().assertExists()
        onAllNodesWithText("Confira as informações").assertCountEquals(0)
    }

    @Test
    fun `duration outside the grid shows the value and moves to the neighbour`() = runComposeUiTest {
        val intents = mutableListOf<GameEditorIntent>()
        val state = create().let { it.copy(form = it.form.copy(durationMinutes = 195)) }
        setContent { SaqzTheme { GameEditorScreen(state = state, onBack = {}, onIntent = { intents += it }) } }

        onNodeWithText("3h15").performScrollTo().assertExists()
        onNodeWithContentDescription("Diminuir a duração").performScrollTo().performClick()
        assertEquals(GameEditorIntent.SelectDuration(180), intents.last())
    }

    @Test
    fun `recurrence toggle emits the intent`() = runComposeUiTest {
        val intents = mutableListOf<GameEditorIntent>()
        setContent { SaqzTheme { GameEditorScreen(state = create(), onBack = {}, onIntent = { intents += it }) } }

        onNodeWithTag(GameEditorTags.RecurrenceToggle).performScrollTo().assertIsOff().performClick()

        assertEquals(listOf<GameEditorIntent>(GameEditorIntent.ToggleRecurring(true)), intents)
    }

    @Test
    fun `recurring shows the group slots and the new one`() = runComposeUiTest {
        setContent {
            SaqzTheme { GameEditorScreen(state = create(recurring = true), onBack = {}, onIntent = {}) }
        }

        onNodeWithTag(GameEditorTags.RecurrenceToggle).performScrollTo().assertIsOn()
        onNodeWithText("Terça · 19h30").assertExists()
        onNodeWithText("Quinta · 20h00 · novo").assertExists()
        onNodeWithText("Os próximos jogos usam a quadra do grupo.").assertExists()
    }

    @Test
    fun `recurrence is absent when editing`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                GameEditorScreen(
                    state = create().copy(recurrenceOffered = false, versionToken = "etag-1"),
                    onBack = {},
                    onIntent = {},
                )
            }
        }

        onNodeWithTag(GameEditorTags.Recurrence).assertDoesNotExist()
    }

    @Test
    fun `recurrence failure offers a retry that submits again`() = runComposeUiTest {
        val intents = mutableListOf<GameEditorIntent>()
        setContent {
            SaqzTheme {
                GameEditorScreen(
                    state = create(recurring = true).copy(recurrenceFailed = true),
                    onBack = {},
                    onIntent = { intents += it },
                )
            }
        }

        onNodeWithTag(GameEditorTags.RecurrenceFailure).assertExists()
        onNodeWithText("Tentar").performClick()

        assertEquals(listOf<GameEditorIntent>(GameEditorIntent.Submit), intents)
    }

    @Test
    fun `subscription required dialog offers the plans and keeps the form on not now`() = runComposeUiTest {
        val intents = mutableListOf<GameEditorIntent>()
        var plansOpened = 0
        setContent {
            SaqzTheme {
                CompositionLocalProvider(LocalOpenSubscriptionPlans provides { plansOpened++ }) {
                    GameEditorScreen(
                        state = create().copy(subscriptionRequired = true, trialEnded = true),
                        onBack = {},
                        onIntent = { intents += it },
                    )
                }
            }
        }

        onNodeWithText("Seu teste terminou").assertExists()
        onNodeWithText(
            "Seus grupos e o histórico estão guardados. Assine para a galera voltar a confirmar presença.",
        ).assertExists()
        onNodeWithTag(SubscriptionRequiredTags.NotNow).performClick()
        assertEquals(listOf<GameEditorIntent>(GameEditorIntent.DismissSubscriptionRequired), intents)
        onNodeWithTag(SubscriptionRequiredTags.ViewPlans).performClick()
        assertEquals(1, plansOpened)
    }

    @Test
    fun `inactive plan dialog uses the neutral title`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                GameEditorScreen(state = create().copy(subscriptionRequired = true), onBack = {}, onIntent = {})
            }
        }

        onNodeWithText("Seu plano não está ativo").assertExists()
    }

    private fun blocked(owner: Boolean, trial: Boolean) = create().copy(
        planBlocked = true,
        planLastDay = "2026-10-12",
        planLimitIsTrial = trial,
        isOwner = owner,
        form = create().form.copy(localDate = "2026-10-17", localTime = "19:00"),
    )

    @Test
    fun `owner on a paid plan reads the plan text with the last day and no time`() = runComposeUiTest {
        setContent { SaqzTheme { GameEditorScreen(state = blocked(owner = true, trial = false), onBack = {}, onIntent = {}) } }

        onNodeWithText(
            "Este jogo começa depois do fim do seu plano. Escolha uma data até 12/10/2026 ou assine para marcar mais jogos.",
        ).performScrollTo().assertExists()
        onNodeWithTag(GameEditorTags.PlanBlockViewPlans).performScrollTo().assertExists()
        onNodeWithTag(GameEditorTags.Submit).assertIsNotEnabled()
    }

    @Test
    fun `owner in the trial reads the trial text`() = runComposeUiTest {
        setContent { SaqzTheme { GameEditorScreen(state = blocked(owner = true, trial = true), onBack = {}, onIntent = {}) } }

        onNodeWithText(
            "Este jogo começa depois do fim do seu teste. Escolha uma data até 12/10/2026 ou assine para marcar mais jogos.",
        ).performScrollTo().assertExists()
    }

    @Test
    fun `owner whose last day already passed reads the text without a date for plan and for trial`() = runComposeUiTest {
        var trial by mutableStateOf(false)
        setContent {
            SaqzTheme {
                GameEditorScreen(
                    state = blocked(owner = true, trial = trial).copy(planLastDayPassed = true),
                    onBack = {},
                    onIntent = {},
                )
            }
        }

        onNodeWithText("Este jogo começa depois do fim do seu plano. Assine para marcar mais jogos.").performScrollTo().assertExists()
        trial = true
        waitForIdle()
        onNodeWithText("Este jogo começa depois do fim do seu teste. Assine para marcar mais jogos.").performScrollTo().assertExists()
        onNodeWithTag(GameEditorTags.PlanBlockViewPlans).performScrollTo().assertExists()
    }

    @Test
    fun `the last day in the block text keeps the two digit day like the rest of the app`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                GameEditorScreen(state = blocked(owner = true, trial = false).copy(planLastDay = "2026-11-01"), onBack = {}, onIntent = {})
            }
        }

        onNodeWithText(
            "Este jogo começa depois do fim do seu plano. Escolha uma data até 01/11/2026 ou assine para marcar mais jogos.",
        ).performScrollTo().assertExists()
    }

    @Test
    fun `admin reads the group plan text without an offer to subscribe`() = runComposeUiTest {
        setContent { SaqzTheme { GameEditorScreen(state = blocked(owner = false, trial = false), onBack = {}, onIntent = {}) } }

        onNodeWithText("Este jogo começa depois do fim do plano do grupo.").performScrollTo().assertExists()
        onAllNodesWithTag(GameEditorTags.PlanBlockViewPlans).assertCountEquals(0)
    }

    @Test
    fun `without the block the submit button is enabled and no plan text shows`() = runComposeUiTest {
        setContent { SaqzTheme { GameEditorScreen(state = create(), onBack = {}, onIntent = {}) } }

        onAllNodesWithTag(GameEditorTags.PlanBlock).assertCountEquals(0)
        onNodeWithTag(GameEditorTags.Submit).assertIsEnabled()
    }

    // ---- DEB-050: o endereço da quadra é sempre opcional ----

    @Test
    fun `a venue without address shows no address error and no missing item`() = runComposeUiTest {
        val state = create().copy(
            form = create().form.copy(venue = GameVenue(name = "Quadra do Parque", address = "")),
            groupHasVenue = false,
        )
        setContent { SaqzTheme { GameEditorScreen(state = state, onBack = {}, onIntent = {}) } }
        waitForIdle()

        onNodeWithTag(GameEditorTags.VenueAddress).performScrollTo().assertExists()
        onNodeWithText("Informe o endereço da quadra.").assertDoesNotExist()
        onNodeWithText("Quadra do Parque").assertExists()
    }

    // ---- DEB-051: sem "Recado para a galera" ----

    // ---- Valor por jogo ----

    @Test
    fun `the fee field shows the inherited value and the avulso help`() = runComposeUiTest {
        val base = create()
        val state = base.copy(form = base.form.copy(gameFeeCents = 2500, gameFeeText = "25,00", gameFeeChoice = GameFeeChoice.Charge))
        setContent { SaqzTheme { GameEditorScreen(state = state, onBack = {}, onIntent = {}) } }
        waitForIdle()

        onNodeWithTag(GameEditorTags.GameFee).performScrollTo().assertExists()
        onNodeWithText("25,00").performScrollTo().assertExists()
        onNodeWithText("Cobrado de quem joga avulso.").performScrollTo().assertExists()
    }

    @Test
    fun `typing in the fee field sends the typed text`() = runComposeUiTest {
        val received = mutableListOf<GameEditorIntent>()
        val charge = create().let { it.copy(form = it.form.copy(gameFeeChoice = GameFeeChoice.Charge)) }
        setContent { SaqzTheme { GameEditorScreen(state = charge, onBack = {}, onIntent = { received += it }) } }
        waitForIdle()

        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(GameEditorTags.GameFee)), useUnmergedTree = true)
            .performScrollTo().performTextInput("30")

        assertEquals(GameEditorIntent.UpdateGameFee("30"), received.last { it is GameEditorIntent.UpdateGameFee })
    }

    @Test
    fun `a zero fee marks the field and the missing item`() = runComposeUiTest {
        val base = create()
        val state = base.copy(
            form = base.form.copy(gameFeeCents = 0, gameFeeText = "0,00", gameFeeChoice = GameFeeChoice.Charge),
            validationErrors = setOf(GameEditorFieldError.GameFeeInvalid),
            submitAttempts = 1,
        )
        setContent { SaqzTheme { GameEditorScreen(state = state, onBack = {}, onIntent = {}) } }
        waitForIdle()

        onNodeWithText("Informe um valor maior que R$ 0,00.").performScrollTo().assertIsDisplayed()
        onNodeWithText("Corrija o valor por jogo para continuar.").assertExists()
    }

    @Test
    fun `nothing chosen shows the field error and the top banner (JOGO-008)`() = runComposeUiTest {
        val state = create().copy(validationErrors = setOf(GameEditorFieldError.GameFeeMissing), submitAttempts = 1)
        setContent { SaqzTheme { GameEditorScreen(state = state, onBack = {}, onIntent = {}) } }
        waitForIdle()

        onNodeWithText("Escolha o valor ou Sem diária.").performScrollTo().assertIsDisplayed()
        onNodeWithText("Corrija o valor por jogo para continuar.").assertExists()
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(GameEditorTags.GameFee)), useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun `the two options send the choice and Sem diária explains itself`() = runComposeUiTest {
        val received = mutableListOf<GameEditorIntent>()
        var state by mutableStateOf(create())
        setContent { SaqzTheme { GameEditorScreen(state = state, onBack = {}, onIntent = { received += it }) } }
        waitForIdle()

        onNodeWithTag(GameFeeBlockTags.NoFee, useUnmergedTree = true).performScrollTo().performClick()
        assertEquals(GameEditorIntent.SelectGameFeeChoice(GameFeeChoice.NoFee), received.last())
        state = state.copy(form = state.form.copy(gameFeeChoice = GameFeeChoice.NoFee))
        waitForIdle()
        onNodeWithText("O avulso joga sem pagar por jogo.").performScrollTo().assertExists()
        onNodeWithTag(GameFeeBlockTags.Charge, useUnmergedTree = true).performClick()
        assertEquals(GameEditorIntent.SelectGameFeeChoice(GameFeeChoice.Charge), received.last())
    }

    @Test
    fun `Cobrar diária shows the monthly suggestion in the help line`() = runComposeUiTest {
        val state = create().let { it.copy(monthlyFeeCents = 8000, form = it.form.copy(gameFeeChoice = GameFeeChoice.Charge)) }
        setContent { SaqzTheme { GameEditorScreen(state = state, onBack = {}, onIntent = {}) } }
        waitForIdle()

        onNodeWithText("Sugestão: mensalidade ÷ 4 (R$\u00A080 ÷ 4).").performScrollTo().assertExists()
    }

    @Test
    fun `the editor has no note field and no promise about the invite`() = runComposeUiTest {
        setContent { SaqzTheme { GameEditorScreen(state = create(), onBack = {}, onIntent = {}) } }
        waitForIdle()

        onNodeWithText("Recado para a galera").assertDoesNotExist()
        onNodeWithText("Opcional. Aparece no convite do jogo.").assertDoesNotExist()
    }

    @Test
    fun `the generic save failure says create when creating`() = runComposeUiTest {
        setContent {
            SaqzTheme { GameEditorScreen(state = create().copy(saveFailed = true), onBack = {}, onIntent = {}) }
        }

        onNodeWithText("Não foi possível criar o jogo").assertExists()
        onNodeWithText("Não foi possível salvar o jogo").assertDoesNotExist()
    }

    @Test
    fun `the generic save failure says save when editing`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                GameEditorScreen(
                    state = create().copy(versionToken = "etag-1", saveFailed = true),
                    onBack = {},
                    onIntent = {},
                )
            }
        }

        onNodeWithText("Não foi possível salvar o jogo").assertExists()
        onNodeWithText("Não foi possível criar o jogo").assertDoesNotExist()
    }

    @Test
    fun `an old game with 3 spots shows the minimum of 4 and says why`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                GameEditorScreen(
                    state = create().let { it.copy(form = it.form.copy(capacity = 4), versionToken = "etag-1", legacyCapacity = 3) },
                    onBack = {},
                    onIntent = {},
                )
            }
        }

        onNodeWithText("Este jogo tinha 3 vagas. O mínimo agora é 4, e salvar grava 4.").assertExists()
    }

    @Test
    fun `a current game does not show the old capacity warning`() = runComposeUiTest {
        setContent { SaqzTheme { GameEditorScreen(state = create(), onBack = {}, onIntent = {}) } }

        onNodeWithText("Vagas neste jogo").assertExists()
        onAllNodesWithText("O mínimo agora é 4", substring = true).assertCountEquals(0)
    }

    private fun create(recurring: Boolean = false) = GameEditorState(
        isLoading = false,
        groupName = "Vôlei do CERET",
        zoneId = "America/Sao_Paulo",
        form = GameEditorFields(
            localDate = "2026-08-06",
            localTime = "20:00",
            durationMinutes = 120,
            venue = GameVenue(name = "CERET", address = "R. Canuto Abreu"),
            capacity = 12,
            confirmationLeadMinutes = 360,
            recurring = recurring,
        ),
        recurrenceOffered = true,
        regularSlots = listOf(GroupRegularSlot(weekday = GroupWeekday.TUESDAY, startTime = "19:30", durationMinutes = 120)),
        groupHasVenue = true,
    )
}
