package br.com.saqz.groups.adapter.output.jdbc.group

import br.com.saqz.sharedkernel.group.GroupFinancialSetup
import br.com.saqz.sharedkernel.group.GroupFinancialSetupLookup
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import javax.sql.DataSource

class JdbcGroupFinancialSetupLookup(dataSource: DataSource) : GroupFinancialSetupLookup {
    private val jdbc = JdbcClient.create(dataSource)
    override fun lockActive(groupId: UUID): GroupFinancialSetup? = jdbc.sql("""
        SELECT id,owner_user_id,default_game_fee_cents,monthly_fee_cents FROM access_groups
        WHERE id=:id AND deleted_at IS NULL FOR SHARE
    """.trimIndent()).param("id", groupId).query { rs, _ ->
        GroupFinancialSetup(groupId, rs.getObject("owner_user_id", UUID::class.java),
            rs.getObject("default_game_fee_cents", Long::class.javaObjectType),
            rs.getObject("monthly_fee_cents", Long::class.javaObjectType))
    }.optional().orElse(null)
}
