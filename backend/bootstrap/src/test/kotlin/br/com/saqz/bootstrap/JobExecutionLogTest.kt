package br.com.saqz.bootstrap

import br.com.saqz.bootstrap.configuration.AutomaticAttendanceReminder
import br.com.saqz.bootstrap.configuration.NotificationPushWorker
import br.com.saqz.groups.adapter.input.scheduling.CompleteFinishedGamesJob
import br.com.saqz.groups.adapter.input.scheduling.MonthlyChargeJob
import br.com.saqz.groups.adapter.output.jdbc.communication.JdbcGroupCommunicationRepository
import br.com.saqz.groups.adapter.output.jdbc.communication.JdbcNotificationPush
import br.com.saqz.groups.adapter.output.jdbc.game.JdbcFinishedGamesRepository
import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.communication.GroupCommunicationService
import br.com.saqz.groups.application.communication.NotificationPushSender
import br.com.saqz.groups.application.communication.PushDelivery
import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.finance.charge.ChargeTransactionRepository
import br.com.saqz.groups.application.finance.charge.ChargeTransactions
import br.com.saqz.groups.application.finance.charge.GameChargeInput
import br.com.saqz.groups.application.finance.charge.GroupMembers
import br.com.saqz.groups.application.finance.charge.MonthlyChargeSchedule
import br.com.saqz.groups.application.finance.charge.MonthlyDueMembership
import br.com.saqz.groups.application.finance.charge.MonthlyDueMembershipRepository
import br.com.saqz.groups.application.finance.charge.MonthlyGenerationCommand
import br.com.saqz.groups.application.game.CompleteFinishedGames
import br.com.saqz.groups.domain.finance.charge.Charge
import br.com.saqz.groups.domain.finance.charge.ChargeIdentity
import br.com.saqz.groups.domain.group.MonthlyLateJoinPolicy
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.sharedkernel.subscription.GroupWriteAccess
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Log de execução dos jobs (BE-10): resumo + itens com ids e nada de dado pessoal. As linhas saem do logger
 * `br.com.saqz.job`, capturado aqui por um appender em memória.
 */
class JobExecutionLogTest {
    private val source = TestPostgres.migrated("classpath:db/migration", owner = this).dataSource
    private val jdbc = JdbcClient.create(source)
    private val transaction = JdbcTransactionRunner(source)
    private val communication = GroupCommunicationService(transaction, JdbcGroupReadRepository(source), JdbcGroupCommunicationRepository(source))
    private val push = JdbcNotificationPush(source, transaction)
    private val reminder = AutomaticAttendanceReminder(communication)

    private val logger = LoggerFactory.getLogger("br.com.saqz.job") as Logger
    private val appender = ListAppender<ILoggingEvent>()

    private val ownerName = "Carlos Organizador"
    private val athleteName = "Maria Silva"
    private val athletePhone = "+5511987654321"
    private val athleteEmail = "maria.silva@example.com"
    private val groupName = "Vôlei de Quinta"
    private val deviceToken = "fcm-token-secreto-123"
    private val owner = user(ownerName, "owner@example.com", "+5511911112222")
    private val athlete = user(athleteName, athleteEmail, athletePhone)
    private val group = UUID.randomUUID()

    init {
        jdbc.sql("""INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at)
            VALUES (:id, :owner, :key, :name, 'America/Sao_Paulo', now(), now())""")
            .param("id", group).param("owner", owner).param("key", UUID.randomUUID()).param("name", groupName).update()
        for (member in listOf(owner, athlete)) jdbc.sql("""
            INSERT INTO group_memberships(group_id, user_id, role, membership_type, active, created_at, updated_at)
            VALUES (:g, :u, 'ATHLETE', 'AVULSO', true, now(), now())
        """).param("g", group).param("u", member).update()
        push.register(athlete, UUID.randomUUID(), deviceToken, "ANDROID")
    }

    @BeforeEach fun attach() {
        jdbc.sql("TRUNCATE games CASCADE").update()
        jdbc.sql("TRUNCATE group_messages CASCADE").update()
        appender.list.clear()
        appender.start()
        logger.addAppender(appender)
    }

    @AfterEach fun detach() { logger.detachAppender(appender) }

    private fun lines() = appender.list.map { it.formattedMessage }

    @Test fun `reminder cron logs a run line with zero when there is no game`() {
        reminder.run()

        val line = lines().single()
        assertTrue(Regex("^job_run job=attendance_reminder published=0 durationMs=\\d+$").matches(line), line)
    }

    @Test fun `reminder cron logs the run and one item with group game and message ids`() {
        val game = game(starts = "now() + interval '5 hours'")

        reminder.run()

        val message = jdbc.sql("SELECT id FROM group_messages WHERE channel = 'REMINDER'").query(UUID::class.java).single()
        val lines = lines()
        assertEquals("job_item job=attendance_reminder event=reminder_published groupId=$group gameId=$game messageId=$message", lines[0])
        assertTrue(Regex("^job_run job=attendance_reminder published=1 durationMs=\\d+$").matches(lines[1]), lines[1])
    }

    @Test fun `frequent push queue is silent when empty and logs summary and items when it delivers`() {
        push.drain(NotificationPushSender { _, _ -> PushDelivery.SENT })
        val worker = NotificationPushWorker(push, NotificationPushSender { _, _ -> PushDelivery.SENT })

        worker.run()
        assertEquals(emptyList(), lines())

        game(starts = "now() + interval '5 hours'")
        communication.remindAutomatically()
        appender.list.clear()
        worker.run()

        val lines = lines()
        val item = lines.single { it.startsWith("job_item job=notification_push event=push_sent") }
        assertTrue("groupId=$group" in item && "userId=$athlete" in item && "notificationId=" in item, item)
        assertTrue(lines.any { Regex("^job_run job=notification_push processed=1 delivered=1 failed=0 durationMs=\\d+$").matches(it) }, lines.toString())
    }

    @Test fun `failed push delivery is a warning with the same ids and the failure code`() {
        game(starts = "now() + interval '5 hours'")
        communication.remindAutomatically()
        appender.list.clear()

        NotificationPushWorker(push, NotificationPushSender { _, _ -> PushDelivery.RETRY }).run()

        val warning = appender.list.single { it.level == Level.WARN }.formattedMessage
        assertTrue(warning.startsWith("job_item_failed job=notification_push event=push_failed"), warning)
        assertTrue("groupId=$group" in warning && "userId=$athlete" in warning && warning.endsWith("error=RETRY"), warning)
        assertTrue(lines().any { Regex("job_run job=notification_push processed=1 delivered=0 failed=1 ").containsMatchIn(it) }, lines().toString())
    }

    @Test fun `finished games are logged with their ids and the run line only when one finished`() {
        val job = CompleteFinishedGamesJob(CompleteFinishedGames(JdbcFinishedGamesRepository(source), Clock.systemUTC()))
        job.run()
        assertEquals(emptyList(), lines())

        val finished = game(starts = "now() - interval '3 hours'", deadline = "now() - interval '4 hours'")
        job.run()

        val lines = lines()
        assertEquals("job_item job=complete_finished_games event=game_completed groupId=$group gameId=$finished", lines[0])
        assertTrue(Regex("^job_run job=complete_finished_games completed=1 durationMs=\\d+$").matches(lines[1]), lines[1])
    }

    @Test fun `monthly charges cron logs the run with zero and then the generated charge ids`() {
        val member = UUID.randomUUID()
        val generated = mutableListOf<Charge>()
        val repository = object : ChargeTransactionRepository {
            override fun createGameCharge(input: GameChargeInput, actorId: UUID, now: Instant): Charge = error("unused")
            override fun reconcileGameCancellation(groupId: UUID, gameId: UUID, actorId: UUID, now: Instant) = Unit
            override fun members(groupId: UUID) = GroupMembers(setOf(member), setOf(member))
            override fun createMonthlyCharge(command: MonthlyGenerationCommand, memberId: UUID, now: Instant): Charge =
                Charge(UUID.randomUUID(), group, member, ChargeIdentity.Monthly(command.month), 100, command.dueDate).also { generated += it }
        }
        val runner = object : TransactionRunner { override fun <T> inTransaction(block: () -> T): T = block() }
        val charges = ChargeTransactions(runner, repository, { Instant.parse("2026-10-13T12:00:00Z") }, GroupWriteAccess { true })
        val due = mutableListOf<MonthlyDueMembership>()
        val job = MonthlyChargeJob(MonthlyChargeSchedule(MonthlyDueMembershipRepository { due }, charges, { LocalDate.of(2026, 10, 13) }) { _, _ -> })

        job.run()
        assertTrue(Regex("^job_run job=monthly_charges candidates=0 generated=0 skipped=0 durationMs=\\d+$").matches(lines().single()), lines().toString())

        appender.list.clear()
        due += MonthlyDueMembership(group, owner, member, 100, 10, MonthlyLateJoinPolicy.GROUP_DUE_DATE, null)
        job.run()

        val lines = lines()
        assertEquals("job_item job=monthly_charges event=charge_generated groupId=$group memberId=$member chargeId=${generated.single().id}", lines[0])
        assertTrue(Regex("^job_run job=monthly_charges candidates=1 generated=1 skipped=0 durationMs=\\d+$").matches(lines[1]), lines[1])
    }

    @Test fun `no job line carries a name, phone, email, message text or token`() {
        game(starts = "now() + interval '5 hours'")
        reminder.run()
        NotificationPushWorker(push, NotificationPushSender { _, _ -> PushDelivery.SENT }).run()
        game(starts = "now() - interval '3 hours'", deadline = "now() - interval '4 hours'")
        CompleteFinishedGamesJob(CompleteFinishedGames(JdbcFinishedGamesRepository(source), Clock.systemUTC())).run()
        NotificationPushWorker(push, NotificationPushSender { _, _ -> PushDelivery.RETRY }).run()

        val text = lines().joinToString("\n")
        assertTrue(lines().size >= 4, text)
        val body = jdbc.sql("SELECT body FROM group_messages WHERE channel = 'REMINDER'").query(String::class.java).single()
        for (secret in listOf(ownerName, athleteName, athletePhone, athleteEmail, "owner@example.com", groupName, deviceToken, "Arena", body.lines().first())) {
            assertFalse(secret in text, "$secret apareceu no log:\n$text")
        }
        assertNotNull(appender.list.firstOrNull())
    }

    private fun game(starts: String, deadline: String = "now() + interval '2 hours'"): UUID {
        val id = UUID.randomUUID()
        jdbc.sql("""
            INSERT INTO games(id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes,
                confirmation_deadline, venue_name, venue_address, capacity, status, created_at, updated_at)
            VALUES (:id, :g, 'Vôlei de Quinta', current_date + 1, '19:00', 'America/Sao_Paulo', $starts, 90,
                $deadline, 'Arena', 'Rua 100', 12, 'PUBLISHED', now(), now())
        """).param("id", id).param("g", group).update()
        return id
    }

    private fun user(name: String, email: String, phone: String): UUID = UUID.randomUUID().also { id ->
        jdbc.sql("""INSERT INTO access_users(id, firebase_subject, email, email_verified, display_name, phone, created_at, updated_at)
            VALUES (:id, :subject, :email, true, :name, :phone, now(), now())""")
            .param("id", id).param("subject", id.toString()).param("email", email).param("name", name).param("phone", phone).update()
    }
}
