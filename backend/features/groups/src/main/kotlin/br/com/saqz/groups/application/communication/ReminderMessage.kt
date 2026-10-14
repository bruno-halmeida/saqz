package br.com.saqz.groups.application.communication

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Limite de `group_messages.body` (varchar(2000)). */
const val REMINDER_BODY_LIMIT = 2000

private val WEEKDAYS = listOf("segunda", "terça", "quarta", "quinta", "sexta", "sábado", "domingo")

fun gameSchedule(localDate: LocalDate, localTime: LocalTime): String =
    "${WEEKDAYS[localDate.dayOfWeek.value - 1]}, ${two(localDate.dayOfMonth)}/${two(localDate.monthValue)}" +
        " às ${two(localTime.hour)}:${two(localTime.minute)}"

/** Aviso de cancelamento: "O jogo de quarta 14/10 às 20h30 foi cancelado." */
fun gameCancelledBody(localDate: LocalDate, localTime: LocalTime): String =
    "O jogo de ${WEEKDAYS[localDate.dayOfWeek.value - 1]} ${two(localDate.dayOfMonth)}/${two(localDate.monthValue)}" +
        " às ${two(localTime.hour)}h${two(localTime.minute)} foi cancelado."

/**
 * Corpo do aviso de jogo liberado: o toque diário por push para quem ainda não respondeu.
 * Mesmo cabeçalho do lembrete, sem as listas nominais.
 */
fun openGameBody(game: ReminderGame): String =
    "Jogo: ${gameSchedule(game.localDate, game.localTime)}\nLocal: ${game.venue}\n" +
        "\nO jogo está liberado. Confirme sua presença."

/** Corpo da janela de presença das 24 h: horário, local e a contagem no momento em que ela abre. */
fun attendanceWindowBody(game: ReminderGame, confirmed: Int, capacity: Int, waitlisted: Int): String =
    "Jogo: ${gameSchedule(game.localDate, game.localTime)}\nLocal: ${game.venue}\n" +
        "$confirmed/$capacity confirmados" + (if (waitlisted > 0) " · $waitlisted na espera" else "") +
        "\n\nConfirme sua presença."

private val SAO_PAULO = ZoneId.of("America/Sao_Paulo")
private val WEEKDAYS_SHORT = listOf("seg", "ter", "qua", "qui", "sex", "sáb", "dom")

/** `Confirmações até qui 14h00` antes do prazo, `Prazo encerrado` depois (horário de São Paulo). */
fun deadlineLine(deadline: Instant, now: Instant): String {
    if (!now.isBefore(deadline)) return "Prazo encerrado"
    val local = deadline.atZone(SAO_PAULO)
    return "Confirmações até ${WEEKDAYS_SHORT[local.dayOfWeek.value - 1]} ${two(local.hour)}h${two(local.minute)}"
}

/** `Vagas: 10/12` ou `Lista completa` quando os confirmados já preenchem a capacidade. */
fun vacanciesLine(confirmed: Int, capacity: Int): String =
    if (confirmed >= capacity) "Lista completa" else "Vagas: $confirmed/$capacity"

/**
 * Corpo do lembrete de presença: `Jogo: {dia, data às hora}`, `Local: {local}`, prazo e vagas (quando o
 * jogo traz prazo e capacidade) e listas nominais
 * por situação, na ordem confirmados, lista de espera e fora, um nome por linha. Seções vazias são
 * omitidas. Acima de [REMINDER_BODY_LIMIT] o texto é cortado no último nome completo, com `…`.
 */
fun reminderBody(game: ReminderGame, roster: ReminderRoster, now: Instant = Instant.now()): String {
    val sections = listOf(
        "✅ Confirmados" to roster.confirmed,
        "🕒 Lista de espera" to roster.waitlisted,
        "❌ Fora" to roster.declined,
    ).filter { (_, names) -> names.isNotEmpty() }
    val body = buildString {
        append("Jogo: ").append(gameSchedule(game.localDate, game.localTime))
        append("\nLocal: ").append(game.venue)
        game.confirmationDeadline?.let { append("\n").append(deadlineLine(it, now)) }
        game.capacity?.let { append("\n").append(vacanciesLine(roster.confirmed.size, it)) }
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
