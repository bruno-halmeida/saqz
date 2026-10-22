package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.adapter.output.jdbc.attendance.WaitlistPositionSql
import br.com.saqz.groups.application.communication.AttendanceUpdatesMode
import br.com.saqz.groups.application.communication.AttendanceWindowCandidate
import br.com.saqz.groups.application.communication.GroupCommunicationRepository
import br.com.saqz.groups.application.communication.GroupMessage
import br.com.saqz.groups.application.communication.GroupNotification
import br.com.saqz.groups.application.communication.MessageChannel
import br.com.saqz.groups.application.communication.OnChangeActivity
import br.com.saqz.groups.application.communication.NotificationPreferences
import br.com.saqz.groups.application.communication.PushPreferences
import br.com.saqz.groups.application.communication.ReminderCandidate
import br.com.saqz.groups.application.communication.ReminderGame
import br.com.saqz.groups.application.communication.ReminderRoster
import br.com.saqz.groups.application.communication.RosterEntry
import br.com.saqz.groups.application.communication.RosterLayout
import br.com.saqz.groups.domain.AthletePosition
import br.com.saqz.groups.domain.attendance.GameSystem
import br.com.saqz.groups.application.communication.WhatsAppPreferences
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.sql.DataSource

class JdbcGroupCommunicationRepository(dataSource: DataSource) : GroupCommunicationRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun lockGroup(groupId: UUID) = jdbc.sql(
        "SELECT id FROM access_groups WHERE id = :group AND deleted_at IS NULL FOR UPDATE",
    ).param("group", groupId).query(UUID::class.java).optional().isPresent

    /** Avisos e mensagens de quem o leitor bloqueou, e os que ele denunciou, não aparecem para ele. */
    override fun messages(viewer: UUID, groupId: UUID, channel: MessageChannel, before: Long?) = jdbc.sql(
        "SELECT m.* FROM group_messages m WHERE group_id = :group AND channel = :channel " +
            "AND (:before IS NULL OR sequence < :before) AND $NOT_FROM_BLOCKED AND $NOT_REPORTED ORDER BY sequence DESC LIMIT 51",
    ).param("group", groupId).param("channel", channel.name, Types.OTHER).param("viewer", viewer)
        .param("before", before, Types.BIGINT).query { rs, _ -> message(rs) }.list()

    override fun findRequest(groupId: UUID, actor: UUID, channel: MessageChannel, requestId: UUID) = jdbc.sql(
        "SELECT m.* FROM group_messages m WHERE group_id = :group AND author_id = :actor AND channel = :channel AND request_id = :request",
    ).param("group", groupId).param("actor", actor).param("channel", channel.name, Types.OTHER)
        .param("request", requestId).query { rs, _ -> message(rs) }.optional().orElse(null)

    override fun publish(
        groupId: UUID, actor: UUID, channel: MessageChannel, requestId: UUID, body: String, gameId: UUID?,
        reminderOrigin: AttendanceUpdatesMode?,
    ): GroupMessage {
        val id = UUID.randomUUID()
        jdbc.sql(
            """
            INSERT INTO group_messages (id, group_id, author_id, author_name, channel, request_id, body, game_id, reminder_origin)
            SELECT :id, :group, :actor, display_name, :channel, :request, :body, :game, :origin FROM access_users WHERE id = :actor
            """.trimIndent(),
        ).param("id", id).param("group", groupId).param("actor", actor).param("channel", channel.name, Types.OTHER)
            .param("request", requestId).param("body", body).param("game", gameId, Types.OTHER)
            .param("origin", reminderOrigin?.name, Types.VARCHAR).update()
        if (channel == MessageChannel.REMINDER) {
            jdbc.sql("INSERT INTO notification_attendance_links(message_id) VALUES (:id)").param("id", id).update()
        }
        // A lista a cada mudança é só do grupo do WhatsApp: central e push seguem com o lembrete dos horários fixos.
        if (reminderOrigin != AttendanceUpdatesMode.ON_CHANGE) deliver(id, groupId, actor, channel, gameId)
        return checkNotNull(findRequest(groupId, actor, channel, requestId))
    }

    /**
     * Grava um aviso de sistema que vale uma vez (FIRST_MEMBER e FIRST_CONFIRMATION por grupo, ENTRY_REQUEST por
     * pedido, via [requestId] fixo). O índice único decide a corrida: `ON CONFLICT DO NOTHING` devolve false, sem erro, e a transação de quem chamou segue.
     */
    fun publishOnce(
        groupId: UUID, actor: UUID, channel: MessageChannel, body: String, gameId: UUID?,
        requestId: UUID = UUID.randomUUID(),
        eventKind: String? = null,
    ): Boolean {
        val id = UUID.randomUUID()
        val inserted = jdbc.sql(
            """
            INSERT INTO group_messages (id, group_id, author_id, author_name, channel, request_id, body, game_id, event_kind)
            SELECT :id, :group, :actor, display_name, :channel, :request, :body, :game, :eventKind FROM access_users WHERE id = :actor
            ON CONFLICT DO NOTHING
            """.trimIndent(),
        ).param("id", id).param("group", groupId).param("actor", actor).param("channel", channel.name, Types.OTHER)
            .param("request", requestId).param("body", body).param("game", gameId, Types.OTHER)
            .param("eventKind", eventKind, Types.VARCHAR).update()
        if (inserted == 0) return false
        deliver(id, groupId, actor, channel, gameId)
        return true
    }

    override fun publishGameCancelled(groupId: UUID, actor: UUID, gameId: UUID, body: String): GroupMessage {
        val id = UUID.randomUUID()
        val requestId = UUID.randomUUID()
        jdbc.sql(
            """
            INSERT INTO group_messages (id, group_id, author_id, author_name, channel, request_id, body, game_id, event_kind)
            SELECT :id, :group, :actor, display_name, 'NOTICE', :request, :body, :game, 'GAME_CANCELLED' FROM access_users WHERE id = :actor
            """.trimIndent(),
        ).param("id", id).param("group", groupId).param("actor", actor)
            .param("request", requestId).param("body", body).param("game", gameId, Types.OTHER).update()
        jdbc.sql(
            """
            INSERT INTO group_notifications (recipient_id, message_id)
            SELECT membership.user_id, :message
            FROM group_memberships membership
            WHERE membership.group_id = :group AND membership.active AND membership.user_id <> :actor
              AND EXISTS (
                  SELECT 1 FROM game_attendance a
                  WHERE a.group_id = :group AND a.game_id = :game AND a.member_user_id = membership.user_id
                    AND a.guest_seq = 0 AND a.status IN ('CONFIRMED', 'WAITLISTED'))
            """.trimIndent(),
        ).param("message", id).param("group", groupId).param("actor", actor).param("game", gameId, Types.OTHER).update()
        val recipients = jdbc.sql("SELECT count(*) FROM group_notifications WHERE message_id = :id AND in_app")
            .param("id", id).query(Int::class.java).single()
        jdbc.sql("UPDATE group_messages SET recipient_count = :count WHERE id = :id")
            .param("count", recipients).param("id", id).update()
        return checkNotNull(findRequest(groupId, actor, MessageChannel.NOTICE, requestId))
    }

    private fun deliver(id: UUID, groupId: UUID, actor: UUID, channel: MessageChannel, gameId: UUID?) {
        // GAME_OPEN e a janela de presença não têm autor de verdade — o dono só assina o aviso do
        // sistema, e jogando como qualquer um também precisa recebê-los. Ver [firstGameOpenNotice].
        // O filtro de "já respondeu" vale para lembrete e jogo liberado. O aviso de prazo vencido é para
        // quem decide quem entra, tenha ou não respondido ao jogo.
        val everyone = (channel == MessageChannel.GAME_OPEN && firstGameOpenNotice(id, gameId)) ||
            channel == MessageChannel.WAITLIST_DEADLINE || channel == MessageChannel.FIRST_CONFIRMATION
        // Os avisos do primeiro atleta e da primeira confirmação NÃO são systemAuthored: o autor é quem causou o
        // evento e não recebe o próprio aviso (se for admin).
        val systemAuthored = channel == MessageChannel.GAME_OPEN || channel == MessageChannel.ATTENDANCE_WINDOW ||
            channel == MessageChannel.WAITLIST_DEADLINE
        // Prazo vencido e avisos de onboarding são assunto de quem organiza: dono e admins (o dono também tem linha ADMIN).
        val adminsOnly = channel in ADMINS_ONLY_CHANNELS
        jdbc.sql(
            """
            INSERT INTO group_notifications (recipient_id, message_id)
            SELECT membership.user_id, :message
            FROM group_memberships membership
            WHERE membership.group_id = :group AND membership.active
              AND (NOT :adminsOnly OR membership.role = 'ADMIN')
              AND (:systemAuthored OR membership.user_id <> :actor)
              AND (NOT :personal OR NOT EXISTS (
                  SELECT 1 FROM user_blocks b WHERE b.blocker_id = membership.user_id AND b.blocked_id = :actor))
              AND (:everyone OR CAST(:game AS uuid) IS NULL OR NOT EXISTS (
                  SELECT 1 FROM game_attendance a
                  WHERE a.group_id = :group AND a.game_id = :game AND a.member_user_id = membership.user_id
                    AND a.guest_seq = 0
              ))
            """.trimIndent(),
        ).param("message", id).param("group", groupId).param("actor", actor)
            .param("systemAuthored", systemAuthored)
            .param("adminsOnly", adminsOnly)
            .param("personal", channel in PERSONAL_CHANNELS)
            .param("everyone", everyone)
            .param("game", gameId, Types.OTHER).update()
        val recipients = jdbc.sql("SELECT count(*) FROM group_notifications WHERE message_id = :id AND in_app")
            .param("id", id).query(Int::class.java).single()
        jdbc.sql("UPDATE group_messages SET recipient_count = :count WHERE id = :id")
            .param("count", recipients).param("id", id).update()
    }

    /**
     * O primeiro aviso de jogo liberado alcança o grupo inteiro — inclusive quem o auto-confirm
     * já confirmou, que só por ele fica sabendo que o jogo abriu. A view
     * `notification_delivery_context` repete este critério por `sequence` na entrega, porque o
     * push é revalidado depois de enfileirado.
     */
    private fun firstGameOpenNotice(messageId: UUID, gameId: UUID?): Boolean = gameId != null && jdbc.sql(
        """
        SELECT NOT EXISTS (
            SELECT 1 FROM group_messages earlier
            WHERE earlier.game_id = :game AND earlier.channel = 'GAME_OPEN'
              AND earlier.sequence < (SELECT sequence FROM group_messages WHERE id = :id))
        """.trimIndent(),
    ).param("game", gameId).param("id", messageId).query(Boolean::class.java).single()

    /** "Avisar" manual vale do jogo publicado até o início, inclusive com o prazo vencido (PROM-009 / NOT-003). */
    override fun reminderGame(groupId: UUID, gameId: UUID): ReminderGame? = jdbc.sql(
        "SELECT local_date, local_time, venue_name, confirmation_deadline, capacity FROM games " +
            "WHERE group_id = :group AND id = :game " +
            "AND status = 'PUBLISHED' AND starts_at > now()",
    ).param("group", groupId).param("game", gameId).query { rs, _ -> ReminderGame(
        localDate = rs.getObject("local_date", LocalDate::class.java),
        localTime = rs.getObject("local_time", LocalTime::class.java),
        venue = rs.getString("venue_name"),
        confirmationDeadline = rs.getTimestamp("confirmation_deadline").toInstant(),
        capacity = rs.getInt("capacity"),
    ) }.optional().orElse(null)

    override fun reminderRoster(groupId: UUID, gameId: UUID): ReminderRoster {
        val rows = jdbc.sql(
            """
            SELECT CASE attendance.status
                       WHEN 'CONFIRMED' THEN 'CONFIRMED'
                       WHEN 'WAITLISTED' THEN 'WAITLISTED'
                       ELSE 'DECLINED' END AS bucket,
                   attendance.member_display_name AS name,
                   attendance.position
            FROM game_attendance attendance
            JOIN group_memberships membership
                ON membership.group_id = attendance.group_id
                AND membership.user_id = attendance.member_user_id AND membership.active
            JOIN games game ON game.id = attendance.game_id AND game.group_id = attendance.group_id
            JOIN access_groups ag ON ag.id = game.group_id
            WHERE attendance.game_id = :game AND attendance.group_id = :group
              AND (attendance.guest_seq = 0 OR attendance.status <> 'DECLINED')
            ORDER BY CASE WHEN attendance.status = 'WAITLISTED'
                          THEN ${WaitlistPositionSql.tier("attendance", "membership", "game", "ag")} ELSE 0 END,
                     CASE WHEN attendance.status = 'WAITLISTED' THEN attendance.waitlist_sequence END NULLS FIRST,
                     lower(attendance.member_display_name), attendance.member_display_name
            """.trimIndent(),
        ).param("group", groupId).param("game", gameId)
            .query { rs, _ ->
                rs.getString("bucket") to RosterEntry(rs.getString("name"), rs.getString("position")?.let(AthletePosition::valueOf))
            }.list()
        fun bucket(name: String) = rows.filter { it.first == name }.map { it.second }
        val (confirmed, waitlisted, declined) = listOf("CONFIRMED", "WAITLISTED", "DECLINED").map(::bucket)
        return ReminderRoster(
            confirmed = confirmed.map { it.name },
            waitlisted = waitlisted.map { it.name },
            declined = declined.map { it.name },
            layout = rosterLayout(groupId, gameId)?.let { (system, slots, numberedWaitlist) ->
                RosterLayout(system, slots, confirmed, waitlisted, declined, numberedWaitlist)
            },
        )
    }

    /**
     * Sistema de jogo do grupo e vagas por posição do jogo. Grupo sem sistema é legado (null); grupo 5-1 com jogo que
     * nasceu sem as vagas por posição também, porque a lista não tem como sair por posição.
     */
    private fun rosterLayout(groupId: UUID, gameId: UUID): Triple<GameSystem, Map<AthletePosition, Int>, Boolean>? {
        val config = jdbc.sql(
            "SELECT game_system, promotion_mode = 'FIFO' AND NOT mensalista_priority AS numbered FROM access_groups WHERE id = :group",
        ).param("group", groupId).query { rs, _ ->
            rs.getString("game_system")?.let { GameSystem.valueOf(it) to rs.getBoolean("numbered") }
        }.optional().orElse(null) ?: return null
        val slots = jdbc.sql("SELECT position, slots FROM game_position_slots WHERE game_id = :game")
            .param("game", gameId)
            .query { rs, _ -> AthletePosition.valueOf(rs.getString("position")) to rs.getInt("slots") }.list().toMap()
        if (config.first == GameSystem.FIVE_ONE && slots.isEmpty()) return null
        return Triple(config.first, slots, config.second)
    }

    /** `DISTINCT ON`: um grupo com agenda recorrente tem vários jogos abertos, mas só o mais próximo é lembrado. */
    override fun reminderCandidates(mode: AttendanceUpdatesMode?): List<ReminderCandidate> =
        candidates(deadlineOpen = false, mode = mode)

    override fun openGameCandidates(): List<ReminderCandidate> = candidates(deadlineOpen = true, mode = null)

    override fun attendanceDigest(gameId: UUID): String? = jdbc.sql(
        "SELECT roster_digest FROM group_attendance_digest WHERE game_id = :game FOR UPDATE",
    ).param("game", gameId).query(String::class.java).optional().orElse(null)

    override fun saveAttendanceDigest(gameId: UUID, digest: String) {
        jdbc.sql(
            """
            INSERT INTO group_attendance_digest (game_id, roster_digest) VALUES (:game, :digest)
            ON CONFLICT (game_id) DO UPDATE SET roster_digest = EXCLUDED.roster_digest, updated_at = now()
            """.trimIndent(),
        ).param("game", gameId).param("digest", digest).update()
    }

    override fun onChangeActivity(groupId: UUID, dayStart: Instant): OnChangeActivity = jdbc.sql(
        """
        SELECT max(created_at) AS last_at, count(*) FILTER (WHERE created_at >= :dayStart) AS today
        FROM group_messages WHERE group_id = :group AND reminder_origin = 'ON_CHANGE'
        """.trimIndent(),
    ).param("group", groupId).param("dayStart", Timestamp.from(dayStart)).query { rs, _ ->
        OnChangeActivity(rs.getTimestamp("last_at")?.toInstant(), rs.getInt("today"))
    }.single()

    private fun candidates(deadlineOpen: Boolean, mode: AttendanceUpdatesMode?): List<ReminderCandidate> = jdbc.sql(
        """
        SELECT DISTINCT ON (games.group_id)
               games.id AS game_id, games.group_id, games.local_date, games.local_time, games.venue_name,
               games.confirmation_deadline, games.capacity, groups.owner_user_id
        FROM games
        JOIN access_groups groups ON groups.id = games.group_id AND groups.deleted_at IS NULL
        WHERE games.status = 'PUBLISHED' AND games.starts_at > now()
          AND (NOT :deadlineOpen OR games.confirmation_deadline > now())
          AND (CAST(:mode AS varchar) IS NULL OR groups.attendance_updates_mode = :mode)
        ORDER BY games.group_id, games.starts_at, games.id
        """.trimIndent(),
    ).param("deadlineOpen", deadlineOpen).param("mode", mode?.name, Types.VARCHAR).query { rs, _ -> candidate(rs) }.list()

    override fun reminderCandidate(groupId: UUID): ReminderCandidate? = jdbc.sql(
        """
        SELECT games.id AS game_id, games.group_id, games.local_date, games.local_time, games.venue_name,
               games.confirmation_deadline, games.capacity, groups.owner_user_id
        FROM games
        JOIN access_groups groups ON groups.id = games.group_id AND groups.deleted_at IS NULL
        WHERE games.group_id = :group AND games.status = 'PUBLISHED'
          AND games.starts_at > now() AND games.confirmation_deadline > now()
        ORDER BY games.starts_at, games.id
        LIMIT 1
        """.trimIndent(),
    ).param("group", groupId).query { rs, _ -> candidate(rs) }.optional().orElse(null)

    /**
     * Jogos que entraram na janela de presença (DEB-113): de 8 h antes do prazo de confirmação até o prazo (o início do
     * jogo, se faltar prazo), sem janela anterior. Jogo publicado já dentro da janela abre na primeira passada. É por
     * jogo, não só o próximo do grupo. A contagem inclui convidados, como o `confirmedCount` do jogo.
     */
    override fun attendanceWindowCandidates(): List<AttendanceWindowCandidate> = jdbc.sql(
        """
        SELECT games.id AS game_id, games.group_id, games.local_date, games.local_time, games.venue_name,
               games.capacity, groups.owner_user_id,
               count(*) FILTER (WHERE a.status = 'CONFIRMED') AS confirmed,
               count(*) FILTER (WHERE a.status = 'WAITLISTED') AS waitlisted
        FROM games
        JOIN access_groups groups ON groups.id = games.group_id AND groups.deleted_at IS NULL
        LEFT JOIN game_attendance a ON a.game_id = games.id
        WHERE games.status = 'PUBLISHED'
          AND now() >= coalesce(games.confirmation_deadline, games.starts_at) - interval '8 hours'
          AND now() < coalesce(games.confirmation_deadline, games.starts_at)
          AND NOT EXISTS (
              SELECT 1 FROM group_messages w WHERE w.game_id = games.id AND w.channel = 'ATTENDANCE_WINDOW')
        GROUP BY games.id, groups.owner_user_id
        ORDER BY games.starts_at, games.id
        """.trimIndent(),
    ).query { rs, _ -> AttendanceWindowCandidate(
        gameId = rs.getObject("game_id", UUID::class.java),
        groupId = rs.getObject("group_id", UUID::class.java),
        ownerId = rs.getObject("owner_user_id", UUID::class.java),
        game = ReminderGame(
            localDate = rs.getObject("local_date", LocalDate::class.java),
            localTime = rs.getObject("local_time", LocalTime::class.java),
            venue = rs.getString("venue_name"),
        ),
        confirmed = rs.getInt("confirmed"),
        capacity = rs.getInt("capacity"),
        waitlisted = rs.getInt("waitlisted"),
    ) }.list()

    private fun candidate(rs: ResultSet) = ReminderCandidate(
        gameId = rs.getObject("game_id", UUID::class.java),
        groupId = rs.getObject("group_id", UUID::class.java),
        ownerId = rs.getObject("owner_user_id", UUID::class.java),
        game = ReminderGame(
            localDate = rs.getObject("local_date", LocalDate::class.java),
            localTime = rs.getObject("local_time", LocalTime::class.java),
            venue = rs.getString("venue_name"),
            confirmationDeadline = rs.getTimestamp("confirmation_deadline").toInstant(),
            capacity = rs.getInt("capacity"),
        ),
    )

    override fun inbox(actor: UUID, before: Long?) = jdbc.sql(
        """
        SELECT m.*, n.sequence AS notification_sequence, n.read_at
        FROM group_notifications n JOIN group_messages m ON m.id = n.message_id
        JOIN access_groups g ON g.id = m.group_id AND g.deleted_at IS NULL
        JOIN group_memberships membership ON membership.group_id = m.group_id AND membership.user_id = n.recipient_id
        WHERE n.recipient_id = :actor AND (:before IS NULL OR n.sequence < :before)
          AND $NOT_FROM_BLOCKED AND $NOT_REPORTED
        ORDER BY n.sequence DESC LIMIT 51
        """.trimIndent(),
    ).param("actor", actor).param("viewer", actor).param("before", before, Types.BIGINT)
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

    private companion object {
        val ADMINS_ONLY_CHANNELS = setOf(
            MessageChannel.WAITLIST_DEADLINE,
            MessageChannel.FIRST_MEMBER,
            MessageChannel.FIRST_CONFIRMATION,
            MessageChannel.ENTRY_REQUEST,
        )
        /** Canais escritos por uma pessoa: são os que o bloqueio esconde. Os do sistema seguem chegando. */
        val PERSONAL_CHANNELS = setOf(MessageChannel.NOTICE, MessageChannel.CHAT)
        const val NOT_FROM_BLOCKED = "(m.channel NOT IN ('NOTICE', 'CHAT') OR NOT EXISTS (" +
            "SELECT 1 FROM user_blocks b WHERE b.blocker_id = :viewer AND b.blocked_id = m.author_id))"
        /** Mensagem ou aviso que o leitor denunciou: some para ele, mesmo depois de a equipe resolver a denúncia. */
        const val NOT_REPORTED = "NOT EXISTS (SELECT 1 FROM content_reports r " +
            "WHERE r.reporter_id = :viewer AND r.target_type = 'MESSAGE' AND r.target_id = m.id)"
    }
}
