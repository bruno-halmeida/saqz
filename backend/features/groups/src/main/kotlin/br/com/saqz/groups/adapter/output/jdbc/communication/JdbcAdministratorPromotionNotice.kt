package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.application.communication.AdministratorPromotionNotice
import br.com.saqz.groups.application.communication.administratorPromotedBody
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.sql.DataSource

/**
 * Aviso individual da promoção a administrador (central e push), no canal AUTO_CONFIRMATION: os apps das lojas já o
 * conhecem, o push leva o corpo, segue a preferência de avisos e não vincula jogo. O dono do grupo assina. O
 * `request_id` sai do grupo, da pessoa e do minuto da promoção, então o UNIQUE de `group_messages` derruba o
 * pedido repetido; uma nova promoção, depois de um rebaixamento, avisa de novo.
 */
class JdbcAdministratorPromotionNotice(
    dataSource: DataSource,
    private val clock: Clock = Clock.systemUTC(),
) : AdministratorPromotionNotice {
    private val jdbc = JdbcClient.create(dataSource)

    override fun promoted(groupId: UUID, userId: UUID) = guarded {
        val message = UUID.randomUUID()
        val minute = clock.instant().truncatedTo(ChronoUnit.MINUTES)
        val inserted = jdbc.sql(
            """
            INSERT INTO group_messages (id, group_id, author_id, author_name, channel, request_id, body)
            SELECT :id, g.id, g.owner_user_id, u.display_name, CAST('AUTO_CONFIRMATION' AS group_message_channel), :request,
                   :body
            FROM access_groups g JOIN access_users u ON u.id = g.owner_user_id
            WHERE g.id = :group AND g.deleted_at IS NULL
            ON CONFLICT DO NOTHING
            """.trimIndent(),
        ).param("id", message).param("group", groupId)
            .param("request", UUID.nameUUIDFromBytes("admin-promoted:$groupId:$userId:$minute".toByteArray()))
            .param("body", groupName(groupId)?.let(::administratorPromotedBody) ?: return@guarded).update()
        if (inserted == 0) return@guarded
        jdbc.sql("INSERT INTO group_notifications (recipient_id, message_id) VALUES (:recipient, :id)")
            .param("recipient", userId).param("id", message).update()
        jdbc.sql(
            "UPDATE group_messages SET recipient_count = (SELECT count(*) FROM group_notifications WHERE message_id = :id AND in_app) WHERE id = :id",
        ).param("id", message).update()
    }

    private fun groupName(groupId: UUID): String? =
        jdbc.sql("SELECT name FROM access_groups WHERE id = :group AND deleted_at IS NULL").param("group", groupId)
            .query(String::class.java).optional().orElse(null)

    /** O aviso nunca desfaz a troca de papel: roda num SAVEPOINT e, se falhar, volta só até ele. */
    private fun guarded(block: () -> Unit) {
        jdbc.sql("SAVEPOINT administrator_promotion_notice").update()
        try {
            block()
            jdbc.sql("RELEASE SAVEPOINT administrator_promotion_notice").update()
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            jdbc.sql("ROLLBACK TO SAVEPOINT administrator_promotion_notice").update()
            log.warn("administrator promotion notice failed", failure)
        }
    }

    private companion object {
        val log: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(JdbcAdministratorPromotionNotice::class.java)
    }
}
