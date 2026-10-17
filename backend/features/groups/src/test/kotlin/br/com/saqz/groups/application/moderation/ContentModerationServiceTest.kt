package br.com.saqz.groups.application.moderation

import br.com.saqz.groups.application.create.TransactionRunner
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class ContentModerationServiceTest {
    private val reporter = UUID.randomUUID()
    private val group = UUID.randomUUID()
    private val target = UUID.randomUUID()
    private val repository = MemoryRepository()
    private val service = ContentModerationService(
        object : TransactionRunner { override fun <T> inTransaction(block: () -> T) = block() },
        repository,
    )

    @Test fun `the same report sent again is one record and the service still answers success`() {
        assertEquals(ModerationResult.Success, service.report(reporter, request()))
        assertEquals(ModerationResult.Success, service.report(reporter, request()))
        assertEquals(ModerationResult.Success, service.report(reporter, request(details = "agora com detalhe")))
        assertEquals(1, repository.saved.size)
    }

    @Test fun `another reason, another target or another reporter is a new report`() {
        service.report(reporter, request())
        service.report(reporter, request(reason = ReportReason.SPAM))
        service.report(reporter, request(targetId = UUID.randomUUID()))
        service.report(UUID.randomUUID(), request())
        assertEquals(4, repository.saved.size)
    }

    private fun request(
        reason: ReportReason = ReportReason.OFFENSIVE,
        targetId: UUID = target,
        details: String? = null,
    ) = ContentReportRequest(group, ReportTarget.USER, targetId, reason, details)

    private inner class MemoryRepository : ContentModerationRepository {
        val saved = mutableListOf<NewContentReport>()
        override fun isActiveMember(groupId: UUID, userId: UUID) = true
        override fun reportedContent(groupId: UUID, target: ReportTarget, targetId: UUID) =
            ReportedContent(UUID.randomUUID(), "Fulana")
        override fun saveReport(report: NewContentReport): Boolean {
            if (saved.any { it.reporterId == report.reporterId && it.targetId == report.targetId && it.reason == report.reason }) {
                return false
            }
            saved += report
            return true
        }
        override fun block(blockerId: UUID, blockedId: UUID) = error("unused")
        override fun unblock(blockerId: UUID, blockedId: UUID) = error("unused")
        override fun blocks(blockerId: UUID) = error("unused")
        override fun pendingAlerts(limit: Int) = error("unused")
        override fun markAlerted(reportId: UUID) = error("unused")
        override fun markAlertFailed(reportId: UUID) = error("unused")
        override fun reports(openOnly: Boolean, limit: Int) = error("unused")
        override fun resolve(reportId: UUID) = error("unused")
        override fun deleteMessage(groupId: UUID, messageId: UUID) = error("unused")
    }
}
