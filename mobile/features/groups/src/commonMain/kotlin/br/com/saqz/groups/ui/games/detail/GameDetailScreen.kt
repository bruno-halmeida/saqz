package br.com.saqz.groups.ui.games.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.component.SaqzBadge
import br.com.saqz.designsystem.component.SaqzBadgeVariant
import br.com.saqz.designsystem.component.SaqzButton
import br.com.saqz.designsystem.component.SaqzButtonVariant
import br.com.saqz.designsystem.component.SaqzCard
import br.com.saqz.designsystem.component.SaqzDialog
import br.com.saqz.designsystem.component.SaqzInput
import br.com.saqz.designsystem.component.SaqzLoadingState
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.data.attendance.AttendanceIntentDto
import br.com.saqz.groups.data.attendance.AttendanceStatusDto
import br.com.saqz.groups.data.game.GameStatusDto
import br.com.saqz.groups.presentation.games.detail.AttendanceAction
import br.com.saqz.groups.presentation.games.detail.GameDetailError
import br.com.saqz.groups.presentation.games.detail.GameDetailIntent
import br.com.saqz.groups.presentation.games.detail.GameDetailState
import br.com.saqz.groups.presentation.games.detail.GameLifecycleAction
import br.com.saqz.groups.resources.*
import org.jetbrains.compose.resources.stringResource

object GameDetailTags {
    const val Edit="game-detail-edit";const val Publish="game-detail-publish";const val Cancel="game-detail-cancel";const val Complete="game-detail-complete";const val Confirm="game-detail-confirm";const val Reload="game-detail-reload"
    const val Attendance="game-detail-attendance";const val AttendConfirm="attendance-confirm";const val AttendDecline="attendance-decline";const val AttendWithdraw="attendance-withdraw";const val AttendDialogConfirm="attendance-dialog-confirm";const val AttendRetry="attendance-retry"
    const val OverrideMember="attendance-override-member";const val OverrideReason="attendance-override-reason";const val OverrideConfirm="attendance-override-confirm";const val OverrideDecline="attendance-override-decline"
    const val Capacity="attendance-capacity";const val SaveCapacity="attendance-capacity-save";const val CapacityWarning="attendance-capacity-warning"
}

@Composable fun GameDetailScreen(state:GameDetailState,onIntent:(GameDetailIntent)->Unit){
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(SaqzTheme.metrics.horizontalPadding),verticalArrangement=Arrangement.spacedBy(SaqzTheme.metrics.sectionVerticalPadding)){
        Text(stringResource(Res.string.game_detail_title),style=SaqzTheme.typography.lead,color=SaqzTheme.colors.textPrimary,modifier=Modifier.semantics{heading()})
        when{state.isLoading->SaqzLoadingState();state.game==null->ErrorPanel(state,onIntent);else->{val game=state.game
            SaqzCard(Modifier.fillMaxWidth()){Column(verticalArrangement=Arrangement.spacedBy(SaqzTheme.metrics.grid)){Text(game.title,style=SaqzTheme.typography.lead,color=SaqzTheme.colors.textPrimary);Text("${game.localDate.ptBr()} às ${game.localTime.take(5)}",color=SaqzTheme.colors.textSecondary);Text(game.venue.name,color=SaqzTheme.colors.textPrimary);Text(game.venue.address,color=SaqzTheme.colors.textSecondary);Row(horizontalArrangement=Arrangement.spacedBy(SaqzTheme.metrics.grid)){SaqzBadge(game.status.label(),SaqzBadgeVariant.Neutral);Text(game.availability(),color=SaqzTheme.colors.textPrimary)};Text("Duração: ${game.durationMinutes} min",color=SaqzTheme.colors.textSecondary);game.gameFeeCents?.let{Text("Valor: ${it.brl()}",color=SaqzTheme.colors.textSecondary)};game.notes?.let{Text(it,color=SaqzTheme.colors.textSecondary)}}}
            if(state.terminal)Text(stringResource(Res.string.game_detail_terminal),color=SaqzTheme.colors.textSecondary)
            if(game.status==GameStatusDto.CANCELLED||game.financeReviewRequired)Text(stringResource(Res.string.game_detail_cancel_finance),color=SaqzTheme.colors.textSecondary)
            AttendancePanel(state,onIntent)
            if(state.canEdit)SaqzButton(stringResource(Res.string.game_detail_edit),{onIntent(GameDetailIntent.OpenEdit)},Modifier.fillMaxWidth().testTag(GameDetailTags.Edit),SaqzButtonVariant.Secondary)
            state.actions.forEach{action->SaqzButton(action.label(),{onIntent(GameDetailIntent.RequestLifecycle(action))},Modifier.fillMaxWidth().testTag(action.tag()),if(action==GameLifecycleAction.CANCEL)SaqzButtonVariant.Destructive else SaqzButtonVariant.Primary,loading=state.isMutating)}
            if(state.error!=null)ErrorPanel(state,onIntent)
        }}
    }
    state.pendingAction?.let{action->SaqzDialog(action.confirmTitle(),{onIntent(GameDetailIntent.DismissConfirmation)},primaryAction={SaqzButton(stringResource(Res.string.game_detail_confirm),{onIntent(GameDetailIntent.ConfirmLifecycle)},Modifier.testTag(GameDetailTags.Confirm),loading=state.isMutating)}){Column(verticalArrangement=Arrangement.spacedBy(SaqzTheme.metrics.grid)){if(action==GameLifecycleAction.CANCEL)Text(stringResource(Res.string.game_detail_cancel_finance),color=SaqzTheme.colors.textSecondary);SaqzButton(stringResource(Res.string.game_detail_dismiss),{onIntent(GameDetailIntent.DismissConfirmation)},Modifier.fillMaxWidth(),SaqzButtonVariant.Secondary)}}}
    state.pendingAttendanceAction?.let{action->AttendanceConfirmation(state,action,onIntent)}
}

@Composable private fun AttendancePanel(state:GameDetailState,onIntent:(GameDetailIntent)->Unit){
    val game=state.game?:return
    SaqzCard(Modifier.fillMaxWidth().testTag(GameDetailTags.Attendance)){Column(verticalArrangement=Arrangement.spacedBy(SaqzTheme.metrics.grid)){
        Text(stringResource(Res.string.attendance_title),style=SaqzTheme.typography.lead,color=SaqzTheme.colors.textPrimary,modifier=Modifier.semantics{heading()})
        Text(stringResource(Res.string.attendance_counts,state.confirmedCount,state.availableSpots),color=SaqzTheme.colors.textPrimary)
        if(state.waitlistCount>0)Text(stringResource(Res.string.attendance_waitlist_count,state.waitlistCount),color=SaqzTheme.colors.textSecondary)
        when(state.ownAttendance?.status){
            AttendanceStatusDto.CONFIRMED->SaqzBadge(stringResource(Res.string.attendance_status_confirmed),SaqzBadgeVariant.Neutral)
            AttendanceStatusDto.DECLINED->SaqzBadge(stringResource(Res.string.attendance_status_declined),SaqzBadgeVariant.Neutral)
            AttendanceStatusDto.WAITLISTED->Text(stringResource(Res.string.attendance_status_waitlisted,state.waitlistPosition?:0),color=SaqzTheme.colors.textSecondary)
            null->Unit
        }
        when{state.terminal->Text(stringResource(Res.string.attendance_frozen),color=SaqzTheme.colors.textSecondary);game.status==GameStatusDto.PUBLISHED&&!state.attendanceOpen->Text(stringResource(Res.string.attendance_deadline_closed),color=SaqzTheme.colors.textSecondary);else->Text(stringResource(Res.string.attendance_deadline,game.confirmationDeadline),color=SaqzTheme.colors.textSecondary)}
        state.attendanceActions.sortedBy{it.ordinal}.forEach{action->SaqzButton(action.attendanceLabel(),{onIntent(GameDetailIntent.RequestAttendance(action))},Modifier.fillMaxWidth().heightIn(min=48.dp).testTag(action.attendanceTag()),if(action==AttendanceAction.WITHDRAW)SaqzButtonVariant.Destructive else if(action==AttendanceAction.DECLINE)SaqzButtonVariant.Secondary else SaqzButtonVariant.Primary,loading=state.isAttendanceMutating)}
        state.attendanceError?.let{Text(it.attendanceErrorLabel(),color=SaqzTheme.colors.errorForeground)}
        if(state.retryAttendanceAvailable)SaqzButton(stringResource(Res.string.attendance_retry),{onIntent(GameDetailIntent.RetryAttendance)},Modifier.fillMaxWidth().heightIn(min=48.dp).testTag(GameDetailTags.AttendRetry),SaqzButtonVariant.Secondary)
        if(state.organizer)OrganizerAttendance(state,onIntent)
    }}
}

@Composable private fun OrganizerAttendance(state:GameDetailState,onIntent:(GameDetailIntent)->Unit){
    var member by remember(state.gameId){mutableStateOf(TextFieldValue())}
    var reason by remember(state.gameId){mutableStateOf(TextFieldValue())}
    var capacity by remember(state.game?.capacity){mutableStateOf(TextFieldValue(state.game?.capacity?.toString().orEmpty()))}
    val validOverride=member.text.isNotBlank()&&reason.text.trim().length in 2..500&&!reason.text.any(Char::isISOControl)&&!state.isAttendanceMutating
    val capacityValue=capacity.text.toIntOrNull()
    val belowConfirmed=capacityValue!=null&&capacityValue<state.confirmedCount
    val validCapacity=capacityValue!=null&&capacityValue in 2..100&&!belowConfirmed&&!state.isAttendanceMutating
    Text(stringResource(Res.string.attendance_organizer_title),style=SaqzTheme.typography.body,color=SaqzTheme.colors.textPrimary,modifier=Modifier.semantics{heading()})
    SaqzInput(member,{member=it.copy(text=it.text.take(64))},stringResource(Res.string.attendance_member_id),Modifier.testTag(GameDetailTags.OverrideMember))
    SaqzInput(reason,{reason=it.copy(text=it.text.take(500))},stringResource(Res.string.attendance_override_reason),Modifier.testTag(GameDetailTags.OverrideReason),helperText=stringResource(Res.string.attendance_reason_helper))
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(SaqzTheme.metrics.grid)){
        SaqzButton(stringResource(Res.string.attendance_override_confirm),{onIntent(GameDetailIntent.OverrideAttendance(member.text.trim(),AttendanceIntentDto.CONFIRM,reason.text))},Modifier.weight(1f).heightIn(min=48.dp).testTag(GameDetailTags.OverrideConfirm),enabled=validOverride)
        SaqzButton(stringResource(Res.string.attendance_override_decline),{onIntent(GameDetailIntent.OverrideAttendance(member.text.trim(),AttendanceIntentDto.DECLINE,reason.text))},Modifier.weight(1f).heightIn(min=48.dp).testTag(GameDetailTags.OverrideDecline),SaqzButtonVariant.Secondary,enabled=validOverride)
    }
    SaqzInput(capacity,{capacity=it.copy(text=it.text.filter(Char::isDigit).take(3))},stringResource(Res.string.attendance_capacity),Modifier.testTag(GameDetailTags.Capacity))
    if(belowConfirmed)Text(stringResource(Res.string.attendance_capacity_warning,state.confirmedCount),color=SaqzTheme.colors.errorForeground,modifier=Modifier.testTag(GameDetailTags.CapacityWarning))
    SaqzButton(stringResource(Res.string.attendance_capacity_save),{capacityValue?.let{onIntent(GameDetailIntent.ChangeCapacity(it))}},Modifier.fillMaxWidth().heightIn(min=48.dp).testTag(GameDetailTags.SaveCapacity),SaqzButtonVariant.Secondary,enabled=validCapacity)
}

@Composable private fun AttendanceConfirmation(state:GameDetailState,action:AttendanceAction,onIntent:(GameDetailIntent)->Unit){
    SaqzDialog(action.attendanceConfirmTitle(),{onIntent(GameDetailIntent.DismissAttendance)},primaryAction={SaqzButton(stringResource(Res.string.game_detail_confirm),{onIntent(GameDetailIntent.ConfirmAttendance)},Modifier.heightIn(min=48.dp).testTag(GameDetailTags.AttendDialogConfirm),loading=state.isAttendanceMutating)}){Column(verticalArrangement=Arrangement.spacedBy(SaqzTheme.metrics.grid)){
        if(action==AttendanceAction.WITHDRAW&&state.withdrawalKeepsCharge)Text(stringResource(Res.string.attendance_withdraw_charge_warning),color=SaqzTheme.colors.textSecondary)
        SaqzButton(stringResource(Res.string.game_detail_dismiss),{onIntent(GameDetailIntent.DismissAttendance)},Modifier.fillMaxWidth().heightIn(min=48.dp),SaqzButtonVariant.Secondary)
    }}
}

@Composable private fun ErrorPanel(state:GameDetailState,onIntent:(GameDetailIntent)->Unit){Text(stringResource(if(state.error==GameDetailError.CONFLICT||state.error==GameDetailError.INVALID_LIFECYCLE)Res.string.game_detail_conflict else Res.string.game_detail_error),color=SaqzTheme.colors.errorForeground);if(state.reloadAvailable||state.game==null)SaqzButton(stringResource(Res.string.game_detail_reload),{onIntent(GameDetailIntent.Reload)},Modifier.fillMaxWidth().testTag(GameDetailTags.Reload))}
@Composable private fun GameLifecycleAction.label()=stringResource(when(this){GameLifecycleAction.PUBLISH->Res.string.game_detail_publish;GameLifecycleAction.CANCEL->Res.string.game_detail_cancel;GameLifecycleAction.COMPLETE->Res.string.game_detail_complete})
@Composable private fun GameLifecycleAction.confirmTitle()=stringResource(when(this){GameLifecycleAction.PUBLISH->Res.string.game_detail_confirm_publish;GameLifecycleAction.CANCEL->Res.string.game_detail_confirm_cancel;GameLifecycleAction.COMPLETE->Res.string.game_detail_confirm_complete})
private fun GameLifecycleAction.tag()=when(this){GameLifecycleAction.PUBLISH->GameDetailTags.Publish;GameLifecycleAction.CANCEL->GameDetailTags.Cancel;GameLifecycleAction.COMPLETE->GameDetailTags.Complete}
@Composable private fun AttendanceAction.attendanceLabel()=stringResource(when(this){AttendanceAction.CONFIRM->Res.string.attendance_confirm;AttendanceAction.DECLINE->Res.string.attendance_decline;AttendanceAction.WITHDRAW->Res.string.attendance_withdraw})
@Composable private fun AttendanceAction.attendanceConfirmTitle()=stringResource(when(this){AttendanceAction.CONFIRM->Res.string.attendance_confirm_title;AttendanceAction.DECLINE->Res.string.attendance_decline_title;AttendanceAction.WITHDRAW->Res.string.attendance_withdraw_title})
private fun AttendanceAction.attendanceTag()=when(this){AttendanceAction.CONFIRM->GameDetailTags.AttendConfirm;AttendanceAction.DECLINE->GameDetailTags.AttendDecline;AttendanceAction.WITHDRAW->GameDetailTags.AttendWithdraw}
@Composable private fun GameDetailError.attendanceErrorLabel()=stringResource(when(this){GameDetailError.DEADLINE->Res.string.attendance_deadline_closed;GameDetailError.FROZEN->Res.string.attendance_frozen;GameDetailError.CONFLICT->Res.string.game_detail_conflict;GameDetailError.VALIDATION->Res.string.attendance_validation;else->Res.string.game_detail_error})
private fun GameStatusDto.label()=when(this){GameStatusDto.DRAFT->"Rascunho";GameStatusDto.PUBLISHED->"Publicado";GameStatusDto.CANCELLED->"Cancelado";GameStatusDto.COMPLETED->"Concluído"}
private fun br.com.saqz.groups.data.game.GameDto.availability()=when{availableSpots>0->"$availableSpots vagas";waitlistCount>0->"Lista de espera: $waitlistCount";else->"Sem vagas"}
private fun String.ptBr()=split('-').let{"${it[2]}/${it[1]}/${it[0]}"}
private fun Long.brl()="R$ ${this/100},${(this%100).toString().padStart(2,'0')}"
