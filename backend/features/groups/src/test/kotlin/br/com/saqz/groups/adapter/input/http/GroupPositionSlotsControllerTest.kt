package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.application.read.GroupReadSnapshot
import br.com.saqz.groups.application.settings.GroupPositionSlotsRepository
import br.com.saqz.groups.application.settings.GroupPositionSlotsService
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.AthletePosition
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import br.com.saqz.groups.domain.attendance.GameSystem
import br.com.saqz.sharedkernel.RequestIdentity
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GroupPositionSlotsControllerTest {
    private val group = UUID.randomUUID()
    private var role: GroupRole? = GroupRole.OWNER
    private var version = 3L
    private var stored: List<Pair<AthletePosition, Int>> = emptyList()
    private var system: GameSystem? = null
    private lateinit var controller: GroupPositionSlotsController

    @BeforeEach fun setup() {
        val transaction = object : TransactionRunner { override fun <T> inTransaction(block: () -> T): T = block() }
        val reads = object : GroupReadRepository {
            override fun find(key: GroupReadKey) = if (role == null) null else
                GroupReadSnapshot(group, AccessName.from("Grupo"), IanaTimeZone.from("America/Sao_Paulo"), role, version)
        }
        val repository = object : GroupPositionSlotsRepository {
            override fun read(groupId: UUID) = stored
            override fun gameSystem(groupId: UUID) = system
            override fun replace(groupId: UUID, expectedVersion: Long, system: GameSystem?, slots: List<Pair<AthletePosition, Int>>): Boolean {
                if (expectedVersion != version) return false
                stored = slots; this@GroupPositionSlotsControllerTest.system = system; version++
                return true
            }
        }
        controller = GroupPositionSlotsController(VerifiedGroupActorResolver { UUID.randomUUID() }, GroupPositionSlotsService(transaction, reads, repository))
    }

    private fun body(vararg rows: Pair<AthletePosition, Int>, system: GameSystem? = GameSystem.FIVE_ONE) =
        PositionSlotsRequest(system, rows.map { PositionSlotRequest(it.first, it.second) })

    @Test fun `group without positions reads as empty`() {
        val response = controller.read(ID, "$group").body!!
        assertEquals(emptyList(), response.positionSlots)
        assertEquals(0, response.total)
    }

    @Test fun `owner writes positions, reads them back with the sum and a new version`() {
        val saved = controller.write(ID, "$group", "\"3\"", body(AthletePosition.PONTA to 4, AthletePosition.CENTRAL to 4, AthletePosition.LEVANTADOR to 2)).body!!
        assertEquals(10, saved.total)
        assertEquals("\"4\"", controller.read(ID, "$group").headers.eTag)
        assertEquals(GameSystem.FIVE_ONE, controller.read(ID, "$group").body!!.gameSystem)
        assertEquals(listOf(AthletePosition.PONTA, AthletePosition.CENTRAL, AthletePosition.LEVANTADOR), controller.read(ID, "$group").body!!.positionSlots.map { it.position })
    }

    @Test fun `empty list turns positions off`() {
        controller.write(ID, "$group", "\"3\"", body(AthletePosition.PONTA to 6))
        controller.write(ID, "$group", "\"4\"", PositionSlotsRequest(GameSystem.SIX_ZERO, emptyList()))
        assertEquals(emptyList(), controller.read(ID, "$group").body!!.positionSlots)
    }

    @Test fun `invalid configurations are 400 on positionSlots`() {
        listOf(
            body(AthletePosition.PONTA to -1, AthletePosition.CENTRAL to 6),
            body(AthletePosition.PONTA to 2, AthletePosition.PONTA to 2),
            body(AthletePosition.PONTA to 0),
            body(AthletePosition.PONTA to 2),
        ).forEach {
            val failure = assertFailsWith<InvalidGroupRequestException> { controller.write(ID, "$group", "\"3\"", it) }
            assertEquals(setOf("positionSlots"), failure.fieldErrors.keys)
        }
    }

    @Test fun `athlete cannot write, stale version conflicts, missing If-Match is required`() {
        role = GroupRole.ATHLETE
        assertFailsWith<AccessForbiddenException> { controller.write(ID, "$group", "\"3\"", body(AthletePosition.PONTA to 6)) }
        role = GroupRole.OWNER
        assertFailsWith<VersionConflictException> { controller.write(ID, "$group", "\"2\"", body(AthletePosition.PONTA to 6)) }
        assertFailsWith<PreconditionRequiredException> { controller.write(ID, "$group", null, body(AthletePosition.PONTA to 6)) }
    }

    @Test fun `non member sees the group as missing`() {
        role = null
        assertFailsWith<GroupNotFoundException> { controller.read(ID, "$group") }
    }

    @Test fun `suggestion returns the 5-1 table, an empty 6-0 and refuses bad input`() {
        val five = controller.suggestion(ID, "FIVE_ONE", null)
        assertEquals(2, five.teams); assertEquals(12, five.total)
        assertEquals(0, controller.suggestion(ID, "SIX_ZERO", 3).total)
        assertFailsWith<InvalidGroupRequestException> { controller.suggestion(ID, "SEVEN_ONE", null) }
        assertFailsWith<InvalidGroupRequestException> { controller.suggestion(ID, "FIVE_ONE", 9) }
    }

    private companion object { val ID = RequestIdentity(subject = "s", emailVerified = true, displayName = "P") }
}
