package br.com.saqz.groups.application.monthlyinvite

import java.time.Clock
import java.time.Duration
import java.time.Instant

interface MonthlyInviteRetentionRepository {
    /** Apaga as linhas de convidados dos grupos cujo link venceu ou foi invalidado antes de [cutoff]; devolve quantas. */
    fun deleteInviteesOfLinksDeadBefore(cutoff: Instant): Int
}

/**
 * Retenção (CONV-010, LGPD): nome e telefone de quem não entrou somem 30 dias depois de o link vencer ou ser
 * invalidado. Enquanto o link for renovado por novos lotes, as linhas ficam.
 */
class PurgeMonthlyInvitees(
    private val repository: MonthlyInviteRetentionRepository,
    private val clock: Clock,
) {
    fun run(): Int = repository.deleteInviteesOfLinksDeadBefore(clock.instant().minus(RETENTION))

    companion object {
        val RETENTION: Duration = Duration.ofDays(30)
    }
}
