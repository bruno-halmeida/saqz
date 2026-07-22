package br.com.saqz.groups.ui.games.list

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.presentation.games.list.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class GamesScreenTest {
    @Test fun `loading shows no prior content`()=runComposeUiTest{screen(base.copy(isLoading=true,upcoming=emptyList()));onNodeWithContentDescription("Carregando").assertExists();onNodeWithTag(GamesTags.card("future")).assertDoesNotExist()}
    @Test fun `upcoming empty state is explicit`()=runComposeUiTest{screen(base.copy(upcoming=emptyList()));onNodeWithText("Nenhum jogo próximo").assertExists()}
    @Test fun `history empty state follows selected tab`()=runComposeUiTest{screen(base.copy(past=emptyList()),GamesTab.PAST);onNodeWithText("Nenhum jogo no histórico").assertExists()}
    @Test fun `error offers typed retry`()=runComposeUiTest{val intents=mutableListOf<GamesScreenIntent>();screen(base.copy(error=GamesLoadError.UNAVAILABLE,upcoming=emptyList()),onIntent=intents::add);onNodeWithTag(GamesTags.Retry).performClick();assertEquals(listOf<GamesScreenIntent>(GamesScreenIntent.Retry),intents)}
    @Test fun `owner and admin see create action`()=runComposeUiTest{screen(base.copy(role=GroupRole.ADMIN));onNodeWithTag(GamesTags.Create).assertExists()}
    @Test fun `athlete never sees organizer create action`()=runComposeUiTest{screen(base.copy(role=GroupRole.ATHLETE));onNodeWithTag(GamesTags.Create).assertDoesNotExist()}
    @Test fun `card exposes localized schedule venue status and spots`()=runComposeUiTest{screen(base);onNodeWithText("Treino semanal").assertExists();onNodeWithText("12/08/2026 às 19:30").assertExists();onNodeWithText("Arena Central").assertExists();onNodeWithText("Publicado").assertExists();onNodeWithText("21 vagas").assertExists()}
    @Test fun `status and waitlist labels never expose enum identifiers`()=runComposeUiTest{val games=listOf(item("draft",GameStatus.Draft,0,3),item("cancel",GameStatus.Cancelled,0,0),item("done",GameStatus.Completed,1,0));screen(base.copy(upcoming=games));onNodeWithText("Rascunho").assertExists();onNodeWithText("Lista de espera: 3").assertExists();onNodeWithText("DRAFT").assertDoesNotExist();onNodeWithText("America/Sao_Paulo").assertDoesNotExist()}
    @Test fun `card click emits exact game intent`()=runComposeUiTest{val intents=mutableListOf<GamesScreenIntent>();screen(base,onIntent=intents::add);onNodeWithTag(GamesTags.card("future")).performClick();assertEquals(listOf<GamesScreenIntent>(GamesScreenIntent.OpenGame("future")),intents)}
    @Test fun `tab actions are semantically ordered and typed`()=runComposeUiTest{val intents=mutableListOf<GamesScreenIntent>();screen(base,onIntent=intents::add);onNodeWithTag(GamesTags.Upcoming).assertHasClickAction();onNodeWithTag(GamesTags.Past).performClick();assertEquals(GamesScreenIntent.SelectTab(GamesTab.PAST),intents.single())}
    @Test fun `compact viewport scrolls to every game card`()=runComposeUiTest{val many=(1..12).map{item("game-$it")};setContent{Box(Modifier.size(320.dp,420.dp)){SaqzTheme{GamesScreen(GamesScreenState(base.copy(upcoming=many))) {}}}};waitForIdle();many.forEachIndexed{index,item->onNodeWithTag(GamesTags.List).performScrollToIndex(index);onNodeWithTag(GamesTags.card(item.id)).assertIsDisplayed().assertHasClickAction()}}
    @Test fun `max text retains reachable 48 dp actions`()=runComposeUiTest{setContent{CompositionLocalProvider(LocalDensity provides Density(1f,2f)){SaqzTheme{GamesScreen(GamesScreenState(base)) {}}}};onNodeWithTag(GamesTags.Create).assertIsDisplayed().assertHeightIsAtLeast(48.dp);onNodeWithTag(GamesTags.card("future")).performScrollTo().assertHasClickAction()}

    private fun androidx.compose.ui.test.ComposeUiTest.screen(state:GamesState,tab:GamesTab=GamesTab.UPCOMING,onIntent:(GamesScreenIntent)->Unit={})=setContent{SaqzTheme{GamesScreen(GamesScreenState(state,tab),onIntent)}}
    private fun item(id:String="future",status:GameStatus=GameStatus.Published,spots:Int=21,waitlist:Int=2)=GameListItem(id,"Treino semanal","12/08/2026","19:30","Arena Central",status,spots,waitlist,"2026-08-12T22:30:00Z")
    private val base get()=GamesState("group",GroupRole.OWNER,listOf(item()),listOf(item("past")))
}
