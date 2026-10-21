package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.CouponRepository
import br.com.saqz.subscriptions.domain.Coupon
import br.com.saqz.subscriptions.domain.CouponEligibility
import br.com.saqz.subscriptions.domain.CouponProduct
import br.com.saqz.subscriptions.domain.CouponRedemption
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID
import javax.sql.DataSource

class JdbcCouponRepository(
    dataSource: DataSource,
) : CouponRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun findByCode(code: String): Coupon? =
        jdbc.sql("SELECT $COLUMNS FROM coupons WHERE lower(code) = lower(:code)")
            .param("code", code)
            .query { rs, _ -> rs.toCoupon() }
            .optional()
            .orElse(null)

    override fun findById(couponId: UUID): Coupon? =
        jdbc.sql("SELECT $COLUMNS FROM coupons WHERE id = :couponId")
            .param("couponId", couponId)
            .query { rs, _ -> rs.toCoupon() }
            .optional()
            .orElse(null)

    override fun hasRedemption(couponId: UUID, userId: UUID): Boolean {
        val count = jdbc.sql(
            """
            SELECT count(*)::int AS cnt
            FROM coupon_redemptions
            WHERE coupon_id = :couponId AND user_id = :userId
            """.trimIndent(),
        )
            .param("couponId", couponId)
            .param("userId", userId)
            .query { rs, _ -> rs.getInt("cnt") }
            .single()
        return count > 0
    }

    override fun saveRedemption(redemption: CouponRedemption) {
        jdbc.sql(
            """
            INSERT INTO coupon_redemptions (coupon_id, user_id, redeemed_at, platform)
            VALUES (:couponId, :userId, :redeemedAt, :platform)
            ON CONFLICT (coupon_id, user_id) DO NOTHING
            """.trimIndent(),
        )
            .param("couponId", redemption.couponId)
            .param("userId", redemption.userId)
            .param("redeemedAt", Timestamp.from(redemption.redeemedAt))
            .param("platform", redemption.platform.name)
            .update()
    }

    override fun countRedemptions(couponId: UUID): Int =
        jdbc.sql("SELECT count(*)::int FROM coupon_redemptions WHERE coupon_id = :couponId")
            .param("couponId", couponId)
            .query(Int::class.java)
            .single()

    companion object {
        internal const val COLUMNS =
            "id, code, discount_percent, duration_cycles, valid_until, campaign, max_uses, eligibility, products, stores, ended_at"

        internal fun ResultSet.toCoupon(): Coupon = Coupon(
            id = getObject("id", UUID::class.java),
            code = getString("code"),
            discountPercent = getInt("discount_percent"),
            durationCycles = getObject("duration_cycles")?.let { (it as Number).toInt() },
            validUntil = getTimestamp("valid_until")?.toInstant(),
            campaign = getString("campaign"),
            maxUses = getObject("max_uses")?.let { (it as Number).toInt() },
            eligibility = strings("eligibility").mapNotNull { raw ->
                CouponEligibility.entries.firstOrNull { it.name == raw }
            }.toSet(),
            products = strings("products").mapNotNull(CouponProduct::parse).toSet(),
            stores = getBoolean("stores"),
            endedAt = getTimestamp("ended_at")?.toInstant(),
        )

        private fun ResultSet.strings(column: String): List<String> =
            (getArray(column)?.array as? Array<*>)?.filterIsInstance<String>().orEmpty()
    }
}
