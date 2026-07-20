package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.game.*
import br.com.saqz.groups.application.attendance.*
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.game.*
import br.com.saqz.sharedkernel.RequestIdentity
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.test.*

class GameControllerTest {
    private val actor = UUID.randomUUID(); private val group = UUID.randomUUID(); private lateinit var repository: MemoryRepository
    private lateinit var effects: RecordingEffects; private lateinit var controller: GameController

    @BeforeEach fun setup() {
        repository = MemoryRepository(); effects = RecordingEffects()
        val tx = object : TransactionRunner { override fun <T> inTransaction(block: () -> T): T = block() }
        val counts = GameAttendanceCountSource { ids -> ids.associateWith { GameAttendanceCounts(3, 2) } }
        val attendance = AttendanceDetailQuery { _, _, gameId -> AttendanceDetail(AttendanceRecord(gameId, group, actor, AttendanceStatus.WAITLISTED, 4, START, START, 2), 3, 21, 2, 24, 1) }
        controller = GameController(VerifiedGroupActorResolver { actor }, CreateGame(tx, repository), EditGame(tx, repository, effects), ChangeGameLifecycle(tx, repository, effects), ListGames(repository, counts), GetGame(repository, counts), attendance)
    }

    @Test fun `create returns 201 quoted ETag and server state`() { val response = controller.create(ID, "$group", request()); assertEquals(201, response.statusCode.value()); assertEquals("\"1\"", response.headers.eTag); assertEquals("DRAFT", response.body!!.status); assertEquals(0, response.body!!.confirmedCount) }
    @Test fun `create retry with same request id returns the authoritative original`() { val id=UUID.randomUUID(); controller.create(ID,"$group",request(id)); val replay=controller.create(ID,"$group",request(id).copy(title="Different")); assertEquals(1,repository.games.size); assertEquals("Treino semanal",replay.body!!.title) }
    @Test fun `create requires request id`() { assertFailsWith<InvalidGroupRequestException> { controller.create(ID,"$group",request().copy(requestId=null)) } }
    @Test fun `create reports all invalid fields`() { val failure=assertFailsWith<InvalidGroupRequestException>{ controller.create(ID,"$group",request().copy(title="x",durationMinutes=2,capacity=1)) }; assertTrue(failure.fieldErrors.keys.containsAll(listOf("title","durationMinutes","capacity"))) }
    @Test fun `athlete cannot create`() { repository.role=GroupRole.ATHLETE; assertFailsWith<AccessForbiddenException>{controller.create(ID,"$group",request())} }
    @Test fun `nonmember create is hidden`() { repository.role=null; assertFailsWith<GameNotFoundException>{controller.create(ID,"$group",request())} }
    @Test fun `owner list includes drafts and derived counts`() { val game=seed(); val listed=controller.list(ID,"$group").single(); assertEquals(game.id,listed.id); assertEquals(3,listed.confirmedCount); assertEquals(21,listed.availableSpots); assertEquals(2,listed.waitlistCount) }
    @Test fun `athlete list hides drafts`() { seed(); repository.role=GroupRole.ATHLETE; assertTrue(controller.list(ID,"$group").isEmpty()) }
    @Test fun `nonmember list is privacy not found`() { repository.role=null; assertFailsWith<GameNotFoundException>{controller.list(ID,"$group")} }
    @Test fun `read returns authoritative counts and quoted ETag`() { val game=seed(GameStatus.PUBLISHED); val response=controller.read(ID,"$group","${game.id}"); assertEquals("\"1\"",response.headers.eTag); assertEquals(3,response.body!!.confirmedCount) }
    @Test fun `read includes only callers own attendance`() { val game=seed(GameStatus.PUBLISHED); val response=controller.read(ID,"$group","${game.id}"); assertEquals(actor,response.body!!.ownAttendance!!.memberId); assertEquals("WAITLISTED",response.body!!.ownAttendance!!.status); assertEquals(4,response.body!!.ownAttendance!!.waitlistPosition) }
    @Test fun `athlete draft read is hidden`() { val game=seed(); repository.role=GroupRole.ATHLETE; assertFailsWith<GameNotFoundException>{controller.read(ID,"$group","${game.id}")} }
    @Test fun `malformed resource identifiers are hidden`() { assertFailsWith<GameNotFoundException>{controller.read(ID,"bad","also-bad")} }
    @Test fun `edit returns incremented ETag and immutable response fields`() { val game=seed(); val response=controller.edit(ID,"$group","${game.id}","\"1\"",request().copy(requestId=null,title="Treino novo")); assertEquals("\"2\"",response.headers.eTag); assertEquals("Treino novo",response.body!!.title) }
    @Test fun `edit requires If-Match`() { val game=seed(); assertFailsWith<PreconditionRequiredException>{controller.edit(ID,"$group","${game.id}",null,request())} }
    @Test fun `edit rejects malformed If-Match`() { val game=seed(); assertFailsWith<InvalidGroupRequestException>{controller.edit(ID,"$group","${game.id}","1",request())} }
    @Test fun `stale edit returns version conflict and preserves game`() { val game=seed(); assertFailsWith<VersionConflictException>{controller.edit(ID,"$group","${game.id}","\"2\"",request().copy(title="Changed"))}; assertEquals("Treino semanal",repository.games.getValue(game.id).snapshot.title) }
    @Test fun `publish returns published state and attendance effect`() { val game=seed(); val response=controller.publish(ID,"$group","${game.id}","\"1\""); assertEquals("PUBLISHED",response.body!!.status); assertTrue(GameSideEffect.ATTENDANCE_OPENED in effects.last) }
    @Test fun `invalid lifecycle transition has stable exception`() { val game=seed(); assertFailsWith<InvalidGameTransitionException>{controller.complete(ID,"$group","${game.id}","\"1\"")} }
    @Test fun `cancel reports finance review without refund field`() { val game=seed(GameStatus.PUBLISHED); val response=controller.cancel(ID,"$group","${game.id}","\"1\""); assertEquals("CANCELLED",response.body!!.status); assertTrue(response.body!!.financeReviewRequired); assertTrue(GameSideEffect.PENDING_CHARGES_CANCELLED in effects.last) }
    @Test fun `complete freezes published game`() { val game=seed(GameStatus.PUBLISHED); val response=controller.complete(ID,"$group","${game.id}","\"1\""); assertEquals("COMPLETED",response.body!!.status); assertEquals(setOf(GameSideEffect.ATTENDANCE_FROZEN),effects.last) }

    private fun seed(status: GameStatus=GameStatus.DRAFT): Game { val game=Game(UUID.randomUUID(),group,snapshot(),status); repository.games[game.id]=game; return game }
    private fun request(id: UUID=UUID.randomUUID()) = GameWriteRequest(id,"Treino semanal",GameVenueRequest(null,"Arena Central","Rua das Flores 100","Quadra 2"),DATE,LocalTime.of(19,30),"America/Sao_Paulo",START,90,24,START.minusSeconds(10800),2500,false,"Levar bola")
    private fun snapshot() = GameSnapshot("Treino semanal",GameVenueSnapshot(null,"Arena Central","Rua das Flores 100","Quadra 2"),DATE,LocalTime.of(19,30),br.com.saqz.groups.domain.IanaTimeZone.from("America/Sao_Paulo"),START,90,24,START.minusSeconds(10800),2500,"Levar bola")

    private inner class MemoryRepository : GameCommandRepository, GameQueryRepository {
        var role: GroupRole?=GroupRole.OWNER; val games=linkedMapOf<UUID,Game>()
        override fun creationContext(actor:UUID,groupId:UUID)=if(groupId==group) GameCreationContext(role,GroupGameDefaults()) else null
        override fun find(actor:UUID,groupId:UUID,gameId:UUID)=if(groupId==group&&role!=null) games[gameId]?.let{GameCommandContext(role,it)} else null
        override fun create(game:Game):GameWriteResult { val stored=games.getOrPut(game.id){game}; return GameWriteResult.Saved(stored) }
        override fun update(game:Game,expectedVersion:Long):GameWriteResult { val old=games[game.id]?:return GameWriteResult.NotFound; if(old.version!=expectedVersion)return GameWriteResult.VersionConflict; val saved=game.copy(version=expectedVersion+1);games[game.id]=saved;return GameWriteResult.Saved(saved) }
        override fun role(actor:UUID,groupId:UUID)=if(groupId==group) role else null
        override fun list(groupId:UUID)=if(groupId==group) games.values.toList() else emptyList()
        override fun find(groupId:UUID,gameId:UUID)=if(groupId==group)games[gameId] else null
    }
    private class RecordingEffects:GameSideEffectPort { var last:Set<GameSideEffect> = emptySet(); override fun apply(game:Game,actorId:UUID,effects:Set<GameSideEffect>){last=effects} }
    private companion object { val ID=RequestIdentity("subject",emailVerified=true,displayName="Player"); val DATE=LocalDate.of(2026,8,12); val START=Instant.parse("2026-08-12T22:30:00Z") }
}
