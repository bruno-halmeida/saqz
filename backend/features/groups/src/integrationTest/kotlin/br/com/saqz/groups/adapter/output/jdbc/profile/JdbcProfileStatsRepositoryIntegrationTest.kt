package br.com.saqz.groups.adapter.output.jdbc.profile

import br.com.saqz.groups.application.profile.GetProfileStats
import br.com.saqz.groups.application.profile.ProfileStats
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.Connection
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcProfileStatsRepositoryIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var stats: GetProfileStats

    @BeforeAll
    fun startDatabase() {
        dataSource = TestPostgres.migrated(*allGroupFeatureMigrationLocations()).dataSource
        stats = GetProfileStats(JdbcProfileStatsRepository(dataSource)) { NOW }
    }

    @BeforeEach
    fun clearData() {
        execute("TRUNCATE access_users CASCADE")
    }

    @Test
    fun `excludes final waitlisted games and includes promoted games`() {
        val fixture = fixture()
        val confirmed = game(fixture.group, "2026-07-01 22:00:00Z")
        val waitlisted = game(fixture.group, "2026-07-08 22:00:00Z")
        val promoted = game(fixture.group, "2026-07-15 22:00:00Z")
        val declined = game(fixture.group, "2026-07-22 22:00:00Z")
        attendance(fixture, confirmed, "CONFIRMED")
        attendance(fixture, waitlisted, "WAITLISTED", sequence = 1)
        attendance(fixture, promoted, "WAITLISTED", sequence = 1)
        execute("UPDATE game_attendance SET status='CONFIRMED', waitlist_sequence=NULL WHERE game_id='$promoted'")
        attendance(fixture, declined, "DECLINED")

        assertEquals(ProfileStats(2, 66, 1), stats.execute(fixture.member))
    }

    @Test
    fun `does not count games before membership creation`() {
        val fixture = fixture(joinedAt = "2026-07-15 00:00:00Z")
        val beforeJoining = game(fixture.group, "2026-07-14 22:00:00Z")
        val afterJoining = game(fixture.group, "2026-07-20 22:00:00Z")
        attendance(fixture, beforeJoining, "DECLINED")
        attendance(fixture, afterJoining, "CONFIRMED")

        assertEquals(ProfileStats(1, 100, 1), stats.execute(fixture.member))
    }

    @Test
    fun `does not count future games`() {
        val fixture = fixture()
        val future = game(fixture.group, "2026-08-02 22:00:00Z")
        attendance(fixture, future, "CONFIRMED")

        assertEquals(ProfileStats(0, null, 1), stats.execute(fixture.member))
    }

    @Test
    fun `does not count past draft games`() {
        val fixture = fixture()
        game(fixture.group, "2026-07-20 22:00:00Z", status = "DRAFT")

        assertEquals(ProfileStats(0, null, 1), stats.execute(fixture.member))
    }

    @Test
    fun `does not count past cancelled games`() {
        val fixture = fixture()
        val cancelled = game(fixture.group, "2026-07-20 22:00:00Z", status = "CANCELLED")
        attendance(fixture, cancelled, "CONFIRMED")

        assertEquals(ProfileStats(0, null, 1), stats.execute(fixture.member))
    }

    @Test
    fun `returns null rate when every past game is waitlisted`() {
        val fixture = fixture()
        val waitlisted = game(fixture.group, "2026-07-20 22:00:00Z")
        attendance(fixture, waitlisted, "WAITLISTED", sequence = 1)

        assertEquals(ProfileStats(0, null, 1), stats.execute(fixture.member))
    }

    @Test
    fun `counts only active group participations`() {
        val fixture = fixture()
        val inactiveGroup = group(fixture.owner, "Inactive Group")
        membership(inactiveGroup, fixture.member, "2026-01-01 00:00:00Z", active = false)

        assertEquals(1, stats.execute(fixture.member).groups)
    }

    @Test
    fun `excludes deleted groups and their games from profile stats`() {
        val fixture = fixture()
        val activeGame = game(fixture.group, "2026-07-20 22:00:00Z")
        attendance(fixture, activeGame, "CONFIRMED")

        val deletedGroup = group(fixture.owner, "Deleted Group")
        membership(deletedGroup, fixture.member, "2026-01-01 00:00:00Z")
        val deletedGame = game(deletedGroup, "2026-07-20 22:00:00Z")
        attendance(fixture, deletedGame, "CONFIRMED", group = deletedGroup)
        execute("UPDATE access_groups SET deleted_at=now() WHERE id='$deletedGroup'")

        assertEquals(ProfileStats(1, 100, 1), stats.execute(fixture.member))
    }

    private fun fixture(joinedAt: String = "2026-01-01 00:00:00Z"): Fixture {
        val owner = user("owner")
        val group = group(owner, "Stats Group")
        val member = user("member")
        membership(group, member, joinedAt)
        return Fixture(owner, group, member)
    }

    private fun user(subject: String): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id,firebase_subject,email_verified,display_name,created_at,updated_at) " +
                "VALUES ('$id','$subject-${UUID.randomUUID()}',true,'Player',now(),now())",
        )
        return id
    }

    private fun group(owner: UUID, name: String): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_groups (id,owner_user_id,creation_key,name,time_zone,created_at,updated_at) " +
                "VALUES ('$id','$owner','${UUID.randomUUID()}','$name','America/Sao_Paulo',now(),now())",
        )
        return id
    }

    private fun membership(group: UUID, member: UUID, joinedAt: String, active: Boolean = true) {
        execute(
            "INSERT INTO group_memberships (group_id,user_id,role,created_at,updated_at,membership_type,active) " +
                "VALUES ('$group','$member','ATHLETE',TIMESTAMPTZ '$joinedAt',TIMESTAMPTZ '$joinedAt','MENSALISTA',$active)",
        )
    }

    private fun game(group: UUID, startsAt: String, status: String = "PUBLISHED"): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO games (id,group_id,title,local_date,local_time,zone_id,starts_at,duration_minutes, " +
                "confirmation_deadline,venue_name,venue_address,capacity,status,created_at,updated_at) " +
                "VALUES ('$id','$group','Treino',DATE '2026-08-01',TIME '19:30','America/Sao_Paulo', " +
                "TIMESTAMPTZ '$startsAt',90,TIMESTAMPTZ '2026-07-01 22:00:00Z','Arena','Rua Central 100',24,'$status',now(),now())",
        )
        return id
    }

    private fun attendance(
        fixture: Fixture,
        game: UUID,
        status: String,
        sequence: Long? = null,
        group: UUID = fixture.group,
    ) {
        val sequenceSql = sequence?.toString() ?: "NULL"
        execute(
            "INSERT INTO game_attendance (game_id,group_id,member_user_id,status,waitlist_sequence,responded_at,updated_at,member_display_name) " +
                "VALUES ('$game','$group','${fixture.member}','$status',$sequenceSql,now(),now(),'Player')",
        )
    }

    private fun execute(sql: String) {
        connection().use { connection -> connection.createStatement().use { it.execute(sql) } }
    }

    private fun connection(): Connection = dataSource.connection

    private data class Fixture(val owner: UUID, val group: UUID, val member: UUID)

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-01T10:00:00Z")
    }
}
