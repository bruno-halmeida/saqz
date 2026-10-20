package br.com.saqz.groups.adapter.output.jdbc.monthlyinvite

import br.com.saqz.groups.application.monthlyinvite.MonthlyInviteMemberMatch
import br.com.saqz.groups.application.monthlyinvite.MonthlyInviteOccupancy
import br.com.saqz.groups.application.monthlyinvite.MonthlyInviteRepository
import br.com.saqz.groups.application.monthlyinvite.MonthlyInviteRetentionRepository
import br.com.saqz.groups.application.monthlyinvite.MonthlyInvitee
import br.com.saqz.groups.application.monthlyinvite.MonthlyInviteePhone
import br.com.saqz.groups.application.monthlyinvite.MonthlyInviteeStatus
import br.com.saqz.groups.application.monthlyinvite.NewMonthlyInvitee
import br.com.saqz.groups.domain.AthleteMembershipType
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class JdbcMonthlyInviteRepository(dataSource: DataSource) : MonthlyInviteRepository, MonthlyInviteRetentionRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun list(groupId: UUID): List<MonthlyInvitee> = jdbc.sql(
        "SELECT $COLUMNS FROM group_monthly_invitees WHERE group_id = :groupId ORDER BY created_at, id",
    )
        .param("groupId", groupId)
        .query { result, _ -> result.toInvitee() }
        .list()

    override fun find(groupId: UUID, id: UUID): MonthlyInvitee? = jdbc.sql(
        "SELECT $COLUMNS FROM group_monthly_invitees WHERE group_id = :groupId AND id = :id",
    )
        .param("groupId", groupId)
        .param("id", id)
        .query { result, _ -> result.toInvitee() }
        .optional()
        .orElse(null)

    override fun deleteMatchingMembers(groupId: UUID) {
        jdbc.sql(
            """
            DELETE FROM group_monthly_invitees invitees
            USING group_memberships memberships
            JOIN access_users users ON users.id = memberships.user_id
            WHERE invitees.group_id = :groupId
              AND memberships.group_id = invitees.group_id
              AND users.phone = invitees.phone
            """.trimIndent(),
        )
            .param("groupId", groupId)
            .update()
    }

    override fun membersByPhone(groupId: UUID, phones: Collection<String>): Map<String, MonthlyInviteMemberMatch> {
        if (phones.isEmpty()) return emptyMap()
        return jdbc.sql(
            """
            SELECT users.phone, users.id, users.display_name, memberships.membership_type::text AS membership_type
            FROM group_memberships memberships
            JOIN access_users users ON users.id = memberships.user_id AND users.deleted_at IS NULL
            WHERE memberships.group_id = :groupId AND users.phone IN (:phones)
            ORDER BY memberships.created_at
            """.trimIndent(),
        )
            .param("groupId", groupId)
            .param("phones", phones.toList())
            .query { result, _ ->
                result.getString("phone") to MonthlyInviteMemberMatch(
                    userId = result.getObject("id", UUID::class.java),
                    name = result.getString("display_name"),
                    membershipType = AthleteMembershipType.valueOf(result.getString("membership_type")),
                )
            }
            .list()
            .reversed()
            .toMap()
    }

    override fun invitedByPhone(groupId: UUID, phones: Collection<String>): Map<String, MonthlyInvitee> {
        if (phones.isEmpty()) return emptyMap()
        return jdbc.sql("SELECT $COLUMNS FROM group_monthly_invitees WHERE group_id = :groupId AND phone IN (:phones)")
            .param("groupId", groupId)
            .param("phones", phones.toList())
            .query { result, _ -> result.toInvitee() }
            .list()
            .associateBy { it.phone.value }
    }

    override fun hasRequest(groupId: UUID, requestId: UUID): Boolean = countRequest(groupId, requestId) > 0

    override fun countRequest(groupId: UUID, requestId: UUID): Int = jdbc.sql(
        "SELECT count(*)::int FROM group_monthly_invitees WHERE group_id = :groupId AND request_id = :requestId",
    )
        .param("groupId", groupId)
        .param("requestId", requestId)
        .query(Int::class.java)
        .single()

    override fun insert(
        groupId: UUID,
        people: List<NewMonthlyInvitee>,
        status: MonthlyInviteeStatus,
        createdByUserId: UUID,
        requestId: UUID,
        now: Instant,
    ) {
        // created_at cresce um microssegundo por pessoa: a ordem da lista é a ordem do lote.
        people.forEachIndexed { index, person ->
            jdbc.sql(
                """
                INSERT INTO group_monthly_invitees (id, group_id, name, phone, status, attempts, created_by_user_id, request_id, created_at)
                VALUES (:id, :groupId, :name, :phone, :status, 0, :createdBy, :requestId, :createdAt)
                """.trimIndent(),
            )
                .param("id", person.id)
                .param("groupId", groupId)
                .param("name", person.name)
                .param("phone", person.phone.value)
                .param("status", status.name)
                .param("createdBy", createdByUserId)
                .param("requestId", requestId)
                .param("createdAt", Timestamp.from(now.plusNanos(index * 1_000L)))
                .update()
        }
    }

    override fun requeue(groupId: UUID, ids: List<UUID>, status: MonthlyInviteeStatus) {
        if (ids.isEmpty()) return
        jdbc.sql(
            """
            UPDATE group_monthly_invitees SET status = :status, attempts = 0, next_attempt_at = NULL
            WHERE group_id = :groupId AND id IN (:ids)
            """.trimIndent(),
        )
            .param("groupId", groupId)
            .param("ids", ids)
            .param("status", status.name)
            .update()
    }

    override fun delete(groupId: UUID, id: UUID): Boolean = jdbc.sql(
        "DELETE FROM group_monthly_invitees WHERE group_id = :groupId AND id = :id",
    )
        .param("groupId", groupId)
        .param("id", id)
        .update() == 1

    override fun occupancy(groupId: UUID): MonthlyInviteOccupancy? = jdbc.sql(
        """
        SELECT groups.owner_user_id,
               (SELECT count(*)::int FROM group_memberships m
                WHERE m.group_id = groups.id AND m.user_id <> groups.owner_user_id) AS member_count
        FROM access_groups groups
        WHERE groups.id = :groupId AND groups.deleted_at IS NULL
        """.trimIndent(),
    )
        .param("groupId", groupId)
        .query { result, _ ->
            MonthlyInviteOccupancy(result.getObject("owner_user_id", UUID::class.java), result.getInt("member_count"))
        }
        .optional()
        .orElse(null)

    override fun deleteInviteesOfLinksDeadBefore(cutoff: Instant): Int = jdbc.sql(
        """
        DELETE FROM group_monthly_invitees invitees
        USING group_monthly_invite_links links
        WHERE links.group_id = invitees.group_id
          AND (links.invalidated_at < :cutoff OR (links.invalidated_at IS NULL AND links.expires_at < :cutoff))
        """.trimIndent(),
    )
        .param("cutoff", Timestamp.from(cutoff))
        .update()

    private fun ResultSet.toInvitee() = MonthlyInvitee(
        id = getObject("id", UUID::class.java),
        groupId = getObject("group_id", UUID::class.java),
        name = getString("name"),
        phone = MonthlyInviteePhone.fromStored(getString("phone")),
        status = MonthlyInviteeStatus.valueOf(getString("status")),
        attempts = getInt("attempts"),
        sentAt = getTimestamp("sent_at")?.toInstant(),
        createdByUserId = getObject("created_by_user_id", UUID::class.java),
        createdAt = getTimestamp("created_at").toInstant(),
    )

    private companion object {
        const val COLUMNS = "id, group_id, name, phone, status, attempts, sent_at, created_by_user_id, created_at"
    }
}
