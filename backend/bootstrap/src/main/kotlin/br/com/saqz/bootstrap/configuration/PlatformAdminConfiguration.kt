package br.com.saqz.bootstrap.configuration

import br.com.saqz.access.adapter.input.http.PlatformAdminMeController
import br.com.saqz.access.adapter.output.jdbc.admin.JdbcAdminAccessStatsRepository
import br.com.saqz.access.adapter.output.jdbc.admin.JdbcAdminUserDirectoryRepository
import br.com.saqz.access.adapter.output.jdbc.admin.JdbcPlatformAdminRepository
import br.com.saqz.access.application.admin.AdminAccessStats
import br.com.saqz.access.application.admin.AdminUserDirectory
import br.com.saqz.access.application.admin.PlatformAdminLookup
import br.com.saqz.adminweb.http.AdminCouponAnalyticsController
import br.com.saqz.adminweb.http.AdminCourtesiesController
import br.com.saqz.adminweb.http.AdminCouponsController
import br.com.saqz.adminweb.http.AdminPartnersController
import br.com.saqz.adminweb.http.AdminGroupsController
import br.com.saqz.adminweb.http.AdminModerationController
import br.com.saqz.adminweb.http.AdminOverviewController
import br.com.saqz.adminweb.http.AdminRevenueController
import br.com.saqz.adminweb.http.AdminSubscriptionsController
import br.com.saqz.adminweb.http.AdminUsersController
import br.com.saqz.groups.adapter.output.jdbc.admin.JdbcAdminGroupDirectoryRepository
import br.com.saqz.groups.adapter.output.jdbc.admin.JdbcAdminGroupStatsRepository
import br.com.saqz.groups.application.admin.AdminGroupDirectory
import br.com.saqz.groups.application.admin.AdminGroupStats
import br.com.saqz.groups.application.moderation.ContentModerationService
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcAdminCouponAnalytics
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcAdminCouponDirectoryRepository
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcAdminPartnerDirectory
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcAdminRevenueReader
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcAdminRevenueStatsRepository
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcAdminSubscriptionDirectoryRepository
import br.com.saqz.subscriptions.application.AdminCouponDirectory
import br.com.saqz.subscriptions.application.AdminPartnerDirectory
import br.com.saqz.subscriptions.application.PreviewCouponPrices
import br.com.saqz.subscriptions.application.AdminRevenueReader
import br.com.saqz.subscriptions.application.AdminRevenueStats
import br.com.saqz.subscriptions.application.AdminSubscriptionCanceler
import br.com.saqz.subscriptions.application.AdminSubscriptionDirectory
import br.com.saqz.subscriptions.application.CancelSubscription
import br.com.saqz.subscriptions.application.PlanCourtesies
import br.com.saqz.subscriptions.application.PlanCourtesyRepository
import br.com.saqz.subscriptions.application.SubscriptionPlanLookup
import java.time.Clock
import javax.sql.DataSource
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.datasource.url")
class PlatformAdminConfiguration {
    @Bean
    fun platformAdminLookup(dataSource: DataSource): PlatformAdminLookup =
        JdbcPlatformAdminRepository(dataSource)

    @Bean
    fun platformAdminMeController(lookup: PlatformAdminLookup) = PlatformAdminMeController(lookup)

    @Bean
    fun adminAccessStats(dataSource: DataSource): AdminAccessStats =
        JdbcAdminAccessStatsRepository(dataSource)

    @Bean
    fun adminGroupStats(dataSource: DataSource): AdminGroupStats =
        JdbcAdminGroupStatsRepository(dataSource)

    @Bean
    fun adminRevenueStats(dataSource: DataSource): AdminRevenueStats =
        JdbcAdminRevenueStatsRepository(dataSource)

    @Bean
    fun adminRevenueReader(dataSource: DataSource): AdminRevenueReader = JdbcAdminRevenueReader(dataSource)

    @Bean
    fun adminRevenueController(reader: AdminRevenueReader) = AdminRevenueController(reader)

    @Bean
    fun adminUserDirectory(dataSource: DataSource): AdminUserDirectory =
        JdbcAdminUserDirectoryRepository(dataSource)

    @Bean
    fun planCourtesies(repository: PlanCourtesyRepository, plans: SubscriptionPlanLookup, clock: Clock) =
        PlanCourtesies(repository, plans, clock)

    @Bean
    fun adminUsersController(directory: AdminUserDirectory, lookup: PlatformAdminLookup, courtesies: PlanCourtesies) =
        AdminUsersController(directory, lookup, courtesies)

    @Bean
    fun adminCourtesiesController(courtesies: PlanCourtesies, lookup: PlatformAdminLookup) =
        AdminCourtesiesController(courtesies, lookup)

    @Bean
    fun adminGroupDirectory(dataSource: DataSource): AdminGroupDirectory =
        JdbcAdminGroupDirectoryRepository(dataSource)

    @Bean
    fun adminGroupsController(directory: AdminGroupDirectory) = AdminGroupsController(directory)

    @Bean
    fun adminSubscriptionDirectory(dataSource: DataSource): AdminSubscriptionDirectory =
        JdbcAdminSubscriptionDirectoryRepository(dataSource)

    /** Delegar ao CancelSubscription do Fluxo 8; sem Asaas configurado, o port responde null (503). */
    @Bean
    fun adminSubscriptionCanceler(cancel: ObjectProvider<CancelSubscription>) =
        AdminSubscriptionCanceler { ownerUserId -> cancel.getIfAvailable()?.execute(ownerUserId) }

    @Bean
    fun adminSubscriptionsController(
        directory: AdminSubscriptionDirectory,
        canceler: AdminSubscriptionCanceler,
    ) = AdminSubscriptionsController(directory, canceler)

    @Bean
    fun adminCouponDirectory(dataSource: DataSource): AdminCouponDirectory =
        JdbcAdminCouponDirectoryRepository(dataSource)

    @Bean
    fun adminCouponsController(
        directory: AdminCouponDirectory,
        preview: ObjectProvider<PreviewCouponPrices>,
        partners: AdminPartnerDirectory,
    ) = AdminCouponsController(directory, preview = preview.getIfAvailable { PreviewCouponPrices(null) }, partnerExists = partners::exists)

    @Bean
    fun adminPartnerDirectory(dataSource: DataSource): AdminPartnerDirectory = JdbcAdminPartnerDirectory(dataSource)

    @Bean
    fun adminPartnersController(partners: AdminPartnerDirectory) = AdminPartnersController(partners)

    @Bean
    fun adminCouponAnalyticsController(dataSource: DataSource, clock: Clock) =
        AdminCouponAnalyticsController(JdbcAdminCouponAnalytics(dataSource, clock))

    @Bean
    fun adminModerationController(moderation: ContentModerationService) = AdminModerationController(moderation)

    @Bean
    fun adminOverviewController(
        accessStats: AdminAccessStats,
        groupStats: AdminGroupStats,
        revenueStats: AdminRevenueStats,
    ) = AdminOverviewController(accessStats, groupStats, revenueStats)
}
