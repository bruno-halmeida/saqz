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

/** DEB-040: a migration que remove a confirmação automática, sobre um banco que ainda tem gatilhos e flags ligados. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RemoveAutoConfirmationMigrationIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeEach
    fun resetDatabase() {
        dataSource = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    }

    @Test
    fun `remove gatilhos e funcoes de aviso, desliga os grupos e mantem colunas, canal e confirmacoes antigas`() {
        flyway().clean()
        flyway("130").migrate()
        assertEquals(2, count("SELECT count(*) FROM pg_trigger WHERE tgname IN ('group_auto_confirmation_on', 'member_auto_confirmation')"))
        assertEquals(3, count(FUNCTIONS))
        val on = group("on", enabled = true)
        val off = group("off", enabled = false)
        val member = member(on, memberEnabled = true)
        val game = game(on)
        // Confirmação automática feita antes da migration: continua como está.
        execute("INSERT INTO game_attendance (game_id,group_id,member_user_id,status,responded_at,updated_at,member_display_name) VALUES ('$game','$on','$member','CONFIRMED',now(),now(),'Mensalista')")
        execute("INSERT INTO attendance_events (id,game_id,group_id,member_user_id,actor_user_id,source,new_status,occurred_at) VALUES ('${UUID.randomUUID()}','$game','$on','$member','$member','SYSTEM','CONFIRMED',now())")

        flyway().migrate()

        assertEquals(0, count("SELECT count(*) FROM pg_trigger WHERE tgname IN ('group_auto_confirmation_on', 'member_auto_confirmation')"))
        assertEquals(0, count(FUNCTIONS))
        assertEquals(0, count("SELECT count(*) FROM access_groups WHERE auto_confirm_enabled"))
        assertEquals(1, count("SELECT count(*) FROM access_groups WHERE id = '$off' AND NOT auto_confirm_enabled"))
        assertEquals(2, count("SELECT count(*) FROM information_schema.columns WHERE column_name = 'auto_confirm_enabled' AND table_name IN ('access_groups', 'group_memberships')"))
        assertEquals(1, count("SELECT count(*) FROM pg_enum e JOIN pg_type t ON t.oid = e.enumtypid WHERE t.typname = 'group_message_channel' AND e.enumlabel = 'AUTO_CONFIRMATION'"))
        assertEquals(1, count("SELECT count(*) FROM game_attendance WHERE game_id = '$game' AND status = 'CONFIRMED'"))
        assertEquals(1, count("SELECT count(*) FROM attendance_events WHERE game_id = '$game' AND source = 'SYSTEM'"))
    }

    @Test
    fun `depois da migration ligar a funcao no grupo nao gera aviso`() {
        val group = group("depois", enabled = false)
        member(group, memberEnabled = true)

        execute("UPDATE access_groups SET auto_confirm_enabled = true WHERE id = '$group'")

        assertEquals(0, count("SELECT count(*) FROM group_messages WHERE channel = 'AUTO_CONFIRMATION'"))
    }

    private fun group(subject: String, enabled: Boolean): UUID {
        val owner = user("$subject-owner")
        val group = UUID.randomUUID()
        execute("INSERT INTO access_groups (id,owner_user_id,creation_key,name,time_zone,profile_status,modality,composition,auto_confirm_enabled,created_at,updated_at) VALUES ('$group','$owner','${UUID.randomUUID()}','Group','America/Sao_Paulo','COMPLETE','COURT_VOLLEYBALL','MIXED',$enabled,now(),now())")
        return group
    }

    private fun member(group: UUID, memberEnabled: Boolean): UUID {
        val id = user("member")
        execute("INSERT INTO group_memberships (group_id,user_id,role,created_at,updated_at,membership_type,auto_confirm_enabled) VALUES ('$group','$id','ATHLETE',now(),now(),'MENSALISTA',$memberEnabled)")
        return id
    }

    private fun game(group: UUID): UUID {
        val game = UUID.randomUUID()
        execute("INSERT INTO games (id,group_id,title,local_date,local_time,zone_id,starts_at,duration_minutes,confirmation_deadline,venue_name,venue_address,capacity,status,created_at,updated_at) VALUES ('$game','$group','Treino',DATE '2026-08-12',TIME '19:30','America/Sao_Paulo',TIMESTAMPTZ '2026-08-12 22:30Z',90,TIMESTAMPTZ '2026-08-11 22:30Z','Arena','Rua Central 100',4,'PUBLISHED',now(),now())")
        return game
    }

    private fun user(subject: String): UUID = UUID.randomUUID().also { id ->
        execute("INSERT INTO access_users (id,firebase_subject,email_verified,display_name,created_at,updated_at) VALUES ('$id','$subject-$id',true,'$subject',now(),now())")
    }

    private fun flyway(target: String? = null): Flyway {
        val configuration = Flyway.configure()
            .dataSource(dataSource)
            .locations(*allGroupFeatureMigrationLocations())
            .cleanDisabled(false)
        if (target != null) configuration.target(target)
        return configuration.load()
    }

    private fun execute(sql: String) { connection().use { it.createStatement().use { statement -> statement.execute(sql) } } }
    private fun count(sql: String): Int = connection().use { connection ->
        connection.createStatement().use { statement -> statement.executeQuery(sql).use { result -> result.next(); result.getInt(1) } }
    }
    private fun connection(): Connection = dataSource.connection

    private companion object {
        const val FUNCTIONS = "SELECT count(*) FROM pg_proc WHERE proname IN ('notify_auto_confirmation', 'notify_group_auto_confirmation_on', 'notify_member_auto_confirmation')"
    }
}
