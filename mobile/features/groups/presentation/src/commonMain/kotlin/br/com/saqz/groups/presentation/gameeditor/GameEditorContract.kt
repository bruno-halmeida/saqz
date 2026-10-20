package br.com.saqz.groups.presentation.gameeditor

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.domain.group.GroupRegularSlot
import br.com.saqz.groups.domain.place.PlaceSuggestion
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.place.VenueSearchState

/**
 * 4a/4b/4e — editor de jogo. `gameId == null` cria; presente edita.
 * O formulário carrega defaults da quadra/vagas/prazo do grupo e deixa o admin trocar só
 * neste jogo. Data e horário compartilham o mesmo bottom-sheet de rolagem (4b).
 *
 * Na criação, "Repetir toda semana" grava o dia e a hora do jogo como horário regular do grupo
 * (a mesma recorrência da agenda, 2m), para não obrigar ninguém a editar o grupo. O jogo marcado
 * sai publicado; os das próximas semanas o backend cria como rascunho.
 */
@Immutable
data class GameEditorState(
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val error: GroupUiError? = null,
    val groupName: String = "",
    val zoneId: String = "",
    /** Primeiro instante em que um jogo já não cabe no plano ou no teste (0h do dia do fim); nulo = sem limite conhecido. */
    val planLimit: String? = null,
    /** Último dia que dá para marcar (aaaa-mm-dd, no fuso do grupo); é o que o texto do bloqueio mostra. */
    val planLastDay: String? = null,
    /** O último dia que dá para marcar já passou (ex.: último dia do teste): o texto não manda escolher uma data, só assinar. */
    val planLastDayPassed: Boolean = false,
    /** O limite é o fim do teste, não o de um plano pago: muda a palavra do texto. */
    val planLimitIsTrial: Boolean = false,
    /** Só o dono assina: o admin recebe o texto sem "assine". */
    val isOwner: Boolean = false,
    /** O início escolhido cai no limite ou depois (DEB-075): não marca nem salva. Na edição só vale se data ou hora mudaram. */
    val planBlocked: Boolean = false,
    /**
     * Início (instante) que o servidor recusou por cair depois do fim do plano (422). Vale como bloqueio enquanto o
     * formulário continuar nele, mesmo sem o app saber o limite: mostra o texto aprovado em vez do erro genérico.
     */
    val planRefusedStart: String? = null,
    /** Data e hora do jogo carregado (`aaaa-mm-ddThh:mm`); na edição, só mudar isso aciona o bloqueio. */
    val originalStart: String? = null,
    /** Há plano pago ativo. */
    val planActive: Boolean = false,
    /** O backend recusou a gravação por falta de plano: a UI abre o diálogo com a oferta. */
    val subscriptionRequired: Boolean = false,
    /** Distingue "o teste terminou" de "o plano não está ativo" no título do diálogo. */
    val trialEnded: Boolean = false,
    val form: GameEditorFields = GameEditorFields(),
    /** Só na criação, e só com perfil completo: na edição a recorrência é da agenda do grupo. */
    val recurrenceOffered: Boolean = false,
    /** Horários regulares que o grupo já tem; a pílula deste jogo entra ao lado deles. */
    val regularSlots: List<GroupRegularSlot> = emptyList(),
    /** Sem quadra padrão, o editor pede o nome da quadra, e ela vira a do grupo ao marcar o jogo. */
    val groupHasVenue: Boolean = false,
    /** O jogo foi marcado, mas o horário regular não foi gravado; o retry resume o rascunho. */
    val recurrenceFailed: Boolean = false,
    val validationErrors: Set<GameEditorFieldError> = emptySet(),
    /** Cada toque em "Marcar jogo" com erro soma um: faz a tela rolar até o campo mesmo com o mesmo erro. */
    val submitAttempts: Int = 0,
    /** Sugestões de endereço do Google; vazio e indisponível quando o backend desliga a busca. */
    val venueSearch: VenueSearchState = VenueSearchState(),
    val versionToken: String? = null,
    /** Confirmados do jogo em edição; serve para avisar quando as vagas ficam abaixo deles. */
    val confirmedCount: Int = 0,
    /** Vagas de um jogo antigo abaixo do mínimo de hoje; o formulário já abre no mínimo e a tela explica. */
    val legacyCapacity: Int? = null,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val hasConflict: Boolean = false,
    val conflictGameId: String? = null,
)

/** Campos editáveis. Strings formatadas já vêm prontas para a UI (AGENTS.md §8). */
@Immutable
data class GameEditorFields(
    val title: String = "",
    val localDate: String = "",
    val localTime: String = "",
    val durationMinutes: Int = 0,
    val gameFeeCents: Long? = null,
    /** O que a pessoa digitou em "Valor por jogo"; [gameFeeCents] é o valor lido dele (vazio = sem cobrança). */
    val gameFeeText: String = "",
    val venue: GameVenue? = null,
    val venueEditable: Boolean = false,
    val capacity: Int = 0,
    val confirmationLeadMinutes: Int = 0,
    /** "Repetir toda semana": só vale na criação ([GameEditorState.recurrenceOffered]). */
    val recurring: Boolean = false,
) {
    val hasDateTime: Boolean get() = localDate.isNotBlank() && localTime.isNotBlank()

}

enum class GameEditorFieldError {
    DateMissing,
    TimeMissing,
    VenueNameMissing,

    VenueAddressTooShort,
    VenueNameTooLong,
    VenueAddressTooLong,
    GameFeeInvalid,
    DurationInvalid,
}

sealed interface GameEditorIntent {
    data object Retry : GameEditorIntent
    data object Submit : GameEditorIntent
    data object OpenDateTimePicker : GameEditorIntent
    data class SaveDateTime(val date: String, val time: String) : GameEditorIntent
    data class SelectDuration(val minutes: Int) : GameEditorIntent
    data class UpdateVenueName(val name: String) : GameEditorIntent
    data class SelectPlaceSuggestion(val suggestion: PlaceSuggestion) : GameEditorIntent
    data object DismissPlaceSuggestions : GameEditorIntent
    data class UpdateVenueAddress(val address: String) : GameEditorIntent
    data class UpdateCapacity(val capacity: Int) : GameEditorIntent
    data class SelectConfirmationLead(val minutes: Int) : GameEditorIntent
    data class UpdateGameFee(val text: String) : GameEditorIntent
    data class ToggleRecurring(val value: Boolean) : GameEditorIntent
    data object DismissConflict : GameEditorIntent
    data object DismissSubscriptionRequired : GameEditorIntent
    /** Releitura do plano ao voltar de "Ver planos": o aviso do teste some depois de assinar. */
    data object RefreshPlan : GameEditorIntent
    data object OpenExistingGame : GameEditorIntent
}

sealed interface GameEditorEffect {
    /** [created] separa jogo novo de edição: só o primeiro leva à confirmação de "Jogo marcado". */
    data class Saved(val created: Boolean) : GameEditorEffect
    data class OpenGameDetail(val gameId: String) : GameEditorEffect
}
