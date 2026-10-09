package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.AppStoreNotification
import br.com.saqz.subscriptions.application.AppStoreNotificationStore
import br.com.saqz.subscriptions.application.AppStoreSubscriptionRepository
import br.com.saqz.subscriptions.application.AppStoreTransactionRecord
import br.com.saqz.subscriptions.domain.AppStoreEnvironment
import br.com.saqz.subscriptions.domain.AppStoreProduct
import br.com.saqz.subscriptions.domain.AppStoreSubscription
import br.com.saqz.subscriptions.domain.AppStoreTransaction
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class JdbcAppStoreSubscriptionRepository(dataSource: DataSource) : AppStoreSubscriptionRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun insertIfAbsent(subscription: AppStoreSubscription) {
        jdbc.sql(
            """
            INSERT INTO app_store_subscriptions (
                original_transaction_id, owner_user_id, environment, product_id, plan, cycle,
                latest_transaction_id, latest_purchase_date, latest_signed_at, expires_at, revoked_at,
                auto_renew, auto_renew_product_id, auto_renew_plan, auto_renew_changed_at,
                in_billing_retry, grace_period_expires_at, renewal_signed_at
            ) VALUES (
                :originalTransactionId, :ownerUserId, :environment, :productId, :plan, :cycle,
                :latestTransactionId, :latestPurchaseDate, :latestSignedAt, :expiresAt, :revokedAt,
                :autoRenew, :autoRenewProductId, :autoRenewPlan, :autoRenewChangedAt,
                :inBillingRetry, :gracePeriodExpiresAt, :renewalSignedAt
            )
            ON CONFLICT (original_transaction_id) DO NOTHING
            """.trimIndent(),
        ).bind(subscription).update()
    }

    override fun findForUpdate(originalTransactionId: String): AppStoreSubscription? = jdbc.sql(
        "SELECT $COLUMNS FROM app_store_subscriptions WHERE original_transaction_id = :id FOR UPDATE",
    ).param("id", originalTransactionId).query { rs, _ -> map(rs) }.optional().orElse(null)

    override fun save(subscription: AppStoreSubscription) {
        jdbc.sql(
            """
            UPDATE app_store_subscriptions SET
                owner_user_id = :ownerUserId, product_id = :productId, plan = :plan, cycle = :cycle,
                latest_transaction_id = :latestTransactionId, latest_purchase_date = :latestPurchaseDate,
                latest_signed_at = :latestSignedAt, expires_at = :expiresAt, revoked_at = :revokedAt,
                auto_renew = :autoRenew, auto_renew_product_id = :autoRenewProductId,
                auto_renew_plan = :autoRenewPlan, auto_renew_changed_at = :autoRenewChangedAt,
                in_billing_retry = :inBillingRetry, grace_period_expires_at = :gracePeriodExpiresAt,
                renewal_signed_at = :renewalSignedAt, updated_at = now()
            WHERE original_transaction_id = :originalTransactionId
            """.trimIndent(),
        ).bind(subscription).update()
    }

    override fun findByOwner(ownerUserId: UUID): List<AppStoreSubscription> = jdbc.sql(
        "SELECT $COLUMNS FROM app_store_subscriptions WHERE owner_user_id = :owner",
    ).param("owner", ownerUserId).query { rs, _ -> map(rs) }.list()

    override fun ownerExists(ownerUserId: UUID): Boolean = jdbc.sql(
        "SELECT EXISTS (SELECT 1 FROM access_users WHERE id = :owner)",
    ).param("owner", ownerUserId).query(Boolean::class.java).single()

    override fun recordTransaction(transaction: AppStoreTransaction, ownerUserId: UUID) {
        jdbc.sql(
            """
            INSERT INTO app_store_transactions (
                transaction_id, original_transaction_id, owner_user_id, product_id, purchase_date,
                expires_at, price_millis, currency, revoked_at, environment
            ) VALUES (
                :transactionId, :originalTransactionId, :ownerUserId, :productId, :purchaseDate,
                :expiresAt, :priceMillis, :currency, :revokedAt, :environment
            )
            ON CONFLICT (transaction_id) DO UPDATE SET revoked_at = EXCLUDED.revoked_at
            """.trimIndent(),
        )
            .param("transactionId", transaction.transactionId)
            .param("originalTransactionId", transaction.originalTransactionId)
            .param("ownerUserId", ownerUserId)
            .param("productId", transaction.productId)
            .param("purchaseDate", Timestamp.from(transaction.purchaseDate))
            .param("expiresAt", transaction.expiresDate?.let(Timestamp::from))
            .param("priceMillis", transaction.priceMillis)
            .param("currency", transaction.currency)
            .param("revokedAt", transaction.revocationDate?.let(Timestamp::from))
            .param("environment", transaction.environment.name)
            .update()
    }

    override fun listTransactionsForOwner(ownerUserId: UUID, limit: Int): List<AppStoreTransactionRecord> = jdbc.sql(
        """
        SELECT transaction_id, purchase_date, price_millis, currency, revoked_at, created_at
        FROM app_store_transactions
        WHERE owner_user_id = :owner AND revoked_at IS NULL
        ORDER BY purchase_date DESC
        LIMIT :limit
        """.trimIndent(),
    ).param("owner", ownerUserId).param("limit", limit).query { rs, _ ->
        AppStoreTransactionRecord(
            transactionId = rs.getString("transaction_id"),
            purchaseDate = rs.getTimestamp("purchase_date").toInstant(),
            priceMillis = rs.getObject("price_millis") as Long?,
            currency = rs.getString("currency"),
            revokedAt = rs.instant("revoked_at"),
            recordedAt = rs.getTimestamp("created_at").toInstant(),
        )
    }.list()

    private fun JdbcClient.StatementSpec.bind(subscription: AppStoreSubscription): JdbcClient.StatementSpec = this
        .param("originalTransactionId", subscription.originalTransactionId)
        .param("ownerUserId", subscription.ownerUserId)
        .param("environment", subscription.environment.name)
        .param("productId", subscription.product.productId)
        // Enums nativos do Postgres (V39): VARCHAR não converte sozinho.
        .param("plan", subscription.product.plan.name, Types.OTHER)
        .param("cycle", subscription.product.cycle.name, Types.OTHER)
        .param("latestTransactionId", subscription.latestTransactionId)
        .param("latestPurchaseDate", Timestamp.from(subscription.latestPurchaseDate))
        .param("latestSignedAt", Timestamp.from(subscription.latestSignedAt))
        .param("expiresAt", Timestamp.from(subscription.expiresAt))
        .param("revokedAt", subscription.revokedAt?.let(Timestamp::from))
        .param("autoRenew", subscription.autoRenew)
        .param("autoRenewProductId", subscription.autoRenewProductId)
        .param("autoRenewPlan", subscription.autoRenewProduct?.plan?.name, Types.OTHER)
        .param("autoRenewChangedAt", subscription.autoRenewChangedAt?.let(Timestamp::from))
        .param("inBillingRetry", subscription.inBillingRetry)
        .param("gracePeriodExpiresAt", subscription.gracePeriodExpiresAt?.let(Timestamp::from))
        .param("renewalSignedAt", subscription.renewalSignedAt?.let(Timestamp::from))

    private fun map(rs: ResultSet) = AppStoreSubscription(
        originalTransactionId = rs.getString("original_transaction_id"),
        ownerUserId = rs.getObject("owner_user_id", UUID::class.java),
        environment = AppStoreEnvironment.valueOf(rs.getString("environment")),
        product = checkNotNull(AppStoreProduct.fromProductId(rs.getString("product_id"))),
        latestTransactionId = rs.getString("latest_transaction_id"),
        latestPurchaseDate = rs.getTimestamp("latest_purchase_date").toInstant(),
        latestSignedAt = rs.getTimestamp("latest_signed_at").toInstant(),
        expiresAt = rs.getTimestamp("expires_at").toInstant(),
        revokedAt = rs.instant("revoked_at"),
        autoRenew = rs.getObject("auto_renew") as Boolean?,
        autoRenewProductId = rs.getString("auto_renew_product_id"),
        autoRenewChangedAt = rs.instant("auto_renew_changed_at"),
        inBillingRetry = rs.getBoolean("in_billing_retry"),
        gracePeriodExpiresAt = rs.instant("grace_period_expires_at"),
        renewalSignedAt = rs.instant("renewal_signed_at"),
    )

    private fun ResultSet.instant(column: String): Instant? = getTimestamp(column)?.toInstant()

    private companion object {
        const val COLUMNS = """
            original_transaction_id, owner_user_id, environment, product_id, latest_transaction_id,
            latest_purchase_date, latest_signed_at, expires_at, revoked_at, auto_renew,
            auto_renew_product_id, auto_renew_changed_at, in_billing_retry, grace_period_expires_at,
            renewal_signed_at
        """
    }
}

class JdbcAppStoreNotificationStore(dataSource: DataSource) : AppStoreNotificationStore {
    private val jdbc = JdbcClient.create(dataSource)

    override fun recordIfNew(notification: AppStoreNotification): Boolean = jdbc.sql(
        """
        INSERT INTO app_store_notifications (
            notification_uuid, notification_type, subtype, original_transaction_id, environment, signed_at
        ) VALUES (:uuid, :type, :subtype, :originalTransactionId, :environment, :signedAt)
        ON CONFLICT (notification_uuid) DO NOTHING
        """.trimIndent(),
    )
        .param("uuid", notification.notificationUuid)
        .param("type", notification.type)
        .param("subtype", notification.subtype)
        .param("originalTransactionId", notification.transaction?.originalTransactionId)
        .param("environment", notification.environment?.name)
        .param("signedAt", Timestamp.from(notification.signedAt))
        .update() == 1
}
