package br.com.saqz.groups.ui.games.list

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.component.*
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.data.game.GameStatusDto
import br.com.saqz.groups.presentation.games.list.*

enum class GamesTab { UPCOMING, PAST }
data class GamesScreenState(val games:GamesState,val tab:GamesTab=GamesTab.UPCOMING)
sealed interface GamesScreenIntent{data class SelectTab(val tab:GamesTab):GamesScreenIntent;data object Retry:GamesScreenIntent;data object Refresh:GamesScreenIntent;data object OpenCreate:GamesScreenIntent;data class OpenGame(val id:String):GamesScreenIntent}
object GamesTags{const val Upcoming="games-upcoming";const val Past="games-past";const val Create="games-create";const val Retry="games-retry";const val List="games-list";fun card(id:String)="game-card-$id"}

@Composable fun GamesScreen(state:GamesScreenState,onIntent:(GamesScreenIntent)->Unit){
    val games=state.games;val visible=if(state.tab==GamesTab.UPCOMING)games.upcoming else games.past
    Column(Modifier.fillMaxSize().padding(SaqzTheme.metrics.horizontalPadding),verticalArrangement=Arrangement.spacedBy(SaqzTheme.metrics.grid)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(SaqzTheme.metrics.grid)){
            SaqzButton("Próximos",{onIntent(GamesScreenIntent.SelectTab(GamesTab.UPCOMING))},Modifier.weight(1f).testTag(GamesTags.Upcoming),if(state.tab==GamesTab.UPCOMING)SaqzButtonVariant.Primary else SaqzButtonVariant.Secondary)
            SaqzButton("Histórico",{onIntent(GamesScreenIntent.SelectTab(GamesTab.PAST))},Modifier.weight(1f).testTag(GamesTags.Past),if(state.tab==GamesTab.PAST)SaqzButtonVariant.Primary else SaqzButtonVariant.Secondary)
        }
        if(games.canCreate)SaqzButton("Novo jogo",{onIntent(GamesScreenIntent.OpenCreate)},Modifier.fillMaxWidth().testTag(GamesTags.Create))
        when{
            games.isLoading->SaqzLoadingState()
            games.error!=null->Column(verticalArrangement=Arrangement.spacedBy(SaqzTheme.metrics.grid)){Text("Não foi possível carregar os jogos",color=SaqzTheme.colors.errorForeground);SaqzButton("Tentar novamente",{onIntent(GamesScreenIntent.Retry)},Modifier.fillMaxWidth().testTag(GamesTags.Retry))}
            visible.isEmpty()->Text(if(state.tab==GamesTab.UPCOMING)"Nenhum jogo próximo" else "Nenhum jogo no histórico",style=SaqzTheme.typography.body,color=SaqzTheme.colors.textSecondary)
            else->LazyColumn(Modifier.fillMaxSize().testTag(GamesTags.List),verticalArrangement=Arrangement.spacedBy(SaqzTheme.metrics.grid)){items(visible,key={it.id}){item->GameCard(item){onIntent(GamesScreenIntent.OpenGame(item.id))}}}
        }
    }
}
@Composable private fun GameCard(item:GameListItem,onClick:()->Unit)=SaqzCard(Modifier.fillMaxWidth().testTag(GamesTags.card(item.id)),onClick){Column(verticalArrangement=Arrangement.spacedBy(SaqzTheme.metrics.subGrid)){Text(item.title,style=SaqzTheme.typography.lead,color=SaqzTheme.colors.textPrimary);Text("${item.dateText} às ${item.timeText}",color=SaqzTheme.colors.textSecondary);Text(item.venueText,color=SaqzTheme.colors.textSecondary);Row(horizontalArrangement=Arrangement.spacedBy(SaqzTheme.metrics.grid)){SaqzBadge(item.status.label(),SaqzBadgeVariant.Neutral);Text(item.availability(),color=SaqzTheme.colors.textPrimary)}}}
private fun GameStatusDto.label()=when(this){GameStatusDto.DRAFT->"Rascunho";GameStatusDto.PUBLISHED->"Publicado";GameStatusDto.CANCELLED->"Cancelado";GameStatusDto.COMPLETED->"Concluído"}
private fun GameListItem.availability()=when{availableSpots>0->if(availableSpots==1)"1 vaga" else "$availableSpots vagas";waitlistCount>0->"Lista de espera: $waitlistCount";else->"Sem vagas"}
