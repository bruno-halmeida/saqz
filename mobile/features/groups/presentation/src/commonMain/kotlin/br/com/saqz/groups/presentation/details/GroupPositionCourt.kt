package br.com.saqz.groups.presentation.details

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.attendance.PositionSummary

/**
 * Um lugar da meia quadra do 5-1 no cartão do próximo jogo (DEB-109). A contagem ("3/4" e "Falta N") só aparece na
 * [first] vez da posição; posição com 0 vagas ([offered] falso) fica esmaecida, com "—" e sem marca.
 */
@Immutable
data class CourtSpotUi(
    val position: AthletePosition,
    val slots: Int,
    val confirmed: Int,
    val first: Boolean,
) {
    val offered: Boolean get() = slots > 0
    val missing: Int get() = (slots - confirmed).coerceAtLeast(0)
    val complete: Boolean get() = offered && missing == 0
}

/** A quadra inteira: frente e fundo, se todas fecharam e quem está na espera de cada posição. */
@Immutable
data class GroupCourtUi(
    val front: List<CourtSpotUi>,
    val back: List<CourtSpotUi>,
    val allComplete: Boolean,
    /** Posições com gente na espera, na ordem da quadra: "Na espera: 1 Ponteiro(a) · 2 centrais". */
    val waiting: List<Pair<AthletePosition, Int>>,
)

/**
 * Meia quadra de um time no 5-1 (decisão de 20/10): frente Ponteiro(a) · Central · Oposto(a); fundo Ponteiro(a) ·
 * Líbero · Levantador(a). O líbero fica no lugar do central do fundo; com 0 líbero no grupo, volta Central.
 */
internal object GroupPositionCourt {
    fun from(positions: List<PositionSummary>): GroupCourtUi? {
        if (positions.isEmpty()) return null
        val byPosition = positions.associateBy(PositionSummary::position)
        val libero = (byPosition[AthletePosition.LIBERO]?.slots ?: 0) > 0
        val layout = listOf(
            AthletePosition.PONTA, AthletePosition.CENTRAL, AthletePosition.OPOSTO,
            AthletePosition.PONTA, if (libero) AthletePosition.LIBERO else AthletePosition.CENTRAL, AthletePosition.LEVANTADOR,
        )
        val seen = mutableSetOf<AthletePosition>()
        val spots = layout.map { position ->
            val summary = byPosition[position]
            CourtSpotUi(position, summary?.slots ?: 0, summary?.confirmed ?: 0, first = seen.add(position))
        }
        val order = layout.distinct() + AthletePosition.entries
        return GroupCourtUi(
            front = spots.take(3),
            back = spots.drop(3),
            allComplete = positions.filter { it.slots > 0 }.all { it.missing == 0 },
            waiting = positions.filter { it.waitlisted > 0 }
                .sortedBy { order.indexOf(it.position) }
                .map { it.position to it.waitlisted },
        )
    }
}
