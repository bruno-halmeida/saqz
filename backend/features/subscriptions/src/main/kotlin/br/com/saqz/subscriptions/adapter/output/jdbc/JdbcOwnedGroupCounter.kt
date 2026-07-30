package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.OwnedGroupCounter
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import javax.sql.DataSource

class JdbcOwnedGroupCounter(
    dataSource: DataSource,
) : OwnedGroupCounter {
    private val jdbc = JdbcClient.create(dataSource)

    override fun countOwnedGroups(ownerUserId: UUID): Int =
        jdbc.sql("SELECT count(*)::int FROM access_groups WHERE owner_user_id = :ownerUserId")
            .param("ownerUserId", ownerUserId)
            .query(Int::class.java)
            .single()
}
