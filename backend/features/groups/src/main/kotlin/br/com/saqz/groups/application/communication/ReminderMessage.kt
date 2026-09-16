package br.com.saqz.groups.application.communication

/** Limite de `group_messages.body` (varchar(2000)). */
const val REMINDER_BODY_LIMIT = 2000

/**
 * Corpo do lembrete de presença: título em negrito e listas nominais por situação,
 * na ordem confirmados, lista de espera, fora e a confirmar. Seções vazias são omitidas.
 * Acima de [REMINDER_BODY_LIMIT] o texto é cortado no último nome completo, com `…`.
 */
fun reminderBody(title: String, roster: ReminderRoster): String {
    val sections = listOf(
        "✅ Confirmados" to roster.confirmed,
        "🕒 Lista de espera" to roster.waitlisted,
        "❌ Fora" to roster.declined,
        "⏳ A confirmar" to roster.pending,
    ).filter { (_, names) -> names.isNotEmpty() }
    val body = buildString {
        append('*').append(title).append('*')
        sections.forEach { (label, names) ->
            append("\n\n").append(label).append(":\n").append(names.joinToString(", "))
        }
    }
    if (body.length <= REMINDER_BODY_LIMIT) return body
    val clipped = body.take(REMINDER_BODY_LIMIT - 1)
    val boundary = clipped.lastIndexOf(", ")
    return (if (boundary > 0) clipped.take(boundary) else clipped).trimEnd() + "…"
}
