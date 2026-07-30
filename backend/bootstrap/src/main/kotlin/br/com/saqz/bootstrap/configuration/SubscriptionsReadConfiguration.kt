package br.com.saqz.bootstrap.configuration

import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.application.session.BootstrapSessionResult
import br.com.saqz.groups.adapter.input.http.InvalidDisplayNameException
import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.sharedkernel.actor.AuthenticatedActor
import br.com.saqz.sharedkernel.actor.AuthenticatedActorResolver
import br.com.saqz.sharedkernel.subscription.OwnedGroupCounter
import br.com.saqz.subscriptions.adapter.input.http.CouponController
import br.com.saqz.subscriptions.adapter.input.http.PlanController
import br.com.saqz.subscriptions.adapter.input.http.SubscriptionQueryController
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcCouponRepository
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcSubscriptionRepository
import br.com.saqz.subscriptions.application.CouponRepository
import br.com.saqz.subscriptions.application.GetMySubscription
import br.com.saqz.subscriptions.application.ListPlans
import br.com.saqz.subscriptions.application.SubscriptionRepository
import br.com.saqz.subscriptions.application.ValidateCoupon
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
    fun couponRepository(dataSource: DataSource): CouponRepository =
        JdbcCouponRepository(dataSource)

    @Bean
    fun listPlans() = ListPlans()

    @Bean
    fun validateCoupon(coupons: CouponRepository, clock: Clock) = ValidateCoupon(coupons, clock)

    @Bean
    fun getMySubscription(
        subscriptions: SubscriptionRepository,
        ownedGroups: OwnedGroupCounter,
        clock: Clock,
    ) = GetMySubscription(subscriptions, ownedGroups, clock)

    @Bean
    fun authenticatedActorResolver(bootstrapSession: BootstrapSession): AuthenticatedActorResolver =
        object : AuthenticatedActorResolver {
            override fun resolve(identity: RequestIdentity): AuthenticatedActor =
                when (val result = bootstrapSession.execute(identity)) {
                    BootstrapSessionResult.InvalidDisplayName -> throw InvalidDisplayNameException()
                    is BootstrapSessionResult.Success -> AuthenticatedActor(result.session.user.id)
                }
        }

    @Bean
    fun planController(listPlans: ListPlans) = PlanController(listPlans)

    @Bean
    fun couponController(validateCoupon: ValidateCoupon) = CouponController(validateCoupon)

    @Bean
    fun subscriptionQueryController(
        actors: AuthenticatedActorResolver,
        getMySubscription: GetMySubscription,
    ) = SubscriptionQueryController(actors, getMySubscription)
}
