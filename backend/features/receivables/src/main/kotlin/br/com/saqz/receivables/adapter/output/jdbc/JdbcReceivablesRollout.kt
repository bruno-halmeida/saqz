package br.com.saqz.receivables.adapter.output.jdbc

import br.com.saqz.receivables.application.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Timestamp
import java.time.Clock
import java.util.UUID
import javax.sql.DataSource

/** A single control-row lock serializes administrative edits and their immutable replay records. */
class JdbcReceivablesRollout(dataSource: DataSource, private val userExists: (UUID) -> Boolean,
    private val clock: Clock) : ReceivablesRollout {
    private val jdbc = JdbcClient.create(dataSource)
    private val tx = TransactionTemplate(DataSourceTransactionManager(dataSource))
    private val mapper = jacksonObjectMapper()
    override fun read(): RolloutState = jdbc.sql("SELECT * FROM receivable_rollout").query { rs, _ ->
        RolloutState(rs.getLong("version"), listOf(SystemRollout(ReceivableSystem.BACKEND, RolloutMode.valueOf(rs.getString("backend_mode"))),
            SystemRollout(ReceivableSystem.MOBILE, RolloutMode.valueOf(rs.getString("mobile_mode")))))
    }.single()
    private fun snapshot(userId: UUID): UserRolloutState = jdbc.sql("""
        SELECT r.backend_mode,r.mobile_mode,COALESCE(u.version,0) AS user_version,
            COALESCE(b.decision,'INHERIT') AS backend_decision,COALESCE(m.decision,'INHERIT') AS mobile_decision,
            a.id AS account_id,a.new_operations_enabled
        FROM receivable_rollout r
        LEFT JOIN receivable_rollout_users u ON u.user_id=:id
        LEFT JOIN receivable_rollout_overrides b ON b.user_id=:id AND b.system='BACKEND'
        LEFT JOIN receivable_rollout_overrides m ON m.user_id=:id AND m.system='MOBILE'
        LEFT JOIN receivable_accounts a ON a.owner_user_id=:id
    """).param("id",userId).query { rs, _ ->
        val systems = listOf(SystemRollout(ReceivableSystem.BACKEND,RolloutMode.valueOf(rs.getString("backend_mode"))),
            SystemRollout(ReceivableSystem.MOBILE,RolloutMode.valueOf(rs.getString("mobile_mode"))))
        val overrides = listOf(UserRolloutOverride(ReceivableSystem.BACKEND,RolloutDecision.valueOf(rs.getString("backend_decision"))),
            UserRolloutOverride(ReceivableSystem.MOBILE,RolloutDecision.valueOf(rs.getString("mobile_decision"))))
        val availability = RolloutPolicy.availability(systems,overrides)
        val accountId = rs.getObject("account_id",UUID::class.java)
        UserRolloutState(userId,rs.getLong("user_version"),overrides,availability.backendEnabled,availability.mobileEnabled,
            accountId,if (accountId == null) null else rs.getBoolean("new_operations_enabled"))
    }.single()
    override fun availability(userId: UUID): ReceivablesAvailability = snapshot(userId).let {
        ReceivablesAvailability(it.backendEnabled,it.mobileEnabled)
    }
    override fun user(userId: UUID): UserRolloutState {
        if (!userExists(userId)) throw RolloutFailure(FinancialError.NOT_FOUND)
        return snapshot(userId)
    }
    override fun change(actor: UUID, change: RolloutChange): RolloutState {
        validate(change.reason, change.expectedVersion, change.systems.map { it.system })
        val canonical = change.copy(reason = change.reason.trim(), systems = change.systems.sortedBy { it.system.ordinal })
        return write(actor, null, change.requestId, canonical, canonical.reason, RolloutState::class.java) {
            val before = read()
            if (before.version != change.expectedVersion) throw RolloutFailure(FinancialError.CONFLICT)
            jdbc.sql("UPDATE receivable_rollout SET version=version+1,backend_mode=:backend,mobile_mode=:mobile")
                .param("backend", canonical.systems[0].mode.name).param("mobile", canonical.systems[1].mode.name).update()
            before to read()
        }
    }
    override fun changeUser(actor: UUID, userId: UUID, change: UserRolloutChange): UserRolloutState {
        validate(change.reason, change.expectedVersion, change.overrides.map { it.system })
        val canonical = change.copy(reason = change.reason.trim(), overrides = change.overrides.sortedBy { it.system.ordinal })
        return write(actor, userId, change.requestId, canonical, canonical.reason, UserRolloutState::class.java) {
            // Account flag updates share the existing financial account lock with activation.
            jdbc.sql("SELECT id FROM receivable_accounts WHERE owner_user_id=:id FOR UPDATE").param("id", userId).query(UUID::class.java).list()
            val before = user(userId)
            if (before.version != change.expectedVersion) throw RolloutFailure(FinancialError.CONFLICT)
            if (change.accountOperationsEnabled != null && before.accountId == null) throw RolloutFailure(FinancialError.INVALID_INPUT)
            jdbc.sql("INSERT INTO receivable_rollout_users(user_id,version) VALUES (:id,1) ON CONFLICT(user_id) DO UPDATE SET version=receivable_rollout_users.version+1")
                .param("id", userId).update()
            jdbc.sql("DELETE FROM receivable_rollout_overrides WHERE user_id=:id").param("id", userId).update()
            canonical.overrides.filter { it.decision != RolloutDecision.INHERIT }.forEach {
                jdbc.sql("INSERT INTO receivable_rollout_overrides VALUES (:id,:system,:decision)")
                    .param("id", userId).param("system", it.system.name).param("decision", it.decision.name).update()
            }
            if (change.accountOperationsEnabled != null) jdbc.sql("UPDATE receivable_accounts SET new_operations_enabled=:enabled,version=version+1,updated_at=:at WHERE id=:id")
                .param("enabled", change.accountOperationsEnabled).param("at", Timestamp.from(clock.instant())).param("id", before.accountId!!).update()
            before to user(userId)
        }
    }
    private fun validate(reason: String, version: Long, systems: List<ReceivableSystem>) {
        if (reason.trim().length !in 3..500 || version < 0 || systems.size != 2 || systems.toSet() != ReceivableSystem.entries.toSet())
            throw RolloutFailure(FinancialError.INVALID_INPUT)
    }
    private fun <T : Any> write(actor: UUID, user: UUID?, id: UUID, content: Any, reason: String, type: Class<T>, mutation: () -> Pair<T,T>): T = tx.execute {
        jdbc.sql("SELECT singleton FROM receivable_rollout FOR UPDATE").query(Boolean::class.java).single()
        val encoded = mapper.writeValueAsString(content)
        val replay = jdbc.sql("SELECT actor_user_id,user_id,request_content,after_state::text FROM receivable_rollout_history WHERE request_id=:id")
            .param("id", id).query { rs, _ ->
                if (rs.getObject(1, UUID::class.java) != actor || rs.getObject(2, UUID::class.java) != user || rs.getString(3) != encoded)
                    throw RolloutFailure(FinancialError.CONFLICT)
                mapper.readValue(rs.getString(4), type)
            }.optional().orElse(null)
        if (replay != null) return@execute replay
        val (before, after) = mutation()
        jdbc.sql("""INSERT INTO receivable_rollout_history(request_id,actor_user_id,user_id,request_content,reason,created_at,before_state,after_state)
            VALUES (:id,:actor,:user,:content,:reason,:at,CAST(:before AS jsonb),CAST(:after AS jsonb))""")
            .param("id", id).param("actor", actor).param("user", user, java.sql.Types.OTHER).param("content", encoded)
            .param("reason", reason).param("at", Timestamp.from(clock.instant())).param("before", mapper.writeValueAsString(before))
            .param("after", mapper.writeValueAsString(after)).update()
        after
    }
    override fun history(page: Int, size: Int): RolloutHistory {
        if (page < 1 || size !in 1..100) throw RolloutFailure(FinancialError.INVALID_INPUT)
        val total = jdbc.sql("SELECT count(*) FROM receivable_rollout_history").query(Long::class.java).single()
        val items = jdbc.sql("SELECT * FROM receivable_rollout_history ORDER BY created_at DESC,request_id LIMIT :size OFFSET :offset")
            .param("size", size).param("offset", (page.toLong()-1)*size).query { rs, _ ->
                RolloutHistoryItem(rs.getObject("request_id", UUID::class.java), rs.getObject("actor_user_id", UUID::class.java),
                    rs.getObject("user_id", UUID::class.java), rs.getString("reason"), rs.getTimestamp("created_at").toInstant().toString(),
                    mapper.readValue(rs.getString("before_state"), Map::class.java), mapper.readValue(rs.getString("after_state"), Map::class.java))
            }.list()
        return RolloutHistory(page, size, total, items)
    }
}
