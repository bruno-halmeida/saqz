package br.com.saqz.groups.adapter.output.jdbc.athlete

import br.com.saqz.groups.application.athlete.AthleteRosterFilter
import br.com.saqz.groups.application.athlete.FinancialStatus
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.AthletePosition
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.groups.testing.startAndAwaitJdbc
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcAthleteRosterRepositoryIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var repository: JdbcAthleteRosterRepository

    @BeforeAll
    fun startDatabase() {
        postgres.startAndAwaitJdbc()
        dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(dataSource).locations(*allGroupFeatureMigrationLocations()).load().migrate()
        repository = JdbcAthleteRosterRepository(dataSource)
    }

    @AfterAll
    fun stopDatabase() = postgres.stop()

    @BeforeEach
    fun clearData() {
        execute("TRUNCATE group_charges, group_memberships, access_groups, access_users CASCADE")
    }

    @Test
    fun `list returns name phone position type and active for every member`() {
        val owner = insertUser("roster-owner", "Owner Person", "+5511988887777")
        val group = insertGroup(owner)
        val member = insertUser("roster-member", "Member Person", "+5511977776666")
        insertMembership(group, member, position = "PONTA", membershipType = "MENSALISTA")

        val roster = repository.list(group, AthleteRosterFilter())

        val entry = roster.single { it.userId == member }
        assertEquals("Member Person", entry.displayName.value)
        assertEquals("+5511977776666", entry.phone)
        assertEquals(AthletePosition.PONTA, entry.position)
        assertEquals(AthleteMembershipType.MENSALISTA, entry.membershipType)
        assertTrue(entry.active)
    }

    @Test
    fun `list excludes inactive members by default`() {
        val owner = insertUser("inactive-default-owner", "Owner Person")
        val group = insertGroup(owner)
        val inactive = insertUser("inactive-default-member", "Inactive Person")
        insertMembership(group, inactive, active = false)

        val roster = repository.list(group, AthleteRosterFilter())

        assertTrue(roster.none { it.userId == inactive })
    }

    @Test
    fun `list includes inactive members when requested`() {
        val owner = insertUser("inactive-include-owner", "Owner Person")
        val group = insertGroup(owner)
        val inactive = insertUser("inactive-include-member", "Inactive Person")
        insertMembership(group, inactive, active = false)

        val roster = repository.list(group, AthleteRosterFilter(includeInactive = true))

        assertTrue(roster.any { it.userId == inactive })
    }

    @Test
    fun `list filters by membership type`() {
        val owner = insertUser("type-owner", "Owner Person")
        val group = insertGroup(owner)
        val mensalista = insertUser("type-mensalista", "Mensalista Person")
        val avulso = insertUser("type-avulso", "Avulso Person")
        insertMembership(group, mensalista, membershipType = "MENSALISTA")
        insertMembership(group, avulso, membershipType = "AVULSO")

        val roster = repository.list(group, AthleteRosterFilter(membershipType = AthleteMembershipType.MENSALISTA))

        assertEquals(setOf(mensalista), roster.map { it.userId }.toSet())
    }

    @Test
    fun `list filters by position`() {
        val owner = insertUser("position-owner", "Owner Person")
        val group = insertGroup(owner)
        val libero = insertUser("position-libero", "Libero Person")
        val central = insertUser("position-central", "Central Person")
        insertMembership(group, libero, position = "LIBERO")
        insertMembership(group, central, position = "CENTRAL")

        val roster = repository.list(group, AthleteRosterFilter(position = AthletePosition.LIBERO))

        assertEquals(setOf(libero), roster.map { it.userId }.toSet())
    }

    @Test
    fun `list search is case insensitive`() {
        val owner = insertUser("case-owner", "Owner Person")
        val group = insertGroup(owner)
        val member = insertUser("case-member", "Fulano Silva")
        insertMembership(group, member)

        val roster = repository.list(group, AthleteRosterFilter(search = "fulano"))

        assertEquals(setOf(member), roster.map { it.userId }.toSet())
    }

    @Test
    fun `list search is accent insensitive`() {
        val owner = insertUser("accent-owner", "Owner Person")
        val group = insertGroup(owner)
        val member = insertUser("accent-member", "João Ávila")
        insertMembership(group, member)

        val roster = repository.list(group, AthleteRosterFilter(search = "joao avila"))

        assertEquals(setOf(member), roster.map { it.userId }.toSet())
    }

    @Test
    fun `list combines search type position and active filters with AND`() {
        val owner = insertUser("combine-owner", "Owner Person")
        val group = insertGroup(owner)
        val match = insertUser("combine-match", "Combine Match")
        val wrongType = insertUser("combine-wrong-type", "Combine Match")
        insertMembership(group, match, position = "OPOSTO", membershipType = "MENSALISTA")
        insertMembership(group, wrongType, position = "OPOSTO", membershipType = "AVULSO")

        val roster = repository.list(
            group,
            AthleteRosterFilter(search = "combine", membershipType = AthleteMembershipType.MENSALISTA, position = AthletePosition.OPOSTO),
        )

        assertEquals(setOf(match), roster.map { it.userId }.toSet())
    }

    @Test
    fun `list orders members deterministically by display name then user id`() {
        val owner = insertUser("order-owner", "Owner Person")
        val group = insertGroup(owner)
        val zeta = insertUser("order-zeta", "Zeta Person")
        val alpha = insertUser("order-alpha", "Alpha Person")
        insertMembership(group, zeta)
        insertMembership(group, alpha)

        val roster = repository.list(group, AthleteRosterFilter())

        val names = roster.map { it.displayName.value }
        assertEquals(names.sorted(), names)
    }

    @Test
    fun `list reports PENDENTE for a member with a pending charge due within the current month`() {
        val owner = insertUser("pending-owner", "Owner Person")
        val group = insertGroup(owner)
        val member = insertUser("pending-member", "Pending Person")
        insertMembership(group, member)
        insertCharge(group, member, status = "PENDING", dueDate = LocalDate.now().withDayOfMonth(1))

        val roster = repository.list(group, AthleteRosterFilter())

        assertEquals(FinancialStatus.PENDENTE, roster.single { it.userId == member }.financialStatus)
    }

    @Test
    fun `list reports EM_DIA for a member with no pending charges`() {
        val owner = insertUser("em-dia-owner", "Owner Person")
        val group = insertGroup(owner)
        val member = insertUser("em-dia-member", "Em Dia Person")
        insertMembership(group, member)
        insertCharge(group, member, status = "PAID", dueDate = LocalDate.now().withDayOfMonth(1))

        val roster = repository.list(group, AthleteRosterFilter())

        assertEquals(FinancialStatus.EM_DIA, roster.single { it.userId == member }.financialStatus)
    }

    @Test
    fun `list filters by financial status`() {
        val owner = insertUser("financial-filter-owner", "Owner Person")
        val group = insertGroup(owner)
        val pending = insertUser("financial-filter-pending", "Pending Person")
        val current = insertUser("financial-filter-current", "Current Person")
        insertMembership(group, pending)
        insertMembership(group, current)
        insertCharge(group, pending, status = "PENDING", dueDate = LocalDate.now().withDayOfMonth(1))

        val roster = repository.list(group, AthleteRosterFilter(financialStatus = FinancialStatus.PENDENTE))

        assertEquals(setOf(pending), roster.map { it.userId }.toSet())
    }

    @Test
    fun `list degrades to DESCONHECIDO without failing when charges cannot be read`() {
        val owner = insertUser("unknown-owner", "Owner Person")
        val group = insertGroup(owner)
        val member = insertUser("unknown-member", "Unknown Person")
        insertMembership(group, member)

        execute("ALTER TABLE group_charges RENAME TO group_charges_tmp")
        try {
            val roster = repository.list(group, AthleteRosterFilter())
            assertEquals(FinancialStatus.DESCONHECIDO, roster.single { it.userId == member }.financialStatus)
        } finally {
            execute("ALTER TABLE group_charges_tmp RENAME TO group_charges")
        }
    }

    @Test
    fun `findOwnProfile returns the caller's memberships across groups with attributes`() {
        val athlete = insertUser("own-profile-athlete", "Own Profile Person", "+5511911112222")
        val ownerOne = insertUser("own-profile-owner-one", "Owner One")
        val ownerTwo = insertUser("own-profile-owner-two", "Owner Two")
        val groupOne = insertGroup(ownerOne, name = "Group One")
        val groupTwo = insertGroup(ownerTwo, name = "Group Two")
        insertMembership(groupOne, athlete, position = "CENTRAL", membershipType = "MENSALISTA")
        insertMembership(groupTwo, athlete, position = "LIBERO")

        val profile = repository.findOwnProfile(athlete)

        assertEquals("Own Profile Person", profile?.displayName?.value)
        assertEquals("+5511911112222", profile?.phone)
        assertEquals(setOf(groupOne, groupTwo), profile?.memberships?.map { it.groupId }?.toSet())
        assertEquals(AthletePosition.CENTRAL, profile?.memberships?.single { it.groupId == groupOne }?.position)
        assertEquals(AthleteMembershipType.MENSALISTA, profile?.memberships?.single { it.groupId == groupOne }?.membershipType)
        assertEquals(GroupRole.ATHLETE, profile?.memberships?.single { it.groupId == groupOne }?.role)
    }

    @Test
    fun `findOwnProfile never exposes another member's phone`() {
        val owner = insertUser("own-profile-privacy-owner", "Owner Person", "+5511900000000")
        val group = insertGroup(owner)
        val athlete = insertUser("own-profile-privacy-athlete", "Athlete Person", "+5511999998888")
        insertMembership(group, athlete)

        val ownProfile = repository.findOwnProfile(athlete)

        assertEquals("+5511999998888", ownProfile?.phone)
        assertTrue(ownProfile.toString().contains("+5511999998888"))
        assertTrue(!ownProfile.toString().contains("+5511900000000"))
    }

    @Test
    fun `findOwnProfile returns null for an unknown user`() {
        assertNull(repository.findOwnProfile(UUID.randomUUID()))
    }

    private fun insertUser(subject: String, name: String, phone: String? = null): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, phone, created_at, updated_at) " +
                "VALUES ('$id', '$subject-${UUID.randomUUID()}', true, '$name', ${phone?.let { "'$it'" } ?: "NULL"}, now(), now())",
        )
        return id
    }

    private fun insertGroup(owner: UUID, name: String = "Training Group"): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at) " +
                "VALUES ('$id', '$owner', '${UUID.randomUUID()}', '$name', 'America/Sao_Paulo', now(), now())",
        )
        return id
    }

    private fun insertMembership(
        group: UUID,
        user: UUID,
        role: String = "ATHLETE",
        position: String? = null,
        membershipType: String = "AVULSO",
        active: Boolean = true,
    ) = execute(
        "INSERT INTO group_memberships (group_id, user_id, role, position, membership_type, active, created_at, updated_at) " +
            "VALUES ('$group', '$user', '$role', ${position?.let { "'$it'" } ?: "NULL"}, '$membershipType', $active, now(), now())",
    )

    private fun insertCharge(group: UUID, member: UUID, status: String, dueDate: LocalDate) = execute(
        "INSERT INTO group_charges (id, group_id, member_user_id, kind, billing_month, amount_cents, due_date, status, created_by_user_id, changed_by_user_id, created_at, updated_at, member_display_name) VALUES " +
            "('${UUID.randomUUID()}', '$group', '$member', 'MONTHLY', DATE '${dueDate.withDayOfMonth(1)}', 5000, DATE '$dueDate', '$status', '$member', '$member', now(), now(), 'Member')",
    )

    private fun execute(sql: String) { connection().use { it.createStatement().use { statement -> statement.execute(sql) } } }
    private fun connection(): Connection = dataSource.connection
}
