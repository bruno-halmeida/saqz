package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.application.communication.GroupCommunicationRepository
import br.com.saqz.groups.application.communication.GroupMessage
import br.com.saqz.groups.application.communication.GroupNotification
import br.com.saqz.groups.application.communication.MessageChannel
import br.com.saqz.groups.application.communication.NotificationPreferences
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
        val recipients = jdbc.sql(
            """
            INSERT INTO group_notifications (recipient_id, message_id)
            SELECT membership.user_id, :message
            FROM group_memberships membership
            LEFT JOIN group_notification_preferences pref ON pref.user_id = membership.user_id
            WHERE membership.group_id = :group AND membership.active AND membership.user_id <> :actor
              AND CASE :channel
                  WHEN 'CHAT' THEN coalesce(pref.messages, true)
                  WHEN 'NOTICE' THEN coalesce(pref.notices, true)
                  ELSE coalesce(pref.reminders, true) END
              AND (CAST(:game AS uuid) IS NULL OR NOT EXISTS (
                  SELECT 1 FROM game_attendance a
                  WHERE a.group_id = :group AND a.game_id = :game AND a.member_user_id = membership.user_id
              ))
            """.trimIndent(),
        ).param("message", id).param("group", groupId).param("actor", actor).param("channel", channel.name)
            .param("game", gameId, Types.OTHER).update()
        jdbc.sql("UPDATE group_messages SET recipient_count = :count WHERE id = :id")
            .param("count", recipients).param("id", id).update()
        return checkNotNull(findRequest(groupId, actor, channel, requestId))
    }

    override fun reminderTitle(groupId: UUID, gameId: UUID): String? = jdbc.sql(
        "SELECT title FROM games WHERE group_id = :group AND id = :game AND status = 'PUBLISHED' " +
            "AND starts_at > now() AND confirmation_deadline > now()",
    ).param("group", groupId).param("game", gameId).query(String::class.java).optional().orElse(null)

    override fun inbox(actor: UUID, before: Long?) = jdbc.sql(
        """
        SELECT m.*, n.sequence AS notification_sequence, n.read_at
        FROM group_notifications n JOIN group_messages m ON m.id = n.message_id
        JOIN access_groups g ON g.id = m.group_id AND g.deleted_at IS NULL
        JOIN group_memberships membership ON membership.group_id = m.group_id AND membership.user_id = n.recipient_id
        WHERE n.recipient_id = :actor AND (:before IS NULL OR n.sequence < :before)
        ORDER BY n.sequence DESC LIMIT 51
        """.trimIndent(),
    ).param("actor", actor).param("before", before, Types.BIGINT)
        .query { rs, _ -> GroupNotification(rs.getLong("notification_sequence"), message(rs), rs.getTimestamp("read_at") != null) }.list()

    override fun markRead(actor: UUID, sequence: Long) {
        jdbc.sql("UPDATE group_notifications SET read_at = coalesce(read_at, now()) WHERE recipient_id = :actor AND sequence = :id")
            .param("actor", actor).param("id", sequence).update()
    }

    override fun preferences(actor: UUID): NotificationPreferences = jdbc.sql(
        "SELECT notices, messages, reminders FROM group_notification_preferences WHERE user_id = :actor",
    ).param("actor", actor).query { rs, _ -> preferences(rs) }.optional().orElse(NotificationPreferences())

    override fun savePreferences(actor: UUID, preferences: NotificationPreferences): NotificationPreferences = jdbc.sql(
        """
        INSERT INTO group_notification_preferences (user_id, notices, messages, reminders) VALUES (:actor, :notices, :messages, :reminders)
        ON CONFLICT (user_id) DO UPDATE SET notices = excluded.notices, messages = excluded.messages, reminders = excluded.reminders
        RETURNING notices, messages, reminders
        """.trimIndent(),
    ).param("actor", actor).param("notices", preferences.notices).param("messages", preferences.messages)
        .param("reminders", preferences.reminders).query { rs, _ -> preferences(rs) }.single()

    private fun preferences(rs: ResultSet) = NotificationPreferences(rs.getBoolean("notices"), rs.getBoolean("messages"), rs.getBoolean("reminders"))

    private fun message(rs: ResultSet) = GroupMessage(
        id = rs.getObject("id", UUID::class.java), sequence = rs.getLong("sequence"), groupId = rs.getObject("group_id", UUID::class.java),
        authorId = rs.getObject("author_id", UUID::class.java), authorName = rs.getString("author_name"),
        channel = MessageChannel.valueOf(rs.getString("channel")), body = rs.getString("body"),
        gameId = rs.getObject("game_id", UUID::class.java), recipientCount = rs.getInt("recipient_count"), createdAt = rs.getTimestamp("created_at").toInstant(),
    )
}
