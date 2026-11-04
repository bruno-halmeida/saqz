package br.com.saqz.groups.adapter.output.jdbc.whatsapp

import br.com.saqz.groups.application.whatsapp.AdminContactTarget
import br.com.saqz.groups.application.whatsapp.AdminContactTargets
import br.com.saqz.groups.application.whatsapp.SaqzAccountLink
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import javax.sql.DataSource

/** Lê contas e grava em `admin_user_contacts` (DEB-162) direto pelo banco, como o diretório de grupos do adm já faz. */
class JdbcAdminContactTargets(dataSource: DataSource) : AdminContactTargets {
    private val jdbc = JdbcClient.create(dataSource)

    override fun find(userId: UUID): AdminContactTarget? = jdbc.sql(FIND)
        .param("id", userId)
        .query { row, _ ->
            AdminContactTarget(
                userId = row.getObject("id", UUID::class.java),
                phone = row.getString("phone"),
                doNotContact = row.getString("last_result") == DO_NOT_CONTACT,
            )
        }
        .optional().orElse(null)

    override fun byPhone(phones: Collection<String>): Map<String, SaqzAccountLink> {
        if (phones.isEmpty()) return emptyMap()
        return jdbc.sql(BY_PHONE)
            .param("phones", phones.toList())
            .query { row, _ -> row.getString("phone") to SaqzAccountLink(row.getObject("id", UUID::class.java), row.getString("display_name")) }
            .list()
            .groupBy({ it.first }, { it.second })
            .filterValues { it.size == 1 }
            .mapValues { it.value.single() }
    }

    override fun recordSent(userId: UUID, adminId: UUID, requestId: UUID, text: String) {
        jdbc.sql(RECORD)
            .param("contactId", UUID.randomUUID())
            .param("userId", userId)
            .param("authorId", adminId)
            .param("requestId", requestId)
            .param("note", note(text))
            .update()
    }

    internal companion object {
        const val DO_NOT_CONTACT = "DO_NOT_CONTACT"
        const val NOTE_MAX = 1000

        /** A observação aceita até 1.000 caracteres, sem espaço nas pontas; a mensagem pode ter 2.000. */
        fun note(text: String): String {
            val trimmed = text.trim()
            return if (trimmed.length <= NOTE_MAX) trimmed else trimmed.take(NOTE_MAX - 1).trimEnd() + "…"
        }

        const val FIND = """
            SELECT u.id, u.phone,
                   (SELECT c.result FROM admin_user_contacts c WHERE c.user_id = u.id
                     ORDER BY c.created_at DESC, c.id LIMIT 1) AS last_result
            FROM access_users u
            WHERE u.id = :id AND u.deleted_at IS NULL
        """

        const val BY_PHONE = """
            SELECT u.id, u.display_name, u.phone
            FROM access_users u
            WHERE u.deleted_at IS NULL AND u.phone IN (:phones)
        """

        const val RECORD = """
            INSERT INTO admin_user_contacts (id, user_id, result, note, created_by, request_id)
            SELECT :contactId, u.id, 'SAQZ_MESSAGE', :note, :authorId, :requestId
            FROM access_users u
            WHERE u.id = :userId AND u.deleted_at IS NULL
            ON CONFLICT (request_id) DO NOTHING
        """
    }
}
