package br.com.saqz.groups.adapter.output.jdbc.admin

import br.com.saqz.groups.application.admin.AdminGroupDetail
import br.com.saqz.groups.application.admin.AdminGroupDirectory
import br.com.saqz.groups.application.admin.AdminGroupGame
import br.com.saqz.groups.application.admin.AdminGroupMember
import br.com.saqz.groups.application.admin.AdminGroupMemberPage
import br.com.saqz.groups.application.admin.AdminGroupOwnerContact
import br.com.saqz.groups.application.admin.AdminGroupPage
import br.com.saqz.groups.application.admin.AdminGroupProfile
import br.com.saqz.groups.application.admin.AdminGroupSlot
import br.com.saqz.groups.application.admin.AdminGroupSummary
import br.com.saqz.groups.application.admin.AdminGroupVenue
import br.com.saqz.groups.application.admin.AdminGroupWhatsApp
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

class JdbcAdminGroupDirectoryRepository(
    dataSource: DataSource,
) : AdminGroupDirectory {
    private val jdbc = JdbcClient.create(dataSource)

    override fun list(
        query: String?,
        status: String?,
        page: Int,
        size: Int,
        sort: String?,
        descending: Boolean,
    ): AdminGroupPage {
        data class Row(val summary: AdminGroupSummary, val total: Long)

        val rows = jdbc.sql(
            """
            SELECT g.id, g.name, g.created_at, g.deleted_at, g.owner_user_id,
                   u.display_name AS owner_name, p.plan AS owner_plan, p.source AS owner_plan_source, p.ends_at AS owner_plan_ends_at,
                   (SELECT count(*) FROM group_memberships m WHERE m.group_id = g.id) AS members,
                   (SELECT count(*) FROM games x
                     WHERE x.group_id = g.id AND x.status IN ('PUBLISHED', 'COMPLETED')
                       AND x.starts_at < now()) AS games_played,
                   count(*) OVER () AS total
            FROM access_groups g
            JOIN access_users u ON u.id = g.owner_user_id
            LEFT JOIN owner_effective_plans p ON p.owner_user_id = g.owner_user_id
            WHERE (:query::text IS NULL
                   OR g.name ILIKE '%' || :query || '%'
                   OR u.display_name ILIKE '%' || :query || '%')
              AND (
                   (:status::text IS NULL AND g.deleted_at IS NULL)
                   OR (:status = 'active' AND g.deleted_at IS NULL)
                   OR (:status = 'deleted' AND g.deleted_at IS NOT NULL)
              )
            ORDER BY ${orderBy(sort, descending)}
            LIMIT :size OFFSET :offset
            """.trimIndent(),
        )
            .param("query", query?.trim()?.takeIf { it.isNotEmpty() })
            .param("status", status)
            .param("size", size)
            .param("offset", (page - 1).toLong() * size)
            .query { rs, _ ->
                Row(
                    summary = AdminGroupSummary(
                        groupId = rs.getObject("id", UUID::class.java),
                        name = rs.getString("name"),
                        ownerUserId = rs.getObject("owner_user_id", UUID::class.java),
                        ownerName = rs.getString("owner_name"),
                        ownerPlan = rs.getString("owner_plan"),
                        ownerPlanSource = rs.getString("owner_plan_source"),
                        ownerPlanEndsAt = rs.instantOrNull("owner_plan_ends_at"),
                        members = rs.getLong("members"),
                        gamesPlayed = rs.getLong("games_played"),
                        deleted = rs.getObject("deleted_at") != null,
                        createdAt = rs.instant("created_at"),
                    ),
                    total = rs.getLong("total"),
                )
            }
            .list()

        val total = rows.firstOrNull()?.total ?: jdbc.sql(
            """
            SELECT count(*)
            FROM access_groups g
            JOIN access_users u ON u.id = g.owner_user_id
            WHERE (:query::text IS NULL
                   OR g.name ILIKE '%' || :query || '%'
                   OR u.display_name ILIKE '%' || :query || '%')
              AND (
                   (:status::text IS NULL AND g.deleted_at IS NULL)
                   OR (:status = 'active' AND g.deleted_at IS NULL)
                   OR (:status = 'deleted' AND g.deleted_at IS NOT NULL)
              )
            """.trimIndent(),
        )
            .param("query", query?.trim()?.takeIf { it.isNotEmpty() })
            .param("status", status)
            .query(Long::class.java)
            .single()

        return AdminGroupPage(rows.map { it.summary }, total, page, size)
    }

    override fun find(groupId: UUID): AdminGroupDetail? {
        val group = jdbc.sql(
            """
            SELECT g.id, g.name, g.time_zone, g.created_at, g.deleted_at, g.owner_user_id,
                   g.city, g.description, g.modality, g.game_system, g.composition, g.level, g.custom_level,
                   g.play_style, g.custom_play_style, g.privacy, g.entry_requires_approval, g.profile_status,
                   g.default_capacity, g.default_duration_minutes, g.default_confirmation_lead_minutes, g.schedule_paused,
                   g.mensalista_priority, g.promotion_mode, g.auto_confirm_enabled, g.attendance_updates_mode,
                   g.default_game_fee_cents, g.default_no_game_fee, g.monthly_fee_cents, g.monthly_due_day,
                   g.monthly_late_join_policy, g.advance_payment_required, g.pix_key, g.pix_label,
                   g.opening_balance_cents, g.currency,
                   EXISTS (SELECT 1 FROM group_photos ph WHERE ph.group_id = g.id) AS has_photo,
                   u.display_name AS owner_name, u.email AS owner_email, u.phone AS owner_phone,
                   u.phone IS NOT NULL AND u.phone = u.verified_phone AS owner_phone_verified,
                   $LATEST_CONTACT AS owner_last_contact,
                   p.plan AS owner_plan, p.source AS owner_plan_source, p.ends_at AS owner_plan_ends_at,
                   NOT EXISTS (SELECT 1 FROM owner_effective_plans op WHERE op.owner_user_id = g.owner_user_id)
                       AND EXISTS (SELECT 1 FROM organizer_trials ot WHERE ot.owner_user_id = g.owner_user_id) AS frozen,
                   v.name AS venue_name, v.address AS venue_address, v.court AS venue_court,
                   w.group_name AS whatsapp_name, w.enabled AS whatsapp_enabled, w.broken_at AS whatsapp_broken_at,
                   w.whatsapp_jid, w.created_at AS whatsapp_linked_at,
                   (SELECT count(*) FROM group_memberships m WHERE m.group_id = g.id) AS members,
                   (SELECT count(*) FROM games x
                     WHERE x.group_id = g.id AND x.status IN ('PUBLISHED', 'COMPLETED')
                       AND x.starts_at < now()) AS games_played,
                   (SELECT max(x.starts_at) FROM games x
                     WHERE x.group_id = g.id AND x.status IN ('PUBLISHED', 'COMPLETED')
                       AND x.starts_at < now()) AS last_game_at
            FROM access_groups g
            JOIN access_users u ON u.id = g.owner_user_id
            LEFT JOIN owner_effective_plans p ON p.owner_user_id = g.owner_user_id
            LEFT JOIN group_venues v ON v.group_id = g.id AND v.id = g.default_venue_id
            LEFT JOIN group_whatsapp_bindings w ON w.group_id = g.id
            WHERE g.id = :id
            """.trimIndent(),
        )
            .param("id", groupId)
            .query { rs, _ ->
                AdminGroupDetail(
                    groupId = rs.getObject("id", UUID::class.java),
                    name = rs.getString("name"),
                    timeZone = rs.getString("time_zone"),
                    ownerUserId = rs.getObject("owner_user_id", UUID::class.java),
                    ownerName = rs.getString("owner_name"),
                    ownerPlan = rs.getString("owner_plan"),
                    ownerPlanSource = rs.getString("owner_plan_source"),
                    ownerPlanEndsAt = rs.instantOrNull("owner_plan_ends_at"),
                    members = rs.getLong("members"),
                    gamesPlayed = rs.getLong("games_played"),
                    deletedAt = rs.instantOrNull("deleted_at"),
                    createdAt = rs.instant("created_at"),
                    lastGames = emptyList(),
                    frozen = rs.getObject("deleted_at") == null && rs.getBoolean("frozen"),
                    lastGameAt = rs.instantOrNull("last_game_at"),
                    ownerContact = AdminGroupOwnerContact(
                        email = rs.getString("owner_email"),
                        phone = rs.getString("owner_phone"),
                        phoneVerified = rs.getBoolean("owner_phone_verified"),
                        doNotContact = rs.getString("owner_last_contact") == DO_NOT_CONTACT,
                    ),
                    profile = rs.profile(),
                    venue = rs.getString("venue_name")?.let {
                        AdminGroupVenue(it, rs.getString("venue_address"), rs.getString("venue_court"))
                    },
                    whatsApp = rs.getString("whatsapp_name")?.let {
                        AdminGroupWhatsApp(
                            name = it,
                            enabled = rs.getBoolean("whatsapp_enabled"),
                            broken = rs.getObject("whatsapp_broken_at") != null,
                            chatId = rs.getString("whatsapp_jid"),
                            linkedAt = rs.instant("whatsapp_linked_at"),
                        )
                    },
                )
            }
            .optional()
            .orElse(null) ?: return null

        val games = jdbc.sql(
            """
            SELECT x.id, x.title, x.starts_at, x.status,
                   (SELECT count(*) FROM game_attendance a
                     WHERE a.game_id = x.id AND a.status = 'CONFIRMED') AS confirmed
            FROM games x
            WHERE x.group_id = :id AND x.status <> 'DRAFT' AND x.starts_at < now()
            ORDER BY x.starts_at DESC
            LIMIT :limit
            """.trimIndent(),
        )
            .param("id", groupId)
            .param("limit", LAST_GAMES)
            .query { rs, _ ->
                AdminGroupGame(
                    gameId = rs.getObject("id", UUID::class.java),
                    title = rs.getString("title"),
                    startsAt = rs.instant("starts_at"),
                    status = rs.getString("status"),
                    confirmed = rs.getLong("confirmed"),
                )
            }
            .list()

        val slots = jdbc.sql(
            """
            SELECT s.weekday, s.start_time, s.duration_minutes, v.name AS venue_name
            FROM group_regular_slots s
            LEFT JOIN group_venues v ON v.group_id = s.group_id AND v.id = s.venue_id
            WHERE s.group_id = :id
            ORDER BY s.position, s.weekday, s.start_time
            """.trimIndent(),
        )
            .param("id", groupId)
            .query { rs, _ ->
                AdminGroupSlot(
                    weekday = rs.getInt("weekday"),
                    startTime = rs.getObject("start_time", LocalTime::class.java).toString(),
                    durationMinutes = rs.getInt("duration_minutes"),
                    venueName = rs.getString("venue_name"),
                )
            }
            .list()

        return group.copy(lastGames = games, slots = slots)
    }

    override fun members(groupId: UUID, query: String?, page: Int, size: Int): AdminGroupMemberPage? {
        val exists = jdbc.sql("SELECT count(*) FROM access_groups WHERE id = :id")
            .param("id", groupId).query(Long::class.java).single() > 0
        if (!exists) return null

        data class Row(val member: AdminGroupMember, val total: Long)

        val search = query?.trim()?.takeIf { it.isNotEmpty() }
        val digits = search?.filter(Char::isDigit)?.takeIf { it.length >= MIN_PHONE_SEARCH_DIGITS }
        val rows = jdbc.sql(
            """
            SELECT u.id, u.display_name, u.email, u.phone,
                   u.phone IS NOT NULL AND u.phone = u.verified_phone AS phone_verified,
                   $LATEST_CONTACT AS last_contact,
                   CASE WHEN g.owner_user_id = u.id THEN 'OWNER' ELSE m.role::text END AS role,
                   m.membership_type, m.active, m.nickname, m.position, m.secondary_position, m.level,
                   m.preferred_side, m.height_cm, m.monthly_fee_exempt, m.mensalista_since, m.created_at AS joined_at,
                   GREATEST(u.updated_at, (SELECT max(v.last_seen_at) FROM access_user_app_versions v WHERE v.user_id = u.id)) AS last_seen_at,
                   count(*) OVER () AS total
            FROM group_memberships m
            JOIN access_groups g ON g.id = m.group_id
            JOIN access_users u ON u.id = m.user_id
            WHERE m.group_id = :id
              AND (:query::text IS NULL
                   OR u.display_name ILIKE '%' || :query || '%'
                   OR regexp_replace(coalesce(u.phone, ''), '[^0-9]', '', 'g') LIKE '%' || :digits::text || '%')
            ORDER BY (g.owner_user_id = u.id) DESC, (m.role::text = 'ADMIN') DESC, lower(u.display_name), u.id
            LIMIT :size OFFSET :offset
            """.trimIndent(),
        )
            .param("id", groupId)
            .param("query", search)
            .param("digits", digits)
            .param("size", size)
            .param("offset", (page - 1).toLong() * size)
            .query { rs, _ ->
                Row(
                    member = AdminGroupMember(
                        userId = rs.getObject("id", UUID::class.java),
                        displayName = rs.getString("display_name"),
                        email = rs.getString("email"),
                        phone = rs.getString("phone"),
                        phoneVerified = rs.getBoolean("phone_verified"),
                        doNotContact = rs.getString("last_contact") == DO_NOT_CONTACT,
                        role = rs.getString("role"),
                        membershipType = rs.getString("membership_type"),
                        active = rs.getBoolean("active"),
                        nickname = rs.getString("nickname"),
                        position = rs.getString("position"),
                        secondaryPosition = rs.getString("secondary_position"),
                        level = rs.getString("level"),
                        preferredSide = rs.getString("preferred_side"),
                        heightCm = rs.getIntOrNull("height_cm"),
                        monthlyFeeExempt = rs.getBoolean("monthly_fee_exempt"),
                        mensalistaSince = rs.getObject("mensalista_since", LocalDate::class.java),
                        joinedAt = rs.instant("joined_at"),
                        lastSeenAt = rs.instant("last_seen_at"),
                    ),
                    total = rs.getLong("total"),
                )
            }
            .list()

        val total = rows.firstOrNull()?.total ?: jdbc.sql(
            """
            SELECT count(*)
            FROM group_memberships m
            JOIN access_users u ON u.id = m.user_id
            WHERE m.group_id = :id
              AND (:query::text IS NULL
                   OR u.display_name ILIKE '%' || :query || '%'
                   OR regexp_replace(coalesce(u.phone, ''), '[^0-9]', '', 'g') LIKE '%' || :digits::text || '%')
            """.trimIndent(),
        )
            .param("id", groupId)
            .param("query", search)
            .param("digits", digits)
            .query(Long::class.java)
            .single()

        return AdminGroupMemberPage(rows.map { it.member }, total, page, size)
    }

    private fun ResultSet.profile() = AdminGroupProfile(
        city = getString("city"),
        description = getString("description"),
        modality = getString("modality"),
        gameSystem = getString("game_system"),
        composition = getString("composition"),
        level = getString("level"),
        customLevel = getString("custom_level"),
        playStyle = getString("play_style"),
        customPlayStyle = getString("custom_play_style"),
        privacy = getString("privacy"),
        entryRequiresApproval = getBoolean("entry_requires_approval"),
        profileStatus = getString("profile_status"),
        hasPhoto = getBoolean("has_photo"),
        defaultCapacity = getIntOrNull("default_capacity"),
        defaultDurationMinutes = getIntOrNull("default_duration_minutes"),
        defaultConfirmationLeadMinutes = getIntOrNull("default_confirmation_lead_minutes"),
        schedulePaused = getBoolean("schedule_paused"),
        mensalistaPriority = getBoolean("mensalista_priority"),
        promotionMode = getString("promotion_mode"),
        autoConfirmEnabled = getBoolean("auto_confirm_enabled"),
        attendanceUpdatesMode = getString("attendance_updates_mode"),
        defaultGameFeeCents = getLongOrNull("default_game_fee_cents"),
        defaultNoGameFee = getBoolean("default_no_game_fee"),
        monthlyFeeCents = getLongOrNull("monthly_fee_cents"),
        monthlyDueDay = getIntOrNull("monthly_due_day"),
        monthlyLateJoinPolicy = getString("monthly_late_join_policy"),
        advancePaymentRequired = getBoolean("advance_payment_required"),
        pixKey = getString("pix_key"),
        pixLabel = getString("pix_label"),
        openingBalanceCents = getLongOrNull("opening_balance_cents"),
        currency = getString("currency"),
    )

    private fun orderBy(sort: String?, descending: Boolean): String {
        val column = when (sort) {
            null -> return "g.created_at DESC, g.id"
            "name" -> "lower(g.name)"
            "owner" -> "lower(u.display_name)"
            "ownerPlan" -> "p.plan"
            "members" -> "members"
            "games" -> "games_played"
            "status" -> "(g.deleted_at IS NOT NULL)"
            "createdAt" -> "g.created_at"
            else -> throw IllegalArgumentException("ordenação desconhecida: $sort")
        }
        return "$column ${if (descending) "DESC" else "ASC"} NULLS LAST, g.id"
    }

    private fun ResultSet.instant(column: String): Instant =
        getObject(column, OffsetDateTime::class.java).toInstant()

    private fun ResultSet.instantOrNull(column: String): Instant? =
        getObject(column, OffsetDateTime::class.java)?.toInstant()

    private fun ResultSet.getIntOrNull(column: String): Int? = (getObject(column) as Number?)?.toInt()

    private fun ResultSet.getLongOrNull(column: String): Long? = (getObject(column) as Number?)?.toLong()

    private companion object {
        const val LAST_GAMES = 5
        const val MIN_PHONE_SEARCH_DIGITS = 4
        const val DO_NOT_CONTACT = "DO_NOT_CONTACT"

        // A linha mais recente decide se a pessoa pode ser procurada (V207).
        const val LATEST_CONTACT =
            "(SELECT c.result FROM admin_user_contacts c WHERE c.user_id = u.id ORDER BY c.created_at DESC, c.id LIMIT 1)"
    }
}
