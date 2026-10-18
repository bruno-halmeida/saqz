package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.sharedkernel.subscription.TrialEndingNotice
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import javax.sql.DataSource

/**
 * Aviso de fim do teste na central e no push do dono. Mensagens do grupo são sempre de um grupo, então o aviso
 * mora no grupo ativo mais antigo de que a pessoa é dona; só o dono recebe (não os admins), pela preferência de avisos.
 */
class JdbcTrialEndingNotice(dataSource: DataSource) : TrialEndingNotice {
    private val jdbc = JdbcClient.create(dataSource)

    override fun publish(ownerId: UUID, body: String, pushBody: String): Boolean {
        val group = jdbc.sql(
            """
            SELECT g.id FROM access_groups g
            JOIN group_memberships gm ON gm.group_id = g.id AND gm.user_id = :owner AND gm.active
            WHERE g.owner_user_id = :owner AND g.deleted_at IS NULL
            ORDER BY g.created_at, g.id LIMIT 1
            """.trimIndent(),
        ).param("owner", ownerId).query(UUID::class.java).optional().orElse(null) ?: return false
        val id = UUID.randomUUID()
        val inserted = jdbc.sql(
            """
            INSERT INTO group_messages (id, group_id, author_id, author_name, channel, request_id, body, push_body)
            SELECT :id, :group, :owner, display_name, CAST('TRIAL_ENDING' AS group_message_channel), :request, :body, :pushBody
            FROM access_users WHERE id = :owner
            """.trimIndent(),
        ).param("id", id).param("group", group).param("owner", ownerId).param("request", UUID.randomUUID())
            .param("body", body).param("pushBody", pushBody).update()
        if (inserted == 0) return false
        jdbc.sql("INSERT INTO group_notifications (recipient_id, message_id) VALUES (:owner, :id)")
            .param("owner", ownerId).param("id", id).update()
        val recipients = jdbc.sql("SELECT count(*) FROM group_notifications WHERE message_id = :id AND in_app")
            .param("id", id).query(Int::class.java).single()
        jdbc.sql("UPDATE group_messages SET recipient_count = :count WHERE id = :id")
            .param("count", recipients).param("id", id).update()
        return true
    }
}
