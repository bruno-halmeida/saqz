package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.AdminCouponAnalytics
import br.com.saqz.subscriptions.application.CouponAnalyticsReport
import br.com.saqz.subscriptions.application.CouponAnalyticsRow
import br.com.saqz.subscriptions.application.CouponConversionMetrics
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource

/** Read-only participation attribution. Never infer a receipt from plan price or ACTIVE status. */
class JdbcAdminCouponAnalytics(dataSource: DataSource, private val clock: Clock) : AdminCouponAnalytics {
    private val jdbc = JdbcClient.create(dataSource)
    private val mapper = jacksonObjectMapper()
    private val snapshot = TransactionTemplate(DataSourceTransactionManager(dataSource)).apply {
        isReadOnly = true
        isolationLevel = TransactionDefinition.ISOLATION_REPEATABLE_READ
    }
    private data class Key(val type: String, val id: UUID)
    private data class Touch(val key: Key, val owner: UUID, val at: Instant, val end: Instant?)
    private data class Payment(val owner: UUID, val id: String, val at: Instant, val cents: Long?)
    private data class Coupon(
        val key: Key, val code: String, val campaign: String?, val days: Int?, val discount: Int?,
        val active: Boolean, val until: Instant?, val max: Int?,
    )

    override fun report(): CouponAnalyticsReport = requireNotNull(snapshot.execute {
        val now = clock.instant()
        val coupons = jdbc.sql("""
            SELECT 'TRIAL' AS type,id,code,campaign,trial_days, NULL::integer AS discount_percent,active,valid_until,max_uses
            FROM trial_coupons
            UNION ALL
            SELECT 'DISCOUNT',id,code,NULL,NULL,discount_percent,true,valid_until,NULL FROM coupons
            ORDER BY code,type,id
        """.trimIndent()).query { rs, _ -> Coupon(
            Key(rs.getString("type"),rs.getObject("id",UUID::class.java)),rs.getString("code"),rs.getString("campaign"),
            rs.getObject("trial_days") as Int?,rs.getObject("discount_percent") as Int?,rs.getBoolean("active"),
            rs.getTimestamp("valid_until")?.toInstant(),rs.getObject("max_uses") as Int?,
        ) }.list()
        val touches = jdbc.sql("""
            SELECT 'TRIAL' AS type,coupon_id,owner_user_id AS owner,started_at AS used_at,ends_at
            FROM organizer_trials WHERE coupon_id IS NOT NULL AND started_at <= :now
            UNION ALL
            SELECT 'DISCOUNT',coupon_id,user_id,redeemed_at,NULL FROM coupon_redemptions WHERE redeemed_at <= :now
        """.trimIndent()).param("now",now.atOffset(ZoneOffset.UTC)).query { rs, _ -> Touch(
            Key(rs.getString("type"),rs.getObject("coupon_id",UUID::class.java)),rs.getObject("owner",UUID::class.java),
            rs.getTimestamp("used_at").toInstant(),rs.getTimestamp("ends_at")?.toInstant(),
        ) }.list()
        val payments = readPayments(now).groupBy { it.owner }
        val confirmations = jdbc.sql("""
            SELECT owner_user_id,first_confirmed_at FROM subscriptions
            WHERE first_confirmed_at IS NOT NULL AND first_confirmed_at <= :now
        """.trimIndent()).param("now",now.atOffset(ZoneOffset.UTC)).query { rs, _ ->
            rs.getObject("owner_user_id",UUID::class.java) to rs.getTimestamp("first_confirmed_at").toInstant()
        }.list().toMap()
        fun metrics(participants: List<Touch>): CouponConversionMetrics {
            val firstUse = participants.groupBy { it.owner }.mapValues { (_, uses) -> uses.minOf { it.at } }
            val credited = firstUse.flatMap { (owner, at) -> payments[owner].orEmpty().filter { !it.at.isBefore(at) } }
            val paid = credited.filter { (it.cents ?: 0) > 0 }
            val incomplete = credited.filter { it.cents == null }.map { it.owner }.toMutableSet()
            firstUse.forEach { (owner, at) ->
                if (confirmations[owner]?.let { !it.isBefore(at) } == true && credited.none { it.owner == owner }) incomplete.add(owner)
            }
            val payers = paid.map { it.owner }.toSet().size
            return CouponConversionMetrics(firstUse.size,payers,rate(payers,firstUse.size),paid.size,paid.sumOf { requireNotNull(it.cents) },incomplete.size)
        }
        val byCoupon = touches.groupBy { it.key }
        CouponAnalyticsReport(now,metrics(touches),coupons.map { coupon ->
            val uses = byCoupon[coupon.key].orEmpty()
            val ended = uses.filter { it.end?.let { end -> !end.isAfter(now) } == true }
            val isTrial = coupon.key.type == "TRIAL"
            val status = when {
                !coupon.active -> "INACTIVE"
                coupon.until?.let { !it.isAfter(now) } == true -> "EXPIRED"
                coupon.max?.let { uses.size >= it } == true -> "EXHAUSTED"
                else -> "ACTIVE"
            }
            CouponAnalyticsRow(coupon.key.id,coupon.key.type,coupon.code,coupon.campaign,coupon.days,coupon.discount,status,
                metrics(uses),if(isTrial) uses.size-ended.size else null,if(isTrial) ended.size else null,
                if(isTrial) metrics(ended).conversionPercent else null)
        })
    })

    private fun readPayments(now: Instant): List<Payment> {
        val payments = linkedMapOf<Pair<UUID,String>,Payment>()
        // Deduplicate BEFORE attribution: a later RECEIVED must not move the first confirmation past a redemption.
        jdbc.sql("""
            SELECT e.id,e.owner_user_id,e.payload,e.processed_at FROM subscription_events e
            WHERE e.processed_at IS NOT NULL AND e.processed_at <= :now
              AND e.owner_user_id IS NOT NULL AND e.type IN ('PAYMENT_CONFIRMED','PAYMENT_RECEIVED')
              AND (EXISTS (SELECT 1 FROM coupon_redemptions r WHERE r.user_id=e.owner_user_id)
                   OR EXISTS (SELECT 1 FROM organizer_trials t WHERE t.owner_user_id=e.owner_user_id AND t.coupon_id IS NOT NULL))
            ORDER BY e.processed_at,e.id
        """.trimIndent()).param("now",now.atOffset(ZoneOffset.UTC)).query { rs ->
            val owner = rs.getObject("owner_user_id",UUID::class.java)
            val node = runCatching { mapper.readTree(rs.getString("payload"))?.path("payment") }.getOrNull()
            val id = node?.path("id")?.takeIf { it.isTextual }?.asText()?.takeIf { it.isNotBlank() }
            val value = node?.path("value")?.takeIf { it.isNumber || it.isTextual }?.asText()?.toBigDecimalOrNull()
            val cents = if(id == null) null else runCatching {
                value?.multiply(BigDecimal(100))?.setScale(0,RoundingMode.HALF_UP)?.longValueExact()
            }.getOrNull()
            val key = owner to (id?.let { "payment:$it" } ?: "event:${rs.getObject("id")}")
            val previous = payments[key]
            payments[key] = previous?.copy(cents=previous.cents ?: cents)
                ?: Payment(owner,key.second,rs.getTimestamp("processed_at").toInstant(),cents)
        }
        return payments.values.toList()
    }
    private fun rate(payers: Int, users: Int): BigDecimal? = if(users == 0) null else
        BigDecimal(payers).multiply(BigDecimal(100)).divide(BigDecimal(users),2,RoundingMode.HALF_UP)
}
