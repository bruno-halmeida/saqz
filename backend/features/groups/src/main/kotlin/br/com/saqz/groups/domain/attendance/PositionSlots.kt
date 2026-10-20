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
 * Sugestão do sistema de jogo para a configuração do grupo, pelo TOTAL de vagas (decisão do dono, 20/10): um time de 6
 * no 5-1 = 1 levantador, 1 oposto, 2 pontas e 2 centrais; times = total ÷ 6 (inteiro), líbero 0 (configurável pelo dono) e o
 * que sobrar da divisão vai para ponta. Tabela simples para trocar fácil.
 */
object PositionSlotSuggestion {
    const val TEAM_SIZE = 6

    /** 6-0 devolve vazio (sem posições, o grupo segue com o total). Total fora do limite de um jogo devolve null. */
    fun of(system: GameSystem, total: Int): Map<AthletePosition, Int>? {
        if (total !in GameCapacity.MIN..GameCapacity.MAX) return null
        return when (system) {
            GameSystem.SIX_ZERO -> emptyMap()
            GameSystem.FIVE_ONE -> {
                val teams = total / TEAM_SIZE
                val leftover = total - TEAM_SIZE * teams
                mapOf(
                    AthletePosition.LIBERO to 0,
                    AthletePosition.PONTA to 2 * teams + leftover,
                    AthletePosition.CENTRAL to 2 * teams,
                    AthletePosition.OPOSTO to teams,
                    AthletePosition.LEVANTADOR to teams,
                )
            }
        }
    }
}

enum class PositionSlotsError {
    NEGATIVE_SLOTS, DUPLICATED_POSITION, NO_SLOTS, TOTAL_OUT_OF_RANGE,

    /** 5-1 sem posições: configurar as vagas por posição é obrigatório. */
    POSITIONS_REQUIRED,

    /** 6-0 (ou sem sistema) com posições: esses sistemas não usam posições. */
    POSITIONS_NOT_ALLOWED,

    /** O `total` enviado não bate com a soma das posições. */
    TOTAL_MISMATCH,
}

object PositionSlotsValidator {
    /**
     * Regra do contrato (decisão do dono): 5-1 EXIGE posições e, se o `total` vier, a soma tem de bater com ele;
     * 6-0 ou sem sistema exige lista vazia (desliga as posições).
     */
    fun validate(system: GameSystem?, slots: List<Pair<AthletePosition, Int>>, total: Int?): PositionSlotsError? {
        if (system != GameSystem.FIVE_ONE) return if (slots.isEmpty()) null else PositionSlotsError.POSITIONS_NOT_ALLOWED
        if (slots.isEmpty()) return PositionSlotsError.POSITIONS_REQUIRED
        validate(slots)?.let { return it }
        if (total != null && total != slots.sumOf { it.second }) return PositionSlotsError.TOTAL_MISMATCH
        return null
    }

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
