package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcCouponRepository.Companion.toCoupon
import br.com.saqz.subscriptions.application.AdminCoupon
import br.com.saqz.subscriptions.application.AdminCouponCreate
import br.com.saqz.subscriptions.application.AdminCouponCreateResult
import br.com.saqz.subscriptions.application.AdminCouponDirectory
import br.com.saqz.subscriptions.application.AdminCouponPlatformState
import br.com.saqz.subscriptions.application.CouponCampaignRules
import br.com.saqz.subscriptions.domain.Coupon
import br.com.saqz.subscriptions.domain.CouponPlatform
import br.com.saqz.subscriptions.domain.CouponStore
import br.com.saqz.subscriptions.domain.CouponStoreOffer
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource

class JdbcAdminCouponDirectoryRepository(
    dataSource: DataSource,
) : AdminCouponDirectory {
    private val jdbc = JdbcClient.create(dataSource)
    private val offers = JdbcCouponStoreOfferRepository(dataSource)
    private val transaction = TransactionTemplate(DataSourceTransactionManager(dataSource))

    override fun list(): List<AdminCoupon> {
        val usesByPlatform = jdbc.sql("SELECT coupon_id, platform, count(*) AS n FROM coupon_redemptions GROUP BY coupon_id, platform")
            .query { rs, _ -> Triple(rs.getObject("coupon_id", UUID::class.java), CouponPlatform.valueOf(rs.getString("platform")), rs.getLong("n")) }
            .list()
            .groupBy({ it.first }, { it.second to it.third })
        return jdbc.sql(
            """
            SELECT c.${JdbcCouponRepository.COLUMNS.replace(", ", ", c.")},
                   (SELECT count(*) FROM coupon_redemptions r WHERE r.coupon_id = c.id) AS redemptions,
                   (SELECT count(*) FROM subscriptions s
                     WHERE s.coupon_id = c.id AND s.status <> 'CANCELED' AND s.canceled_at IS NULL
                       AND (s.status = 'ACTIVE' OR s.first_confirmed_at IS NOT NULL)
                       AND (c.duration_cycles IS NULL OR COALESCE(s.coupon_cycles_remaining, 0) > 0)) AS active_subscriptions
            FROM coupons c
            ORDER BY c.created_at DESC
            """.trimIndent(),
        ).query { rs, _ ->
            val coupon = rs.toCoupon()
            Triple(coupon, rs.getLong("redemptions"), rs.getLong("active_subscriptions"))
        }.list().map { (coupon, redemptions, active) ->
            AdminCoupon(
                id = coupon.id,
                code = coupon.code,
                discountPercent = coupon.discountPercent,
                durationCycles = coupon.durationCycles,
                validUntil = coupon.validUntil,
                redemptions = redemptions,
                activeSubscriptions = active,
                campaign = coupon.campaign,
                maxUses = coupon.maxUses,
                eligibility = coupon.eligibility,
                products = coupon.products.map { it.key() },
                stores = coupon.stores,
                endedAt = coupon.endedAt,
                redemptionsByPlatform = usesByPlatform[coupon.id].orEmpty().toMap(),
                platforms = if (coupon.stores) platformStates(offers.findByCoupon(coupon.id)) else emptyMap(),
            )
        }
    }

    override fun create(command: AdminCouponCreate): AdminCouponCreateResult = requireNotNull(
        transaction.execute { status ->
            val id = UUID.randomUUID()
            // Duplicidade case-insensitive: o fluxo 8 resolve cupom com lower(code) = lower(:code)
            // e espera no máximo uma linha — ON CONFLICT no índice case-sensitive não basta.
            val inserted = jdbc.sql(
                """
                INSERT INTO coupons (id, code, discount_percent, duration_cycles, valid_until, created_at,
                    campaign, max_uses, eligibility, products, stores)
                SELECT :id, :code, :discount, :cycles, :validUntil, now(),
                    :campaign, :maxUses, string_to_array(:eligibility, ',')::varchar[], string_to_array(:products, ',')::varchar[], :stores
                WHERE NOT EXISTS (SELECT 1 FROM coupons x WHERE lower(x.code) = lower(:code))
                ON CONFLICT (code) DO NOTHING
                """.trimIndent(),
            )
                .param("id", id)
                .param("code", command.code)
                .param("discount", command.discountPercent)
                .param("cycles", command.durationCycles)
                .param("validUntil", command.validUntil?.atOffset(ZoneOffset.UTC))
                .param("campaign", command.campaign)
                .param("maxUses", command.maxUses)
                .param("eligibility", command.eligibility.joinToString(",") { it.name })
                .param("products", command.products.joinToString(",") { it.key() })
                .param("stores", command.stores)
                .update()
            if (inserted == 0) {
                status.setRollbackOnly()
                return@execute AdminCouponCreateResult.DuplicateCode
            }
            val coupon = Coupon(
                id = id, code = command.code, discountPercent = command.discountPercent,
                durationCycles = command.durationCycles, validUntil = command.validUntil, campaign = command.campaign,
                maxUses = command.maxUses, eligibility = command.eligibility, products = command.products, stores = command.stores,
            )
            offers.insertAll(CouponCampaignRules.offersFor(coupon))
            AdminCouponCreateResult.Created(coupon)
        },
    )

    override fun deactivate(couponId: UUID, now: Instant): Boolean = requireNotNull(
        transaction.execute {
            val updated = jdbc.sql(
                """
                UPDATE coupons SET valid_until = LEAST(COALESCE(valid_until, :now), :now),
                    ended_at = COALESCE(ended_at, :now)
                WHERE id = :id
                """.trimIndent(),
            )
                .param("now", now.atOffset(ZoneOffset.UTC))
                .param("id", couponId)
                .update() > 0
            if (updated) requestOfferDeactivation(couponId)
            updated
        },
    )

    override fun retryFailedOffers(couponId: UUID): Int = jdbc.sql(
        """
        UPDATE coupon_store_offers o SET state = 'PENDING', attempts = 0, next_attempt_at = NULL, message = NULL, updated_at = now()
        FROM coupons c
        WHERE o.coupon_id = c.id AND c.id = :id AND o.state = 'FAILED' AND c.ended_at IS NULL
        """.trimIndent(),
    ).param("id", couponId).update()

    /** Sem nada criado na loja vai direto a DEACTIVATED; o resto o job desativa. */
    private fun requestOfferDeactivation(couponId: UUID) {
        jdbc.sql(
            """
            UPDATE coupon_store_offers SET
                state = CASE WHEN asc_offer_code_id IS NULL AND (store = 'APP_STORE' OR store_created_at IS NULL)
                             THEN 'DEACTIVATED' ELSE 'DEACTIVATING' END,
                deactivated_at = CASE WHEN asc_offer_code_id IS NULL AND (store = 'APP_STORE' OR store_created_at IS NULL)
                             THEN now() END,
                attempts = 0, next_attempt_at = NULL, updated_at = now()
            WHERE coupon_id = :id AND state NOT IN ('DEACTIVATING', 'DEACTIVATED')
            """.trimIndent(),
        ).param("id", couponId).update()
    }

    private fun platformStates(all: List<CouponStoreOffer>): Map<CouponPlatform, AdminCouponPlatformState> =
        mapOf(CouponPlatform.ANDROID to CouponStore.GOOGLE_PLAY, CouponPlatform.IOS to CouponStore.APP_STORE)
            .mapNotNull { (platform, store) ->
                val offers = all.filter { it.store == store }
                val worst = CouponStoreOffer.worst(offers) ?: return@mapNotNull null
                platform to AdminCouponPlatformState(worst, offers.firstOrNull { it.state == worst && it.message != null }?.message)
            }.toMap()
}
