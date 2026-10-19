package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.ActiveCourtesy
import br.com.saqz.subscriptions.application.CourtesyCandidate
import br.com.saqz.subscriptions.application.PlanCourtesyRepository
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.PlanCourtesy
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class JdbcPlanCourtesyRepository(dataSource: DataSource) : PlanCourtesyRepository {
    private val jdbc = JdbcClient.create(dataSource)
    private val transaction = TransactionTemplate(DataSourceTransactionManager(dataSource))

    override fun findActive(ownerUserId: UUID, now: Instant): PlanCourtesy? = jdbc.sql(
        "SELECT $COLUMNS FROM plan_courtesies WHERE owner_user_id = :owner AND revoked_at IS NULL AND (ends_at IS NULL OR ends_at > :now)",
    ).param("owner", ownerUserId).param("now", Timestamp.from(now)).query { rs, _ -> rs.courtesy() }.optional().orElse(null)

    override fun history(ownerUserId: UUID): List<PlanCourtesy> = jdbc.sql(
        "SELECT $COLUMNS FROM plan_courtesies WHERE owner_user_id = :owner ORDER BY created_at DESC, id",
    ).param("owner", ownerUserId).query { rs, _ -> rs.courtesy() }.list()

    override fun userExists(userId: UUID): Boolean = jdbc.sql(
        "SELECT EXISTS (SELECT 1 FROM access_users WHERE id = :id AND deleted_at IS NULL)",
    ).param("id", userId).query(Boolean::class.java).single()

    override fun grant(courtesy: PlanCourtesy): PlanCourtesy {
        transaction.executeWithoutResult {
            // Serializa dois admins mexendo no mesmo dono; sem isso o índice parcial devolveria 500 para o segundo.
            jdbc.sql("SELECT id FROM access_users WHERE id = :owner FOR UPDATE").param("owner", courtesy.ownerUserId)
                .query(UUID::class.java).list()
            jdbc.sql(
                """
                UPDATE plan_courtesies SET revoked_at = :now, revoked_by = :by, revoked_reason = 'Substituída por outra cortesia'
                WHERE owner_user_id = :owner AND revoked_at IS NULL
                """.trimIndent(),
            ).param("now", Timestamp.from(courtesy.createdAt)).param("by", courtesy.grantedBy)
                .param("owner", courtesy.ownerUserId).update()
            jdbc.sql(
                """
                INSERT INTO plan_courtesies (id, owner_user_id, plan, ends_at, reason, granted_by, created_at)
                VALUES (:id, :owner, :plan::subscription_plan, :endsAt, :reason, :by, :createdAt)
                """.trimIndent(),
            ).param("id", courtesy.id).param("owner", courtesy.ownerUserId).param("plan", courtesy.plan.name)
                .param("endsAt", courtesy.endsAt?.let(Timestamp::from)).param("reason", courtesy.reason)
                .param("by", courtesy.grantedBy).param("createdAt", Timestamp.from(courtesy.createdAt)).update()
        }
        return courtesy
    }

    override fun revoke(ownerUserId: UUID, by: UUID, reason: String?, now: Instant): Boolean = jdbc.sql(
        """
        UPDATE plan_courtesies SET revoked_at = :now, revoked_by = :by, revoked_reason = :reason
        WHERE owner_user_id = :owner AND revoked_at IS NULL AND (ends_at IS NULL OR ends_at > :now)
        """.trimIndent(),
    ).param("now", Timestamp.from(now)).param("by", by).param("reason", reason).param("owner", ownerUserId).update() > 0

    override fun listActive(now: Instant): List<ActiveCourtesy> = jdbc.sql(
        """
        SELECT ${COLUMNS.split(", ").joinToString { "c.$it" }}, u.display_name, u.email
        FROM plan_courtesies c JOIN access_users u ON u.id = c.owner_user_id
        WHERE c.revoked_at IS NULL AND (c.ends_at IS NULL OR c.ends_at > :now)
        ORDER BY c.created_at DESC, c.id
        """.trimIndent(),
    ).param("now", Timestamp.from(now)).query { rs, _ ->
        ActiveCourtesy(rs.courtesy(), rs.getString("display_name"), rs.getString("email"))
    }.list()

    override fun eligibleOwners(now: Instant): List<CourtesyCandidate> = jdbc.sql(
        """
        SELECT u.id, u.display_name, u.email,
               (SELECT array_agg(g.name ORDER BY g.created_at) FROM access_groups g
                 WHERE g.owner_user_id = u.id AND g.deleted_at IS NULL) AS group_names,
               EXISTS (SELECT 1 FROM plan_courtesies c WHERE c.owner_user_id = u.id AND c.revoked_at IS NULL
                        AND (c.ends_at IS NULL OR c.ends_at > :now)) AS has_courtesy
        FROM access_users u
        WHERE u.deleted_at IS NULL AND u.suspended_at IS NULL
          AND EXISTS (SELECT 1 FROM access_groups g WHERE g.owner_user_id = u.id AND g.deleted_at IS NULL)
        ORDER BY u.display_name, u.id
        """.trimIndent(),
    ).param("now", Timestamp.from(now)).query { rs, _ ->
        CourtesyCandidate(
            ownerUserId = rs.getObject("id", UUID::class.java),
            displayName = rs.getString("display_name"),
            email = rs.getString("email"),
            groups = (rs.getArray("group_names")?.array as? Array<*>)?.map { it.toString() }.orEmpty(),
            effectivePlan = null,
            hasCourtesy = rs.getBoolean("has_courtesy"),
        )
    }.list()

    private fun ResultSet.courtesy() = PlanCourtesy(
        id = getObject("id", UUID::class.java),
        ownerUserId = getObject("owner_user_id", UUID::class.java),
        plan = Plan.valueOf(getString("plan")),
        endsAt = getTimestamp("ends_at")?.toInstant(),
        reason = getString("reason"),
        grantedBy = getObject("granted_by", UUID::class.java),
        createdAt = getTimestamp("created_at").toInstant(),
        revokedAt = getTimestamp("revoked_at")?.toInstant(),
        revokedBy = getObject("revoked_by", UUID::class.java),
        revokedReason = getString("revoked_reason"),
    )

    private companion object {
        const val COLUMNS = "id, owner_user_id, plan, ends_at, reason, granted_by, created_at, revoked_at, revoked_by, revoked_reason"
    }
}
