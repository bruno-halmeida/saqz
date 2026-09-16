package br.com.saqz.groups.application.communication

import java.time.LocalDate
import java.time.LocalTime

/** Limite de `group_messages.body` (varchar(2000)). */
const val REMINDER_BODY_LIMIT = 2000

private val WEEKDAYS = listOf("segunda", "terça", "quarta", "quinta", "sexta", "sábado", "domingo")

fun gameSchedule(localDate: LocalDate, localTime: LocalTime): String =
    "${WEEKDAYS[localDate.dayOfWeek.value - 1]}, ${two(localDate.dayOfMonth)}/${two(localDate.monthValue)}" +
        " às ${two(localTime.hour)}:${two(localTime.minute)}"

/**
 * Corpo do aviso de jogo liberado: o toque diário por push para quem ainda não respondeu.
 * Mesmo cabeçalho do lembrete, sem as listas nominais.
 */
fun openGameBody(game: ReminderGame): String =
    "Jogo: ${gameSchedule(game.localDate, game.localTime)}\nLocal: ${game.venue}\n" +
        "\nO jogo está liberado. Confirme sua presença."

/**
 * Corpo do lembrete de presença: `Jogo: {dia, data às hora}`, `Local: {local}` e listas nominais
 * por situação, na ordem confirmados, lista de espera e fora, um nome por linha. Seções vazias são
 * omitidas. Acima de [REMINDER_BODY_LIMIT] o texto é cortado no último nome completo, com `…`.
 */
fun reminderBody(game: ReminderGame, roster: ReminderRoster): String {
    val sections = listOf(
        "✅ Confirmados" to roster.confirmed,
        "🕒 Lista de espera" to roster.waitlisted,
        "❌ Fora" to roster.declined,
    ).filter { (_, names) -> names.isNotEmpty() }
    val body = buildString {
        append("Jogo: ").append(gameSchedule(game.localDate, game.localTime))
        append("\nLocal: ").append(game.venue)
        sections.forEach { (label, names) ->
            append("\n\n").append(label).append(":\n").append(names.joinToString("\n"))
        }
    }
    if (body.length <= REMINDER_BODY_LIMIT) return body
    val suffix = "\n…"
    val clipped = body.take(REMINDER_BODY_LIMIT - suffix.length)
    val boundary = clipped.lastIndexOf('\n')
    return (if (boundary > 0) clipped.take(boundary) else clipped).trimEnd() + suffix
}

private fun two(value: Int) = value.toString().padStart(2, '0')
