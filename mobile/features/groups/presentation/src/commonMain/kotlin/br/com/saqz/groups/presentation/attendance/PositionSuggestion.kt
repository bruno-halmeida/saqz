package br.com.saqz.groups.presentation.attendance

import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.attendance.PositionSummary

/**
 * A posição do perfil como sugestão no "Vou" de jogo com posições (PRES-014). Vale qualquer posição; a do perfil só
 * aparece como sugestão ("Do seu perfil") quando o jogo tem vagas nela, e só vem **marcada** se ainda tiver vaga.
 */
internal object PositionSuggestion {
    /** A posição do perfil, se o jogo oferece vagas nela; [offered] são as posições com ao menos uma vaga configurada. */
    fun suggested(profile: AthletePosition?, offered: Collection<AthletePosition>): AthletePosition? =
        profile?.takeIf { it in offered }

    fun suggested(profile: AthletePosition?, positions: List<PositionSummary>): AthletePosition? =
        suggested(profile, positions.filter { it.slots > 0 }.map(PositionSummary::position))

    /** O que vem marcado: a sugerida com vaga; cheia ou sem sugestão, nada. */
    fun initial(profile: AthletePosition?, positions: List<PositionSummary>): AthletePosition? =
        suggested(profile, positions)?.takeIf { position -> positions.any { it.position == position && !it.isFull } }
}
