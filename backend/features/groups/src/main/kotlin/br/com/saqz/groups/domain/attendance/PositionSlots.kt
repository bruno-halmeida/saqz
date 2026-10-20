package br.com.saqz.groups.domain.attendance

import br.com.saqz.groups.domain.AthletePosition
import br.com.saqz.groups.domain.game.GameCapacity

/** Sistema de jogo do grupo (DEB-102). 6-0 não usa posições: o grupo segue com o total de vagas. */
enum class GameSystem { FIVE_ONE, SIX_ZERO }

/** Vagas de uma posição num jogo, com a situação atual (confirmados e espera daquela posição). */
data class PositionLoad(
    val position: AthletePosition,
    val slots: Int,
    val confirmed: Int,
    val waitlisted: Int,
) {
    /** Quanto falta para a posição fechar; confirmados forçados acima das vagas não deixam negativo. */
    val missing: Int get() = (slots - confirmed).coerceAtLeast(0)
}

/**
 * Sugestão do sistema de jogo para a configuração do grupo. Tabela simples para trocar fácil (o gestor confirma os
 * números com o dono): vagas de UM time, multiplicadas pelo número de times.
 */
object PositionSlotSuggestion {
    const val DEFAULT_TEAMS = 2
    val TEAMS_RANGE = 1..6

    private val FIVE_ONE_PER_TEAM: Map<AthletePosition, Int> = mapOf(
        AthletePosition.LEVANTADOR to 1,
        AthletePosition.OPOSTO to 1,
        AthletePosition.PONTA to 2,
        AthletePosition.CENTRAL to 2,
        AthletePosition.LIBERO to 0,
    )

    /** 6-0 devolve vazio: sem posições, o grupo segue com o total. Times fora de [TEAMS_RANGE] devolve null. */
    fun of(system: GameSystem, teams: Int = DEFAULT_TEAMS): Map<AthletePosition, Int>? {
        if (teams !in TEAMS_RANGE) return null
        return when (system) {
            GameSystem.SIX_ZERO -> emptyMap()
            GameSystem.FIVE_ONE -> AthletePosition.entries.associateWith { (FIVE_ONE_PER_TEAM[it] ?: 0) * teams }
        }
    }
}

enum class PositionSlotsError { NEGATIVE_SLOTS, DUPLICATED_POSITION, NO_SLOTS, TOTAL_OUT_OF_RANGE }

object PositionSlotsValidator {
    /** Lista vazia desliga as posições. Com posições: vagas ≥ 0, sem repetir posição, ao menos uma com vaga e soma no limite do jogo. */
    fun validate(slots: List<Pair<AthletePosition, Int>>): PositionSlotsError? {
        if (slots.isEmpty()) return null
        if (slots.any { it.second < 0 }) return PositionSlotsError.NEGATIVE_SLOTS
        if (slots.map { it.first }.distinct().size != slots.size) return PositionSlotsError.DUPLICATED_POSITION
        val total = slots.sumOf { it.second }
        if (total == 0) return PositionSlotsError.NO_SLOTS
        if (total !in GameCapacity.MIN..GameCapacity.MAX) return PositionSlotsError.TOTAL_OUT_OF_RANGE
        return null
    }
}

/**
 * Posição de quem confirma num jogo com posições. Pedido com posição: vale se o jogo tem vaga configurada nela, senão
 * nulo (o chamador nega). Pedido sem posição (apps 1.0.6 a 1.0.9 nunca mandam): principal do perfil, depois a
 * secundária, e sem perfil útil a posição com mais vagas livres (empate pela ordem do enum). Só conta posição com vaga
 * configurada (> 0).
 */
object PositionChoice {
    fun resolve(
        loads: List<PositionLoad>,
        requested: AthletePosition?,
        profileMain: AthletePosition?,
        profileSecondary: AthletePosition?,
    ): AthletePosition? {
        val open = loads.filter { it.slots > 0 }.associateBy { it.position }
        if (requested != null) return requested.takeIf { it in open }
        profileMain?.takeIf { it in open }?.let { return it }
        profileSecondary?.takeIf { it in open }?.let { return it }
        return open.values.sortedWith(compareByDescending<PositionLoad> { it.slots - it.confirmed }.thenBy { it.position.ordinal })
            .firstOrNull()?.position
    }
}
