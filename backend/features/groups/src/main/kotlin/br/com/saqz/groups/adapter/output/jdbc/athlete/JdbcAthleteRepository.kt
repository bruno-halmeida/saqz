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

class JdbcAthleteRepository(
    dataSource: DataSource,
) : AthleteRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun find(groupId: UUID, userId: UUID): AthleteMembership? = jdbc.sql(
        """
        SELECT
            m.user_id,
            u.display_name,
            CASE WHEN g.owner_user_id = m.user_id THEN 'OWNER' ELSE m.role END AS role,
            m.position,
            m.secondary_position,
            m.level,
            m.preferred_side,
            m.height_cm,
            m.nickname,
            m.monthly_fee_cents,
            m.monthly_due_day,
            m.membership_type,
            m.active
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
                position = :position,
                secondary_position = :secondaryPosition,
                level = :level,
                preferred_side = :preferredSide,
                height_cm = :heightCm,
                updated_at = now()
            WHERE group_id = :groupId AND user_id = :userId
              AND EXISTS (
                  SELECT 1 FROM access_groups g
                  WHERE g.id = :groupId AND g.deleted_at IS NULL
              )
            RETURNING user_id, group_id, role, position, secondary_position, level,
                      preferred_side, height_cm, nickname, monthly_fee_cents,
                      monthly_due_day, membership_type, active
        )
        SELECT
            updated.user_id,
            u.display_name,
            CASE WHEN g.owner_user_id = updated.user_id THEN 'OWNER' ELSE updated.role END AS role,
            updated.position,
            updated.secondary_position,
            updated.level,
            updated.preferred_side,
            updated.height_cm,
            updated.nickname,
            updated.monthly_fee_cents,
            updated.monthly_due_day,
            updated.membership_type,
            updated.active
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
                position = :position,
                secondary_position = :secondaryPosition,
                level = :level,
                preferred_side = :preferredSide,
                height_cm = :heightCm,
                monthly_fee_cents = :monthlyFeeCents,
                monthly_due_day = :monthlyDueDay,
                membership_type = :membershipType,
                active = :active,
                updated_at = now()
            WHERE group_id = :groupId AND user_id = :userId
              AND EXISTS (
                  SELECT 1 FROM access_groups g
                  WHERE g.id = :groupId AND g.deleted_at IS NULL
              )
            RETURNING user_id, group_id, role, position, secondary_position, level,
                      preferred_side, height_cm, nickname, monthly_fee_cents,
                      monthly_due_day, membership_type, active
        )
        SELECT
            updated.user_id,
            u.display_name,
            CASE WHEN g.owner_user_id = updated.user_id THEN 'OWNER' ELSE updated.role END AS role,
            updated.position,
            updated.secondary_position,
            updated.level,
            updated.preferred_side,
            updated.height_cm,
            updated.nickname,
            updated.monthly_fee_cents,
            updated.monthly_due_day,
            updated.membership_type,
            updated.active
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
            INSERT INTO group_membership_removals (group_id, user_id, removed_at)
            SELECT :groupId, :userId, now()
            FROM access_groups
            WHERE id = :groupId AND deleted_at IS NULL
            ON CONFLICT (group_id, user_id) DO UPDATE SET removed_at = now()
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
