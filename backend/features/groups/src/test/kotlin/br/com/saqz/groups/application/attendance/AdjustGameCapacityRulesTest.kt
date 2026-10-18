package br.com.saqz.groups.application.attendance

import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.group.PromotionMode
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** PROM-006: aumentar vagas sobe a fila na mesma conta; quem pode, quando e com que versão. */
class AdjustGameCapacityRulesTest {
    private val group = UUID.randomUUID()
    private val game = UUID.randomUUID()
    private val owner = UUID.randomUUID()
    private val admin = UUID.randomUUID()
    private val athlete = UUID.randomUUID()
    private val waiting = List(4) { UUID.randomUUID() }
    private val repository = InMemoryAttendance(group, game, NOW).apply {
        roles[owner] = GroupRole.OWNER
        roles[admin] = GroupRole.ADMIN
        roles[athlete] = GroupRole.ATHLETE
        gameVersion = 3
        capacity = 4
    }
    private val charges = RecordingCharges()
    private val service = AdjustGameCapacity(DIRECT_TRANSACTION, repository, charges, { NOW })

    private fun fill() {
        repeat(4) { repository.record(UUID.randomUUID(), AttendanceStatus.CONFIRMED) }
        waiting.forEachIndexed { i, id -> repository.record(id, AttendanceStatus.WAITLISTED, sequence = i + 1L) }
    }

    @Test
    fun `aumentar sobe so quem cabe nas vagas novas, na ordem da fila, e devolve a versao seguinte`() {
        fill()

        val result = assertIs<CapacityCommandResult.Success>(service.execute(owner, group, game, 3, 6))

        assertEquals(6, result.capacity)
        assertEquals(4L, result.version)
        assertEquals(waiting.take(2), result.promoted.map { it.memberId })
        assertEquals(6, repository.updatedCapacity)
        assertEquals(List(2) { AttendanceStatus.CONFIRMED } + List(2) { AttendanceStatus.WAITLISTED }, waiting.map { repository.status(it) })
        assertEquals(waiting.take(2).map { ChargeCall("promoted", it, 0, owner) }, charges.calls)
    }

    @Test
    fun `admin tambem ajusta`() {
        assertIs<CapacityCommandResult.Success>(service.execute(admin, group, game, 3, 5))
    }

    @Test
    fun `fila menor que as vagas novas sobe inteira`() {
        repeat(4) { repository.record(UUID.randomUUID(), AttendanceStatus.CONFIRMED) }
        repository.record(waiting[0], AttendanceStatus.WAITLISTED, sequence = 1)

        val result = assertIs<CapacityCommandResult.Success>(service.execute(owner, group, game, 3, 10))

        assertEquals(listOf(waiting[0]), result.promoted.map { it.memberId })
    }

    @Test
    fun `manter as vagas nao promove ninguem`() {
        fill()

        assertTrue(assertIs<CapacityCommandResult.Success>(service.execute(owner, group, game, 3, 4)).promoted.isEmpty())
        assertEquals(List(4) { AttendanceStatus.WAITLISTED }, waiting.map { repository.status(it) })
        assertTrue(charges.calls.isEmpty())
    }

    @Test
    fun `em escolha manual aumentar as vagas nao sobe ninguem`() {
        fill()
        repository.promotionMode = PromotionMode.MANUAL

        val result = assertIs<CapacityCommandResult.Success>(service.execute(owner, group, game, 3, 8))

        assertTrue(result.promoted.isEmpty())
        assertEquals(8, repository.updatedCapacity)
    }

    @Test
    fun `limites de vagas sao 4 a 100`() {
        listOf(3, 0, -1, 101).forEach {
            assertEquals(CapacityCommandResult.InvalidCapacity, service.execute(owner, group, game, 3, it))
        }
        listOf(4, 100).forEach {
            assertIs<CapacityCommandResult.Success>(service.execute(owner, group, game, 3, it))
        }
    }

    @Test
    fun `papel, estado do jogo, versao e disputa de gravacao`() {
        assertEquals(CapacityCommandResult.Hidden, service.execute(UUID.randomUUID(), group, UUID.randomUUID(), 3, 8))
        assertEquals(CapacityCommandResult.Hidden, service.execute(UUID.randomUUID(), group, game, 3, 8))
        assertEquals(CapacityCommandResult.Forbidden, service.execute(athlete, group, game, 3, 8))
        assertEquals(CapacityCommandResult.Conflict, service.execute(owner, group, game, 2, 8))
        repository.rejectCapacityUpdate = true
        assertEquals(CapacityCommandResult.Conflict, service.execute(owner, group, game, 3, 8))
        repository.rejectCapacityUpdate = false
        repository.gameStatus = GameStatus.CANCELLED
        assertEquals(CapacityCommandResult.Frozen, service.execute(owner, group, game, 3, 8))
        assertEquals(null, repository.updatedCapacity)
        assertEquals(4, repository.capacity)
    }

    @Test
    fun `adjust do editor do jogo usa o mesmo caminho`() {
        val result = assertIs<CapacityCommandResult.Success>(service.adjust(owner, group, game, 3, 7))

        assertEquals(7, result.capacity)
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-10T10:00:00Z")
    }
}
