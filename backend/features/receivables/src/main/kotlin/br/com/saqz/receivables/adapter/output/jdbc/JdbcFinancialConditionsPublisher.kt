package br.com.saqz.receivables.adapter.output.jdbc

import br.com.saqz.receivables.application.*
import br.com.saqz.receivables.domain.FeeSchedule
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant

import javax.sql.DataSource

/** Publication commands and immutable versions commit together; no edit or deletion API. */
class JdbcFinancialConditionsPublisher(dataSource: DataSource) : FinancialConditionsPublisher {
    private val jdbc = JdbcClient.create(dataSource)
    private val transactions = TransactionTemplate(DataSourceTransactionManager(dataSource))

    override fun terms(request: FinancialRequest, version: String, content: String, effectiveAt: Instant,
                       now: Instant): FinancialResult<PublishedFinancialCondition> {
        if (!version.matches(Regex("[A-Za-z0-9._-]{1,64}")) || content.isBlank() || content.length > 200_000) {
            return FinancialResult.Failure(FinancialError.INVALID_INPUT, request.requestId)
        }
        val contentHash = hash(content)
        return publish(request, "TERMS", version, hash("$version:$contentHash:$effectiveAt"), effectiveAt, now) {
            jdbc.sql("""
                INSERT INTO receivable_terms(version,content,content_sha256,effective_at,published_at)
                VALUES (:version,:content,:hash,:effective,:now)
            """.trimIndent()).param("version", version).param("content", content).param("hash", contentHash)
                .param("effective", Timestamp.from(effectiveAt)).param("now", Timestamp.from(now)).update()
        }
    }

    override fun fees(request: FinancialRequest, schedule: FeeSchedule, effectiveAt: Instant,
                      now: Instant): FinancialResult<PublishedFinancialCondition> {
        // PostgreSQL numeric(12,10) must not silently round a published commercial rate.
        if (schedule.providerRate.stripTrailingZeros().scale() > 10 ||
            schedule.commissionRate.stripTrailingZeros().scale() > 10) {
            return FinancialResult.Failure(FinancialError.INVALID_INPUT, request.requestId)
        }
        val digest = hash(listOf(schedule.id, schedule.method, schedule.providerRate.stripTrailingZeros().toPlainString(),
            schedule.providerFixedCents, schedule.commissionRate.stripTrailingZeros().toPlainString(),
            schedule.commissionFixedCents, schedule.termsVersion, effectiveAt).joinToString(":"))
        return publish(request, "FEE_SCHEDULE", schedule.id.toString(), digest, effectiveAt, now) {
            val termsReady = jdbc.sql("""
                SELECT count(*) FROM receivable_terms WHERE version=:version AND published_at<=:now AND effective_at<=:effective
            """.trimIndent()).param("version", schedule.termsVersion).param("now", Timestamp.from(now))
                .param("effective", Timestamp.from(effectiveAt)).query(Long::class.java).single() == 1L
            if (!termsReady) throw InvalidPublication()
            jdbc.sql("""
                INSERT INTO receivable_fee_schedules(id,method,provider_rate,provider_fixed_cents,
                    commission_rate,commission_fixed_cents,terms_version,effective_at,published_at,created_by)
                VALUES (:id,:method,:providerRate,:providerFixed,:commissionRate,:commissionFixed,:terms,:effective,:now,:actor)
            """.trimIndent()).param("id", schedule.id).param("method", schedule.method.name)
                .param("providerRate", schedule.providerRate).param("providerFixed", schedule.providerFixedCents)
                .param("commissionRate", schedule.commissionRate).param("commissionFixed", schedule.commissionFixedCents)
                .param("terms", schedule.termsVersion).param("effective", Timestamp.from(effectiveAt))
                .param("now", Timestamp.from(now)).param("actor", request.actorUserId).update()
        }
    }

    private fun publish(request: FinancialRequest, kind: String, resource: String, digest: String,
                        effective: Instant, now: Instant, write: () -> Unit): FinancialResult<PublishedFinancialCondition> = try {
        transactions.execute {
            // Serialize retries without rewriting an immutable row or permitting a second version.
            jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))")
                .param("key", "receivable-publication:${request.requestId}").query { _, _ -> Unit }.single()
            val existing = jdbc.sql("SELECT * FROM receivable_condition_publications WHERE request_id=:request")
                .param("request", request.requestId).query { rs, _ ->
                    Triple(rs.getObject("actor_user_id", java.util.UUID::class.java), rs.getString("request_digest"),
                        PublishedFinancialCondition(rs.getString("kind"), rs.getString("resource_id"),
                            rs.getTimestamp("effective_at").toInstant(), rs.getTimestamp("published_at").toInstant()))
                }.optional().orElse(null)
            if (existing != null) {
                if (existing.first != request.actorUserId || existing.second != digest || existing.third.kind != kind) {
                    throw FinancialRequestConflict()
                }
                return@execute FinancialResult.Success(existing.third, request.requestId)
            }
            if (effective < now) throw InvalidPublication()
            write()
            jdbc.sql("""
                INSERT INTO receivable_condition_publications VALUES (:request,:actor,:kind,:resource,:digest,:effective,:now)
            """.trimIndent()).param("request", request.requestId).param("actor", request.actorUserId)
                .param("kind", kind).param("resource", resource).param("digest", digest)
                .param("effective", Timestamp.from(effective)).param("now", Timestamp.from(now)).update()
            FinancialResult.Success(PublishedFinancialCondition(kind, resource, effective, now), request.requestId)
        }
    } catch (_: InvalidPublication) {
        FinancialResult.Failure(FinancialError.INVALID_INPUT, request.requestId)
    } catch (_: FinancialRequestConflict) {
        FinancialResult.Failure(FinancialError.CONFLICT, request.requestId)
    } catch (_: DataIntegrityViolationException) {
        FinancialResult.Failure(FinancialError.CONFLICT, request.requestId)
    }

    private class InvalidPublication : RuntimeException()
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
