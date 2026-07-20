package br.com.saqz.groups.presentation.games.detail

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.saqz.groups.data.GroupRoleDto
import br.com.saqz.groups.data.game.GameDto
import br.com.saqz.groups.data.game.GameGateway
import br.com.saqz.groups.data.game.GameGatewayFailure
import br.com.saqz.groups.data.game.GameStatusDto
import br.com.saqz.groups.data.game.toGameGatewayFailure
import br.com.saqz.network.NetworkResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

enum class GameLifecycleAction(val mutation:String) { PUBLISH("publish"), CANCEL("cancel"), COMPLETE("complete") }
enum class GameDetailError { UNAVAILABLE, HIDDEN, CONFLICT, INVALID_LIFECYCLE }
@Immutable data class GameDetailState(val groupId:String,val gameId:String,val role:GroupRoleDto,val game:GameDto?=null,val etag:String?=null,val isLoading:Boolean=true,val isMutating:Boolean=false,val pendingAction:GameLifecycleAction?=null,val error:GameDetailError?=null,val reloadAvailable:Boolean=false){
    val organizer get()=role==GroupRoleDto.OWNER||role==GroupRoleDto.ADMIN
    val canEdit get()=organizer&&(game?.status==GameStatusDto.DRAFT||game?.status==GameStatusDto.PUBLISHED)
    val actions get()=when{!organizer->emptyList();game?.status==GameStatusDto.DRAFT->listOf(GameLifecycleAction.PUBLISH);game?.status==GameStatusDto.PUBLISHED->listOf(GameLifecycleAction.CANCEL,GameLifecycleAction.COMPLETE);else->emptyList()}
    val terminal get()=game?.status==GameStatusDto.CANCELLED||game?.status==GameStatusDto.COMPLETED
}
sealed interface GameDetailIntent { data object Refresh:GameDetailIntent;data object OpenEdit:GameDetailIntent;data class RequestLifecycle(val action:GameLifecycleAction):GameDetailIntent;data object DismissConfirmation:GameDetailIntent;data object ConfirmLifecycle:GameDetailIntent;data object Reload:GameDetailIntent }
sealed interface GameDetailEffect { data class OpenEdit(val groupId:String,val gameId:String):GameDetailEffect;data class LifecycleApplied(val action:GameLifecycleAction):GameDetailEffect }

class GameDetailViewModel(private val gateway:GameGateway,groupId:String,gameId:String,role:GroupRoleDto,testScope:CoroutineScope?=null):ViewModel(){
    private val scope=testScope?:viewModelScope
    private val mutable=MutableStateFlow(GameDetailState(groupId,gameId,role))
    val state:StateFlow<GameDetailState> = mutable.asStateFlow()
    private val channel=Channel<GameDetailEffect>(Channel.BUFFERED)
    val effects:Flow<GameDetailEffect> = channel.receiveAsFlow()
    init{load()}
    fun onIntent(intent:GameDetailIntent){when(intent){GameDetailIntent.Refresh,GameDetailIntent.Reload->load();GameDetailIntent.OpenEdit->openEdit();is GameDetailIntent.RequestLifecycle->request(intent.action);GameDetailIntent.DismissConfirmation->if(!mutable.value.isMutating)mutable.value=mutable.value.copy(pendingAction=null);GameDetailIntent.ConfirmLifecycle->confirm()}}
    private fun load(){if(mutable.value.isMutating)return;mutable.value=mutable.value.copy(isLoading=true,error=null,reloadAvailable=false,pendingAction=null);scope.launch{when(val result=gateway.read(mutable.value.groupId,mutable.value.gameId)){is NetworkResult.Success->mutable.value=mutable.value.copy(game=result.value.game,etag=result.value.etag,isLoading=false,error=null);is NetworkResult.Failure->mutable.value=mutable.value.copy(game=null,etag=null,isLoading=false,error=if(result.error.toGameGatewayFailure()==GameGatewayFailure.HiddenResource)GameDetailError.HIDDEN else GameDetailError.UNAVAILABLE)}}}
    private fun openEdit(){val current=mutable.value;if(current.canEdit&&!current.isMutating)channel.trySend(GameDetailEffect.OpenEdit(current.groupId,current.gameId))}
    private fun request(action:GameLifecycleAction){val current=mutable.value;if(!current.isMutating&&action in current.actions)mutable.value=current.copy(pendingAction=action,error=null)}
    private fun confirm(){val current=mutable.value;val action=current.pendingAction?:return;val etag=current.etag?:return;if(current.isMutating||action !in current.actions)return;mutable.value=current.copy(isMutating=true,error=null);scope.launch{when(val result=gateway.lifecycle(current.groupId,current.gameId,etag,action.mutation)){is NetworkResult.Success->{mutable.value=mutable.value.copy(game=result.value.game,etag=result.value.etag,isMutating=false,pendingAction=null,reloadAvailable=false);channel.trySend(GameDetailEffect.LifecycleApplied(action))};is NetworkResult.Failure->fail(result.error.toGameGatewayFailure())}}}
    private fun fail(failure:GameGatewayFailure){val error=when(failure){GameGatewayFailure.Conflict->GameDetailError.CONFLICT;GameGatewayFailure.HiddenResource->GameDetailError.HIDDEN;GameGatewayFailure.InvalidLifecycle->GameDetailError.INVALID_LIFECYCLE;else->GameDetailError.UNAVAILABLE};mutable.value=mutable.value.copy(isMutating=false,pendingAction=null,error=error,reloadAvailable=error==GameDetailError.CONFLICT||error==GameDetailError.INVALID_LIFECYCLE)}
}
