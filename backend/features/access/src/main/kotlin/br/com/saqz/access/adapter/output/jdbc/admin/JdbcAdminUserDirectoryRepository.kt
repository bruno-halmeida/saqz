package br.com.saqz.access.adapter.output.jdbc.admin

import br.com.saqz.access.application.admin.AdminAthleteProfile
import br.com.saqz.access.application.admin.AdminUserApp
import br.com.saqz.access.application.admin.AdminUserContact
import br.com.saqz.access.application.admin.AdminUserContactSummary
import br.com.saqz.access.application.admin.AdminUserDetail
import br.com.saqz.access.application.admin.AdminUserDirectory
import br.com.saqz.access.application.admin.AdminUserGroup
import br.com.saqz.access.application.admin.AdminUserOrigin
import br.com.saqz.access.application.admin.AdminUserOwnedGroup
import br.com.saqz.access.application.admin.AdminUserPage
import br.com.saqz.access.application.admin.AdminUserSubscription
import br.com.saqz.access.application.admin.AdminUserSummary
import br.com.saqz.access.application.admin.AdminUserTrial
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

class JdbcAdminUserDirectoryRepository(
    dataSource: DataSource,
) : AdminUserDirectory {
    private val jdbc = JdbcClient.create(dataSource)

    override fun list(
        query: String?,
        plan: String?,
        status: String?,
        page: Int,
        size: Int,
        sort: String?,
        descending: Boolean,
        trial: String?,
    ): AdminUserPage {
        data class Row(val summary: AdminUserSummary, val total: Long)

        val search = query?.trim()?.takeIf { it.isNotEmpty() }
        // Telefone se busca pelos dígitos: "(11) 9000" tem de achar "+5511900001234".
        val digits = search?.filter(Char::isDigit)?.takeIf { it.length >= MIN_PHONE_SEARCH_DIGITS }

        val rows = jdbc.sql(
            """
            SELECT u.id, u.display_name, u.email, u.phone, u.city, u.created_at, u.suspended_at,
                   u.phone IS NOT NULL AND u.phone = u.verified_phone AS phone_verified,
                   $LAST_SEEN AS last_seen_at,
                   p.plan, p.source AS plan_source, p.ends_at AS plan_ends_at, t.ends_at AS trial_ends_at,
                   (SELECT count(*) FROM group_memberships m
                     JOIN access_groups mg ON mg.id = m.group_id AND mg.deleted_at IS NULL
                     WHERE m.user_id = u.id) AS memberships,
                   (SELECT count(*) FROM access_groups g
                     WHERE g.owner_user_id = u.id AND g.deleted_at IS NULL) AS owned_groups,
                   og.id AS owned_group_id, og.name AS owned_group_name, og.deleted_at AS owned_group_deleted_at,
                   og.members AS owned_group_members, og.games_played AS owned_group_games,
                   lc.result AS last_contact_result, lc.created_at AS last_contact_at,
                   count(*) OVER () AS total
            $USERS
            LEFT JOIN LATERAL (
                SELECT g.id, g.name, g.deleted_at,
                       (SELECT count(*) FROM group_memberships m WHERE m.group_id = g.id) AS members,
                       $GAMES_PLAYED AS games_played
                FROM access_groups g
                WHERE g.owner_user_id = u.id
                ORDER BY (g.deleted_at IS NOT NULL), g.created_at, g.id
                LIMIT 1
            ) og ON true
            LEFT JOIN LATERAL (
                SELECT c.result, c.created_at FROM admin_user_contacts c
                WHERE c.user_id = u.id
                ORDER BY c.created_at DESC, c.id
                LIMIT 1
            ) lc ON true
            $FILTERS
            ORDER BY ${orderBy(sort, descending, trial)}
            LIMIT :size OFFSET :offset
            """.trimIndent(),
        )
            .param("query", search)
            .param("digits", digits)
            .param("plan", plan)
            .param("status", status)
            .param("trial", trial)
            .param("size", size)
            .param("offset", (page - 1).toLong() * size)
            .query { rs, _ ->
                val hasPlan = rs.getString("plan") != null
                val lastResult = rs.getString("last_contact_result")
                Row(
                    summary = AdminUserSummary(
                        userId = rs.getObject("id", UUID::class.java),
                        displayName = rs.getString("display_name"),
                        email = rs.getString("email"),
                        city = rs.getString("city"),
                        plan = rs.getString("plan"),
                        planSource = rs.getString("plan_source"),
                        planEndsAt = rs.instantOrNull("plan_ends_at"),
                        suspended = rs.getObject("suspended_at") != null,
                        memberships = rs.getLong("memberships"),
                        ownedGroups = rs.getLong("owned_groups"),
                        createdAt = rs.instant("created_at"),
                        lastSeenAt = rs.instant("last_seen_at"),
                        phone = rs.getString("phone"),
                        phoneVerified = rs.getBoolean("phone_verified"),
                        trialEndsAt = rs.instantOrNull("trial_ends_at"),
                        ownedGroup = rs.getObject("owned_group_id", UUID::class.java)?.let { groupId ->
                            val deleted = rs.getObject("owned_group_deleted_at") != null
                            AdminUserOwnedGroup(
                                groupId = groupId,
                                name = rs.getString("owned_group_name"),
                                deleted = deleted,
                                frozen = !deleted && !hasPlan && rs.getObject("trial_ends_at") != null,
                                members = rs.getLong("owned_group_members"),
                                gamesPlayed = rs.getLong("owned_group_games"),
                            )
                        },
                        lastContact = lastResult?.let { AdminUserContactSummary(it, rs.instant("last_contact_at")) },
                        doNotContact = lastResult == AdminUserDirectory.DO_NOT_CONTACT,
                    ),
                    total = rs.getLong("total"),
                )
            }
            .list()

        val total = rows.firstOrNull()?.total ?: jdbc.sql("SELECT count(*) $USERS $FILTERS")
            .param("query", search)
            .param("digits", digits)
            .param("plan", plan)
            .param("status", status)
            .param("trial", trial)
            .query(Long::class.java)
            .single()

        return AdminUserPage(
            items = rows.map { it.summary },
            total = total,
            page = page,
            size = size,
        )
    }

    override fun find(userId: UUID): AdminUserDetail? {
        val user = jdbc.sql(
            """
            SELECT u.id, u.display_name, u.email, u.email_verified, u.nickname, u.phone, u.phone_visibility, u.city,
                   u.platform_admin, u.onboarding_completed_at, u.suspended_at, u.created_at,
                   u.phone IS NOT NULL AND u.phone = u.verified_phone AS phone_verified,
                   $LAST_SEEN AS last_seen_at,
                   EXISTS (SELECT 1 FROM access_user_photos ph WHERE ph.user_id = u.id) AS has_photo,
                   p.plan AS effective_plan, p.source AS effective_plan_source, p.ends_at AS effective_plan_ends_at,
                   t.started_at AS trial_started_at, t.ends_at AS trial_ends_at,
                   (SELECT count(*) FROM organizer_trial_notices n WHERE n.owner_user_id = u.id) AS trial_notices,
                   ca.coupon_type, ca.coupon_code, ca.campaign, ca.first_used_at,
                   s.plan, s.cycle, s.created_at AS subscribed_at,
                   CASE WHEN s.canceled_at IS NOT NULL THEN 'CANCELED' ELSE s.status::text END AS subscription_status
            FROM access_users u
            LEFT JOIN subscriptions s ON s.owner_user_id = u.id
                 AND (s.status = 'ACTIVE' OR s.first_confirmed_at IS NOT NULL)
            LEFT JOIN owner_effective_plans p ON p.owner_user_id = u.id
            LEFT JOIN organizer_trials t ON t.owner_user_id = u.id
            LEFT JOIN customer_acquisition ca ON ca.owner_user_id = u.id
            WHERE u.id = :id AND u.deleted_at IS NULL
            """.trimIndent(),
        )
            .param("id", userId)
            .query { rs, _ ->
                AdminUserDetail(
                    userId = rs.getObject("id", UUID::class.java),
                    displayName = rs.getString("display_name"),
                    email = rs.getString("email"),
                    nickname = rs.getString("nickname"),
                    phone = rs.getString("phone"),
                    city = rs.getString("city"),
                    suspendedAt = rs.instantOrNull("suspended_at"),
                    createdAt = rs.instant("created_at"),
                    lastSeenAt = rs.instant("last_seen_at"),
                    groups = emptyList(),
                    subscription = rs.getString("plan")?.let {
                        AdminUserSubscription(
                            plan = it,
                            cycle = rs.getString("cycle"),
                            status = rs.getString("subscription_status"),
                            since = rs.instant("subscribed_at"),
                        )
                    },
                    emailVerified = rs.getBoolean("email_verified"),
                    phoneVerified = rs.getBoolean("phone_verified"),
                    phoneVisibility = rs.getString("phone_visibility"),
                    hasPhoto = rs.getBoolean("has_photo"),
                    platformAdmin = rs.getBoolean("platform_admin"),
                    onboardingCompletedAt = rs.instantOrNull("onboarding_completed_at"),
                    plan = rs.getString("effective_plan"),
                    planSource = rs.getString("effective_plan_source"),
                    planEndsAt = rs.instantOrNull("effective_plan_ends_at"),
                    trial = rs.instantOrNull("trial_started_at")?.let { startedAt ->
                        AdminUserTrial(startedAt, rs.instant("trial_ends_at"), rs.getLong("trial_notices"))
                    },
                    origin = rs.getString("coupon_type")?.let { type ->
                        AdminUserOrigin(type, rs.getString("coupon_code"), rs.getString("campaign"), rs.instant("first_used_at"))
                    },
                )
            }
            .optional()
            .orElse(null) ?: return null

        val groups = jdbc.sql(
            """
            SELECT g.id, g.name, m.membership_type, m.active, m.nickname, m.position, m.secondary_position, m.level,
                   m.preferred_side, m.height_cm, m.monthly_fee_exempt, m.mensalista_since,
                   CASE WHEN g.owner_user_id = :id THEN 'OWNER' ELSE m.role::text END AS role,
                   (SELECT count(*) FROM group_memberships gm WHERE gm.group_id = g.id) AS members,
                   $GAMES_PLAYED AS games_played,
                   NOT EXISTS (SELECT 1 FROM owner_effective_plans op WHERE op.owner_user_id = g.owner_user_id)
                       AND EXISTS (SELECT 1 FROM organizer_trials ot WHERE ot.owner_user_id = g.owner_user_id) AS frozen
            FROM group_memberships m
            JOIN access_groups g ON g.id = m.group_id AND g.deleted_at IS NULL
            WHERE m.user_id = :id
            ORDER BY g.created_at
            """.trimIndent(),
        )
            .param("id", userId)
            .query { rs, _ ->
                AdminUserGroup(
                    groupId = rs.getObject("id", UUID::class.java),
                    name = rs.getString("name"),
                    role = rs.getString("role"),
                    members = rs.getLong("members"),
                    gamesPlayed = rs.getLong("games_played"),
                    frozen = rs.getBoolean("frozen"),
                    athlete = AdminAthleteProfile(
                        membershipType = rs.getString("membership_type"),
                        active = rs.getBoolean("active"),
                        nickname = rs.getString("nickname"),
                        position = rs.getString("position"),
                        secondaryPosition = rs.getString("secondary_position"),
                        level = rs.getString("level"),
                        preferredSide = rs.getString("preferred_side"),
                        heightCm = rs.getObject("height_cm")?.let { (it as Number).toInt() },
                        monthlyFeeExempt = rs.getBoolean("monthly_fee_exempt"),
                        mensalistaSince = rs.getObject("mensalista_since", LocalDate::class.java),
                    ),
                )
            }
            .list()

        val apps = jdbc.sql(
            """
            SELECT platform, app_version, app_build, first_seen_at, last_seen_at
            FROM access_user_app_versions
            WHERE user_id = :id
            ORDER BY last_seen_at DESC
            """.trimIndent(),
        )
            .param("id", userId)
            .query { rs, _ ->
                AdminUserApp(
                    platform = rs.getString("platform"),
                    appVersion = rs.getString("app_version"),
                    appBuild = rs.getString("app_build"),
                    firstSeenAt = rs.instant("first_seen_at"),
                    lastSeenAt = rs.instant("last_seen_at"),
                )
            }
            .list()

        val contacts = contacts(userId)
        return user.copy(
            groups = groups,
            apps = apps,
            contacts = contacts,
            doNotContact = contacts.firstOrNull()?.result == AdminUserDirectory.DO_NOT_CONTACT,
        )
    }

    override fun addContact(userId: UUID, authorId: UUID, result: String, note: String?): AdminUserContact? {
        val contactId = UUID.randomUUID()
        val inserted = jdbc.sql(
            """
            INSERT INTO admin_user_contacts (id, user_id, result, note, created_by)
            SELECT :contactId, u.id, :result, :note, :authorId
            FROM access_users u
            WHERE u.id = :userId AND u.deleted_at IS NULL
            """.trimIndent(),
        )
            .param("contactId", contactId)
            .param("userId", userId)
            .param("authorId", authorId)
            .param("result", result)
            .param("note", note)
            .update()
        return if (inserted == 0) null else contacts(userId).first { it.id == contactId }
    }

    private fun contacts(userId: UUID): List<AdminUserContact> = jdbc.sql(
        """
        SELECT c.id, c.result, c.note, c.created_at, a.display_name AS author_name
        FROM admin_user_contacts c
        JOIN access_users a ON a.id = c.created_by
        WHERE c.user_id = :id
        ORDER BY c.created_at DESC, c.id
        LIMIT $MAX_CONTACTS
        """.trimIndent(),
    )
        .param("id", userId)
        .query { rs, _ ->
            AdminUserContact(
                id = rs.getObject("id", UUID::class.java),
                result = rs.getString("result"),
                note = rs.getString("note"),
                authorName = rs.getString("author_name"),
                createdAt = rs.instant("created_at"),
            )
        }
        .list()

    // updated_at fica intacto de propósito: é o proxy de último acesso do usuário,
    // e ação administrativa não é acesso.
    override fun suspend(userId: UUID): Boolean = jdbc.sql(
        "UPDATE access_users SET suspended_at = COALESCE(suspended_at, now()) " +
            "WHERE id = :id AND deleted_at IS NULL",
    ).param("id", userId).update() > 0

    override fun reactivate(userId: UUID): Boolean = jdbc.sql(
        "UPDATE access_users SET suspended_at = NULL WHERE id = :id AND deleted_at IS NULL",
    ).param("id", userId).update() > 0

    // Lista fechada: só trechos fixos entram no SQL; o id desempata para a paginação ficar estável.
    private fun orderBy(sort: String?, descending: Boolean, trial: String?): String {
        val column = when (sort) {
            null -> return when (trial) {
                "active" -> "t.ends_at ASC, u.id"
                "ended" -> "t.ends_at DESC, u.id"
                else -> "u.created_at DESC, u.id"
            }
            "name" -> "lower(u.display_name)"
            "city" -> "lower(u.city)"
            "groups" -> "memberships"
            "plan" -> "p.plan"
            "status" -> "(u.suspended_at IS NOT NULL)"
            "lastSeen" -> "last_seen_at"
            "createdAt" -> "u.created_at"
            else -> throw IllegalArgumentException("ordenação desconhecida: $sort")
        }
        return "$column ${if (descending) "DESC" else "ASC"} NULLS LAST, u.id"
    }

    private fun ResultSet.instant(column: String): Instant =
        getObject(column, OffsetDateTime::class.java).toInstant()

    private fun ResultSet.instantOrNull(column: String): Instant? =
        getObject(column, OffsetDateTime::class.java)?.toInstant()

    private companion object {
        const val MIN_PHONE_SEARCH_DIGITS = 4
        const val MAX_CONTACTS = 100

        // updated_at sozinho atrasa: o app só renova a sessão de vez em quando. A versão do app é gravada no máximo a cada 6 h.
        const val LAST_SEEN =
            "GREATEST(u.updated_at, (SELECT max(v.last_seen_at) FROM access_user_app_versions v WHERE v.user_id = u.id))"

        const val GAMES_PLAYED =
            "(SELECT count(*) FROM games x WHERE x.group_id = g.id AND x.status IN ('PUBLISHED', 'COMPLETED') AND x.starts_at < now())"

        // Lista e contagem usam as mesmas juntas e filtros: se divergirem, o total mente.
        val USERS = """
            FROM access_users u
            LEFT JOIN owner_effective_plans p ON p.owner_user_id = u.id
            LEFT JOIN organizer_trials t ON t.owner_user_id = u.id
        """.trimIndent()

        val FILTERS = """
            WHERE u.deleted_at IS NULL
              AND (:query::text IS NULL
                   OR u.display_name ILIKE '%' || :query || '%'
                   OR u.email ILIKE '%' || :query || '%'
                   OR regexp_replace(coalesce(u.phone, ''), '[^0-9]', '', 'g') LIKE '%' || :digits::text || '%')
              AND (:plan::text IS NULL
                   OR (:plan = 'FREE' AND p.plan IS NULL)
                   OR p.plan::text = :plan)
              AND (:status::text IS NULL
                   OR (:status = 'suspended' AND u.suspended_at IS NOT NULL)
                   OR (:status = 'active' AND u.suspended_at IS NULL))
              AND (:trial::text IS NULL
                   OR (:trial = 'active' AND p.source = 'TRIAL')
                   OR (:trial = 'ended' AND t.ends_at <= now() AND p.owner_user_id IS NULL
                       AND NOT EXISTS (SELECT 1 FROM subscriptions s
                                       WHERE s.owner_user_id = u.id AND s.first_confirmed_at IS NOT NULL)
                       AND NOT EXISTS (SELECT 1 FROM app_store_subscriptions a WHERE a.owner_user_id = u.id)
                       AND NOT EXISTS (SELECT 1 FROM google_play_subscriptions g WHERE g.owner_user_id = u.id)))
        """.trimIndent()
    }
}
