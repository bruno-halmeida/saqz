package br.com.saqz.groups.presentation.duration

/**
 * Duração de jogo e de horário regular: o contador anda numa grade de 30 em 30 minutos até 3h e de hora em hora
 * depois, até 8h (480 min, o máximo do backend em `Game.kt`). Regra pura, igual no editor de jogo, na configuração do
 * grupo e nos horários regulares.
 *
 * Valor salvo fora da grade (195, por exemplo) continua válido enquanto estiver entre 1h e 8h: aparece como "3h15" e o
 * – / + vai para o vizinho da grade.
 */
object CustomDuration {
    val Grid = listOf(60, 90, 120, 150, 180, 240, 300, 360, 420, 480)
    const val MIN_MINUTES = 60
    const val MAX_MINUTES = 480

    /** Vizinho de baixo na grade; abaixo do piso fica no piso. */
    fun previous(minutes: Int): Int = Grid.lastOrNull { it < minutes } ?: Grid.first()

    /** Vizinho de cima na grade; acima do teto fica no teto. */
    fun next(minutes: Int): Int = Grid.firstOrNull { it > minutes } ?: Grid.last()

    fun isInvalid(minutes: Int): Boolean = minutes !in MIN_MINUTES..MAX_MINUTES
}
