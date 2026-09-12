package br.com.saqz.groups.adapter.output.jdbc.plan

import br.com.saqz.sharedkernel.subscription.OwnerGroupHistory
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import javax.sql.DataSource

class JdbcOwnerGroupHistory(dataSource: DataSource) : OwnerGroupHistory {
    private val jdbc = JdbcClient.create(dataSource)
    override fun hasEverOwnedGroup(ownerId: UUID): Boolean = jdbc.sql(
        "SELECT EXISTS (SELECT 1 FROM access_groups WHERE owner_user_id = :owner)",
    ).param("owner", ownerId).query(Boolean::class.java).single()
}
