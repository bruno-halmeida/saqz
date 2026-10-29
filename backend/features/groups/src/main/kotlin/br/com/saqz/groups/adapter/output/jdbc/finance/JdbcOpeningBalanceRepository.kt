package br.com.saqz.groups.adapter.output.jdbc.finance

import br.com.saqz.groups.application.finance.openingbalance.OpeningBalance
import br.com.saqz.groups.application.finance.openingbalance.OpeningBalanceRepository
import br.com.saqz.groups.domain.GroupRole
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class JdbcOpeningBalanceRepository(dataSource: DataSource) : OpeningBalanceRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun role(actorId: UUID, groupId: UUID): GroupRole? = jdbc.sql(
        """
        SELECT CASE WHEN g.owner_user_id = :actor THEN 'OWNER' ELSE m.role::text END
        FROM access_groups g LEFT JOIN group_memberships m ON m.group_id = g.id AND m.user_id = :actor
        WHERE g.id = :group AND g.deleted_at IS NULL AND (g.owner_user_id = :actor OR m.user_id IS NOT NULL)
        """.trimIndent(),
    ).param("actor", actorId).param("group", groupId).query(String::class.java).optional().map(GroupRole::valueOf).orElse(null)

    override fun find(groupId: UUID): OpeningBalance? = jdbc.sql(
        "SELECT opening_balance_cents, opening_balance_version FROM access_groups WHERE id = :group AND deleted_at IS NULL",
    ).param("group", groupId).query { rs, _ ->
        OpeningBalance(rs.getObject("opening_balance_cents", java.lang.Long::class.java)?.toLong(), rs.getLong("opening_balance_version"))
    }.optional().orElse(null)

    override fun update(groupId: UUID, expectedVersion: Long, amountCents: Long, actorId: UUID, at: Instant): OpeningBalance? {
        val old = jdbc.sql(
            "SELECT opening_balance_cents FROM access_groups WHERE id = :group AND deleted_at IS NULL AND opening_balance_version = :version FOR UPDATE",
        ).param("group", groupId).param("version", expectedVersion)
            .query { rs, _ -> listOf(rs.getObject(1, java.lang.Long::class.java)?.toLong()) }.optional().orElse(null) ?: return null
        jdbc.sql(
            "UPDATE access_groups SET opening_balance_cents = :amount, opening_balance_version = opening_balance_version + 1 WHERE id = :group",
        ).param("amount", amountCents).param("group", groupId).update()
        jdbc.sql(
            """
            INSERT INTO group_opening_balance_events (id, group_id, actor_user_id, old_amount_cents, new_amount_cents, occurred_at)
            VALUES (:id, :group, :actor, :old, :new, :at)
            """.trimIndent(),
        ).param("id", UUID.randomUUID()).param("group", groupId).param("actor", actorId)
            .param("old", old.single(), java.sql.Types.BIGINT).param("new", amountCents).param("at", Timestamp.from(at)).update()
        return OpeningBalance(amountCents, expectedVersion + 1)
    }
}
