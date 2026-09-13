package br.com.saqz.bootstrap.configuration

import br.com.saqz.access.application.admin.PlatformAdminLookup
import br.com.saqz.adminweb.http.AdminReceivablesOperationsController
import br.com.saqz.receivables.adapter.input.http.PlanNoticeAudience
import br.com.saqz.receivables.adapter.input.http.PublicReceivablesController
import br.com.saqz.receivables.adapter.input.http.ReceivablesNoticeActorResolver
import br.com.saqz.receivables.adapter.input.http.ReceivablesNoticesController
import br.com.saqz.receivables.adapter.output.jdbc.JdbcOperationalNotices
import br.com.saqz.receivables.adapter.output.jdbc.JdbcOperationalReceivables
import br.com.saqz.receivables.adapter.output.jdbc.JdbcExternalResidualCostLedger
import br.com.saqz.receivables.adapter.output.jdbc.JdbcPaymentExecution
import br.com.saqz.receivables.application.*
import br.com.saqz.subscriptions.adapter.input.http.SubscriptionActorResolver
import br.com.saqz.subscriptions.application.SubscriptionRepository
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import javax.sql.DataSource

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.datasource.url")
class ReceivablesOperationsConfiguration {
    @Bean
    fun operationalReceivables(dataSource: DataSource) = JdbcOperationalReceivables(dataSource)

    @Bean
    fun operationalNotices(dataSource: DataSource) = JdbcOperationalNotices(dataSource)

    @Bean
    fun externalResidualCostLedger(dataSource: DataSource) = JdbcExternalResidualCostLedger(dataSource)

    @Bean
    fun reconcileExternalResidualCost(ledger: JdbcExternalResidualCostLedger) = ReconcileExternalResidualCost(ledger)

    @Bean
    fun operationalRecoveryProbe(
        payments: ObjectProvider<JdbcPaymentExecution>,
        store: JdbcOperationalReceivables,
    ) = OperationalRecoveryProbe { operation ->
        // Administrative recovery observes existing provider facts; it cannot submit or cancel a payment.
        // No provider bean means the result remains unknown; never substitute a new cash operation.
        payments.getIfAvailable()?.reconcileObserved(operation.resourceId)
        val status = store.detail(operation.id)?.operation?.status ?: OperationStatus.UNKNOWN
        OperationalRecoveryObservation(status)
    }

    @Bean
    fun recoverOperationalFailure(
        store: JdbcOperationalReceivables,
        probe: OperationalRecoveryProbe,
        clock: Clock,
    ) = RecoverOperationalFailure(store, probe, clock)

    @Bean
    fun adminReceivablesOperationsController(
        admins: PlatformAdminLookup,
        store: JdbcOperationalReceivables,
        recovery: RecoverOperationalFailure,
        notices: JdbcOperationalNotices,
        clock: Clock,
    ) = AdminReceivablesOperationsController(admins, store, recovery, notices, clock)

    @Bean
    fun publicReceivablesController(conditions: FinancialConditions, clock: Clock) =
        PublicReceivablesController(conditions, clock)

    @Bean
    fun receivablesNoticesController(
        actors: SubscriptionActorResolver,
        subscriptions: SubscriptionRepository,
        notices: JdbcOperationalNotices,
        clock: Clock,
    ) = ReceivablesNoticesController(
        ReceivablesNoticeActorResolver { actors.resolve(it) },
        PlanNoticeAudience { subscriptions.findByOwnerUserId(it) != null },
        notices,
        clock,
    )
}
