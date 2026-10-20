package br.com.saqz.groups.presentation.ui.setup

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.model.GroupComposition
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.model.GroupPlayStyle
import br.com.saqz.groups.model.GroupSetupForm
import br.com.saqz.groups.presentation.setup.GroupSetupError
import br.com.saqz.groups.presentation.setup.GroupSetupIntent
import br.com.saqz.groups.presentation.setup.GroupSetupMode
import br.com.saqz.groups.presentation.setup.GroupSetupState
import kotlin.test.Test
import kotlin.test.assertEquals

/** DEB-102 (a): card de vagas por posição no 5-1, total visível e Salvar bloqueado até a soma bater. */
@OptIn(ExperimentalTestApi::class)
class GroupPositionSlotsScreenTest {
    private fun state(
        slots: Map<AthletePosition, Int>,
        capacity: Int = 18,
        playStyle: GroupPlayStyle = GroupPlayStyle.FIVE_ONE,
        ready: Boolean = true,
    ) = GroupSetupState(
        mode = GroupSetupMode.Edit("group-1"),
        canManageGameConfig = true,
        positionSlotsReady = ready,
        positionSlots = slots,
        form = GroupSetupForm(
            name = "Vôlei do CERET", modality = GroupModality.COURT_VOLLEYBALL, composition = GroupComposition.MIXED,
            playStyle = playStyle, defaultCapacity = capacity,
        ),
    )

    private val eighteen = mapOf(
        AthletePosition.LEVANTADOR to 3, AthletePosition.OPOSTO to 3, AthletePosition.PONTA to 6, AthletePosition.CENTRAL to 6,
    )

    private fun creating(slots: Map<AthletePosition, Int>, capacity: Int = 18) =
        state(slots, capacity).copy(mode = GroupSetupMode.Create, canManageGameConfig = false)

    @Test
    fun criacaoCincoUmMostraOPainelEMantemOCriarHabilitadoComASomaErrada() = runComposeUiTest {
        val intents = mutableListOf<GroupSetupIntent>()
        show(creating(eighteen - AthletePosition.PONTA + (AthletePosition.PONTA to 4)), intents::add)
        onNodeWithTag(GroupSetupTags.Advanced).performScrollTo().performClick()
        onNodeWithTag(GroupPositionSlotsTags.Section).performScrollTo().assertExists()
        onNodeWithText("Faltam 2 vagas para fechar 18.").performScrollTo().assertExists()
        onNodeWithText("16 de 18").performScrollTo().assertExists()
        onNodeWithTag(GroupSetupTags.Submit).assertIsEnabled().performClick()
        assertEquals(GroupSetupIntent.Submit, intents.last())
    }

    @Test
    fun somaErradaRecusadaAbreMaisDetalhesSozinhoEContaNoAvisoGeral() = runComposeUiTest {
        val wrong = creating(eighteen - AthletePosition.PONTA + (AthletePosition.PONTA to 4))
        show(wrong.copy(errors = setOf(GroupSetupError.PositionSlotsSumMismatch)))
        onNodeWithTag(GroupSetupTags.ErrorBanner).assertExists()
        onNodeWithText("Falta 1 informação para criar o grupo. Ela está marcada abaixo.").assertExists()
        onNodeWithTag(GroupPositionSlotsTags.Section).performScrollTo().assertExists()
    }

    @Test
    fun erroForaDeMaisDetalhesNaoAbreASecao() = runComposeUiTest {
        show(creating(eighteen).copy(errors = setOf(GroupSetupError.NameRequired)))
        onNodeWithTag(GroupSetupTags.ErrorBanner).assertExists()
        onNodeWithTag(GroupPositionSlotsTags.Section).assertDoesNotExist()
    }

    @Test
    fun criacaoCincoUmComASomaCertaLiberaOCriar() = runComposeUiTest {
        show(creating(eighteen))
        onNodeWithTag(GroupSetupTags.Advanced).performScrollTo().performClick()
        onNodeWithText("As posições fecham os 18 jogadores.").performScrollTo().assertExists()
        onNodeWithTag(GroupSetupTags.Submit).assertIsEnabled()
    }

    @Test
    fun criacaoSeisZeroNaoTemCardNemBloqueio() = runComposeUiTest {
        show(creating(emptyMap()).copy(form = creating(emptyMap()).form.copy(playStyle = GroupPlayStyle.SIX_ZERO)))
        onNodeWithTag(GroupSetupTags.Advanced).performScrollTo().performClick()
        onNodeWithTag(GroupPositionSlotsTags.Section).assertDoesNotExist()
        onNodeWithTag(GroupSetupTags.Submit).assertIsEnabled()
    }

    private fun ComposeUiTest.show(state: GroupSetupState, onIntent: (GroupSetupIntent) -> Unit = {}) =
        setContent { SaqzTheme { GroupSetupScreen(state, onIntent, {}) } }

    @Test
    fun cincoUmMostraOTotalEAsPosicoesQueFecham() = runComposeUiTest {
        show(state(eighteen))
        onNodeWithTag(GroupSetupTags.Capacity).performScrollTo().assertExists()
        onNodeWithTag(GroupPositionSlotsTags.Section).performScrollTo().assertExists()
        AthletePosition.entries.forEach { onNodeWithTag(GroupPositionSlotsTags.position(it)).performScrollTo().assertExists() }
        onNodeWithText("As posições fecham os 18 jogadores.").performScrollTo().assertExists()
        onNodeWithTag(GroupSetupTags.Submit).assertIsEnabled()
    }

    @Test
    fun faltamVagasMostraAContaEMantemOSalvarHabilitado() = runComposeUiTest {
        show(state(eighteen - AthletePosition.PONTA + (AthletePosition.PONTA to 4)))
        onNodeWithText("Faltam 2 vagas para fechar 18.").performScrollTo().assertExists()
        onNodeWithTag(GroupSetupTags.Submit).assertIsEnabled()
    }

    @Test
    fun sobramVagasMostraAContaEMantemOSalvarHabilitado() = runComposeUiTest {
        show(state(eighteen + (AthletePosition.LIBERO to 1)))
        onNodeWithText("Sobra 1 vaga em relação a 18.").performScrollTo().assertExists()
        onNodeWithTag(GroupSetupTags.Submit).assertIsEnabled()
    }

    @Test
    fun seisZeroEJogoSemCardNemBloqueio() = runComposeUiTest {
        show(state(emptyMap(), playStyle = GroupPlayStyle.SIX_ZERO))
        onNodeWithTag(GroupPositionSlotsTags.Section).assertDoesNotExist()
        onNodeWithTag(GroupSetupTags.Submit).assertIsEnabled()
    }

    @Test
    fun semAsPosicoesDoServidorOCardNaoApareceENaoBloqueia() = runComposeUiTest {
        show(state(emptyMap(), ready = false))
        onNodeWithTag(GroupPositionSlotsTags.Section).assertDoesNotExist()
        onNodeWithTag(GroupSetupTags.Submit).assertIsEnabled()
    }

    @Test
    fun sugerirEOsSteppersMandamAsIntencoes() = runComposeUiTest {
        val intents = mutableListOf<GroupSetupIntent>()
        show(state(eighteen), intents::add)
        onNodeWithTag(GroupPositionSlotsTags.Suggest).performScrollTo().performClick()
        assertEquals<List<GroupSetupIntent>>(listOf(GroupSetupIntent.SuggestPositionSlots), intents)
    }
}
