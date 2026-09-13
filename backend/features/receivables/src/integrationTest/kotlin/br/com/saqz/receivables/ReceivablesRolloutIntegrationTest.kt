package br.com.saqz.receivables

import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.receivables.adapter.output.jdbc.JdbcReceivablesRollout
import br.com.saqz.receivables.application.*
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import java.nio.file.Path
import java.time.Clock
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.test.*

class ReceivablesRolloutIntegrationTest {
    private val actor = UUID.randomUUID()
    private val user = UUID.randomUUID()
    private fun modes(mode: RolloutMode) = ReceivableSystem.entries.map { SystemRollout(it,mode) }
    private fun overrides(decision: RolloutDecision) = ReceivableSystem.entries.map { UserRolloutOverride(it,decision) }
    private fun fixture(block: (JdbcReceivablesRollout, JdbcClient) -> Unit) {
        val db = TestPostgres.migrated("filesystem:" + Path.of("src/main/resources/db/migration").toAbsolutePath(), owner=this)
        block(JdbcReceivablesRollout(db.dataSource, { it == user }, Clock.systemUTC()), JdbcClient.create(db.dataSource))
    }
    @Test fun `defaults off replay returns original state and conflicts preserve immutable history`() = fixture { store, jdbc ->
        assertEquals(ReceivablesAvailability(false,false), store.availability(user))
        val change = RolloutChange(UUID.randomUUID(),0,"enable selected",modes(RolloutMode.SELECTED_USERS))
        val first = store.change(actor,change)
        store.change(actor,RolloutChange(UUID.randomUUID(),1,"turn off",modes(RolloutMode.OFF)))
        assertEquals(first,store.change(actor,change))
        assertEquals(FinancialError.CONFLICT,assertFailsWith<RolloutFailure> { store.change(UUID.randomUUID(),change) }.error)
        assertEquals(FinancialError.CONFLICT,assertFailsWith<RolloutFailure> { store.change(actor,change.copy(reason="different")) }.error)
        assertEquals(FinancialError.CONFLICT,assertFailsWith<RolloutFailure> { store.change(actor,change.copy(requestId=UUID.randomUUID())) }.error)
        assertEquals(2,store.history(1,1).total)
        assertEquals(1,store.history(2,1).items.size)
        assertEquals(actor,store.history(1,25).items.first().actorUserId)
        assertFails { jdbc.sql("DELETE FROM receivable_rollout_history").update() }
        assertFails { jdbc.sql("UPDATE receivable_rollout_history SET reason='changed'").update() }
    }
    @Test fun `user overrides inherit removes rows account flag audited and absent account rejected`() = fixture { store,jdbc ->
        val change = UserRolloutChange(UUID.randomUUID(),0,"allow tester",overrides(RolloutDecision.ALLOW),null)
        val first = store.changeUser(actor,user,change)
        assertFalse(first.backendEnabled)
        store.change(actor,RolloutChange(UUID.randomUUID(),0,"selected users",modes(RolloutMode.SELECTED_USERS)))
        assertTrue(store.user(user).mobileEnabled)
        assertEquals(first,store.changeUser(actor,user,change))
        assertEquals(FinancialError.INVALID_INPUT,assertFailsWith<RolloutFailure> {
            store.changeUser(actor,user,change.copy(requestId=UUID.randomUUID(),expectedVersion=1,accountOperationsEnabled=true)) }.error)
        assertEquals(1,store.user(user).version)
        val account = UUID.randomUUID()
        jdbc.sql("""INSERT INTO receivable_accounts(id,owner_user_id,legal_identity_digest,legal_data_encrypted,registration,created_at,updated_at)
            VALUES (:id,:user,'digest','encrypted','INCOMPLETE',now(),now())""").param("id",account).param("user",user).update()
        val second = store.changeUser(actor,user,change.copy(requestId=UUID.randomUUID(),expectedVersion=1,
            overrides=overrides(RolloutDecision.INHERIT),accountOperationsEnabled=true))
        assertEquals(account,second.accountId)
        assertEquals(true,second.accountOperationsEnabled)
        assertFalse(second.backendEnabled)
        assertEquals(0,jdbc.sql("SELECT count(*) FROM receivable_rollout_overrides").query(Int::class.java).single())
        assertEquals(FinancialError.NOT_FOUND,assertFailsWith<RolloutFailure> { store.user(UUID.randomUUID()) }.error)
    }
    @Test fun `concurrent expected versions permit exactly one mutation`() = fixture { store,_ ->
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results = pool.invokeAll((1..2).map { java.util.concurrent.Callable {
                runCatching { store.change(actor,RolloutChange(UUID.randomUUID(),0,"concurrent write",modes(RolloutMode.ALL_USERS))) }
            } }).map { it.get() }
            assertEquals(1,results.count { it.isSuccess })
            assertEquals(FinancialError.CONFLICT,(results.single { it.isFailure }.exceptionOrNull() as RolloutFailure).error)
            assertEquals(1,store.history(1,25).total)
        } finally { pool.shutdownNow() }
    }
}
