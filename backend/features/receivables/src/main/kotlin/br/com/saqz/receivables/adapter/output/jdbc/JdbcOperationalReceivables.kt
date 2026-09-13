package br.com.saqz.receivables.adapter.output.jdbc

import br.com.saqz.receivables.application.*
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class JdbcOperationalReceivables(dataSource: DataSource) : OperationalReceivablesStore {
    private val jdbc = JdbcClient.create(dataSource)
    private val transaction = TransactionTemplate(DataSourceTransactionManager(dataSource))

    override fun list(status: OperationStatus?, kind: OperationKind?, page: Int, size: Int): OperationalPage<OperationalOperation> {
        require(page >= 1 && size in 1..100)
        val where = buildList {
            add("status<>'SUCCEEDED'")
            if (status != null) add("status=:status")
            if (kind != null) add("kind=:kind")
        }.joinToString(" AND ")
        fun JdbcClient.StatementSpec.bind(): JdbcClient.StatementSpec {
            var statement = this
            if (status != null) statement = statement.param("status", status.name)
            if (kind != null) statement = statement.param("kind", kind.name)
            return statement
        }
        val total = jdbc.sql("SELECT count(*) FROM receivable_operations WHERE $where").bind()
            .query(Long::class.java).single()
        val items = jdbc.sql("""
            SELECT * FROM receivable_operations WHERE $where
            ORDER BY updated_at DESC,id DESC LIMIT :limit OFFSET :offset
        """.trimIndent()).bind().param("limit", size).param("offset", (page - 1) * size)
            .query { result, _ -> result.operation() }.list()
        return OperationalPage(items, page, size, total)
    }

    override fun detail(operationId: UUID): OperationalOperationDetail? {
        val operation = find(operationId) ?: return null
        val audits = jdbc.sql("""
            SELECT actor_user_id,request_id,action,reason,result,operation_status,created_at
            FROM receivable_operation_audit WHERE operation_id=:operation ORDER BY created_at,id
        """.trimIndent()).param("operation", operationId).query { result, _ -> OperationalAudit(
            actorUserId = result.getObject("actor_user_id", UUID::class.java),
            requestId = result.getObject("request_id", UUID::class.java),
            action = result.getString("action"),
            reason = result.getString("reason"),
            result = result.getString("result")?.let(OperationalRecoveryResult::valueOf),
            operationStatus = result.getString("operation_status")?.let(OperationStatus::valueOf),
            createdAt = result.getTimestamp("created_at").toInstant(),
        ) }.list()
        return OperationalOperationDetail(operation, audits)
    }

    override fun reserve(
        command: OperationalRecoveryCommand,
        now: Instant,
        leaseUntil: Instant,
    ): OperationalRecoveryReservation = transaction.execute {
        require(leaseUntil > now)
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key,0))")
            .param("key", command.operationId.toString()).query { _, _ -> true }.single()
        val digest = digest(command)
        val previous = jdbc.sql("SELECT * FROM receivable_operational_recoveries WHERE request_id=:request FOR UPDATE")
            .param("request", command.requestId).query { result, _ -> RecoveryRow(
                result.getObject("operation_id", UUID::class.java), result.getObject("actor_user_id", UUID::class.java),
                result.getString("reason"), result.getString("request_digest"), result.getString("state"),
                result.getObject("lease_token", UUID::class.java), result.getTimestamp("lease_until").toInstant(),
                result.getString("result"), result.getString("operation_status"),
            ) }.optional().orElse(null)
        if (previous != null) {
            if (previous.operationId != command.operationId || previous.actor != command.actorUserId ||
                previous.reason != command.reason || previous.digest != digest) return@execute OperationalRecoveryReservation.Conflict
            if (previous.state == "COMPLETED") return@execute OperationalRecoveryReservation.Replay(
                OperationalRecoveryOutcome(command.requestId, command.operationId,
                    OperationalRecoveryResult.valueOf(requireNotNull(previous.result)),
                    OperationStatus.valueOf(requireNotNull(previous.operationStatus))))
            if (previous.leaseUntil > now) return@execute OperationalRecoveryReservation.Busy
        }
        if (previous == null) {
            val auditCollision = jdbc.sql("SELECT count(*) FROM receivable_operation_audit WHERE request_id=:request")
                .param("request", command.requestId).query(Long::class.java).single()
            if (auditCollision != 0L) return@execute OperationalRecoveryReservation.Conflict
        }
        val operation = jdbc.sql("SELECT * FROM receivable_operations WHERE id=:id FOR UPDATE")
            .param("id", command.operationId).query { result, _ -> result.operation() }.optional().orElse(null)
            ?: return@execute OperationalRecoveryReservation.NotFound
        // Crash window: the provider reconciliation may have committed the operation before this
        // recovery request could be completed. A retry reads that durable terminal state and closes
        // the original request; it never calls the provider or recreates the resource.
        if (previous != null && previous.leaseUntil <= now &&
            operation.status in setOf(OperationStatus.SUCCEEDED, OperationStatus.REJECTED)) {
            val result = if (operation.status == OperationStatus.SUCCEEDED) {
                OperationalRecoveryResult.CONFIRMED
            } else OperationalRecoveryResult.REJECTED
            jdbc.sql("""
                UPDATE receivable_operational_recoveries
                SET state='COMPLETED',result=:result,operation_status=:status,completed_at=:now,lease_until=:now
                WHERE request_id=:request AND state='RUNNING'
            """.trimIndent()).param("result", result.name).param("status", operation.status.name)
                .param("now", Timestamp.from(now)).param("request", command.requestId).update()
            jdbc.sql("""
                INSERT INTO receivable_operation_audit(id,operation_id,actor_user_id,request_id,action,reason,
                    result,operation_status,created_at)
                VALUES (:id,:operation,:actor,:request,'RECOVER',:reason,:result,:status,:now)
            """.trimIndent()).param("id", UUID.randomUUID()).param("operation", command.operationId)
                .param("actor", command.actorUserId).param("request", command.requestId).param("reason", command.reason)
                .param("result", result.name).param("status", operation.status.name).param("now", Timestamp.from(now)).update()
            return@execute OperationalRecoveryReservation.Replay(
                OperationalRecoveryOutcome(command.requestId, command.operationId, result, operation.status),
            )
        }
        if (!operation.recoverable) return@execute OperationalRecoveryReservation.NotRecoverable
        val providerLease = jdbc.sql("SELECT lease_until FROM receivable_operations WHERE id=:id")
            .param("id", operation.id).query(Timestamp::class.java).optional().orElse(null)?.toInstant()
        if (operation.status == OperationStatus.RUNNING && providerLease != null && providerLease > now) {
            return@execute OperationalRecoveryReservation.Busy
        }
        val another = jdbc.sql("""
            SELECT count(*) FROM receivable_operational_recoveries
            WHERE operation_id=:operation AND request_id<>:request AND state='RUNNING' AND lease_until>:now
        """.trimIndent()).param("operation", operation.id).param("request", command.requestId)
            .param("now", Timestamp.from(now)).query(Long::class.java).single()
        if (another != 0L) return@execute OperationalRecoveryReservation.Busy
        val token = UUID.randomUUID()
        if (previous == null) {
            jdbc.sql("""
                INSERT INTO receivable_operational_recoveries(request_id,operation_id,actor_user_id,reason,
                    request_digest,state,lease_token,lease_until,created_at)
                VALUES (:request,:operation,:actor,:reason,:digest,'RUNNING',:token,:lease,:now)
            """.trimIndent()).param("request", command.requestId).param("operation", operation.id)
                .param("actor", command.actorUserId).param("reason", command.reason).param("digest", digest)
                .param("token", token).param("lease", Timestamp.from(leaseUntil)).param("now", Timestamp.from(now)).update()
        } else {
            jdbc.sql("""
                UPDATE receivable_operational_recoveries SET lease_token=:token,lease_until=:lease
                WHERE request_id=:request AND state='RUNNING'
            """.trimIndent()).param("token", token).param("lease", Timestamp.from(leaseUntil))
                .param("request", command.requestId).update()
        }
        OperationalRecoveryReservation.Claimed(token, operation)
    }

    override fun complete(
        command: OperationalRecoveryCommand,
        token: UUID,
        observation: OperationalRecoveryObservation,
        now: Instant,
    ): OperationalRecoveryOutcome = transaction.execute {
        val result = when (observation.status) {
            OperationStatus.SUCCEEDED -> OperationalRecoveryResult.CONFIRMED
            OperationStatus.REJECTED -> OperationalRecoveryResult.REJECTED
            else -> OperationalRecoveryResult.STILL_UNKNOWN
        }
        val updated = jdbc.sql("""
            UPDATE receivable_operational_recoveries
            SET state='COMPLETED',result=:result,operation_status=:status,completed_at=:now,lease_until=:now
            WHERE request_id=:request AND lease_token=:token AND state='RUNNING'
        """.trimIndent()).param("result", result.name).param("status", observation.status.name)
            .param("now", Timestamp.from(now)).param("request", command.requestId).param("token", token).update()
        check(updated == 1) { "Operational recovery lease lost" }
        jdbc.sql("""
            INSERT INTO receivable_operation_audit(id,operation_id,actor_user_id,request_id,action,reason,
                result,operation_status,created_at)
            VALUES (:id,:operation,:actor,:request,'RECOVER',:reason,:result,:status,:now)
        """.trimIndent()).param("id", UUID.randomUUID()).param("operation", command.operationId)
            .param("actor", command.actorUserId).param("request", command.requestId).param("reason", command.reason)
            .param("result", result.name).param("status", observation.status.name).param("now", Timestamp.from(now)).update()
        OperationalRecoveryOutcome(command.requestId, command.operationId, result, observation.status)
    }

    private fun find(id: UUID): OperationalOperation? = jdbc.sql("SELECT * FROM receivable_operations WHERE id=:id")
        .param("id", id).query { result, _ -> result.operation() }.optional().orElse(null)

    private fun ResultSet.operation() = OperationalOperation(
        id = getObject("id", UUID::class.java), accountId = getObject("account_id", UUID::class.java),
        requestId = getObject("request_id", UUID::class.java), kind = OperationKind.valueOf(getString("kind")),
        resourceId = getObject("resource_id", UUID::class.java), status = OperationStatus.valueOf(getString("status")),
        attempts = getInt("attempts"), failureCode = getString("failure_code"),
        createdAt = getTimestamp("created_at").toInstant(), updatedAt = getTimestamp("updated_at").toInstant(),
        nextAttemptAt = getTimestamp("next_attempt_at").toInstant(),
    )

    private fun digest(command: OperationalRecoveryCommand): String = MessageDigest.getInstance("SHA-256")
        .digest("${command.operationId}\n${command.actorUserId}\n${command.reason}".toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private data class RecoveryRow(
        val operationId: UUID, val actor: UUID, val reason: String, val digest: String, val state: String,
        val token: UUID, val leaseUntil: Instant, val result: String?, val operationStatus: String?,
    )
}

class JdbcOperationalNotices(dataSource: DataSource) : OperationalNotices {
    private val jdbc = JdbcClient.create(dataSource)
    private val transaction = TransactionTemplate(DataSourceTransactionManager(dataSource))

    override fun publish(command: PublishOperationalNotice, now: Instant): OperationalNotice = transaction.execute {
        require(command.title.trim() == command.title && command.title.length in 3..120)
        require(command.message.trim() == command.message && command.message.length in 3..2000)
        require(command.endsAt == null || command.endsAt > command.startsAt)
        val digest = MessageDigest.getInstance("SHA-256").digest(
            "${command.actorUserId}\n${command.audience}\n${command.title}\n${command.message}\n${command.startsAt}\n${command.endsAt}"
                .toByteArray(StandardCharsets.UTF_8),
        ).joinToString("") { "%02x".format(it) }
        val id = UUID.randomUUID()
        jdbc.sql("""
            INSERT INTO receivable_operational_notices(id,request_id,actor_user_id,audience,title,message,
                starts_at,ends_at,request_digest,created_at)
            VALUES (:id,:request,:actor,:audience,:title,:message,:starts,:ends,:digest,:now)
            ON CONFLICT(request_id) DO NOTHING
        """.trimIndent()).param("id", id).param("request", command.requestId).param("actor", command.actorUserId)
            .param("audience", command.audience.name).param("title", command.title).param("message", command.message)
            .param("starts", Timestamp.from(command.startsAt)).param("ends", command.endsAt?.let(Timestamp::from), java.sql.Types.TIMESTAMP)
            .param("digest", digest).param("now", Timestamp.from(now)).update()
        val stored = byRequest(command.requestId)
        val storedDigest = jdbc.sql("SELECT request_digest FROM receivable_operational_notices WHERE request_id=:request")
            .param("request", command.requestId).query(String::class.java).single()
        if (storedDigest != digest) throw OperationalNoticeConflict()
        stored
    }

    override fun list(audience: OperationalNoticeAudience?, page: Int, size: Int): OperationalPage<OperationalNotice> {
        require(page >= 1 && size in 1..100)
        val where = if (audience == null) "TRUE" else "audience=:audience"
        fun JdbcClient.StatementSpec.bind(): JdbcClient.StatementSpec =
            if (audience == null) this else param("audience", audience.name)
        val total = jdbc.sql("SELECT count(*) FROM receivable_operational_notices WHERE $where").bind()
            .query(Long::class.java).single()
        val items = jdbc.sql("SELECT * FROM receivable_operational_notices WHERE $where ORDER BY created_at DESC,id DESC LIMIT :limit OFFSET :offset")
            .bind().param("limit", size).param("offset", (page - 1) * size).query { result, _ -> result.notice() }.list()
        return OperationalPage(items, page, size, total)
    }

    override fun active(audience: OperationalNoticeAudience, at: Instant): List<OperationalNotice> = jdbc.sql("""
        SELECT * FROM receivable_operational_notices
        WHERE audience=:audience AND starts_at<=:at AND (ends_at IS NULL OR ends_at>:at)
        ORDER BY starts_at DESC,id DESC LIMIT 100
    """.trimIndent()).param("audience", audience.name).param("at", Timestamp.from(at))
        .query { result, _ -> result.notice() }.list()

    private fun byRequest(requestId: UUID): OperationalNotice = jdbc.sql("SELECT * FROM receivable_operational_notices WHERE request_id=:request")
        .param("request", requestId).query { result, _ -> result.notice() }.single()

    private fun ResultSet.notice() = OperationalNotice(
        id = getObject("id", UUID::class.java), requestId = getObject("request_id", UUID::class.java),
        audience = OperationalNoticeAudience.valueOf(getString("audience")), title = getString("title"),
        message = getString("message"), startsAt = getTimestamp("starts_at").toInstant(),
        endsAt = getTimestamp("ends_at")?.toInstant(), createdAt = getTimestamp("created_at").toInstant(),
    )
}
