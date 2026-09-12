package br.com.saqz.bootstrap.configuration

import br.com.saqz.groups.adapter.output.jdbc.plan.JdbcOwnerGroupHistory
import br.com.saqz.sharedkernel.subscription.GroupCreationTrial
import br.com.saqz.sharedkernel.subscription.OwnerGroupHistory
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcOrganizerTrialRepository
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcPaidSubscriptionHistory
import br.com.saqz.subscriptions.application.OrganizerTrialRepository
import br.com.saqz.subscriptions.application.PaidSubscriptionHistory
import br.com.saqz.subscriptions.application.StartOrganizerTrial
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import javax.sql.DataSource

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.datasource.url")
class OrganizerTrialConfiguration {
    @Bean
    fun trialGroupWriteAccess(
        groups: br.com.saqz.sharedkernel.subscription.GroupPlanOwnerLookup,
        trials: br.com.saqz.sharedkernel.subscription.OrganizerTrialAccessLookup,
    ) = br.com.saqz.sharedkernel.subscription.GroupWriteAccess { groupId ->
        groups.ownerOf(groupId)?.let { !trials.forOwner(it).readOnly } ?: false
    }

    @Bean
    fun trialGroupWebGuard(
        actors: br.com.saqz.sharedkernel.actor.AuthenticatedActorResolver,
        groups: br.com.saqz.sharedkernel.subscription.GroupPlanOwnerLookup,
        trials: br.com.saqz.sharedkernel.subscription.OrganizerTrialAccessLookup,
    ): org.springframework.web.servlet.config.annotation.WebMvcConfigurer =
        object : org.springframework.web.servlet.config.annotation.WebMvcConfigurer {
            override fun addInterceptors(registry: org.springframework.web.servlet.config.annotation.InterceptorRegistry) {
                registry.addInterceptor(br.com.saqz.trials.http.TrialGroupWriteInterceptor(actors, groups, trials))
                    .addPathPatterns("/api/groups/**")
            }
        }

    @Bean
    fun groupPlanOwnerLookup(dataSource: DataSource): br.com.saqz.sharedkernel.subscription.GroupPlanOwnerLookup =
        br.com.saqz.groups.adapter.output.jdbc.plan.JdbcGroupPlanOwnerLookup(dataSource)

    @Bean
    fun organizerTrialAccess(
        trials: OrganizerTrialRepository,
        paid: br.com.saqz.subscriptions.application.SubscriptionPlanLookup,
        eligibility: GroupCreationTrial,
        groups: br.com.saqz.sharedkernel.subscription.OwnedGroupCounter,
        limits: br.com.saqz.sharedkernel.subscription.SubscriptionLimits,
        clock: Clock,
    ): br.com.saqz.sharedkernel.subscription.OrganizerTrialAccessLookup =
        br.com.saqz.subscriptions.application.GetOrganizerTrial(trials, paid, eligibility, groups, limits, clock)

    @Bean
    fun organizerTrialController(
        actors: br.com.saqz.sharedkernel.actor.AuthenticatedActorResolver,
        trials: br.com.saqz.sharedkernel.subscription.OrganizerTrialAccessLookup,
        groups: br.com.saqz.sharedkernel.subscription.GroupPlanOwnerLookup,
        @org.springframework.beans.factory.annotation.Value("\${saqz.branch.domain}") branchDomain: String,
    ): br.com.saqz.trials.http.OrganizerTrialController {
        val domain = java.net.URI(branchDomain)
        require(domain.scheme == "https" && !domain.host.isNullOrBlank() && domain.userInfo == null && domain.port == -1)
        require(domain.path.isNullOrEmpty() || domain.path == "/")
        require(domain.query == null && domain.fragment == null)
        return br.com.saqz.trials.http.OrganizerTrialController(
            actors, trials, groups, domain.toString().trimEnd('/') + "/?%24ios_nativelink=true",
        )
    }

    @Bean
    fun organizerTrialRepository(dataSource: DataSource): OrganizerTrialRepository = JdbcOrganizerTrialRepository(dataSource)

    @Bean
    fun ownerGroupHistory(dataSource: DataSource): OwnerGroupHistory = JdbcOwnerGroupHistory(dataSource)

    @Bean
    fun paidSubscriptionHistory(dataSource: DataSource): PaidSubscriptionHistory = JdbcPaidSubscriptionHistory(dataSource)

    @Bean
    fun groupCreationTrial(trials: OrganizerTrialRepository, history: OwnerGroupHistory, paid: PaidSubscriptionHistory, clock: Clock): GroupCreationTrial =
        StartOrganizerTrial(trials, history, paid, clock)
}
