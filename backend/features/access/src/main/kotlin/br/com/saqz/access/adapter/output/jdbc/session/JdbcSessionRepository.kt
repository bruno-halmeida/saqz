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
import java.sql.Types
import java.util.UUID
import javax.sql.DataSource

class JdbcSessionRepository(
    dataSource: DataSource,
) : SessionRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun upsertAndLoad(command: SessionUpsert): SessionView {
        val user = jdbc.sql(
            """
            INSERT INTO access_users (
                id, firebase_subject, email, email_verified, display_name, created_at, updated_at
            ) VALUES (
                :id, :subject, :email, :emailVerified, :displayName, now(), now()
            )
            ON CONFLICT (firebase_subject) DO UPDATE SET
                email = EXCLUDED.email,
                email_verified = EXCLUDED.email_verified,
                display_name = EXCLUDED.display_name,
                updated_at = now()
            RETURNING id, email, display_name, phone, nickname, city, phone_visibility
            """.trimIndent(),
        )
            .param("id", UUID.randomUUID())
            .param("subject", command.subject)
            .param("email", command.email)
            .param("emailVerified", command.emailVerified)
            .param("displayName", command.displayName.value)
            .query { result, _ ->
                SessionUserRow(
                    id = result.getObject("id", UUID::class.java),
                    email = result.getString("email"),
                    displayName = result.getString("display_name"),
                    phone = result.getString("phone"),
                    nickname = result.getString("nickname"),
                    city = result.getString("city"),
                    phoneVisibility = result.getString("phone_visibility"),
                )
            }
            .single()

        return SessionView(
            user = UserAccount(
                id = user.id,
                firebaseSubject = command.subject,
                email = user.email,
                displayName = AccessName.from(user.displayName),
                phone = user.phone?.let(PhoneNumber::from),
                photoDigest = loadPhotoDigest(user.id),
                nickname = user.nickname,
                city = user.city,
                phoneVisibility = user.phoneVisibility,
            ),
            memberships = loadMemberships(user.id),
        )
    }

    override fun updateProfile(command: ProfileCompletion): SessionView? {
        val user = jdbc.sql(
            """
            UPDATE access_users
            SET phone = CASE WHEN :phoneProvided THEN :phone ELSE phone END,
                display_name = CASE WHEN :displayNameProvided THEN :displayName ELSE display_name END,
                nickname = CASE WHEN :nicknameProvided THEN :nickname ELSE nickname END,
                city = CASE WHEN :cityProvided THEN :city ELSE city END,
                phone_visibility = CASE WHEN :phoneVisibilityProvided THEN :phoneVisibility ELSE phone_visibility END,
                updated_at = now()
            WHERE firebase_subject = :subject
            RETURNING id, email, display_name, phone, nickname, city, phone_visibility
            """.trimIndent(),
        )
            .param("phone", command.phone?.value, Types.VARCHAR)
            .param("phoneProvided", command.phoneProvided)
            .param("displayName", command.displayName?.value, Types.VARCHAR)
            .param("displayNameProvided", command.displayNameProvided)
            .param("nickname", command.nickname, Types.VARCHAR)
            .param("nicknameProvided", command.nicknameProvided)
            .param("city", command.city, Types.VARCHAR)
            .param("cityProvided", command.cityProvided)
            .param("phoneVisibility", command.phoneVisibility?.name, Types.VARCHAR)
            .param("phoneVisibilityProvided", command.phoneVisibilityProvided)
            .param("subject", command.subject)
            .query { result, _ ->
                SessionUserRow(
                    id = result.getObject("id", UUID::class.java),
                    email = result.getString("email"),
                    displayName = result.getString("display_name"),
                    phone = result.getString("phone"),
                    nickname = result.getString("nickname"),
                    city = result.getString("city"),
                    phoneVisibility = result.getString("phone_visibility"),
                )
            }
            .optional()
            .orElse(null) ?: return null

        return SessionView(
            user = UserAccount(
                id = user.id,
                firebaseSubject = command.subject,
                email = user.email,
                displayName = AccessName.from(user.displayName),
                phone = user.phone?.let(PhoneNumber::from),
                photoDigest = loadPhotoDigest(user.id),
                nickname = user.nickname,
                city = user.city,
                phoneVisibility = user.phoneVisibility,
            ),
            memberships = loadMemberships(user.id),
        )
    }

    private fun loadPhotoDigest(userId: UUID): String? =
        jdbc.sql("SELECT encode(sha256_digest, 'hex') FROM access_user_photos WHERE user_id = :userId")
            .param("userId", userId)
            .query(String::class.java)
            .optional()
            .orElse(null)

    private fun loadMemberships(userId: UUID): List<SessionMembership> = jdbc.sql(
        """
        SELECT
            groups.id AS group_id,
            groups.name AS group_name,
            CASE WHEN groups.owner_user_id = memberships.user_id THEN 'OWNER' ELSE memberships.role END AS role
        FROM group_memberships memberships
        JOIN access_groups groups ON groups.id = memberships.group_id
        WHERE memberships.user_id = :userId
          AND groups.deleted_at IS NULL
        ORDER BY groups.name, groups.id
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

    private data class SessionUserRow(
        val id: UUID,
        val email: String?,
        val displayName: String,
        val phone: String?,
        val nickname: String?,
        val city: String?,
        val phoneVisibility: String,
    )
}
