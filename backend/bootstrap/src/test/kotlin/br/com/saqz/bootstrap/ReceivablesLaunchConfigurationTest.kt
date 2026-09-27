package br.com.saqz.bootstrap

import br.com.saqz.access.adapter.output.jdbc.admin.JdbcAdminUserDirectoryRepository
import br.com.saqz.bootstrap.configuration.ReceivablesRolloutConfiguration
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.receivables.application.ReceivablesAvailability
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.mock.env.MockEnvironment
import java.time.Clock
import java.util.UUID
import kotlin.test.assertEquals

class ReceivablesLaunchConfigurationTest {
    @Test fun `deployment defaults off even if a previous rollout enabled all users`() {
        val db = TestPostgres.migrated("classpath:db/migration", owner = this).dataSource
        JdbcClient.create(db).sql("UPDATE receivable_rollout SET backend_mode='ALL_USERS', mobile_mode='ALL_USERS'").update()
        val config = ReceivablesRolloutConfiguration()
        val users = JdbcAdminUserDirectoryRepository(db)
        val closed = config.receivablesRollout(db, users, Clock.systemUTC(), MockEnvironment())
        assertEquals(ReceivablesAvailability(false, false, maintenanceAvailable = true), closed.availability(UUID.randomUUID()))
        val opened = config.receivablesRollout(db, users, Clock.systemUTC(),
            MockEnvironment().withProperty("saqz.receivables.launch-enabled", "true"))
        assertEquals(ReceivablesAvailability(true, true, maintenanceAvailable = true), opened.availability(UUID.randomUUID()))
    }
}
