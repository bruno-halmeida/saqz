package br.com.saqz.groups.adapter.output.jdbc.membership

import br.com.saqz.sharedkernel.group.GroupAdministrationDirectory
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import javax.sql.DataSource

class JdbcGroupAdministrationDirectory(dataSource: DataSource) : GroupAdministrationDirectory {
    private val jdbc = JdbcClient.create(dataSource)
    override fun ownerOf(groupId: UUID): UUID? = jdbc.sql("SELECT owner_user_id FROM access_groups WHERE id=:id")
        .param("id", groupId).query(UUID::class.java).optional().orElse(null)

    override fun isAdministrator(ownerUserId: UUID, userId: UUID): Boolean = jdbc.sql("""
        SELECT EXISTS(SELECT 1 FROM group_memberships m JOIN access_groups g ON g.id=m.group_id
            WHERE g.owner_user_id=:owner AND g.deleted_at IS NULL AND m.user_id=:user AND m.role='ADMIN')
    """.trimIndent()).param("owner", ownerUserId).param("user", userId).query(Boolean::class.java).single()
}
