package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.application.communication.gameScheduleChangedBody
import br.com.saqz.groups.application.game.GameScheduleChangeNotice
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameSnapshot
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import javax.sql.DataSource

/**
 * Aviso de mudança de dia, hora ou local do jogo publicado (DEB-046), na central e no push, no canal AUTO_CONFIRMATION
 * (os apps das lojas já o conhecem, o push leva o corpo, segue a preferência de avisos e não entra na fila do grupo de
 * WhatsApp); sem jogo vinculado, então o texto leva o dia anterior, o novo dia, a hora e o local. Chega a todo membro
 * ativo, menos a quem editou e a quem respondeu "Não vou". Um aviso por versão salva: o `request_id` sai do jogo e da
 * versão, e o UNIQUE derruba a repetição. Roda num SAVEPOINT na transação do salvamento: se falhar, o jogo salva igual.
 */
class JdbcGameScheduleChangeNotice(dataSource: DataSource) : GameScheduleChangeNotice {
    private val jdbc = JdbcClient.create(dataSource)

    override fun changed(actor: UUID, before: GameSnapshot, after: Game) {
        jdbc.sql("SAVEPOINT game_schedule_change_notice").update()
        try {
            publish(actor, before, after)
            jdbc.sql("RELEASE SAVEPOINT game_schedule_change_notice").update()
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            jdbc.sql("ROLLBACK TO SAVEPOINT game_schedule_change_notice").update()
            log.warn("game schedule change notice failed", failure)
        }
    }

    private fun publish(actor: UUID, before: GameSnapshot, after: Game) {
        val snapshot = after.snapshot
        val body = gameScheduleChangedBody(
            before.localDate, snapshot.localDate, snapshot.localTime, snapshot.venue.name, snapshot.venue.court,
        )
        val message = UUID.randomUUID()
        val inserted = jdbc.sql(
            """
            INSERT INTO group_messages (id, group_id, author_id, author_name, channel, request_id, body)
            SELECT :id, :group, :actor, display_name, CAST('AUTO_CONFIRMATION' AS group_message_channel), :request, :body
            FROM access_users WHERE id = :actor
            ON CONFLICT DO NOTHING
            """.trimIndent(),
        ).param("id", message).param("group", after.groupId).param("actor", actor)
            .param("request", UUID.nameUUIDFromBytes("game-schedule-changed:${after.id}:${after.version}".toByteArray()))
            .param("body", body).update()
        if (inserted == 0) return
        jdbc.sql(
            """
            INSERT INTO group_notifications (recipient_id, message_id)
            SELECT membership.user_id, :message
            FROM group_memberships membership
            WHERE membership.group_id = :group AND membership.active AND membership.user_id <> :actor
              AND NOT EXISTS (
                  SELECT 1 FROM game_attendance a
                  WHERE a.group_id = :group AND a.game_id = :game AND a.member_user_id = membership.user_id
                    AND a.guest_seq = 0 AND a.status = 'DECLINED')
            """.trimIndent(),
        ).param("message", message).param("group", after.groupId).param("actor", actor).param("game", after.id).update()
        jdbc.sql(
            "UPDATE group_messages SET recipient_count = (SELECT count(*) FROM group_notifications WHERE message_id = :id AND in_app) WHERE id = :id",
        ).param("id", message).update()
    }

    private companion object {
        val log: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(JdbcGameScheduleChangeNotice::class.java)
    }
}
