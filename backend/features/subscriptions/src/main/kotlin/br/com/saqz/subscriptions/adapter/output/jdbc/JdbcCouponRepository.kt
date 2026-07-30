package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.CouponRepository
import br.com.saqz.subscriptions.domain.Coupon
import org.springframework.jdbc.core.simple.JdbcClient
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
}
