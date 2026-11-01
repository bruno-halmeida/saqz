package br.com.saqz.groups.adapter.output.jdbc.whatsapp

import br.com.saqz.groups.application.whatsapp.AdminWhatsAppBindings
import br.com.saqz.groups.application.whatsapp.SaqzGroupLink
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import javax.sql.DataSource

class JdbcAdminWhatsAppBindings(dataSource: DataSource) : AdminWhatsAppBindings {
    private val jdbc = JdbcClient.create(dataSource)

    override fun activeByJid(): Map<String, SaqzGroupLink> = jdbc.sql(ACTIVE)
        .query { row, _ ->
            row.getString("whatsapp_jid") to SaqzGroupLink(
                row.getObject("group_id", UUID::class.java),
                row.getString("name"),
                row.getObject("broken_at") != null,
            )
        }
        .list().toMap()

    private companion object {
        const val ACTIVE = """
            SELECT b.whatsapp_jid, b.group_id, g.name, b.broken_at
            FROM group_whatsapp_bindings b
            JOIN access_groups g ON g.id = b.group_id AND g.deleted_at IS NULL
            WHERE b.enabled
        """
    }
}
