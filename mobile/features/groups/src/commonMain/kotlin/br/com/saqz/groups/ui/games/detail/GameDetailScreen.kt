package br.com.saqz.groups.ui.games.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import br.com.saqz.designsystem.component.SaqzBadge
import br.com.saqz.designsystem.component.SaqzBadgeVariant
import br.com.saqz.designsystem.component.SaqzButton
import br.com.saqz.designsystem.component.SaqzButtonVariant
import br.com.saqz.designsystem.component.SaqzCard
import br.com.saqz.designsystem.component.SaqzDialog
import br.com.saqz.designsystem.component.SaqzLoadingState
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.data.game.GameStatusDto
import br.com.saqz.groups.presentation.games.detail.GameDetailError
import br.com.saqz.groups.presentation.games.detail.GameDetailIntent
import br.com.saqz.groups.presentation.games.detail.GameDetailState
import br.com.saqz.groups.presentation.games.detail.GameLifecycleAction
import br.com.saqz.groups.resources.*
import org.jetbrains.compose.resources.stringResource

object GameDetailTags { const val Edit="game-detail-edit";const val Publish="game-detail-publish";const val Cancel="game-detail-cancel";const val Complete="game-detail-complete";const val Confirm="game-detail-confirm";const val Reload="game-detail-reload" }

@Composable fun GameDetailScreen(state:GameDetailState,onIntent:(GameDetailIntent)->Unit){
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(SaqzTheme.metrics.horizontalPadding),verticalArrangement=Arrangement.spacedBy(SaqzTheme.metrics.sectionVerticalPadding)){
        Text(stringResource(Res.string.game_detail_title),style=SaqzTheme.typography.lead,color=SaqzTheme.colors.textPrimary,modifier=Modifier.semantics{heading()})
        when{state.isLoading->SaqzLoadingState();state.game==null->ErrorPanel(state,onIntent);else->{val game=state.game
            SaqzCard(Modifier.fillMaxWidth()){Column(verticalArrangement=Arrangement.spacedBy(SaqzTheme.metrics.grid)){Text(game.title,style=SaqzTheme.typography.lead,color=SaqzTheme.colors.textPrimary);Text("${game.localDate.ptBr()} às ${game.localTime.take(5)}",color=SaqzTheme.colors.textSecondary);Text(game.venue.name,color=SaqzTheme.colors.textPrimary);Text(game.venue.address,color=SaqzTheme.colors.textSecondary);Row(horizontalArrangement=Arrangement.spacedBy(SaqzTheme.metrics.grid)){SaqzBadge(game.status.label(),SaqzBadgeVariant.Neutral);Text(game.availability(),color=SaqzTheme.colors.textPrimary)};Text("Duração: ${game.durationMinutes} min",color=SaqzTheme.colors.textSecondary);game.gameFeeCents?.let{Text("Valor: ${it.brl()}",color=SaqzTheme.colors.textSecondary)};game.notes?.let{Text(it,color=SaqzTheme.colors.textSecondary)}}}
            if(state.terminal)Text(stringResource(Res.string.game_detail_terminal),color=SaqzTheme.colors.textSecondary)
            if(game.status==GameStatusDto.CANCELLED||game.financeReviewRequired)Text(stringResource(Res.string.game_detail_cancel_finance),color=SaqzTheme.colors.textSecondary)
            if(state.canEdit)SaqzButton(stringResource(Res.string.game_detail_edit),{onIntent(GameDetailIntent.OpenEdit)},Modifier.fillMaxWidth().testTag(GameDetailTags.Edit),SaqzButtonVariant.Secondary)
            state.actions.forEach{action->SaqzButton(action.label(),{onIntent(GameDetailIntent.RequestLifecycle(action))},Modifier.fillMaxWidth().testTag(action.tag()),if(action==GameLifecycleAction.CANCEL)SaqzButtonVariant.Destructive else SaqzButtonVariant.Primary,loading=state.isMutating)}
            if(state.error!=null)ErrorPanel(state,onIntent)
        }}
    }
    state.pendingAction?.let{action->SaqzDialog(action.confirmTitle(),{onIntent(GameDetailIntent.DismissConfirmation)},primaryAction={SaqzButton(stringResource(Res.string.game_detail_confirm),{onIntent(GameDetailIntent.ConfirmLifecycle)},Modifier.testTag(GameDetailTags.Confirm),loading=state.isMutating)}){Column(verticalArrangement=Arrangement.spacedBy(SaqzTheme.metrics.grid)){if(action==GameLifecycleAction.CANCEL)Text(stringResource(Res.string.game_detail_cancel_finance),color=SaqzTheme.colors.textSecondary);SaqzButton(stringResource(Res.string.game_detail_dismiss),{onIntent(GameDetailIntent.DismissConfirmation)},Modifier.fillMaxWidth(),SaqzButtonVariant.Secondary)}}}
}

@Composable private fun ErrorPanel(state:GameDetailState,onIntent:(GameDetailIntent)->Unit){Text(stringResource(if(state.error==GameDetailError.CONFLICT||state.error==GameDetailError.INVALID_LIFECYCLE)Res.string.game_detail_conflict else Res.string.game_detail_error),color=SaqzTheme.colors.errorForeground);if(state.reloadAvailable||state.game==null)SaqzButton(stringResource(Res.string.game_detail_reload),{onIntent(GameDetailIntent.Reload)},Modifier.fillMaxWidth().testTag(GameDetailTags.Reload))}
@Composable private fun GameLifecycleAction.label()=stringResource(when(this){GameLifecycleAction.PUBLISH->Res.string.game_detail_publish;GameLifecycleAction.CANCEL->Res.string.game_detail_cancel;GameLifecycleAction.COMPLETE->Res.string.game_detail_complete})
@Composable private fun GameLifecycleAction.confirmTitle()=stringResource(when(this){GameLifecycleAction.PUBLISH->Res.string.game_detail_confirm_publish;GameLifecycleAction.CANCEL->Res.string.game_detail_confirm_cancel;GameLifecycleAction.COMPLETE->Res.string.game_detail_confirm_complete})
private fun GameLifecycleAction.tag()=when(this){GameLifecycleAction.PUBLISH->GameDetailTags.Publish;GameLifecycleAction.CANCEL->GameDetailTags.Cancel;GameLifecycleAction.COMPLETE->GameDetailTags.Complete}
private fun GameStatusDto.label()=when(this){GameStatusDto.DRAFT->"Rascunho";GameStatusDto.PUBLISHED->"Publicado";GameStatusDto.CANCELLED->"Cancelado";GameStatusDto.COMPLETED->"Concluído"}
private fun br.com.saqz.groups.data.game.GameDto.availability()=when{availableSpots>0->"$availableSpots vagas";waitlistCount>0->"Lista de espera: $waitlistCount";else->"Sem vagas"}
private fun String.ptBr()=split('-').let{"${it[2]}/${it[1]}/${it[0]}"}
private fun Long.brl()="R$ ${this/100},${(this%100).toString().padStart(2,'0')}"
