package br.com.saqz.groups.presentation.gamedetail

/**
 * A linha verde-limão do cabeçalho (DEB-116), só em jogo publicado com resposta aberta e vaga: para quem
 * ainda não respondeu, "Restam N vagas" (1 a 3, jogo sem posições) ou "Tem vaga para …" (jogo com posições);
 * para o gestor, o que falta por posição.
 */
internal fun GameDetailState.resolveLimeLine(): GameLimeLine? {
    val spots = attendance?.availableSpots ?: 0
    val open = header?.let { it.statusTone == GameDetailStatusTone.Published && it.canRespond } == true
    if (!open || spots <= 0) return null
    val unanswered = response.status == null
    if (!hasPositions) {
        return spots.takeIf { unanswered && it in 1..SPOTS_LEFT_MAX }?.let(GameLimeLine::SpotsLeft)
    }
    val missing = positions.filter { it.slots > 0 && it.missing > 0 }.sortedBy { it.position.ordinal }
    return when {
        missing.isEmpty() -> null
        isAdmin -> GameLimeLine.Missing(missing.map { it.position to it.missing })
        unanswered -> GameLimeLine.OpenPositions(missing.map { it.position })
        else -> null
    }
}

/** Escassez que muda comportamento: o mesmo corte da Início e do grupo. */
private const val SPOTS_LEFT_MAX = 3
