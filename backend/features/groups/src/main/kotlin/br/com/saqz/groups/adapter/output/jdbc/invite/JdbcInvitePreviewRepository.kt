package br.com.saqz.groups.adapter.output.jdbc.invite

import br.com.saqz.groups.application.invite.InviteTokenDigest
import br.com.saqz.groups.application.invite.preview.PreviewInviteAttemptWindow
import br.com.saqz.groups.application.invite.preview.PreviewInviteRepository
import br.com.saqz.groups.application.invite.preview.PreviewInviteCard
import br.com.saqz.groups.application.invite.preview.PreviewNextGame
import br.com.saqz.groups.application.invite.preview.PreviewRegularSlot
import br.com.saqz.groups.application.invite.preview.PreviewableInvite
import br.com.saqz.groups.application.invite.preview.RecordInvalidPreviewInviteAttempt
import br.com.saqz.groups.domain.group.GroupComposition
import br.com.saqz.groups.domain.group.GroupLevel
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.DayOfWeek
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class JdbcInvitePreviewRepository(dataSource: DataSource) : PreviewInviteRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun lockAttemptWindow(userId: UUID, initializedAt: Instant): PreviewInviteAttemptWindow {
        jdbc.sql(
            """
            INSERT INTO invite_redemption_limits (user_id, window_started_at, invalid_count)
            VALUES (:userId, :windowStartedAt, 0)
            ON CONFLICT (user_id) DO NOTHING
            """.trimIndent(),
        )
            .param("userId", userId)
            .param("windowStartedAt", Timestamp.from(initializedAt))
            .update()
        return jdbc.sql(
            """
            SELECT window_started_at, invalid_count
            FROM invite_redemption_limits
            WHERE user_id = :userId
            FOR UPDATE
            """.trimIndent(),
        )
            .param("userId", userId)
            .query { result, _ ->
                PreviewInviteAttemptWindow(
                    result.getTimestamp("window_started_at").toInstant(),
                    result.getInt("invalid_count"),
                )
            }
            .single()
    }

    override fun recordInvalidAttempt(command: RecordInvalidPreviewInviteAttempt) {
        val changed = jdbc.sql(
            """
            UPDATE invite_redemption_limits
            SET window_started_at = :windowStartedAt,
                invalid_count = :invalidCount
            WHERE user_id = :userId
            """.trimIndent(),
        )
            .param("userId", command.userId)
            .param("windowStartedAt", Timestamp.from(command.windowStartedAt))
            .param("invalidCount", command.invalidCount)
            .update()
        check(changed == 1) { "Invite preview attempt window was not locked" }
    }

    override fun findInvite(digest: InviteTokenDigest, now: Instant): PreviewableInvite? = jdbc.sql(
        """
        SELECT
            invites.group_id,
            groups.deleted_at IS NOT NULL AS group_deleted,
            groups.name AS group_name,
            groups.city,
            groups.composition,
            groups.level,
            creator.display_name AS inviter_name,
            (SELECT COUNT(*) FROM group_memberships memberships WHERE memberships.group_id = groups.id) AS member_count,
            slots.weekday AS slot_weekday,
            slots.start_time AS slot_start_time,
            next_game.starts_at AS next_game_starts_at,
            next_game.venue_name AS next_game_venue_name,
            next_game.venue_court AS next_game_court
        FROM group_invites invites
        JOIN access_groups groups ON groups.id = invites.group_id
        JOIN access_users creator ON creator.id = invites.created_by_user_id
        LEFT JOIN group_regular_slots slots ON slots.group_id = groups.id
        LEFT JOIN LATERAL (
            SELECT games.starts_at, games.venue_name, games.venue_court
            FROM games
            WHERE games.group_id = groups.id
              AND games.status = 'PUBLISHED'
              AND games.starts_at > :now
            ORDER BY games.starts_at, games.id
            LIMIT 1
        ) next_game ON true
        WHERE invites.token_digest = :tokenDigest
        ORDER BY slots.position, slots.weekday, slots.start_time
        """.trimIndent(),
    )
        .param("tokenDigest", digest.toByteArray())
        .param("now", Timestamp.from(now))
        .query { result, _ -> result.toRow() }
        .list()
        .takeUnless(List<*>::isEmpty)
        ?.let { rows ->
            val first = rows.first()
            PreviewableInvite(
                groupDeleted = first.groupDeleted,
                // SPEC_DEVIATION: VUL-137's expires_at is not present in this base branch.
                // The field stays null until that migration is available.
                expiredAt = null,
                card = PreviewInviteCard(
                    groupName = first.groupName,
                    city = first.city,
                    composition = first.composition,
                    level = first.level,
                    memberCount = first.memberCount,
                    regularSlots = rows.mapNotNull { it.slot },
                    inviterName = first.inviterName,
                    // SPEC_DEVIATION: VUL-139's entry_requires_approval is not present yet.
                    entryRequiresApproval = false,
                    expiresAt = null,
                    nextGame = first.nextGame,
                ),
            )
        }

    private fun ResultSet.toRow() = Row(
        groupDeleted = getBoolean("group_deleted"),
        groupName = getString("group_name"),
        city = getString("city"),
        composition = getString("composition")?.let(GroupComposition::valueOf),
        level = getString("level")?.let(GroupLevel::valueOf),
        inviterName = getString("inviter_name"),
        memberCount = getInt("member_count"),
        slot = getObject("slot_weekday", Integer::class.java)?.let {
            PreviewRegularSlot(
                weekday = DayOfWeek.of(it.toInt()),
                startTime = getTime("slot_start_time").toLocalTime(),
            )
        },
        nextGame = getTimestamp("next_game_starts_at")?.let {
            PreviewNextGame(
                startsAt = it.toInstant(),
                venueName = getString("next_game_venue_name"),
                court = getString("next_game_court"),
            )
        },
    )

    private data class Row(
        val groupDeleted: Boolean,
        val groupName: String,
        val city: String?,
        val composition: GroupComposition?,
        val level: GroupLevel?,
        val inviterName: String?,
        val memberCount: Int,
        val slot: PreviewRegularSlot?,
        val nextGame: PreviewNextGame?,
    )
}
