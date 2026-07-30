package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.CouponRepository
import br.com.saqz.subscriptions.domain.Coupon
import br.com.saqz.subscriptions.domain.CouponRedemption
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.util.UUID
import javax.sql.DataSource

class JdbcCouponRepository(
    dataSource: DataSource,
) : CouponRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun findByCode(code: String): Coupon? =
        jdbc.sql(
            """
            SELECT id, code, discount_percent, duration_cycles, valid_until
            FROM coupons
            WHERE lower(code) = lower(:code)
            """.trimIndent(),
        )
            .param("code", code)
            .query { rs, _ ->
                Coupon(
                    id = rs.getObject("id", UUID::class.java),
                    code = rs.getString("code"),
                    discountPercent = rs.getInt("discount_percent"),
                    durationCycles = rs.getObject("duration_cycles") as Int?,
                    validUntil = rs.getTimestamp("valid_until")?.toInstant(),
                )
            }
            .optional()
            .orElse(null)

    override fun findById(couponId: UUID): Coupon? =
        jdbc.sql(
            """
            SELECT id, code, discount_percent, duration_cycles, valid_until
            FROM coupons
            WHERE id = :couponId
            """.trimIndent(),
        )
            .param("couponId", couponId)
            .query { rs, _ ->
                Coupon(
                    id = rs.getObject("id", UUID::class.java),
                    code = rs.getString("code"),
                    discountPercent = rs.getInt("discount_percent"),
                    durationCycles = rs.getObject("duration_cycles") as Int?,
                    validUntil = rs.getTimestamp("valid_until")?.toInstant(),
                )
            }
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
            INSERT INTO coupon_redemptions (coupon_id, user_id, redeemed_at)
            VALUES (:couponId, :userId, :redeemedAt)
            """.trimIndent(),
        )
            .param("couponId", redemption.couponId)
            .param("userId", redemption.userId)
            .param("redeemedAt", Timestamp.from(redemption.redeemedAt))
            .update()
    }
}
