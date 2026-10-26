package br.com.saqz.receivables.adapter.output.jdbc

import org.springframework.jdbc.core.simple.JdbcClient
import java.net.URI
import java.security.SecureRandom
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.Base64
import java.util.UUID
import javax.sql.DataSource

data class SettleLink(val code: String, val groupId: UUID, val gameId: UUID?, val billingMonth: LocalDate?, val announcedAt: Instant?)

/** Link da hora do acerto (FIN-013), no domínio do link de presença: `https://links.saqz.app/acerto/{code}`. */
class JdbcSettleLinks(dataSource: DataSource, private val linksDomain: URI, private val clock: java.time.Clock = java.time.Clock.systemUTC()) :
    br.com.saqz.receivables.application.SettleLinkUrls {
    private val jdbc = JdbcClient.create(dataSource)
    private val random = SecureRandom()

    init { require(linksDomain.scheme == "https" && !linksDomain.host.isNullOrBlank()) }

    fun url(code: String): String = linksDomain.toString().trimEnd('/') + "/acerto/" + code

    /** Mesmo código sempre para o mesmo (grupo, jogo) ou (grupo, mês). */
    fun forGame(groupId: UUID, gameId: UUID, at: Instant): SettleLink = obtain(groupId, gameId, null, at)
    fun forMonth(groupId: UUID, month: LocalDate, at: Instant): SettleLink = obtain(groupId, null, month.withDayOfMonth(1), at)

    override fun monthUrl(groupId: UUID, month: LocalDate): String = url(forMonth(groupId, month, clock.instant()).code)
    override fun gameUrl(groupId: UUID, gameId: UUID): String = url(forGame(groupId, gameId, clock.instant()).code)

    fun find(code: String): SettleLink? {
        if (!code.matches(Regex("[A-Za-z0-9_-]{8,32}"))) return null
        return jdbc.sql("SELECT * FROM settle_links WHERE code=:code").param("code", code).query { rs, _ -> link(rs) }.optional().orElse(null)
    }

    /** Marca a hora do acerto como enviada; falso se já tinha saído (uma vez por jogo ou mês). */
    fun announce(code: String, at: Instant): Boolean = jdbc.sql(
        "UPDATE settle_links SET announced_at=:at WHERE code=:code AND announced_at IS NULL",
    ).param("at", Timestamp.from(at)).param("code", code).update() == 1

    private fun obtain(groupId: UUID, gameId: UUID?, month: LocalDate?, at: Instant): SettleLink {
        val code = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(12).also(random::nextBytes))
        jdbc.sql("""
            INSERT INTO settle_links(code,group_id,game_id,billing_month,created_at) VALUES (:code,:group,:game,:month,:at)
            ON CONFLICT DO NOTHING
        """.trimIndent()).param("code", code).param("group", groupId).param("game", gameId, java.sql.Types.OTHER)
            .param("month", month, java.sql.Types.DATE).param("at", Timestamp.from(at)).update()
        val where = if (gameId != null) "game_id=:target" else "billing_month=:target"
        return jdbc.sql("SELECT * FROM settle_links WHERE group_id=:group AND $where").param("group", groupId)
            .param("target", gameId ?: month).query { rs, _ -> link(rs) }.single()
    }

    private fun link(rs: java.sql.ResultSet) = SettleLink(rs.getString("code"), rs.getObject("group_id", UUID::class.java),
        rs.getObject("game_id", UUID::class.java), rs.getObject("billing_month", LocalDate::class.java),
        rs.getTimestamp("announced_at")?.toInstant())
}
