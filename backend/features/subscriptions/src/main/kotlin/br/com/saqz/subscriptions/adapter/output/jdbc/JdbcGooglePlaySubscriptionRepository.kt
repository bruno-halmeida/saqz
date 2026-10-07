package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.GooglePlayNotificationStore
import br.com.saqz.subscriptions.application.GooglePlayOrderRecord
import br.com.saqz.subscriptions.application.GooglePlaySubscriptionRepository
import br.com.saqz.subscriptions.domain.GooglePlayProduct
import br.com.saqz.subscriptions.domain.GooglePlayState
import br.com.saqz.subscriptions.domain.GooglePlaySubscription
import br.com.saqz.subscriptions.domain.Plan
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class JdbcGooglePlaySubscriptionRepository(dataSource: DataSource) : GooglePlaySubscriptionRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun insertIfAbsent(subscription: GooglePlaySubscription) {
        jdbc.sql(
            """
            INSERT INTO google_play_subscriptions (
                purchase_token, owner_user_id, product_id, base_plan_id, plan, cycle, state, expires_at,
                auto_renew, canceled_at, latest_order_id, linked_purchase_token, acknowledged, test_purchase,
                pending_plan
            ) VALUES (
                :purchaseToken, :ownerUserId, :productId, :basePlanId, :plan, :cycle, :state, :expiresAt,
                :autoRenew, :canceledAt, :latestOrderId, :linkedPurchaseToken, :acknowledged, :testPurchase,
                :pendingPlan
            )
            ON CONFLICT (purchase_token) DO NOTHING
            """.trimIndent(),
        ).bind(subscription).update()
    }

    override fun findForUpdate(purchaseToken: String): GooglePlaySubscription? = jdbc.sql(
        "SELECT $COLUMNS FROM google_play_subscriptions WHERE purchase_token = :token FOR UPDATE",
    ).param("token", purchaseToken).query { rs, _ -> map(rs) }.optional().orElse(null)

    override fun save(subscription: GooglePlaySubscription) {
        jdbc.sql(
            """
            UPDATE google_play_subscriptions SET
                product_id = :productId, base_plan_id = :basePlanId, plan = :plan, cycle = :cycle,
                state = :state, expires_at = :expiresAt, auto_renew = :autoRenew, canceled_at = :canceledAt,
                latest_order_id = :latestOrderId, linked_purchase_token = :linkedPurchaseToken,
                acknowledged = :acknowledged, test_purchase = :testPurchase, pending_plan = :pendingPlan,
                updated_at = now()
            WHERE purchase_token = :purchaseToken
            """.trimIndent(),
        ).bind(subscription).update()
    }

    override fun findByOwner(ownerUserId: UUID): List<GooglePlaySubscription> = jdbc.sql(
        "SELECT $COLUMNS FROM google_play_subscriptions WHERE owner_user_id = :owner",
    ).param("owner", ownerUserId).query { rs, _ -> map(rs) }.list()

    override fun ownerExists(ownerUserId: UUID): Boolean = jdbc.sql(
        "SELECT EXISTS (SELECT 1 FROM access_users WHERE id = :owner)",
    ).param("owner", ownerUserId).query(Boolean::class.java).single()

    override fun supersede(purchaseToken: String, at: Instant) {
        jdbc.sql(
            """
            UPDATE google_play_subscriptions SET superseded_at = :at, updated_at = now()
            WHERE purchase_token = :token AND superseded_at IS NULL
            """.trimIndent(),
        ).param("token", purchaseToken).param("at", Timestamp.from(at)).update()
    }

    override fun markAcknowledged(purchaseToken: String) {
        jdbc.sql("UPDATE google_play_subscriptions SET acknowledged = true, updated_at = now() WHERE purchase_token = :token")
            .param("token", purchaseToken).update()
    }

    override fun recordOrder(subscription: GooglePlaySubscription) {
        val orderId = subscription.latestOrderId ?: return
        jdbc.sql(
            """
            INSERT INTO google_play_orders (order_id, purchase_token, owner_user_id, product_id, base_plan_id, test_purchase)
            VALUES (:orderId, :purchaseToken, :ownerUserId, :productId, :basePlanId, :testPurchase)
            ON CONFLICT (order_id) DO NOTHING
            """.trimIndent(),
        )
            .param("orderId", orderId)
            .param("purchaseToken", subscription.purchaseToken)
            .param("ownerUserId", subscription.ownerUserId)
            .param("productId", subscription.product.productId)
            .param("basePlanId", subscription.product.basePlanId)
            .param("testPurchase", subscription.testPurchase)
            .update()
    }

    override fun listOrdersForOwner(ownerUserId: UUID, limit: Int): List<GooglePlayOrderRecord> = jdbc.sql(
        """
        SELECT order_id, product_id, recorded_at FROM google_play_orders
        WHERE owner_user_id = :owner ORDER BY recorded_at DESC LIMIT :limit
        """.trimIndent(),
    ).param("owner", ownerUserId).param("limit", limit).query { rs, _ ->
        GooglePlayOrderRecord(
            orderId = rs.getString("order_id"),
            productId = rs.getString("product_id"),
            recordedAt = rs.getTimestamp("recorded_at").toInstant(),
        )
    }.list()

    private fun JdbcClient.StatementSpec.bind(subscription: GooglePlaySubscription): JdbcClient.StatementSpec = this
        .param("purchaseToken", subscription.purchaseToken)
        .param("ownerUserId", subscription.ownerUserId)
        .param("productId", subscription.product.productId)
        .param("basePlanId", subscription.product.basePlanId)
        // Enums nativos do Postgres (V39): VARCHAR não converte sozinho.
        .param("plan", subscription.product.plan.name, Types.OTHER)
        .param("cycle", subscription.product.cycle.name, Types.OTHER)
        .param("state", subscription.state.name)
        .param("expiresAt", Timestamp.from(subscription.expiresAt))
        .param("autoRenew", subscription.autoRenew)
        .param("canceledAt", subscription.canceledAt?.let(Timestamp::from))
        .param("latestOrderId", subscription.latestOrderId)
        .param("linkedPurchaseToken", subscription.linkedPurchaseToken)
        .param("acknowledged", subscription.acknowledged)
        .param("testPurchase", subscription.testPurchase)
        .param("pendingPlan", subscription.pendingPlan?.name, Types.OTHER)

    private fun map(rs: ResultSet) = GooglePlaySubscription(
        purchaseToken = rs.getString("purchase_token"),
        ownerUserId = rs.getObject("owner_user_id", UUID::class.java),
        product = checkNotNull(GooglePlayProduct.of(rs.getString("product_id"), rs.getString("base_plan_id"))),
        state = GooglePlayState.valueOf(rs.getString("state")),
        expiresAt = rs.getTimestamp("expires_at").toInstant(),
        autoRenew = rs.getObject("auto_renew") as Boolean?,
        canceledAt = rs.getTimestamp("canceled_at")?.toInstant(),
        latestOrderId = rs.getString("latest_order_id"),
        linkedPurchaseToken = rs.getString("linked_purchase_token"),
        supersededAt = rs.getTimestamp("superseded_at")?.toInstant(),
        acknowledged = rs.getBoolean("acknowledged"),
        testPurchase = rs.getBoolean("test_purchase"),
        pendingPlan = rs.getString("pending_plan")?.let(Plan::valueOf),
    )

    private companion object {
        const val COLUMNS = """
            purchase_token, owner_user_id, product_id, base_plan_id, state, expires_at, auto_renew,
            canceled_at, latest_order_id, linked_purchase_token, superseded_at, acknowledged, test_purchase,
            pending_plan
        """
    }
}

class JdbcGooglePlayNotificationStore(dataSource: DataSource) : GooglePlayNotificationStore {
    private val jdbc = JdbcClient.create(dataSource)

    override fun recordIfNew(messageId: String, notificationType: Int?, purchaseToken: String?): Boolean = jdbc.sql(
        """
        INSERT INTO google_play_notifications (message_id, notification_type, purchase_token)
        VALUES (:messageId, :type, :token)
        ON CONFLICT (message_id) DO NOTHING
        """.trimIndent(),
    )
        .param("messageId", messageId)
        .param("type", notificationType)
        .param("token", purchaseToken)
        .update() == 1
}
