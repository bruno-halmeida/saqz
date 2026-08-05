package br.com.saqz.groups.adapter.output.jdbc.home

import br.com.saqz.groups.application.home.HomeOwnChargeOldest
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcHomeRepositoryIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeEach
    fun reset() {
        dataSource = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    }

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
            today = LocalDate.of(2026, 8, 1),
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
        assertEquals(YearMonth.of(2026, 8), ownerAdmin.monthlyCharges.billingMonth)
        assertNull(ownerAdmin.gameToSettle)
        val adminAdmin = admin.groups.single { it.name == "Admin home" }
        assertEquals(settleLatest, adminAdmin.gameToSettle?.gameId)
        assertEquals("America/Sao_Paulo", adminAdmin.gameToSettle?.zoneId)
        assertEquals(2, adminAdmin.gameToSettle?.pendingCount)
        assertEquals(500, adminAdmin.gameToSettle?.totalCents)
        assertEquals(YearMonth.of(2026, 8), adminAdmin.monthlyCharges.billingMonth)
        assertTrue(admin.groups.none { it.name == "Athlete only" })
        assertNull(home.ownCharges)
    }

    @Test
    fun aggregatesOwnPendingChargesPerGroupWithPixAndOverdueFlag() {
        val actor = user("own-actor", "Own Actor")
        val other = user("own-other", "Other Person")
        val owner = user("own-owner", "Own Owner")
        val monthlyGroup = group("Cobranca A", owner)
        val gameGroup = group("Cobranca B", owner)
        val deletedGroup = group("Cobranca C", owner)
        membership(monthlyGroup, actor)
        membership(gameGroup, actor)
        membership(deletedGroup, actor)
        pix(monthlyGroup, "racha@saqz.test", "Tesoureiro")
        execute("UPDATE access_groups SET deleted_at = now() WHERE id = '$deletedGroup'")

        // Competência mais antiga (julho) vence depois da mais nova: separa "mais antiga" de "mais próxima".
        monthlyCharge(monthlyGroup, actor, 900, "2026-07-01", "PENDING", owner, dueDate = "2026-08-15")
        monthlyCharge(monthlyGroup, actor, 600, "2026-08-01", "PENDING", owner, dueDate = "2026-08-05")
        monthlyCharge(monthlyGroup, actor, 111, "2026-06-01", "PAID", owner)
        monthlyCharge(monthlyGroup, actor, 112, "2026-05-01", "WAIVED", owner)
        monthlyCharge(monthlyGroup, actor, 113, "2026-04-01", "CANCELLED", owner)
        monthlyCharge(monthlyGroup, other, 222, "2026-07-01", "PENDING", owner)
        val played = game(gameGroup, "2026-07-20T10:00:00Z", "COMPLETED", capacity = 4)
        gameCharge(gameGroup, actor, played, 300, "PENDING", owner, dueDate = "2026-07-25")
        monthlyCharge(deletedGroup, actor, 400, "2026-07-01", "PENDING", owner)

        val ownCharges = requireNotNull(
            repository().find(
                actorId = actor,
                now = Instant.parse("2026-08-01T12:00:00Z"),
                today = LocalDate.of(2026, 8, 1),
            ).ownCharges,
        )

        assertEquals(2, ownCharges.groupCount)
        assertEquals(1_800, ownCharges.totalCents)
        assertEquals(listOf("Cobranca A", "Cobranca B"), ownCharges.groups.map { it.groupName })

        val monthly = ownCharges.groups.single { it.groupName == "Cobranca A" }
        assertEquals(monthlyGroup, monthly.groupId)
        assertEquals(2, monthly.count)
        assertEquals(1_500, monthly.totalCents)
        assertEquals(LocalDate.of(2026, 8, 5), monthly.nextDueDate)
        assertEquals(false, monthly.overdue)
        assertEquals("racha@saqz.test", monthly.pixKey)
        assertEquals("Tesoureiro", monthly.pixLabel)
        assertEquals(
            HomeOwnChargeOldest.Monthly(YearMonth.of(2026, 7), LocalDate.of(2026, 8, 15)),
            monthly.oldest,
        )

        val byGame = ownCharges.groups.single { it.groupName == "Cobranca B" }
        assertEquals(1, byGame.count)
        assertEquals(300, byGame.totalCents)
        assertEquals(LocalDate.of(2026, 7, 25), byGame.nextDueDate)
        assertTrue(byGame.overdue)
        assertNull(byGame.pixKey)
        assertNull(byGame.pixLabel)
        assertEquals(
            HomeOwnChargeOldest.Game(
                gameId = played,
                startsAt = Instant.parse("2026-07-20T10:00:00Z"),
                zoneId = "America/Sao_Paulo",
                dueDate = LocalDate.of(2026, 7, 25),
            ),
            byGame.oldest,
        )
    }

    @Test
    fun treatsAChargeDueTodayInTheBillingZoneAsNotOverdue() {
        val actor = user("own-today-actor", "Today Actor")
        val owner = user("own-today-owner", "Today Owner")
        val group = group("Vence hoje", owner)
        membership(group, actor)
        monthlyCharge(group, actor, 500, "2026-08-01", "PENDING", owner, dueDate = "2026-08-01")

        val ownCharges = requireNotNull(
            repository().find(
                actorId = actor,
                now = Instant.parse("2026-08-01T12:00:00Z"),
                today = LocalDate.of(2026, 8, 1),
            ).ownCharges,
        )

        assertEquals(false, ownCharges.groups.single().overdue)
    }

    @Test
    fun keepsShowingOwnChargesToWhoLeftTheGroup() {
        val actor = user("own-left-actor", "Left Actor")
        val neverJoined = user("own-never-actor", "Never Actor")
        val owner = user("own-left-owner", "Left Owner")
        val inactiveGroup = group("Saiu do grupo", owner)
        val noMembershipGroup = group("Sem vinculo", owner)
        membership(inactiveGroup, actor, active = false)
        monthlyCharge(inactiveGroup, actor, 700, "2026-07-01", "PENDING", owner)
        monthlyCharge(noMembershipGroup, neverJoined, 800, "2026-07-01", "PENDING", owner)

        val left = requireNotNull(ownChargesOf(actor))
        val never = requireNotNull(ownChargesOf(neverJoined))

        // A cobrança é o vínculo: sair do grupo (ou nunca ter membership) não apaga a dívida.
        assertEquals(listOf("Saiu do grupo"), left.groups.map { it.groupName })
        assertEquals(700, left.totalCents)
        assertEquals(listOf("Sem vinculo"), never.groups.map { it.groupName })
        assertEquals(800, never.totalCents)
    }

    @Test
    fun ranksTheOldestChargeByCompetenceInTheGameOwnZone() {
        val actor = user("own-zone-actor", "Zone Actor")
        val owner = user("own-zone-owner", "Zone Owner")
        val group = group("Fuso do jogo", owner)
        membership(group, actor)

        // 05:00Z é 31/07 em Los_Angeles (fuso do jogo) e 01/08 tanto em UTC quanto em Sao_Paulo
        // (fuso do grupo): só a conversão pelo zone_id do jogo coloca o jogo antes da mensalidade.
        val played = game(group, "2026-08-01T05:00:00Z", "COMPLETED", capacity = 4, zone = "America/Los_Angeles")
        gameCharge(group, actor, played, 300, "PENDING", owner, dueDate = "2026-08-10")
        // Vencimento mais cedo que o do jogo: se a competência empatar, o desempate por due_date
        // elege a mensalidade e o teste falha — em vez de virar moeda no desempate por id.
        monthlyCharge(group, actor, 600, "2026-08-01", "PENDING", owner, dueDate = "2026-08-05")

        val single = requireNotNull(ownChargesOf(actor)).groups.single()

        assertEquals(2, single.count)
        assertEquals(900, single.totalCents)
        assertEquals(LocalDate.of(2026, 8, 5), single.nextDueDate)
        assertEquals(
            HomeOwnChargeOldest.Game(
                gameId = played,
                startsAt = Instant.parse("2026-08-01T05:00:00Z"),
                zoneId = "America/Los_Angeles",
                dueDate = LocalDate.of(2026, 8, 10),
            ),
            single.oldest,
        )
    }

    @Test
    fun returnsEmptyMemberDataAndNoAdminBlockForUnknownActor() {
        val home = repository().find(
            actorId = UUID.randomUUID(),
            now = Instant.parse("2026-08-01T12:00:00Z"),
            today = LocalDate.of(2026, 8, 1),
        )

        assertTrue(home.member.groups.isEmpty())
        assertNull(home.member.nextGame)
        assertNull(home.member.lastCompletedGame)
        assertNull(home.admin)
        assertNull(home.ownCharges)
    }

    private fun repository() = JdbcHomeRepository(dataSource)

    private fun ownChargesOf(actor: UUID) = repository().find(
        actorId = actor,
        now = Instant.parse("2026-08-01T12:00:00Z"),
        today = LocalDate.of(2026, 8, 1),
    ).ownCharges

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

    private fun game(
        group: UUID,
        startsAt: String,
        status: String,
        capacity: Int,
        zone: String = "America/Sao_Paulo",
    ): UUID {
        val id = UUID.randomUUID()
        val start = Instant.parse(startsAt)
        val local = start.atZone(ZoneId.of(zone))
        val deadline = start.minusSeconds(86_400)
        val localDate = local.toLocalDate()
        val localTime = local.toLocalTime()
        execute(
            "INSERT INTO games (id,group_id,title,local_date,local_time,zone_id,starts_at,duration_minutes,confirmation_deadline," +
                "venue_name,venue_address,capacity,status,created_at,updated_at) " +
                "VALUES ('$id','$group','Home game',DATE '$localDate',TIME '$localTime'," +
                "'$zone',TIMESTAMPTZ '$startsAt',90,TIMESTAMPTZ '$deadline','Home court','Home address'," +
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

    private fun pix(group: UUID, key: String, label: String) {
        execute("UPDATE access_groups SET pix_key = '$key', pix_label = '$label' WHERE id = '$group'")
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
        dueDate: String = billingMonth,
    ) {
        val id = UUID.randomUUID()
        val displayName = memberName(member)
        execute(
            "INSERT INTO group_charges (id,group_id,member_user_id,kind,billing_month,amount_cents,due_date,status," +
                "created_by_user_id,changed_by_user_id,created_at,updated_at,member_display_name) " +
                "VALUES ('$id','$group','$member','MONTHLY',DATE '$billingMonth',$amount,DATE '$dueDate','$status'," +
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
        dueDate: String = "2026-08-01",
    ) {
        val id = UUID.randomUUID()
        val displayName = memberName(member)
        execute(
            "INSERT INTO group_charges (id,group_id,member_user_id,kind,game_id,amount_cents,due_date,status," +
                "created_by_user_id,changed_by_user_id,created_at,updated_at,member_display_name) " +
                "VALUES ('$id','$group','$member','GAME','$game',$amount,DATE '$dueDate','$status'," +
                "'$actor','$actor',now(),now(),'$displayName')",
        )
    }

    private fun memberName(member: UUID): String =
        query("SELECT display_name FROM access_users WHERE id = '$member'") { it.getString(1) }

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
