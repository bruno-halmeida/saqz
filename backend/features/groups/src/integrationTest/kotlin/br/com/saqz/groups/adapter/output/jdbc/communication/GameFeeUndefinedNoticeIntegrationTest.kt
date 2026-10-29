package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.adapter.output.jdbc.attendance.FilaHarness
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.communication.GameFeeUndefinedNotices
import br.com.saqz.groups.application.communication.MessageChannel
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID
import kotlin.test.assertEquals

/** DEB-146 (JOGO-008): aviso único a dono e admins dos grupos sem valor por jogo, no canal FIRST_MEMBER. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GameFeeUndefinedNoticeIntegrationTest {
    private lateinit var h: FilaHarness
    private lateinit var admin: UUID
    private lateinit var atleta: UUID
    private val communication get() = JdbcGroupCommunicationRepository(h.dataSource)
    private val notices get() = GameFeeUndefinedNotices(JdbcTransactionRunner(h.dataSource), JdbcGameFeeUndefinedNotices(h.dataSource, communication))

    @BeforeEach fun reset() {
        h = FilaHarness(this); h.cenario(capacity = 8)
        h.execute("UPDATE group_memberships SET role='ADMIN' WHERE group_id='${h.group}' AND user_id='${h.dono}'")
        admin = h.atleta(); h.execute("UPDATE group_memberships SET role='ADMIN' WHERE user_id='$admin'")
        atleta = h.atleta()
    }

    private fun avisos(user: UUID) = h.int(
        "SELECT count(*) FROM group_notifications n JOIN group_messages m ON m.id=n.message_id " +
            "WHERE n.recipient_id='$user' AND m.event_kind='GAME_FEE_UNDEFINED'",
    )

    @Test fun `manda uma vez a dono e admins, com titulo, corpo e eventKind, e rodar de novo nao repete`() {
        assertEquals(1, notices.run())
        assertEquals(1, avisos(h.dono)); assertEquals(1, avisos(admin)); assertEquals(0, avisos(atleta))
        assertEquals("FIRST_MEMBER", h.string("SELECT channel::text FROM group_messages WHERE event_kind='GAME_FEE_UNDEFINED'"))
        assertEquals("Grupo: defina o valor por jogo", h.string("SELECT push_title FROM group_messages WHERE event_kind='GAME_FEE_UNDEFINED'"))
        assertEquals("Hoje o avulso joga sem diária. Toque para definir.", h.string("SELECT body FROM group_messages WHERE event_kind='GAME_FEE_UNDEFINED'"))
        val central = communication.inbox(h.dono, null).single { it.message.eventKind == "GAME_FEE_UNDEFINED" }
        assertEquals(MessageChannel.FIRST_MEMBER, central.message.channel)

        assertEquals(0, notices.run())
        assertEquals(1, avisos(h.dono)); assertEquals(1, h.int("SELECT count(*) FROM group_messages WHERE event_kind='GAME_FEE_UNDEFINED'"))
    }

    @Test fun `o primeiro atleta ja avisado nao impede o aviso, e o aviso nao impede o primeiro atleta`() {
        communication.publishOnce(h.group, atleta, MessageChannel.FIRST_MEMBER, "Rafa entrou no grupo", null)
        assertEquals(1, notices.run())
        assertEquals(2, h.int("SELECT count(*) FROM group_messages WHERE channel='FIRST_MEMBER'"))
    }

    @Test fun `grupo com valor ou Sem diaria nao recebe`() {
        h.execute("UPDATE access_groups SET default_game_fee_cents=2000 WHERE id='${h.group}'")
        assertEquals(0, notices.run())
        h.execute("UPDATE access_groups SET default_game_fee_cents=NULL, default_no_game_fee=true WHERE id='${h.group}'")
        assertEquals(0, notices.run())
        assertEquals(0, avisos(h.dono))
    }
}
