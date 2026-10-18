package br.com.saqz.subscriptions.application

import br.com.saqz.sharedkernel.job.JobLog
import br.com.saqz.sharedkernel.subscription.TrialEndingNotice
import br.com.saqz.sharedkernel.subscription.TrialUsage
import br.com.saqz.sharedkernel.subscription.TrialUsageLookup
import br.com.saqz.subscriptions.domain.OrganizerTrial
import java.text.NumberFormat
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

/**
 * Marcos do aviso, em dias de calendário que faltam para o fim do teste: faltam 3, falta 1, o dia do fim e, depois
 * dele, -3 e -14 (3 e 14 dias depois, DEB-078). O mais apertado de uma rodada é sempre o menor.
 */
internal val TRIAL_ENDING_MILESTONES = listOf(3, 1, 0, -3, -14)

/** Quantos dias depois do fim o teste ainda pode receber o último aviso (o marco -14 e um dia de folga). */
internal const val TRIAL_ENDED_WINDOW_DAYS = 15L

interface TrialEndingRepository {
    /** Testes que terminam em até [ahead] e os que terminaram há até [behind] (teste que já acabou também recebe aviso). */
    fun around(now: java.time.Instant, ahead: Duration, behind: Duration): List<OrganizerTrial>

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

/** Link para assinar que vai no corpo da central e do WhatsApp; o push curto abre o app na tela de planos. */
const val SUBSCRIBE_URL = "https://saqz.app/assinar"

/** Texto da central e do WhatsApp ([body]) e o push curto ([push]) de um marco. */
data class TrialEndingMessage(val body: String, val push: String)

/**
 * Job diário: a 3 dias, a 1 dia, no dia do fim do teste e 3 e 14 dias depois, quem ainda não assinou recebe, uma vez
 * por marco, o aviso na central, no push e no WhatsApp. Número e horário calculados aqui, no backend.
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
        trials.around(now, Duration.ofDays(TRIAL_ENDING_MILESTONES.max() + 1L), Duration.ofDays(TRIAL_ENDED_WINDOW_DAYS + 1)).forEach { trial ->
            val daysLeft = trialDaysLeft(now, trial.endsAt, zone)
            if (daysLeft < -TRIAL_ENDED_WINDOW_DAYS) return@forEach
            // Depois do fim só valem os marcos de depois do fim: "termina hoje" não sai com o teste já acabado.
            val due = TRIAL_ENDING_MILESTONES.filter { daysLeft <= it && (it < 0 || trial.endsAt > now) }
            if (due.isEmpty() || paid.findEntitlingPlan(trial.ownerUserId) != null) return@forEach
            if (!trials.claim(trial.ownerUserId, due)) return@forEach
            val milestone = due.min()
            val message = trialEndingMessage(milestone, daysLeft, trial.endsAt, usage.usageOf(trial.ownerUserId, trial.startedAt, now), zone)
            val published = runCatching { notice.publish(trial.ownerUserId, message.body, message.push) }.getOrDefault(false)
            if (!published) {
                trials.release(trial.ownerUserId, milestone)
                JobLog.itemFailed("trial_ending", "reminder_not_published", "NOT_PUBLISHED", "userId" to trial.ownerUserId, "milestone" to milestone)
                return@forEach
            }
            runCatching { whatsApp.send(trial.ownerUserId, message.body) }
            JobLog.item("trial_ending", "reminder_sent", "userId" to trial.ownerUserId, "milestone" to milestone)
            sent++
        }
        return sent
    }
}

/**
 * Aviso do [milestone] (o menor marco devido). [daysLeft] só entra no "faltam N dias" do primeiro, para a rodada perdida
 * não dizer 3 quando faltam 2. Sem jogo marcado, o resumo do primeiro aviso vira o convite para criar o primeiro.
 */
fun trialEndingMessage(milestone: Int, daysLeft: Int, endsAt: Instant, usage: TrialUsage, zone: ZoneId = TRIAL_ZONE): TrialEndingMessage {
    val hour = formatClock(endsAt, zone)
    return when {
        milestone >= 3 -> {
            val summary = if (usage.games == 0) "Você ainda não marcou nenhum jogo. Crie o primeiro em 1 minuto e veja a lista da galera se fechando."
            else "Até aqui, ${trialUsageSummary(usage)}."
            TrialEndingMessage(
                "Faltam $daysLeft dias do seu teste do Saqz. $summary Para a galera continuar confirmando presença, " +
                    "assine até ${formatDay(endsAt, zone)}, a partir de R\$ 39,90 por mês. Assinar agora: $SUBSCRIBE_URL",
                "Faltam $daysLeft dias do seu teste. Veja como seguir com a galera.",
            )
        }
        milestone == 1 -> TrialEndingMessage(
            "Seu teste termina amanhã, às $hour. Depois disso você não marca jogos e a galera não confirma presença. " +
                "Seus grupos e o histórico ficam guardados, e voltam na hora quando você assina. Assinar agora: $SUBSCRIBE_URL",
            "Seu teste termina amanhã. Assine para a galera não ficar sem lista.",
        )
        milestone == 0 -> TrialEndingMessage(
            "Seu teste termina hoje, às $hour. Assine agora para a galera continuar confirmando presença sem parar. " +
                "Tudo o que você montou fica guardado. Assinar agora: $SUBSCRIBE_URL",
            "Seu teste termina hoje às $hour. Toque para assinar.",
        )
        milestone == -3 -> afterTheEnd("Sua galera está esperando: seus grupos estão congelados. Assine para voltar de onde parou.")
        else -> afterTheEnd("Seus grupos e o histórico seguem guardados. Quando quiser voltar, é só assinar.")
    }
}

/** DEB-078: o push é o próprio texto, sem o link (o toque abre a tela de planos). */
private fun afterTheEnd(text: String) = TrialEndingMessage("$text Assinar agora: $SUBSCRIBE_URL", text)

/** "3 jogos organizados, 12 atletas confirmados e R$ 450,00 no caixa". */
fun trialUsageSummary(usage: TrialUsage): String {
    val games = if (usage.games == 1) "1 jogo organizado" else "${usage.games} jogos organizados"
    val athletes = if (usage.confirmedAthletes == 1) "1 atleta confirmado" else "${usage.confirmedAthletes} atletas confirmados"
    return "$games, $athletes e ${formatBrl(usage.cashCents)} no caixa"
}

/** "12h" ou "12h30", no fuso de São Paulo. */
internal fun formatClock(at: Instant, zone: ZoneId): String {
    val time = at.atZone(zone)
    return if (time.minute == 0) "${time.hour}h" else "%dh%02d".format(time.hour, time.minute)
}

/** "15/10". */
internal fun formatDay(at: Instant, zone: ZoneId): String = at.atZone(zone).let { "%02d/%02d".format(it.dayOfMonth, it.monthValue) }

internal fun formatBrl(cents: Long): String =
    NumberFormat.getCurrencyInstance(Locale.of("pt", "BR")).format(cents / 100.0).replace('\u00a0', ' ')
