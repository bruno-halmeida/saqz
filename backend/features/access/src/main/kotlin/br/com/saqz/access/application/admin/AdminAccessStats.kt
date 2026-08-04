package br.com.saqz.access.application.admin

import java.time.Instant
import java.time.LocalDate

data class CohortWeek(
    val weekStart: LocalDate,
    val signups: Long,
    val joinedGroup: Long,
)

/**
 * Contagens administrativas do domínio de acesso (adm-web · visão geral).
 * Janelas são [from, to); from null significa "desde o início".
 */
interface AdminAccessStats {
    fun totalUsers(): Long

    fun newUsers(from: Instant?, to: Instant): Long

    /** Proxy de atividade: o bootstrap de sessão faz upsert e bumpa updated_at. */
    fun activeUsers(since: Instant): Long

    /** Semanas ISO (segunda-feira), da mais antiga para a mais recente, terminando na semana de [now]. */
    fun signupCohort(weeksBack: Int, now: Instant): List<CohortWeek>
}
