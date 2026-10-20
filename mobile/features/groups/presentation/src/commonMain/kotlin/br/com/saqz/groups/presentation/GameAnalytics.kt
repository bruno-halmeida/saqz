package br.com.saqz.groups.presentation

import br.com.saqz.core.common.analytics.SaqzAnalytics
import br.com.saqz.groups.domain.game.GameError
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Onde o servidor recusou o jogo por cair depois do fim do plano ou do teste (`action` do evento). */
internal enum class GamePlanEndAction(val value: String) { Create("create"), Edit("edit"), Publish("publish") }

/**
 * Evento `game_after_plan_end` (Firebase) quando o servidor responde 422 `GAME_AFTER_PLAN_END`. Só datas: sem id de
 * grupo, de jogo ou de usuário. [limitDate] é o `startsBefore` do servidor no fuso do grupo, se ele veio.
 */
internal fun trackGameAfterPlanEnd(error: GameError, attemptedDate: String, zoneId: String?, action: GamePlanEndAction) {
    if (error !is GameError.AfterPlanEnd) return
    val params = buildList {
        add("attempted_date" to attemptedDate)
        error.startsBefore?.let { add("limit_date" to localDate(it, zoneId)) }
        add("error_code" to "GAME_AFTER_PLAN_END")
        add("action" to action.value)
    }
    SaqzAnalytics.event("game_after_plan_end", *params.toTypedArray())
}

private fun localDate(instant: String, zoneId: String?): String {
    val zone = zoneId?.let { runCatching { TimeZone.of(it) }.getOrNull() } ?: TimeZone.UTC
    return runCatching { Instant.parse(instant).toLocalDateTime(zone).date.toString() }.getOrElse { instant.take(10) }
}
