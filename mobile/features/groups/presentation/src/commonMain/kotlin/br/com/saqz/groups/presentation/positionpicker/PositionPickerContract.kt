package br.com.saqz.groups.presentation.positionpicker

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.presentation.GroupUiError

/**
 * De onde o "Vou" abriu a tela (DEB-107). Decide o que acontece depois de confirmar: Início, grupo e jogo voltam para
 * onde estavam; o link mostra o próprio cartão de resultado; a notificação abre o jogo.
 */
enum class PositionPickerOrigin(val routeValue: String) {
    Home("home"),
    Group("group"),
    Game("game"),
    Link("link"),
    Push("push"),
    ;

    companion object {
        fun from(value: String): PositionPickerOrigin = entries.firstOrNull { it.routeValue == value } ?: Game
    }
}

/** O cartão do jogo no topo: "28" / "JUL", "Terça, 19h30", duração, local e grupo. */
@Immutable
data class PositionPickerGameUi(
    val day: String,
    val month: String,
    val title: String,
    val durationMinutes: Int,
    val venue: String,
    val groupName: String,
)

/** Uma posição com vaga configurada no jogo. [queue] é a vez de quem entrar agora na espera dela. */
@Immutable
data class PositionOptionUi(
    val position: AthletePosition,
    val slots: Int,
    val confirmed: Int,
    val waitlisted: Int,
    val full: Boolean,
    /** A posição do perfil de quem responde ("Do seu perfil"). */
    val suggested: Boolean,
) {
    val open: Int get() = (slots - confirmed).coerceAtLeast(0)
    val queue: Int get() = waitlisted + 1
}

data class PositionPickerState(
    val loading: Boolean = true,
    val loadError: GroupUiError? = null,
    val game: PositionPickerGameUi? = null,
    val options: List<PositionOptionUi> = emptyList(),
    val selected: AthletePosition? = null,
    val sending: Boolean = false,
    /** A releitura antes de enviar achou a escolhida cheia: nada foi enviado. */
    val raced: AthletePosition? = null,
    /** Falha de rede ou do servidor no envio; o botão tenta de novo. */
    val submitFailed: Boolean = false,
) {
    val selectedOption: PositionOptionUi? get() = options.firstOrNull { it.position == selected }
    val allFull: Boolean get() = options.isNotEmpty() && options.all { it.full }
    val openPositions: List<AthletePosition> get() = options.filterNot { it.full }.map { it.position }
    /** A leitura veio sem posição com vaga (o jogo deixou de ter posições, push antigo): confirma como jogo comum. */
    val withoutPositions: Boolean get() = !loading && loadError == null && options.isEmpty()
    val canSubmit: Boolean get() = (selectedOption != null || withoutPositions) && !sending && !loading
}

sealed interface PositionPickerIntent {
    data class Select(val position: AthletePosition) : PositionPickerIntent
    data object Submit : PositionPickerIntent
    data object Retry : PositionPickerIntent
}

sealed interface PositionPickerEffect {
    /** Início, grupo e jogo: volta, sem aviso; o cartão de lá se atualiza ao reaparecer. */
    data object Close : PositionPickerEffect

    /** Link do WhatsApp: volta ao link, que mostra o resultado com a posição. */
    data class LinkResult(val status: AttendanceStatus) : PositionPickerEffect

    /** Notificação: abre o jogo. */
    data class OpenGame(val groupId: String, val gameId: String) : PositionPickerEffect
}
