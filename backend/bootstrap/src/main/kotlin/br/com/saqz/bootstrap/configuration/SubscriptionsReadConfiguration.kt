package br.com.saqz.bootstrap.configuration

import br.com.saqz.access.adapter.input.http.AccountSuspendedException
import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.application.session.SessionActorResult
import br.com.saqz.groups.adapter.input.http.InvalidDisplayNameException
import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.sharedkernel.actor.AuthenticatedActor
import br.com.saqz.sharedkernel.actor.AuthenticatedActorResolver
import br.com.saqz.sharedkernel.subscription.OwnedGroupCounter
import br.com.saqz.subscriptions.adapter.input.http.CouponController
import br.com.saqz.subscriptions.adapter.input.http.PlanController
import br.com.saqz.subscriptions.adapter.input.http.SubscriptionQueryController
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcCouponRepository
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcPlanCourtesyRepository
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcSubscriptionPastDueRepository
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcSubscriptionRepository
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcSubscriptionsTransactionRunner
import br.com.saqz.subscriptions.application.AppStoreSubscriptionRepository
import br.com.saqz.subscriptions.application.AsaasGateway
import br.com.saqz.subscriptions.application.CancelUnpaidWebCheckout
import br.com.saqz.subscriptions.application.CouponEligibilityLookup
import br.com.saqz.subscriptions.application.CouponRepository
import br.com.saqz.subscriptions.application.CouponStoreOfferRepository
import br.com.saqz.subscriptions.application.GetMySubscription
import br.com.saqz.subscriptions.application.GooglePlaySubscriptionRepository
import br.com.saqz.subscriptions.application.ListPlans
import br.com.saqz.subscriptions.application.OverdueInvoiceResolver
import br.com.saqz.subscriptions.application.PlanCourtesyRepository
import br.com.saqz.subscriptions.application.RecoverUnconfirmedPayment
import br.com.saqz.subscriptions.application.SubscriptionRepository
import br.com.saqz.subscriptions.application.SubscriptionsTransactionRunner
import br.com.saqz.subscriptions.application.ValidateCoupon
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import javax.sql.DataSource

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.datasource.url")
class SubscriptionsReadConfiguration {
    @Bean
    fun subscriptionRepository(dataSource: DataSource): SubscriptionRepository =
        JdbcSubscriptionRepository(dataSource)

    @Bean
    fun subscriptionsTransactionRunner(dataSource: DataSource): SubscriptionsTransactionRunner =
        JdbcSubscriptionsTransactionRunner(dataSource)

    @Bean
    fun couponRepository(dataSource: DataSource): CouponRepository =
        JdbcCouponRepository(dataSource)

    @Bean
    fun planCourtesyRepository(dataSource: DataSource): PlanCourtesyRepository =
        JdbcPlanCourtesyRepository(dataSource)

    @Bean
    fun listPlans() = ListPlans()

    @Bean
    fun validateCoupon(
        coupons: CouponRepository,
        clock: Clock,
        offers: CouponStoreOfferRepository,
        eligibility: CouponEligibilityLookup,
    ) = ValidateCoupon(coupons, clock, offers, eligibility)

    @Bean
    fun getMySubscription(
        subscriptions: SubscriptionRepository,
        ownedGroups: OwnedGroupCounter,
        clock: Clock,
        recoverUnconfirmed: ObjectProvider<RecoverUnconfirmedPayment>,
        appStoreSubscriptions: AppStoreSubscriptionRepository,
        googlePlaySubscriptions: GooglePlaySubscriptionRepository,
        dataSource: DataSource,
        gateway: ObjectProvider<AsaasGateway>,
        courtesies: PlanCourtesyRepository,
    ) = GetMySubscription(
        subscriptions,
        ownedGroups,
        clock,
        recoverUnconfirmed.getIfAvailable(),
        appStoreSubscriptions,
        googlePlaySubscriptions,
        // A mesma fatura do aviso de atraso; sem o Asaas configurado vale o link do webhook guardado.
        OverdueInvoiceResolver(JdbcSubscriptionPastDueRepository(dataSource)) { gateway.ifAvailable },
        courtesies,
    )

    /** Sem o Asaas configurado (pilha local) não há o que cancelar: o caso é registrado em log e a compra segue. */
    @Bean
    fun cancelUnpaidWebCheckout(
        subscriptions: SubscriptionRepository,
        transaction: SubscriptionsTransactionRunner,
        clock: Clock,
        gateway: ObjectProvider<AsaasGateway>,
    ) = CancelUnpaidWebCheckout(subscriptions, { gateway.ifAvailable }, transaction, clock)

    @Bean
    fun authenticatedActorResolver(bootstrapSession: BootstrapSession): AuthenticatedActorResolver =
        object : AuthenticatedActorResolver {
            override fun resolve(identity: RequestIdentity): AuthenticatedActor =
                when (val result = bootstrapSession.actorId(identity)) {
                    SessionActorResult.InvalidDisplayName -> throw InvalidDisplayNameException()
                    SessionActorResult.Suspended -> throw AccountSuspendedException()
                    is SessionActorResult.Found -> AuthenticatedActor(result.userId)
                }
        }

    @Bean
    fun planController(listPlans: ListPlans) = PlanController(listPlans)

    @Bean
    fun couponController(
        validateCoupon: ValidateCoupon,
        actors: AuthenticatedActorResolver,
        @Value("\${saqz.app-store.app-apple-id:}") appAppleId: String,
    ) = CouponController(validateCoupon, actors, appAppleId)

    @Bean
    fun subscriptionQueryController(
        actors: AuthenticatedActorResolver,
        getMySubscription: GetMySubscription,
    ) = SubscriptionQueryController(actors, getMySubscription)
}
