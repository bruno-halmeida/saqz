package br.com.saqz.groups.adapter.output.jdbc.attendance

import br.com.saqz.groups.adapter.output.jdbc.athlete.JdbcAthleteRepository
import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcChargeTransactionRepository
import br.com.saqz.groups.adapter.output.jdbc.game.JdbcGameOccurrenceRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.attendance.*
import br.com.saqz.groups.application.finance.charge.ChargeTransactions
import br.com.saqz.groups.application.game.ListGames
import br.com.saqz.groups.domain.AthletePosition
import br.com.saqz.groups.domain.attendance.*
import br.com.saqz.groups.testing.*
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.AbstractDataSource
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.lang.reflect.Proxy
import java.sql.Connection
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcAttendanceCommandRepositoryIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeEach fun reset() { dataSource = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource }

    @Test fun `self confirmation below capacity persists current response and audit`() { val f = fixture(); val saved = success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM)); assertEquals(AttendanceStatus.CONFIRMED, saved.status); assertEquals(1, count("game_attendance")); assertEquals(1, count("attendance_events")) }
    @Test fun `confirmation at capacity receives first monotonic waitlist position`() { val f = fixture(capacity = 2); repeat(2) { attendance(f, member(f.group, "confirmed-$it"), "CONFIRMED") }; val saved = success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM)); assertEquals(AttendanceStatus.WAITLISTED, saved.status); assertEquals(1, saved.waitlistSequence) }
    @Test fun `successive full confirmations receive unique fifo positions`() { val f = fixture(capacity = 2); repeat(2) { attendance(f, member(f.group, "confirmed-$it"), "CONFIRMED") }; val second = member(f.group, "second"); val firstSaved = success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM)); val secondSaved = success(f.service.execute(second, f.group, f.game, intent = AttendanceIntent.CONFIRM)); assertEquals(listOf(1L, 2L), listOf(firstSaved.waitlistSequence, secondSaved.waitlistSequence)) }
    @Test fun `confirmation retry returns same row without another audit`() { val f = fixture(); val first = success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM)); val retry = success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM)); assertEquals(first, retry); assertEquals(1, count("game_attendance")); assertEquals(1, count("attendance_events")) }
    @Test fun `self response replay with the same request id creates one avulso charge`() {
        val f = fixture()
        execute("UPDATE group_memberships SET membership_type='AVULSO' WHERE group_id='${f.group}' AND user_id='${f.member}'")
        execute("UPDATE access_groups SET mensalista_priority=false WHERE id='${f.group}'")
        execute("UPDATE games SET game_fee_cents=2500 WHERE id='${f.game}'")
        val requestId = UUID.randomUUID()

        val first = f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM, requestId = requestId)
        val replay = f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM, requestId = requestId)

        assertEquals(first, replay)
        assertEquals(1, count("attendance_events"))
        assertEquals(1, int("SELECT count(*) FROM group_charges WHERE member_user_id='${f.member}'"))
    }
    @Test fun `vou, nao vou, vou with the same two request ids ends confirmed with three events`() {
        val f = fixture()
        val going = UUID.randomUUID()
        val notGoing = UUID.randomUUID()

        f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM, requestId = going)
        f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.DECLINE, requestId = notGoing)
        val last = success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM, requestId = going))
        val retry = success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM, requestId = going))

        assertEquals(AttendanceStatus.CONFIRMED, last.status)
        assertEquals(last, retry)
        assertEquals("CONFIRMED", status(f.member))
        assertEquals(3, count("attendance_events"))
    }

    @Test fun `vou after nao vou on a full game with the same request id goes to the waitlist`() {
        val f = fixture(capacity = 2)
        repeat(2) { attendance(f, member(f.group, "confirmed-$it"), "CONFIRMED") }
        val going = UUID.randomUUID()
        val notGoing = UUID.randomUUID()

        f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM, requestId = going)
        f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.DECLINE, requestId = notGoing)
        val last = success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM, requestId = going))

        assertEquals(AttendanceStatus.WAITLISTED, last.status)
        assertEquals(3, count("attendance_events"))
    }

    @Test fun `two identical simultaneous responses record a single event`() {
        val f = fixture()
        val requestId = UUID.randomUUID()
        val barrier = CyclicBarrier(3)
        val executor = Executors.newFixedThreadPool(2)
        val results = try {
            val futures = (1..2).map {
                executor.submit<AttendanceCommandResult> {
                    barrier.await()
                    f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM, requestId = requestId)
                }
            }
            barrier.await()
            futures.map { it.get() }
        } finally { executor.shutdownNow() }

        assertEquals(1, results.map { success(it) }.toSet().size)
        assertEquals(1, count("attendance_events"))
        assertEquals(1, count("game_attendance"))
    }

    @Test fun `attendance row stores member nickname snapshot at creation`() { val f = fixture(); execute("UPDATE access_users SET nickname='Apelido' WHERE id='${f.member}'"); success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM)); assertEquals("Apelido", string("SELECT member_display_name FROM game_attendance WHERE member_user_id='${f.member}'")) }
    @Test fun `a status update never rewrites the attendance row's member display name snapshot`() { val f = fixture(); success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM)); execute("UPDATE access_users SET display_name='Renamed' WHERE id='${f.member}'"); success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.DECLINE)); success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM)); assertEquals("User", string("SELECT member_display_name FROM game_attendance WHERE member_user_id='${f.member}'")) }
    @Test fun `renaming a member changes only future attendance snapshots`() { val f = fixture(); success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM)); execute("UPDATE access_users SET display_name='Renamed' WHERE id='${f.member}'"); assertEquals("User", string("SELECT member_display_name FROM game_attendance WHERE member_user_id='${f.member}'")); val secondGame = UUID.randomUUID(); execute("INSERT INTO games (id,group_id,title,local_date,local_time,zone_id,starts_at,duration_minutes,confirmation_deadline,venue_name,venue_address,capacity,game_fee_cents,status,created_at,updated_at) VALUES ('$secondGame','${f.group}','Treino 2',(CURRENT_DATE + 3),TIME '19:30','America/Sao_Paulo',now() + interval '3 days',90,now() + interval '2 days','Arena','Rua Central 100',2,2500,'PUBLISHED',now(),now())"); success(f.service.execute(f.member, f.group, secondGame, intent = AttendanceIntent.CONFIRM)); assertEquals("Renamed", string("SELECT member_display_name FROM game_attendance WHERE game_id='$secondGame'")) }
    @Test fun `organizer override records the target member display name snapshot`() { val f = fixture(); val target = member(f.group, "target", name = "Alvo"); success(f.service.execute(f.owner, f.group, f.game, target, AttendanceIntent.CONFIRM, AttendanceSource.ORGANIZER, "Chegou após o prazo")); assertEquals("Alvo", snapshotName(target)) }
    @Test fun `withdrawal promotion keeps the snapshot taken when the member joined the queue`() { val f = fixture(capacity = 2); attendance(f, member(f.group, "occupied"), "CONFIRMED"); success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM)); val waiting = member(f.group, "waiting", name = "Espera"); assertEquals(AttendanceStatus.WAITLISTED, success(f.service.execute(waiting, f.group, f.game, intent = AttendanceIntent.CONFIRM)).status); execute("UPDATE access_users SET display_name='Renomeado' WHERE id='$waiting'"); f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.DECLINE); assertEquals("CONFIRMED", status(waiting)); assertEquals("Espera", snapshotName(waiting)) }
    @Test fun `queue position is the rank in the promotion order, not the stored sequence`() {
        val f = fullFixture()
        val first = member(f.group, "first", "AVULSO", name = "Primeiro"); waitlist(f, first, 1, "Primeiro")
        val second = member(f.group, "second", "AVULSO", name = "Segundo"); waitlist(f, second, 2, "Segundo")
        val repository = JdbcAttendanceCommandRepository(dataSource)
        assertEquals(listOf(1L, 2L), rosterOf(f, f.member)!!.waitlisted.map { it.waitlistPosition })
        assertEquals(2L, repository.find(second, f.group, f.game)!!.own!!.waitlistPosition)
        assertEquals(2L, repository.ownByGame(second, f.group).getValue(f.game).waitlistPosition)
        execute("DELETE FROM game_attendance WHERE member_user_id='$first'")
        assertEquals(listOf(1L), rosterOf(f, f.member)!!.waitlisted.map { it.waitlistPosition })
        assertEquals(1L, repository.find(second, f.group, f.game)!!.own!!.waitlistPosition)
        assertEquals(1L, repository.ownByGame(second, f.group).getValue(f.game).waitlistPosition)
        assertEquals(2L, repository.find(second, f.group, f.game)!!.own!!.waitlistSequence)
    }
    @Test fun `queue position puts the mensalista ahead while the deadline is open`() {
        val f = fullFixture()
        val avulso = member(f.group, "avulso", "AVULSO", name = "Avulso"); waitlist(f, avulso, 1, "Avulso")
        val mensalista = member(f.group, "mensalista", "MENSALISTA", name = "Mensalista"); waitlist(f, mensalista, 2, "Mensalista")
        val repository = JdbcAttendanceCommandRepository(dataSource)
        assertEquals(listOf("Mensalista", "Avulso"), rosterOf(f, f.member)!!.waitlisted.map { it.displayName })
        assertEquals(listOf(1L, 2L), rosterOf(f, f.member)!!.waitlisted.map { it.waitlistPosition })
        assertEquals(1L, repository.find(mensalista, f.group, f.game)!!.own!!.waitlistPosition)
        assertEquals(2L, repository.find(avulso, f.group, f.game)!!.own!!.waitlistPosition)
    }
    @Test fun `capacity promotion keeps the snapshot taken when the member joined the queue`() { val f = fullFixture(capacity = 4); val waiting = member(f.group, "waiting", name = "Espera"); assertEquals(AttendanceStatus.WAITLISTED, success(f.service.execute(waiting, f.group, f.game, intent = AttendanceIntent.CONFIRM)).status); execute("UPDATE access_users SET display_name='Renomeado' WHERE id='$waiting'"); assertIs<CapacityCommandResult.Success>(capacity(f).execute(f.owner, f.group, f.game, 1, 5)); assertEquals("CONFIRMED", status(waiting)); assertEquals("Espera", snapshotName(waiting)) }
    @Test fun `roster lists confirmed by name and waitlist in arrival order with snapshot names`() { val f = fixture(capacity = 2); attendance(f, member(f.group, "confirmed-b"), "CONFIRMED", "bruno"); attendance(f, member(f.group, "confirmed-a"), "CONFIRMED", "Ana"); waitlist(f, member(f.group, "waiting-1"), 1, "Zeca"); waitlist(f, member(f.group, "waiting-2"), 2, "Alice"); val roster = requireNotNull(rosterOf(f, f.member)); assertEquals(listOf("Ana", "bruno"), roster.confirmed.map { it.displayName }); assertEquals(listOf("Zeca", "Alice"), roster.waitlisted.map { it.displayName }); assertEquals(listOf(1L, 2L), roster.waitlisted.map { it.waitlistPosition }) }
    @Test fun `roster omits declined members and keeps confirmed positions empty`() { val f = fixture(); attendance(f, f.member, "CONFIRMED", "Ana"); attendance(f, member(f.group, "declined"), "DECLINED", "Zeca"); val roster = requireNotNull(rosterOf(f, f.member)); assertEquals(listOf("Ana"), roster.confirmed.map { it.displayName }); assertEquals(listOf(null), roster.confirmed.map { it.waitlistPosition }); assertEquals(emptyList(), roster.waitlisted) }
    @Test fun `roster of a game without responses is empty for a member`() { val f = fixture(); val roster = requireNotNull(rosterOf(f, f.member)); assertEquals(emptyList(), roster.confirmed); assertEquals(emptyList(), roster.waitlisted) }
    @Test fun `roster stays visible to the owner and hidden from nonmembers`() { val f = fixture(); attendance(f, f.member, "CONFIRMED", "Ana"); assertEquals(listOf("Ana"), requireNotNull(rosterOf(f, f.owner)).confirmed.map { it.displayName }); assertNull(rosterOf(f, user("outsider"))); assertNull(rosterOf(fixture("other"), f.member)) }
    @Test fun `roster names survive a member rename`() { val f = fixture(); success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM)); execute("UPDATE access_users SET display_name='Renomeado' WHERE id='${f.member}'"); assertEquals(listOf("User"), requireNotNull(rosterOf(f, f.member)).confirmed.map { it.displayName }) }
    @Test fun `detail exposes declined and pending counts`() {
        val f = fixture()
        attendance(f, member(f.group, "declined"), "DECLINED")
        waitlist(f, member(f.group, "waitlisted"), 1)

        val detail = requireNotNull(JdbcAttendanceCommandRepository(dataSource).find(f.member, f.group, f.game))

        assertEquals(1, detail.declinedCount)
        assertEquals(1, detail.pendingCount)
    }

    @Test fun `detail counts the owner and admin memberships as pending`() {
        val f = fixture()
        // O dono ganha a associação que a criação do grupo grava: papel ADMIN, mas atleta como todo mundo.
        execute("INSERT INTO group_memberships (group_id,user_id,role,created_at,updated_at) VALUES ('${f.group}','${f.owner}','ADMIN',now(),now())")

        val detail = requireNotNull(JdbcAttendanceCommandRepository(dataSource).find(f.member, f.group, f.game))

        assertEquals(2, detail.pendingCount)
    }

    // --- VUL-226: minha presença na listagem de jogos ---
    @Test fun `own by game returns the actors status waitlist position and version for each answered game`() {
        val f = fixture()
        val declined = f.copy(game = extraGame(f, 3))
        val waitlisted = f.copy(game = extraGame(f, 4))
        attendance(f, f.member, "CONFIRMED")
        attendance(declined, f.member, "DECLINED")
        execute("UPDATE game_attendance SET version=5 WHERE game_id='${declined.game}' AND member_user_id='${f.member}'")
        waitlist(waitlisted, member(f.group, "ahead"), 1)
        waitlist(waitlisted, f.member, 2)

        val own = JdbcAttendanceCommandRepository(dataSource).ownByGame(f.member, f.group)

        assertEquals(setOf(f.game, declined.game, waitlisted.game), own.keys)
        assertEquals(AttendanceStatus.CONFIRMED, own.getValue(f.game).status)
        assertNull(own.getValue(f.game).waitlistSequence)
        assertEquals(1L, own.getValue(f.game).version)
        assertEquals(AttendanceStatus.DECLINED, own.getValue(declined.game).status)
        assertNull(own.getValue(declined.game).waitlistSequence)
        assertEquals(5L, own.getValue(declined.game).version)
        assertEquals(AttendanceStatus.WAITLISTED, own.getValue(waitlisted.game).status)
        assertEquals(2L, own.getValue(waitlisted.game).waitlistSequence)
        assertEquals(listOf(f.member), own.values.map { it.memberId }.distinct())
        assertEquals(listOf(f.group), own.values.map { it.groupId }.distinct())
    }

    @Test fun `own by game leaves out the games the actor has not answered`() {
        val f = fixture()
        val unanswered = extraGame(f, 3)
        attendance(f, f.member, "CONFIRMED")

        val own = JdbcAttendanceCommandRepository(dataSource).ownByGame(f.member, f.group)

        assertEquals(setOf(f.game), own.keys)
        assertNull(own[unanswered])
    }

    @Test fun `own by game never returns another members answer`() {
        val f = fixture()
        attendance(f, member(f.group, "other"), "CONFIRMED")
        waitlist(f, member(f.group, "queued"), 1)

        assertEquals(emptyMap(), JdbcAttendanceCommandRepository(dataSource).ownByGame(f.member, f.group))
    }

    @Test fun `own by game is scoped to the requested group`() {
        val f = fixture()
        val other = fixture("other")
        attendance(other, f.member, "CONFIRMED")
        val repository = JdbcAttendanceCommandRepository(dataSource)

        assertEquals(emptyMap(), repository.ownByGame(f.member, f.group))
        assertEquals(setOf(other.game), repository.ownByGame(f.member, other.group).keys)
    }

    @Test fun `own by game is empty once the group is deleted`() {
        val f = fixture()
        attendance(f, f.member, "CONFIRMED")
        execute("UPDATE access_groups SET deleted_at=now() WHERE id='${f.group}'")

        assertEquals(emptyMap(), JdbcAttendanceCommandRepository(dataSource).ownByGame(f.member, f.group))
    }

    @Test fun `own by game runs a single statement no matter how many games the group has`() {
        val f = fixture()
        attendance(f, f.member, "CONFIRMED")
        val counting = CountingDataSource(dataSource)
        val repository = JdbcAttendanceCommandRepository(counting)

        val withOneGame = repository.ownByGame(f.member, f.group)
        assertEquals(1, counting.preparedStatements.get())
        (3..7).forEach { attendance(f.copy(game = extraGame(f, it)), f.member, "CONFIRMED") }
        val withSixGames = repository.ownByGame(f.member, f.group)

        assertEquals(1, withOneGame.size)
        assertEquals(6, withSixGames.size)
        assertEquals(2, counting.preparedStatements.get())
    }

    @Test fun `listing games with own attendance costs the same statements for one game and for six`() {
        val f = fixture()
        attendance(f, f.member, "CONFIRMED")
        val counting = CountingDataSource(dataSource)
        val attendanceRepository = JdbcAttendanceCommandRepository(counting)
        val listGames = ListGames(JdbcGameOccurrenceRepository(counting), attendanceRepository)
        fun cost(): Int {
            val before = counting.preparedStatements.get()
            listGames.execute(f.member, f.group)
            attendanceRepository.ownByGame(f.member, f.group)
            return counting.preparedStatements.get() - before
        }

        val withOneGame = cost()
        (3..7).forEach { attendance(f.copy(game = extraGame(f, it)), f.member, "CONFIRMED") }

        // role + list + counts + ownByGame
        assertEquals(4, withOneGame)
        assertEquals(withOneGame, cost())
    }

    // --- VUL-152: ordenação da reserva por faixa + FIFO e colapso pós-prazo ---
    @Test fun `roster with priority on lists mensalistas before avulsos in fifo within each tier`() {
        val f = fixture(capacity = 2)
        // Mensalista com seq 2 entra na faixa alta; avulso com seq 1 fica abaixo mesmo chegando antes.
        val mensalistaLate = member(f.group, "mensalista-late")
        val mensalistaEarly = member(f.group, "mensalista-early")
        val avulsoEarly = member(f.group, "avulso-early", "AVULSO")
        waitlist(f, avulsoEarly, 1, "Avulso1")
        waitlist(f, mensalistaLate, 2, "Mensalista2")
        waitlist(f, mensalistaEarly, 3, "Mensalista3")
        val roster = requireNotNull(rosterOf(f, f.member))
        assertEquals(listOf("Mensalista2", "Mensalista3", "Avulso1"), roster.waitlisted.map { it.displayName })
        // A posição é o lugar na fila como o app mostra, não a sequência gravada (2, 3, 1).
        assertEquals(listOf(1L, 2L, 3L), roster.waitlisted.map { it.waitlistPosition })
    }

    @Test fun `roster lists declined members alphabetically with their position`() {
        val f = fixture()
        attendance(f, f.member, "CONFIRMED", "Ana")
        val zeca = member(f.group, "declined-z"); attendance(f, zeca, "DECLINED", "Zeca")
        val bia = member(f.group, "declined-b"); attendance(f, bia, "DECLINED", "bia")
        execute("UPDATE game_attendance SET position='PONTA' WHERE member_user_id='$bia'")
        attendance(f, f.owner, "DECLINED", "Dono")
        val declined = requireNotNull(rosterOf(f, f.member)).declined
        assertEquals(listOf("bia", "Dono", "Zeca"), declined.map { it.displayName })
        assertEquals(listOf(AthletePosition.PONTA, null, null), declined.map { it.position })
        assertEquals(listOf(null, null, null), declined.map { it.waitlistPosition })
        assertEquals(listOf(false, false, false), declined.map { it.forced })
        assertEquals(listOf("Ana"), requireNotNull(rosterOf(f, f.owner)).confirmed.map { it.displayName })
    }

    @Test fun `roster declined leaves out guests and members who left the group`() {
        val f = fixture()
        attendance(f, f.member, "DECLINED", "Ana")
        execute("INSERT INTO game_attendance (game_id,group_id,member_user_id,guest_seq,status,responded_at,updated_at,member_display_name) VALUES ('${f.game}','${f.group}','${f.member}',1,'DECLINED',now(),now(),'Convidado')")
        val left = member(f.group, "left"); attendance(f, left, "DECLINED", "Saiu")
        execute("DELETE FROM group_memberships WHERE user_id='$left'")
        assertEquals(listOf("Ana"), requireNotNull(rosterOf(f, f.member)).declined.map { it.displayName })
    }

    @Test fun `roster declined drops a member who changes nao vou to vou`() {
        val f = fixture()
        success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.DECLINE))
        assertEquals(1, requireNotNull(rosterOf(f, f.member)).declined.size)
        success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM))
        val roster = requireNotNull(rosterOf(f, f.member))
        assertEquals(emptyList(), roster.declined)
        assertEquals(listOf(f.member), roster.confirmed.map { it.memberId })
    }

    @Test fun `declined members do not change confirmed and waitlist order with priority and positions`() {
        val f = fixture(capacity = 2)
        attendance(f, member(f.group, "confirmed-b"), "CONFIRMED", "bruno"); attendance(f, member(f.group, "confirmed-a"), "CONFIRMED", "Ana")
        val avulso = member(f.group, "avulso", "AVULSO"); waitlist(f, avulso, 1, "Avulso1")
        val mensalista = member(f.group, "mensalista"); waitlist(f, mensalista, 2, "Mensalista2")
        val ponta = member(f.group, "ponta"); waitlist(f, ponta, 3, "Ponta3")
        execute("UPDATE game_attendance SET position='PONTA' WHERE member_user_id IN ('$avulso','$ponta')")
        val before = requireNotNull(rosterOf(f, f.member))
        attendance(f, member(f.group, "declined-a"), "DECLINED", "Aaa"); attendance(f, member(f.group, "declined-z"), "DECLINED", "Zzz")
        val after = requireNotNull(rosterOf(f, f.member))
        assertEquals(before.confirmed, after.confirmed)
        assertEquals(before.waitlisted, after.waitlisted)
        assertEquals(before.positions, after.positions)
        assertEquals(listOf("Mensalista2", "Ponta3", "Avulso1"), after.waitlisted.map { it.displayName })
        assertEquals(listOf("Aaa", "Zzz"), after.declined.map { it.displayName })
    }

    @Test fun `roster with priority off collapses to single fifo tier`() {
        val f = fixture(capacity = 2)
        execute("UPDATE access_groups SET mensalista_priority = false WHERE id = '${f.group}'")
        val mensalistaLate = member(f.group, "mensalista-late")
        val avulsoEarly = member(f.group, "avulso-early", "AVULSO")
        waitlist(f, avulsoEarly, 1, "Avulso1")
        waitlist(f, mensalistaLate, 2, "Mensalista2")
        val roster = requireNotNull(rosterOf(f, f.member))
        assertEquals(listOf("Avulso1", "Mensalista2"), roster.waitlisted.map { it.displayName })
    }

    @Test fun `roster collapses tiers into pure fifo after the confirmation deadline`() {
        val f = fixtureWithDeadline("past")
        // Avulso com seq 1 e mensalista com seq 2: antes do prazo o mensalista viria primeiro,
        // mas após o prazo as faixas colapsam em FIFO pura pela sequência.
        val avulsoEarly = member(f.group, "avulso-early", "AVULSO")
        val mensalistaLate = member(f.group, "mensalista-late")
        waitlist(f, avulsoEarly, 1, "Avulso1")
        waitlist(f, mensalistaLate, 2, "Mensalista2")
        val roster = requireNotNull(rosterOf(f, f.member))
        assertEquals(listOf("Avulso1", "Mensalista2"), roster.waitlisted.map { it.displayName })
    }

    @Test fun `roster keeps mensalista tier before deadline even when avulso has lower sequence`() {
        val f = fixtureWithDeadline("future")
        val avulsoEarly = member(f.group, "avulso-early", "AVULSO")
        val mensalistaLate = member(f.group, "mensalista-late")
        waitlist(f, avulsoEarly, 1, "Avulso1")
        waitlist(f, mensalistaLate, 2, "Mensalista2")
        val roster = requireNotNull(rosterOf(f, f.member))
        assertEquals(listOf("Mensalista2", "Avulso1"), roster.waitlisted.map { it.displayName })
    }

    @Test fun `roster puts removed membership in the non-priority tier`() {
        val f = fixture(capacity = 2)
        val avulsoEarly = member(f.group, "avulso-early", "AVULSO")
        val mensalista = member(f.group, "mensalista")
        val removedMensalista = member(f.group, "removed-mensalista")
        waitlist(f, avulsoEarly, 1, "Avulso1")
        waitlist(f, mensalista, 2, "Mensalista2")
        waitlist(f, removedMensalista, 3, "Removido3")
        JdbcAthleteRepository(dataSource).remove(f.group, removedMensalista)
        val roster = requireNotNull(rosterOf(f, f.member))
        assertEquals(listOf("Mensalista2", "Avulso1", "Removido3"), roster.waitlisted.map { it.displayName })
    }

    @Test fun `paid confirmation creates exactly one pending charge`() { val f = fixture(fee = 2500); todosAvulsos(f.group); f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM); assertEquals(1, count("group_charges")); assertEquals("PENDING", string("SELECT status FROM group_charges")) }
    @Test fun `paid confirmation retry creates no duplicate charge or charge audit`() { val f = fixture(fee = 2500); todosAvulsos(f.group); repeat(2) { f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM) }; assertEquals(1, count("group_charges")); assertEquals(1, count("group_charge_events")) }
    @Test fun `free game confirmation creates no charge`() { val f = fixture(fee = null); f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM); assertEquals(0, count("group_charges")) }
    @Test fun `waitlisted paid game response creates no charge`() { val f = fixture(capacity = 2, fee = 2500); repeat(2) { attendance(f, member(f.group, "confirmed-$it"), "CONFIRMED") }; f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM); assertEquals(0, count("group_charges")) }
    @Test fun `declined member becoming confirmed receives one charge`() { val f = fixture(fee = 2500); todosAvulsos(f.group); attendance(f, f.member, "DECLINED"); val saved = success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM)); assertEquals(AttendanceStatus.CONFIRMED, saved.status); assertEquals(2, saved.version); assertEquals(1, count("group_charges")) }
    @Test fun `avulso self confirmation waits even with free spots`() { val f = fixture(); val avulso = member(f.group, "avulso", "AVULSO"); val saved = success(f.service.execute(avulso, f.group, f.game, intent = AttendanceIntent.CONFIRM)); assertEquals(AttendanceStatus.WAITLISTED, saved.status); assertEquals(1, saved.waitlistSequence); assertEquals(0, int("SELECT count(*) FROM game_attendance WHERE status='CONFIRMED'")) }
    @Test fun `avulso self confirmation takes a free spot when priority is off`() { val f = fixture(); execute("UPDATE access_groups SET mensalista_priority = false WHERE id = '${f.group}'"); val avulso = member(f.group, "avulso", "AVULSO"); val saved = success(f.service.execute(avulso, f.group, f.game, intent = AttendanceIntent.CONFIRM)); assertEquals(AttendanceStatus.CONFIRMED, saved.status); assertNull(saved.waitlistSequence) }
    @Test fun `waiting avulso is promoted when a confirmed member withdraws and priority is off`() { val f = fixture(); success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM)); val avulso = member(f.group, "avulso", "AVULSO"); success(f.service.execute(avulso, f.group, f.game, intent = AttendanceIntent.CONFIRM)); execute("UPDATE access_groups SET mensalista_priority = false WHERE id = '${f.group}'"); val result = assertIs<AttendanceCommandResult.Success>(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.DECLINE)); assertEquals(listOf(avulso), result.promoted.map { it.memberId }); assertEquals("CONFIRMED", status(avulso)) }
    @Test fun `waiting avulso is promoted before the deadline when a confirmed member withdraws with priority on and no mensalista waits`() { val f = fixture(); success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM)); val avulso = member(f.group, "avulso", "AVULSO"); success(f.service.execute(avulso, f.group, f.game, intent = AttendanceIntent.CONFIRM)); val later = member(f.group, "later", "AVULSO"); success(f.service.execute(later, f.group, f.game, intent = AttendanceIntent.CONFIRM)); val result = assertIs<AttendanceCommandResult.Success>(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.DECLINE)); assertEquals(listOf(avulso), result.promoted.map { it.memberId }); assertEquals("CONFIRMED", status(avulso)); assertEquals("WAITLISTED", status(later)) }
    @Test fun `waiting mensalista is promoted before an older waiting avulso when a confirmed member withdraws before the deadline`() { val f = fixture(capacity = 2); attendance(f, f.member, "CONFIRMED"); attendance(f, member(f.group, "occupied"), "CONFIRMED"); val avulso = member(f.group, "avulso", "AVULSO"); val mensalista = member(f.group, "mensalista"); waitlist(f, avulso, 1); waitlist(f, mensalista, 2); val result = assertIs<AttendanceCommandResult.Success>(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.DECLINE)); assertEquals(listOf(mensalista), result.promoted.map { it.memberId }); assertEquals("CONFIRMED", status(mensalista)); assertEquals("WAITLISTED", status(avulso)) }
    @Test fun `host leaving with a confirmed guest frees two spots for the mensalista and then the earliest avulso`() { val f = fixture(capacity = 2); attendance(f, f.member, "CONFIRMED"); execute("INSERT INTO game_attendance (game_id,group_id,member_user_id,guest_seq,status,responded_at,updated_at,member_display_name) VALUES ('${f.game}','${f.group}','${f.member}',1,'CONFIRMED',now(),now(),'Convidado')"); val avulsoA = member(f.group, "avulso-a", "AVULSO"); val mensalista = member(f.group, "mensalista"); val avulsoB = member(f.group, "avulso-b", "AVULSO"); waitlist(f, avulsoA, 1); waitlist(f, mensalista, 2); waitlist(f, avulsoB, 3); val result = assertIs<AttendanceCommandResult.Success>(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.DECLINE)); assertEquals(listOf(mensalista, avulsoA), result.promoted.map { it.memberId }); assertEquals("CONFIRMED", status(mensalista)); assertEquals("CONFIRMED", status(avulsoA)); assertEquals("WAITLISTED", status(avulsoB)) }
    @Test fun `waiting avulso keeps pure fifo after the deadline even with a mensalista behind`() { val f = fixtureWithDeadline("past", capacity = 2); attendance(f, f.member, "CONFIRMED"); attendance(f, member(f.group, "occupied"), "CONFIRMED"); val avulso = member(f.group, "avulso", "AVULSO"); val mensalista = member(f.group, "mensalista"); waitlist(f, avulso, 1); waitlist(f, mensalista, 2); val result = assertIs<AttendanceCommandResult.Success>(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.DECLINE)); assertEquals(listOf(avulso), result.promoted.map { it.memberId }); assertEquals("WAITLISTED", status(mensalista)) }
    @Test fun `manual promotion mode does not promote a waiting avulso when a confirmed member withdraws before the deadline`() { val f = fixture(capacity = 2); execute("UPDATE access_groups SET promotion_mode='MANUAL' WHERE id='${f.group}'"); attendance(f, f.member, "CONFIRMED"); attendance(f, member(f.group, "occupied"), "CONFIRMED"); val avulso = member(f.group, "avulso", "AVULSO"); waitlist(f, avulso, 1); val result = assertIs<AttendanceCommandResult.Success>(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.DECLINE)); assertEquals(emptyList(), result.promoted.map { it.memberId }); assertEquals("WAITLISTED", status(avulso)) }
    @Test fun `a never occupied spot is not given to a waiting avulso before the deadline when a waitlisted member leaves`() { val f = fixture(capacity = 2); val silent = member(f.group, "silent"); val avulso = member(f.group, "avulso", "AVULSO"); waitlist(f, f.member, 1); waitlist(f, avulso, 2); val result = assertIs<AttendanceCommandResult.Success>(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.DECLINE)); assertEquals(emptyList(), result.promoted.map { it.memberId }); assertEquals("WAITLISTED", status(avulso)); assertEquals(0, int("SELECT count(*) FROM game_attendance WHERE status='CONFIRMED' AND member_user_id='$silent'")) }
    @Test fun `organizer override confirms an avulso within capacity`() { val f = fixture(); val avulso = member(f.group, "avulso", "AVULSO"); val saved = success(f.service.execute(f.owner, f.group, f.game, avulso, AttendanceIntent.CONFIRM, AttendanceSource.ORGANIZER, "Autorizado pelo organizador")); assertEquals(AttendanceStatus.CONFIRMED, saved.status) }
    @Test fun `nonmember self response is privacy hidden`() { val f = fixture(); val outsider = user("outsider"); assertSame(AttendanceCommandResult.Hidden, f.service.execute(outsider, f.group, f.game, intent = AttendanceIntent.CONFIRM)); assertEquals(0, count("game_attendance")) }
    @Test fun `cross group game is privacy hidden`() { val f = fixture(); val other = fixture("other"); assertSame(AttendanceCommandResult.Hidden, f.service.execute(f.member, f.group, other.game, intent = AttendanceIntent.CONFIRM)) }
    @Test fun `deleted group hides attendance reads and rejects attendance writes`() { val f = fixture(); execute("UPDATE access_groups SET deleted_at=now() WHERE id='${f.group}'"); assertNull(rosterOf(f, f.member)); assertSame(AttendanceCommandResult.Hidden, f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM)); assertSame(CapacityCommandResult.Hidden, capacity(f).execute(f.owner, f.group, f.game, 1, 5)); assertEquals(0, count("game_attendance")) }
    @Test fun `simultaneous final spot confirmations produce one confirmed and one waitlisted`() { val f = fixture(capacity = 2); attendance(f, member(f.group, "occupied"), "CONFIRMED"); val second = member(f.group, "second"); val results = concurrent(f, listOf(f.member, second)); assertEquals(1, results.count { it.status == AttendanceStatus.CONFIRMED }); assertEquals(1, results.count { it.status == AttendanceStatus.WAITLISTED }); assertEquals(2, int("SELECT count(*) FROM game_attendance WHERE status='CONFIRMED'")) }
    @Test fun `simultaneous full confirmations allocate unique ordered waitlist sequences`() { val f = fixture(capacity = 2); repeat(2) { attendance(f, member(f.group, "occupied-$it"), "CONFIRMED") }; val second = member(f.group, "second"); val third = member(f.group, "third"); val results = concurrent(f, listOf(f.member, second, third)); assertEquals(listOf(1L, 2L, 3L), results.mapNotNull { it.waitlistSequence }.sorted()); assertEquals(3, int("SELECT waitlist_sequence_allocator FROM games WHERE id='${f.game}'")) }
    @Test fun `injected audit failure rolls back attendance and allocator`() { val f = fixture(capacity = 2); repeat(2) { attendance(f, member(f.group, "occupied-$it"), "CONFIRMED") }; val delegate = JdbcAttendanceCommandRepository(dataSource); val failing = object : AttendanceCommandRepository by delegate { override fun append(event: AttendanceEvent) { delegate.append(event); error("injected audit") } }; val service = service(failing); assertFailsWith<IllegalStateException> { service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM) }; assertEquals(2, count("game_attendance")); assertEquals(0, int("SELECT waitlist_sequence_allocator FROM games WHERE id='${f.game}'")); assertEquals(0, count("attendance_events")) }
    @Test fun `injected charge failure rolls back attendance audit allocator and charge`() { val f = fixture(fee = 2500); val real = chargePort(); val failing = AttendanceChargePort { aggregate, actor -> real.confirmed(aggregate, actor); error("injected charge") }; val service = RespondAttendance(JdbcTransactionRunner(dataSource), JdbcAttendanceCommandRepository(dataSource), failing, { now() }); assertFailsWith<IllegalStateException> { service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM) }; listOf("game_attendance", "attendance_events", "group_charges", "group_charge_events").forEach { assertEquals(0, count(it), it) } }
    @Test fun `confirmed withdrawal promotes exactly earliest fifo member`() { val f = promotionFixture(); val result = assertIs<AttendanceCommandResult.Success>(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.DECLINE)); assertEquals(listOf(f.waiting.first()), result.promoted.map { it.memberId }); assertEquals("CONFIRMED", status(f.waiting.first())); assertEquals("WAITLISTED", status(f.waiting.last())) }
    @Test
    fun `fifo promotion uses the mensalista tier before deadline`() {
        val f = fixture()
        attendance(f, f.member, "CONFIRMED")
        val avulso = member(f.group, "avulso", "AVULSO")
        val mensalista = member(f.group, "mensalista")
        waitlist(f, avulso, 1)
        waitlist(f, mensalista, 2)
        f.service.execute(
            f.owner,
            f.group,
            f.game,
            f.member,
            AttendanceIntent.DECLINE,
            AttendanceSource.ORGANIZER,
            "Liberar vaga",
        )
        assertEquals("CONFIRMED", status(mensalista))
        assertEquals("WAITLISTED", status(avulso))
        assertEquals(
            "SYSTEM",
            string("SELECT source FROM attendance_events WHERE member_user_id='$mensalista' AND new_status='CONFIRMED'"),
        )
    }

    @Test
    fun `fifo promotion collapses to pure fifo after deadline`() {
        val f = fixtureWithDeadline("past")
        attendance(f, f.member, "CONFIRMED")
        val avulso = member(f.group, "avulso", "AVULSO")
        val mensalista = member(f.group, "mensalista")
        waitlist(f, avulso, 1)
        waitlist(f, mensalista, 2)
        f.service.execute(
            f.owner,
            f.group,
            f.game,
            f.member,
            AttendanceIntent.DECLINE,
            AttendanceSource.ORGANIZER,
            "Liberar vaga",
        )
        assertEquals("CONFIRMED", status(avulso))
        assertEquals("WAITLISTED", status(mensalista))
    }

    @Test
    fun `organizer override withdrawal triggers automatic fifo promotion`() {
        val f = fullFixture()
        val waiting = member(f.group, "waiting")
        waitlist(f, waiting, 1)
        val result = assertIs<AttendanceCommandResult.Success>(
            f.service.execute(
                f.owner,
                f.group,
                f.game,
                f.member,
                AttendanceIntent.DECLINE,
                AttendanceSource.ORGANIZER,
                "Remover confirmado",
            ),
        )
        assertEquals(listOf(waiting), result.promoted.map { it.memberId })
        assertEquals("SYSTEM", string("SELECT source FROM attendance_events WHERE member_user_id='$waiting'"))
    }
    @Test fun `confirmed withdrawal keeps its existing pending charge`() { val f = promotionFixture(); f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.DECLINE); assertEquals("PENDING", string("SELECT status FROM group_charges WHERE member_user_id='${f.member}'")) }
    @Test fun `paid game promotion creates one charge for promoted member`() { val f = promotionFixture(); f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.DECLINE); assertEquals(1, int("SELECT count(*) FROM group_charges WHERE member_user_id='${f.waiting.first()}'")); assertEquals(2, count("group_charges")) }
    @Test fun `fifo promotion preserves later stable waitlist sequence`() { val f = promotionFixture(); f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.DECLINE); assertEquals(2, int("SELECT waitlist_sequence FROM game_attendance WHERE member_user_id='${f.waiting.last()}'")) }
    @Test fun `withdrawal without waitlist promotes nobody`() { val f = fixture(); success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM)); val result = assertIs<AttendanceCommandResult.Success>(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.DECLINE)); assertEquals(emptyList(), result.promoted) }
    @Test fun `waitlisted withdrawal does not open or promote a capacity spot`() { val f = fullFixture(); val waiting = member(f.group, "waiting"); waitlist(f, waiting, 1); val later = member(f.group, "later"); waitlist(f, later, 2); val result = assertIs<AttendanceCommandResult.Success>(f.service.execute(waiting, f.group, f.game, intent = AttendanceIntent.DECLINE)); assertEquals(emptyList(), result.promoted); assertEquals("WAITLISTED", status(later)) }
    @Test
    fun `capacity increase promotes exactly newly available fifo spots`() {
        val f = fullFixture(capacity = 4)
        val waiting = (1..3).map {
            member(f.group, "waiting-$it").also { id -> waitlist(f, id, it.toLong()) }
        }
        val result = capacity(f).execute(f.owner, f.group, f.game, 1, 6)
        assertEquals(
            waiting.take(2),
            assertIs<CapacityCommandResult.Success>(result).promoted.map { it.memberId },
        )
        assertEquals("WAITLISTED", status(waiting.last()))
        assertEquals(2, count("attendance_events"))
    }
    @Test fun `capacity increase charges every paid game promotion once`() { val f = fullFixture(); val waiting = (1..2).map { member(f.group, "waiting-$it").also { id -> waitlist(f, id, it.toLong()) } }; todosAvulsos(f.group); capacity(f).execute(f.owner, f.group, f.game, 1, 4); assertEquals(waiting.toSet(), queryStrings("SELECT member_user_id::text FROM group_charges").map(UUID::fromString).toSet()) }
    @Test
    fun `manual mode does not auto promote on withdrawal or capacity increase`() {
        val f = fullFixture(capacity = 4)
        execute("UPDATE access_groups SET promotion_mode='MANUAL' WHERE id='${f.group}'")
        val waiting = member(f.group, "waiting")
        waitlist(f, waiting, 1)
        f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.DECLINE)
        assertEquals("WAITLISTED", status(waiting))
        val result = assertIs<CapacityCommandResult.Success>(capacity(f).execute(f.owner, f.group, f.game, 1, 5))
        assertEquals(emptyList(), result.promoted)
        assertEquals("WAITLISTED", status(waiting))
        assertEquals(1, count("attendance_events"))
    }

    @Test
    fun `manual promotion selects any waitlisted member and creates avulso charge`() {
        val f = fullFixture()
        execute("UPDATE access_groups SET promotion_mode='MANUAL' WHERE id='${f.group}'")
        val first = member(f.group, "first")
        val selected = member(f.group, "selected", "AVULSO")
        waitlist(f, first, 1)
        waitlist(f, selected, 2)
        f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.DECLINE)
        val result = assertIs<AttendanceCommandResult.Success>(
            f.service.promote(
                f.owner,
                f.group,
                f.game,
                selected,
                UUID.randomUUID(),
                "Escolha do organizador",
            ),
        )
        assertEquals(selected, result.attendance.memberId)
        assertEquals("CONFIRMED", status(selected))
        assertEquals("WAITLISTED", status(first))
        assertEquals("ORGANIZER", string("SELECT source FROM attendance_events WHERE member_user_id='$selected'"))
        assertEquals(1, int("SELECT count(*) FROM group_charges WHERE member_user_id='$selected'"))
    }

    @Test
    fun `manual promotion retry with the same request id replays without another event or charge`() {
        val f = fullFixture()
        execute("UPDATE access_groups SET promotion_mode='MANUAL' WHERE id='${f.group}'")
        val selected = member(f.group, "selected", "AVULSO")
        waitlist(f, selected, 1)
        f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.DECLINE)
        val requestId = UUID.randomUUID()
        val first = f.service.promote(f.owner, f.group, f.game, selected, requestId, "Escolha do organizador")
        val replay = f.service.promote(f.owner, f.group, f.game, selected, requestId, "Outro motivo")
        assertEquals(first, replay)
        assertEquals(2, count("attendance_events"))
        assertEquals(1, int("SELECT count(*) FROM group_charges WHERE member_user_id='$selected'"))
    }

    @Test
    fun `directed promotion is rejected in fifo mode`() {
        val f = fixture()
        attendance(f, f.member, "CONFIRMED")
        val selected = member(f.group, "selected")
        waitlist(f, selected, 1)
        val result = f.service.promote(
            f.owner,
            f.group,
            f.game,
            selected,
            UUID.randomUUID(),
            "Escolha do organizador",
        )
        assertEquals(AttendanceCommandResult.Denied(AttendanceDenial.MANUAL_PROMOTION_ONLY), result)
        assertEquals("WAITLISTED", status(selected))
    }
    @Test fun `capacity increase stops when fifo is empty`() { val f = fullFixture(); val waiting = member(f.group, "waiting"); waitlist(f, waiting, 1); val result = assertIs<CapacityCommandResult.Success>(capacity(f).execute(f.owner, f.group, f.game, 1, 6)); assertEquals(listOf(waiting), result.promoted.map { it.memberId }); assertEquals(3, int("SELECT count(*) FROM game_attendance WHERE status='CONFIRMED'")) }
    @Test fun `capacity decrease silently demotes nobody`() { val f = fullFixture(capacity = 6, confirmed = 6); assertIs<CapacityCommandResult.Success>(capacity(f).execute(f.owner, f.group, f.game, 1, 4)); assertEquals(6, int("SELECT count(*) FROM game_attendance WHERE status='CONFIRMED'")); assertEquals(4, int("SELECT capacity FROM games")) }
    @Test fun `capacity below confirmed count blocks new confirmation`() { val f = fullFixture(capacity = 6, confirmed = 6); capacity(f).execute(f.owner, f.group, f.game, 1, 4); val newcomer = member(f.group, "newcomer"); assertEquals(AttendanceStatus.WAITLISTED, success(f.service.execute(newcomer, f.group, f.game, intent = AttendanceIntent.CONFIRM)).status) }
    @Test fun `confirmed withdrawal succeeds when reduced capacity cannot promote`() { val f = fullFixture(capacity = 6, confirmed = 6); capacity(f).execute(f.owner, f.group, f.game, 1, 4); val waiting = member(f.group, "waiting"); waitlist(f, waiting, 1); val result = assertIs<AttendanceCommandResult.Success>(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.DECLINE)); assertEquals(AttendanceStatus.DECLINED, result.attendance.status); assertEquals(emptyList(), result.promoted); assertEquals("WAITLISTED", status(waiting)); assertEquals(1, count("attendance_events")) }
    @Test fun `stale capacity version changes nothing`() { val f = fullFixture(); val waiting = member(f.group, "waiting"); waitlist(f, waiting, 1); assertSame(CapacityCommandResult.Conflict, capacity(f).execute(f.owner, f.group, f.game, 2, 5)); assertEquals(2, int("SELECT capacity FROM games")); assertEquals("WAITLISTED", status(waiting)) }
    @Test fun `capacity rejects values outside game bounds`() { val f = fixture(); assertSame(CapacityCommandResult.InvalidCapacity, capacity(f).execute(f.owner, f.group, f.game, 1, 1)); assertSame(CapacityCommandResult.InvalidCapacity, capacity(f).execute(f.owner, f.group, f.game, 1, 3)); assertSame(CapacityCommandResult.InvalidCapacity, capacity(f).execute(f.owner, f.group, f.game, 1, 101)); assertEquals(2, int("SELECT capacity FROM games")) }
    @Test fun `capacity accepts the minimum of 4 and the ceiling of 100 on a legacy game with 2 spots`() { val f = fixture(); assertIs<CapacityCommandResult.Success>(capacity(f).execute(f.owner, f.group, f.game, 1, 4)); assertEquals(4, int("SELECT capacity FROM games")); assertIs<CapacityCommandResult.Success>(capacity(f).execute(f.owner, f.group, f.game, 2, 100)); assertEquals(100, int("SELECT capacity FROM games")) }
    @Test fun `athlete cannot adjust capacity`() { val f = fixture(); assertSame(CapacityCommandResult.Forbidden, capacity(f).execute(f.member, f.group, f.game, 1, 5)) }
    @Test fun `cancelled game capacity and promotions are frozen`() { val f = fullFixture(); execute("UPDATE games SET status='CANCELLED' WHERE id='${f.game}'"); assertSame(CapacityCommandResult.Frozen, capacity(f).execute(f.owner, f.group, f.game, 1, 5)) }
    @Test fun `injected promotion charge failure rolls back withdrawal promotion audit and charge`() { val f = fullFixture(); val waiting = member(f.group, "waiting"); waitlist(f, waiting, 1); val real = chargePort(); val failing = object : AttendanceChargePort { override fun confirmed(aggregate: AttendanceAggregate, actorId: UUID) = real.confirmed(aggregate, actorId); override fun promoted(aggregate: AttendanceAggregate, actorId: UUID) { real.promoted(aggregate, actorId); error("injected promotion") } }; val service = RespondAttendance(JdbcTransactionRunner(dataSource), JdbcAttendanceCommandRepository(dataSource), failing, { now() }); assertFailsWith<IllegalStateException> { service.execute(f.member, f.group, f.game, intent = AttendanceIntent.DECLINE) }; assertEquals("CONFIRMED", status(f.member)); assertEquals("WAITLISTED", status(waiting)); assertEquals(0, count("attendance_events")); assertEquals(0, count("group_charges")) }
    @Test fun `concurrent withdraw and confirm preserve capacity and earliest promotion`() { val f = fullFixture(); val waiting = member(f.group, "waiting"); waitlist(f, waiting, 1); val newcomer = member(f.group, "newcomer"); race({ f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.DECLINE) }, { f.service.execute(newcomer, f.group, f.game, intent = AttendanceIntent.CONFIRM) }); assertEquals(2, int("SELECT count(*) FROM game_attendance WHERE status='CONFIRMED'")); assertEquals("CONFIRMED", status(waiting)); assertEquals("WAITLISTED", status(newcomer)) }
    @Test fun `concurrent capacity increase and confirm preserve fifo and capacity`() { val f = fullFixture(capacity = 4); val waiting = member(f.group, "waiting"); waitlist(f, waiting, 1); val newcomer = member(f.group, "newcomer"); race({ capacity(f).execute(f.owner, f.group, f.game, 1, 5) }, { f.service.execute(newcomer, f.group, f.game, intent = AttendanceIntent.CONFIRM) }); assertEquals(5, int("SELECT count(*) FROM game_attendance WHERE status='CONFIRMED'")); assertEquals("CONFIRMED", status(waiting)); assertEquals("WAITLISTED", status(newcomer)) }

    // --- Voltar à espera: a linha já existe (DECLINED) e o gatilho monotônico não pode rodar duas vezes ---
    @Test fun `declined member returning to a full game waits again with the next monotonic sequence`() {
        val f = fixture(capacity = 2)
        repeat(2) { attendance(f, member(f.group, "occupied-$it"), "CONFIRMED") }

        val waiting = success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM))
        assertEquals(AttendanceStatus.WAITLISTED, waiting.status)
        assertEquals(1L, waiting.waitlistSequence)
        val declined = success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.DECLINE))
        assertEquals(AttendanceStatus.DECLINED, declined.status)
        assertNull(declined.waitlistSequence)

        val again = success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM))

        assertEquals(AttendanceStatus.WAITLISTED, again.status)
        assertEquals(2L, again.waitlistSequence)
        assertEquals(3L, again.version)
        assertEquals("WAITLISTED", status(f.member))
        assertEquals(2, int("SELECT waitlist_sequence_allocator FROM games WHERE id='${f.game}'"))
        assertEquals(1, int("SELECT count(*) FROM game_attendance WHERE member_user_id='${f.member}'"))
        assertEquals(3, int("SELECT count(*) FROM attendance_events WHERE member_user_id='${f.member}'"))
    }

    @Test fun `confirmed member who declined returns to a game that filled up meanwhile and waits`() {
        val f = fixture(capacity = 2)
        assertEquals(AttendanceStatus.CONFIRMED, success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM)).status)
        assertEquals(AttendanceStatus.DECLINED, success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.DECLINE)).status)
        repeat(2) { attendance(f, member(f.group, "filled-$it"), "CONFIRMED") }

        val again = success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM))

        assertEquals(AttendanceStatus.WAITLISTED, again.status)
        assertEquals(1L, again.waitlistSequence)
        assertEquals(3L, again.version)
        assertEquals(1, int("SELECT waitlist_sequence_allocator FROM games WHERE id='${f.game}'"))
    }

    @Test fun `saving an answer that already has a row as a first version loses the write`() {
        val f = fixture()
        attendance(f, f.member, "DECLINED")
        val stamp = now()

        val failure = assertFailsWith<IllegalStateException> {
            JdbcAttendanceCommandRepository(dataSource).save(AttendanceRecord(f.game, f.group, f.member, AttendanceStatus.CONFIRMED, null, stamp, stamp, 1))
        }

        assertEquals("attendance optimistic write lost", failure.message)
        assertEquals("DECLINED", status(f.member))
    }

    @Test fun `saving an answer over a stale version loses the write`() {
        val f = fixture()
        attendance(f, f.member, "DECLINED")
        val stamp = now()

        val failure = assertFailsWith<IllegalStateException> {
            JdbcAttendanceCommandRepository(dataSource).save(AttendanceRecord(f.game, f.group, f.member, AttendanceStatus.CONFIRMED, null, stamp, stamp, 3))
        }

        assertEquals("attendance optimistic write lost", failure.message)
        assertEquals("DECLINED", status(f.member))
        assertEquals(1, int("SELECT version FROM game_attendance WHERE member_user_id='${f.member}'"))
    }

    @Test fun `saving a first version writes the row and a next version updates it in place`() {
        val f = fixture()
        val repository = JdbcAttendanceCommandRepository(dataSource)
        val stamp = now()

        repository.save(AttendanceRecord(f.game, f.group, f.member, AttendanceStatus.CONFIRMED, null, stamp, stamp, 1))
        assertEquals("CONFIRMED", status(f.member))
        assertEquals(1, int("SELECT version FROM game_attendance WHERE member_user_id='${f.member}'"))

        repository.save(AttendanceRecord(f.game, f.group, f.member, AttendanceStatus.DECLINED, null, stamp, stamp.plusSeconds(5), 2))
        assertEquals("DECLINED", status(f.member))
        assertEquals(2, int("SELECT version FROM game_attendance WHERE member_user_id='${f.member}'"))
        assertEquals(1, int("SELECT count(*) FROM game_attendance WHERE member_user_id='${f.member}'"))
    }

    private fun concurrent(f: Fixture, members: List<UUID>): List<AttendanceRecord> {
        val barrier = CyclicBarrier(members.size + 1)
        val executor = Executors.newFixedThreadPool(members.size)
        return try {
            val futures = members.map { member -> executor.submit<AttendanceRecord> { barrier.await(); success(f.service.execute(member, f.group, f.game, intent = AttendanceIntent.CONFIRM)) } }
            barrier.await()
            futures.map { it.get() }
        } finally { executor.shutdownNow() }
    }

    private fun race(first: () -> Any, second: () -> Any) {
        val barrier = CyclicBarrier(3)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val futures = listOf(first, second).map { command -> executor.submit<Any> { barrier.await(); command() } }
            barrier.await()
            futures.forEach { it.get() }
        } finally { executor.shutdownNow() }
    }

    // --- Regra de 13/10/2026: depois do prazo o "Vou" entra pela espera e o grupo decide ---
    @Test fun `after the deadline a late Vou goes to the waitlist and FIFO fills the free spot at once`() {
        val f = fixtureWithDeadline("past", capacity = 2)
        val result = f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM)
        val saved = success(result)
        assertEquals(AttendanceStatus.CONFIRMED, saved.status)
        assertEquals(1, assertIs<AttendanceCommandResult.Success>(result).promoted.size)
        assertEquals("CONFIRMED", status(f.member))
        assertEquals(listOf("WAITLISTED", "CONFIRMED"), queryStrings("SELECT new_status FROM attendance_events WHERE member_user_id='${f.member}' ORDER BY occurred_at, id").sortedDescending())
    }
    @Test fun `after the deadline FIFO respects the queue order when the late Vou arrives with a free spot`() {
        val f = fixtureWithDeadline("past", capacity = 2)
        attendance(f, member(f.group, "occupied"), "CONFIRMED")
        val ahead = member(f.group, "ahead", "AVULSO"); waitlist(f, ahead, 1)
        execute("UPDATE games SET capacity=2 WHERE id='${f.game}'")
        execute("DELETE FROM game_attendance WHERE game_id='${f.game}' AND status='CONFIRMED'")
        attendance(f, member(f.group, "occupied2"), "CONFIRMED")
        val saved = success(f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM))
        assertEquals("CONFIRMED", status(ahead))
        assertEquals(AttendanceStatus.WAITLISTED, saved.status)
        assertEquals(2, int("SELECT count(*) FROM game_attendance WHERE game_id='${f.game}' AND status='CONFIRMED'"))
    }
    @Test fun `after the deadline with MANUAL the late Vou waits for the organizer even with a free spot`() {
        val f = fixtureWithDeadline("past", capacity = 2)
        execute("UPDATE access_groups SET promotion_mode='MANUAL' WHERE id='${f.group}'")
        val result = f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM)
        assertEquals(AttendanceStatus.WAITLISTED, success(result).status)
        assertEquals(0, assertIs<AttendanceCommandResult.Success>(result).promoted.size)
        assertEquals("WAITLISTED", status(f.member))
    }
    @Test fun `after the deadline Nao vou frees the spot and FIFO promotes the first in line while MANUAL does not`() {
        val f = fixtureWithDeadline("past", capacity = 2)
        attendance(f, f.member, "CONFIRMED"); attendance(f, member(f.group, "occupied-a"), "CONFIRMED")
        val waiting = member(f.group, "waiting", "AVULSO"); waitlist(f, waiting, 1)
        val result = f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.DECLINE)
        assertEquals(AttendanceStatus.DECLINED, success(result).status)
        assertEquals("CONFIRMED", status(waiting))
        val g = fixtureWithDeadline("past", capacity = 2)
        execute("UPDATE access_groups SET promotion_mode='MANUAL' WHERE id='${g.group}'")
        attendance(g, g.member, "CONFIRMED"); attendance(g, member(g.group, "occupied-b"), "CONFIRMED")
        val waitingManual = member(g.group, "waiting-manual", "AVULSO"); waitlist(g, waitingManual, 1)
        assertEquals(AttendanceStatus.DECLINED, success(g.service.execute(g.member, g.group, g.game, intent = AttendanceIntent.DECLINE)).status)
        assertEquals("WAITLISTED", status(waitingManual))
    }
    @Test fun `once the game started the athlete response is frozen`() {
        val f = fixtureWithDeadline("past", capacity = 2)
        execute("ALTER TABLE games DISABLE TRIGGER ALL")
        execute("UPDATE games SET starts_at = now() - interval '1 minute' WHERE id='${f.game}'")
        val result = f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM)
        assertEquals(AttendanceDenial.FROZEN, assertIs<AttendanceCommandResult.Denied>(result).reason)
    }

    // A diária é só do avulso: estes casos conferem a cobrança, então o grupo inteiro vira avulso e sem prioridade (a espera fica FIFO).
    private fun todosAvulsos(group: UUID) {
        execute("UPDATE group_memberships SET membership_type='AVULSO' WHERE group_id='$group'")
        execute("UPDATE access_groups SET mensalista_priority=false WHERE id='$group'")
    }

    private fun promotionFixture(avulso: Boolean = true): PromotionFixture {
        val f = fixture(capacity = 2)
        if (avulso) todosAvulsos(f.group)
        f.service.execute(f.member, f.group, f.game, intent = AttendanceIntent.CONFIRM)
        attendance(f, member(f.group, "occupied"), "CONFIRMED")
        val waiting = (1..2).map { member(f.group, "waiting-$it").also { id -> waitlist(f, id, it.toLong()) } }
        if (avulso) todosAvulsos(f.group)
        return PromotionFixture(f.owner, f.member, f.group, f.game, f.service, waiting)
    }

    private fun fullFixture(capacity: Int = 2, confirmed: Int = capacity): Fixture {
        val f = fixture(capacity = capacity)
        attendance(f, f.member, "CONFIRMED")
        repeat(confirmed - 1) { attendance(f, member(f.group, "occupied-$it"), "CONFIRMED") }
        return f
    }

    private fun fixture(subject: String = "attendance", capacity: Int = 2, fee: Long? = 2500): Fixture {
        val owner = user("$subject-owner")
        val group = UUID.randomUUID()
        execute("INSERT INTO access_groups (id,owner_user_id,creation_key,name,time_zone,profile_status,modality,composition,created_at,updated_at) VALUES ('$group','$owner','${UUID.randomUUID()}','Attendance Group','America/Sao_Paulo','COMPLETE','COURT_VOLLEYBALL','MIXED',now(),now())")
        val member = member(group, "$subject-member")
        val game = UUID.randomUUID()
        // Deadline and start stay ahead of Postgres now() so SELF responses never trip
        // the deadline/started rules; ck_games_deadline still holds because deadline < starts_at.
        execute("INSERT INTO games (id,group_id,title,local_date,local_time,zone_id,starts_at,duration_minutes,confirmation_deadline,venue_name,venue_address,capacity,game_fee_cents,status,created_at,updated_at) VALUES ('$game','$group','Treino',(CURRENT_DATE + 2),TIME '19:30','America/Sao_Paulo',now() + interval '2 days',90,now() + interval '1 day','Arena','Rua Central 100',$capacity,${fee ?: "NULL"},'PUBLISHED',now(),now())")
        return Fixture(owner, member, group, game, service())
    }
    // `deadlineRelative` controls whether the game's confirmation deadline is in the past ("past")
    // or in the future ("future") relative to the Postgres now(), exercising the tier collapse.
    private fun fixtureWithDeadline(deadlineRelative: String, capacity: Int = 2, fee: Long? = 2500): Fixture {
        val owner = user("deadline-$deadlineRelative-owner")
        val group = UUID.randomUUID()
        execute("INSERT INTO access_groups (id,owner_user_id,creation_key,name,time_zone,profile_status,modality,composition,created_at,updated_at) VALUES ('$group','$owner','${UUID.randomUUID()}','Deadline Group','America/Sao_Paulo','COMPLETE','COURT_VOLLEYBALL','MIXED',now(),now())")
        val member = member(group, "deadline-$deadlineRelative-member")
        val game = UUID.randomUUID()
        val deadline = if (deadlineRelative == "past") "now() - interval '1 hour'" else "now() + interval '1 hour'"
        execute("INSERT INTO games (id,group_id,title,local_date,local_time,zone_id,starts_at,duration_minutes,confirmation_deadline,venue_name,venue_address,capacity,game_fee_cents,status,created_at,updated_at) VALUES ('$game','$group','Treino',(CURRENT_DATE + 2),TIME '19:30','America/Sao_Paulo',now() + interval '2 days',90,$deadline,'Arena','Rua Central 100',$capacity,${fee ?: "NULL"},'PUBLISHED',now(),now())")
        return Fixture(owner, member, group, game, service())
    }
    private fun service(repository: AttendanceCommandRepository = JdbcAttendanceCommandRepository(dataSource)) = RespondAttendance(JdbcTransactionRunner(dataSource), repository, chargePort(), { now() })
    private fun capacity(f: Fixture) = AdjustGameCapacity(JdbcTransactionRunner(dataSource), JdbcAttendanceCommandRepository(dataSource), chargePort(), { now() })
    private fun chargePort() = AttendanceChargeAdapter(ChargeTransactions(JdbcTransactionRunner(dataSource), JdbcChargeTransactionRepository(dataSource)) { now() })
    // timestamptz guarda microssegundos; sem truncar, o objeto devolvido na primeira chamada
    // (nanos da JVM em Linux) nunca bate com o relido do banco no replay.
    private fun now() = Instant.now().truncatedTo(ChronoUnit.MICROS)
    private fun attendance(f: Fixture, member: UUID, status: String, name: String = "Member") { execute("INSERT INTO game_attendance (game_id,group_id,member_user_id,status,responded_at,updated_at,member_display_name) VALUES ('${f.game}','${f.group}','$member','$status',now(),now(),'$name')") }
    private fun waitlist(f: Fixture, member: UUID, sequence: Long, name: String = "Member") { execute("INSERT INTO game_attendance (game_id,group_id,member_user_id,status,waitlist_sequence,responded_at,updated_at,member_display_name) VALUES ('${f.game}','${f.group}','$member','WAITLISTED',$sequence,now(),now(),'$name')") }
    // `membership` stays the third positional parameter so the mensalista/avulso cases keep reading
    // as member(group, subject, "AVULSO"); the display name is opt-in by name for the snapshot cases.
    private fun member(group: UUID, subject: String, membership: String = "MENSALISTA", name: String = "User"): UUID { val id = user(subject, name); execute("INSERT INTO group_memberships (group_id,user_id,role,created_at,updated_at,membership_type) VALUES ('$group','$id','ATHLETE',now(),now(),'$membership')"); return id }
    private fun user(subject: String, name: String = "User"): UUID { val id = UUID.randomUUID(); execute("INSERT INTO access_users (id,firebase_subject,email_verified,display_name,created_at,updated_at) VALUES ('$id','$subject-${UUID.randomUUID()}',true,'$name',now(),now())"); return id }
    private fun rosterOf(f: Fixture, actor: UUID) = JdbcAttendanceCommandRepository(dataSource).roster(actor, f.group, f.game)
    private fun snapshotName(member: UUID) = string("SELECT member_display_name FROM game_attendance WHERE member_user_id='$member'")
    private fun success(result: AttendanceCommandResult) = assertIs<AttendanceCommandResult.Success>(result).attendance
    private fun execute(sql: String) { connection().use { c -> c.createStatement().use { it.execute(sql) } } }
    private fun count(table: String) = int("SELECT count(*) FROM $table")
    private fun int(sql: String) = query(sql) { it.getInt(1) }
    private fun string(sql: String) = query(sql) { it.getString(1) }
    private fun status(member: UUID) = string("SELECT status FROM game_attendance WHERE member_user_id='$member'")
    private fun queryStrings(sql: String): List<String> = connection().use { c -> c.createStatement().use { s -> s.executeQuery(sql).use { r -> buildList { while (r.next()) add(r.getString(1)) } } } }
    private fun <T> query(sql: String, read: (java.sql.ResultSet) -> T): T = connection().use { c -> c.createStatement().use { s -> s.executeQuery(sql).use { r -> check(r.next()); read(r) } } }
    private fun connection(): Connection = dataSource.connection
    // Jogo PUBLISHED a mais no grupo do fixture. `days` >= 3 e distinto por chamada: o fixture ocupa
    // now() + 2 dias e games_schedule_no_overlap proíbe dois jogos ativos no mesmo início.
    private fun extraGame(f: Fixture, days: Int): UUID { val id = UUID.randomUUID(); execute("INSERT INTO games (id,group_id,title,local_date,local_time,zone_id,starts_at,duration_minutes,confirmation_deadline,venue_name,venue_address,capacity,game_fee_cents,status,created_at,updated_at) VALUES ('$id','${f.group}','Treino $days',(CURRENT_DATE + $days),TIME '19:30','America/Sao_Paulo',now() + interval '$days days',90,now() + interval '${days - 1} days','Arena','Rua Central 100',2,2500,'PUBLISHED',now(),now())"); return id }
    // ponytail: cópia enxuta do CountingDataSource de JdbcGroupReadRepositoryIntegrationTest (lá ele é
    // private). Extrair para br.com.saqz.groups.testing quando um terceiro teste precisar contar statements.
    private class CountingDataSource(private val delegate: DataSource) : AbstractDataSource() {
        val preparedStatements = AtomicInteger()
        override fun getConnection(): Connection = wrap(delegate.connection)
        override fun getConnection(username: String, password: String): Connection = wrap(delegate.getConnection(username, password))
        private fun wrap(connection: Connection): Connection = Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java),
        ) { _, method, arguments ->
            if (method.name == "prepareStatement") preparedStatements.incrementAndGet()
            method.invoke(connection, *(arguments ?: emptyArray()))
        } as Connection
    }
    private data class Fixture(val owner: UUID, val member: UUID, val group: UUID, val game: UUID, val service: RespondAttendance)
    private data class PromotionFixture(val owner: UUID, val member: UUID, val group: UUID, val game: UUID, val service: RespondAttendance, val waiting: List<UUID>)
}
