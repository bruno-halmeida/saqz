package br.com.saqz.groups.adapter.output.jdbc.migration

import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.Connection
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FinanceEnumMigrationIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeEach
    fun resetDatabase() {
        dataSource = TestPostgres.empty(owner = this).dataSource
    }

    @Test
    fun `v41 registers finance enums and converts the closed-set columns`() {
        flyway().migrate()

        assertEquals(
            listOf(
                "charge_kind" to listOf("GAME", "MONTHLY"),
                "charge_paid_method" to listOf("PIX", "CASH", "OTHER"),
                "charge_status" to listOf("PENDING", "PAID", "WAIVED", "CANCELLED"),
                "expense_action" to listOf("CREATED", "EDITED", "VOIDED"),
                "expense_category" to listOf("VENUE", "EQUIPMENT", "REFEREE", "RACHA", "OTHER"),
                "expense_direction" to listOf("IN", "OUT"),
                "expense_status" to listOf("ACTIVE", "VOIDED"),
            ),
            enumCatalog(),
        )
        assertEquals("charge_kind", udt("group_charges", "kind"))
        assertEquals("charge_status", udt("group_charges", "status"))
        assertEquals("charge_paid_method", udt("group_charges", "paid_method"))
        assertEquals("charge_status", udt("group_charge_events", "old_status"))
        assertEquals("charge_status", udt("group_charge_events", "new_status"))
        assertEquals("expense_category", udt("group_expenses", "category"))
        assertEquals("expense_status", udt("group_expenses", "status"))
        assertEquals("expense_direction", udt("group_expenses", "direction"))
        assertEquals("expense_action", udt("group_expense_events", "action"))
        assertEquals("expense_category", udt("group_expense_events", "category"))
        assertEquals("expense_status", udt("group_expense_events", "status"))
        assertEquals("expense_direction", udt("group_expense_events", "direction"))
        assertTrue(
            queryStrings(
                """
                SELECT conname FROM pg_constraint
                WHERE conrelid IN (
                    'group_charges'::regclass, 'group_charge_events'::regclass,
                    'group_expenses'::regclass, 'group_expense_events'::regclass
                )
                  AND contype = 'c'
                  AND conname IN (
                      'ck_group_charges_kind', 'ck_group_charges_status', 'ck_group_charges_paid_method',
                      'ck_group_charge_events_old', 'ck_group_charge_events_new',
                      'ck_group_expenses_category', 'ck_group_expenses_status', 'ck_group_expenses_direction',
                      'ck_group_expense_events_action', 'ck_group_expense_events_direction'
                  )
                """.trimIndent(),
            ).isEmpty(),
        )
        assertEquals(1, int("SELECT count(*) FROM pg_indexes WHERE indexname = 'uq_group_charges_game_member'"))
        assertEquals(1, int("SELECT count(*) FROM pg_indexes WHERE indexname = 'uq_group_charges_month_member'"))
        assertEquals("PENDING", defaultLabel("group_charges", "status"))
        assertEquals("ACTIVE", defaultLabel("group_expenses", "status"))
        assertEquals("OUT", defaultLabel("group_expenses", "direction"))
    }

    @Test
    fun `v40 to v41 preserves charge and expense labels including null paid method and old status`() {
        flyway("40").migrate()
        val fixture = fixture()
        val charge = UUID.randomUUID()
        execute(
            """
            INSERT INTO group_charges (
                id, group_id, member_user_id, kind, game_id, amount_cents, due_date, status,
                created_by_user_id, changed_by_user_id, created_at, updated_at, member_display_name
            ) VALUES (
                '$charge', '${fixture.group}', '${fixture.member}', 'GAME', '${fixture.game}', 2500,
                DATE '2026-08-10', 'PAID', '${fixture.owner}', '${fixture.owner}', now(), now(), 'Member'
            )
            """.trimIndent(),
        )
        execute("UPDATE group_charges SET paid_method = 'CASH' WHERE id = '$charge'")
        val monthly = UUID.randomUUID()
        execute(
            """
            INSERT INTO group_charges (
                id, group_id, member_user_id, kind, billing_month, amount_cents, due_date, status,
                created_by_user_id, changed_by_user_id, created_at, updated_at, member_display_name
            ) VALUES (
                '$monthly', '${fixture.group}', '${fixture.member}', 'MONTHLY', DATE '2026-08-01', 3000,
                DATE '2026-08-10', 'PENDING', '${fixture.owner}', '${fixture.owner}', now(), now(), 'Member'
            )
            """.trimIndent(),
        )
        execute(
            """
            INSERT INTO group_charge_events (
                id, charge_id, group_id, actor_user_id, old_status, new_status, occurred_at
            ) VALUES (
                '${UUID.randomUUID()}', '$charge', '${fixture.group}', '${fixture.owner}',
                NULL, 'PENDING', now()
            )
            """.trimIndent(),
        )
        val expense = UUID.randomUUID()
        execute(
            """
            INSERT INTO group_expenses (
                id, group_id, description, amount_cents, expense_date, category, notes,
                direction, status, created_by_user_id, changed_by_user_id, created_at, updated_at
            ) VALUES (
                '$expense', '${fixture.group}', 'Racha', 1200, DATE '2026-08-01', 'RACHA', 'Entrada',
                'IN', 'ACTIVE', '${fixture.owner}', '${fixture.owner}', now(), now()
            )
            """.trimIndent(),
        )
        execute(
            """
            INSERT INTO group_expense_events (
                id, expense_id, group_id, actor_user_id, action, description, amount_cents,
                expense_date, category, notes, direction, status, version, occurred_at
            ) VALUES (
                '${UUID.randomUUID()}', '$expense', '${fixture.group}', '${fixture.owner}',
                'CREATED', 'Racha', 1200, DATE '2026-08-01', 'RACHA', 'Entrada',
                'IN', 'ACTIVE', 1, now()
            )
            """.trimIndent(),
        )

        flyway().migrate()

        assertEquals("GAME", string("SELECT kind::text FROM group_charges WHERE id = '$charge'"))
        assertEquals("PAID", string("SELECT status::text FROM group_charges WHERE id = '$charge'"))
        assertEquals("CASH", string("SELECT paid_method::text FROM group_charges WHERE id = '$charge'"))
        assertEquals(null, nullableString("SELECT paid_method::text FROM group_charges WHERE id = '$monthly'"))
        assertEquals(null, nullableString("SELECT old_status::text FROM group_charge_events WHERE charge_id = '$charge'"))
        assertEquals("PENDING", string("SELECT new_status::text FROM group_charge_events WHERE charge_id = '$charge'"))
        assertEquals("RACHA", string("SELECT category::text FROM group_expenses WHERE id = '$expense'"))
        assertEquals("IN", string("SELECT direction::text FROM group_expenses WHERE id = '$expense'"))
        assertEquals("CREATED", string("SELECT action::text FROM group_expense_events WHERE expense_id = '$expense'"))
        assertEquals("charge_kind", udt("group_charges", "kind"))
    }

    @Test
    fun `v41 keeps charge pending and expense active defaults`() {
        flyway().migrate()
        val fixture = fixture()
        val charge = UUID.randomUUID()
        execute(
            """
            INSERT INTO group_charges (
                id, group_id, member_user_id, kind, game_id, amount_cents, due_date,
                created_by_user_id, changed_by_user_id, created_at, updated_at, member_display_name
            ) VALUES (
                '$charge', '${fixture.group}', '${fixture.member}', 'GAME', '${fixture.game}', 2500,
                DATE '2026-08-10', '${fixture.owner}', '${fixture.owner}', now(), now(), 'Member'
            )
            """.trimIndent(),
        )
        val expense = UUID.randomUUID()
        execute(
            """
            INSERT INTO group_expenses (
                id, group_id, description, amount_cents, expense_date, category,
                created_by_user_id, changed_by_user_id, created_at, updated_at
            ) VALUES (
                '$expense', '${fixture.group}', 'Aluguel', 5000, DATE '2026-08-01', 'VENUE',
                '${fixture.owner}', '${fixture.owner}', now(), now()
            )
            """.trimIndent(),
        )
        assertEquals("PENDING", string("SELECT status::text FROM group_charges WHERE id = '$charge'"))
        assertEquals("ACTIVE", string("SELECT status::text FROM group_expenses WHERE id = '$expense'"))
        assertEquals("OUT", string("SELECT direction::text FROM group_expenses WHERE id = '$expense'"))
    }

    @Test
    fun `v41 rejects labels that are not in the enums`() {
        flyway().migrate()
        val fixture = fixture()
        val charge = UUID.randomUUID()
        execute(
            """
            INSERT INTO group_charges (
                id, group_id, member_user_id, kind, game_id, amount_cents, due_date, status,
                created_by_user_id, changed_by_user_id, created_at, updated_at, member_display_name
            ) VALUES (
                '$charge', '${fixture.group}', '${fixture.member}', 'GAME', '${fixture.game}', 2500,
                DATE '2026-08-10', 'PENDING', '${fixture.owner}', '${fixture.owner}', now(), now(), 'Member'
            )
            """.trimIndent(),
        )
        assertFailsWith<Exception> {
            execute("UPDATE group_charges SET status = 'SETTLED' WHERE id = '$charge'")
        }
        assertFailsWith<Exception> {
            execute("UPDATE group_charges SET paid_method = 'CREDIT_CARD' WHERE id = '$charge'")
        }
        assertFailsWith<Exception> {
            execute(
                """
                INSERT INTO group_expenses (
                    id, group_id, description, amount_cents, expense_date, category,
                    created_by_user_id, changed_by_user_id, created_at, updated_at
                ) VALUES (
                    '${UUID.randomUUID()}', '${fixture.group}', 'Aluguel', 5000, DATE '2026-08-01', 'MONTHLY',
                    '${fixture.owner}', '${fixture.owner}', now(), now()
                )
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `v41 restores game and monthly charge uniqueness`() {
        flyway().migrate()
        val first = fixture()
        execute(
            """
            INSERT INTO group_charges (
                id, group_id, member_user_id, kind, game_id, amount_cents, due_date, status,
                created_by_user_id, changed_by_user_id, created_at, updated_at, member_display_name
            ) VALUES (
                '${UUID.randomUUID()}', '${first.group}', '${first.member}', 'GAME', '${first.game}', 2500,
                DATE '2026-08-10', 'PENDING', '${first.owner}', '${first.owner}', now(), now(), 'Member'
            )
            """.trimIndent(),
        )
        assertFailsWith<Exception> {
            execute(
                """
                INSERT INTO group_charges (
                    id, group_id, member_user_id, kind, game_id, amount_cents, due_date, status,
                    created_by_user_id, changed_by_user_id, created_at, updated_at, member_display_name
                ) VALUES (
                    '${UUID.randomUUID()}', '${first.group}', '${first.member}', 'GAME', '${first.game}', 2500,
                    DATE '2026-08-10', 'PENDING', '${first.owner}', '${first.owner}', now(), now(), 'Member'
                )
                """.trimIndent(),
            )
        }
    }

    private fun flyway(target: String? = null): Flyway {
        val configuration = Flyway.configure()
            .dataSource(dataSource)
            .locations(*allGroupFeatureMigrationLocations())
            .cleanDisabled(false)
        if (target != null) configuration.target(target)
        return configuration.load()
    }

    private fun fixture(): Fixture {
        val owner = user("enum-owner")
        val group = UUID.randomUUID()
        execute(
            """
            INSERT INTO access_groups (
                id, owner_user_id, creation_key, name, time_zone, profile_status,
                modality, composition, created_at, updated_at
            ) VALUES (
                '$group', '$owner', '${UUID.randomUUID()}', 'Enum Group', 'America/Sao_Paulo',
                'COMPLETE', 'COURT_VOLLEYBALL', 'MIXED', now(), now()
            )
            """.trimIndent(),
        )
        val member = user("enum-member")
        execute(
            "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) " +
                "VALUES ('$group', '$member', 'ATHLETE', now(), now())",
        )
        val game = UUID.randomUUID()
        execute(
            """
            INSERT INTO games (
                id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes,
                confirmation_deadline, venue_name, venue_address, capacity, status, created_at, updated_at
            ) VALUES (
                '$game', '$group', 'Treino', DATE '2026-08-12', TIME '19:30', 'America/Sao_Paulo',
                TIMESTAMPTZ '2026-08-12 22:30Z', 90, TIMESTAMPTZ '2026-08-11 22:30Z',
                'Arena', 'Rua Central 100', 12, 'PUBLISHED', now(), now()
            )
            """.trimIndent(),
        )
        return Fixture(owner, group, member, game)
    }

    private fun user(subject: String): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
                "VALUES ('$id', '$subject-${UUID.randomUUID()}', true, 'User', now(), now())",
        )
        return id
    }

    private fun enumCatalog(): List<Pair<String, List<String>>> {
        val labels = mutableMapOf<String, MutableList<String>>()
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    """
                    SELECT t.typname, e.enumlabel
                    FROM pg_type t
                    JOIN pg_namespace n ON n.oid = t.typnamespace
                    JOIN pg_enum e ON e.enumtypid = t.oid
                    WHERE n.nspname = 'public'
                      AND t.typname IN (
                          'charge_kind', 'charge_status', 'charge_paid_method',
                          'expense_category', 'expense_status', 'expense_direction', 'expense_action'
                      )
                    ORDER BY t.typname, e.enumsortorder
                    """.trimIndent(),
                ).use { result ->
                    while (result.next()) {
                        labels.getOrPut(result.getString("typname")) { mutableListOf() }
                            .add(result.getString("enumlabel"))
                    }
                }
            }
        }
        return labels.toSortedMap().map { it.key to it.value }
    }

    private fun execute(sql: String) {
        connection().use { connection -> connection.createStatement().use { it.execute(sql) } }
    }

    private fun string(sql: String): String = query(sql) { it.getString(1) }

    private fun nullableString(sql: String): String? = query(sql) { it.getString(1) }

    private fun int(sql: String): Int = query(sql) { it.getInt(1) }

    private fun udt(table: String, column: String): String =
        string("SELECT udt_name FROM information_schema.columns WHERE table_name = '$table' AND column_name = '$column'")

    private fun defaultLabel(table: String, column: String): String {
        val default = string(
            "SELECT column_default FROM information_schema.columns WHERE table_name = '$table' AND column_name = '$column'",
        )
        return default.substringAfter("'").substringBefore("'")
    }

    private fun queryStrings(sql: String): List<String> {
        val values = mutableListOf<String>()
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { result ->
                    while (result.next()) values += result.getString(1)
                }
            }
        }
        return values
    }

    private fun <T> query(sql: String, read: (java.sql.ResultSet) -> T): T = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                check(result.next())
                read(result)
            }
        }
    }

    private fun connection(): Connection = dataSource.connection

    private data class Fixture(val owner: UUID, val group: UUID, val member: UUID, val game: UUID)
}
