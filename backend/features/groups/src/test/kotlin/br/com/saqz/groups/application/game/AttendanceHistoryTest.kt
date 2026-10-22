package br.com.saqz.groups.application.game

import br.com.saqz.groups.application.game.AttendanceHistoryKind.CONFIRMED
import br.com.saqz.groups.application.game.AttendanceHistoryKind.DECLINED
import br.com.saqz.groups.application.game.AttendanceHistoryKind.GAVE_UP
import br.com.saqz.groups.application.game.AttendanceHistoryKind.GUEST_JOINED
import br.com.saqz.groups.application.game.AttendanceHistoryKind.GUEST_LEFT
import br.com.saqz.groups.application.game.AttendanceHistoryKind.LEFT_WAITLIST
import br.com.saqz.groups.application.game.AttendanceHistoryKind.ORGANIZER_ADDED
import br.com.saqz.groups.application.game.AttendanceHistoryKind.ORGANIZER_REMOVED
import br.com.saqz.groups.application.game.AttendanceHistoryKind.ORGANIZER_WAITLISTED
import br.com.saqz.groups.application.game.AttendanceHistoryKind.PROMOTED
import br.com.saqz.groups.application.game.AttendanceHistoryKind.SYSTEM_REMOVED
import br.com.saqz.groups.application.game.AttendanceHistoryKind.SYSTEM_WAITLISTED
import br.com.saqz.groups.application.game.AttendanceHistoryKind.WAITLISTED
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.attendance.AttendanceSource
import br.com.saqz.groups.domain.attendance.AttendanceSource.ORGANIZER
import br.com.saqz.groups.domain.attendance.AttendanceSource.SELF
import br.com.saqz.groups.domain.attendance.AttendanceSource.SYSTEM
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.attendance.AttendanceStatus.CONFIRMED as C
import br.com.saqz.groups.domain.attendance.AttendanceStatus.DECLINED as D
import br.com.saqz.groups.domain.attendance.AttendanceStatus.WAITLISTED as W
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** DEB-135 (JOGO-012): cada `source` × transição vira um tipo; só gestor lê; motivo só do organizador. */
class AttendanceHistoryTest {
    @Test
    fun `cada source e transicao vira o tipo certo`() {
        val cases = listOf(
            Triple(SELF, null, C) to CONFIRMED,
            Triple(SELF, D, C) to CONFIRMED,
            Triple(SELF, W, C) to CONFIRMED,
            Triple(SELF, null, W) to WAITLISTED,
            Triple(SELF, D, W) to WAITLISTED,
            Triple(SELF, null, D) to DECLINED,
            Triple(SELF, C, D) to GAVE_UP,
            Triple(SELF, W, D) to LEFT_WAITLIST,
            Triple(ORGANIZER, null, C) to ORGANIZER_ADDED,
            Triple(ORGANIZER, W, C) to ORGANIZER_ADDED,
            Triple(ORGANIZER, D, W) to ORGANIZER_WAITLISTED,
            Triple(ORGANIZER, C, D) to ORGANIZER_REMOVED,
            Triple(ORGANIZER, W, D) to ORGANIZER_REMOVED,
            Triple(SYSTEM, W, C) to PROMOTED,
            Triple(SYSTEM, C, D) to SYSTEM_REMOVED,
            Triple(SYSTEM, W, D) to SYSTEM_REMOVED,
            Triple(SYSTEM, C, W) to SYSTEM_WAITLISTED,
        )
        cases.forEach { (t, kind) -> assertEquals(kind, AttendanceHistoryKind.of(t.first, t.second, t.third, guest = false), "$t") }
    }

    @Test
    fun `convidado entra, sai e sobe da espera`() {
        assertEquals(GUEST_JOINED, AttendanceHistoryKind.of(SELF, null, C, guest = true))
        assertEquals(GUEST_JOINED, AttendanceHistoryKind.of(ORGANIZER, null, W, guest = true))
        assertEquals(GUEST_LEFT, AttendanceHistoryKind.of(SELF, C, D, guest = true))
        assertEquals(GUEST_LEFT, AttendanceHistoryKind.of(SYSTEM, W, D, guest = true))
        assertEquals(PROMOTED, AttendanceHistoryKind.of(SYSTEM, W, C, guest = true))
    }

    @Test
    fun `atleta recebe forbidden, estranho e jogo de outro grupo ficam ocultos`() {
        assertEquals(AttendanceHistoryResult.Forbidden, history(GroupRole.ATHLETE).list(actor, group, game, null))
        assertEquals(AttendanceHistoryResult.Hidden, history(null).list(actor, group, game, null))
        assertEquals(AttendanceHistoryResult.Hidden, history(GroupRole.OWNER, exists = false).list(actor, group, game, null))
        assertIs<AttendanceHistoryResult.Success>(history(GroupRole.ADMIN).list(actor, group, game, null))
    }

    @Test
    fun `motivo so do organizador e cursor so quando ha mais`() {
        val rows = listOf(row(ORGANIZER, C, D, "pediu para sair"), row(SYSTEM, C, D, "Conta excluída"), row(SELF, null, C, null))
        val first = assertIs<AttendanceHistoryResult.Success>(history(GroupRole.OWNER, rows = rows).list(actor, group, game, null, limit = 2))
        assertEquals(listOf("pediu para sair", null), first.entries.map { it.row.reason })
        assertEquals(AttendanceHistoryCursor(rows[1].occurredAt, rows[1].rank, rows[1].id), first.nextCursor)
        assertEquals(first.nextCursor, AttendanceHistoryCursor.decode(first.nextCursor!!.encode()))
        val all = assertIs<AttendanceHistoryResult.Success>(history(GroupRole.OWNER, rows = rows).list(actor, group, game, null, limit = 3))
        assertNull(all.nextCursor)
    }

    private val actor = UUID.randomUUID()
    private val group = UUID.randomUUID()
    private val game = UUID.randomUUID()

    private fun history(role: GroupRole?, exists: Boolean = true, rows: List<AttendanceHistoryRow> = emptyList()) =
        AttendanceHistory(object : AttendanceHistoryRepository {
            override fun role(actorId: UUID, groupId: UUID) = role
            override fun gameExists(groupId: UUID, gameId: UUID) = exists
            override fun page(groupId: UUID, gameId: UUID, after: AttendanceHistoryCursor?, limit: Int) = rows.take(limit)
        })

    private fun row(source: AttendanceSource, old: AttendanceStatus?, new: AttendanceStatus, reason: String?) = AttendanceHistoryRow(
        UUID.randomUUID(), Instant.parse("2026-10-22T20:00:00Z"), 1, UUID.randomUUID(), "Ana", 0, null, actor, "Bruno",
        source, old, new, reason, afterDeadline = false, freedByName = null, freedByGuest = false,
    )
}
