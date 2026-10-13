package br.com.saqz.subscriptions.application

import br.com.saqz.sharedkernel.subscription.TrialEndingNotice
import br.com.saqz.sharedkernel.subscription.TrialUsage
import br.com.saqz.sharedkernel.subscription.TrialUsageLookup
import br.com.saqz.subscriptions.domain.OrganizerTrial
import java.text.NumberFormat
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

/** Marcos do aviso, em dias de calendário até o fim do teste: faltam 3, falta 1 e o dia do fim. */
internal val TRIAL_ENDING_MILESTONES = listOf(3, 1, 0)

interface TrialEndingRepository {
    /** Testes que ainda não acabaram e terminam em até [within]. */
    fun endingSoon(now: java.time.Instant, within: Duration): List<OrganizerTrial>

    /**
     * Reserva todos os marcos de [due] que ainda não existem. Devolve true quando o mais apertado (o menor)
     * foi reservado por esta chamada: só então o aviso sai. Uma chamada repetida devolve false.
     */
    fun claim(ownerUserId: UUID, due: List<Int>): Boolean

    /** Desfaz a reserva de [milestone] quando o aviso não pôde ser publicado, para a próxima rodada tentar de novo. */
    fun release(ownerUserId: UUID, milestone: Int)
}

/** WhatsApp particular do dono; não faz nada sem WhatsApp ligado ou sem telefone. */
fun interface TrialEndingWhatsApp {
    fun send(ownerUserId: UUID, body: String)
}

/**
 * Job diário: a 3 dias, a 1 dia e no dia do fim do teste do Organizador, quem ainda não assinou recebe, uma vez
 * por marco, a contagem e o uso real do teste na central, no push e no WhatsApp. Número calculado aqui, no backend.
 */
class TrialEndingReminders(
    private val trials: TrialEndingRepository,
    private val paid: SubscriptionPlanLookup,
    private val usage: TrialUsageLookup,
    private val notice: TrialEndingNotice,
    private val whatsApp: TrialEndingWhatsApp,
    private val clock: Clock,
    private val zone: ZoneId = TRIAL_ZONE,
) {
    /** Devolve quantos avisos saíram nesta rodada. */
    fun run(): Int {
        val now = clock.instant()
        var sent = 0
        trials.endingSoon(now, Duration.ofDays(TRIAL_ENDING_MILESTONES.max() + 1L)).forEach { trial ->
            val daysLeft = trialDaysLeft(now, trial.endsAt, zone)
            val due = TRIAL_ENDING_MILESTONES.filter { daysLeft <= it }
            if (due.isEmpty() || paid.findEntitlingPlan(trial.ownerUserId) != null) return@forEach
            if (!trials.claim(trial.ownerUserId, due)) return@forEach
            val body = trialEndingBody(daysLeft, usage.usageOf(trial.ownerUserId, trial.startedAt, now))
            val published = runCatching { notice.publish(trial.ownerUserId, body) }.getOrDefault(false)
            if (!published) {
                trials.release(trial.ownerUserId, due.min())
                return@forEach
            }
            runCatching { whatsApp.send(trial.ownerUserId, body) }
            sent++
        }
        return sent
    }
}

fun trialEndingBody(daysLeft: Int, usage: TrialUsage): String {
    val countdown = when (daysLeft) {
        0 -> "Hoje é o último dia do seu teste do Organizador."
        1 -> "Falta 1 dia do seu teste do Organizador."
        else -> "Faltam $daysLeft dias do seu teste do Organizador."
    }
    val summary = if (usage.isEmpty) "" else " Até aqui: ${trialUsageSummary(usage)}."
    return "$countdown$summary Assine para continuar marcando jogos."
}

/** "3 jogos, 12 atletas confirmados, R$ 450,00 no caixa". */
fun trialUsageSummary(usage: TrialUsage): String {
    val games = if (usage.games == 1) "1 jogo" else "${usage.games} jogos"
    val athletes = if (usage.confirmedAthletes == 1) "1 atleta confirmado" else "${usage.confirmedAthletes} atletas confirmados"
    return "$games, $athletes, ${formatBrl(usage.cashCents)} no caixa"
}

internal fun formatBrl(cents: Long): String =
    NumberFormat.getCurrencyInstance(Locale.of("pt", "BR")).format(cents / 100.0).replace(' ', ' ')
