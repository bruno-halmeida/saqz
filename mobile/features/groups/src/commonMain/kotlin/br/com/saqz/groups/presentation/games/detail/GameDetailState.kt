package br.com.saqz.groups.presentation.games.detail

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.data.GroupRoleDto
import br.com.saqz.groups.data.attendance.AttendanceDetailDto
import br.com.saqz.groups.data.attendance.AttendanceStatusDto
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.GameVersionToken
import br.com.saqz.groups.presentation.attendance.share.AttendanceShareImageModel

@Immutable
data class GameDetailState(
    val groupId:String,
    val gameId:String,
    val role:GroupRoleDto,
    val game:Game?=null,
    val version:GameVersionToken?=null,
    val attendance:AttendanceDetailDto?=null,
    val attendanceEtag:String?=null,
    val attendanceOpen:Boolean=false,
    val isLoading:Boolean=true,
    val isMutating:Boolean=false,
    val isAttendanceMutating:Boolean=false,
    val pendingAction:GameLifecycleAction?=null,
    val pendingAttendanceAction:AttendanceAction?=null,
    val attendanceCommandKey:String?=null,
    val error:GameDetailError?=null,
    val attendanceError:GameDetailError?=null,
    val reloadAvailable:Boolean=false,
    val retryAttendanceAvailable:Boolean=false,
    val isAttendanceLinkLoading:Boolean=false,
    val attendanceLinkUrl:String?=null,
    val attendanceLinkError:GameDetailError?=null,
    val isAttendanceShareSnapshotLoading:Boolean=false,
    val attendanceShareSnapshot:AttendanceShareImageModel?=null,
    val attendanceShareError:GameDetailError?=null,
    val showAttendanceSharePrivacy:Boolean=false,
) {
    val organizer get()=role==GroupRoleDto.OWNER||role==GroupRoleDto.ADMIN
    val canEdit get()=organizer&&(game?.status==GameStatus.Draft||game?.status==GameStatus.Published)
    val actions get()=when{!organizer->emptyList();game?.status==GameStatus.Draft->listOf(GameLifecycleAction.PUBLISH);game?.status==GameStatus.Published->listOf(GameLifecycleAction.CANCEL,GameLifecycleAction.COMPLETE);else->emptyList()}
    val terminal get()=game?.status==GameStatus.Cancelled||game?.status==GameStatus.Completed
    val ownAttendance get()=attendance?.ownAttendance
    val confirmedCount get()=attendance?.confirmedCount?:game?.confirmedCount?:0
    val availableSpots get()=attendance?.availableSpots?:game?.availableSpots?:0
    val waitlistCount get()=attendance?.waitlistCount?:game?.waitlistCount?:0
    val waitlistPosition get()=ownAttendance?.waitlistPosition
    val withdrawalKeepsCharge get()=pendingAttendanceAction==AttendanceAction.WITHDRAW&&game?.gameFeeCents!=null
    val attendanceActions get()=if(!attendanceOpen||game?.status!=GameStatus.Published)emptySet() else when(ownAttendance?.status){null->setOf(AttendanceAction.CONFIRM,AttendanceAction.DECLINE);AttendanceStatusDto.CONFIRMED->setOf(AttendanceAction.WITHDRAW);AttendanceStatusDto.DECLINED->setOf(AttendanceAction.CONFIRM);AttendanceStatusDto.WAITLISTED->setOf(AttendanceAction.WITHDRAW)}
}
