package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.attendance.*
import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.attendance.*
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.sharedkernel.RequestIdentity
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AttendanceControllerTest {
    private val owner = UUID.randomUUID(); private val member = UUID.randomUUID(); private val second = UUID.randomUUID()
    private val group = UUID.randomUUID(); private val game = UUID.randomUUID()
    private lateinit var repository: MemoryRepository; private lateinit var controller: AttendanceController
    private var actor = owner

    @BeforeEach fun setup() {
        repository = MemoryRepository()
        val transaction = object : TransactionRunner { override fun <T> inTransaction(block: () -> T): T = block() }
        val charges = AttendanceChargePort { _, _ -> }
        controller = AttendanceController(
            VerifiedGroupActorResolver { actor },
            RespondAttendance(transaction, repository, charges, { NOW }, UUID::randomUUID),
            AdjustGameCapacity(transaction, repository, charges, { NOW }, UUID::randomUUID),
            repository,
            repository,
        )
    }

    @Test fun `athlete read returns no response and authoritative counts`() { actor = member; val response = controller.read(ID, "$group", "$game"); assertNull(response.ownAttendance); assertEquals(0, response.confirmedCount); assertEquals(2, response.availableSpots) }
    @Test fun `athlete read returns only own confirmed response`() { actor = member; repository.record(member, AttendanceStatus.CONFIRMED); repository.record(second, AttendanceStatus.DECLINED); val own = controller.read(ID, "$group", "$game").ownAttendance!!; assertEquals(member, own.memberId); assertEquals("CONFIRMED", own.status) }
    @Test fun `athlete read returns own stable waitlist position`() { actor = member; repository.record(member, AttendanceStatus.WAITLISTED, 7); val response = controller.read(ID, "$group", "$game"); assertEquals(7, response.ownAttendance!!.waitlistPosition) }
    @Test fun `nonmember attendance read is privacy hidden`() { actor = UUID.randomUUID(); assertFailsWith<GameNotFoundException> { controller.read(ID, "$group", "$game") } }
    @Test fun `self confirm returns ETag audit and updated aggregate`() { actor = member; val response = controller.respond(ID, "$group", "$game", self()); assertEquals("\"1\"", response.headers.eTag); assertEquals("CONFIRMED", response.body!!.attendance.status); assertEquals("SELF", response.body!!.audit!!.source); assertEquals(1, response.body!!.detail.confirmedCount) }
    @Test fun `self confirm at capacity becomes waitlisted`() { actor = member; repository.record(second, AttendanceStatus.CONFIRMED); repository.record(UUID.randomUUID().also(repository.members::add), AttendanceStatus.CONFIRMED); val response = controller.respond(ID, "$group", "$game", self()); assertEquals("WAITLISTED", response.body!!.attendance.status); assertEquals(1, response.body!!.attendance.waitlistPosition) }
    @Test fun `self request exposes no client authored capacity queue charge actor or timestamp`() { assertEquals(setOf("requestId", "intent"), AttendanceSelfRequest::class.java.declaredFields.filterNot { it.isSynthetic }.map { it.name }.toSet()) }
    @Test fun `self response requires request id`() { actor = member; assertFailsWith<InvalidGroupRequestException> { controller.respond(ID, "$group", "$game", self().copy(requestId = null)) } }
    @Test fun `self response rejects unknown intent`() { actor = member; assertFailsWith<InvalidGroupRequestException> { controller.respond(ID, "$group", "$game", self().copy(intent = "WAITLISTED")) } }
    @Test fun `self response retry is equivalent without duplicate audit`() { actor = member; val first = controller.respond(ID, "$group", "$game", self()); val retry = controller.respond(ID, "$group", "$game", self()); assertEquals(first.body!!.attendance, retry.body!!.attendance); assertNull(retry.body!!.audit); assertEquals(1, repository.events.size) }
    @Test fun `self response after deadline is distinct`() { actor = member; repository.deadline = NOW.minusSeconds(1); assertFailsWith<AttendanceDeadlinePassedException> { controller.respond(ID, "$group", "$game", self()) } }
    @Test fun `cancelled game response is distinctly frozen`() { actor = member; repository.status = GameStatus.CANCELLED; assertFailsWith<AttendanceFrozenException> { controller.respond(ID, "$group", "$game", self()) } }
    @Test fun `organizer override returns target and exact audit reason`() { val response = controller.override(ID, "$group", "$game", override()); assertEquals(member, response.body!!.attendance.memberId); assertEquals("ORGANIZER", response.body!!.audit!!.source); assertEquals("Chegou após o prazo", response.body!!.audit!!.reason) }
    @Test fun `athlete cannot override another member`() { actor = member; assertFailsWith<AccessForbiddenException> { controller.override(ID, "$group", "$game", override().copy(memberId = second)) } }
    @Test fun `nonmember override remains privacy hidden`() { actor = UUID.randomUUID(); assertFailsWith<GameNotFoundException> { controller.override(ID, "$group", "$game", override()) } }
    @Test fun `organizer override requires valid reason`() { assertFailsWith<InvalidGroupRequestException> { controller.override(ID, "$group", "$game", override().copy(reason = " ")) } }
    @Test fun `capacity update returns game ETag and promotes without identities`() { repository.record(member, AttendanceStatus.CONFIRMED); repository.record(second, AttendanceStatus.CONFIRMED); val waiting = UUID.randomUUID().also(repository.members::add); repository.record(waiting, AttendanceStatus.WAITLISTED, 1); val response = controller.capacity(ID, "$group", "$game", "\"1\"", CapacityRequest(UUID.randomUUID(), 3)); assertEquals("\"2\"", response.headers.eTag); assertEquals(1, response.body!!.promotedCount); assertEquals(3, response.body!!.detail.confirmedCount); assertTrue(CapacityResponse::class.java.declaredFields.none { it.name.contains("member", true) }) }
    @Test fun `stale capacity returns conflict and preserves game`() { assertFailsWith<VersionConflictException> { controller.capacity(ID, "$group", "$game", "\"2\"", CapacityRequest(UUID.randomUUID(), 3)) }; assertEquals(2, repository.capacity) }
    @Test fun `capacity requires quoted If Match`() { assertFailsWith<PreconditionRequiredException> { controller.capacity(ID, "$group", "$game", null, CapacityRequest(UUID.randomUUID(), 3)) }; assertFailsWith<InvalidGroupRequestException> { controller.capacity(ID, "$group", "$game", "1", CapacityRequest(UUID.randomUUID(), 3)) } }
    @Test fun `capacity rejects out of range value`() { assertFailsWith<InvalidGroupRequestException> { controller.capacity(ID, "$group", "$game", "\"1\"", CapacityRequest(UUID.randomUUID(), 1)) } }
    @Test fun `member roster read returns confirmed and waitlisted names`() { actor = member; repository.record(member, AttendanceStatus.CONFIRMED, name = "Bruno"); repository.record(second, AttendanceStatus.WAITLISTED, 1, "Ana"); val roster = controller.roster(ID, "$group", "$game"); assertEquals(listOf("Bruno"), roster.confirmed.map { it.displayName }); assertEquals(listOf("Ana"), roster.waitlisted.map { it.displayName }); assertEquals(listOf(1L), roster.waitlisted.map { it.waitlistPosition }) }
    @Test fun `nonmember roster read is privacy hidden`() { actor = UUID.randomUUID(); assertFailsWith<GameNotFoundException> { controller.roster(ID, "$group", "$game") } }
    @Test fun `malformed attendance identifiers are privacy hidden`() { actor = member; assertFailsWith<GameNotFoundException> { controller.read(ID, "bad", "bad") } }

    private fun self() = AttendanceSelfRequest(UUID.randomUUID(), "CONFIRM")
    private fun override() = AttendanceOverrideRequest(UUID.randomUUID(), member, "CONFIRM", "Chegou após o prazo")

    private inner class MemoryRepository : AttendanceCommandRepository, AttendanceDetailQuery, AttendanceRosterQuery {
        val members = linkedSetOf(member, second); val records = linkedMapOf<UUID, AttendanceRecord>(); val events = mutableListOf<AttendanceEvent>()
        val names = mutableMapOf<UUID, String>()
        var status = GameStatus.PUBLISHED; var deadline = NOW.plusSeconds(60); var capacity = 2; var version = 1L; var allocator = 0L
        override fun lock(groupId: UUID, gameId: UUID, memberId: UUID, actorId: UUID): AttendanceAggregate? = if (groupId == group && gameId == game && memberId in members) AttendanceAggregate(group, game, memberId, actorId, role(actorId), status, deadline, capacity, confirmed(), records[memberId], 2500, LocalDate.of(2026, 8, 12)) else null
        override fun lockCapacity(groupId: UUID, gameId: UUID, actorId: UUID): CapacityAggregate? = if (groupId == group && gameId == game) CapacityAggregate(group, game, actorId, role(actorId), status, deadline, capacity, confirmed(), version, 2500, LocalDate.of(2026, 8, 12)) else null
        override fun nextWaitlistSequence(groupId: UUID, gameId: UUID) = ++allocator
        override fun earliestWaitlisted(groupId: UUID, gameId: UUID) = records.values.filter { it.status == AttendanceStatus.WAITLISTED }.minByOrNull { it.waitlistSequence!! }
        override fun save(record: AttendanceRecord) { records[record.memberId] = record }
        override fun append(event: AttendanceEvent) { events += event }
        override fun updateCapacity(gameId: UUID, expectedVersion: Long, capacity: Int): Boolean { if (version != expectedVersion) return false; this.capacity = capacity; version++; return true }
        override fun find(actorId: UUID, groupId: UUID, gameId: UUID): AttendanceDetail? = if (groupId == group && gameId == game && role(actorId) != null) AttendanceDetail(records[actorId], confirmed(), (capacity - confirmed()).coerceAtLeast(0), records.values.count { it.status == AttendanceStatus.WAITLISTED }, capacity, version) else null
        override fun roster(actorId: UUID, groupId: UUID, gameId: UUID): AttendanceRoster? {
            if (groupId != group || gameId != game || role(actorId) == null) return null
            fun entries(status: AttendanceStatus) = records.values.filter { it.status == status }
                .map { AttendanceRosterMember(it.memberId, names[it.memberId] ?: "Atleta", it.waitlistSequence) }
            return AttendanceRoster(
                entries(AttendanceStatus.CONFIRMED).sortedBy { it.displayName.lowercase() },
                entries(AttendanceStatus.WAITLISTED).sortedBy { it.waitlistPosition },
            )
        }
        fun record(memberId: UUID, status: AttendanceStatus, sequence: Long? = null, name: String? = null) { records[memberId] = AttendanceRecord(game, group, memberId, status, sequence, NOW, NOW, 1); name?.let { names[memberId] = it }; if (sequence != null) allocator = maxOf(allocator, sequence) }
        private fun confirmed() = records.values.count { it.status == AttendanceStatus.CONFIRMED }
        private fun role(actorId: UUID) = when (actorId) { owner -> GroupRole.OWNER; in members -> GroupRole.ATHLETE; else -> null }
    }

    private companion object { val NOW: Instant = Instant.parse("2026-08-01T10:00:00Z"); val ID = RequestIdentity(subject = "subject", emailVerified = true, displayName = "Player") }
}
