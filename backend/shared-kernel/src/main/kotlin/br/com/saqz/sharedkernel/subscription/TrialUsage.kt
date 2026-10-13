package br.com.saqz.sharedkernel.subscription

import java.time.Instant
import java.util.UUID

/** O que o Organizador já fez durante o teste: jogos publicados ou encerrados, atletas que confirmaram e entradas no caixa. */
data class TrialUsage(val games: Int, val confirmedAthletes: Int, val cashCents: Long) {
    val isEmpty: Boolean get() = games == 0 && confirmedAthletes == 0 && cashCents == 0L
}

/** Implementado por :features:groups (jogos, presenças e caixa vivem lá); consumido pelo teste e pelo aviso de fim de teste. */
fun interface TrialUsageLookup {
    fun usageOf(ownerId: UUID, since: Instant, until: Instant): TrialUsage
}

/**
 * Publica o aviso de fim de teste na central e no push do dono (canal TRIAL_ENDING). Devolve false quando o
 * dono não tem grupo ativo em que o aviso possa morar.
 */
fun interface TrialEndingNotice {
    fun publish(ownerId: UUID, body: String): Boolean
}
