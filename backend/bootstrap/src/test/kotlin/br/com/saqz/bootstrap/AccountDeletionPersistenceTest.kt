package br.com.saqz.bootstrap

import br.com.saqz.access.adapter.output.jdbc.session.JdbcAccountDeletionJobs
import br.com.saqz.access.adapter.output.jdbc.session.JdbcSessionRepository
import br.com.saqz.access.application.session.AccountDeleted
import br.com.saqz.access.application.session.AccountDeletionIdentityMismatch
import br.com.saqz.access.application.session.SessionUpsert
import br.com.saqz.access.domain.AccessName
import br.com.saqz.bootstrap.configuration.removeDeletedAccountPersonalData
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class AccountDeletionPersistenceTest {
    @Test
    fun `deletion purges personal content and tokens while retaining anonymized financial history`() {
        val db = TestPostgres.migrated("classpath:db/migration", owner = this).dataSource
        val jdbc = JdbcClient.create(db)
        val sessions = JdbcSessionRepository(db)
        val user = sessions.upsertAndLoad(command("deleting-person")).user.id
        val peer = sessions.upsertAndLoad(command("unaffected-person")).user.id
        val group = UUID.randomUUID()
        val message = UUID.randomUUID()
        val charge = UUID.randomUUID()
        fun execute(sql: String) { jdbc.sql(sql).update() }
        execute("""INSERT INTO access_groups(id, owner_user_id, creation_key, name, time_zone, version, created_at, updated_at)
            VALUES ('$group', '$user', '${UUID.randomUUID()}', 'Private group', 'America/Sao_Paulo', 1, now(), now())""")
        for (id in listOf(user, peer)) execute("""INSERT INTO group_memberships(group_id, user_id, role, created_at, updated_at)
            VALUES ('$group', '$id', 'ADMIN', now(), now())""")
        execute("""INSERT INTO group_messages(id, group_id, author_id, author_name, channel, body, request_id)
            VALUES ('$message', '$group', '$user', 'Private Name', 'NOTICE', 'Personal text', '${UUID.randomUUID()}')""")
        execute("INSERT INTO group_notifications(recipient_id, message_id) VALUES ('$peer', '$message')")
        execute("""INSERT INTO notification_devices(installation_id, user_id, token, platform)
            VALUES ('${UUID.randomUUID()}', '$user', 'private-push-token', 'IOS')""")
        execute("""INSERT INTO access_user_photos(user_id, photo_bytes, byte_size, width, height, sha256_digest, created_at, updated_at)
            VALUES ('$user', decode('01','hex'), 1, 1, 1, decode(repeat('ab',32),'hex'), now(), now())""")
        execute("""INSERT INTO group_charges(id, group_id, member_user_id, member_display_name, kind,
            billing_month, amount_cents, due_date, status, created_by_user_id, changed_by_user_id, created_at, updated_at)
            VALUES ('$charge', '$group', '$user', 'Private Name', 'MONTHLY', '2026-09-01', 4500,
                '2026-09-27', 'PAID', '$user', '$user', now(), now())""")

        val transaction = TransactionTemplate(DataSourceTransactionManager(db))
        transaction.executeWithoutResult {
            sessions.softDelete("deleting-person", user)
            execute("UPDATE access_groups SET deleted_at = now() WHERE id = '$group'")
            removeDeletedAccountPersonalData(jdbc, user)
        }
        assertEquals("Conta excluída", jdbc.sql("SELECT display_name FROM access_users WHERE id = '$user'").query(String::class.java).single())
        assertTrue(jdbc.sql("SELECT email IS NULL AND phone IS NULL FROM access_users WHERE id = '$user'").query(Boolean::class.java).single())
        for (table in listOf("group_messages", "group_notifications", "notification_push_queue", "notification_devices", "access_user_photos")) {
            assertEquals(0, jdbc.sql("SELECT count(*) FROM $table").query(Int::class.java).single(), table)
        }
        assertEquals(0, jdbc.sql("SELECT count(*) FROM group_memberships WHERE user_id = '$user'").query(Int::class.java).single())
        assertEquals("Private Name", jdbc.sql("SELECT display_name FROM access_users WHERE id = '$peer'").query(String::class.java).single())
        assertEquals("Conta excluída", jdbc.sql("SELECT member_display_name FROM group_charges WHERE id = '$charge'").query(String::class.java).single())
        assertEquals(4500L, jdbc.sql("SELECT amount_cents FROM group_charges WHERE id = '$charge'").query(Long::class.java).single())
        assertEquals("PAID", jdbc.sql("SELECT status FROM group_charges WHERE id = '$charge'").query(String::class.java).single())
        assertTrue(sessions.deletionRequested("deleting-person"))
        assertThrows<AccountDeleted> { sessions.upsertAndLoad(command("deleting-person")) }
        assertThrows<br.com.saqz.subscriptions.application.SubscriptionOwnerUnavailable> {
            br.com.saqz.subscriptions.adapter.output.jdbc.JdbcSubscriptionRepository(db).lockOwner(user)
        }
    }

    @Test
    fun `durable deletion jobs lease retry redact and refuse stale completion`() {
        val db = TestPostgres.migrated("classpath:db/migration", owner = this).dataSource
        val sessions = JdbcSessionRepository(db)
        val user = sessions.upsertAndLoad(command("job-person")).user.id
        sessions.softDelete("job-person", user)
        val jobs = JdbcAccountDeletionJobs(db)
        val now = Instant.now().plusSeconds(1)
        val first = assertNotNull(jobs.claim(now))
        assertEquals("job-person", first.subject)
        assertEquals(user, first.userId)
        assertNull(jobs.claim(now))
        jobs.retry(first, now.plusSeconds(60))
        assertNull(jobs.claim(now.plusSeconds(59)))
        val retry = assertNotNull(jobs.claim(now.plusSeconds(60)))
        assertEquals(2, retry.attempt)
        jobs.complete(first, now)
        val jdbc = JdbcClient.create(db)
        assertEquals(1, jdbc.sql("SELECT count(*) FROM account_deletion_requests WHERE completed_at IS NULL").query(Int::class.java).single())
        jobs.complete(retry, now.plusSeconds(60))
        assertEquals(1, jdbc.sql("SELECT count(*) FROM account_deletion_requests WHERE completed_at IS NOT NULL AND firebase_subject IS NULL").query(Int::class.java).single())
        assertEquals("deleted:$user", jdbc.sql("SELECT firebase_subject FROM access_users WHERE id = '$user'").query(String::class.java).single())
        assertTrue(sessions.deletionRequested("job-person"))
        assertNull(jobs.claim(now.plusSeconds(600)))
    }

    @Test
    fun `changed identity cannot delete the profile displayed before reauthentication`() {
        val db = TestPostgres.migrated("classpath:db/migration", owner = this).dataSource
        val sessions = JdbcSessionRepository(db)
        sessions.upsertAndLoad(command("person-a"))
        assertThrows<AccountDeletionIdentityMismatch> { sessions.softDelete("person-a", UUID.randomUUID()) }
        assertFalse(sessions.deletionRequested("person-a"))
        assertNotNull(sessions.existingUser("person-a"))
    }

    private fun command(subject: String) = SessionUpsert(subject, "$subject@example.test", true, AccessName.from("Private Name"))
}
