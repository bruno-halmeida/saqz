package br.com.saqz.groups.application.game.series

import br.com.saqz.groups.application.game.recurrence.MaterializeWeeklySeries
import br.com.saqz.sharedkernel.subscription.GameCreationHorizon
import br.com.saqz.sharedkernel.job.JobLog
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * Rotina da recorrência: jogos nascem mês a mês, conforme o plano do dono renova
 * ([GameCreationHorizon]), e o próximo de cada horário é liberado perto da data
 * ([ReleaseSeriesOccurrences]). Trial expirado sem assinatura cancela o futuro de toda série.
 * Tudo aqui é por estado, então rodar duas vezes (ou em duas instâncias) não duplica nada.
 */
class ExtendGameSeries(
    private val series: WeeklySeriesRepository,
    private val schedules: ScheduleSeriesDefaultsRepository,
    private val materialize: MaterializeWeeklySeries,
    private val boundary: ApplySeriesBoundary,
    private val scheduleSeries: ScheduleSeriesSync,
    private val horizon: GameCreationHorizon,
    private val ids: () -> java.util.UUID,
    private val clock: Clock,
    private val release: SeriesOccurrenceRelease = SeriesOccurrenceRelease.None,
    private val onFailure: (String, Throwable) -> Unit = { _, _ -> },
) {
    /** Quantas séries e quantos grupos de agenda a rodada percorreu, e quantos passos falharam. */
    data class Outcome(val series: Int, val groups: Int, val failed: Int)

    fun run(): Outcome {
        var failed = 0
        val open = series.openSeries()
        open.forEach { (groupId, lineageId) ->
            guarded("series $lineageId", groupId, failed = { failed++ }) {
                val current = series.find(groupId, lineageId) ?: return@guarded
                val today = LocalDate.ofInstant(clock.instant(), ZoneId.of(current.rule.zoneId))
                if (horizon.until(groupId) != null) {
                    materialize.execute(current.rule, today)
                } else {
                    boundary.thisAndFuture(
                        groupId, current.rule.revisionId, current.version,
                        current.rule.copy(revisionId = ids(), localStartDate = maxOf(today, current.rule.localStartDate)),
                        current.revisionNumber + 1, maxOf(today, current.rule.localStartDate), SeriesBoundaryAction.CANCEL,
                    )
                }
            }
        }
        // Cobre quem voltou a ter plano (série cancelada no fim do trial renasce) e agenda que ficou sem série.
        val scheduled = schedules.groupsWithRegularSlots()
        scheduled.forEach { groupId -> guarded("group $groupId", groupId, failed = { failed++ }) { scheduleSeries.sync(groupId) } }
        // Também as séries sem horário regular (criadas pelo editor de jogo em grupos antigos).
        guarded("release", null, failed = { failed++ }) { release.releaseAll() }
        return Outcome(open.size, scheduled.size, failed)
    }

    // Um grupo com conflito de horário não pode parar a rotina dos outros.
    private fun guarded(what: String, groupId: java.util.UUID?, failed: () -> Unit, block: () -> Unit) =
        runCatching(block).onFailure {
            failed()
            JobLog.itemFailed("extend_game_series", "step_failed", it, "groupId" to groupId, "step" to what.substringBefore(' '))
            onFailure(what, it)
        }
}
