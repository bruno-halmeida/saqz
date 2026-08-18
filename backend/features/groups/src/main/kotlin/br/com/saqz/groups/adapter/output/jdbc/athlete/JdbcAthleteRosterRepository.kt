package br.com.saqz.groups.adapter.output.jdbc.athlete

import br.com.saqz.groups.application.athlete.AthleteRosterEntry
import br.com.saqz.groups.application.athlete.AthleteRosterFilter
import br.com.saqz.groups.application.athlete.AthleteRosterRepository
import br.com.saqz.groups.application.athlete.FinancialStatus
import br.com.saqz.groups.application.athlete.OwnAthleteMembership
import br.com.saqz.groups.application.athlete.OwnAthleteProfile
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.AthleteLevel
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.AthletePosition
import br.com.saqz.groups.domain.AthletePreferredSide
import br.com.saqz.groups.domain.GroupRole
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.sql.Types
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource

// ponytail: no `unaccent` extension dependency; folds the handful of
// Portuguese accented letters actually used in names via translate().
private const val FOLD_FROM = "ÀÁÂÃÄàáâãäÈÉÊËèéêëÌÍÎÏìíîïÒÓÔÕÖòóôõöÙÚÛÜùúûüÇç"
private const val FOLD_TO = "AAAAAaaaaaEEEEeeeeIIIIiiiiOOOOOoooooUUUUuuuuCc"

class JdbcAthleteRosterRepository(
    dataSource: DataSource,
) : AthleteRosterRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun list(actorId: UUID, groupId: UUID, filter: AthleteRosterFilter): List<AthleteRosterEntry> {
        val conditions = buildList {
            add("m.group_id = :groupId")
            if (!filter.includeInactive) add("m.active = true")
            if (filter.membershipType != null) add("m.membership_type = :membershipType")
            if (filter.position != null) add("m.position = :position")
            if (!filter.search.isNullOrBlank()) {
                add("${fold("coalesce(u.nickname, u.display_name)")} LIKE '%' || ${fold(":search")} || '%'")
            }
        }
        var spec = jdbc.sql(
            """
            SELECT m.user_id,
                   coalesce(u.nickname, u.display_name) AS display_name,
                   CASE
                       WHEN m.user_id = :actorId
                           OR u.phone_visibility = 'EVERYONE'
                           OR (
                               u.phone_visibility = 'ADMINS'
                               AND (
                                   groups.owner_user_id = :actorId
                                   OR EXISTS (
                                       SELECT 1
                                       FROM group_memberships actor_membership
                                       WHERE actor_membership.group_id = :groupId
                                         AND actor_membership.user_id = :actorId
                                         AND actor_membership.role = 'ADMIN'
                                   )
                               )
                           )
                       THEN u.phone
                       ELSE NULL
                   END AS phone,
                   m.position, m.secondary_position, m.level, m.preferred_side,
                   m.height_cm, m.nickname, m.monthly_fee_cents, m.monthly_due_day,
                   m.membership_type, m.active, m.created_at
            FROM group_memberships m
            JOIN access_users u ON u.id = m.user_id
            JOIN access_groups groups ON groups.id = m.group_id AND groups.deleted_at IS NULL
            WHERE ${conditions.joinToString(" AND ")}
            ORDER BY coalesce(u.nickname, u.display_name), m.user_id
            """.trimIndent(),
        ).param("actorId", actorId).param("groupId", groupId)
        if (filter.membershipType != null) spec = spec.param("membershipType", filter.membershipType.name, Types.OTHER)
        if (filter.position != null) spec = spec.param("position", filter.position.name, Types.OTHER)
        if (!filter.search.isNullOrBlank()) spec = spec.param("search", filter.search)
        val rows = spec.query(::mapRosterRow).list()

        val monthEnd = YearMonth.from(LocalDate.now(groupZoneId(groupId))).atEndOfMonth()
        val pending = runCatching { pendingChargeMemberIds(groupId, monthEnd) }

        val withStatus = rows.map { row ->
            val status = pending.fold(
                onSuccess = { if (row.userId in it) FinancialStatus.PENDENTE else FinancialStatus.EM_DIA },
                onFailure = { FinancialStatus.DESCONHECIDO },
            )
            AthleteRosterEntry(
                userId = row.userId,
                displayName = row.displayName,
                phone = row.phone,
                position = row.position,
                membershipType = row.membershipType,
                active = row.active,
                financialStatus = status,
                nickname = row.nickname,
                secondaryPosition = row.secondaryPosition,
                level = row.level,
                preferredSide = row.preferredSide,
                heightCm = row.heightCm,
                monthlyFeeCents = row.monthlyFeeCents,
                monthlyDueDay = row.monthlyDueDay,
                joinedAt = row.joinedAt,
            )
        }
        return if (filter.financialStatus == null) withStatus else withStatus.filter { it.financialStatus == filter.financialStatus }
    }

    override fun findOwnProfile(actor: UUID): OwnAthleteProfile? {
        val user = jdbc.sql("SELECT display_name, phone FROM access_users WHERE id = :actor")
            .param("actor", actor)
            .query { rs, _ -> rs.getString("display_name") to rs.getString("phone") }
            .optional()
            .orElse(null) ?: return null

        val memberships = jdbc.sql(
            """
            SELECT
                m.group_id,
                g.name AS group_name,
                CASE WHEN g.owner_user_id = m.user_id THEN 'OWNER' ELSE m.role::text END AS role,
                m.position,
                m.secondary_position,
                m.level,
                m.preferred_side,
                m.height_cm,
                m.nickname,
                m.monthly_fee_cents,
                m.monthly_due_day,
                m.membership_type,
                m.active,
                m.created_at
            FROM group_memberships m
            JOIN access_groups g ON g.id = m.group_id
                AND g.deleted_at IS NULL
            WHERE m.user_id = :actor
            ORDER BY g.name, m.group_id
            """.trimIndent(),
        )
            .param("actor", actor)
            .query { rs, _ ->
                OwnAthleteMembership(
                    groupId = rs.getObject("group_id", UUID::class.java),
                    groupName = AccessName.from(rs.getString("group_name")),
                    role = GroupRole.valueOf(rs.getString("role")),
                    position = rs.getString("position")?.let(AthletePosition::valueOf),
                    nickname = rs.getString("nickname"),
                    secondaryPosition = rs.getString("secondary_position")?.let(AthletePosition::valueOf),
                    level = rs.getString("level")?.let(AthleteLevel::valueOf),
                    preferredSide = rs.getString("preferred_side")?.let(AthletePreferredSide::valueOf),
                    heightCm = rs.getNullableInt("height_cm"),
                    monthlyFeeCents = rs.getNullableLong("monthly_fee_cents"),
                    monthlyDueDay = rs.getNullableInt("monthly_due_day"),
                    membershipType = AthleteMembershipType.valueOf(rs.getString("membership_type")),
                    active = rs.getBoolean("active"),
                    joinedAt = rs.getTimestamp("created_at").toInstant(),
                )
            }
            .list()

        return OwnAthleteProfile(
            userId = actor,
            displayName = AccessName.from(user.first),
            phone = user.second,
            memberships = memberships,
        )
    }

    private fun fold(expr: String) = "lower(translate($expr, '$FOLD_FROM', '$FOLD_TO'))"

    private fun groupZoneId(groupId: UUID): ZoneId = jdbc.sql(
        "SELECT time_zone FROM access_groups WHERE id = :groupId AND deleted_at IS NULL",
    )
        .param("groupId", groupId)
        .query(String::class.java)
        .optional()
        .map(ZoneId::of)
        .orElse(ZoneOffset.UTC)

    private fun pendingChargeMemberIds(groupId: UUID, monthEnd: LocalDate): Set<UUID> = jdbc.sql(
        """
        SELECT DISTINCT charges.member_user_id
        FROM group_charges charges
        JOIN access_groups groups
            ON groups.id = charges.group_id
            AND groups.deleted_at IS NULL
        WHERE charges.group_id = :groupId
          AND charges.status = 'PENDING'
          AND charges.due_date <= :monthEnd
        """.trimIndent(),
    )
        .param("groupId", groupId)
        .param("monthEnd", monthEnd)
        .query { rs, _ -> rs.getObject("member_user_id", UUID::class.java) }
        .list()
        .toSet()

    private fun mapRosterRow(rs: ResultSet, rowNumber: Int) = RosterRow(
        userId = rs.getObject("user_id", UUID::class.java),
        displayName = AccessName.from(rs.getString("display_name")),
        phone = rs.getString("phone"),
        position = rs.getString("position")?.let(AthletePosition::valueOf),
        nickname = rs.getString("nickname"),
        secondaryPosition = rs.getString("secondary_position")?.let(AthletePosition::valueOf),
        level = rs.getString("level")?.let(AthleteLevel::valueOf),
        preferredSide = rs.getString("preferred_side")?.let(AthletePreferredSide::valueOf),
        heightCm = rs.getNullableInt("height_cm"),
        monthlyFeeCents = rs.getNullableLong("monthly_fee_cents"),
        monthlyDueDay = rs.getNullableInt("monthly_due_day"),
        membershipType = AthleteMembershipType.valueOf(rs.getString("membership_type")),
        active = rs.getBoolean("active"),
        joinedAt = rs.getTimestamp("created_at").toInstant(),
    )

    private data class RosterRow(
        val userId: UUID,
        val displayName: AccessName,
        val phone: String?,
        val position: AthletePosition?,
        val nickname: String?,
        val secondaryPosition: AthletePosition?,
        val level: AthleteLevel?,
        val preferredSide: AthletePreferredSide?,
        val heightCm: Int?,
        val monthlyFeeCents: Long?,
        val monthlyDueDay: Int?,
        val membershipType: AthleteMembershipType,
        val active: Boolean,
        val joinedAt: Instant,
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
