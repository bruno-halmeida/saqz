package br.com.saqz.groups.presentation.games.detail

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.saqz.groups.data.GroupRoleDto
import br.com.saqz.groups.data.attendance.*
import br.com.saqz.groups.data.game.*
import br.com.saqz.network.NetworkResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlin.time.Clock
import kotlin.random.Random

enum class GameLifecycleAction(val mutation:String) { PUBLISH("publish"), CANCEL("cancel"), COMPLETE("complete") }
enum class AttendanceAction { CONFIRM, DECLINE, WITHDRAW }
enum class GameDetailError { UNAVAILABLE, HIDDEN, CONFLICT, INVALID_LIFECYCLE, DEADLINE, FROZEN, VALIDATION }
fun interface AttendanceCommandKeyFactory { fun create():String }

@Immutable
data class GameDetailState(
    val groupId:String,
    val gameId:String,
    val role:GroupRoleDto,
    val game:GameDto?=null,
    val etag:String?=null,
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
) {
    val organizer get()=role==GroupRoleDto.OWNER||role==GroupRoleDto.ADMIN
    val canEdit get()=organizer&&(game?.status==GameStatusDto.DRAFT||game?.status==GameStatusDto.PUBLISHED)
    val actions get()=when{!organizer->emptyList();game?.status==GameStatusDto.DRAFT->listOf(GameLifecycleAction.PUBLISH);game?.status==GameStatusDto.PUBLISHED->listOf(GameLifecycleAction.CANCEL,GameLifecycleAction.COMPLETE);else->emptyList()}
    val terminal get()=game?.status==GameStatusDto.CANCELLED||game?.status==GameStatusDto.COMPLETED
    val ownAttendance get()=attendance?.ownAttendance
    val confirmedCount get()=attendance?.confirmedCount?:game?.confirmedCount?:0
    val availableSpots get()=attendance?.availableSpots?:game?.availableSpots?:0
    val waitlistCount get()=attendance?.waitlistCount?:game?.waitlistCount?:0
    val waitlistPosition get()=ownAttendance?.waitlistPosition
    val withdrawalKeepsCharge get()=pendingAttendanceAction==AttendanceAction.WITHDRAW&&game?.gameFeeCents!=null
    val attendanceActions get()=if(!attendanceOpen||game?.status!=GameStatusDto.PUBLISHED)emptySet() else when(ownAttendance?.status){null->setOf(AttendanceAction.CONFIRM,AttendanceAction.DECLINE);AttendanceStatusDto.CONFIRMED->setOf(AttendanceAction.WITHDRAW);AttendanceStatusDto.DECLINED->setOf(AttendanceAction.CONFIRM);AttendanceStatusDto.WAITLISTED->setOf(AttendanceAction.WITHDRAW)}
}

sealed interface GameDetailIntent {
    data object Refresh:GameDetailIntent
    data object RefreshAttendance:GameDetailIntent
    data object OpenEdit:GameDetailIntent
    data class RequestLifecycle(val action:GameLifecycleAction):GameDetailIntent
    data object DismissConfirmation:GameDetailIntent
    data object ConfirmLifecycle:GameDetailIntent
    data object Reload:GameDetailIntent
    data class RequestAttendance(val action:AttendanceAction):GameDetailIntent
    data object DismissAttendance:GameDetailIntent
    data object ConfirmAttendance:GameDetailIntent
    data class OverrideAttendance(val memberId:String,val intent:AttendanceIntentDto,val reason:String):GameDetailIntent
    data class ChangeCapacity(val capacity:Int):GameDetailIntent
    data object RetryAttendance:GameDetailIntent
}

sealed interface GameDetailEffect {
    data class OpenEdit(val groupId:String,val gameId:String):GameDetailEffect
    data class LifecycleApplied(val action:GameLifecycleAction):GameDetailEffect
    data class AttendanceApplied(val status:AttendanceStatusDto,val promotedCount:Int,val refreshCharges:Boolean):GameDetailEffect
    data class CapacityApplied(val capacity:Int,val promotedCount:Int):GameDetailEffect
}

private sealed interface AttendanceOperation {
    val key:String
    data class Self(val action:AttendanceAction,override val key:String):AttendanceOperation
    data class Override(val memberId:String,val intent:AttendanceIntentDto,val reason:String,override val key:String):AttendanceOperation
    data class Capacity(val capacity:Int,val etag:String,override val key:String):AttendanceOperation
}

class GameDetailViewModel(
    private val gateway:GameGateway,
    groupId:String,
    gameId:String,
    role:GroupRoleDto,
    testScope:CoroutineScope?=null,
    private val attendanceGateway:AttendanceGateway?=null,
    private val keys:AttendanceCommandKeyFactory=AttendanceCommandKeyFactory{"attendance-${Random.nextLong()}"},
    private val now:()->Instant={Clock.System.now()},
):ViewModel(){
    private val scope=testScope?:viewModelScope
    private val mutable=MutableStateFlow(GameDetailState(groupId,gameId,role))
    val state:StateFlow<GameDetailState> = mutable.asStateFlow()
    private val channel=Channel<GameDetailEffect>(Channel.BUFFERED)
    val effects:Flow<GameDetailEffect> = channel.receiveAsFlow()
    private var retryOperation:AttendanceOperation?=null
    init{load()}

    fun onIntent(intent:GameDetailIntent){when(intent){
        GameDetailIntent.Refresh,GameDetailIntent.Reload->load()
        GameDetailIntent.RefreshAttendance->refreshAttendance()
        GameDetailIntent.OpenEdit->openEdit()
        is GameDetailIntent.RequestLifecycle->request(intent.action)
        GameDetailIntent.DismissConfirmation->if(!mutable.value.isMutating)mutable.value=mutable.value.copy(pendingAction=null)
        GameDetailIntent.ConfirmLifecycle->confirm()
        is GameDetailIntent.RequestAttendance->requestAttendance(intent.action)
        GameDetailIntent.DismissAttendance->if(!mutable.value.isAttendanceMutating)mutable.value=mutable.value.copy(pendingAttendanceAction=null,attendanceCommandKey=null)
        GameDetailIntent.ConfirmAttendance->confirmAttendance()
        is GameDetailIntent.OverrideAttendance->override(intent)
        is GameDetailIntent.ChangeCapacity->capacity(intent.capacity)
        GameDetailIntent.RetryAttendance->retryOperation?.let(::execute)
    }}

    private fun load(){
        if(mutable.value.isMutating||mutable.value.isAttendanceMutating)return
        val current=mutable.value
        mutable.value=current.copy(isLoading=true,error=null,attendanceError=null,reloadAvailable=false,pendingAction=null)
        scope.launch{when(val result=gateway.read(current.groupId,current.gameId)){
            is NetworkResult.Success->{mutable.value=mutable.value.copy(game=result.value.game,etag=result.value.etag,attendanceOpen=result.value.game.attendanceOpen(),error=null);loadAttendance(current.groupId,current.gameId)}
            is NetworkResult.Failure->mutable.value=mutable.value.copy(game=null,etag=null,isLoading=false,error=if(result.error.toGameGatewayFailure()==GameGatewayFailure.HiddenResource)GameDetailError.HIDDEN else GameDetailError.UNAVAILABLE)
        }}
    }

    private suspend fun loadAttendance(groupId:String,gameId:String){
        val attendance=attendanceGateway
        if(attendance==null){mutable.value=mutable.value.copy(isLoading=false);return}
        when(val result=attendance.read(groupId,gameId)){
            is NetworkResult.Success->mutable.value=mutable.value.copy(attendance=result.value,isLoading=false,attendanceError=null)
            is NetworkResult.Failure->mutable.value=mutable.value.copy(isLoading=false,attendanceError=result.error.toAttendanceGatewayFailure().error())
        }
    }

    private fun refreshAttendance(){val current=mutable.value;if(current.isAttendanceMutating||attendanceGateway==null)return;scope.launch{loadAttendance(current.groupId,current.gameId)}}
    private fun openEdit(){val current=mutable.value;if(current.canEdit&&!current.isMutating)channel.trySend(GameDetailEffect.OpenEdit(current.groupId,current.gameId))}
    private fun request(action:GameLifecycleAction){val current=mutable.value;if(!current.isMutating&&action in current.actions)mutable.value=current.copy(pendingAction=action,error=null)}
    private fun confirm(){val current=mutable.value;val action=current.pendingAction?:return;val etag=current.etag?:return;if(current.isMutating||action !in current.actions)return;mutable.value=current.copy(isMutating=true,error=null);scope.launch{when(val result=gateway.lifecycle(current.groupId,current.gameId,etag,action.mutation)){is NetworkResult.Success->{mutable.value=mutable.value.copy(game=result.value.game,etag=result.value.etag,isMutating=false,pendingAction=null,reloadAvailable=false);channel.trySend(GameDetailEffect.LifecycleApplied(action))};is NetworkResult.Failure->fail(result.error.toGameGatewayFailure())}}}

    private fun requestAttendance(action:AttendanceAction){
        val current=mutable.value
        if(current.isAttendanceMutating||action !in current.attendanceActions)return
        val key=if(current.pendingAttendanceAction==action)current.attendanceCommandKey?:keys.create() else keys.create()
        mutable.value=current.copy(pendingAttendanceAction=action,attendanceCommandKey=key,attendanceError=null)
    }
    private fun confirmAttendance(){val current=mutable.value;val action=current.pendingAttendanceAction?:return;val key=current.attendanceCommandKey?:return;execute(AttendanceOperation.Self(action,key))}
    private fun override(intent:GameDetailIntent.OverrideAttendance){val current=mutable.value;if(!current.organizer||current.isAttendanceMutating)return;if(intent.memberId.isBlank()||intent.reason.trim().length<2){mutable.value=current.copy(attendanceError=GameDetailError.VALIDATION);return};execute(AttendanceOperation.Override(intent.memberId,intent.intent,intent.reason.trim(),keys.create()))}
    private fun capacity(value:Int){val current=mutable.value;val etag=current.etag?:return;if(!current.organizer||current.isAttendanceMutating)return;if(value !in 2..100){mutable.value=current.copy(attendanceError=GameDetailError.VALIDATION);return};execute(AttendanceOperation.Capacity(value,etag,keys.create()))}

    private fun execute(operation:AttendanceOperation){
        val attendance=attendanceGateway?:return
        val current=mutable.value
        if(current.isAttendanceMutating)return
        retryOperation=operation
        mutable.value=current.copy(isAttendanceMutating=true,attendanceError=null,retryAttendanceAvailable=false)
        scope.launch{when(operation){
            is AttendanceOperation.Self->when(val result=attendance.respond(current.groupId,current.gameId,SelfAttendanceCommand(operation.key,operation.action.intent()))){is NetworkResult.Success->applied(result.value.value,result.value.etag,true);is NetworkResult.Failure->attendanceFailed(result.error.toAttendanceGatewayFailure())}
            is AttendanceOperation.Override->when(val result=attendance.override(current.groupId,current.gameId,OverrideAttendanceCommand(operation.key,operation.memberId,operation.intent,operation.reason))){is NetworkResult.Success->applied(result.value.value,result.value.etag,false);is NetworkResult.Failure->attendanceFailed(result.error.toAttendanceGatewayFailure())}
            is AttendanceOperation.Capacity->when(val result=attendance.capacity(current.groupId,current.gameId,operation.etag,CapacityCommand(operation.key,operation.capacity))){is NetworkResult.Success->capacityApplied(result.value);is NetworkResult.Failure->attendanceFailed(result.error.toAttendanceGatewayFailure())}
        }}
    }

    private fun applied(result:AttendanceMutationDto,etag:String,updateOwn:Boolean){
        retryOperation=null
        val detail=if(updateOwn)result.detail.copy(ownAttendance=result.attendance) else result.detail
        mutable.value=mutable.value.copy(attendance=detail,attendanceEtag=etag,isAttendanceMutating=false,pendingAttendanceAction=null,attendanceCommandKey=null,attendanceError=null,retryAttendanceAvailable=false).syncGame(detail)
        channel.trySend(GameDetailEffect.AttendanceApplied(result.attendance.status,result.promotedCount,true))
    }
    private fun capacityApplied(result:VersionedCapacityDto){
        retryOperation=null
        mutable.value=mutable.value.copy(attendance=result.value.detail,etag=result.etag,isAttendanceMutating=false,attendanceError=null,retryAttendanceAvailable=false).syncGame(result.value.detail)
        channel.trySend(GameDetailEffect.CapacityApplied(result.value.capacity,result.value.promotedCount))
    }
    private fun attendanceFailed(failure:AttendanceGatewayFailure){val error=failure.error();mutable.value=mutable.value.copy(isAttendanceMutating=false,attendanceError=error,retryAttendanceAvailable=error in setOf(GameDetailError.CONFLICT,GameDetailError.UNAVAILABLE),reloadAvailable=error==GameDetailError.CONFLICT)}
    private fun fail(failure:GameGatewayFailure){val error=when(failure){GameGatewayFailure.Conflict->GameDetailError.CONFLICT;GameGatewayFailure.HiddenResource->GameDetailError.HIDDEN;GameGatewayFailure.InvalidLifecycle->GameDetailError.INVALID_LIFECYCLE;else->GameDetailError.UNAVAILABLE};mutable.value=mutable.value.copy(isMutating=false,pendingAction=null,error=error,reloadAvailable=error==GameDetailError.CONFLICT||error==GameDetailError.INVALID_LIFECYCLE)}

    private fun GameDto.attendanceOpen()=status==GameStatusDto.PUBLISHED&&runCatching{now()<=Instant.parse(confirmationDeadline)}.getOrDefault(false)
    private fun AttendanceAction.intent()=if(this==AttendanceAction.CONFIRM)AttendanceIntentDto.CONFIRM else AttendanceIntentDto.DECLINE
    private fun AttendanceGatewayFailure.error()=when(this){AttendanceGatewayFailure.HiddenResource->GameDetailError.HIDDEN;AttendanceGatewayFailure.DeadlinePassed->GameDetailError.DEADLINE;AttendanceGatewayFailure.Frozen->GameDetailError.FROZEN;AttendanceGatewayFailure.Conflict->GameDetailError.CONFLICT;is AttendanceGatewayFailure.Validation->GameDetailError.VALIDATION;else->GameDetailError.UNAVAILABLE}
    private fun GameDetailState.syncGame(detail:AttendanceDetailDto)=copy(game=game?.copy(capacity=detail.capacity,confirmedCount=detail.confirmedCount,availableSpots=detail.availableSpots,waitlistCount=detail.waitlistCount))
}
