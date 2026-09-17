package br.com.saqz.groups.application.game.series

import br.com.saqz.groups.application.game.recurrence.MaterializeWeeklySeries
import br.com.saqz.sharedkernel.subscription.GameCreationHorizon
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * Rotina da recorrência: jogos nascem mês a mês, conforme o plano do dono renova
 * ([GameCreationHorizon]). Trial expirado sem assinatura cancela o futuro de toda série.
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
    private val onFailure: (String, Throwable) -> Unit = { _, _ -> },
) {
    fun run() {
        series.openSeries().forEach { (groupId, lineageId) ->
            guarded("series $lineageId") {
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
        schedules.groupsWithRegularSlots().forEach { groupId -> guarded("group $groupId") { scheduleSeries.sync(groupId) } }
    }

    // Um grupo com conflito de horário não pode parar a rotina dos outros.
    private fun guarded(what: String, block: () -> Unit) =
        runCatching(block).onFailure { onFailure(what, it) }
}
