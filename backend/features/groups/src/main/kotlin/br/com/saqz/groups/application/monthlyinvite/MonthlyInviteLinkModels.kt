package br.com.saqz.groups.application.monthlyinvite

import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Estado do link de mensalistas visto pelo dono e pelos admins (CONV-011). */
enum class MonthlyInviteLinkState { NONE, ACTIVE, EXPIRED, INVALIDATED }

/** Link de mensalistas guardado: o código é nulo depois de invalidado. */
data class MonthlyInviteLink(
    val groupId: UUID,
    val shortCode: String?,
    val expiresAt: Instant,
    val invalidatedAt: Instant?,
) {
    fun state(now: Instant): MonthlyInviteLinkState = when {
        invalidatedAt != null || shortCode == null -> MonthlyInviteLinkState.INVALIDATED
        !now.isBefore(expiresAt) -> MonthlyInviteLinkState.EXPIRED
        else -> MonthlyInviteLinkState.ACTIVE
    }

    companion object {
        /** Vale 7 dias a partir do último envio (lote, reenviar, reenviar para todos, copiar o link). */
        val VALIDITY: Duration = Duration.ofDays(7)
    }
}

interface MonthlyInviteLinkRepository {
    /** Trava o grupo (vivo) e devolve o link; erro se o grupo foi excluído. */
    fun lockAndFind(groupId: UUID): MonthlyInviteLink?

    fun find(groupId: UUID): MonthlyInviteLink?

    /** Grava um código novo (cria a linha ou troca o código morto) e limpa a invalidação. */
    fun replaceCode(groupId: UUID, shortCode: String, expiresAt: Instant, createdByUserId: UUID, now: Instant)

    fun renew(groupId: UUID, expiresAt: Instant, now: Instant)

    /** Anula o código e marca a invalidação; a linha fica para a retenção. */
    fun invalidate(groupId: UUID, now: Instant)

    /** True se o código existe no link de mensalistas ou no convite público. */
    fun isShortCodeTaken(shortCode: String): Boolean
}
