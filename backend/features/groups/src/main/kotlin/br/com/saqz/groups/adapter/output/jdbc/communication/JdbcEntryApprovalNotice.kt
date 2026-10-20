package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.application.communication.EntryApprovalNotice
import br.com.saqz.groups.application.communication.entryApprovedBody
import br.com.saqz.groups.domain.AthleteMembershipType
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

/**
 * Aviso individual da aprovação do pedido (central e push), no canal AUTO_CONFIRMATION: os apps das lojas já o
 * conhecem, o push leva o corpo, segue a preferência de avisos e não vincula jogo. O dono do grupo assina. O
 * `request_id` sai do grupo, da pessoa e do instante do pedido, então o UNIQUE de `group_messages` derruba a repetição.
 * A pessoa já é membro quando isto roda (a view de entrega exige o vínculo ativo).
 */
class JdbcEntryApprovalNotice(dataSource: DataSource) : EntryApprovalNotice {
    private val jdbc = JdbcClient.create(dataSource)

    override fun approved(groupId: UUID, userId: UUID, requestedAt: Instant, membershipType: AthleteMembershipType) = guarded {
        val message = UUID.randomUUID()
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
            .param("request", UUID.nameUUIDFromBytes("entry-approved:$groupId:$userId:$requestedAt".toByteArray()))
            .param("body", groupName(groupId)?.let { entryApprovedBody(it, membershipType) } ?: return@guarded).update()
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

    /** O aviso nunca desfaz a aprovação: roda num SAVEPOINT e, se falhar, volta só até ele. */
    private fun guarded(block: () -> Unit) {
        jdbc.sql("SAVEPOINT entry_approval_notice").update()
        try {
            block()
            jdbc.sql("RELEASE SAVEPOINT entry_approval_notice").update()
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            jdbc.sql("ROLLBACK TO SAVEPOINT entry_approval_notice").update()
            log.warn("entry approval notice failed", failure)
        }
    }

    private companion object {
        val log: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(JdbcEntryApprovalNotice::class.java)
    }
}
