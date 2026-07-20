package br.com.saqz.groups.presentation.games.detail

import br.com.saqz.groups.data.GroupRoleDto
import br.com.saqz.groups.data.game.*
import br.com.saqz.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class GameDetailViewModelTest {
    @Test fun `initial load exposes authoritative snapshot and etag`()=runTest{val f=fixture(this);runCurrent();assertEquals("Treino",f.vm.state.value.game?.title);assertEquals("\"7\"",f.vm.state.value.etag);assertFalse(f.vm.state.value.isLoading)}
    @Test fun `athlete sees snapshot without organizer actions`()=runTest{val f=fixture(this,role=GroupRoleDto.ATHLETE);runCurrent();assertTrue(f.vm.state.value.actions.isEmpty());assertFalse(f.vm.state.value.canEdit)}
    @Test fun `draft organizer can edit and publish only`()=runTest{val f=fixture(this);runCurrent();assertTrue(f.vm.state.value.canEdit);assertEquals(listOf(GameLifecycleAction.PUBLISH),f.vm.state.value.actions)}
    @Test fun `published organizer can edit cancel and complete`()=runTest{val f=fixture(this,initial=versioned(GameStatusDto.PUBLISHED));runCurrent();assertEquals(listOf(GameLifecycleAction.CANCEL,GameLifecycleAction.COMPLETE),f.vm.state.value.actions);assertTrue(f.vm.state.value.canEdit)}
    @Test fun `request lifecycle requires confirmation before network`()=runTest{val f=fixture(this);runCurrent();f.vm.onIntent(GameDetailIntent.RequestLifecycle(GameLifecycleAction.PUBLISH));assertEquals(GameLifecycleAction.PUBLISH,f.vm.state.value.pendingAction);assertTrue(f.gateway.lifecycleCalls.isEmpty())}
    @Test fun `confirmed lifecycle uses current etag updates snapshot and emits once`()=runTest{val f=fixture(this);runCurrent();val effect=async{f.vm.effects.first()};f.vm.onIntent(GameDetailIntent.RequestLifecycle(GameLifecycleAction.PUBLISH));f.vm.onIntent(GameDetailIntent.ConfirmLifecycle);runCurrent();assertEquals("\"7\"",f.gateway.lifecycleCalls.single().etag);assertEquals("publish",f.gateway.lifecycleCalls.single().mutation);assertEquals(GameStatusDto.PUBLISHED,f.vm.state.value.game?.status);assertEquals(GameDetailEffect.LifecycleApplied(GameLifecycleAction.PUBLISH),effect.await())}
    @Test fun `duplicate confirm is single flight`()=runTest{val f=fixture(this);runCurrent();f.gateway.gate=CompletableDeferred();f.vm.onIntent(GameDetailIntent.RequestLifecycle(GameLifecycleAction.PUBLISH));f.vm.onIntent(GameDetailIntent.ConfirmLifecycle);f.vm.onIntent(GameDetailIntent.ConfirmLifecycle);runCurrent();assertEquals(1,f.gateway.lifecycleCalls.size);f.gateway.gate!!.complete(Unit);runCurrent()}
    @Test fun `conflict preserves snapshot and offers authoritative reload`()=runTest{val f=fixture(this,lifecycleResult=NetworkResult.Failure(problem("VERSION_CONFLICT",409)));runCurrent();f.vm.onIntent(GameDetailIntent.RequestLifecycle(GameLifecycleAction.PUBLISH));f.vm.onIntent(GameDetailIntent.ConfirmLifecycle);runCurrent();assertEquals(GameStatusDto.DRAFT,f.vm.state.value.game?.status);assertEquals(GameDetailError.CONFLICT,f.vm.state.value.error);assertTrue(f.vm.state.value.reloadAvailable);f.gateway.readResult=versioned(GameStatusDto.PUBLISHED).success();f.vm.onIntent(GameDetailIntent.Reload);runCurrent();assertEquals(GameStatusDto.PUBLISHED,f.vm.state.value.game?.status)}
    @Test fun `terminal snapshot is read only and ignores edit`()=runTest{val f=fixture(this,initial=versioned(GameStatusDto.CANCELLED));runCurrent();val effect=async{withTimeoutOrNull(1){f.vm.effects.first()}};f.vm.onIntent(GameDetailIntent.OpenEdit);assertTrue(f.vm.state.value.terminal);assertTrue(f.vm.state.value.actions.isEmpty());assertNull(effect.await())}

    private fun fixture(scope:kotlinx.coroutines.CoroutineScope,role:GroupRoleDto=GroupRoleDto.OWNER,initial:VersionedGameDto=versioned(),lifecycleResult:NetworkResult<VersionedGameDto> = versioned(GameStatusDto.PUBLISHED).success()):Fixture{val gateway=FakeGateway(initial.success(),lifecycleResult);return Fixture(GameDetailViewModel(gateway,"group","game",role,scope),gateway)}
    private fun versioned(status:GameStatusDto=GameStatusDto.DRAFT)=VersionedGameDto(GameDto("game","group","Treino",GameVenueDto(null,"Arena","Rua 1"),"2026-08-12","19:30:00","America/Sao_Paulo","2026-08-12T22:30:00Z",90,24,"2026-08-12T19:00:00Z",2500,"Notas",status,7,3,21,2,status==GameStatusDto.CANCELLED),"\"7\"")
    private fun VersionedGameDto.success()=NetworkResult.Success(this)
    private fun problem(code:String,status:Int)=NetworkError.ApiProblemError(ApiProblem(status,code,"c"))
    private data class Call(val etag:String,val mutation:String)
    private class FakeGateway(var readResult:NetworkResult<VersionedGameDto>,var lifecycleResult:NetworkResult<VersionedGameDto>):GameGateway{val lifecycleCalls=mutableListOf<Call>();var gate:CompletableDeferred<Unit>?=null;override suspend fun read(groupId:String,gameId:String)=readResult;override suspend fun lifecycle(groupId:String,gameId:String,etag:String,mutation:String):NetworkResult<VersionedGameDto>{lifecycleCalls+=Call(etag,mutation);gate?.await();return lifecycleResult};override suspend fun list(groupId:String)=error("unused");override suspend fun create(groupId:String,command:GameWriteCommand)=error("unused");override suspend fun edit(groupId:String,gameId:String,etag:String,command:GameWriteCommand)=error("unused");override suspend fun createSeries(groupId:String,command:WeeklySeriesWriteCommand)=error("unused");override suspend fun readSeries(groupId:String,seriesId:String)=error("unused");override suspend fun boundary(groupId:String,seriesId:String,etag:String,command:SeriesBoundaryCommand)=error("unused")}
    private data class Fixture(val vm:GameDetailViewModel,val gateway:FakeGateway)
}
