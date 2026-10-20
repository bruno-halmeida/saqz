package br.com.saqz.groups.presentation.duration

/**
 * O que a pessoa escolheu como duração: os [minutes] gravados e, enquanto o "Outros" está em edição, o [text] digitado
 * em horas (`null` fora do "Outros"). O texto existe porque "1", "2" ou vazio não são durações do "Outros", mas
 * também não podem virar um chip sem a pessoa tocar nele.
 */
data class DurationDraft(val minutes: Int, val text: String? = null)

/**
 * Duração de jogo e de horário regular (DEB-105): chips 1h a 2h30 e "Outros" em horas cheias de 3 a 8 (180 a 480
 * min). O backend aceita o jogo de 15 a 480 (`Game.kt`, `ck_games_duration`). Regra pura, igual no editor de jogo, na
 * configuração do grupo e nos horários regulares.
 */
object CustomDuration {
    val Presets = listOf(60, 90, 120, 150)
    const val MIN_HOURS = 3
    const val MAX_HOURS = 8
    const val MIN_MINUTES = 150
    const val MAX_MINUTES = 480
    private const val MINUTES_PER_HOUR = 60
    private const val MAX_TEXT = 4

    /** Fora dos chips: é o que marca "Outros". */
    fun isCustom(minutes: Int): Boolean = minutes !in Presets

    fun selectPreset(minutes: Int) = DurationDraft(minutes)

    /** Toque em "Outros": com valor fora dos chips mantém o valor; vindo de um chip começa vazio, sem duração. */
    fun startOther(current: DurationDraft): DurationDraft = when {
        current.text != null -> current
        isCustom(current.minutes) -> current
        else -> DurationDraft(minutes = 0, text = "")
    }

    /** Só dígitos, vírgula e ponto entram no campo (os dois últimos viram erro); letras não. */
    fun type(raw: String): DurationDraft {
        val text = raw.filter { it.isDigit() || it == ',' || it == '.' }.take(MAX_TEXT)
        return DurationDraft(minutes = hours(text)?.let { it * MINUTES_PER_HOUR } ?: 0, text = text)
    }

    /** Horas inteiras de 3 a 8; vazio, vírgula, ponto ou fora da faixa dão `null`. */
    fun hours(text: String): Int? = text.takeIf { t -> t.isNotEmpty() && t.all(Char::isDigit) }
        ?.toIntOrNull()?.takeIf { it in MIN_HOURS..MAX_HOURS }

    /** Texto do campo: o digitado, ou as horas cheias do valor salvo (195 min não é hora cheia: campo vazio). */
    fun fieldText(draft: DurationDraft): String = draft.text
        ?: draft.minutes.takeIf { isCustom(it) && it % MINUTES_PER_HOUR == 0 }?.let { (it / MINUTES_PER_HOUR).toString() }
        .orEmpty()

    /** "Outros" marcado: em edição ou com valor fora dos chips. */
    fun isOther(draft: DurationDraft): Boolean = draft.text != null || isCustom(draft.minutes)

    fun isInvalid(draft: DurationDraft): Boolean = when {
        draft.text != null -> hours(draft.text) == null
        else -> isCustom(draft.minutes) && draft.minutes !in MIN_MINUTES..MAX_MINUTES
    }
}
