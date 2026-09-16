package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.application.communication.GroupCommunicationRepository
import br.com.saqz.groups.application.communication.GroupMessage
import br.com.saqz.groups.application.communication.GroupNotification
import br.com.saqz.groups.application.communication.MessageChannel
import br.com.saqz.groups.application.communication.NotificationPreferences
import br.com.saqz.groups.application.communication.PushPreferences
import br.com.saqz.groups.application.communication.ReminderCandidate
import br.com.saqz.groups.application.communication.ReminderRoster
import br.com.saqz.groups.application.communication.WhatsAppPreferences
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.sql.Types
import java.util.UUID
import javax.sql.DataSource

class JdbcGroupCommunicationRepository(dataSource: DataSource) : GroupCommunicationRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun lockGroup(groupId: UUID) = jdbc.sql(
        "SELECT id FROM access_groups WHERE id = :group AND deleted_at IS NULL FOR UPDATE",
    ).param("group", groupId).query(UUID::class.java).optional().isPresent

    override fun messages(groupId: UUID, channel: MessageChannel, before: Long?) = jdbc.sql(
        "SELECT m.* FROM group_messages m WHERE group_id = :group AND channel = :channel " +
            "AND (:before IS NULL OR sequence < :before) ORDER BY sequence DESC LIMIT 51",
    ).param("group", groupId).param("channel", channel.name, Types.OTHER)
        .param("before", before, Types.BIGINT).query { rs, _ -> message(rs) }.list()

    override fun findRequest(groupId: UUID, actor: UUID, channel: MessageChannel, requestId: UUID) = jdbc.sql(
        "SELECT m.* FROM group_messages m WHERE group_id = :group AND author_id = :actor AND channel = :channel AND request_id = :request",
    ).param("group", groupId).param("actor", actor).param("channel", channel.name, Types.OTHER)
        .param("request", requestId).query { rs, _ -> message(rs) }.optional().orElse(null)

    override fun publish(groupId: UUID, actor: UUID, channel: MessageChannel, requestId: UUID, body: String, gameId: UUID?): GroupMessage {
        val id = UUID.randomUUID()
        jdbc.sql(
            """
            INSERT INTO group_messages (id, group_id, author_id, author_name, channel, request_id, body, game_id)
            SELECT :id, :group, :actor, display_name, :channel, :request, :body, :game FROM access_users WHERE id = :actor
            """.trimIndent(),
        ).param("id", id).param("group", groupId).param("actor", actor).param("channel", channel.name, Types.OTHER)
            .param("request", requestId).param("body", body).param("game", gameId, Types.OTHER).update()
        if (channel == MessageChannel.REMINDER) {
            jdbc.sql("INSERT INTO notification_attendance_links(message_id) VALUES (:id)").param("id", id).update()
        }
        jdbc.sql(
            """
            INSERT INTO group_notifications (recipient_id, message_id)
            SELECT membership.user_id, :message
            FROM group_memberships membership
            LEFT JOIN group_notification_preferences pref ON pref.user_id = membership.user_id
            WHERE membership.group_id = :group AND membership.active AND membership.user_id <> :actor
              AND (CAST(:game AS uuid) IS NULL OR NOT EXISTS (
                  SELECT 1 FROM game_attendance a
                  WHERE a.group_id = :group AND a.game_id = :game AND a.member_user_id = membership.user_id
              ))
            """.trimIndent(),
        ).param("message", id).param("group", groupId).param("actor", actor).param("channel", channel.name)
            .param("game", gameId, Types.OTHER).update()
        val recipients = jdbc.sql("SELECT count(*) FROM group_notifications WHERE message_id = :id AND in_app")
            .param("id", id).query(Int::class.java).single()
        jdbc.sql("UPDATE group_messages SET recipient_count = :count WHERE id = :id")
            .param("count", recipients).param("id", id).update()
        return checkNotNull(findRequest(groupId, actor, channel, requestId))
    }

    override fun reminderTitle(groupId: UUID, gameId: UUID): String? = jdbc.sql(
        "SELECT title FROM games WHERE group_id = :group AND id = :game AND status = 'PUBLISHED' " +
            "AND starts_at > now() AND confirmation_deadline > now()",
    ).param("group", groupId).param("game", gameId).query(String::class.java).optional().orElse(null)

    override fun reminderRoster(groupId: UUID, gameId: UUID): ReminderRoster {
        val rows = jdbc.sql(
            """
            SELECT CASE attendance.status
                       WHEN 'CONFIRMED' THEN 'CONFIRMED'
                       WHEN 'WAITLISTED' THEN 'WAITLISTED'
                       ELSE 'DECLINED' END AS bucket,
                   attendance.member_display_name AS name
            FROM game_attendance attendance
            JOIN group_memberships membership
                ON membership.group_id = attendance.group_id
                AND membership.user_id = attendance.member_user_id AND membership.active
            WHERE attendance.game_id = :game AND attendance.group_id = :group
            ORDER BY lower(attendance.member_display_name), attendance.member_display_name
            """.trimIndent(),
        ).param("group", groupId).param("game", gameId)
            .query { rs, _ -> rs.getString("bucket") to rs.getString("name") }.list()
        return ReminderRoster(
            confirmed = rows.filter { it.first == "CONFIRMED" }.map { it.second },
            waitlisted = rows.filter { it.first == "WAITLISTED" }.map { it.second },
            declined = rows.filter { it.first == "DECLINED" }.map { it.second },
        )
    }

    override fun reminderCandidates(): List<ReminderCandidate> = jdbc.sql(
        """
        SELECT games.id AS game_id, games.group_id, games.title, groups.owner_user_id
        FROM games
        JOIN access_groups groups ON groups.id = games.group_id AND groups.deleted_at IS NULL
        WHERE games.status = 'PUBLISHED' AND games.starts_at > now() AND games.confirmation_deadline > now()
        ORDER BY games.starts_at, games.id
        """.trimIndent(),
    ).query { rs, _ -> ReminderCandidate(
        gameId = rs.getObject("game_id", UUID::class.java),
        groupId = rs.getObject("group_id", UUID::class.java),
        ownerId = rs.getObject("owner_user_id", UUID::class.java),
        title = rs.getString("title"),
    ) }.list()

    override fun inbox(actor: UUID, before: Long?) = jdbc.sql(
        """
        SELECT m.*, n.sequence AS notification_sequence, n.read_at
        FROM group_notifications n JOIN group_messages m ON m.id = n.message_id
        JOIN access_groups g ON g.id = m.group_id AND g.deleted_at IS NULL
        JOIN group_memberships membership ON membership.group_id = m.group_id AND membership.user_id = n.recipient_id
        WHERE n.recipient_id = :actor AND n.in_app AND (:before IS NULL OR n.sequence < :before)
        ORDER BY n.sequence DESC LIMIT 51
        """.trimIndent(),
    ).param("actor", actor).param("before", before, Types.BIGINT)
        .query { rs, _ -> GroupNotification(rs.getLong("notification_sequence"), message(rs), rs.getTimestamp("read_at") != null) }.list()

    override fun markRead(actor: UUID, sequence: Long) {
        jdbc.sql("UPDATE group_notifications SET read_at = coalesce(read_at, now()) WHERE recipient_id = :actor AND sequence = :id")
            .param("actor", actor).param("id", sequence).update()
    }

    override fun preferences(actor: UUID): NotificationPreferences = jdbc.sql(
        "SELECT * FROM group_notification_preferences WHERE user_id = :actor",
    ).param("actor", actor).query { rs, _ -> preferences(rs) }.optional().orElse(NotificationPreferences())

    override fun savePreferences(actor: UUID, preferences: NotificationPreferences): NotificationPreferences = jdbc.sql(
        """
        INSERT INTO group_notification_preferences
            (user_id, notices, messages, reminders, push_notices, push_messages, push_reminders, push_charges,
             whatsapp_notices, whatsapp_reminders, whatsapp_charges)
        VALUES (:actor, :notices, :messages, :reminders, :pn, :pm, :pr, :pc, :wn, :wr, :wc)
        ON CONFLICT (user_id) DO UPDATE SET notices = excluded.notices, messages = excluded.messages, reminders = excluded.reminders,
            push_notices = excluded.push_notices, push_messages = excluded.push_messages,
            push_reminders = excluded.push_reminders, push_charges = excluded.push_charges,
            whatsapp_notices = excluded.whatsapp_notices, whatsapp_reminders = excluded.whatsapp_reminders,
            whatsapp_charges = excluded.whatsapp_charges
        RETURNING *
        """.trimIndent(),
    ).param("actor", actor).param("notices", preferences.notices).param("messages", preferences.messages)
        .param("reminders", preferences.reminders).param("pn", preferences.push.notices).param("pm", preferences.push.messages)
        .param("pr", preferences.push.reminders).param("pc", preferences.push.charges)
        .param("wn", preferences.whatsapp.notices).param("wr", preferences.whatsapp.reminders).param("wc", preferences.whatsapp.charges)
        .query { rs, _ -> preferences(rs) }.single()

    private fun preferences(rs: ResultSet) = NotificationPreferences(
        rs.getBoolean("notices"), rs.getBoolean("messages"), rs.getBoolean("reminders"),
        PushPreferences(rs.getBoolean("push_notices"), rs.getBoolean("push_messages"), rs.getBoolean("push_reminders"), rs.getBoolean("push_charges")),
        WhatsAppPreferences(rs.getBoolean("whatsapp_notices"), rs.getBoolean("whatsapp_reminders"), rs.getBoolean("whatsapp_charges")),
    )

    private fun message(rs: ResultSet) = GroupMessage(
        id = rs.getObject("id", UUID::class.java), sequence = rs.getLong("sequence"), groupId = rs.getObject("group_id", UUID::class.java),
        authorId = rs.getObject("author_id", UUID::class.java), authorName = rs.getString("author_name"),
        channel = MessageChannel.valueOf(rs.getString("channel")), body = rs.getString("body"),
        gameId = rs.getObject("game_id", UUID::class.java), recipientCount = rs.getInt("recipient_count"), createdAt = rs.getTimestamp("created_at").toInstant(),
    )
}
