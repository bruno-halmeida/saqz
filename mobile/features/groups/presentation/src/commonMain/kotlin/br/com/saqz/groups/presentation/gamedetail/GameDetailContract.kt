package br.com.saqz.groups.presentation.gamedetail

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.group.PromotionMode
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.home.HomeWaitlistKind

/** 4c — detalhe do jogo. Roster nominal e contagens ausentes ficam vazios/nulos até o domínio expô-los. */
@Immutable
data class GameDetailState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val error: GroupUiError? = null,
    val header: GameDetailHeaderUi? = null,
    val attendance: GameDetailAttendanceUi? = null,
    val confirmedRoster: List<GameDetailConfirmedUi> = emptyList(),
    val waitlist: List<GameDetailWaitlistUi> = emptyList(),
    val mensalistaPriority: Boolean = false,
    val promotionMode: PromotionMode = PromotionMode.FIFO,
    val isAdmin: Boolean = false,
    val cancelling: Boolean = false,
    val cancelFailed: Boolean = false,
    val cancelDialogOpen: Boolean = false,
    /** Rascunho (os da recorrência nascem assim) virando jogo publicado. */
    val publishing: Boolean = false,
    val publishFailed: Boolean = false,
    val promotingMemberId: String? = null,
    val promotionFailed: Boolean = false,
    val capacitySheetOpen: Boolean = false,
    val capacityDraft: Int = 2,
    val savingCapacity: Boolean = false,
    val capacityFailed: Boolean = false,
    val guest: GameGuestUi = GameGuestUi(),
    val response: GameDetailResponseUi = GameDetailResponseUi(),
)
@Immutable
data class GameDetailHeaderUi(
    val statusTone: GameDetailStatusTone,
    val confirmationDeadline: String,
    val weekday: GroupWeekday?,
    val dateTime: String,
    val venue: String,
    val durationMinutes: Int,
    val availableSpots: Int,
    val confirmationOpen: Boolean = true,
    /** Ainda dá para responder: jogo publicado e por começar. Depois do prazo o "Vou" entra pela espera. */
    val responseOpen: Boolean = true,
    val confirmationDeadlineWeekday: GroupWeekday? = null,
)
enum class GameDetailStatusTone { Draft, Published, Cancelled, Completed }

/**
 * A resposta de presença de quem olha ("Vou"/"Não vou"), no mesmo desenho do hero da Início e do
 * grupo. Todo membro joga, então vale para qualquer papel; o prazo (`header.confirmationOpen`)
 * fica no cabeçalho, que é de onde o convidado também o lê.
 */
@Immutable
data class GameDetailResponseUi(
    /** Só em jogo publicado. Rascunho, cancelado e encerrado não perguntam nada. */
    val visible: Boolean = false,
    /** `null` = sem resposta. */
    val status: AttendanceStatus? = null,
    /** Só preenchido em espera: o tipo muda a explicação e o texto do botão de sair. */
    val waitlistKind: HomeWaitlistKind? = null,
    val waitlistPosition: Long? = null,
    val responding: Boolean = false,
    val failed: Boolean = false,
    /** O organizador está sem plano: o aviso próprio substitui o "Tente novamente". */
    val planPaused: Boolean = false,
)

@Immutable
data class GameDetailAttendanceUi(
    val confirmed: Int,
    val capacity: Int,
    val availableSpots: Int,
    val declined: Int = 0,
    val pending: Int = 0,
)
@Immutable
data class GameDetailConfirmedUi(
    val id: String,
    val name: String,
    val isYou: Boolean,
    /** `null` = convidado ou membro sem posição; a tela traduz pelo mesmo mapa da lista de espera. */
    val position: AthletePosition?,
    /** `null` = membro. Convidado: de quem é e o que quem olha pode fazer com ele. */
    val guest: GameGuestRowUi? = null,
)
@Immutable
data class GameDetailWaitlistUi(
    val id: String,
    val name: String,
    val queuePosition: Long?,
    val athletePosition: AthletePosition?,
    val isMensalista: Boolean,
    /** `null` = membro. Convidado: de quem é e o que quem olha pode fazer com ele. */
    val guest: GameGuestRowUi? = null,
)

@Immutable
data class GameGuestRowUi(
    val hostId: String,
    val guestSeq: Int,
    val hostName: String,
    /** O convidado é de quem está olhando. */
    val isYours: Boolean,
    /** Anfitrião com confirmações abertas, ou gestor com jogo publicado. */
    val canRemove: Boolean,
)

enum class GameGuestHint { Default, NeedAnswer, Closed }

@Immutable
data class GameGuestRemovalUi(
    val rowId: String,
    val hostId: String,
    val guestSeq: Int,
    val name: String,
    val confirmed: Boolean,
)

@Immutable
data class GameGuestUi(
    /** Botão aparece só com jogo publicado e quem olha sendo membro que joga (tem `memberId`). */
    val visible: Boolean = false,
    val enabled: Boolean = false,
    val hint: GameGuestHint = GameGuestHint.Default,
    /** "R$ 25,00" quando o jogo tem taxa; `null` = a folha não fala de cobrança. */
    val feeLabel: String? = null,
    val sheetOpen: Boolean = false,
    val name: String = "",
    val adding: Boolean = false,
    val addFailed: Boolean = false,
    val removal: GameGuestRemovalUi? = null,
    val removing: Boolean = false,
    val removeFailed: Boolean = false,
    /** Nome de quem acabou de entrar (`joined = true`) ou sair; a UI mostra o toast e manda `DismissGuestNotice`. */
    val noticeName: String? = null,
    val noticeJoined: Boolean = true,
) {
    val canSubmit: Boolean get() = name.trim().length in MIN_NAME..MAX_NAME && !adding

    private companion object {
        const val MIN_NAME = 2
        const val MAX_NAME = 80
    }
}

sealed interface GameDetailIntent {
    data object Retry : GameDetailIntent

    /**
     * Volta à tela (aba, volta de uma filha, primeiro plano): recarga por baixo — sem esqueleto,
     * sem tela de erro, sem fechar folha nem descartar escrita em voo. É assim que a vaga de quem
     * foi promovido da fila chega sem reabrir o app.
     */
    data object Refresh : GameDetailIntent
    /** "Vou" / "Não vou" (e "Sair da lista", que é um "Não vou"): a resposta de quem olha. */
    data class Respond(val intent: AttendanceIntent) : GameDetailIntent
    data object Edit : GameDetailIntent
    data object OpenSettlement : GameDetailIntent
    data object RequestCancel : GameDetailIntent
    data object ConfirmCancel : GameDetailIntent
    data object DismissCancel : GameDetailIntent
    data object Publish : GameDetailIntent
    data class Promote(val memberId: String, val reason: String, val guestSeq: Int = 0) : GameDetailIntent
    data object OpenCapacitySheet : GameDetailIntent
    data class UpdateCapacity(val value: Int) : GameDetailIntent
    data object SaveCapacity : GameDetailIntent
    data object DismissCapacitySheet : GameDetailIntent
    data object OpenGuestSheet : GameDetailIntent
    data class UpdateGuestName(val value: String) : GameDetailIntent
    data object SubmitGuest : GameDetailIntent
    data object DismissGuestSheet : GameDetailIntent
    data class RequestRemoveGuest(val rowId: String) : GameDetailIntent
    data object ConfirmRemoveGuest : GameDetailIntent
    data object DismissRemoveGuest : GameDetailIntent
    data object DismissGuestNotice : GameDetailIntent
}
sealed interface GameDetailEffect {
    data object OpenEditor : GameDetailEffect
    data object OpenSettlement : GameDetailEffect
    data object Cancelled : GameDetailEffect
}
