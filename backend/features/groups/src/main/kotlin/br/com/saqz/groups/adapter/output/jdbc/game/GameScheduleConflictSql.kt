package br.com.saqz.groups.adapter.output.jdbc.game

import java.sql.SQLException

internal const val GAME_SCHEDULE_UNIQUE_CONSTRAINT = "games_schedule_start_unique"

internal fun Throwable.isGameScheduleConflict(): Boolean {
    var failure: Throwable? = this
    while (failure != null) {
        if (failure is SQLException && failure.message?.contains(GAME_SCHEDULE_UNIQUE_CONSTRAINT) == true) return true
        failure = failure.cause
    }
    return false
}
