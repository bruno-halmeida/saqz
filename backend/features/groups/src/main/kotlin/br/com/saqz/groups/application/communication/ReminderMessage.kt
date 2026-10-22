package br.com.saqz.groups.application.communication

import br.com.saqz.groups.domain.AthletePosition
import br.com.saqz.groups.domain.attendance.GameSystem
import java.security.MessageDigest
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

/** Dia do jogo no tom das mensagens de aviso: "quarta 14/10". */
fun gameDay(localDate: LocalDate): String =
    "${WEEKDAYS[localDate.dayOfWeek.value - 1]} ${two(localDate.dayOfMonth)}/${two(localDate.monthValue)}"

/**
 * Texto do push do lembrete automático (DEB-066, copy de 18/10). Só o push usa: o grupo do WhatsApp recebe o corpo
 * completo do lembrete (jogo, local, prazo e listas), não este texto. Sem o jogo, cai na forma genérica.
 */
fun reminderPushBody(localDate: LocalDate?): String =
    "${localDate?.let { "O jogo de ${gameDay(it)}" } ?: "O próximo jogo"} está fechando a lista. Confirme sua presença."

/** Aviso de cancelamento: "O jogo de quarta 14/10 às 20h30 foi cancelado." */
fun gameCancelledBody(localDate: LocalDate, localTime: LocalTime): String =
    "O jogo de ${WEEKDAYS[localDate.dayOfWeek.value - 1]} ${two(localDate.dayOfMonth)}/${two(localDate.monthValue)}" +
        " às ${two(localTime.hour)}h${two(localTime.minute)} foi cancelado."

/**
 * Aviso de mudança (DEB-046, copy do DEB-071): "O jogo mudou! Era quarta 14/10, agora é quinta 15/10 às 20h30, Arena
 * Central. Quem não puder, avise no app." É a mesma mensagem na central, no push e no grupo do WhatsApp.
 */
fun gameScheduleChangedBody(previousDate: LocalDate, localDate: LocalDate, localTime: LocalTime, venue: String, court: String?): String =
    "O jogo mudou! Era ${WEEKDAYS[previousDate.dayOfWeek.value - 1]} ${two(previousDate.dayOfMonth)}/${two(previousDate.monthValue)}, agora é " +
        "${WEEKDAYS[localDate.dayOfWeek.value - 1]} ${two(localDate.dayOfMonth)}/${two(localDate.monthValue)}" +
        " às ${two(localTime.hour)}h${two(localTime.minute)}, $venue" + (court?.let { ", $it" } ?: "") + ". Quem não puder, avise no app."

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
 *
 * Jogo com sistema ([ReminderRoster.layout], ajustes do dono de 22/10): no 6-0 os confirmados vêm numerados de 1 até a
 * capacidade, com a vaga vazia como linha só com o número (`7.`); no 5-1 cada posição vira uma seção com as vagas
 * numeradas dentro dela, e a espera e o fora também vêm por posição (quem saiu sem posição cai em `Sem posição`). A
 * espera só é numerada quando a ordem impressa é a de promoção ([RosterLayout.numberedWaitlist]).
 */
fun reminderBody(game: ReminderGame, roster: ReminderRoster, now: Instant = Instant.now()): String {
    val sections = roster.layout?.let { layoutSections(it, game.capacity) } ?: listOf(
        "✅ Confirmados" to roster.confirmed,
        "🕒 Lista de espera" to roster.waitlisted,
        "❌ Fora" to roster.declined,
    ).filter { (_, names) -> names.isNotEmpty() }
    val body = buildString {
        append("Jogo: ").append(gameSchedule(game.localDate, game.localTime))
        append("\nLocal: ").append(game.venue)
        game.confirmationDeadline?.let { append("\n").append(deadlineLine(it, now)) }
        game.capacity?.let { append("\n").append(vacanciesLine(roster.confirmed.size, it)) }
        sections.forEach { (label, lines) ->
            append("\n\n").append(label).append(":\n").append(lines.joinToString("\n"))
        }
    }
    if (body.length <= REMINDER_BODY_LIMIT) return body
    val suffix = "\n…"
    val clipped = body.take(REMINDER_BODY_LIMIT - suffix.length)
    val boundary = clipped.lastIndexOf('\n')
    return (if (boundary > 0) clipped.take(boundary) else clipped).trimEnd() + suffix
}

/** Ordem das seções de posição no 5-1. */
private val POSITION_ORDER = listOf(
    AthletePosition.LEVANTADOR, AthletePosition.PONTA, AthletePosition.CENTRAL, AthletePosition.OPOSTO, AthletePosition.LIBERO,
)

/** O plural das seções da tela de jogo no 5-1 (`position_plural_*` do app), com inicial maiúscula. */
private val POSITION_LABELS = mapOf(
    AthletePosition.LEVANTADOR to "Levantadores(as)",
    AthletePosition.PONTA to "Ponteiros(as)",
    AthletePosition.CENTRAL to "Centrais",
    AthletePosition.OPOSTO to "Opostos(as)",
    AthletePosition.LIBERO to "Líberos",
)

private const val NO_POSITION = "Sem posição"

private fun layoutSections(layout: RosterLayout, capacity: Int?): List<Pair<String, List<String>>> {
    val perPosition = layout.system == GameSystem.FIVE_ONE && layout.slots.isNotEmpty()
    val confirmed = if (perPosition) {
        byPosition(layout.confirmed, layout.slots) { position, names ->
            position?.let { numbered(names, layout.slots[it] ?: 0) } ?: names
        }
    } else {
        numbered(layout.confirmed.map { it.name }, capacity ?: 0)
    }
    val waitlist = { names: List<String> -> if (layout.numberedWaitlist) numbered(names, 0) else names }
    val waitlisted = if (perPosition) byPosition(layout.waitlisted) { _, names -> waitlist(names) }
    else waitlist(layout.waitlisted.map { it.name })
    val declined = if (perPosition) byPosition(layout.declined) { _, names -> names } else layout.declined.map { it.name }
    return listOf("✅ Confirmados" to confirmed, "🕒 Lista de espera" to waitlisted, "❌ Fora" to declined)
        .filter { (_, lines) -> lines.isNotEmpty() }
}

/** `1. Ana` … e as vagas que faltam até [slots] como linhas só com o número (`7.`). */
private fun numbered(names: List<String>, slots: Int): List<String> =
    List(maxOf(names.size, slots)) { index -> names.getOrNull(index)?.let { "${index + 1}. $it" } ?: "${index + 1}." }

/**
 * Uma subseção `Posição:` por posição, na ordem de [POSITION_ORDER]: as posições com vaga no jogo ([slots]) sempre
 * aparecem; as outras, só com alguém nelas. Quem não tem posição vem por último, em `Sem posição`.
 */
private fun byPosition(
    entries: List<RosterEntry>,
    slots: Map<AthletePosition, Int> = emptyMap(),
    lines: (AthletePosition?, List<String>) -> List<String>,
): List<String> {
    val groups = entries.groupBy({ it.position }, { it.name })
    val positions: List<AthletePosition?> = POSITION_ORDER.filter { (slots[it] ?: 0) > 0 || groups.containsKey(it) } +
        (if (groups.containsKey(null)) listOf(null) else emptyList())
    return positions.flatMapIndexed { index, position ->
        listOfNotNull("".takeIf { index > 0 }) +
            "${position?.let(POSITION_LABELS::getValue) ?: NO_POSITION}:" +
            lines(position, groups[position].orEmpty())
    }
}

/**
 * Assinatura da lista para o modo ON_CHANGE: situação, posição e nome de cada entrada, na ordem da lista. Não usa o
 * corpo, que carrega a linha do prazo e muda com o tempo.
 */
fun rosterDigest(roster: ReminderRoster): String {
    val layout = roster.layout
    val lines = if (layout == null) {
        listOf("C" to roster.confirmed, "W" to roster.waitlisted, "D" to roster.declined)
            .flatMap { (bucket, names) -> names.map { "$bucket||$it" } }
    } else {
        listOf("system|${layout.system}") +
            listOf("C" to layout.confirmed, "W" to layout.waitlisted, "D" to layout.declined)
                .flatMap { (bucket, entries) -> entries.map { "$bucket|${it.position ?: ""}|${it.name}" } }
    }
    return MessageDigest.getInstance("SHA-256").digest(lines.joinToString("\n").toByteArray())
        .joinToString("") { "%02x".format(it) }
}

private fun two(value: Int) = value.toString().padStart(2, '0')
