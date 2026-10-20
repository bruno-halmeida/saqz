package br.com.saqz.groups.application.monthlyinvite

import br.com.saqz.groups.application.invite.InviteCode
import br.com.saqz.groups.application.invite.InviteLinkFactory
import br.com.saqz.groups.application.invite.SecureTokenGenerator
import br.com.saqz.groups.application.invite.manage.newUniqueCode
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class RenewedMonthlyInviteLink(
    val code: InviteCode,
    val url: URI,
    val expiresAt: Instant,
    /** True quando nasceu um código (primeiro envio, ou o anterior tinha vencido ou sido invalidado). */
    val newCode: Boolean,
)

/**
 * Cada envio conta como uso do link (CONV-011): código vivo ganha mais 7 dias; sem link, vencido ou invalidado,
 * nasce um código novo e o velho fica morto. Roda dentro da transação de quem chama, que já autorizou o ator.
 */
class GetOrRenewMonthlyInviteLink(
    private val repository: MonthlyInviteLinkRepository,
    private val tokenGenerator: SecureTokenGenerator,
    private val linkFactory: InviteLinkFactory,
    private val clock: Clock,
) {
    fun execute(groupId: UUID, actor: UUID): RenewedMonthlyInviteLink {
        val now = clock.instant()
        val expiresAt = now.plus(MonthlyInviteLink.VALIDITY)
        val current = repository.lockAndFind(groupId)
        if (current != null && current.state(now) == MonthlyInviteLinkState.ACTIVE) {
            repository.renew(groupId, expiresAt, now)
            val code = InviteCode.fromShort(requireNotNull(current.shortCode))
            return RenewedMonthlyInviteLink(code, linkFactory.create(code), expiresAt, newCode = false)
        }
        val code = newUniqueCode(tokenGenerator, repository::isShortCodeTaken)
        repository.replaceCode(groupId, code.value, expiresAt, actor, now)
        return RenewedMonthlyInviteLink(code, linkFactory.create(code), expiresAt, newCode = true)
    }
}
