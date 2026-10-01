package br.com.saqz.groups.application.moderation

import br.com.saqz.groups.application.create.TransactionRunner
import java.util.UUID

/**
 * Denúncia e bloqueio (Apple 1.2). Só quem está ativo no grupo denuncia ou bloqueia a partir
 * dele, e o alvo precisa estar no mesmo grupo — o id sozinho não basta, senão o endpoint vira
 * oráculo de contas. Toda denúncia, e todo bloqueio novo, vira e-mail para a equipe pelo
 * [dispatchAlerts], que roda fora da requisição e tenta de novo se o SMTP falhar.
 */
class ContentModerationService(
    private val transaction: TransactionRunner,
    private val repository: ContentModerationRepository,
    private val newId: () -> UUID = UUID::randomUUID,
) {
    fun report(actor: UUID, request: ContentReportRequest): ModerationResult {
        if (request.reason == ReportReason.BLOCKED) return invalid()
        val details = request.details?.trim()?.ifEmpty { null }
        if (details != null && (details.length > MAX_DETAILS || details.any { it.isISOControl() && it !in "\n\t\r" })) {
            return invalid()
        }
        if (request.target == ReportTarget.GROUP && request.targetId != request.groupId) return invalid()
        return transaction.inTransaction {
            if (!repository.isActiveMember(request.groupId, actor)) return@inTransaction notFound()
            val content = repository.reportedContent(request.groupId, request.target, request.targetId)
                ?: return@inTransaction notFound()
            if (request.target != ReportTarget.GROUP && content.responsibleUserId == actor) return@inTransaction invalid()
            repository.saveReport(NewContentReport(
                newId(), actor, request.groupId, request.target, request.targetId, content, request.reason, details,
            ))
            ModerationResult.Success
        }
    }

    fun block(actor: UUID, blockedId: UUID, groupId: UUID): ModerationResult {
        if (blockedId == actor) return invalid()
        return transaction.inTransaction {
            if (!repository.isActiveMember(groupId, actor)) return@inTransaction notFound()
            val content = repository.reportedContent(groupId, ReportTarget.USER, blockedId)
                ?: return@inTransaction notFound()
            if (repository.block(actor, blockedId)) {
                repository.saveReport(NewContentReport(
                    newId(), actor, groupId, ReportTarget.USER, blockedId, content, ReportReason.BLOCKED, null,
                ))
            }
            ModerationResult.Success
        }
    }

    fun unblock(actor: UUID, blockedId: UUID) = repository.unblock(actor, blockedId)

    fun blocks(actor: UUID): List<BlockedUser> = repository.blocks(actor)

    /** Envia os alertas pendentes; cada um é independente, então uma falha não segura os outros. */
    fun dispatchAlerts(sender: ContentReportAlertSender): AlertDispatch {
        var sent = 0
        var failed = 0
        repository.pendingAlerts(ALERT_BATCH).forEach { alert ->
            val delivered = runCatching { sender.send(alert) }.isSuccess
            if (delivered) {
                repository.markAlerted(alert.id)
                sent++
            } else {
                repository.markAlertFailed(alert.id)
                failed++
            }
        }
        return AlertDispatch(sent, failed)
    }

    fun reports(openOnly: Boolean, limit: Int) = repository.reports(openOnly, limit)
    fun resolve(reportId: UUID) = repository.resolve(reportId)
    fun deleteMessage(groupId: UUID, messageId: UUID) = transaction.inTransaction { repository.deleteMessage(groupId, messageId) }

    private fun invalid() = ModerationResult.Failure(ModerationError.INVALID)
    private fun notFound() = ModerationResult.Failure(ModerationError.NOT_FOUND)

    private companion object {
        const val MAX_DETAILS = 1000
        const val ALERT_BATCH = 20
    }
}

data class AlertDispatch(val sent: Int, val failed: Int)
