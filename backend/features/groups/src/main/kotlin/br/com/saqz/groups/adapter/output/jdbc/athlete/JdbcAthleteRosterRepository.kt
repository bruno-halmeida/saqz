package br.com.saqz.groups.adapter.output.jdbc.athlete

import br.com.saqz.groups.application.athlete.AthleteRosterEntry
import br.com.saqz.groups.application.athlete.AthleteRosterFilter
import br.com.saqz.groups.application.athlete.AthleteRosterRepository
import br.com.saqz.groups.application.athlete.FinancialStatus
import br.com.saqz.groups.application.athlete.OwnAthleteMembership
import br.com.saqz.groups.application.athlete.OwnAthleteProfile
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.AthletePosition
import br.com.saqz.groups.domain.GroupRole
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
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

    override fun list(groupId: UUID, filter: AthleteRosterFilter): List<AthleteRosterEntry> {
        val conditions = buildList {
            add("m.group_id = :groupId")
            if (!filter.includeInactive) add("m.active = true")
            if (filter.membershipType != null) add("m.membership_type = :membershipType")
            if (filter.position != null) add("m.position = :position")
            if (!filter.search.isNullOrBlank()) add("${fold("u.display_name")} LIKE '%' || ${fold(":search")} || '%'")
        }
        var spec = jdbc.sql(
            """
            SELECT m.user_id, u.display_name, u.phone, m.position, m.membership_type, m.active
            FROM group_memberships m
            JOIN access_users u ON u.id = m.user_id
            WHERE ${conditions.joinToString(" AND ")}
            ORDER BY u.display_name, m.user_id
            """.trimIndent(),
        ).param("groupId", groupId)
        if (filter.membershipType != null) spec = spec.param("membershipType", filter.membershipType.name)
        if (filter.position != null) spec = spec.param("position", filter.position.name)
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
                CASE WHEN g.owner_user_id = m.user_id THEN 'OWNER' ELSE m.role END AS role,
                m.position,
                m.membership_type,
                m.active
            FROM group_memberships m
            JOIN access_groups g ON g.id = m.group_id
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
                    membershipType = AthleteMembershipType.valueOf(rs.getString("membership_type")),
                    active = rs.getBoolean("active"),
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

    private fun groupZoneId(groupId: UUID): ZoneId = jdbc.sql("SELECT time_zone FROM access_groups WHERE id = :groupId")
        .param("groupId", groupId)
        .query(String::class.java)
        .optional()
        .map(ZoneId::of)
        .orElse(ZoneOffset.UTC)

    private fun pendingChargeMemberIds(groupId: UUID, monthEnd: LocalDate): Set<UUID> = jdbc.sql(
        """
        SELECT DISTINCT member_user_id FROM group_charges
        WHERE group_id = :groupId AND status = 'PENDING' AND due_date <= :monthEnd
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
        membershipType = AthleteMembershipType.valueOf(rs.getString("membership_type")),
        active = rs.getBoolean("active"),
    )

    private data class RosterRow(
        val userId: UUID,
        val displayName: AccessName,
        val phone: String?,
        val position: AthletePosition?,
        val membershipType: AthleteMembershipType,
        val active: Boolean,
    )
}
