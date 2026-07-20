package br.com.saqz.groups.presentation.games.list

import br.com.saqz.groups.data.GroupRoleDto
import br.com.saqz.groups.data.game.*
import br.com.saqz.network.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class GamesViewModelTest {
    @Test fun `selection clears previous group before loading`()=runTest{val f=fixture(this);f.vm.onIntent(select(A));runCurrent();f.gateway.pending[B]=CompletableDeferred();f.vm.onIntent(select(B));assertEquals(B,f.vm.state.value.groupId);assertTrue(f.vm.state.value.upcoming.isEmpty());assertTrue(f.vm.state.value.past.isEmpty());assertTrue(f.vm.state.value.isLoading);f.gateway.pending.getValue(B).complete(success(emptyList()));runCurrent()}
    @Test fun `success splits upcoming and past and orders authoritatively`()=runTest{val f=fixture(this);f.gateway.results[A]=success(listOf(game("future-2","2026-08-20"),game("past","2026-08-01"),game("future-1","2026-08-12")));f.vm.onIntent(select(A));runCurrent();assertEquals(listOf("future-1","future-2"),f.vm.state.value.upcoming.map{it.id});assertEquals(listOf("past"),f.vm.state.value.past.map{it.id})}
    @Test fun `presentation uses pt BR date time and server availability`()=runTest{val f=fixture(this);f.vm.onIntent(select(A));runCurrent();val item=f.vm.state.value.upcoming.single();assertEquals("12/08/2026",item.dateText);assertEquals("19:30",item.timeText);assertEquals("Arena Central",item.venueText);assertEquals(21,item.availableSpots);assertEquals(2,item.waitlistCount)}
    @Test fun `empty success is distinct from loading and error`()=runTest{val f=fixture(this);f.gateway.results[A]=success(emptyList());f.vm.onIntent(select(A));runCurrent();assertFalse(f.vm.state.value.isLoading);assertNull(f.vm.state.value.error);assertTrue(f.vm.state.value.upcoming.isEmpty())}
    @Test fun `initial failure exposes error without protected content`()=runTest{val f=fixture(this);f.gateway.results[A]=NetworkResult.Failure(NetworkError.Unavailable);f.vm.onIntent(select(A));runCurrent();assertEquals(GamesLoadError.UNAVAILABLE,f.vm.state.value.error);assertTrue(f.vm.state.value.upcoming.isEmpty())}
    @Test fun `refresh keeps content while request is pending`()=runTest{val f=fixture(this);f.vm.onIntent(select(A));runCurrent();f.gateway.pending[A]=CompletableDeferred();f.vm.onIntent(GamesIntent.Refresh);runCurrent();assertTrue(f.vm.state.value.isRefreshing);assertEquals(listOf("future"),f.vm.state.value.upcoming.map{it.id});f.gateway.pending.getValue(A).complete(success(listOf(game("new","2026-08-19"))));runCurrent();assertEquals(listOf("new"),f.vm.state.value.upcoming.map{it.id})}
    @Test fun `duplicate refresh is single flight`()=runTest{val f=fixture(this);f.vm.onIntent(select(A));runCurrent();f.gateway.pending[A]=CompletableDeferred();f.vm.onIntent(GamesIntent.Refresh);f.vm.onIntent(GamesIntent.Refresh);runCurrent();assertEquals(2,f.gateway.calls.count{it==A});f.gateway.pending.getValue(A).complete(success(emptyList()));runCurrent()}
    @Test fun `stale prior group response cannot replace selected group`()=runTest{val f=fixture(this);f.gateway.pending[A]=CompletableDeferred();f.vm.onIntent(select(A));runCurrent();f.vm.onIntent(select(B));runCurrent();f.gateway.pending.getValue(A).complete(success(listOf(game("stale","2026-08-20"))));runCurrent();assertEquals(B,f.vm.state.value.groupId);assertFalse(f.vm.state.value.upcoming.any{it.id=="stale"})}
    @Test fun `organizer receives one create navigation effect`()=runTest{val f=fixture(this);f.vm.onIntent(select(A,GroupRoleDto.ADMIN));runCurrent();val effect=async{f.vm.effects.first()};f.vm.onIntent(GamesIntent.OpenCreate);f.vm.onIntent(GamesIntent.OpenCreate);assertEquals(GamesEffect.OpenCreate(A),effect.await());assertNull(withTimeoutOrNull(1){f.vm.effects.first()})}
    @Test fun `athlete cannot open create`()=runTest{val f=fixture(this);f.vm.onIntent(select(A,GroupRoleDto.ATHLETE));runCurrent();f.vm.onIntent(GamesIntent.OpenCreate);assertNull(withTimeoutOrNull(1){f.vm.effects.first()});assertFalse(f.vm.state.value.canCreate)}
    @Test fun `member receives one allowed game navigation effect`()=runTest{val f=fixture(this);f.vm.onIntent(select(A,GroupRoleDto.ATHLETE));runCurrent();val effect=async{f.vm.effects.first()};f.vm.onIntent(GamesIntent.OpenGame("future"));f.vm.onIntent(GamesIntent.OpenGame("future"));assertEquals(GamesEffect.OpenGame(A,"future"),effect.await());assertNull(withTimeoutOrNull(1){f.vm.effects.first()})}
    @Test fun `unknown or loading game open is ignored`()=runTest{val f=fixture(this);f.gateway.pending[A]=CompletableDeferred();f.vm.onIntent(select(A));runCurrent();f.vm.onIntent(GamesIntent.OpenGame("future"));assertNull(withTimeoutOrNull(1){f.vm.effects.first()});f.gateway.pending.getValue(A).complete(success(listOf(game("future","2026-08-12"))));runCurrent();f.vm.onIntent(GamesIntent.OpenGame("unknown"));assertNull(withTimeoutOrNull(1){f.vm.effects.first()})}

    private fun fixture(scope:kotlinx.coroutines.CoroutineScope):Fixture{val gateway=FakeGateway();gateway.results[A]=success(listOf(game("future","2026-08-12")));gateway.results[B]=success(listOf(game("b","2026-08-13")));return Fixture(GamesViewModel(gateway,scope),gateway)}
    private fun select(group:String,role:GroupRoleDto=GroupRoleDto.OWNER)=GamesIntent.SelectGroup(group,role,"2026-08-10")
    private fun game(id:String,date:String)=GameDto(id,A,"Treino semanal",GameVenueDto(null,"Arena Central","Rua das Flores 100"),date,"19:30:00","America/Sao_Paulo","${date}T22:30:00Z",90,24,"${date}T19:30:00Z",2500,null,GameStatusDto.PUBLISHED,1,3,21,2)
    private fun success(games:List<GameDto>)=NetworkResult.Success(games)
    private class FakeGateway:GameGateway{val results=mutableMapOf<String,NetworkResult<List<GameDto>>>();val pending=mutableMapOf<String,CompletableDeferred<NetworkResult<List<GameDto>>>>();val calls=mutableListOf<String>();override suspend fun list(groupId:String):NetworkResult<List<GameDto>>{calls+=groupId;return pending[groupId]?.await()?:results.getValue(groupId)};override suspend fun read(groupId:String,gameId:String)=error("unused");override suspend fun create(groupId:String,command:GameWriteCommand)=error("unused");override suspend fun edit(groupId:String,gameId:String,etag:String,command:GameWriteCommand)=error("unused");override suspend fun lifecycle(groupId:String,gameId:String,etag:String,mutation:String)=error("unused");override suspend fun createSeries(groupId:String,command:WeeklySeriesWriteCommand)=error("unused");override suspend fun readSeries(groupId:String,seriesId:String)=error("unused");override suspend fun boundary(groupId:String,seriesId:String,etag:String,command:SeriesBoundaryCommand)=error("unused")}
    private data class Fixture(val vm:GamesViewModel,val gateway:FakeGateway)
    private companion object{const val A="group-a";const val B="group-b"}
}
