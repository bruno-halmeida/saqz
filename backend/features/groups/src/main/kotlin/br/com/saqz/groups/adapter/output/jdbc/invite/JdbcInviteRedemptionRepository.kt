package br.com.saqz.groups.adapter.output.jdbc.invite

import br.com.saqz.groups.application.invite.InviteTokenDigest
import br.com.saqz.groups.application.invite.redeem.GroupAthleteOccupancy
import br.com.saqz.groups.application.invite.redeem.InviteAttemptWindow
import br.com.saqz.groups.application.invite.redeem.InviteRedemptionRepository
import br.com.saqz.groups.application.invite.redeem.RecordInvalidInviteAttempt
import br.com.saqz.groups.application.invite.redeem.RedeemMembershipCommand
import br.com.saqz.groups.application.invite.redeem.RedeemableInvite
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.plan.ClosedAthleteOccupancy
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class JdbcInviteRedemptionRepository(dataSource: DataSource) : InviteRedemptionRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun lockAttemptWindow(userId: UUID, initializedAt: Instant): InviteAttemptWindow {
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
                InviteAttemptWindow(result.getTimestamp("window_started_at").toInstant(), result.getInt("invalid_count"))
            }
            .single()
    }

    override fun findInvite(digest: InviteTokenDigest): RedeemableInvite? = jdbc.sql(
        // Deliberately includes deleted groups so the use case can return its specific error.
        """
        SELECT invites.group_id, groups.deleted_at IS NOT NULL AS group_deleted
        FROM group_invites invites
        JOIN access_groups groups ON groups.id = invites.group_id
        WHERE invites.token_digest = :tokenDigest
        """.trimIndent(),
    )
        .param("tokenDigest", digest.toByteArray())
        .query { result, _ ->
            RedeemableInvite(
                groupId = result.getObject("group_id", UUID::class.java),
                groupDeleted = result.getBoolean("group_deleted"),
            )
        }
        .optional()
        .orElse(null)

    override fun recordInvalidAttempt(command: RecordInvalidInviteAttempt) {
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
        check(changed == 1) { "Invite attempt window was not locked" }
    }

    override fun loadAthleteOccupancy(groupId: UUID): GroupAthleteOccupancy? {
        val ownerUserId = jdbc.sql(
            "SELECT owner_user_id FROM access_groups " +
                "WHERE id = :groupId AND deleted_at IS NULL FOR UPDATE",
        )
            .param("groupId", groupId)
            .query(UUID::class.java)
            .optional()
            .orElse(null)
            ?: return null

        val openMemberIds = jdbc.sql(
            """
            SELECT user_id
            FROM group_memberships
            WHERE group_id = :groupId
              AND user_id <> :ownerUserId
              AND EXISTS (
                  SELECT 1 FROM access_groups
                  WHERE id = :groupId AND deleted_at IS NULL
              )
            """.trimIndent(),
        )
            .param("groupId", groupId)
            .param("ownerUserId", ownerUserId)
            .query { result, _ -> result.getObject("user_id", UUID::class.java) }
            .list()
            .toSet()

        val openWaitlistIds = jdbc.sql(
            """
            SELECT DISTINCT member_user_id
            FROM game_attendance
            WHERE group_id = :groupId
              AND status = 'WAITLISTED'
              AND member_user_id <> :ownerUserId
              AND EXISTS (
                  SELECT 1 FROM access_groups
                  WHERE id = :groupId AND deleted_at IS NULL
              )
            """.trimIndent(),
        )
            .param("groupId", groupId)
            .param("ownerUserId", ownerUserId)
            .query { result, _ -> result.getObject("member_user_id", UUID::class.java) }
            .list()
            .toSet()

        val closedOccupancies = jdbc.sql(
            """
            SELECT user_id, removed_at
            FROM group_membership_removals
            WHERE group_id = :groupId
              AND user_id <> :ownerUserId
              AND removed_at > now() - interval '30 days'
              AND EXISTS (
                  SELECT 1 FROM access_groups
                  WHERE id = :groupId AND deleted_at IS NULL
              )
            """.trimIndent(),
        )
            .param("groupId", groupId)
            .param("ownerUserId", ownerUserId)
            .query { result, _ ->
                ClosedAthleteOccupancy(
                    athleteId = result.getObject("user_id", UUID::class.java),
                    closedAt = result.getTimestamp("removed_at").toInstant(),
                )
            }
            .list()

        return GroupAthleteOccupancy(
            ownerUserId = ownerUserId,
            openMemberIds = openMemberIds,
            openWaitlistIds = openWaitlistIds,
            closedOccupancies = closedOccupancies,
        )
    }

    override fun redeemMembership(command: RedeemMembershipCommand): GroupRole = jdbc.sql(
        """
        WITH target_group AS (
            SELECT id, owner_user_id
            FROM access_groups
            WHERE id = :groupId AND deleted_at IS NULL
        ), persisted_membership AS (
            INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at)
            SELECT id, :userId, 'ATHLETE', now(), now()
            FROM target_group
            WHERE owner_user_id <> :userId
            ON CONFLICT (group_id, user_id) DO UPDATE SET
                role = group_memberships.role,
                updated_at = group_memberships.updated_at
            RETURNING role
        )
        SELECT CASE
            WHEN target_group.owner_user_id = :userId THEN 'OWNER'
            ELSE persisted_membership.role
        END AS resolved_role
        FROM target_group
        LEFT JOIN persisted_membership ON true
        """.trimIndent(),
    )
        .param("groupId", command.groupId)
        .param("userId", command.userId)
        .query(String::class.java)
        .single()
        .let(GroupRole::valueOf)
}
