package br.com.saqz.groups.ui.games.detail

import androidx.compose.ui.test.*
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.data.GroupRoleDto
import br.com.saqz.groups.data.game.*
import br.com.saqz.groups.presentation.games.detail.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class GameDetailScreenTest {
    @Test fun `member sees localized authoritative snapshot`()=runComposeUiTest{screen(state());onNodeWithText("Treino").assertExists();onNodeWithText("12/08/2026 às 19:30").assertExists();onNodeWithText("Arena").assertExists();onNodeWithText("21 vagas").assertExists();onNodeWithText("Valor: R$ 25,00").assertExists()}
    @Test fun `athlete never sees organizer actions`()=runComposeUiTest{screen(state(role=GroupRoleDto.ATHLETE));onNodeWithTag(GameDetailTags.Edit).assertDoesNotExist();onNodeWithTag(GameDetailTags.Publish).assertDoesNotExist()}
    @Test fun `draft organizer sees edit and publish only`()=runComposeUiTest{screen(state());onNodeWithTag(GameDetailTags.Edit).assertExists();onNodeWithTag(GameDetailTags.Publish).assertExists();onNodeWithTag(GameDetailTags.Cancel).assertDoesNotExist();onNodeWithTag(GameDetailTags.Complete).assertDoesNotExist()}
    @Test fun `published organizer sees edit cancel and complete`()=runComposeUiTest{screen(state(status=GameStatusDto.PUBLISHED));onNodeWithTag(GameDetailTags.Edit).assertExists();onNodeWithTag(GameDetailTags.Cancel).assertExists();onNodeWithTag(GameDetailTags.Complete).assertExists();onNodeWithTag(GameDetailTags.Publish).assertDoesNotExist()}
    @Test fun `cancel confirmation explains manual finance review`()=runComposeUiTest{val intents=mutableListOf<GameDetailIntent>();screen(state(status=GameStatusDto.PUBLISHED).copy(pendingAction=GameLifecycleAction.CANCEL),intents::add);onNodeWithText("Cancelar este jogo?").assertExists();onNodeWithText("O cancelamento exige revisão financeira manual. Nenhum reembolso é automático.").assertExists();onNodeWithTag(GameDetailTags.Confirm).performClick();waitForIdle();assertEquals(GameDetailIntent.ConfirmLifecycle,intents.single())}
    @Test fun `cancelled and completed games are read only`()=runComposeUiTest{screen(state(status=GameStatusDto.CANCELLED));onNodeWithText("Este jogo está encerrado e não pode mais ser alterado.").assertExists();onNodeWithText("O cancelamento exige revisão financeira manual. Nenhum reembolso é automático.").assertExists();onNodeWithTag(GameDetailTags.Edit).assertDoesNotExist();onNodeWithTag(GameDetailTags.Cancel).assertDoesNotExist()}
    @Test fun `conflict offers typed reload without hiding snapshot`()=runComposeUiTest{val intents=mutableListOf<GameDetailIntent>();screen(state().copy(error=GameDetailError.CONFLICT,reloadAvailable=true),intents::add);onNodeWithText("Treino").assertExists();onNodeWithText("O jogo mudou. Recarregue antes de continuar.").assertExists();onNodeWithTag(GameDetailTags.Reload).performClick();waitForIdle();assertEquals(GameDetailIntent.Reload,intents.single())}

    private fun androidx.compose.ui.test.ComposeUiTest.screen(value:GameDetailState,onIntent:(GameDetailIntent)->Unit={})=setContent{SaqzTheme{GameDetailScreen(value,onIntent)}}
    private fun state(role:GroupRoleDto=GroupRoleDto.OWNER,status:GameStatusDto=GameStatusDto.DRAFT):GameDetailState{val game=GameDto("game","group","Treino",GameVenueDto(null,"Arena","Rua 1"),"2026-08-12","19:30:00","America/Sao_Paulo","2026-08-12T22:30:00Z",90,24,"2026-08-12T19:00:00Z",2500,"Notas",status,7,3,21,2,status==GameStatusDto.CANCELLED);return GameDetailState("group","game",role,game,"\"7\"",isLoading=false)}
}
