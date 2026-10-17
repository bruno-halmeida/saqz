package br.com.saqz.groups.plan

import br.com.saqz.groups.adapter.output.jdbc.plan.JdbcOwnerPlanUsageLookup
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.util.UUID
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcOwnerPlanUsageLookupIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeEach
    fun resetDatabase() {
        dataSource = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    }

    @Test
    fun `two groups of the same owner do not add their athletes`() {
        val owner = user("owner")
        val first = group(owner)
        val second = group(owner)
        repeat(3) { membership(first, user("a$it"), "ATHLETE") }
        repeat(2) { membership(second, user("b$it"), "ATHLETE") }

        val usage = JdbcOwnerPlanUsageLookup(dataSource).usageFor(owner)

        assertEquals(2, usage.ownedGroupCount)
        assertEquals(3, usage.largestGroupAthleteCount)
    }

    @Test
    fun `the same athlete counts in each group and the owner never counts`() {
        val owner = user("owner")
        val first = group(owner)
        val second = group(owner)
        val shared = user("shared")
        membership(first, owner, "ADMIN")
        membership(first, shared, "ATHLETE")
        membership(second, shared, "ATHLETE")

        assertEquals(1, JdbcOwnerPlanUsageLookup(dataSource).usageFor(owner).largestGroupAthleteCount)
    }

    @Test
    fun `a member who left or was removed stops counting at once even with a removal record`() {
        val owner = user("owner")
        val group = group(owner)
        val stays = user("stays")
        val leaves = user("leaves")
        membership(group, stays, "ATHLETE")
        membership(group, leaves, "ATHLETE")
        val lookup = JdbcOwnerPlanUsageLookup(dataSource)
        assertEquals(2, lookup.usageFor(owner).largestGroupAthleteCount)

        execute("DELETE FROM group_memberships WHERE group_id = '$group' AND user_id = '$leaves'")
        execute(
            "INSERT INTO group_membership_removals (group_id, user_id, removed_at) " +
                "VALUES ('$group', '$leaves', now())",
        )

        assertEquals(1, lookup.usageFor(owner).largestGroupAthleteCount)
    }

    @Test
    fun `owner without groups or athletes has zero usage`() {
        val usage = JdbcOwnerPlanUsageLookup(dataSource).usageFor(user("alone"))

        assertEquals(0, usage.ownedGroupCount)
        assertEquals(0, usage.largestGroupAthleteCount)
    }

    private fun user(subject: String): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
                "VALUES ('$id', '$subject-$id', true, 'Plan Person', now(), now())",
        )
        return id
    }

    private fun group(owner: UUID): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, profile_status, " +
                "modality, composition, created_at, updated_at) VALUES " +
                "('$id', '$owner', '${UUID.randomUUID()}', 'Plan Group', 'UTC', 'COMPLETE', " +
                "'COURT_VOLLEYBALL', 'MIXED', now(), now())",
        )
        return id
    }

    private fun membership(group: UUID, user: UUID, role: String) = execute(
        "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) " +
            "VALUES ('$group', '$user', '$role', now(), now())",
    )

    private fun execute(sql: String) {
        dataSource.connection.use { it.createStatement().use { statement -> statement.execute(sql) } }
    }
}
