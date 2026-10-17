package br.com.saqz.groups.presentation.ui.gameeditor

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

        onNodeWithText("Seu teste do Organizador terminou").assertExists()
        onNodeWithText(
            "Seus grupos e o histórico continuam aqui. Para marcar jogos e mexer no grupo, assine um plano.",
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

    @Test
    fun `trial warning prints the game date and time and not the trial instant`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                GameEditorScreen(
                    state = create().copy(
                        trialEndsAt = "2026-10-13T19:05:36.796387Z",
                        trialWarningVisible = true,
                        form = create().form.copy(localDate = "2026-10-17", localTime = "19:00"),
                    ),
                    onBack = {},
                    onIntent = {},
                )
            }
        }

        onNodeWithText(
            "Este jogo começa sábado, 17/10 às 19h00, depois do fim do teste. " +
                "O acesso ao grupo nessa data depende do plano ativo da conta.",
        ).performScrollTo().assertExists()
    }

    // ---- Backend v.0.0.17: quadra sem endereço volta como 400 `venue.address`, e o erro cai no campo ----

    private fun refusedAddress(state: GameEditorState = create()) = state.copy(
        form = state.form.copy(venue = GameVenue(name = "Quadra do Parque", address = "")),
        groupHasVenue = false,
        validationErrors = setOf(GameEditorFieldError.VenueAddressRequired),
        submitAttempts = 1,
    )

    @Test
    fun `a refused venue address is marked on the address field and the form stays as it was`() = runComposeUiTest {
        setContent { SaqzTheme { GameEditorScreen(state = refusedAddress(), onBack = {}, onIntent = {}) } }
        waitForIdle()

        onNodeWithText("Informe o endereço da quadra.").assertIsDisplayed()
        onNodeWithText("Corrija o endereço da quadra para continuar.").assertExists()
        onNodeWithTag(GameEditorTags.SaveFailure).assertDoesNotExist()
        onNodeWithText("Quadra do Parque").assertExists()
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
