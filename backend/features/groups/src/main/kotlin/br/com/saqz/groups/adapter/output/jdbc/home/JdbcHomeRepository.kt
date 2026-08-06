package br.com.saqz.groups.adapter.output.jdbc.home

import br.com.saqz.groups.application.home.HomeAdminGroup
import br.com.saqz.groups.application.home.HomeAdminReadModel
import br.com.saqz.groups.application.home.HomeGameToSettle
import br.com.saqz.groups.application.home.HomeLastCompletedGame
import br.com.saqz.groups.application.home.HomeMemberGroup
import br.com.saqz.groups.application.home.HomeMemberReadModel
import br.com.saqz.groups.application.home.HomeMonthlyCharges
import br.com.saqz.groups.application.home.HomeNextGame
import br.com.saqz.groups.application.home.HomeOwnAttendance
import br.com.saqz.groups.application.home.HomeOwnChargeGroup
import br.com.saqz.groups.application.home.HomeOwnChargeOldest
import br.com.saqz.groups.application.home.HomeOwnChargesReadModel
import br.com.saqz.groups.application.home.HomeReadModel
import br.com.saqz.groups.application.home.HomeRepository
import br.com.saqz.groups.application.home.HomeRosterMember
import br.com.saqz.groups.application.home.HomeRosterPreview
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import javax.sql.DataSource

class JdbcHomeRepository(
    dataSource: DataSource,
) : HomeRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun find(actorId: UUID, now: Instant, today: LocalDate): HomeReadModel {
        val currentMonth = YearMonth.from(today)
        val memberGroups = jdbc.sql(MEMBER_GROUPS)
            .param("actor", actorId)
            .param("now", Timestamp.from(now))
            .query(::mapMemberGroup)
            .list()
        val nextGame = jdbc.sql(NEXT_GAME)
            .param("actor", actorId)
            .param("now", Timestamp.from(now))
            .query(::mapNextGame)
            .optional()
            .orElse(null)
        val roster = nextGame?.let {
            jdbc.sql(NEXT_GAME_ROSTER)
                .param("actor", actorId)
                .param("game", it.gameId)
                .query(::mapRosterMember)
                .list()
        } ?: emptyList()
        val lastCompleted = jdbc.sql(LAST_COMPLETED_GAME)
            .param("actor", actorId)
            .query(::mapLastCompletedGame)
            .optional()
            .orElse(null)
        val adminGroups = jdbc.sql(ADMIN_GROUPS)
            .param("actor", actorId)
            .param("billingMonth", currentMonth.atDay(1))
            .query { rs, row -> mapAdminGroup(rs, row, currentMonth) }
            .list()
        val ownChargeGroups = jdbc.sql(OWN_CHARGES)
            .param("actor", actorId)
            .param("today", today)
            .query(::mapOwnChargeGroup)
            .list()

        return HomeReadModel(
            member = HomeMemberReadModel(
                nextGame = nextGame?.withRoster(roster),
                lastCompletedGame = lastCompleted,
                groups = memberGroups,
            ),
            admin = adminGroups.takeIf { it.isNotEmpty() }?.let(::HomeAdminReadModel),
            ownCharges = ownChargeGroups.takeIf { it.isNotEmpty() }?.let {
                HomeOwnChargesReadModel(
                    groupCount = it.size,
                    totalCents = it.sumOf(HomeOwnChargeGroup::totalCents),
                    groups = it,
                )
            },
        )
    }

    private fun mapMemberGroup(result: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = HomeMemberGroup(
        id = result.getObject("group_id", UUID::class.java),
        name = result.getString("group_name"),
        role = GroupRole.valueOf(result.getString("role")),
        memberCount = result.getInt("member_count"),
        gamesPlayed = result.getInt("games_played"),
    )

    private fun mapNextGame(result: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = HomeNextGame(
        groupId = result.getObject("group_id", UUID::class.java),
        groupName = result.getString("group_name"),
        gameId = result.getObject("game_id", UUID::class.java),
        local = result.getString("local"),
        zoneId = result.getString("zone_id"),
        startsAt = result.getTimestamp("starts_at").toInstant(),
        confirmationDeadline = result.getTimestamp("confirmation_deadline").toInstant(),
        capacity = result.getInt("capacity"),
        confirmedCount = result.getInt("confirmed_count"),
        declinedCount = result.getInt("declined_count"),
        pendingCount = result.getInt("pending_count"),
        waitlistCount = result.getInt("waitlist_count"),
        ownAttendance = result.getString("own_status")?.let {
            HomeOwnAttendance(
                status = AttendanceStatus.valueOf(it),
                waitlistPosition = result.getObject("own_waitlist_position", Long::class.javaObjectType),
            )
        },
        membershipType = AthleteMembershipType.valueOf(result.getString("membership_type")),
        mensalistaPriority = result.getBoolean("mensalista_priority"),
        rosterPreview = HomeRosterPreview(emptyList(), emptyList()),
    )

    private fun mapRosterMember(result: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int) = RosterRow(
        displayName = result.getString("display_name"),
        status = AttendanceStatus.valueOf(result.getString("status")),
        waitlistPosition = result.getObject("waitlist_position", Long::class.javaObjectType),
    )

    private fun mapLastCompletedGame(
        result: ResultSet,
        @Suppress("UNUSED_PARAMETER") row: Int,
    ) = HomeLastCompletedGame(
        groupId = result.getObject("group_id", UUID::class.java),
        groupName = result.getString("group_name"),
        gameId = result.getObject("game_id", UUID::class.java),
        zoneId = result.getString("zone_id"),
        startsAt = result.getTimestamp("starts_at").toInstant(),
        confirmedCount = result.getInt("confirmed_count"),
        ownPlayed = result.getBoolean("own_played"),
    )

    private fun mapAdminGroup(
        result: ResultSet,
        @Suppress("UNUSED_PARAMETER") row: Int,
        currentMonth: YearMonth,
    ) = HomeAdminGroup(
        id = result.getObject("group_id", UUID::class.java),
        name = result.getString("group_name"),
        entryRequestCount = result.getInt("entry_request_count"),
        monthlyCharges = HomeMonthlyCharges(
            count = result.getInt("monthly_pending_count"),
            totalCents = result.getLong("monthly_pending_cents"),
            billingMonth = currentMonth,
        ),
        gameToSettle = result.getObject("settlement_game_id", UUID::class.java)?.let {
            HomeGameToSettle(
                gameId = it,
                startsAt = result.getTimestamp("settlement_starts_at").toInstant(),
                zoneId = result.getString("settlement_zone_id"),
                pendingCount = result.getInt("settlement_pending_count"),
                totalCents = result.getLong("settlement_pending_cents"),
            )
        },
    )

    private fun mapOwnChargeGroup(result: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int): HomeOwnChargeGroup {
        val oldestDueDate = result.getObject("oldest_due_date", LocalDate::class.java)
        return HomeOwnChargeGroup(
            groupId = result.getObject("group_id", UUID::class.java),
            groupName = result.getString("group_name"),
            count = result.getInt("pending_count"),
            totalCents = result.getLong("pending_cents"),
            nextDueDate = result.getObject("next_due_date", LocalDate::class.java),
            overdue = result.getBoolean("overdue"),
            pixKey = result.getString("pix_key"),
            pixLabel = result.getString("pix_label"),
            oldest = result.getObject("oldest_month", LocalDate::class.java)
                ?.let { HomeOwnChargeOldest.Monthly(YearMonth.from(it), oldestDueDate) }
                ?: HomeOwnChargeOldest.Game(
                    gameId = result.getObject("oldest_game_id", UUID::class.java),
                    startsAt = result.getTimestamp("oldest_game_starts_at").toInstant(),
                    zoneId = result.getString("oldest_game_zone_id"),
                    dueDate = oldestDueDate,
                ),
        )
    }

    private fun HomeNextGame.withRoster(rows: List<RosterRow>) = copy(
        rosterPreview = HomeRosterPreview(
            confirmed = rows.filter { it.status == AttendanceStatus.CONFIRMED }
                .map { HomeRosterMember(it.displayName, null) },
            waitlisted = rows.filter { it.status == AttendanceStatus.WAITLISTED }
                .map { HomeRosterMember(it.displayName, it.waitlistPosition) },
        ),
    )

    private data class RosterRow(
        val displayName: String,
        val status: AttendanceStatus,
        val waitlistPosition: Long?,
    )

    private companion object {
        const val MEMBER_GROUPS = """
            SELECT groups.id AS group_id,
                   groups.name AS group_name,
                   CASE
                       WHEN groups.owner_user_id = :actor THEN 'OWNER'
                       ELSE memberships.role
                   END AS role,
                   (
                       SELECT count(*)
                       FROM group_memberships members
                       WHERE members.group_id = groups.id
                         AND members.active = true
                   ) AS member_count,
                   (
                       SELECT count(*)
                       FROM games played
                       WHERE played.group_id = groups.id
                         AND played.status IN ('PUBLISHED', 'COMPLETED')
                         AND played.starts_at < :now
                   ) AS games_played
            FROM access_groups groups
            LEFT JOIN group_memberships memberships
                ON memberships.group_id = groups.id
               AND memberships.user_id = :actor
            WHERE groups.deleted_at IS NULL
              AND (groups.owner_user_id = :actor OR memberships.user_id IS NOT NULL)
            ORDER BY groups.name, groups.id
        """

        const val NEXT_GAME = """
            SELECT games.group_id,
                   groups.name AS group_name,
                   games.id AS game_id,
                   games.venue_name AS local,
                   games.zone_id,
                   games.starts_at,
                   games.confirmation_deadline,
                   games.capacity,
                   (
                       SELECT count(*)
                       FROM game_attendance attendance
                       WHERE attendance.game_id = games.id
                         AND attendance.status = 'CONFIRMED'
                   ) AS confirmed_count,
                   (
                       SELECT count(*)
                       FROM game_attendance attendance
                       WHERE attendance.game_id = games.id
                         AND attendance.status = 'DECLINED'
                   ) AS declined_count,
                   -- Todo membro ativo joga, inclusive dono e admin: o papel administrativo
                   -- não dispensa ninguém de responder.
                   (
                       SELECT count(*)
                       FROM group_memberships pending
                       WHERE pending.group_id = games.group_id
                         AND pending.active
                         AND NOT EXISTS (
                             SELECT 1
                             FROM game_attendance response
                             WHERE response.game_id = games.id
                               AND response.member_user_id = pending.user_id
                         )
                   ) AS pending_count,
                   (
                       SELECT count(*)
                       FROM game_attendance attendance
                       WHERE attendance.game_id = games.id
                         AND attendance.status = 'WAITLISTED'
                   ) AS waitlist_count,
                   own.status AS own_status,
                   own.waitlist_sequence AS own_waitlist_position,
                   COALESCE(memberships.membership_type, 'AVULSO') AS membership_type,
                   groups.mensalista_priority
            FROM games
            JOIN access_groups groups
                ON groups.id = games.group_id
               AND groups.deleted_at IS NULL
            LEFT JOIN group_memberships memberships
                ON memberships.group_id = games.group_id
               AND memberships.user_id = :actor
            LEFT JOIN game_attendance own
                ON own.game_id = games.id
               AND own.member_user_id = :actor
            WHERE games.status = 'PUBLISHED'
              AND games.starts_at >= :now
              AND (groups.owner_user_id = :actor OR memberships.user_id IS NOT NULL)
            ORDER BY games.starts_at, games.id
            LIMIT 1
        """

        const val NEXT_GAME_ROSTER = """
            SELECT member_display_name AS display_name,
                   status,
                   waitlist_sequence AS waitlist_position
            FROM (
                SELECT attendance.member_display_name,
                       attendance.status,
                       attendance.waitlist_sequence,
                       row_number() OVER (
                           PARTITION BY attendance.status
                           ORDER BY attendance.waitlist_sequence NULLS FIRST,
                                    lower(attendance.member_display_name),
                                    attendance.member_display_name,
                                    attendance.member_user_id
                       ) AS position
                FROM game_attendance attendance
                WHERE attendance.game_id = :game
                  AND attendance.status IN ('CONFIRMED', 'WAITLISTED')
            ) roster
            WHERE position <= 5
            ORDER BY CASE WHEN status = 'CONFIRMED' THEN 0 ELSE 1 END,
                     waitlist_position NULLS FIRST,
                     lower(member_display_name),
                     member_display_name
        """

        const val LAST_COMPLETED_GAME = """
            SELECT games.group_id,
                   groups.name AS group_name,
                   games.id AS game_id,
                   games.zone_id,
                   games.starts_at,
                   (
                       SELECT count(*)
                       FROM game_attendance attendance
                       WHERE attendance.game_id = games.id
                         AND attendance.status = 'CONFIRMED'
                   ) AS confirmed_count,
                   EXISTS (
                       SELECT 1
                       FROM game_attendance own
                       WHERE own.game_id = games.id
                         AND own.member_user_id = :actor
                         AND own.status = 'CONFIRMED'
                   ) AS own_played
            FROM games
            JOIN access_groups groups
                ON groups.id = games.group_id
               AND groups.deleted_at IS NULL
            LEFT JOIN group_memberships memberships
                ON memberships.group_id = games.group_id
               AND memberships.user_id = :actor
            WHERE games.status = 'COMPLETED'
              AND (groups.owner_user_id = :actor OR memberships.user_id IS NOT NULL)
            ORDER BY games.starts_at DESC, games.id DESC
            LIMIT 1
        """

        const val ADMIN_GROUPS = """
            WITH administered_groups AS (
                SELECT groups.id AS group_id, groups.name AS group_name
                FROM access_groups groups
                LEFT JOIN group_memberships memberships
                    ON memberships.group_id = groups.id
                   AND memberships.user_id = :actor
                WHERE groups.deleted_at IS NULL
                  AND (
                      groups.owner_user_id = :actor
                      OR memberships.role = 'ADMIN'
                  )
            ),
            entry_requests AS (
                SELECT group_id, count(*) AS entry_request_count
                FROM group_entry_requests
                GROUP BY group_id
            ),
            monthly_pending AS (
                SELECT group_id,
                       count(*) AS monthly_pending_count,
                       coalesce(sum(amount_cents), 0) AS monthly_pending_cents
                FROM group_charges
                WHERE kind = 'MONTHLY'
                  AND status = 'PENDING'
                  AND billing_month = :billingMonth
                GROUP BY group_id
            ),
            settlements AS (
                SELECT games.group_id,
                       games.id AS game_id,
                       games.starts_at,
                       games.zone_id,
                       count(charges.id) AS pending_count,
                       sum(charges.amount_cents) AS pending_cents,
                       row_number() OVER (
                           PARTITION BY games.group_id
                           ORDER BY games.starts_at DESC, games.id DESC
                       ) AS position
                FROM games
                JOIN group_charges charges
                    ON charges.group_id = games.group_id
                   AND charges.game_id = games.id
                   AND charges.kind = 'GAME'
                   AND charges.status = 'PENDING'
                JOIN administered_groups administered
                    ON administered.group_id = games.group_id
                WHERE games.status = 'COMPLETED'
                GROUP BY games.group_id, games.id, games.starts_at, games.zone_id
            )
            SELECT administered.group_id,
                   administered.group_name,
                   coalesce(entry_requests.entry_request_count, 0) AS entry_request_count,
                   coalesce(monthly_pending.monthly_pending_count, 0) AS monthly_pending_count,
                   coalesce(monthly_pending.monthly_pending_cents, 0) AS monthly_pending_cents,
                   settlements.game_id AS settlement_game_id,
                   settlements.starts_at AS settlement_starts_at,
                   settlements.zone_id AS settlement_zone_id,
                   settlements.pending_count AS settlement_pending_count,
                   settlements.pending_cents AS settlement_pending_cents
            FROM administered_groups administered
            LEFT JOIN entry_requests
                ON entry_requests.group_id = administered.group_id
            LEFT JOIN monthly_pending
                ON monthly_pending.group_id = administered.group_id
            LEFT JOIN settlements
                ON settlements.group_id = administered.group_id
               AND settlements.position = 1
            ORDER BY administered.group_name, administered.group_id
        """

        // Cobranças do próprio ator: a cobrança é o vínculo, então não filtra por membership
        // (quem saiu do grupo continua devendo). "Mais antiga" é por competência — mês da
        // mensalidade ou data local do jogo —, e não pela data de vencimento.
        const val OWN_CHARGES = """
            WITH pending AS (
                SELECT charges.id,
                       charges.group_id,
                       charges.billing_month,
                       charges.game_id,
                       charges.due_date,
                       charges.amount_cents,
                       games.starts_at AS game_starts_at,
                       games.zone_id AS game_zone_id,
                       row_number() OVER (
                           PARTITION BY charges.group_id
                           ORDER BY coalesce(
                                        charges.billing_month,
                                        (games.starts_at AT TIME ZONE games.zone_id)::date
                                    ),
                                    charges.due_date,
                                    charges.id
                       ) AS position
                FROM group_charges charges
                LEFT JOIN games
                    ON games.group_id = charges.group_id
                   AND games.id = charges.game_id
                WHERE charges.member_user_id = :actor
                  AND charges.status = 'PENDING'
            ),
            totals AS (
                SELECT group_id,
                       count(*) AS pending_count,
                       sum(amount_cents) AS pending_cents,
                       min(due_date) AS next_due_date
                FROM pending
                GROUP BY group_id
            )
            SELECT groups.id AS group_id,
                   groups.name AS group_name,
                   groups.pix_key,
                   groups.pix_label,
                   totals.pending_count,
                   totals.pending_cents,
                   totals.next_due_date,
                   totals.next_due_date < :today AS overdue,
                   oldest.billing_month AS oldest_month,
                   oldest.game_id AS oldest_game_id,
                   oldest.game_starts_at AS oldest_game_starts_at,
                   oldest.game_zone_id AS oldest_game_zone_id,
                   oldest.due_date AS oldest_due_date
            FROM totals
            JOIN access_groups groups
                ON groups.id = totals.group_id
               AND groups.deleted_at IS NULL
            JOIN pending oldest
                ON oldest.group_id = totals.group_id
               AND oldest.position = 1
            ORDER BY groups.name, groups.id
        """
    }
}
