package br.com.saqz.groups.adapter.output.jdbc.moderation

import br.com.saqz.groups.adapter.output.jdbc.communication.JdbcGroupCommunicationRepository
import br.com.saqz.groups.adapter.output.jdbc.communication.JdbcNotificationPush
import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.communication.CommunicationError
import br.com.saqz.groups.application.communication.CommunicationResult
import br.com.saqz.groups.application.communication.GroupCommunicationService
import br.com.saqz.groups.application.communication.GroupMessage
import br.com.saqz.groups.application.communication.MessageChannel
import br.com.saqz.groups.application.moderation.ContentModerationService
import br.com.saqz.groups.application.moderation.ContentReportAlert
import br.com.saqz.groups.application.moderation.ContentReportRequest
import br.com.saqz.groups.application.moderation.ModerationError
import br.com.saqz.groups.application.moderation.ModerationResult
import br.com.saqz.groups.application.moderation.ReportReason
import br.com.saqz.groups.application.moderation.ReportTarget
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ContentModerationIntegrationTest {
    private val source = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    private val jdbc = JdbcClient.create(source)
    private val transaction = JdbcTransactionRunner(source)
    private val communication = GroupCommunicationService(transaction, JdbcGroupReadRepository(source), JdbcGroupCommunicationRepository(source))
    private val moderation = ContentModerationService(transaction, JdbcContentModerationRepository(source))
    private val owner = user("Dona do Grupo")
    private val member = user("Atleta")
    private val group = group("Vôlei de Sábado", owner)

    init {
        membership(group, owner, "ADMIN")
        membership(group, member)
    }

    @Test fun `a member reports a notice and the team gets one alert with the content`() {
        val notice = notice("Treino cancelado")
        assertEquals(ModerationResult.Success, moderation.report(member, request(ReportTarget.MESSAGE, notice.id, details = "  Ofendeu a turma  ")))

        val sent = mutableListOf<ContentReportAlert>()
        assertEquals(1, moderation.dispatchAlerts { sent += it }.sent)
        assertEquals(0, moderation.dispatchAlerts { sent += it }.sent)
        val alert = sent.single()
        assertEquals(ReportReason.OFFENSIVE, alert.reason)
        assertEquals(owner, alert.responsibleUserId)
        assertEquals("Dona do Grupo", alert.responsibleName)
        assertEquals("Atleta", alert.reporterName)
        assertEquals("Vôlei de Sábado", alert.groupName)
        assertEquals("Treino cancelado", alert.excerpt)
        assertEquals("Ofendeu a turma", alert.details)
    }

    @Test fun `a failed alert stays queued and goes out on the next run`() {
        moderation.report(member, request(ReportTarget.GROUP, group))
        assertEquals(1, moderation.dispatchAlerts { error("smtp down") }.failed)
        val sent = mutableListOf<ContentReportAlert>()
        assertEquals(1, moderation.dispatchAlerts { sent += it }.sent)
        assertEquals(group, sent.single().targetId)
        assertEquals(2, jdbc.sql("SELECT alert_attempts FROM content_reports").query(Int::class.java).single())
    }

    @Test fun `reports are limited to members, to targets in that group and to other people's content`() {
        val stranger = user("Estranha")
        val otherGroup = group("Outro", stranger)
        membership(otherGroup, stranger, "ADMIN")
        val notice = notice("Aviso")
        val notFound = ModerationResult.Failure(ModerationError.NOT_FOUND)
        assertEquals(notFound, moderation.report(stranger, request(ReportTarget.MESSAGE, notice.id)))
        assertEquals(notFound, moderation.report(member, request(ReportTarget.USER, stranger)))
        assertEquals(notFound, moderation.report(member, request(ReportTarget.MESSAGE, UUID.randomUUID())))
        val invalid = ModerationResult.Failure(ModerationError.INVALID)
        assertEquals(invalid, moderation.report(owner, request(ReportTarget.MESSAGE, notice.id)))
        assertEquals(invalid, moderation.report(member, request(ReportTarget.USER, member)))
        assertEquals(invalid, moderation.report(member, request(ReportTarget.GROUP, otherGroup)))
        assertEquals(invalid, moderation.report(member, request(ReportTarget.USER, owner, details = "x".repeat(1001))))
        assertEquals(invalid, moderation.report(member, request(ReportTarget.USER, owner, reason = ReportReason.BLOCKED)))
        assertEquals(0L, count("content_reports"))
    }

    @Test fun `blocking hides the person's notices at once, stops notifications and alerts the team once`() {
        val before = notice("Antes do bloqueio")
        assertEquals(listOf(before.id), visibleNotices(member))
        assertEquals(1, inbox(member).size)

        assertEquals(ModerationResult.Success, moderation.block(member, owner, group))
        assertEquals(ModerationResult.Success, moderation.block(member, owner, group))
        assertTrue(visibleNotices(member).isEmpty())
        assertTrue(inbox(member).isEmpty())
        val after = notice("Depois do bloqueio")
        assertEquals(0, after.recipientCount)
        assertTrue(inbox(member).isEmpty())
        assertEquals(listOf(owner to "Dona do Grupo"), moderation.blocks(member).map { it.userId to it.displayName })
        assertEquals(1, jdbc.sql("SELECT count(*) FROM content_reports WHERE reason = 'BLOCKED' AND target_user_id = :owner")
            .param("owner", owner).query(Int::class.java).single())

        moderation.unblock(member, owner)
        assertEquals(listOf(after.id, before.id), visibleNotices(member))
        assertTrue(moderation.blocks(member).isEmpty())
    }

    @Test fun `only someone sharing the group can be blocked, and never yourself`() {
        val stranger = user("Estranha")
        assertEquals(ModerationResult.Failure(ModerationError.NOT_FOUND), moderation.block(member, stranger, group))
        assertEquals(ModerationResult.Failure(ModerationError.NOT_FOUND), moderation.block(stranger, owner, group))
        assertEquals(ModerationResult.Failure(ModerationError.INVALID), moderation.block(member, member, group))
        assertEquals(0L, count("user_blocks"))
    }

    @Test fun `objectionable notices are refused before publishing`() {
        assertEquals(
            CommunicationResult.Failure(CommunicationError.OBJECTIONABLE),
            communication.publish(owner, group, MessageChannel.NOTICE, UUID.randomUUID(), "Vai tomar no cu"),
        )
        assertEquals(0L, count("group_messages"))
    }

    @Test fun `the team removes a reported notice together with everything delivered from it`() {
        JdbcNotificationPush(source, transaction).register(member, UUID.randomUUID(), "member-device", "ANDROID")
        val notice = notice("Conteúdo impróprio")
        moderation.report(member, request(ReportTarget.MESSAGE, notice.id))
        assertTrue(count("notification_push_queue") > 0)

        assertTrue(moderation.deleteMessage(group, notice.id))
        assertFalse(moderation.deleteMessage(group, notice.id))
        assertEquals(0L, count("group_messages"))
        assertEquals(0L, count("group_notifications"))
        assertEquals(0L, count("notification_push_queue"))
        val report = moderation.reports(openOnly = true, limit = 10).single()
        assertEquals("Conteúdo impróprio", report.excerpt)
        assertTrue(moderation.resolve(report.id))
        assertTrue(moderation.reports(openOnly = true, limit = 10).isEmpty())
        assertEquals(1, moderation.reports(openOnly = false, limit = 10).size)
    }

    private fun request(
        target: ReportTarget,
        id: UUID,
        reason: ReportReason = ReportReason.OFFENSIVE,
        details: String? = null,
    ) = ContentReportRequest(group, target, id, reason, details)

    private fun notice(body: String): GroupMessage =
        communication.publish(owner, group, MessageChannel.NOTICE, UUID.randomUUID(), body).success()

    private fun visibleNotices(viewer: UUID) =
        communication.messages(viewer, group, MessageChannel.NOTICE, null).success().items.map { it.id }

    private fun inbox(viewer: UUID) = communication.inbox(viewer, null).success().items

    private fun <T> CommunicationResult<T>.success(): T = assertIs<CommunicationResult.Success<T>>(this).value

    private fun count(table: String) = jdbc.sql("SELECT count(*) FROM $table").query(Long::class.java).single()

    private fun user(name: String): UUID {
        val id = UUID.randomUUID()
        jdbc.sql("INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) VALUES (:id, :subject, true, :name, now(), now())")
            .param("id", id).param("subject", id.toString()).param("name", name).update()
        return id
    }

    private fun group(name: String, owner: UUID): UUID {
        val id = UUID.randomUUID()
        jdbc.sql("INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at) VALUES (:id, :owner, :key, :name, 'UTC', now(), now())")
            .param("id", id).param("owner", owner).param("key", UUID.randomUUID()).param("name", name).update()
        return id
    }

    private fun membership(group: UUID, user: UUID, role: String = "ATHLETE") {
        jdbc.sql("INSERT INTO group_memberships (group_id, user_id, role, membership_type, active, created_at, updated_at) VALUES (:group, :user, :role, 'AVULSO', true, now(), now())")
            .param("group", group).param("user", user).param("role", role, java.sql.Types.OTHER).update()
    }
}
