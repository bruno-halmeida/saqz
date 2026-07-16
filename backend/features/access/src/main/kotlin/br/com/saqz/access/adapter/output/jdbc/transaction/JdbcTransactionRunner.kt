package br.com.saqz.access.adapter.output.jdbc.transaction

import br.com.saqz.access.application.group.create.TransactionRunner
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import javax.sql.DataSource

class JdbcTransactionRunner(
    dataSource: DataSource,
) : TransactionRunner {
    private val transaction = TransactionTemplate(DataSourceTransactionManager(dataSource)).apply {
        isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
    }

    override fun <T> inTransaction(block: () -> T): T = transaction.execute { block() }
}
