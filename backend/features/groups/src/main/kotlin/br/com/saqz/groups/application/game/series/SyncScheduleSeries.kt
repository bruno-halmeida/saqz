package br.com.saqz.groups.application.game.series

import br.com.saqz.groups.application.attendance.AutoConfirmationMaterializationPort
import br.com.saqz.groups.application.game.recurrence.GameIdFactory
import br.com.saqz.groups.application.game.recurrence.MaterializedGameOccurrence
import br.com.saqz.groups.application.game.recurrence.creatableBetween
import br.com.saqz.sharedkernel.subscription.GameCreationHorizon
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.GameVenueSnapshot
import br.com.saqz.groups.domain.game.recurrence.WeeklyRecurrenceResolver
import br.com.saqz.groups.domain.game.recurrence.WeeklyRecurrenceResult
import br.com.saqz.groups.domain.game.recurrence.WeeklySeriesRule
import br.com.saqz.groups.domain.game.recurrence.WeeklySlotRule
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/** Roda depois do commit de quem chamou; é por estado, então repetir a chamada não duplica nada. */
fun interface ScheduleSeriesSync { fun sync(groupId: UUID) }

data class ScheduleSeriesSlot(val weekday: DayOfWeek, val startTime: LocalTime, val durationMinutes: Int)
data class ScheduleSeriesDefaults(
    val title: String,
    val zoneId: String,
    val paused: Boolean,
    val venue: GameVenueSnapshot?,
    val capacity: Int,
    val confirmationLeadMinutes: Int,
    val gameFeeCents: Long?,
    val slots: List<ScheduleSeriesSlot>,
)
interface ScheduleSeriesDefaultsRepository {
    fun seriesDefaults(groupId: UUID): ScheduleSeriesDefaults?
    /** Grupos ativos com horário regular: os que a rotina mantém com série. */
    fun groupsWithRegularSlots(): List<UUID> = emptyList()
}

/** Mantém a série semanal do grupo igual aos horários regulares: cria, edita daqui para frente ou cancela. */
class SyncScheduleSeries(
    private val defaults: ScheduleSeriesDefaultsRepository,
    private val series: WeeklySeriesRepository,
    private val boundary: ApplySeriesBoundary,
    private val ids: GameIdFactory,
    private val clock: Clock,
    private val autoConfirmation: AutoConfirmationMaterializationPort = AutoConfirmationMaterializationPort { },
    private val horizon: GameCreationHorizon = GameCreationHorizon.Unlimited,
) : ScheduleSeriesSync {
    override fun sync(groupId: UUID) {
        val group = defaults.seriesDefaults(groupId) ?: return
        // Pausa = "para de criar novos jogos; os já criados continuam" (group_schedule_pause_help).
        if (group.paused) return
        // A série exige local (venue_name/venue_address NOT NULL); sem quadra padrão não há o que gerar.
        val venue = group.venue ?: return
        val now = clock.instant()
        val today = LocalDate.ofInstant(now, ZoneId.of(group.zoneId))

        // ponytail: a geração da série mora no id derivado, sem coluna nova. Série cancelada (recorrência
        // desligada) fica fechada e a próxima ativação abre a geração seguinte; custa 1 SELECT por ativação passada.
        var generation = 0
        var current = series.find(groupId, lineageId(groupId, generation))
        var start = today
        while (current != null && current.rule.activeThroughDate != null) {
            // A geração nova nunca começa antes do fim da anterior.
            start = maxOf(start, current.rule.activeThroughDate!!.plusDays(1))
            generation++
            current = series.find(groupId, lineageId(groupId, generation))
        }
        // Sem horizonte = trial expirado sem assinatura: mesma saída de recorrência desligada, cancela o futuro.
        val until = horizon.until(groupId)
        val slots = (if (until == null) emptyList() else group.slots).map { slot ->
            WeeklySlotRule(
                // Chave por dia+hora: horário que não mudou mantém os mesmos jogos (e presenças) na edição.
                slotKey = derivedId(groupId, "slot:${slot.weekday}:${slot.startTime}"),
                weekday = slot.weekday,
                localTime = slot.startTime,
                durationMinutes = slot.durationMinutes,
                venue = venue,
                capacity = group.capacity,
                confirmationLeadMinutes = group.confirmationLeadMinutes,
                gameFeeCents = group.gameFeeCents,
                title = group.title,
            )
        }
        when {
            current == null && slots.isEmpty() -> Unit
            current == null -> create(groupId, generation, group.zoneId, start, slots, until ?: now)
            current.rule.slots.toSet() == slots.toSet() -> Unit
            // Vale a partir de agora: a fronteira é hoje e o repositório não toca em jogo que já começou.
            else -> boundary.thisAndFuture(
                groupId = groupId,
                currentRevisionId = current.rule.revisionId,
                expectedVersion = current.version,
                successorRule = current.rule.copy(
                    revisionId = ids.create(),
                    localStartDate = maxOf(today, current.rule.localStartDate),
                    // CANCEL ainda grava a revisão sucessora e ela precisa de slots válidos.
                    slots = slots.ifEmpty { current.rule.slots },
                ),
                revisionNumber = current.revisionNumber + 1,
                boundary = maxOf(today, current.rule.localStartDate),
                action = if (slots.isEmpty()) SeriesBoundaryAction.CANCEL else SeriesBoundaryAction.EDIT,
            )
        }
    }

    private fun create(groupId: UUID, generation: Int, zoneId: String, start: LocalDate, slots: List<WeeklySlotRule>, until: java.time.Instant) {
        val rule = WeeklySeriesRule(
            groupId = groupId,
            seriesId = lineageId(groupId, generation),
            revisionId = derivedId(groupId, "revision:$generation"),
            zoneId = zoneId,
            localStartDate = start,
            slots = slots,
        )
        val resolved = when (val result = WeeklyRecurrenceResolver.resolve(rule, start)) {
            is WeeklyRecurrenceResult.Invalid -> return
            is WeeklyRecurrenceResult.Valid -> result.occurrences
        }
        val now = clock.instant()
        // O horário de hoje que já passou não vira jogo.
        // O resto nasce mês a mês, pela ExtendGameSeries.
        val materialized = resolved.creatableBetween(now, until)
            .map { MaterializedGameOccurrence(ids.create(), it, GameStatus.DRAFT, now) }
        if (series.create(rule, materialized)) autoConfirmation.apply(materialized)
    }

    private fun lineageId(groupId: UUID, generation: Int) = derivedId(groupId, "series:$generation")
    private fun derivedId(groupId: UUID, part: String) =
        UUID.nameUUIDFromBytes("schedule-series:$groupId:$part".toByteArray())
}
