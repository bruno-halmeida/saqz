package br.com.saqz.groups.adapter.output.jdbc.invite

import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.invite.InviteCode
import br.com.saqz.groups.application.invite.InviteTokenDigest
import br.com.saqz.groups.application.invite.preview.PreviewInviteAttemptWindow
import br.com.saqz.groups.application.invite.preview.RecordInvalidPreviewInviteAttempt
import br.com.saqz.groups.domain.group.GroupComposition
import br.com.saqz.groups.domain.group.GroupLevel
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalTime
import java.util.Base64
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcInvitePreviewRepositoryIntegrationTest {
    private val now = Instant.parse("2026-07-16T18:00:00Z")
    private val code = InviteCode.from(
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 21 }),
    )
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var repository: JdbcInvitePreviewRepository
    private lateinit var transaction: JdbcTransactionRunner

    @BeforeAll
    fun startDatabase() {
        dataSource = TestPostgres.migrated(*allGroupFeatureMigrationLocations()).dataSource
        repository = JdbcInvitePreviewRepository(dataSource)
        transaction = JdbcTransactionRunner(dataSource)
    }

    @BeforeEach
    fun clearData() {
        execute(
            "TRUNCATE games, group_regular_slots, group_venues, group_invites, group_memberships, " +
                "access_groups, invite_redemption_limits, access_users CASCADE",
        )
    }

    @Test
    fun `preview lookup assembles profile memberships inviter slots and next published game`() {
        val owner = insertUser("preview-owner", "Owner Display")
        val group = insertCompleteGroup(owner)
        insertMembership(group, owner, "ADMIN")
        insertMembership(group, insertUser("preview-admin", "Admin Display"), "ADMIN")
        insertMembership(group, insertUser("preview-athlete", "Athlete Display"), "ATHLETE")
        insertSlot(group, 2, "19:30", 0)
        insertSlot(group, 6, "10:00", 1)
        insertGame(group, now.plusSeconds(7_200), "PUBLISHED", "Next Arena", "Court 1")
        insertGame(group, now.plusSeconds(3_600), "CANCELLED", "Cancelled Arena", "Court 9")
        insertInvite(group, owner)

        val found = transaction.inTransaction { repository.findInvite(InviteTokenDigest.sha256(code), now) }

        val preview = requireNotNull(found)
        assertFalse(preview.groupDeleted)
        assertNull(preview.expiredAt)
        assertEquals("Preview Group", preview.card.groupName)
        assertEquals("São Paulo", preview.card.city)
        assertEquals(GroupComposition.MIXED, preview.card.composition)
        assertEquals(GroupLevel.INTERMEDIATE, preview.card.level)
        assertEquals(3, preview.card.memberCount)
        assertEquals(listOf(2 to LocalTime.of(19, 30), 6 to LocalTime.of(10, 0)), preview.card.regularSlots.map { it.weekday.value to it.startTime })
        assertEquals("Owner Display", preview.card.inviterName)
        assertFalse(preview.card.entryRequiresApproval)
        assertNull(preview.card.expiresAt)
        assertEquals(now.plusSeconds(7_200), preview.card.nextGame?.startsAt)
        assertEquals("Next Arena", preview.card.nextGame?.venueName)
        assertEquals("Court 1", preview.card.nextGame?.court)
    }

    @Test
    fun `preview lookup returns no next game when no future published game exists`() {
        val owner = insertUser("preview-no-game-owner", "No Game Owner")
        val group = insertCompleteGroup(owner)
        insertGame(group, now.minusSeconds(1), "PUBLISHED", "Past Arena", null)
        insertGame(group, now.plusSeconds(1_000), "DRAFT", "Draft Arena", null)
        insertInvite(group, owner)

        val preview = requireNotNull(transaction.inTransaction { repository.findInvite(InviteTokenDigest.sha256(code), now) })

        assertNull(preview.card.nextGame)
    }

    @Test
    fun `deleted group remains distinguishable only inside the application port`() {
        val owner = insertUser("preview-deleted-owner", "Deleted Owner")
        val group = insertCompleteGroup(owner)
        insertInvite(group, owner)
        execute("UPDATE access_groups SET deleted_at = TIMESTAMPTZ '2026-07-16 18:00:00Z' WHERE id = '$group'")

        val preview = requireNotNull(transaction.inTransaction { repository.findInvite(InviteTokenDigest.sha256(code), now) })

        assertTrue(preview.groupDeleted)
    }

    @Test
    fun `unknown digest returns no invite`() {
        assertNull(transaction.inTransaction { repository.findInvite(InviteTokenDigest.from(ByteArray(32) { 99 }), now) })
    }

    @Test
    fun `authenticated attempt window shares redeem table semantics`() {
        val user = insertUser("preview-window", "Window Owner")

        val initial = transaction.inTransaction { repository.lockAttemptWindow(user, now) }
        transaction.inTransaction {
            repository.recordInvalidAttempt(RecordInvalidPreviewInviteAttempt(user, now.minusSeconds(30), 7))
        }
        val stored = transaction.inTransaction { repository.lockAttemptWindow(user, now) }

        assertEquals(PreviewInviteAttemptWindow(now, 0), initial)
        assertEquals(PreviewInviteAttemptWindow(now.minusSeconds(30), 7), stored)
    }

    private fun insertUser(subject: String, displayName: String): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
                "VALUES ('$id', '$subject', true, '$displayName', now(), now())",
        )
        return id
    }

    private fun insertCompleteGroup(owner: UUID): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, profile_status, " +
                "modality, composition, city, level, created_at, updated_at) VALUES " +
                "('$id', '$owner', '${UUID.randomUUID()}', 'Preview Group', 'America/Sao_Paulo', 'COMPLETE', " +
                "'COURT_VOLLEYBALL', 'MIXED', 'São Paulo', 'INTERMEDIATE', now(), now())",
        )
        return id
    }

    private fun insertMembership(group: UUID, user: UUID, role: String) = execute(
        "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) " +
            "VALUES ('$group', '$user', '$role', now(), now())",
    )

    private fun insertSlot(group: UUID, weekday: Int, startTime: String, position: Int) = execute(
        "INSERT INTO group_regular_slots (id, group_id, weekday, start_time, duration_minutes, position, created_at, updated_at) " +
            "VALUES ('${UUID.randomUUID()}', '$group', $weekday, TIME '$startTime', 90, $position, now(), now())",
    )

    private fun insertGame(
        group: UUID,
        startsAt: Instant,
        status: String,
        venueName: String,
        court: String?,
    ) {
        connection().use { connection ->
            connection.prepareStatement(
                "INSERT INTO games (id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes, " +
                    "confirmation_deadline, venue_name, venue_address, venue_court, capacity, status, created_at, updated_at) " +
                    "VALUES (?, ?, 'Game', DATE '2026-08-12', TIME '19:30', 'UTC', ?, 90, ?, ?, 'Address', ?, 12, ?, now(), now())",
            ).use { statement ->
                statement.setObject(1, UUID.randomUUID())
                statement.setObject(2, group)
                statement.setTimestamp(3, Timestamp.from(startsAt))
                statement.setTimestamp(4, Timestamp.from(startsAt.minusSeconds(3_600)))
                statement.setString(5, venueName)
                statement.setString(6, court)
                statement.setObject(7, status, java.sql.Types.OTHER)
                statement.executeUpdate()
            }
        }
    }

    private fun insertInvite(group: UUID, creator: UUID) {
        connection().use { connection ->
            connection.prepareStatement(
                "INSERT INTO group_invites (group_id, token_digest, created_by_user_id, created_at, expires_at) VALUES (?, ?, ?, now(), ?)",
            ).use { statement ->
                statement.setObject(1, group)
                statement.setBytes(2, InviteTokenDigest.sha256(code).toByteArray())
                statement.setObject(3, creator)
                statement.setTimestamp(4, Timestamp.from(now.plusSeconds(7 * 24 * 60 * 60)))
                statement.executeUpdate()
            }
        }
    }

    private fun execute(sql: String) {
        connection().use { it.createStatement().use { statement -> statement.execute(sql) } }
    }

    private fun connection(): Connection = dataSource.connection
}
