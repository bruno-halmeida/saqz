package br.com.saqz.access.adapter.output.jdbc.admin

import br.com.saqz.access.application.admin.PlatformAdminLookup
import br.com.saqz.access.application.admin.PlatformAdminView
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import javax.sql.DataSource

class JdbcPlatformAdminRepository(
    dataSource: DataSource,
) : PlatformAdminLookup {
    private val jdbc = JdbcClient.create(dataSource)

    override fun findBySubject(subject: String): PlatformAdminView? = jdbc.sql(
        """
        SELECT id, email, display_name
        FROM access_users
        WHERE firebase_subject = :subject AND platform_admin
          AND deleted_at IS NULL AND suspended_at IS NULL
        """.trimIndent(),
    )
        .param("subject", subject)
        .query { rs, _ ->
            PlatformAdminView(
                userId = rs.getObject("id", UUID::class.java),
                email = rs.getString("email"),
                displayName = rs.getString("display_name"),
            )
        }
        .optional()
        .orElse(null)
}
