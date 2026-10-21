package br.com.saqz.groups.adapter.output.jdbc.athlete

import br.com.saqz.groups.application.athlete.AthleteMembership
import br.com.saqz.groups.application.athlete.AthleteRepository
import br.com.saqz.groups.application.athlete.UpdateAthleteCommand
import br.com.saqz.groups.application.athlete.UpdateOwnAthleteProfileCommand
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.AthleteLevel
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.AthletePosition
import br.com.saqz.groups.domain.AthletePreferredSide
import br.com.saqz.groups.domain.GroupRole
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.sql.Types
import java.util.UUID
import javax.sql.DataSource

// Campo que a modalidade atual do grupo não usa fica guardado, escondido (MEM-004): a resposta não o mostra e
// salvar o perfil não o sobrescreve, para que voltar à modalidade original traga o valor de volta.
private const val COURT_NOW =
    "(SELECT modality FROM access_groups WHERE id = :groupId) = 'COURT_VOLLEYBALL'"
private const val SIDE_NOW =
    "(SELECT modality FROM access_groups WHERE id = :groupId) IN ('BEACH_VOLLEYBALL', 'FOOTVOLLEY')"

class JdbcAthleteRepository(
    dataSource: DataSource,
) : AthleteRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun find(groupId: UUID, userId: UUID): AthleteMembership? = jdbc.sql(
        """
        SELECT
            m.user_id,
            u.display_name,
            CASE WHEN g.owner_user_id = m.user_id THEN 'OWNER' ELSE m.role::text END AS role,
            CASE WHEN g.modality = 'COURT_VOLLEYBALL' THEN m.position END AS position,
            CASE WHEN g.modality = 'COURT_VOLLEYBALL' THEN m.secondary_position END AS secondary_position,
            m.level,
            CASE WHEN g.modality IN ('BEACH_VOLLEYBALL', 'FOOTVOLLEY') THEN m.preferred_side END AS preferred_side,
            CASE WHEN g.modality = 'COURT_VOLLEYBALL' THEN m.height_cm END AS height_cm,
            m.nickname,
            m.monthly_fee_cents,
            m.monthly_due_day,
            m.membership_type,
            m.active,
            m.monthly_fee_exempt
        FROM group_memberships m
        JOIN access_users u ON u.id = m.user_id
        JOIN access_groups g ON g.id = m.group_id AND g.deleted_at IS NULL
        WHERE m.group_id = :groupId AND m.user_id = :userId
        """.trimIndent(),
    )
        .param("groupId", groupId)
        .param("userId", userId)
        .query(::mapAthlete)
        .optional()
        .orElse(null)

    override fun updateOwn(command: UpdateOwnAthleteProfileCommand): AthleteMembership = jdbc.sql(
        """
        WITH updated AS (
            UPDATE group_memberships
            SET nickname = :nickname,
                position = CASE WHEN $COURT_NOW THEN :position ELSE group_memberships.position END,
                secondary_position = CASE WHEN $COURT_NOW THEN :secondaryPosition ELSE group_memberships.secondary_position END,
                level = :level,
                preferred_side = CASE WHEN $SIDE_NOW THEN :preferredSide ELSE group_memberships.preferred_side END,
                height_cm = CASE WHEN $COURT_NOW THEN :heightCm ELSE group_memberships.height_cm END,
                athlete_registration_completed = true,
                updated_at = now()
            WHERE group_id = :groupId AND user_id = :userId
              AND EXISTS (
                  SELECT 1 FROM access_groups g
                  WHERE g.id = :groupId AND g.deleted_at IS NULL
              )
            RETURNING user_id, group_id, role, position, secondary_position, level,
                      preferred_side, height_cm, nickname, monthly_fee_cents,
                      monthly_due_day, membership_type, active, monthly_fee_exempt
        )
        SELECT
            updated.user_id,
            u.display_name,
            CASE WHEN g.owner_user_id = updated.user_id THEN 'OWNER' ELSE updated.role::text END AS role,
            CASE WHEN g.modality = 'COURT_VOLLEYBALL' THEN updated.position END AS position,
            CASE WHEN g.modality = 'COURT_VOLLEYBALL' THEN updated.secondary_position END AS secondary_position,
            updated.level,
            CASE WHEN g.modality IN ('BEACH_VOLLEYBALL', 'FOOTVOLLEY') THEN updated.preferred_side END AS preferred_side,
            CASE WHEN g.modality = 'COURT_VOLLEYBALL' THEN updated.height_cm END AS height_cm,
            updated.nickname,
            updated.monthly_fee_cents,
            updated.monthly_due_day,
            updated.membership_type,
            updated.active,
            updated.monthly_fee_exempt
        FROM updated
        JOIN access_users u ON u.id = updated.user_id
        JOIN access_groups g ON g.id = updated.group_id AND g.deleted_at IS NULL
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
        .query(::mapAthlete)
        .single()

    override fun updatePosition(groupId: UUID, userId: UUID, position: AthletePosition?): AthleteMembership =
        updateOwn(
            UpdateOwnAthleteProfileCommand(
                groupId = groupId,
                userId = userId,
                nickname = null,
                position = position,
                secondaryPosition = null,
                level = null,
                preferredSide = null,
                heightCm = null,
            ),
        )

    override fun update(command: UpdateAthleteCommand): AthleteMembership = jdbc.sql(
        """
        WITH updated AS (
            UPDATE group_memberships
            SET nickname = :nickname,
                position = CASE WHEN $COURT_NOW THEN :position ELSE group_memberships.position END,
                secondary_position = CASE WHEN $COURT_NOW THEN :secondaryPosition ELSE group_memberships.secondary_position END,
                level = :level,
                preferred_side = CASE WHEN $SIDE_NOW THEN :preferredSide ELSE group_memberships.preferred_side END,
                height_cm = CASE WHEN $COURT_NOW THEN :heightCm ELSE group_memberships.height_cm END,
                monthly_fee_cents = :monthlyFeeCents,
                monthly_due_day = :monthlyDueDay,
                membership_type = :membershipType,
                active = :active,
                monthly_fee_exempt = :monthlyFeeExempt,
                updated_at = now()
            WHERE group_id = :groupId AND user_id = :userId
              AND EXISTS (
                  SELECT 1 FROM access_groups g
                  WHERE g.id = :groupId AND g.deleted_at IS NULL
              )
            RETURNING user_id, group_id, role, position, secondary_position, level,
                      preferred_side, height_cm, nickname, monthly_fee_cents,
                      monthly_due_day, membership_type, active, monthly_fee_exempt
        )
        SELECT
            updated.user_id,
            u.display_name,
            CASE WHEN g.owner_user_id = updated.user_id THEN 'OWNER' ELSE updated.role::text END AS role,
            CASE WHEN g.modality = 'COURT_VOLLEYBALL' THEN updated.position END AS position,
            CASE WHEN g.modality = 'COURT_VOLLEYBALL' THEN updated.secondary_position END AS secondary_position,
            updated.level,
            CASE WHEN g.modality IN ('BEACH_VOLLEYBALL', 'FOOTVOLLEY') THEN updated.preferred_side END AS preferred_side,
            CASE WHEN g.modality = 'COURT_VOLLEYBALL' THEN updated.height_cm END AS height_cm,
            updated.nickname,
            updated.monthly_fee_cents,
            updated.monthly_due_day,
            updated.membership_type,
            updated.active,
            updated.monthly_fee_exempt
        FROM updated
        JOIN access_users u ON u.id = updated.user_id
        JOIN access_groups g ON g.id = updated.group_id AND g.deleted_at IS NULL
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
        .param("monthlyFeeCents", command.monthlyFeeCents, Types.BIGINT)
        .param("monthlyDueDay", command.monthlyDueDay, Types.SMALLINT)
        .param("membershipType", command.membershipType.name, Types.OTHER)
        .param("active", command.active)
        .param("monthlyFeeExempt", command.monthlyFeeExempt ?: false)
        .query(::mapAthlete)
        .single()

    override fun remove(groupId: UUID, userId: UUID) {
        val activeGroup = jdbc.sql(
            "SELECT id FROM access_groups WHERE id = :groupId AND deleted_at IS NULL FOR UPDATE",
        )
            .param("groupId", groupId)
            .query(UUID::class.java)
            .optional()
            .orElse(null)
        check(activeGroup != null) { "Grupo excluído ou inexistente" }
        jdbc.sql(
            """
            INSERT INTO group_membership_removals (group_id, user_id, removed_at, requires_approval)
            SELECT :groupId, :userId, now(), true
            FROM access_groups
            WHERE id = :groupId AND deleted_at IS NULL
            ON CONFLICT (group_id, user_id) DO UPDATE SET removed_at = now(), requires_approval = true
            """.trimIndent(),
        )
            .param("groupId", groupId)
            .param("userId", userId)
            .update()
        jdbc.sql(
            "DELETE FROM group_memberships " +
                "WHERE group_id = :groupId AND user_id = :userId " +
                "AND EXISTS (SELECT 1 FROM access_groups WHERE id = :groupId AND deleted_at IS NULL)",
        )
            .param("groupId", groupId)
            .param("userId", userId)
            .update()
    }

    private fun mapAthlete(rs: ResultSet, rowNumber: Int) = AthleteMembership(
        userId = rs.getObject("user_id", UUID::class.java),
        displayName = AccessName.from(rs.getString("display_name")),
        role = GroupRole.valueOf(rs.getString("role")),
        position = rs.getString("position")?.let(AthletePosition::valueOf),
        secondaryPosition = rs.getString("secondary_position")?.let(AthletePosition::valueOf),
        level = rs.getString("level")?.let(AthleteLevel::valueOf),
        preferredSide = rs.getString("preferred_side")?.let(AthletePreferredSide::valueOf),
        heightCm = rs.getNullableInt("height_cm"),
        nickname = rs.getString("nickname"),
        monthlyFeeCents = rs.getNullableLong("monthly_fee_cents"),
        monthlyDueDay = rs.getNullableInt("monthly_due_day"),
        membershipType = AthleteMembershipType.valueOf(rs.getString("membership_type")),
        active = rs.getBoolean("active"),
        monthlyFeeExempt = rs.getBoolean("monthly_fee_exempt"),
    )

    private fun ResultSet.getNullableInt(column: String): Int? {
        val value = getInt(column)
        return if (wasNull()) null else value
    }

    private fun ResultSet.getNullableLong(column: String): Long? {
        val value = getLong(column)
        return if (wasNull()) null else value
    }
}
