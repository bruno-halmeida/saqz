package br.com.saqz.bootstrap

import br.com.saqz.access.adapter.output.jdbc.session.JdbcSessionRepository
import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.application.session.AccountDeleted
import br.com.saqz.bootstrap.configuration.AccessSessionConfiguration
import br.com.saqz.access.application.session.BootstrapSessionResult
import br.com.saqz.access.application.session.DeleteAccount
import br.com.saqz.access.application.session.SessionUpsert
import br.com.saqz.access.domain.AccessName
import br.com.saqz.groups.adapter.output.jdbc.athlete.JdbcAthleteRepository
import br.com.saqz.groups.adapter.output.jdbc.group.delete.JdbcGroupDeletionRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.delete.DeleteGroup
import br.com.saqz.groups.adapter.input.http.FinanceStatementController
import br.com.saqz.groups.adapter.input.http.GameNotFoundException
import br.com.saqz.groups.adapter.input.http.VerifiedGroupActorResolver
import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcFinanceStatementRepository
import br.com.saqz.groups.application.finance.statement.FinanceStatementService
import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.Connection
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeleteAccountIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var sessionRepository: JdbcSessionRepository
    private lateinit var deleteAccount: DeleteAccount

    @BeforeAll
    fun startDatabase() {
        dataSource = TestPostgres.migrated("classpath:db/migration").dataSource
        sessionRepository = JdbcSessionRepository(dataSource)
        deleteAccount = accountDeletion()
    }

    @BeforeEach
    fun clearData() {
        execute(
            "TRUNCATE group_charges, game_attendance, games, group_membership_removals, " +
                "group_memberships, access_groups, access_user_photos, access_users CASCADE",
        )
    }

    @Test
    fun `deleting an account cascades owned groups and preserves third party history`() {
        val deleted = sessionRepository.upsertAndLoad(
            SessionUpsert("deleted-subject", "same@example.test", true, AccessName.from("Deleted Public")),
        )
        val thirdPartyOwner = sessionRepository.upsertAndLoad(
            SessionUpsert("third-party-owner", "owner@example.test", true, AccessName.from("Group Owner")),
        )
        val ownedGroup = insertGroup(deleted.user.id, "Owned Group")
        val thirdPartyGroup = insertGroup(thirdPartyOwner.user.id, "Third Party Group")
        val ownedGame = insertGame(ownedGroup)
        execute("UPDATE games SET notes = 'Personal phone 11999990000', venue_court = 'Private detail' WHERE id = '$ownedGame'")
        insertSeriesSlot(ownedGroup)
        insertMembership(thirdPartyGroup, deleted.user.id, "ADMIN")
        insertEntryRequest(thirdPartyGroup, deleted.user.id)
        val game = insertGame(thirdPartyGroup)
        insertAttendance(game, thirdPartyGroup, deleted.user.id, "Deleted Public")
        insertCharge(thirdPartyGroup, deleted.user.id, thirdPartyOwner.user.id, "Deleted Public")
        val event = UUID.randomUUID()
        val selfEvent = UUID.randomUUID()
        execute("""INSERT INTO attendance_events(id, game_id, group_id, member_user_id, actor_user_id,
            source, old_status, new_status, reason, occurred_at)
            VALUES ('$event', '$game', '$thirdPartyGroup', '${deleted.user.id}', '${thirdPartyOwner.user.id}',
                'ORGANIZER', 'CONFIRMED', 'DECLINED', 'Personal explanation', now()),
                ('$selfEvent', '$game', '$thirdPartyGroup', '${deleted.user.id}', '${deleted.user.id}',
                'SELF', NULL, 'CONFIRMED', NULL, now())""")
        assertFailsWith<java.sql.SQLException> {
            execute("UPDATE attendance_events SET reason = 'Conta excluída' WHERE id = '$event'")
        }

        deleteAccount.execute("deleted-subject")

        assertEquals(1, count("SELECT count(*) FROM access_users WHERE deleted_at IS NOT NULL"))
        assertNull(textOrNull("SELECT email FROM access_users WHERE id = '${deleted.user.id}'"))
        assertEquals("Conta excluída", text("SELECT display_name FROM access_users WHERE id = '${deleted.user.id}'"))
        assertEquals(1, count("SELECT count(*) FROM access_groups WHERE id = '$ownedGroup' AND deleted_at IS NOT NULL"))
        assertEquals(1, count("SELECT count(*) FROM access_groups WHERE id = '$thirdPartyGroup' AND deleted_at IS NULL"))
        assertEquals(0, count("SELECT count(*) FROM group_memberships WHERE group_id = '$thirdPartyGroup' AND user_id = '${deleted.user.id}'"))
        assertEquals(0, count("SELECT count(*) FROM group_entry_requests WHERE user_id = '${deleted.user.id}'"))
        assertEquals(0, count("SELECT count(*) FROM group_membership_removals WHERE group_id = '$thirdPartyGroup' AND user_id = '${deleted.user.id}'"))
        assertEquals("Conta excluída", text("SELECT member_display_name FROM game_attendance WHERE member_user_id = '${deleted.user.id}'"))
        assertEquals("Conta excluída", text("SELECT member_display_name FROM group_charges WHERE member_user_id = '${deleted.user.id}'"))
        assertEquals("Conta excluída", text("SELECT reason FROM attendance_events WHERE id = '$event'"))
        assertNull(textOrNull("SELECT reason FROM attendance_events WHERE id = '$selfEvent'"))
        assertEquals("CONFIRMED", text("SELECT old_status FROM attendance_events WHERE id = '$event'"))
        assertEquals("DECLINED", text("SELECT new_status FROM attendance_events WHERE id = '$event'"))
        assertEquals(5000, count("SELECT amount_cents FROM group_charges WHERE member_user_id = '${deleted.user.id}'"))
        fun statement(actor: UUID, group: UUID) = FinanceStatementController(
            VerifiedGroupActorResolver { actor },
            FinanceStatementService(JdbcFinanceStatementRepository(dataSource)),
        ).list(RequestIdentity("verified-test-actor"), group.toString(), "2026-08")
        val retained = statement(thirdPartyOwner.user.id, thirdPartyGroup).items.single()
        assertEquals("Mensalidade · Conta excluída", retained.title)
        assertEquals(5000L, retained.amountCents)
        assertFailsWith<GameNotFoundException> { statement(deleted.user.id, thirdPartyGroup) }
        assertFailsWith<GameNotFoundException> { statement(deleted.user.id, ownedGroup) }
        assertFailsWith<GameNotFoundException> { statement(UUID.randomUUID(), thirdPartyGroup) }
        assertEquals("Endereço removido", text("SELECT venue_address FROM games WHERE id = '$ownedGame'"))
        assertEquals("Jogo excluído", text("SELECT title FROM games WHERE id = '$ownedGame'"))
        assertNull(textOrNull("SELECT notes FROM games WHERE id = '$ownedGame'"))
        assertNull(textOrNull("SELECT venue_court FROM games WHERE id = '$ownedGame'"))
        assertEquals("Endereço removido", text("SELECT venue_address FROM game_series_slots WHERE group_id = '$ownedGroup'"))
        assertEquals("Jogo excluído", text("SELECT title FROM game_series_slots WHERE group_id = '$ownedGroup'"))
        assertNull(textOrNull("SELECT venue_court FROM game_series_slots WHERE group_id = '$ownedGroup'"))
        assertEquals("Rua Central 100", text("SELECT venue_address FROM games WHERE id = '$game'"))
        assertFailsWith<java.sql.SQLException> {
            execute("UPDATE attendance_events SET new_status = 'WAITLISTED' WHERE id = '$event'")
        }
        assertFailsWith<java.sql.SQLException> {
            execute("DELETE FROM attendance_events WHERE id = '$event'")
        }

        deleteAccount.execute("deleted-subject")
        assertEquals(1, count("SELECT count(*) FROM access_users WHERE deleted_at IS NOT NULL"))

        assertFailsWith<AccountDeleted> {
            BootstrapSession(sessionRepository).execute(
                RequestIdentity("deleted-subject", "same@example.test", true, "New Person"),
            )
        }
        assertEquals(2, count("SELECT count(*) FROM access_users"))
        val replacement = assertIs<BootstrapSessionResult.Success>(
            BootstrapSession(sessionRepository).execute(
                RequestIdentity("new-provider-subject", "same@example.test", true, "New Person"),
            ),
        ).session
        assertNotEquals(deleted.user.id, replacement.user.id)
        assertEquals(0, replacement.memberships.size)
        assertEquals("New Person", replacement.user.displayName.value)
        assertEquals(3, count("SELECT count(*) FROM access_users"))
        assertEquals(0, count("SELECT count(*) FROM game_attendance WHERE member_user_id = '${replacement.user.id}'"))
        assertEquals(0, count("SELECT count(*) FROM group_charges WHERE member_user_id = '${replacement.user.id}'"))
    }

    private fun accountDeletion(): DeleteAccount {
        val transaction = JdbcTransactionRunner(dataSource)
        val deleteGroup = DeleteGroup(transaction, JdbcGroupDeletionRepository(dataSource))
        val athletes = JdbcAthleteRepository(dataSource)
        return AccessSessionConfiguration().deleteAccount(transaction, sessionRepository, deleteGroup, athletes, dataSource)
    }

    private fun insertGroup(owner: UUID, name: String): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at) " +
                "VALUES ('$id', '$owner', '${UUID.randomUUID()}', '$name', 'UTC', now(), now())",
        )
        return id
    }

    private fun insertMembership(group: UUID, user: UUID, role: String) = execute(
        "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) " +
            "VALUES ('$group', '$user', '$role', now(), now())",
    )

    private fun insertSeriesSlot(group: UUID) {
        val revision = UUID.randomUUID()
        execute("""INSERT INTO game_series(id, lineage_id, group_id, revision_number, zone_id,
            local_start_date, created_at, updated_at)
            VALUES ('$revision', '${UUID.randomUUID()}', '$group', 1, 'UTC', '2026-09-01', now(), now())""")
        execute("""INSERT INTO game_series_slots(series_revision_id, group_id, slot_key, title, weekday,
            local_time, duration_minutes, venue_name, venue_address, venue_court, capacity,
            confirmation_lead_minutes, created_at)
            VALUES ('$revision', '$group', '${UUID.randomUUID()}', 'Personal title', 1, '19:00', 90,
                'Personal venue', 'Private Street 123', 'Personal contact', 12, 1440, now())""")
    }

    private fun insertEntryRequest(group: UUID, user: UUID) = execute(
        "INSERT INTO group_entry_requests (group_id, user_id, requested_at) " +
            "VALUES ('$group', '$user', now())",
    )

    private fun insertGame(group: UUID): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO games (id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes, " +
                "confirmation_deadline, venue_name, venue_address, capacity, status, created_at, updated_at) VALUES " +
                "('$id', '$group', 'Training', DATE '2026-08-12', TIME '19:30', 'UTC', " +
                "TIMESTAMPTZ '2026-08-12 19:30Z', 90, TIMESTAMPTZ '2026-08-11 19:30Z', 'Arena', 'Rua Central 100', " +
                "12, 'PUBLISHED', now(), now())",
        )
        return id
    }

    private fun insertAttendance(game: UUID, group: UUID, member: UUID, displayName: String) = execute(
        "INSERT INTO game_attendance (game_id, group_id, member_user_id, status, responded_at, updated_at, " +
            "version, member_display_name) VALUES ('$game', '$group', '$member', 'CONFIRMED', now(), now(), 1, '$displayName')",
    )

    private fun insertCharge(group: UUID, member: UUID, actor: UUID, displayName: String) {
        val charge = UUID.randomUUID()
        execute(
            "INSERT INTO group_charges (id, group_id, member_user_id, kind, billing_month, amount_cents, due_date, " +
                "created_by_user_id, changed_by_user_id, created_at, updated_at, member_display_name, status) VALUES " +
                "('$charge', '$group', '$member', 'MONTHLY', DATE '2026-08-01', 5000, DATE '2026-08-10', " +
                "'$actor', '$actor', now(), now(), '$displayName', 'PAID')",
        )
        execute("""INSERT INTO group_charge_events(id, charge_id, group_id, actor_user_id, old_status, new_status, occurred_at)
            VALUES ('${UUID.randomUUID()}', '$charge', '$group', '$actor', NULL, 'PAID', '2026-08-10T12:00:00Z')""")
    }

    private fun execute(sql: String) {
        connection().use { it.createStatement().use { statement -> statement.execute(sql) } }
    }

    private fun count(sql: String): Int = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                result.next()
                result.getInt(1)
            }
        }
    }

    private fun text(sql: String): String = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                result.next()
                result.getString(1)
            }
        }
    }

    private fun textOrNull(sql: String): String? = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                result.next()
                result.getString(1)
            }
        }
    }

    private fun connection(): Connection = dataSource.connection
}
