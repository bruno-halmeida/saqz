package br.com.saqz.access.adapter.output.jdbc.session

import br.com.saqz.access.application.session.SessionMembership
import br.com.saqz.access.application.session.SessionRepository
import br.com.saqz.access.application.session.SessionUpsert
import br.com.saqz.access.application.session.SessionView
import br.com.saqz.access.application.session.UserAccount
import br.com.saqz.access.domain.AccessName
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import javax.sql.DataSource

class JdbcSessionRepository(
    dataSource: DataSource,
) : SessionRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun upsertAndLoad(command: SessionUpsert): SessionView {
        val userId = jdbc.sql(
            """
            INSERT INTO access_users (
                id, firebase_subject, email, email_verified, display_name, created_at, updated_at
            ) VALUES (
                :id, :subject, :email, true, :displayName, now(), now()
            )
            ON CONFLICT (firebase_subject) DO UPDATE SET
                email = EXCLUDED.email,
                email_verified = true,
                display_name = EXCLUDED.display_name,
                updated_at = now()
            RETURNING id
            """.trimIndent(),
        )
            .param("id", UUID.randomUUID())
            .param("subject", command.subject)
            .param("email", command.email)
            .param("displayName", command.displayName.value)
            .query(UUID::class.java)
            .single()

        val memberships = jdbc.sql(
            """
            SELECT group_id, group_name, role
            FROM (
                SELECT groups.id AS group_id, groups.name AS group_name, 'OWNER' AS role
                FROM access_groups groups
                WHERE groups.owner_user_id = :userId
                UNION ALL
                SELECT groups.id AS group_id, groups.name AS group_name, memberships.role AS role
                FROM group_memberships memberships
                JOIN access_groups groups ON groups.id = memberships.group_id
                WHERE memberships.user_id = :userId
            ) session_memberships
            ORDER BY group_name, group_id
            """.trimIndent(),
        )
            .param("userId", userId)
            .query { result, _ ->
                SessionMembership(
                    groupId = result.getObject("group_id", UUID::class.java),
                    groupName = AccessName.from(result.getString("group_name")),
                    role = result.getString("role"),
                )
            }
            .list()

        return SessionView(
            user = UserAccount(userId, command.subject, command.email, command.displayName),
            memberships = memberships,
        )
    }
}
