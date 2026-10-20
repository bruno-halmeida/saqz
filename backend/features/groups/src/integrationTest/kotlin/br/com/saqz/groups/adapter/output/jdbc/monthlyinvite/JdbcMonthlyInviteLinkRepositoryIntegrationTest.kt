package br.com.saqz.groups.adapter.output.jdbc.monthlyinvite

import br.com.saqz.groups.adapter.output.jdbc.invite.JdbcInviteManagementRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.invite.InviteCode
import br.com.saqz.groups.application.invite.manage.RotateInviteCommand
import br.com.saqz.groups.application.monthlyinvite.MonthlyInviteLinkState
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcMonthlyInviteLinkRepositoryIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var repository: JdbcMonthlyInviteLinkRepository
    private lateinit var publicInvites: JdbcInviteManagementRepository
    private lateinit var transaction: JdbcTransactionRunner
    private val now = Instant.parse("2026-10-20T15:00:00Z")

    @BeforeAll
    fun startDatabase() {
        dataSource = TestPostgres.empty().dataSource
        Flyway.configure().dataSource(dataSource).locations(*allGroupFeatureMigrationLocations()).load().migrate()
        repository = JdbcMonthlyInviteLinkRepository(dataSource)
        publicInvites = JdbcInviteManagementRepository(dataSource)
        transaction = JdbcTransactionRunner(dataSource)
    }

    @BeforeEach
    fun clearData() {
        execute("TRUNCATE group_monthly_invite_links, group_invites, access_groups, access_users CASCADE")
    }

    @Test
    fun `link round trips, renews and is replaced`() {
        val (owner, group) = fixture()
        val expiry = now.plus(Duration.ofDays(7))
        transaction.inTransaction { repository.replaceCode(group, code(1).value, expiry, owner, now) }

        val created = repository.find(group)!!
        assertEquals(code(1).value, created.shortCode)
        assertEquals(expiry, created.expiresAt)
        assertEquals(MonthlyInviteLinkState.ACTIVE, created.state(now))

        val renewed = now.plus(Duration.ofDays(10))
        transaction.inTransaction { repository.renew(group, renewed, now.plus(Duration.ofDays(3))) }
        assertEquals(renewed, repository.find(group)!!.expiresAt)

        transaction.inTransaction { repository.replaceCode(group, code(2).value, renewed, owner, now) }
        assertEquals(code(2).value, repository.find(group)!!.shortCode)
        assertFalse(repository.isShortCodeTaken(code(1).value))
    }

    @Test
    fun `invalidate clears the code, keeps the row, and a new code revives it`() {
        val (owner, group) = fixture()
        transaction.inTransaction { repository.replaceCode(group, code(1).value, now.plus(Duration.ofDays(7)), owner, now) }

        transaction.inTransaction { repository.invalidate(group, now) }

        val invalidated = repository.find(group)!!
        assertNull(invalidated.shortCode)
        assertEquals(now, invalidated.invalidatedAt)
        assertEquals(MonthlyInviteLinkState.INVALIDATED, invalidated.state(now))
        assertFalse(repository.isShortCodeTaken(code(1).value))
        val digestRows = number("SELECT count(*) FROM group_monthly_invite_links WHERE short_code_digest IS NOT NULL")
        assertEquals(0, digestRows)

        transaction.inTransaction { repository.replaceCode(group, code(3).value, now.plus(Duration.ofDays(7)), owner, now) }
        assertNull(repository.find(group)!!.invalidatedAt)
    }

    @Test
    fun `a code is never taken in both kinds`() {
        val (owner, group) = fixture()
        transaction.inTransaction { publicInvites.rotate(RotateInviteCommand(group, code(1), owner, null)) }
        transaction.inTransaction { repository.replaceCode(group, code(2).value, now.plus(Duration.ofDays(7)), owner, now) }

        assertTrue(repository.isShortCodeTaken(code(1).value))
        assertTrue(publicInvites.isShortCodeTaken(code(2).value))
        assertFalse(repository.isShortCodeTaken(code(4).value))
        assertFalse(publicInvites.isShortCodeTaken(code(4).value))
    }

    @Test
    fun `public invite rotation and monthly invalidation do not touch each other`() {
        val (owner, group) = fixture()
        transaction.inTransaction { publicInvites.rotate(RotateInviteCommand(group, code(1), owner, null)) }
        transaction.inTransaction { repository.replaceCode(group, code(2).value, now.plus(Duration.ofDays(7)), owner, now) }

        transaction.inTransaction { publicInvites.rotate(RotateInviteCommand(group, code(3), owner, null)) }
        transaction.inTransaction { publicInvites.expire(group) }
        assertEquals(code(2).value, repository.find(group)!!.shortCode)

        transaction.inTransaction { publicInvites.rotate(RotateInviteCommand(group, code(4), owner, null)) }
        transaction.inTransaction { repository.invalidate(group, now) }
        assertEquals(code(4).value, publicInvites.findMetadata(group)!!.shortCode)
    }

    @Test
    fun `deleted group cannot be locked and group removal cascades`() {
        val (owner, group) = fixture()
        transaction.inTransaction { repository.replaceCode(group, code(1).value, now.plus(Duration.ofDays(7)), owner, now) }
        execute("UPDATE access_groups SET deleted_at = now() WHERE id = '$group'")

        assertFailsWith<IllegalArgumentException> { transaction.inTransaction { repository.lockAndFind(group) } }

        execute("DELETE FROM access_groups WHERE id = '$group'")
        assertNull(repository.find(group))
    }

    private fun fixture(): Pair<UUID, UUID> {
        val owner = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
                "VALUES ('$owner', 'owner-$owner', true, 'Dono', now(), now())",
        )
        val group = UUID.randomUUID()
        execute(
            "INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at) " +
                "VALUES ('$group', '$owner', '${UUID.randomUUID()}', 'Vôlei', 'UTC', now(), now())",
        )
        return owner to group
    }

    private fun code(seed: Int) = InviteCode.from(InviteCode.SHORT_ALPHABET.substring(seed, seed + 8))

    private fun execute(sql: String) {
        dataSource.connection.use { it.createStatement().use { statement -> statement.execute(sql) } }
    }

    private fun number(sql: String): Int = dataSource.connection.use {
        it.createStatement().use { statement -> statement.executeQuery(sql).use { result -> result.next(); result.getInt(1) } }
    }

}
