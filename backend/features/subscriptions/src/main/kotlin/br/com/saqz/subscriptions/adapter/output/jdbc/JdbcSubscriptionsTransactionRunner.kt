package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.SubscriptionsTransactionRunner
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import javax.sql.DataSource

class JdbcSubscriptionsTransactionRunner(
    dataSource: DataSource,
) : SubscriptionsTransactionRunner {
    private val transaction = TransactionTemplate(DataSourceTransactionManager(dataSource)).apply {
        isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
    }

    // O bloco pode devolver nulo (notificação de loja sem dono, nada a cancelar): o `!!` virava NPE e 500 no webhook.
    @Suppress("UNCHECKED_CAST")
    override fun <T> inTransaction(block: () -> T): T = transaction.execute { block() } as T
}
