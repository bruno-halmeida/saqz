package br.com.saqz.groups.adapter.output.jdbc.invite

import br.com.saqz.groups.application.invite.InviteKind
import br.com.saqz.groups.application.invite.InviteTokenDigest
import br.com.saqz.groups.application.invite.redeem.CreateEntryRequestCommand
import br.com.saqz.groups.application.invite.redeem.GroupAthleteOccupancy
import br.com.saqz.groups.application.invite.redeem.InviteAttemptWindow
import br.com.saqz.groups.application.invite.redeem.InviteRedemptionRepository
import br.com.saqz.groups.application.invite.redeem.RecordInvalidInviteAttempt
import br.com.saqz.groups.application.invite.redeem.RedeemMembershipCommand
import br.com.saqz.groups.application.invite.redeem.RedeemableInvite
import br.com.saqz.groups.application.entryrequest.EntryRequestRepository
import br.com.saqz.groups.application.entryrequest.EntryRequestAthleteProfile
import br.com.saqz.groups.application.entryrequest.GroupEntryRequest
import br.com.saqz.groups.application.entryrequest.SaveEntryRequestAthleteProfileCommand
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.AthleteLevel
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.AthletePosition
import br.com.saqz.groups.domain.AthletePreferredSide
import br.com.saqz.groups.domain.GroupRole
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class JdbcInviteRedemptionRepository(dataSource: DataSource) : InviteRedemptionRepository, EntryRequestRepository {
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
        // O link do lembrete vale sem prazo, mas só se a mensagem é posterior à última troca (rotated_at, não a criação preguiçosa) ou desativação do convite.
        """
        SELECT invites.group_id,
               groups.deleted_at IS NOT NULL AS group_deleted,
               groups.entry_requires_approval,
               invites.expires_at,
               'PUBLIC' AS kind
        FROM group_invites invites
        JOIN access_groups groups ON groups.id = invites.group_id
        WHERE invites.token_digest = :tokenDigest OR invites.short_code_digest = :tokenDigest
        UNION ALL
        -- Link de mensalistas (CONV-011): o código morto (trocado ou invalidado) já não está na tabela.
        SELECT links.group_id, groups.deleted_at IS NOT NULL AS group_deleted,
               groups.entry_requires_approval, links.expires_at, 'MONTHLY' AS kind
        FROM group_monthly_invite_links links
        JOIN access_groups groups ON groups.id = links.group_id
        WHERE links.short_code_digest = :tokenDigest
        UNION ALL
        SELECT m.group_id, groups.deleted_at IS NOT NULL AS group_deleted,
               groups.entry_requires_approval, NULL::timestamptz AS expires_at, 'PUBLIC' AS kind
        FROM notification_attendance_links link
        JOIN group_messages m ON m.id = link.message_id
        JOIN access_groups groups ON groups.id = m.group_id
        WHERE sha256(convert_to(link.code, 'UTF8')) = :tokenDigest
          AND m.created_at >= GREATEST(
              COALESCE((SELECT rotated_at FROM group_invites WHERE group_id = m.group_id), '-infinity'),
              COALESCE((SELECT deactivated_at FROM group_invite_deactivations WHERE group_id = m.group_id), '-infinity')
          )
        """.trimIndent(),
    )
        .param("tokenDigest", digest.toByteArray())
        .query { result, _ ->
            RedeemableInvite(
                groupId = result.getObject("group_id", UUID::class.java),
                expiresAt = result.getTimestamp("expires_at")?.toInstant(),
                groupDeleted = result.getBoolean("group_deleted"),
                entryRequiresApproval = result.getBoolean("entry_requires_approval"),
                kind = InviteKind.valueOf(result.getString("kind")),
            )
        }
        .optional()
        .orElse(null)

    override fun findMembershipRole(groupId: UUID, userId: UUID): GroupRole? = jdbc.sql(
        """
        SELECT CASE
            WHEN groups.owner_user_id = :userId THEN 'OWNER'
            ELSE memberships.role::text
        END AS resolved_role
        FROM access_groups groups
        LEFT JOIN group_memberships memberships
            ON memberships.group_id = groups.id
            AND memberships.user_id = :userId
        WHERE groups.id = :groupId
          AND groups.deleted_at IS NULL
          AND (groups.owner_user_id = :userId OR memberships.user_id IS NOT NULL)
        """.trimIndent(),
    )
        .param("groupId", groupId)
        .param("userId", userId)
        .query(String::class.java)
        .optional()
        .orElse(null)
        ?.let(GroupRole::valueOf)

    override fun createEntryRequest(command: CreateEntryRequestCommand) {
        jdbc.sql(
            """
            INSERT INTO group_entry_requests (group_id, user_id, requested_at)
            VALUES (:groupId, :userId, :requestedAt)
            ON CONFLICT (group_id, user_id) DO NOTHING
            """.trimIndent(),
        )
            .param("groupId", command.groupId)
            .param("userId", command.userId)
            .param("requestedAt", Timestamp.from(command.requestedAt))
            .update()
    }

    override fun mustRequestReturn(groupId: UUID, userId: UUID): Boolean = jdbc.sql(
        """
        SELECT EXISTS (
            SELECT 1 FROM group_membership_removals
            WHERE group_id = :groupId AND user_id = :userId AND requires_approval
        )
        """.trimIndent(),
    )
        .param("groupId", groupId)
        .param("userId", userId)
        .query(Boolean::class.java)
        .single()

    override fun clearReturnMark(groupId: UUID, userId: UUID) {
        jdbc.sql(
            """
            UPDATE group_membership_removals SET requires_approval = false
            WHERE group_id = :groupId AND user_id = :userId AND requires_approval
            """.trimIndent(),
        )
            .param("groupId", groupId)
            .param("userId", userId)
            .update()
    }

    override fun list(groupId: UUID): List<GroupEntryRequest> = jdbc.sql(
        """
        SELECT requests.user_id, users.display_name, requests.requested_at
        FROM group_entry_requests requests
        JOIN access_users users ON users.id = requests.user_id AND users.deleted_at IS NULL
        WHERE requests.group_id = :groupId
        ORDER BY requests.requested_at, requests.user_id
        """.trimIndent(),
    )
        .param("groupId", groupId)
        .query(::mapEntryRequest)
        .list()

    override fun find(groupId: UUID, userId: UUID): GroupEntryRequest? = jdbc.sql(
        """
        SELECT requests.user_id, users.display_name, requests.requested_at
        FROM group_entry_requests requests
        JOIN access_users users ON users.id = requests.user_id AND users.deleted_at IS NULL
        WHERE requests.group_id = :groupId
          AND requests.user_id = :userId
        """.trimIndent(),
    )
        .param("groupId", groupId)
        .param("userId", userId)
        .query(::mapEntryRequest)
        .optional()
        .orElse(null)

    override fun findAthleteProfile(groupId: UUID, userId: UUID): EntryRequestAthleteProfile? = jdbc.sql(
        """
        SELECT nickname, position::text, secondary_position::text, level::text, preferred_side::text, height_cm
        FROM group_entry_requests
        WHERE group_id = :groupId AND user_id = :userId AND athlete_registration_completed
        """.trimIndent(),
    )
        .param("groupId", groupId)
        .param("userId", userId)
        .query { result, _ ->
            EntryRequestAthleteProfile(
                nickname = result.getString("nickname"),
                position = result.getString("position")?.let(AthletePosition::valueOf),
                secondaryPosition = result.getString("secondary_position")?.let(AthletePosition::valueOf),
                level = result.getString("level")?.let(AthleteLevel::valueOf),
                preferredSide = result.getString("preferred_side")?.let(AthletePreferredSide::valueOf),
                heightCm = result.getInt("height_cm").takeUnless { result.wasNull() },
            )
        }
        .optional()
        .orElse(null)

    override fun saveAthleteProfile(command: SaveEntryRequestAthleteProfileCommand): Boolean = jdbc.sql(
        """
        UPDATE group_entry_requests
        SET nickname = :nickname,
            position = :position,
            secondary_position = :secondaryPosition,
            level = :level,
            preferred_side = :preferredSide,
            height_cm = :heightCm,
            athlete_registration_completed = true
        WHERE group_id = :groupId AND user_id = :userId
        """.trimIndent(),
    )
        .param("groupId", command.groupId)
        .param("userId", command.userId)
        .param("nickname", command.nickname, Types.VARCHAR)
        .param("position", command.position?.name, Types.OTHER)
        .param("secondaryPosition", command.secondaryPosition?.name, Types.OTHER)
        .param("level", command.level?.name, Types.OTHER)
        .param("preferredSide", command.preferredSide?.name, Types.OTHER)
        .param("heightCm", command.heightCm, Types.SMALLINT)
        .update() == 1

    override fun applyAthleteProfileToMembership(groupId: UUID, userId: UUID) {
        jdbc.sql(
            """
            UPDATE group_memberships memberships
            SET nickname = requests.nickname,
                position = requests.position,
                secondary_position = requests.secondary_position,
                level = requests.level,
                preferred_side = requests.preferred_side,
                height_cm = requests.height_cm,
                athlete_registration_completed = true,
                updated_at = now()
            FROM group_entry_requests requests
            WHERE requests.group_id = :groupId AND requests.user_id = :userId
              AND requests.athlete_registration_completed
              AND memberships.group_id = requests.group_id
              AND memberships.user_id = requests.user_id
            """.trimIndent(),
        )
            .param("groupId", groupId)
            .param("userId", userId)
            .update()
    }

    override fun delete(groupId: UUID, userId: UUID) {
        jdbc.sql(
            """
            DELETE FROM group_entry_requests
            WHERE group_id = :groupId AND user_id = :userId
            """.trimIndent(),
        )
            .param("groupId", groupId)
            .param("userId", userId)
            .update()
    }

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

        val memberIds = jdbc.sql(
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

        return GroupAthleteOccupancy(
            ownerUserId = ownerUserId,
            memberIds = memberIds,
        )
    }

    /**
     * Entrada direta (convite ou link de presença). Apaga o pedido pendente da pessoa no grupo, na mesma transação:
     * com "Aprovar pedidos" desligado depois do pedido, a linha ficaria órfã em Convidar e nas Inícios (DEB-089).
     */
    override fun redeemMembership(command: RedeemMembershipCommand): GroupRole {
        jdbc.sql("DELETE FROM group_entry_requests WHERE group_id = :groupId AND user_id = :userId")
            .param("groupId", command.groupId).param("userId", command.userId).update()
        return insertMembership(command)
    }

    private fun insertMembership(command: RedeemMembershipCommand): GroupRole = jdbc.sql(
        """
        WITH target_group AS (
            SELECT id, owner_user_id
            FROM access_groups
            WHERE id = :groupId AND deleted_at IS NULL
        ), persisted_membership AS (
            INSERT INTO group_memberships (group_id, user_id, role, membership_type, created_at, updated_at)
            SELECT id, :userId, 'ATHLETE', :membershipType, now(), now()
            FROM target_group
            WHERE owner_user_id <> :userId
            ON CONFLICT (group_id, user_id) DO UPDATE SET
                role = group_memberships.role,
                updated_at = group_memberships.updated_at
            RETURNING role
        )
        SELECT CASE
            WHEN target_group.owner_user_id = :userId THEN 'OWNER'
            ELSE persisted_membership.role::text
        END AS resolved_role
        FROM target_group
        LEFT JOIN persisted_membership ON true
        """.trimIndent(),
    )
        .param("groupId", command.groupId)
        .param("userId", command.userId)
        .param("membershipType", command.membershipType.name, Types.OTHER)
        .query(String::class.java)
        .single()
        .let(GroupRole::valueOf)

    override fun findMembershipType(groupId: UUID, userId: UUID): AthleteMembershipType? = jdbc.sql(
        "SELECT membership_type::text FROM group_memberships WHERE group_id = :groupId AND user_id = :userId",
    )
        .param("groupId", groupId)
        .param("userId", userId)
        .query(String::class.java)
        .optional()
        .orElse(null)
        ?.let(AthleteMembershipType::valueOf)

    override fun hasMonthlyFeeDefault(groupId: UUID): Boolean = jdbc.sql(
        """
        SELECT EXISTS (
            SELECT 1 FROM access_groups
            WHERE id = :groupId AND monthly_fee_cents IS NOT NULL AND monthly_due_day IS NOT NULL
        )
        """.trimIndent(),
    )
        .param("groupId", groupId)
        .query(Boolean::class.java)
        .single()

    /** Mesmo efeito do PATCH de atleta com MENSALISTA sem valor próprio: valor e dia vêm do grupo (MEM-003). */
    override fun promoteToMensalista(groupId: UUID, userId: UUID) {
        jdbc.sql(
            """
            UPDATE group_memberships
            SET membership_type = 'MENSALISTA', monthly_fee_cents = NULL, monthly_due_day = NULL, updated_at = now()
            WHERE group_id = :groupId AND user_id = :userId AND membership_type = 'AVULSO'
            """.trimIndent(),
        )
            .param("groupId", groupId)
            .param("userId", userId)
            .update()
    }

    override fun deleteMatchingInvitee(groupId: UUID, userId: UUID) {
        jdbc.sql(
            """
            DELETE FROM group_monthly_invitees invitees
            USING access_users users
            WHERE invitees.group_id = :groupId AND users.id = :userId AND invitees.phone = users.phone
            """.trimIndent(),
        )
            .param("groupId", groupId)
            .param("userId", userId)
            .update()
    }

    private fun mapEntryRequest(result: java.sql.ResultSet, rowNumber: Int) = GroupEntryRequest(
        userId = result.getObject("user_id", UUID::class.java),
        displayName = AccessName.from(result.getString("display_name")),
        requestedAt = result.getTimestamp("requested_at").toInstant(),
    )
}
