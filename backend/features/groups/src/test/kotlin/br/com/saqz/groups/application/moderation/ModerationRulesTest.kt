package br.com.saqz.groups.application.moderation

import br.com.saqz.groups.application.create.TransactionRunner
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Denúncia e bloqueio (Apple 1.2): quem pode denunciar, o que é recusado e o que a equipe recebe. */
class ModerationRulesTest {
    private val actor = UUID.randomUUID()
    private val group = UUID.randomUUID()
    private val target = UUID.randomUUID()
    private val repository = FakeRepository()
    private val ids = generateSequence(1L) { it + 1 }.map { UUID(0, it) }.iterator()
    private val service = ContentModerationService(
        object : TransactionRunner { override fun <T> inTransaction(block: () -> T) = block() },
        repository,
    ) { ids.next() }

    private fun request(
        reason: ReportReason = ReportReason.SPAM,
        targetKind: ReportTarget = ReportTarget.USER,
        targetId: UUID = target,
        details: String? = null,
    ) = ContentReportRequest(group, targetKind, targetId, reason, details)

    private fun invalid() = ModerationResult.Failure(ModerationError.INVALID)
    private fun notFound() = ModerationResult.Failure(ModerationError.NOT_FOUND)

    @Test fun `BLOCKED não vem do app, só o bloqueio gera essa denúncia`() {
        assertEquals(invalid(), service.report(actor, request(reason = ReportReason.BLOCKED)))
        assertTrue(repository.saved.isEmpty())
    }

    @Test fun `detalhes são aparados, vazio vira nenhum e o limite é 1000 caracteres`() {
        service.report(actor, request(details = "  motivo  "))
        service.report(actor, request(reason = ReportReason.OFFENSIVE, details = "   "))
        assertEquals(listOf("motivo", null), repository.saved.map { it.details })

        assertEquals(ModerationResult.Success, service.report(actor, request(reason = ReportReason.HARASSMENT, details = "a".repeat(1000))))
        assertEquals(invalid(), service.report(actor, request(reason = ReportReason.OTHER, details = "a".repeat(1001))))
        assertEquals(3, repository.saved.size)
    }

    @Test fun `caractere de controle nos detalhes é recusado, quebra de linha não`() {
        assertEquals(invalid(), service.report(actor, request(details = "a\u0000b")))
        assertEquals(ModerationResult.Success, service.report(actor, request(details = "linha 1\nlinha 2\tfim\r")))
        assertEquals(1, repository.saved.size)
    }

    @Test fun `denunciar o grupo só vale para o próprio grupo`() {
        assertEquals(invalid(), service.report(actor, request(targetKind = ReportTarget.GROUP, targetId = UUID.randomUUID())))
        assertEquals(ModerationResult.Success, service.report(actor, request(targetKind = ReportTarget.GROUP, targetId = group)))
    }

    @Test fun `quem não está ativo no grupo não denuncia nem descobre se o alvo existe`() {
        repository.member = false
        assertEquals(notFound(), service.report(actor, request()))
        assertTrue(repository.saved.isEmpty())
    }

    @Test fun `alvo que não existe naquele grupo dá não encontrado`() {
        repository.content = null
        assertEquals(notFound(), service.report(actor, request()))
        assertTrue(repository.saved.isEmpty())
    }

    @Test fun `ninguém denuncia a si mesmo, mas pode denunciar o grupo ou conteúdo sem autor`() {
        repository.content = ReportedContent(actor, "texto")
        assertEquals(invalid(), service.report(actor, request(targetKind = ReportTarget.MESSAGE)))
        assertEquals(invalid(), service.report(actor, request(targetKind = ReportTarget.USER)))
        assertEquals(ModerationResult.Success, service.report(actor, request(targetKind = ReportTarget.GROUP, targetId = group)))
        repository.content = ReportedContent(null, "texto")
        assertEquals(ModerationResult.Success, service.report(actor, request(targetKind = ReportTarget.MESSAGE)))
        assertEquals(2, repository.saved.size)
    }

    @Test fun `a denúncia gravada leva autor, grupo, alvo, conteúdo da hora, motivo e id gerado`() {
        val content = ReportedContent(UUID.randomUUID(), "trecho")
        repository.content = content
        service.report(actor, request(reason = ReportReason.OFFENSIVE, targetKind = ReportTarget.MESSAGE, details = "grosseria"))
        assertEquals(
            NewContentReport(UUID(0, 1), actor, group, ReportTarget.MESSAGE, target, content, ReportReason.OFFENSIVE, "grosseria"),
            repository.saved.single(),
        )
    }

    @Test fun `bloquear a si mesmo é recusado e não toca em nada`() {
        assertEquals(invalid(), service.block(actor, actor, group))
        assertTrue(repository.blocked.isEmpty())
    }

    @Test fun `bloquear exige ser ativo no grupo e o bloqueado existir nele`() {
        repository.member = false
        assertEquals(notFound(), service.block(actor, target, group))
        repository.member = true
        repository.content = null
        assertEquals(notFound(), service.block(actor, target, group))
        assertTrue(repository.blocked.isEmpty())
        assertTrue(repository.saved.isEmpty())
    }

    @Test fun `bloqueio novo gera uma denúncia BLOCKED para a equipe`() {
        val content = ReportedContent(target, "Fulana")
        repository.content = content
        assertEquals(ModerationResult.Success, service.block(actor, target, group))
        assertEquals(listOf(actor to target), repository.blocked)
        assertEquals(
            NewContentReport(UUID(0, 1), actor, group, ReportTarget.USER, target, content, ReportReason.BLOCKED, null),
            repository.saved.single(),
        )
    }

    @Test fun `bloquear de novo quem já está bloqueado continua sucesso e não repete o alerta`() {
        service.block(actor, target, group)
        assertEquals(ModerationResult.Success, service.block(actor, target, group))
        assertEquals(1, repository.saved.size)
    }

    @Test fun `desbloquear, listar bloqueios, denúncias abertas e resolver vão ao repositório com os mesmos argumentos`() {
        service.unblock(actor, target)
        assertEquals(listOf(actor to target), repository.unblocked)
        assertEquals(listOf(BlockedUser(UUID(0, 7), "Fulana", Instant.EPOCH)), service.blocks(actor))
        assertEquals(listOf(summary), service.reports(openOnly = true, limit = 30))
        assertEquals(listOf(true to 30), repository.reportQueries)
        assertEquals(true, service.resolve(UUID(0, 9)))
        assertEquals(false, service.resolve(UUID(0, 10)))
        assertEquals(listOf(UUID(0, 9), UUID(0, 10)), repository.resolved)
        assertEquals(true, service.deleteMessage(group, UUID(0, 5)))
        assertEquals(false, service.deleteMessage(group, UUID(0, 6)))
        assertEquals(listOf(group to UUID(0, 5), group to UUID(0, 6)), repository.deleted)
    }

    @Test fun `o alerta pendente sai por e-mail, é marcado enviado e a falha de um não segura os outros`() {
        val ok1 = alert(UUID(0, 1)); val broken = alert(UUID(0, 2)); val ok2 = alert(UUID(0, 3))
        repository.pending = listOf(ok1, broken, ok2)
        val sent = mutableListOf<UUID>()
        val outcome = service.dispatchAlerts { if (it.id == broken.id) error("smtp fora") else sent += it.id }
        assertEquals(AlertDispatch(sent = 2, failed = 1), outcome)
        assertEquals(listOf(ok1.id, ok2.id), sent)
        assertEquals(listOf(ok1.id, ok2.id), repository.alerted)
        assertEquals(listOf(broken.id), repository.alertFailed)
        assertEquals(20, repository.pendingLimit)
    }

    @Test fun `sem alertas pendentes nada é enviado`() {
        assertEquals(AlertDispatch(0, 0), service.dispatchAlerts { error("não deveria enviar") })
    }

    private val summary = ContentReportSummary(
        UUID(0, 1), Instant.EPOCH, ReportReason.SPAM, ReportTarget.USER, target, group, "Grupo", actor, null, null, null, null, null,
    )

    private fun alert(id: UUID) = ContentReportAlert(
        id, Instant.EPOCH, ReportReason.SPAM, ReportTarget.USER, target, group, "Grupo", actor, "Ana", null, null, null, null,
    )

    private inner class FakeRepository : ContentModerationRepository {
        var member = true
        var content: ReportedContent? = ReportedContent(UUID.randomUUID(), "Fulana")
        val saved = mutableListOf<NewContentReport>()
        val blocked = mutableListOf<Pair<UUID, UUID>>()
        val unblocked = mutableListOf<Pair<UUID, UUID>>()
        val resolved = mutableListOf<UUID>()
        val deleted = mutableListOf<Pair<UUID, UUID>>()
        val reportQueries = mutableListOf<Pair<Boolean, Int>>()
        var pending = emptyList<ContentReportAlert>()
        var pendingLimit = 0
        val alerted = mutableListOf<UUID>()
        val alertFailed = mutableListOf<UUID>()

        override fun isActiveMember(groupId: UUID, userId: UUID) = member
        override fun reportedContent(groupId: UUID, target: ReportTarget, targetId: UUID) = content
        override fun saveReport(report: NewContentReport): Boolean { saved += report; return true }
        override fun block(blockerId: UUID, blockedId: UUID): Boolean {
            if (blockerId to blockedId in blocked) return false
            blocked += blockerId to blockedId
            return true
        }
        override fun unblock(blockerId: UUID, blockedId: UUID) { unblocked += blockerId to blockedId }
        override fun blocks(blockerId: UUID) = listOf(BlockedUser(UUID(0, 7), "Fulana", Instant.EPOCH))
        override fun pendingAlerts(limit: Int): List<ContentReportAlert> { pendingLimit = limit; return pending }
        override fun markAlerted(reportId: UUID) { alerted += reportId }
        override fun markAlertFailed(reportId: UUID) { alertFailed += reportId }
        override fun reports(openOnly: Boolean, limit: Int): List<ContentReportSummary> { reportQueries += openOnly to limit; return listOf(summary) }
        override fun resolve(reportId: UUID): Boolean { resolved += reportId; return reportId == UUID(0, 9) }
        override fun deleteMessage(groupId: UUID, messageId: UUID): Boolean { deleted += groupId to messageId; return messageId == UUID(0, 5) }
    }
}
