package br.com.saqz.groups.adapter.output.jdbc.finance

import br.com.saqz.groups.application.finance.uncharged.UnchargedGameSummary
import br.com.saqz.groups.application.finance.uncharged.UnchargedGameView
import br.com.saqz.groups.application.finance.uncharged.UnchargedKind
import br.com.saqz.groups.application.finance.uncharged.UnchargedPerson
import br.com.saqz.groups.application.finance.uncharged.UnchargedRepository
import br.com.saqz.groups.application.finance.uncharged.UnchargedReviewOutcome
import br.com.saqz.groups.application.finance.uncharged.UnchargedReviewTarget
import br.com.saqz.groups.domain.GroupRole
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.sql.DataSource

/**
 * FIN-016. Entra quem está CONFIRMED num jogo publicado ou realizado, sem a marca Sem diária, sem nenhuma diária
 * daquele jogo (em qualquer estado) e que não é mensalista agora; convidado entra sempre, no nome do anfitrião.
 */
class JdbcUnchargedRepository(dataSource: DataSource) : UnchargedRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun role(actorId: UUID, groupId: UUID): GroupRole? = jdbc.sql(
        """
        SELECT CASE WHEN g.owner_user_id = :actor THEN 'OWNER' ELSE m.role::text END
        FROM access_groups g LEFT JOIN group_memberships m ON m.group_id = g.id AND m.user_id = :actor
        WHERE g.id = :group AND g.deleted_at IS NULL AND (g.owner_user_id = :actor OR m.user_id IS NOT NULL)
        """.trimIndent(),
    ).param("actor", actorId).param("group", groupId).query(String::class.java).optional().map(GroupRole::valueOf).orElse(null)

    override fun summary(groupId: UUID): List<UnchargedGameSummary> = jdbc.sql(
        """
        SELECT g.id, g.title, g.local_date, g.local_time, g.venue_name, count(*) AS people
        FROM games g JOIN game_attendance a ON a.game_id = g.id
        WHERE g.group_id = :group AND $ELIGIBLE_GAME AND $ELIGIBLE_PERSON AND NOT $SKIPPED
        GROUP BY g.id ORDER BY g.starts_at DESC, g.id
        """.trimIndent(),
    ).param("group", groupId).query { rs, _ ->
        UnchargedGameSummary(
            rs.getObject("id", UUID::class.java), rs.getString("title"), rs.getObject("local_date", LocalDate::class.java),
            rs.getObject("local_time", LocalTime::class.java), rs.getString("venue_name"), rs.getInt("people"),
        )
    }.list()

    override fun game(groupId: UUID, gameId: UUID): UnchargedGameView? {
        // Valor do jogo; sem ele, o padrão do grupo; sem os dois, o gestor digita.
        val suggested = jdbc.sql(
            """
            SELECT coalesce(g.game_fee_cents, ag.default_game_fee_cents) FROM games g
            JOIN access_groups ag ON ag.id = g.group_id AND ag.deleted_at IS NULL
            WHERE g.id = :game AND g.group_id = :group
            """.trimIndent(),
        ).param("game", gameId).param("group", groupId).query { rs, _ -> listOf(rs.getObject(1, java.lang.Long::class.java)?.toLong()) }
            .optional().orElse(null) ?: return null
        val people = jdbc.sql(
            """
            SELECT a.member_user_id, a.guest_seq, coalesce(a.member_display_name, u.nickname, u.display_name, '') AS display_name,
                   CASE WHEN a.guest_seq > 0 THEN coalesce(host.member_display_name, u.nickname, u.display_name) END AS host_name,
                   a.no_show_marked_at IS NOT NULL AS no_show,
                   NOT EXISTS (SELECT 1 FROM group_memberships m WHERE m.group_id = g.group_id AND m.user_id = a.member_user_id AND m.active) AS left_group,
                   $SKIPPED AS skipped
            FROM games g JOIN game_attendance a ON a.game_id = g.id
            LEFT JOIN game_attendance host ON host.game_id = a.game_id AND host.member_user_id = a.member_user_id AND host.guest_seq = 0
            LEFT JOIN access_users u ON u.id = a.member_user_id
            WHERE g.id = :game AND g.group_id = :group AND $ELIGIBLE_GAME AND $ELIGIBLE_PERSON
            ORDER BY a.guest_seq > 0, a.responded_at, a.member_user_id, a.guest_seq
            """.trimIndent(),
        ).param("game", gameId).param("group", groupId).query { rs, _ ->
            val guest = rs.getInt("guest_seq")
            UnchargedPerson(
                rs.getObject("member_user_id", UUID::class.java), guest, rs.getString("display_name"), rs.getString("host_name"),
                if (guest > 0) UnchargedKind.GUEST else UnchargedKind.AVULSO,
                rs.getBoolean("no_show"), rs.getBoolean("left_group"), rs.getBoolean("skipped"),
            )
        }.list()
        return UnchargedGameView(gameId, suggested.single(), people)
    }

    override fun lockForReview(groupId: UUID, gameId: UUID): UnchargedReviewTarget? {
        jdbc.sql("SELECT id FROM access_groups WHERE id = :group AND deleted_at IS NULL FOR UPDATE").param("group", groupId)
            .query(UUID::class.java).optional().orElse(null) ?: return null
        return jdbc.sql(
            """
            SELECT g.starts_at, g.zone_id, (g.status IN ('PUBLISHED', 'COMPLETED') AND NOT g.no_game_fee) AS billable
            FROM games g WHERE g.id = :game AND g.group_id = :group FOR UPDATE
            """.trimIndent(),
        ).param("game", gameId).param("group", groupId).query { rs, _ ->
            UnchargedReviewTarget(rs.getTimestamp("starts_at").toInstant(), ZoneId.of(rs.getString("zone_id")), rs.getBoolean("billable"))
        }.optional().orElse(null)
    }

    override fun replay(groupId: UUID, requestId: UUID): UnchargedReviewOutcome? = jdbc.sql(
        "SELECT charged, paid, skipped, ignored, charge_ids FROM game_uncharged_reviews WHERE group_id = :group AND request_id = :request",
    ).param("group", groupId).param("request", requestId).query { rs, _ ->
        @Suppress("UNCHECKED_CAST")
        val ids = (rs.getArray("charge_ids").array as Array<UUID>).toList()
        UnchargedReviewOutcome(rs.getInt("charged"), rs.getInt("paid"), rs.getInt("skipped"), rs.getInt("ignored"), ids)
    }.optional().orElse(null)

    override fun skip(groupId: UUID, gameId: UUID, memberId: UUID, guestSeq: Int, actorId: UUID, at: Instant) {
        jdbc.sql(
            """
            INSERT INTO game_uncharged_skips (game_id, member_user_id, guest_seq, group_id, decided_by_user_id, decided_at)
            VALUES (:game, :member, :guest, :group, :actor, :at)
            ON CONFLICT (game_id, member_user_id, guest_seq) DO UPDATE SET decided_by_user_id = EXCLUDED.decided_by_user_id, decided_at = EXCLUDED.decided_at
            """.trimIndent(),
        ).param("game", gameId).param("member", memberId).param("guest", guestSeq).param("group", groupId)
            .param("actor", actorId).param("at", Timestamp.from(at)).update()
    }

    override fun clearSkip(gameId: UUID, memberId: UUID, guestSeq: Int) {
        jdbc.sql("DELETE FROM game_uncharged_skips WHERE game_id = :game AND member_user_id = :member AND guest_seq = :guest")
            .param("game", gameId).param("member", memberId).param("guest", guestSeq).update()
    }

    override fun save(groupId: UUID, gameId: UUID, requestId: UUID, actorId: UUID, outcome: UnchargedReviewOutcome, at: Instant) {
        jdbc.sql(
            """
            INSERT INTO game_uncharged_reviews (group_id, request_id, game_id, actor_user_id, charged, paid, skipped, ignored, charge_ids, created_at)
            VALUES (:group, :request, :game, :actor, :charged, :paid, :skipped, :ignored, CAST(:ids AS uuid[]), :at)
            """.trimIndent(),
        ).param("group", groupId).param("request", requestId).param("game", gameId).param("actor", actorId)
            .param("charged", outcome.charged).param("paid", outcome.paid).param("skipped", outcome.skipped).param("ignored", outcome.ignored)
            .param("ids", outcome.chargeIds.joinToString(",", "{", "}")).param("at", Timestamp.from(at)).update()
    }

    private companion object {
        const val ELIGIBLE_GAME = "g.status IN ('PUBLISHED', 'COMPLETED') AND NOT g.no_game_fee"
        const val ELIGIBLE_PERSON = """a.status = 'CONFIRMED'
            AND NOT EXISTS (SELECT 1 FROM group_charges c WHERE c.kind = 'GAME' AND c.game_id = a.game_id
                            AND c.member_user_id = a.member_user_id AND c.guest_seq = a.guest_seq)
            AND (a.guest_seq > 0 OR coalesce((SELECT m.membership_type::text FROM group_memberships m
                 WHERE m.group_id = g.group_id AND m.user_id = a.member_user_id), 'AVULSO') <> 'MENSALISTA')"""
        const val SKIPPED = """EXISTS (SELECT 1 FROM game_uncharged_skips s WHERE s.game_id = a.game_id
            AND s.member_user_id = a.member_user_id AND s.guest_seq = a.guest_seq)"""
    }
}
