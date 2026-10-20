package br.com.saqz.groups.adapter.output.jdbc.monthlyinvite

import br.com.saqz.groups.application.monthlyinvite.MonthlyInviteLink
import br.com.saqz.groups.application.monthlyinvite.MonthlyInviteQueueStore
import br.com.saqz.groups.application.monthlyinvite.MonthlyInviteSendContext
import br.com.saqz.groups.application.monthlyinvite.MonthlyInviteeStatus
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

/** Cada chamada é uma transação curta própria (autocommit): a fila não segura conexão durante a espera. */
class JdbcMonthlyInviteQueueStore(dataSource: DataSource) : MonthlyInviteQueueStore {
    private val jdbc = JdbcClient.create(dataSource)

    override fun context(inviteeId: UUID): MonthlyInviteSendContext? = jdbc.sql(
        """
        SELECT invitees.id, invitees.name, invitees.phone, invitees.status, groups.id AS group_id, groups.name AS group_name,
               COALESCE(creator.display_name, owner.display_name) AS sender_name,
               links.short_code, links.expires_at, links.invalidated_at
        FROM group_monthly_invitees invitees
        JOIN access_groups groups ON groups.id = invitees.group_id AND groups.deleted_at IS NULL
        JOIN access_users owner ON owner.id = groups.owner_user_id
        LEFT JOIN access_users creator ON creator.id = invitees.created_by_user_id
        LEFT JOIN group_monthly_invite_links links ON links.group_id = groups.id
        WHERE invitees.id = :id
        """.trimIndent(),
    )
        .param("id", inviteeId)
        .query { result, _ ->
            val groupId = result.getObject("group_id", UUID::class.java)
            MonthlyInviteSendContext(
                inviteeId = inviteeId,
                name = result.getString("name"),
                phone = result.getString("phone"),
                status = MonthlyInviteeStatus.valueOf(result.getString("status")),
                groupName = result.getString("group_name"),
                senderName = result.getString("sender_name"),
                link = result.getTimestamp("expires_at")?.let {
                    MonthlyInviteLink(
                        groupId,
                        result.getString("short_code"),
                        it.toInstant(),
                        result.getTimestamp("invalidated_at")?.toInstant(),
                    )
                },
            )
        }
        .optional()
        .orElse(null)

    override fun markSent(inviteeId: UUID, attempts: Int, sentAt: Instant) {
        jdbc.sql(
            """
            UPDATE group_monthly_invitees SET status = 'WAITING', attempts = :attempts, sent_at = :sentAt, next_attempt_at = NULL
            WHERE id = :id
            """.trimIndent(),
        )
            .param("id", inviteeId)
            .param("attempts", attempts)
            .param("sentAt", Timestamp.from(sentAt))
            .update()
    }

    override fun markFailed(inviteeId: UUID, attempts: Int) {
        jdbc.sql("UPDATE group_monthly_invitees SET status = 'FAILED', attempts = :attempts, next_attempt_at = NULL WHERE id = :id")
            .param("id", inviteeId)
            .param("attempts", attempts)
            .update()
    }

    override fun markNoWhatsApp(inviteeId: UUID) {
        jdbc.sql("UPDATE group_monthly_invitees SET status = 'NO_WHATSAPP', next_attempt_at = NULL WHERE id = :id")
            .param("id", inviteeId)
            .update()
    }

    override fun markPending(inviteeIds: List<UUID>, status: MonthlyInviteeStatus) {
        if (inviteeIds.isEmpty()) return
        jdbc.sql(
            """
            UPDATE group_monthly_invitees SET status = :status
            WHERE id IN (:ids) AND status IN ('QUEUED', 'PAUSED')
            """.trimIndent(),
        )
            .param("ids", inviteeIds)
            .param("status", status.name)
            .update()
    }

    override fun pendingAtStartup(): List<UUID> = jdbc.sql(
        """
        SELECT invitees.id
        FROM group_monthly_invitees invitees
        JOIN access_groups groups ON groups.id = invitees.group_id AND groups.deleted_at IS NULL
        WHERE invitees.status IN ('QUEUED', 'PAUSED')
        ORDER BY invitees.created_at, invitees.id
        """.trimIndent(),
    )
        .query { result, _ -> result.getObject("id", UUID::class.java) }
        .list()
}
