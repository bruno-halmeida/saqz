package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.game.AttendanceHistory
import br.com.saqz.groups.application.game.AttendanceHistoryCursor
import br.com.saqz.groups.application.game.AttendanceHistoryRepository
import br.com.saqz.groups.application.game.AttendanceHistoryRow
import br.com.saqz.groups.application.photo.MemberPhotoDirectory
import br.com.saqz.groups.application.photo.ReadMemberPhotoResult
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.attendance.AttendanceSource
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.sharedkernel.RequestIdentity
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** DEB-135 (JOGO-012): gestor lê, atleta 403, quem não é do grupo 404; foto só do membro, não do convidado. */
class AttendanceHistoryControllerTest {
    private val group = UUID.randomUUID(); private val game = UUID.randomUUID(); private val ana = UUID.randomUUID()
    private var role: GroupRole? = GroupRole.OWNER

    private val rows = listOf(
        row(guestSeq = 0, source = AttendanceSource.SELF, old = null, new = AttendanceStatus.CONFIRMED),
        row(guestSeq = 1, source = AttendanceSource.SELF, old = null, new = AttendanceStatus.CONFIRMED),
    )
    private val controller = AttendanceHistoryController(
        VerifiedGroupActorResolver { UUID.randomUUID() },
        AttendanceHistory(object : AttendanceHistoryRepository {
            override fun role(actorId: UUID, groupId: UUID) = role
            override fun gameExists(groupId: UUID, gameId: UUID) = true
            override fun page(groupId: UUID, gameId: UUID, after: AttendanceHistoryCursor?, limit: Int) = rows.take(limit)
        }),
        object : MemberPhotoDirectory {
            override fun tags(viewerId: UUID, groupId: UUID, userIds: Collection<UUID>) = mapOf(ana to "v1")
            override fun read(viewerId: UUID, groupId: UUID, userId: UUID) = ReadMemberPhotoResult.NotFound
        },
    )

    @Test fun `gestor recebe tipos e foto so do membro`() {
        val page = controller.list(ID, "$group", "$game", null, null)
        assertEquals(listOf("CONFIRMED", "GUEST_JOINED"), page.entries.map { it.kind })
        assertEquals(listOf("v1", null), page.entries.map { it.photoTag })
        assertNull(page.nextCursor)
    }

    @Test fun `atleta 403 e estranho 404`() {
        role = GroupRole.ATHLETE
        assertFailsWith<AccessForbiddenException> { controller.list(ID, "$group", "$game", null, null) }
        role = null
        assertFailsWith<GameNotFoundException> { controller.list(ID, "$group", "$game", null, null) }
        assertFailsWith<GameNotFoundException> { controller.list(ID, "$group", "nao-e-uuid", null, null) }
    }

    @Test fun `cursor invalido e pedido invalido`() {
        assertFailsWith<InvalidGroupRequestException> { controller.list(ID, "$group", "$game", "lixo", null) }
    }

    private fun row(guestSeq: Int, source: AttendanceSource, old: AttendanceStatus?, new: AttendanceStatus) = AttendanceHistoryRow(
        UUID.randomUUID(), Instant.parse("2026-10-22T20:00:00Z"), 1, ana, "Ana", guestSeq, if (guestSeq > 0) "Duda" else null,
        ana, "Ana", source, old, new, null, afterDeadline = false, freedByName = null, freedByGuest = false,
    )

    private companion object { val ID = RequestIdentity(subject = "subject", emailVerified = true, displayName = "Player") }
}
