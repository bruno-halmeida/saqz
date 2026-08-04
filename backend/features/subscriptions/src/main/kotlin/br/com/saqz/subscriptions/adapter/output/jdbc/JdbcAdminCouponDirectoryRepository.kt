package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.AdminCoupon
import br.com.saqz.subscriptions.application.AdminCouponCreateResult
import br.com.saqz.subscriptions.application.AdminCouponDirectory
import br.com.saqz.subscriptions.domain.Coupon
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource

class JdbcAdminCouponDirectoryRepository(
    dataSource: DataSource,
) : AdminCouponDirectory {
    private val jdbc = JdbcClient.create(dataSource)

    override fun list(): List<AdminCoupon> = jdbc.sql(
        """
        SELECT c.id, c.code, c.discount_percent, c.duration_cycles, c.valid_until,
               (SELECT count(*) FROM coupon_redemptions r WHERE r.coupon_id = c.id) AS redemptions,
               (SELECT count(*) FROM subscriptions s
                 WHERE s.coupon_id = c.id AND s.status <> 'CANCELED'
                   AND (s.coupon_cycles_remaining IS NULL OR s.coupon_cycles_remaining > 0)) AS active_subscriptions
        FROM coupons c
        ORDER BY c.created_at DESC
        """.trimIndent(),
    ).query { rs, _ ->
        AdminCoupon(
            id = rs.getObject("id", UUID::class.java),
            code = rs.getString("code"),
            discountPercent = rs.getInt("discount_percent"),
            durationCycles = rs.getObject("duration_cycles")?.let { (it as Number).toInt() },
            validUntil = rs.getObject("valid_until", OffsetDateTime::class.java)?.toInstant(),
            redemptions = rs.getLong("redemptions"),
            activeSubscriptions = rs.getLong("active_subscriptions"),
        )
    }.list()

    override fun create(
        code: String,
        discountPercent: Int,
        durationCycles: Int?,
        validUntil: Instant?,
    ): AdminCouponCreateResult {
        val id = UUID.randomUUID()
        // Duplicidade case-insensitive: o fluxo 8 resolve cupom com lower(code) = lower(:code)
        // e espera no máximo uma linha — ON CONFLICT no índice case-sensitive não basta.
        val inserted = jdbc.sql(
            """
            INSERT INTO coupons (id, code, discount_percent, duration_cycles, valid_until, created_at)
            SELECT :id, :code, :discount, :cycles, :validUntil, now()
            WHERE NOT EXISTS (SELECT 1 FROM coupons x WHERE lower(x.code) = lower(:code))
            """.trimIndent(),
        )
            .param("id", id)
            .param("code", code)
            .param("discount", discountPercent)
            .param("cycles", durationCycles)
            .param("validUntil", validUntil?.atOffset(ZoneOffset.UTC))
            .update()
        return if (inserted > 0) {
            AdminCouponCreateResult.Created(Coupon(id, code, discountPercent, durationCycles, validUntil))
        } else {
            AdminCouponCreateResult.DuplicateCode
        }
    }

    override fun deactivate(couponId: UUID, now: Instant): Boolean = jdbc.sql(
        "UPDATE coupons SET valid_until = LEAST(COALESCE(valid_until, :now), :now) WHERE id = :id",
    )
        .param("now", now.atOffset(ZoneOffset.UTC))
        .param("id", couponId)
        .update() > 0
}
