package br.com.saqz.access.adapter.output.jdbc.session

import br.com.saqz.access.application.session.AccountDeletionJobs
import br.com.saqz.access.application.session.AccountDeletionWork
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource

class JdbcAccountDeletionJobs(dataSource: DataSource) : AccountDeletionJobs {
    private val jdbc = JdbcClient.create(dataSource)
    private val transaction = TransactionTemplate(DataSourceTransactionManager(dataSource))

    override fun claim(now: Instant): AccountDeletionWork? = jdbc.sql(
        """UPDATE account_deletion_requests SET attempts = attempts + 1, next_attempt_at = :lease
           WHERE user_id = (SELECT user_id FROM account_deletion_requests
               WHERE completed_at IS NULL AND next_attempt_at <= :now
               ORDER BY next_attempt_at LIMIT 1 FOR UPDATE SKIP LOCKED)
           RETURNING user_id, firebase_subject, attempts""",
    ).param("now", now.atOffset(ZoneOffset.UTC)).param("lease", now.plusSeconds(300).atOffset(ZoneOffset.UTC))
        .query { row, _ -> AccountDeletionWork(row.getObject("user_id", UUID::class.java), row.getString("firebase_subject"), row.getInt("attempts")) }
        .optional().orElse(null)

    override fun complete(work: AccountDeletionWork, now: Instant) {
        transaction.executeWithoutResult {
            val completed = jdbc.sql(
                """UPDATE account_deletion_requests SET completed_at = :now, firebase_subject = NULL
                   WHERE user_id = :id AND attempts = :attempt AND completed_at IS NULL""",
            ).param("now", now.atOffset(ZoneOffset.UTC)).param("id", work.userId).param("attempt", work.attempt).update()
            if (completed == 1) {
                jdbc.sql("UPDATE access_users SET firebase_subject = :redacted WHERE id = :id AND deleted_at IS NOT NULL")
                    .param("redacted", "deleted:${work.userId}").param("id", work.userId).update()
            }
        }
    }

    override fun retry(work: AccountDeletionWork, nextAttemptAt: Instant) {
        jdbc.sql("""UPDATE account_deletion_requests SET next_attempt_at = :next
            WHERE user_id = :id AND attempts = :attempt AND completed_at IS NULL""")
            .param("next", nextAttemptAt.atOffset(ZoneOffset.UTC)).param("id", work.userId).param("attempt", work.attempt).update()
    }
}
