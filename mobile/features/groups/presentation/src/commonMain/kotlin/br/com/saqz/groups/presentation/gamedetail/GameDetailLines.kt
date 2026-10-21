package br.com.saqz.groups.presentation.gamedetail

/**
 * A linha verde-limão do cabeçalho (DEB-116), só em jogo publicado com resposta aberta e vaga: para quem
 * ainda não respondeu, "Restam N vagas" (1 a 3, jogo sem posições) ou "Tem vaga para …" (jogo com posições);
 * para o gestor, o que falta por posição.
 */
internal fun GameDetailState.resolveLimeLine(): GameLimeLine? {
    val header = header ?: return null
    val attendance = attendance ?: return null
    if (header.statusTone != GameDetailStatusTone.Published || !header.canRespond || attendance.availableSpots <= 0) {
        return null
    }
    val unanswered = response.status == null
    if (!hasPositions) {
        return attendance.availableSpots.takeIf { unanswered && it in 1..SPOTS_LEFT_MAX }?.let(GameLimeLine::SpotsLeft)
    }
    val open = positions.filter { it.slots > 0 && it.missing > 0 }.sortedBy { it.position.ordinal }
    return when {
        open.isEmpty() -> null
        isAdmin -> GameLimeLine.Missing(open.map { it.position to it.missing })
        unanswered -> GameLimeLine.OpenPositions(open.map { it.position })
        else -> null
    }
}

/** Escassez que muda comportamento: o mesmo corte da Início e do grupo. */
private const val SPOTS_LEFT_MAX = 3
