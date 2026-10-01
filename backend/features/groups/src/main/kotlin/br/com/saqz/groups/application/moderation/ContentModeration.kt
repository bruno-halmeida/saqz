package br.com.saqz.groups.application.moderation

import java.time.Instant
import java.util.UUID

/** O que pode ser denunciado. MESSAGE cobre só avisos e mensagens escritos por pessoas. */
enum class ReportTarget { USER, GROUP, MESSAGE }

/** BLOCKED não vem do app: é a denúncia que todo bloqueio gera para a equipe analisar. */
enum class ReportReason { SPAM, OFFENSIVE, HARASSMENT, OTHER, BLOCKED }

data class ContentReportRequest(
    val groupId: UUID,
    val target: ReportTarget,
    val targetId: UUID,
    val reason: ReportReason,
    val details: String?,
)

/** O conteúdo denunciado como estava na hora da denúncia, e quem responde por ele. */
data class ReportedContent(val responsibleUserId: UUID?, val excerpt: String?)

data class NewContentReport(
    val id: UUID,
    val reporterId: UUID,
    val groupId: UUID,
    val target: ReportTarget,
    val targetId: UUID,
    val content: ReportedContent,
    val reason: ReportReason,
    val details: String?,
)

data class BlockedUser(val userId: UUID, val displayName: String, val blockedAt: Instant)

/** Denúncia pronta para virar e-mail da equipe, com os nomes resolvidos. */
data class ContentReportAlert(
    val id: UUID,
    val createdAt: Instant,
    val reason: ReportReason,
    val target: ReportTarget,
    val targetId: UUID,
    val groupId: UUID,
    val groupName: String,
    val reporterId: UUID,
    val reporterName: String,
    val responsibleUserId: UUID?,
    val responsibleName: String?,
    val excerpt: String?,
    val details: String?,
)

data class ContentReportSummary(
    val id: UUID,
    val createdAt: Instant,
    val reason: ReportReason,
    val target: ReportTarget,
    val targetId: UUID,
    val groupId: UUID,
    val groupName: String,
    val reporterId: UUID,
    val responsibleUserId: UUID?,
    val responsibleName: String?,
    val excerpt: String?,
    val details: String?,
    val resolvedAt: Instant?,
)

enum class ModerationError { NOT_FOUND, INVALID }

sealed interface ModerationResult {
    data object Success : ModerationResult
    data class Failure(val reason: ModerationError) : ModerationResult
}

interface ContentModerationRepository {
    fun isActiveMember(groupId: UUID, userId: UUID): Boolean

    /** null quando o alvo não existe naquele grupo. */
    fun reportedContent(groupId: UUID, target: ReportTarget, targetId: UUID): ReportedContent?
    fun saveReport(report: NewContentReport)

    /** Idempotente. true só quando o bloqueio é novo. */
    fun block(blockerId: UUID, blockedId: UUID): Boolean
    fun unblock(blockerId: UUID, blockedId: UUID)
    fun blocks(blockerId: UUID): List<BlockedUser>

    fun pendingAlerts(limit: Int): List<ContentReportAlert>
    fun markAlerted(reportId: UUID)
    fun markAlertFailed(reportId: UUID)

    fun reports(openOnly: Boolean, limit: Int): List<ContentReportSummary>
    fun resolve(reportId: UUID): Boolean

    /** Remove um aviso/mensagem e tudo o que foi entregue a partir dele. */
    fun deleteMessage(groupId: UUID, messageId: UUID): Boolean
}

fun interface ContentReportAlertSender {
    fun send(alert: ContentReportAlert)
}
