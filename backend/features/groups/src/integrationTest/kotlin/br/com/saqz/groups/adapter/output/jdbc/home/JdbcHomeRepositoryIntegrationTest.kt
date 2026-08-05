package br.com.saqz.groups.adapter.output.jdbc.home

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
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcHomeRepositoryIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeAll
    fun start() {
        postgres.startAndAwaitJdbc()
        dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
    }

    @BeforeEach
    fun reset() {
        flyway().clean()
        flyway().migrate()
    }

    @AfterAll
    fun stop() = postgres.stop()

    @Test
    fun aggregatesMemberAndAdminBlocksAcrossAuthorizedGroups() {
        val actor = user("home-actor", "Home Actor")
        val confirmed = user("home-confirmed", "Confirmed Person")
        val declined = user("home-declined", "Declined Person")
        val waitlisted = user("home-waitlisted", "Waitlisted Person")
        val pending = user("home-pending", "Pending Person")
        val otherOwner = user("home-other-owner", "Other Owner")
        val entryOne = user("home-entry-one", "Entry One")
        val entryTwo = user("home-entry-two", "Entry Two")
        val ownerGroup = group("Owner home", actor)
        val memberGroup = group("Member home", otherOwner)
        val athleteOnlyGroup = group("Athlete only", otherOwner)
        val adminGroup = group("Admin home", otherOwner)

        membership(ownerGroup, actor, role = "ADMIN", membershipType = "MENSALISTA")
        membership(ownerGroup, confirmed)
        membership(ownerGroup, declined)
        membership(ownerGroup, waitlisted)
        membership(ownerGroup, pending)
        membership(memberGroup, actor)
        membership(athleteOnlyGroup, actor)
        membership(adminGroup, actor, role = "ADMIN")

        val next = game(ownerGroup, "2026-08-02T10:00:00Z", "PUBLISHED", capacity = 4)
        game(ownerGroup, "2026-08-01T08:00:00Z", "CANCELLED", capacity = 4)
        val laterNext = game(memberGroup, "2026-08-03T10:00:00Z", "PUBLISHED", capacity = 4)
        val latestCompleted = game(ownerGroup, "2026-07-31T10:00:00Z", "COMPLETED", capacity = 4)
        val olderCompleted = game(memberGroup, "2026-07-20T10:00:00Z", "COMPLETED", capacity = 4)
        val settleOlder = game(adminGroup, "2026-07-20T10:00:00Z", "COMPLETED", capacity = 4)
        val settleLatest = game(adminGroup, "2026-07-30T10:00:00Z", "COMPLETED", capacity = 4)
        check(laterNext != next && olderCompleted != latestCompleted && settleOlder != settleLatest)

        attendance(next, ownerGroup, confirmed, "CONFIRMED", null)
        attendance(next, ownerGroup, declined, "DECLINED", null)
        attendance(next, ownerGroup, waitlisted, "WAITLISTED", 1)
        attendance(next, ownerGroup, actor, "WAITLISTED", 2)
        attendance(latestCompleted, ownerGroup, actor, "CONFIRMED", null)
        attendance(olderCompleted, memberGroup, actor, "DECLINED", null)

        entryRequest(ownerGroup, entryOne)
        entryRequest(ownerGroup, entryTwo)
        monthlyCharge(ownerGroup, confirmed, 100, "2026-08-01", "PENDING", actor)
        monthlyCharge(ownerGroup, declined, 200, "2026-08-01", "PENDING", actor)
        monthlyCharge(ownerGroup, waitlisted, 999, "2026-07-01", "PENDING", actor)
        monthlyCharge(adminGroup, confirmed, 300, "2026-08-01", "PAID", actor)
        gameCharge(adminGroup, confirmed, settleOlder, 100, "PENDING", actor)
        gameCharge(adminGroup, declined, settleLatest, 150, "PENDING", actor)
        gameCharge(adminGroup, waitlisted, settleLatest, 350, "PENDING", actor)

        val home = repository().find(
            actorId = actor,
            now = Instant.parse("2026-08-01T12:00:00Z"),
            currentMonth = YearMonth.of(2026, 8),
        )

        assertEquals(listOf("Admin home", "Athlete only", "Member home", "Owner home"), home.member.groups.map { it.name })
        assertEquals(listOf("ADMIN", "ATHLETE", "ATHLETE", "OWNER"), home.member.groups.map { it.role.name })
        assertEquals(5, home.member.groups.single { it.name == "Owner home" }.memberCount)
        assertEquals(1, home.member.groups.single { it.name == "Member home" }.gamesPlayed)
        assertEquals(next, home.member.nextGame?.gameId)
        assertEquals("Owner home", home.member.nextGame?.groupName)
        assertEquals("Home court", home.member.nextGame?.local)
        assertEquals("America/Sao_Paulo", home.member.nextGame?.zoneId)
        assertEquals(4, home.member.nextGame?.capacity)
        assertEquals(1, home.member.nextGame?.confirmedCount)
        assertEquals(1, home.member.nextGame?.declinedCount)
        assertEquals(2, home.member.nextGame?.waitlistCount)
        assertEquals(1, home.member.nextGame?.pendingCount)
        assertEquals("WAITLISTED", home.member.nextGame?.ownAttendance?.status?.name)
        assertEquals(2, home.member.nextGame?.ownAttendance?.waitlistPosition)
        assertEquals("MENSALISTA", home.member.nextGame?.membershipType?.name)
        assertTrue(home.member.nextGame?.mensalistaPriority == true)
        assertEquals(listOf("Confirmed Person"), home.member.nextGame?.rosterPreview?.confirmed?.map { it.displayName })
        assertEquals(
            listOf("Waitlisted Person", "Home Actor"),
            home.member.nextGame?.rosterPreview?.waitlisted?.map { it.displayName },
        )
        assertEquals(listOf(1L, 2L), home.member.nextGame?.rosterPreview?.waitlisted?.map { it.waitlistPosition })
        assertEquals(latestCompleted, home.member.lastCompletedGame?.gameId)
        assertEquals("America/Sao_Paulo", home.member.lastCompletedGame?.zoneId)
        assertEquals(1, home.member.lastCompletedGame?.confirmedCount)
        assertTrue(home.member.lastCompletedGame?.ownPlayed == true)

        val admin = requireNotNull(home.admin)
        assertEquals(listOf("Admin home", "Owner home"), admin.groups.map { it.name })
        val ownerAdmin = admin.groups.single { it.name == "Owner home" }
        assertEquals(2, ownerAdmin.entryRequestCount)
        assertEquals(2, ownerAdmin.monthlyCharges.count)
        assertEquals(300, ownerAdmin.monthlyCharges.totalCents)
        assertNull(ownerAdmin.gameToSettle)
        val adminAdmin = admin.groups.single { it.name == "Admin home" }
        assertEquals(settleLatest, adminAdmin.gameToSettle?.gameId)
        assertEquals(2, adminAdmin.gameToSettle?.pendingCount)
        assertEquals(500, adminAdmin.gameToSettle?.totalCents)
        assertTrue(admin.groups.none { it.name == "Athlete only" })
    }

    @Test
    fun returnsEmptyMemberDataAndNoAdminBlockForUnknownActor() {
        val home = repository().find(
            actorId = UUID.randomUUID(),
            now = Instant.parse("2026-08-01T12:00:00Z"),
            currentMonth = YearMonth.of(2026, 8),
        )

        assertTrue(home.member.groups.isEmpty())
        assertNull(home.member.nextGame)
        assertNull(home.member.lastCompletedGame)
        assertNull(home.admin)
    }

    private fun repository() = JdbcHomeRepository(dataSource)

    private fun user(subject: String, displayName: String): UUID {
        val id = UUID.randomUUID()
        val firebaseSubject = subject + "-" + UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id,firebase_subject,email_verified,display_name,created_at,updated_at) " +
                "VALUES ('$id','$firebaseSubject',true,'$displayName',now(),now())",
        )
        return id
    }

    private fun group(name: String, owner: UUID): UUID {
        val id = UUID.randomUUID()
        val creationKey = UUID.randomUUID()
        execute(
            "INSERT INTO access_groups (id,owner_user_id,creation_key,name,time_zone,profile_status,modality,composition,created_at,updated_at) " +
                "VALUES ('$id','$owner','$creationKey','$name','America/Sao_Paulo','COMPLETE','COURT_VOLLEYBALL','MIXED',now(),now())",
        )
        return id
    }

    private fun membership(
        group: UUID,
        member: UUID,
        role: String = "ATHLETE",
        membershipType: String = "AVULSO",
        active: Boolean = true,
    ) {
        execute(
            "INSERT INTO group_memberships (group_id,user_id,role,created_at,updated_at,membership_type,active) " +
                "VALUES ('$group','$member','$role',now(),now(),'$membershipType',$active)",
        )
    }

    private fun game(group: UUID, startsAt: String, status: String, capacity: Int): UUID {
        val id = UUID.randomUUID()
        val start = Instant.parse(startsAt)
        val local = start.atZone(ZoneId.of("America/Sao_Paulo"))
        val deadline = start.minusSeconds(86_400)
        val localDate = local.toLocalDate()
        val localTime = local.toLocalTime()
        execute(
            "INSERT INTO games (id,group_id,title,local_date,local_time,zone_id,starts_at,duration_minutes,confirmation_deadline," +
                "venue_name,venue_address,capacity,status,created_at,updated_at) " +
                "VALUES ('$id','$group','Home game',DATE '$localDate',TIME '$localTime'," +
                "'America/Sao_Paulo',TIMESTAMPTZ '$startsAt',90,TIMESTAMPTZ '$deadline','Home court','Home address'," +
                "$capacity,'$status',now(),now())",
        )
        return id
    }

    private fun attendance(
        game: UUID,
        group: UUID,
        member: UUID,
        status: String,
        waitlistPosition: Long?,
    ) {
        val sequence = waitlistPosition?.toString() ?: "NULL"
        val displayName = memberName(member)
        execute(
            "INSERT INTO game_attendance (game_id,group_id,member_user_id,status,waitlist_sequence,responded_at,updated_at,member_display_name) " +
                "VALUES ('$game','$group','$member','$status',$sequence,now(),now(),'$displayName')",
        )
    }

    private fun entryRequest(group: UUID, user: UUID) {
        execute("INSERT INTO group_entry_requests (group_id,user_id,requested_at) VALUES ('$group','$user',now())")
    }

    private fun monthlyCharge(
        group: UUID,
        member: UUID,
        amount: Long,
        billingMonth: String,
        status: String,
        actor: UUID,
    ) {
        val id = UUID.randomUUID()
        val displayName = memberName(member)
        execute(
            "INSERT INTO group_charges (id,group_id,member_user_id,kind,billing_month,amount_cents,due_date,status," +
                "created_by_user_id,changed_by_user_id,created_at,updated_at,member_display_name) " +
                "VALUES ('$id','$group','$member','MONTHLY',DATE '$billingMonth',$amount,DATE '$billingMonth','$status'," +
                "'$actor','$actor',now(),now(),'$displayName')",
        )
    }

    private fun gameCharge(
        group: UUID,
        member: UUID,
        game: UUID,
        amount: Long,
        status: String,
        actor: UUID,
    ) {
        val id = UUID.randomUUID()
        val displayName = memberName(member)
        execute(
            "INSERT INTO group_charges (id,group_id,member_user_id,kind,game_id,amount_cents,due_date,status," +
                "created_by_user_id,changed_by_user_id,created_at,updated_at,member_display_name) " +
                "VALUES ('$id','$group','$member','GAME','$game',$amount,DATE '2026-08-01','$status'," +
                "'$actor','$actor',now(),now(),'$displayName')",
        )
    }

    private fun memberName(member: UUID): String =
        query("SELECT display_name FROM access_users WHERE id = '$member'") { it.getString(1) }

    private fun flyway() = Flyway.configure()
        .dataSource(dataSource)
        .locations(*allGroupFeatureMigrationLocations())
        .cleanDisabled(false)
        .load()

    private fun execute(sql: String) {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement -> statement.execute(sql) }
        }
    }

    private fun <T> query(sql: String, read: (java.sql.ResultSet) -> T): T =
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { result ->
                    check(result.next())
                    read(result)
                }
            }
        }
}
