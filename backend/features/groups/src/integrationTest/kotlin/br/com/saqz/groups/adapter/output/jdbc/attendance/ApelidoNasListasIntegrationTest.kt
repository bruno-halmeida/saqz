package br.com.saqz.groups.adapter.output.jdbc.attendance

import br.com.saqz.groups.adapter.output.jdbc.communication.JdbcGroupCommunicationRepository
import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcChargeTransactionRepository
import br.com.saqz.groups.adapter.output.jdbc.game.JdbcAttendanceHistoryRepository
import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.attendance.AttendanceCommandResult
import br.com.saqz.groups.application.attendance.RespondAttendance
import br.com.saqz.groups.application.communication.CommunicationResult
import br.com.saqz.groups.application.communication.GroupCommunicationService
import br.com.saqz.groups.application.finance.charge.ChargeTransactions
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceSource
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** DEB-136 (MEM-004): nas listas do jogo o membro aparece pelo apelido do grupo, depois o do perfil, depois o nome. */
class ApelidoNasListasIntegrationTest {
    private val source = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    private val jdbc = JdbcClient.create(source)
    private val transaction = JdbcTransactionRunner(source)
    private val attendance = JdbcAttendanceCommandRepository(source)
    private val communication = JdbcGroupCommunicationRepository(source)
    private val service = GroupCommunicationService(transaction, JdbcGroupReadRepository(source), communication)
    private val history = JdbcAttendanceHistoryRepository(source)
    private val respond = RespondAttendance(
        transaction, attendance,
        AttendanceChargeAdapter(ChargeTransactions(transaction, JdbcChargeTransactionRepository(source)) { Instant.now() }),
        { Instant.now() },
    )

    private val owner = user("Dono")
    private val group = UUID.randomUUID()

    init {
        jdbc.sql("""INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at)
            VALUES (:id, :owner, :key, 'Vôlei de quinta', 'America/Sao_Paulo', now(), now())""")
            .param("id", group).param("owner", owner).param("key", UUID.randomUUID()).update()
        membership(owner, "ADMIN")
    }

    @BeforeEach fun reset() {
        jdbc.sql("TRUNCATE games CASCADE").update()
        jdbc.sql("TRUNCATE group_messages CASCADE").update()
        jdbc.sql("UPDATE access_groups SET game_system = NULL, play_style = NULL, mensalista_priority = false WHERE id = :g")
            .param("g", group).update()
    }

    @Test fun `o elenco do jogo usa o apelido do grupo, depois o do perfil, e ordena pelo nome novo`() {
        val game = game(capacity = 10)
        val bruno = member("Bruno Henrique Almeida", groupNick = "Zé")
        val ana = member("Ana Souza", profileNick = "Aninha")
        val carla = member("Carla Dias")
        val dani = member("Daniela Lima", profileNick = "Dani")
        listOf(bruno, ana, carla, dani).forEach { confirm(it, game) }

        val roster = attendance.roster(owner, group, game)!!
        assertEquals(listOf("Aninha", "Carla Dias", "Dani", "Zé"), roster.confirmed.map { it.displayName })
    }

    @Test fun `apelido trocado depois da resposta aparece na lista e no lembrete`() {
        val game = game(capacity = 10)
        val bruno = member("Bruno Henrique Almeida")
        confirm(bruno, game)
        setGroupNick(bruno, "Bruno")

        assertEquals(listOf("Bruno"), attendance.roster(owner, group, game)!!.confirmed.map { it.displayName })
        assertEquals(listOf("Bruno"), communication.reminderRoster(group, game).confirmed)
    }

    @Test fun `convidado mantem o nome digitado e o anfitriao aparece pelo apelido`() {
        val game = game(capacity = 10)
        val host = member("Yara Prado", groupNick = "Yayá")
        confirm(host, game)
        guest(game, host, "Fulano Convidado")

        val confirmed = attendance.roster(owner, group, game)!!.confirmed
        assertEquals(listOf("Fulano Convidado", "Yayá"), confirmed.map { it.displayName })
        assertEquals("Yayá", confirmed.first { it.guestSeq == 1 }.hostDisplayName)
        assertEquals(listOf("Fulano Convidado", "Yayá"), communication.reminderRoster(group, game).confirmed)
    }

    @Test fun `o lembrete 6-0 numera pelos apelidos em ordem alfabetica`() {
        jdbc.sql("UPDATE access_groups SET play_style = 'SIX_ZERO' WHERE id = :g").param("g", group).update()
        val game = game(capacity = 3)
        confirm(member("Zuleica Prado", groupNick = "Branca"), game)
        confirm(member("Abel Souza", profileNick = "Tico"), game)
        decline(member("Carla Dias", groupNick = "Cacá"), game)

        val body = service.remind(owner, group, game, UUID.randomUUID()).success().body
        assertTrue(body.endsWith("✅ Confirmados:\n1. Branca\n2. Tico\n3.\n\n❌ Fora:\nCacá"), body)
        assertEquals(listOf("Cacá"), attendance.roster(owner, group, game)!!.declined.map { it.displayName })
    }

    @Test fun `o historico mostra o apelido atual de quem respondeu e de quem fez`() {
        val game = game(capacity = 10)
        val ana = member("Ana Souza")
        confirm(ana, game)
        assertIs<AttendanceCommandResult.Success>(
            respond.execute(owner, group, game, memberId = ana, intent = AttendanceIntent.DECLINE,
                source = AttendanceSource.ORGANIZER, reason = "Teste"),
        )
        setGroupNick(ana, "Aninha")
        setGroupNick(owner, "Chefe")

        val rows = history.page(group, game, null, 50)
        assertTrue(rows.isNotEmpty())
        assertTrue(rows.all { it.memberName == "Aninha" }, rows.toString())
        assertEquals(setOf("Aninha", "Chefe"), rows.map { it.actorName }.toSet())
    }

    @Test fun `a resposta grava a copia pela regra nova e a cobranca mantem o nome copiado`() {
        val game = game(capacity = 10, fee = 2500)
        val bruno = member("Bruno Henrique Almeida", groupNick = "Bruno", profileNick = "Bh")
        confirm(bruno, game)
        setGroupNick(bruno, "Brunão")

        assertEquals("Bruno", single("SELECT member_display_name FROM game_attendance WHERE member_user_id = :u", bruno))
        assertEquals("Bh", single("SELECT member_display_name FROM group_charges WHERE member_user_id = :u", bruno))
        assertEquals(listOf("Brunão"), attendance.roster(owner, group, game)!!.confirmed.map { it.displayName })
    }

    private fun confirm(member: UUID, game: UUID) =
        assertIs<AttendanceCommandResult.Success>(respond.execute(member, group, game, intent = AttendanceIntent.CONFIRM))

    private fun decline(member: UUID, game: UUID) =
        assertIs<AttendanceCommandResult.Success>(respond.execute(member, group, game, intent = AttendanceIntent.DECLINE))

    private fun guest(game: UUID, host: UUID, name: String) {
        jdbc.sql("""INSERT INTO game_attendance(game_id, group_id, member_user_id, status, responded_at, updated_at, version,
                member_display_name, guest_seq)
            VALUES (:game, :g, :u, 'CONFIRMED', now(), now(), 1, :name, 1)""")
            .param("game", game).param("g", group).param("u", host).param("name", name).update()
    }

    private fun single(sql: String, user: UUID): String = jdbc.sql(sql).param("u", user).query(String::class.java).single()

    private fun setGroupNick(user: UUID, nick: String) {
        jdbc.sql("UPDATE group_memberships SET nickname = :n WHERE group_id = :g AND user_id = :u")
            .param("n", nick).param("g", group).param("u", user).update()
    }

    private fun game(capacity: Int, fee: Int? = null): UUID {
        val id = UUID.randomUUID()
        jdbc.sql("""
            INSERT INTO games(id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes,
                confirmation_deadline, venue_name, venue_address, capacity, game_fee_cents, status, published_at, created_at, updated_at)
            VALUES (:id, :g, 'Vôlei de quinta', current_date + 1, '19:00', 'America/Sao_Paulo', now() + interval '5 hours', 90,
                now() + interval '2 hours', 'Arena', 'Rua 100', :capacity, :fee, 'PUBLISHED', now(), now(), now())
        """).param("id", id).param("g", group).param("capacity", capacity).param("fee", fee, java.sql.Types.INTEGER).update()
        return id
    }

    private fun member(name: String, groupNick: String? = null, profileNick: String? = null): UUID = user(name, profileNick).also {
        membership(it, "ATHLETE")
        if (groupNick != null) setGroupNick(it, groupNick)
    }

    private fun membership(user: UUID, role: String) {
        jdbc.sql("""INSERT INTO group_memberships(group_id, user_id, role, membership_type, active, created_at, updated_at)
            VALUES (:g, :u, :role, 'AVULSO', true, now(), now())""")
            .param("g", group).param("u", user).param("role", role, java.sql.Types.OTHER).update()
    }

    private fun user(name: String, nick: String? = null): UUID = UUID.randomUUID().also { id ->
        jdbc.sql("""INSERT INTO access_users(id, firebase_subject, email_verified, display_name, nickname, phone, created_at, updated_at)
            VALUES (:id, :subject, true, :name, :nick, '+5511999999999', now(), now())""")
            .param("id", id).param("subject", id.toString()).param("name", name).param("nick", nick).update()
    }

    private fun <T> CommunicationResult<T>.success(): T = assertIs<CommunicationResult.Success<T>>(this).value
}
