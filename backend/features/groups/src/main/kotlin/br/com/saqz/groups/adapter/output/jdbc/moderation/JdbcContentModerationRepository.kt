package br.com.saqz.groups.adapter.output.jdbc.moderation

import br.com.saqz.groups.application.moderation.BlockedUser
import br.com.saqz.groups.application.moderation.ContentModerationRepository
import br.com.saqz.groups.application.moderation.ContentReportAlert
import br.com.saqz.groups.application.moderation.ContentReportSummary
import br.com.saqz.groups.application.moderation.NewContentReport
import br.com.saqz.groups.application.moderation.ReportReason
import br.com.saqz.groups.application.moderation.ReportTarget
import br.com.saqz.groups.application.moderation.ReportedContent
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.util.UUID
import javax.sql.DataSource

class JdbcContentModerationRepository(dataSource: DataSource) : ContentModerationRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun isActiveMember(groupId: UUID, userId: UUID) = jdbc.sql(
        """
        SELECT EXISTS (
            SELECT 1 FROM group_memberships membership
            JOIN access_groups g ON g.id = membership.group_id AND g.deleted_at IS NULL
            WHERE membership.group_id = :group AND membership.user_id = :user AND membership.active)
        """.trimIndent(),
    ).param("group", groupId).param("user", userId).query(Boolean::class.java).single()

    override fun reportedContent(groupId: UUID, target: ReportTarget, targetId: UUID): ReportedContent? {
        val sql = when (target) {
            ReportTarget.GROUP ->
                "SELECT owner_user_id AS responsible, name AS excerpt FROM access_groups " +
                    "WHERE id = :group AND id = :target AND deleted_at IS NULL"
            ReportTarget.USER ->
                "SELECT u.id AS responsible, u.display_name AS excerpt FROM group_memberships membership " +
                    "JOIN access_users u ON u.id = membership.user_id " +
                    "WHERE membership.group_id = :group AND membership.user_id = :target AND membership.active"
            ReportTarget.MESSAGE ->
                "SELECT author_id AS responsible, body AS excerpt FROM group_messages " +
                    "WHERE group_id = :group AND id = :target AND channel IN ('NOTICE', 'CHAT')"
        }
        return jdbc.sql(sql).param("group", groupId).param("target", targetId)
            .query { rs, _ -> ReportedContent(rs.getObject("responsible", UUID::class.java), rs.getString("excerpt")) }
            .optional().orElse(null)
    }

    override fun saveReport(report: NewContentReport) {
        jdbc.sql(
            """
            INSERT INTO content_reports
                (id, reporter_id, group_id, target_type, target_id, target_user_id, excerpt, reason, details)
            VALUES (:id, :reporter, :group, :type, :target, :responsible, :excerpt, :reason, :details)
            """.trimIndent(),
        ).param("id", report.id).param("reporter", report.reporterId).param("group", report.groupId)
            .param("type", report.target.name).param("target", report.targetId)
            .param("responsible", report.content.responsibleUserId)
            .param("excerpt", report.content.excerpt?.take(MAX_EXCERPT))
            .param("reason", report.reason.name).param("details", report.details).update()
    }

    override fun block(blockerId: UUID, blockedId: UUID) = jdbc.sql(
        "INSERT INTO user_blocks (blocker_id, blocked_id) VALUES (:blocker, :blocked) ON CONFLICT DO NOTHING",
    ).param("blocker", blockerId).param("blocked", blockedId).update() == 1

    override fun unblock(blockerId: UUID, blockedId: UUID) {
        jdbc.sql("DELETE FROM user_blocks WHERE blocker_id = :blocker AND blocked_id = :blocked")
            .param("blocker", blockerId).param("blocked", blockedId).update()
    }

    override fun blocks(blockerId: UUID): List<BlockedUser> = jdbc.sql(
        """
        SELECT b.blocked_id, u.display_name, b.created_at FROM user_blocks b
        JOIN access_users u ON u.id = b.blocked_id
        WHERE b.blocker_id = :blocker ORDER BY b.created_at DESC
        """.trimIndent(),
    ).param("blocker", blockerId).query { rs, _ -> BlockedUser(
        rs.getObject("blocked_id", UUID::class.java), rs.getString("display_name"), rs.getTimestamp("created_at").toInstant(),
    ) }.list()

    /** Depois de [MAX_ALERT_ATTEMPTS] falhas a denúncia sai da fila de e-mail, mas continua no painel. */
    override fun pendingAlerts(limit: Int): List<ContentReportAlert> = jdbc.sql(
        """
        SELECT r.*, g.name AS group_name, reporter.display_name AS reporter_name,
               responsible.display_name AS responsible_name
        FROM content_reports r
        JOIN access_groups g ON g.id = r.group_id
        JOIN access_users reporter ON reporter.id = r.reporter_id
        LEFT JOIN access_users responsible ON responsible.id = r.target_user_id
        WHERE r.alerted_at IS NULL AND r.alert_attempts < :maxAttempts
        ORDER BY r.created_at, r.id
        LIMIT :limit
        """.trimIndent(),
    ).param("maxAttempts", MAX_ALERT_ATTEMPTS).param("limit", limit).query { rs, _ -> ContentReportAlert(
        id = rs.getObject("id", UUID::class.java),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        reason = ReportReason.valueOf(rs.getString("reason")),
        target = ReportTarget.valueOf(rs.getString("target_type")),
        targetId = rs.getObject("target_id", UUID::class.java),
        groupId = rs.getObject("group_id", UUID::class.java),
        groupName = rs.getString("group_name"),
        reporterId = rs.getObject("reporter_id", UUID::class.java),
        reporterName = rs.getString("reporter_name"),
        responsibleUserId = rs.getObject("target_user_id", UUID::class.java),
        responsibleName = rs.getString("responsible_name"),
        excerpt = rs.getString("excerpt"),
        details = rs.getString("details"),
    ) }.list()

    override fun markAlerted(reportId: UUID) {
        jdbc.sql("UPDATE content_reports SET alerted_at = now(), alert_attempts = alert_attempts + 1 WHERE id = :id")
            .param("id", reportId).update()
    }

    override fun markAlertFailed(reportId: UUID) {
        jdbc.sql("UPDATE content_reports SET alert_attempts = alert_attempts + 1 WHERE id = :id")
            .param("id", reportId).update()
    }

    override fun reports(openOnly: Boolean, limit: Int): List<ContentReportSummary> = jdbc.sql(
        """
        SELECT r.*, g.name AS group_name, responsible.display_name AS responsible_name
        FROM content_reports r
        JOIN access_groups g ON g.id = r.group_id
        LEFT JOIN access_users responsible ON responsible.id = r.target_user_id
        WHERE (NOT :openOnly OR r.resolved_at IS NULL)
        ORDER BY r.created_at DESC, r.id
        LIMIT :limit
        """.trimIndent(),
    ).param("openOnly", openOnly).param("limit", limit).query { rs, _ -> summary(rs) }.list()

    override fun resolve(reportId: UUID) = jdbc.sql(
        "UPDATE content_reports SET resolved_at = coalesce(resolved_at, now()) WHERE id = :id",
    ).param("id", reportId).update() == 1

    /** Mesma ordem da exclusão de conta: filas e entregas antes das notificações, e elas antes da mensagem. */
    override fun deleteMessage(groupId: UUID, messageId: UUID): Boolean {
        val exists = jdbc.sql("SELECT EXISTS (SELECT 1 FROM group_messages WHERE id = :message AND group_id = :group)")
            .param("message", messageId).param("group", groupId).query(Boolean::class.java).single()
        if (!exists) return false
        val notifications = "SELECT sequence FROM group_notifications WHERE message_id = :message"
        fun execute(sql: String) { jdbc.sql(sql).param("message", messageId).update() }
        listOf("notification_push_deliveries", "notification_push_queue", "notification_whatsapp_queue").forEach {
            execute("DELETE FROM $it WHERE notification_id IN ($notifications)")
        }
        execute("DELETE FROM group_notifications WHERE message_id = :message")
        listOf("notification_attendance_links", "notification_whatsapp_group_queue").forEach {
            execute("DELETE FROM $it WHERE message_id = :message")
        }
        execute("DELETE FROM group_messages WHERE id = :message")
        return true
    }

    private fun summary(rs: ResultSet) = ContentReportSummary(
        id = rs.getObject("id", UUID::class.java),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        reason = ReportReason.valueOf(rs.getString("reason")),
        target = ReportTarget.valueOf(rs.getString("target_type")),
        targetId = rs.getObject("target_id", UUID::class.java),
        groupId = rs.getObject("group_id", UUID::class.java),
        groupName = rs.getString("group_name"),
        reporterId = rs.getObject("reporter_id", UUID::class.java),
        responsibleUserId = rs.getObject("target_user_id", UUID::class.java),
        responsibleName = rs.getString("responsible_name"),
        excerpt = rs.getString("excerpt"),
        details = rs.getString("details"),
        resolvedAt = rs.getTimestamp("resolved_at")?.toInstant(),
    )

    private companion object {
        const val MAX_EXCERPT = 2000
        const val MAX_ALERT_ATTEMPTS = 20
    }
}
