package br.com.saqz.bootstrap.configuration

import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.application.session.BootstrapSessionResult
import br.com.saqz.access.adapter.input.http.AccountSuspendedException
import br.com.saqz.groups.adapter.input.http.InvalidDisplayNameException
import br.com.saqz.subscriptions.adapter.input.http.CheckoutLoginController
import br.com.saqz.subscriptions.adapter.input.http.SubscriptionActorResolver
import br.com.saqz.subscriptions.adapter.input.http.SubscriptionPurchaseInformationController
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcCheckoutLoginTokenStore
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcPurchaseInformationEmailStore
import br.com.saqz.subscriptions.adapter.output.mail.SmtpPurchaseInformationSender
import br.com.saqz.subscriptions.application.CheckoutIdentitySessions
import br.com.saqz.subscriptions.application.CheckoutLoginLinkFactory
import br.com.saqz.subscriptions.application.CheckoutLoginTokens
import br.com.saqz.subscriptions.application.PurchaseInformationReservationPort
import br.com.saqz.subscriptions.application.PurchaseInformationSender
import br.com.saqz.subscriptions.application.PurchaseInformationOperationalLog
import br.com.saqz.subscriptions.application.RedeemCheckoutLogin
import br.com.saqz.subscriptions.application.SendPurchaseInformation
import br.com.saqz.subscriptions.application.SubscriptionEmailLookup
import com.google.firebase.FirebaseApp
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.mail.javamail.JavaMailSender
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.datasource.url")
class PurchaseInformationConfiguration {
    @Bean
    fun purchaseInformationEmailStore(dataSource: DataSource) = JdbcPurchaseInformationEmailStore(dataSource)

    @Bean
    fun checkoutLoginTokens(dataSource: DataSource): CheckoutLoginTokens = JdbcCheckoutLoginTokenStore(dataSource)

    @Bean
    fun checkoutLoginLinkFactory(
        tokens: CheckoutLoginTokens,
        @Value("\${saqz.subscription.purchase-url}") purchaseUrl: String,
    ) = CheckoutLoginLinkFactory(tokens, purchaseUrl)

    @Bean
    fun checkoutIdentitySessions(
        firebaseApp: FirebaseApp,
        dataSource: DataSource,
    ): CheckoutIdentitySessions = FirebaseCheckoutSessions(firebaseApp, dataSource)

    @Bean
    fun redeemCheckoutLogin(
        tokens: CheckoutLoginTokens,
        sessions: CheckoutIdentitySessions,
        clock: Clock,
    ) = RedeemCheckoutLogin(tokens, sessions, clock)

    @Bean
    fun checkoutLoginController(redeem: RedeemCheckoutLogin) = CheckoutLoginController(redeem)

    @Bean
    fun purchaseInformationSender(
        mailSender: JavaMailSender,
        @Value("\${saqz.mail.from}") mailFrom: String,
        @Value("\${saqz.subscription.purchase-url}") purchaseUrl: String,
    ): PurchaseInformationSender = SmtpPurchaseInformationSender(mailSender, safeMailFrom(mailFrom), purchaseUrl)

    @Bean
    fun purchaseInformationOperationalLog(): PurchaseInformationOperationalLog {
        val logger = LoggerFactory.getLogger(SendPurchaseInformation::class.java)
        return PurchaseInformationOperationalLog { ownerUserId, reservationToken, type, cause ->
            logger.warn(
                "purchase-information failure ownerUserId={} reservationToken={} type={} cause={}",
                ownerUserId,
                reservationToken ?: "-",
                type,
                cause,
            )
        }
    }

    @Bean
    fun sendPurchaseInformation(
        emailLookup: SubscriptionEmailLookup,
        reservations: PurchaseInformationReservationPort,
        sender: PurchaseInformationSender,
        checkoutLinks: CheckoutLoginLinkFactory,
        clock: Clock,
        operationalLog: PurchaseInformationOperationalLog,
    ) = SendPurchaseInformation(emailLookup, reservations, sender, checkoutLinks, clock, operationalLog)

    @Bean
    fun subscriptionActorResolver(
        dataSource: DataSource,
        bootstrapSession: BootstrapSession,
    ): SubscriptionActorResolver {
        val jdbc = JdbcClient.create(dataSource)
        return SubscriptionActorResolver { identity ->
            when (val existing = findExistingUser(jdbc, identity.subject)) {
                null -> when (val result = bootstrapSession.execute(identity)) {
                    BootstrapSessionResult.InvalidDisplayName -> throw InvalidDisplayNameException()
                    BootstrapSessionResult.Suspended -> throw AccountSuspendedException()
                    is BootstrapSessionResult.Success -> result.session.user.id
                }
                else -> if (existing.suspendedAt != null) {
                    throw AccountSuspendedException()
                } else {
                    existing.id
                }
            }
        }
    }

    @Bean
    fun subscriptionPurchaseInformationController(
        actors: SubscriptionActorResolver,
        sendPurchaseInformation: SendPurchaseInformation,
    ) = SubscriptionPurchaseInformationController(actors, sendPurchaseInformation)

    private fun safeMailFrom(value: String): String {
        require(value.length <= 254 && MAIL_FROM.matches(value)) {
            "saqz.mail.from must be a single safe e-mail address"
        }
        return value
    }

    private fun findExistingUser(jdbc: JdbcClient, subject: String): ExistingUser? = jdbc.sql(
        """
        SELECT id, suspended_at
        FROM access_users
        WHERE firebase_subject = :subject
          AND deleted_at IS NULL
        """.trimIndent(),
    )
        .param("subject", subject)
        .query { result, _ ->
            ExistingUser(
                id = result.getObject("id", UUID::class.java),
                suspendedAt = result.getObject("suspended_at", OffsetDateTime::class.java),
            )
        }
        .optional()
        .orElse(null)

    private data class ExistingUser(
        val id: UUID,
        val suspendedAt: OffsetDateTime?,
    )

    private companion object {
        val MAIL_FROM = Regex("[^@\\s]+@[^@\\s]+")
    }
}
