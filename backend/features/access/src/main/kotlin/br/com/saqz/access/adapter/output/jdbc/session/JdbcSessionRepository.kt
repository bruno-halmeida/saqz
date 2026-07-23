package br.com.saqz.access.adapter.output.jdbc.session

import br.com.saqz.access.application.session.ProfileCompletion
import br.com.saqz.access.application.session.SessionMembership
import br.com.saqz.access.application.session.SessionRepository
import br.com.saqz.access.application.session.SessionUpsert
import br.com.saqz.access.application.session.SessionView
import br.com.saqz.access.application.session.UserAccount
import br.com.saqz.access.domain.AccessName
import br.com.saqz.access.domain.PhoneNumber
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import javax.sql.DataSource

class JdbcSessionRepository(
    dataSource: DataSource,
) : SessionRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun upsertAndLoad(command: SessionUpsert): SessionView {
        val (userId, phone) = jdbc.sql(
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
            RETURNING id, phone
            """.trimIndent(),
        )
            .param("id", UUID.randomUUID())
            .param("subject", command.subject)
            .param("email", command.email)
            .param("displayName", command.displayName.value)
            .query { result, _ -> result.getObject("id", UUID::class.java) to result.getString("phone") }
            .single()

        return SessionView(
            user = UserAccount(userId, command.subject, command.email, command.displayName, phone?.let(PhoneNumber::from)),
            memberships = loadMemberships(userId),
        )
    }

    override fun updateProfile(command: ProfileCompletion): SessionView? {
        val (userId, email, displayName) = jdbc.sql(
            """
            UPDATE access_users
            SET phone = :phone,
                display_name = COALESCE(:displayName, display_name),
                updated_at = now()
            WHERE firebase_subject = :subject
            RETURNING id, email, display_name
            """.trimIndent(),
        )
            .param("phone", command.phone.value)
            .param("displayName", command.displayName?.value)
            .param("subject", command.subject)
            .query { result, _ ->
                Triple(
                    result.getObject("id", UUID::class.java),
                    result.getString("email"),
                    result.getString("display_name"),
                )
            }
            .optional()
            .orElse(null) ?: return null

        return SessionView(
            user = UserAccount(userId, command.subject, email, AccessName.from(displayName), command.phone),
            memberships = loadMemberships(userId),
        )
    }

    private fun loadMemberships(userId: UUID): List<SessionMembership> = jdbc.sql(
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
}
