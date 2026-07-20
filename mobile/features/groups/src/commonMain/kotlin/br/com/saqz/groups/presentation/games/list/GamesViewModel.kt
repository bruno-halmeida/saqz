package br.com.saqz.groups.presentation.games.list

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.saqz.groups.data.GroupRoleDto
import br.com.saqz.groups.data.game.GameDto
import br.com.saqz.groups.data.game.GameGateway
import br.com.saqz.groups.data.game.GameStatusDto
import br.com.saqz.network.NetworkResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@Immutable data class GameListItem(val id:String,val title:String,val dateText:String,val timeText:String,val venueText:String,val status:GameStatusDto,val availableSpots:Int,val waitlistCount:Int,val startsAt:String)
enum class GamesLoadError { UNAVAILABLE, HIDDEN }
@Immutable data class GamesState(val groupId:String?=null,val role:GroupRoleDto?=null,val upcoming:List<GameListItem> = emptyList(),val past:List<GameListItem> = emptyList(),val isLoading:Boolean=false,val isRefreshing:Boolean=false,val error:GamesLoadError?=null){val canCreate:Boolean get()=role==GroupRoleDto.OWNER||role==GroupRoleDto.ADMIN}
sealed interface GamesIntent{data class SelectGroup(val groupId:String,val role:GroupRoleDto,val today:String):GamesIntent;data object Refresh:GamesIntent;data class OpenGame(val gameId:String):GamesIntent;data object OpenCreate:GamesIntent}
sealed interface GamesEffect{data class OpenGame(val groupId:String,val gameId:String):GamesEffect;data class OpenCreate(val groupId:String):GamesEffect}

class GamesViewModel(private val gateway:GameGateway,testScope:CoroutineScope?=null):ViewModel(){
    private val scope=testScope?:viewModelScope;private val mutable=MutableStateFlow(GamesState());val state:StateFlow<GamesState> = mutable.asStateFlow();private val channel=Channel<GamesEffect>(Channel.BUFFERED);val effects:Flow<GamesEffect> = channel.receiveAsFlow();private var generation=0L;private var today="9999-12-31";private val emittedNavigations=mutableSetOf<String>()
    fun onIntent(intent:GamesIntent){when(intent){is GamesIntent.SelectGroup->select(intent);GamesIntent.Refresh->refresh();is GamesIntent.OpenGame->open(intent.gameId);GamesIntent.OpenCreate->create()}}
    private fun select(intent:GamesIntent.SelectGroup){generation++;today=intent.today;emittedNavigations.clear();mutable.value=GamesState(groupId=intent.groupId,role=intent.role,isLoading=true);load(intent.groupId,generation,false)}
    private fun refresh(){val current=mutable.value;val group=current.groupId?:return;if(current.isLoading||current.isRefreshing)return;mutable.value=current.copy(isRefreshing=true,error=null);load(group,generation,true)}
    private fun load(group:String,operation:Long,refresh:Boolean){scope.launch{when(val result=gateway.list(group)){is NetworkResult.Success->{if(operation!=generation)return@launch;val items=result.value.sortedBy{it.startsAt}.map{it.item()};mutable.value=mutable.value.copy(upcoming=items.filter{it.localDate()>=today},past=items.filter{it.localDate()<today}.reversed(),isLoading=false,isRefreshing=false,error=null)};is NetworkResult.Failure->{if(operation!=generation)return@launch;mutable.value=mutable.value.copy(upcoming=if(refresh)mutable.value.upcoming else emptyList(),past=if(refresh)mutable.value.past else emptyList(),isLoading=false,isRefreshing=false,error=GamesLoadError.UNAVAILABLE)}}}}
    private fun open(gameId:String){val current=mutable.value;val group=current.groupId?:return;if(current.isLoading)return;if((current.upcoming+current.past).none{it.id==gameId})return;if(emittedNavigations.add("game:$gameId"))channel.trySend(GamesEffect.OpenGame(group,gameId))}
    private fun create(){val current=mutable.value;val group=current.groupId?:return;if(!current.canCreate||current.isLoading)return;if(emittedNavigations.add("create"))channel.trySend(GamesEffect.OpenCreate(group))}
}
private fun GameDto.item()=GameListItem(id,title,localDate.isoDatePtBr(),localTime.take(5),venue.name,status,availableSpots,waitlistCount,startsAt)
private fun GameListItem.localDate()=dateText.split('/').let{"${it[2]}-${it[1]}-${it[0]}"}
private fun String.isoDatePtBr()=split('-').let{"${it[2]}/${it[1]}/${it[0]}"}
