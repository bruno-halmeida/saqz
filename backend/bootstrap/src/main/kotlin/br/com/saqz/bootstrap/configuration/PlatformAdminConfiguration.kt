package br.com.saqz.bootstrap.configuration

import br.com.saqz.access.adapter.input.http.PlatformAdminMeController
import br.com.saqz.access.adapter.output.jdbc.admin.JdbcAdminAccessStatsRepository
import br.com.saqz.access.adapter.output.jdbc.admin.JdbcAdminUserDirectoryRepository
import br.com.saqz.access.adapter.output.jdbc.admin.JdbcPlatformAdminRepository
import br.com.saqz.access.application.admin.AdminAccessStats
import br.com.saqz.access.application.admin.AdminUserDirectory
import br.com.saqz.access.application.admin.PlatformAdminLookup
import br.com.saqz.adminweb.http.AdminGroupsController
import br.com.saqz.adminweb.http.AdminOverviewController
import br.com.saqz.adminweb.http.AdminUsersController
import br.com.saqz.groups.adapter.output.jdbc.admin.JdbcAdminGroupDirectoryRepository
import br.com.saqz.groups.adapter.output.jdbc.admin.JdbcAdminGroupStatsRepository
import br.com.saqz.groups.application.admin.AdminGroupDirectory
import br.com.saqz.groups.application.admin.AdminGroupStats
import br.com.saqz.adminweb.http.AdminSubscriptionsController
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcAdminRevenueStatsRepository
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcAdminSubscriptionDirectoryRepository
import br.com.saqz.subscriptions.application.AdminRevenueStats
import br.com.saqz.subscriptions.application.AdminSubscriptionCanceler
import br.com.saqz.subscriptions.application.AdminSubscriptionDirectory
import br.com.saqz.subscriptions.application.CancelSubscription
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

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
    fun adminUserDirectory(dataSource: DataSource): AdminUserDirectory =
        JdbcAdminUserDirectoryRepository(dataSource)

    @Bean
    fun adminUsersController(directory: AdminUserDirectory) = AdminUsersController(directory)

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
    fun adminOverviewController(
        accessStats: AdminAccessStats,
        groupStats: AdminGroupStats,
        revenueStats: AdminRevenueStats,
    ) = AdminOverviewController(accessStats, groupStats, revenueStats)
}
