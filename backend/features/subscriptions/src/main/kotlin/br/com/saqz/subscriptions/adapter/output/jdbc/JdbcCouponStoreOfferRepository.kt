package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.CouponStoreOfferRepository
import br.com.saqz.subscriptions.domain.CouponProduct
import br.com.saqz.subscriptions.domain.CouponStore
import br.com.saqz.subscriptions.domain.CouponStoreOffer
import br.com.saqz.subscriptions.domain.CouponStoreOfferState
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource

class JdbcCouponStoreOfferRepository(dataSource: DataSource) : CouponStoreOfferRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun insertAll(offers: List<CouponStoreOffer>) {
        offers.forEach { offer ->
            jdbc.sql(
                """
                INSERT INTO coupon_store_offers (id, coupon_id, store, plan, cycle, product_id, base_plan_id, offer_id,
                    custom_code, store_price_cents, state, message, attempts)
                VALUES (:id, :couponId, :store, :plan, :cycle, :productId, :basePlanId, :offerId,
                    :customCode, :storePriceCents, :state, :message, 0)
                """.trimIndent(),
            )
                .param("id", offer.id)
                .param("couponId", offer.couponId)
                .param("store", offer.store.name)
                .param("plan", offer.product.plan.name)
                .param("cycle", offer.product.cycle.name)
                .param("productId", offer.productId)
                .param("basePlanId", offer.basePlanId)
                .param("offerId", offer.offerId)
                .param("customCode", offer.customCode)
                .param("storePriceCents", offer.storePriceCents)
                .param("state", offer.state.name)
                .param("message", offer.message)
                .update()
        }
    }

    override fun findByCoupon(couponId: UUID): List<CouponStoreOffer> =
        jdbc.sql("SELECT * FROM coupon_store_offers WHERE coupon_id = :couponId ORDER BY store, plan, cycle")
            .param("couponId", couponId)
            .query { rs, _ -> rs.toOffer() }
            .list()

    override fun find(couponId: UUID, store: CouponStore, plan: String, cycle: String): CouponStoreOffer? =
        jdbc.sql(
            "SELECT * FROM coupon_store_offers WHERE coupon_id = :couponId AND store = :store AND plan = :plan AND cycle = :cycle",
        )
            .param("couponId", couponId)
            .param("store", store.name)
            .param("plan", plan)
            .param("cycle", cycle)
            .query { rs, _ -> rs.toOffer() }
            .optional()
            .orElse(null)

    override fun findWork(now: Instant, limit: Int): List<CouponStoreOffer> =
        jdbc.sql(
            """
            SELECT * FROM coupon_store_offers
            WHERE state IN ('PENDING', 'CREATING', 'WAITING_STORE', 'DEACTIVATING')
              AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
            ORDER BY created_at, id
            LIMIT :limit
            """.trimIndent(),
        )
            .param("now", now.atOffset(ZoneOffset.UTC))
            .param("limit", limit)
            .query { rs, _ -> rs.toOffer() }
            .list()

    override fun save(offer: CouponStoreOffer) {
        jdbc.sql(
            """
            UPDATE coupon_store_offers SET
                asc_subscription_id = :ascSubscriptionId, asc_offer_code_id = :ascOfferCodeId,
                asc_custom_code_id = :ascCustomCodeId, store_price_cents = :storePriceCents, state = :state,
                message = :message, attempts = :attempts, next_attempt_at = :nextAttemptAt,
                store_created_at = :storeCreatedAt, activated_at = :activatedAt, deactivated_at = :deactivatedAt,
                updated_at = now()
            WHERE id = :id
            """.trimIndent(),
        )
            .param("id", offer.id)
            .param("ascSubscriptionId", offer.ascSubscriptionId)
            .param("ascOfferCodeId", offer.ascOfferCodeId)
            .param("ascCustomCodeId", offer.ascCustomCodeId)
            .param("storePriceCents", offer.storePriceCents)
            .param("state", offer.state.name)
            .param("message", offer.message)
            .param("attempts", offer.attempts)
            .param("nextAttemptAt", offer.nextAttemptAt?.atOffset(ZoneOffset.UTC))
            .param("storeCreatedAt", offer.storeCreatedAt?.atOffset(ZoneOffset.UTC))
            .param("activatedAt", offer.activatedAt?.atOffset(ZoneOffset.UTC))
            .param("deactivatedAt", offer.deactivatedAt?.atOffset(ZoneOffset.UTC))
            .update()
    }

    override fun findByGooglePlayOffer(productId: String, basePlanId: String, offerId: String): CouponStoreOffer? =
        jdbc.sql(
            """
            SELECT * FROM coupon_store_offers
            WHERE store = 'GOOGLE_PLAY' AND product_id = :productId AND base_plan_id = :basePlanId AND offer_id = :offerId
            ORDER BY created_at DESC LIMIT 1
            """.trimIndent(),
        )
            .param("productId", productId)
            .param("basePlanId", basePlanId)
            .param("offerId", offerId)
            .query { rs, _ -> rs.toOffer() }
            .optional()
            .orElse(null)

    override fun findByAppStoreOffer(productId: String, offerIdentifier: String): CouponStoreOffer? =
        jdbc.sql(
            """
            SELECT * FROM coupon_store_offers
            WHERE store = 'APP_STORE' AND product_id = :productId AND upper(custom_code) = upper(:offer)
            ORDER BY created_at DESC LIMIT 1
            """.trimIndent(),
        )
            .param("productId", productId)
            .param("offer", offerIdentifier)
            .query { rs, _ -> rs.toOffer() }
            .optional()
            .orElse(null)

    private fun ResultSet.toOffer() = CouponStoreOffer(
        id = getObject("id", UUID::class.java),
        couponId = getObject("coupon_id", UUID::class.java),
        store = CouponStore.valueOf(getString("store")),
        product = CouponProduct(Plan.valueOf(getString("plan")), SubscriptionCycle.valueOf(getString("cycle"))),
        productId = getString("product_id"),
        basePlanId = getString("base_plan_id"),
        offerId = getString("offer_id"),
        ascSubscriptionId = getString("asc_subscription_id"),
        ascOfferCodeId = getString("asc_offer_code_id"),
        customCode = getString("custom_code"),
        ascCustomCodeId = getString("asc_custom_code_id"),
        storePriceCents = getObject("store_price_cents")?.let { (it as Number).toLong() },
        state = CouponStoreOfferState.valueOf(getString("state")),
        message = getString("message"),
        attempts = getInt("attempts"),
        nextAttemptAt = getTimestamp("next_attempt_at")?.toInstant(),
        storeCreatedAt = getTimestamp("store_created_at")?.toInstant(),
        activatedAt = getTimestamp("activated_at")?.toInstant(),
        deactivatedAt = getTimestamp("deactivated_at")?.toInstant(),
    )
}
