package br.com.saqz.groups.presentation.gamedetail

import br.com.saqz.groups.presentation.photo.MemberPhoto
import androidx.compose.runtime.Immutable
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.group.PromotionMode
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.presentation.GameCapacityLimits
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.home.HomeWaitlistKind

/** 4c — detalhe do jogo. Roster nominal e contagens ausentes ficam vazios/nulos até o domínio expô-los. */
@Immutable
data class GameDetailState(
    val isLoading: Boolean = true,
    /** Puxar para atualizar em andamento (GER-002); o jogo continua na tela. */
    val isRefreshing: Boolean = false,
    /** O último puxar falhou: o jogo fica e o aviso aparece. */
    val refreshFailed: Boolean = false,
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
    /** O plano ou o teste acaba antes do início do rascunho: publicar só depois de mudar a data (DEB-075). */
    val publishAfterPlanEnd: Boolean = false,
    val promotingMemberId: String? = null,
    val promotionFailed: Boolean = false,
    val capacitySheetOpen: Boolean = false,
    val capacityDraft: Int = GameCapacityLimits.Min,
    val savingCapacity: Boolean = false,
    val capacityFailed: Boolean = false,
    val guest: GameGuestUi = GameGuestUi(),
    val response: GameDetailResponseUi = GameDetailResponseUi(),
    /** "Tirar" e "Colocar alguém no jogo" do gestor (DEB-017). */
    val organizer: GameOrganizerUi = GameOrganizerUi(),
    /** DEB-102: vagas por posição do jogo; vazio = jogo sem posições (total e uma espera só, como antes). */
    val positions: List<GamePositionUi> = emptyList(),
    /** Seletor de posição aberto ("Vou" ou "Forçar entrada"). */
    val positionPicker: GamePositionPickerUi? = null,
    /** Título da barra de cima (DEB-116); `null` enquanto carrega, e a tela diz "Jogo". */
    val groupName: String? = null,
    /** Textos do cabeçalho azul que dependem do fuso do jogo. */
    val hero: GameDetailHeroUi? = null,
    /** Quem respondeu "Não vou" (DEB-116); `null` = servidor antigo, e a aba mostra só o número. */
    val declinedRoster: List<GameDetailConfirmedUi>? = null,
    /** "Ver no mapa" sem endereço ou sem app de mapa: a falha fica colada na linha da quadra. */
    val mapFailed: Boolean = false,
    /** "Avisar" de quem não respondeu, o mesmo envio do grupo. */
    val reminder: GameReminderUi = GameReminderUi(),
    /** Grupo do WhatsApp vinculado e ativo (só gestor): o "Avisar" manda a lista para lá mesmo com todo mundo respondido. */
    val whatsAppGroupActive: Boolean = false,
    /** Avulso em jogo com taxa e respostas abertas: a nota da cobrança, como no grupo. */
    val showsFeeNote: Boolean = false,
    /** PROM-010: a caixa de pagamento do avulso na espera; `null` fora dela. */
    val advancePayment: AdvancePaymentUi? = null,
    /** Linha da espera com o Recebi em voo: trava o toque duplo (GER-001). */
    val receivingPaymentId: String? = null,
    val receivePaymentFailed: Boolean = false,
    /** Linha cujo Colocar abriu a folha de "ainda não pagou" (PROM-010). */
    val advancePlaceRowId: String? = null,
    /** A diária já formatada ("R$ 20") para a linha antes do Vou (DEB-146); nula sem valor ou para quem não é avulso. */
    val feeNoteAmount: String? = null,
    /** Menu ⋯ da barra (dono/admin). */
    val gameMenuOpen: Boolean = false,
    /** Linha cujo ⋮ abriu a folha de ações da pessoa. */
    val personActionsId: String? = null,
    /**
     * JOGO-011: o jogo encerrado ainda tem diária pendente. `false` troca "Encerrar jogo e acertar" por "Ver acerto";
     * `null` (não carregou, ou não é gestor) mantém o botão de sempre.
     */
    val settlementPending: Boolean? = null,
    /** PRES-016: "Marcar falta" e "Desfazer" do gestor no jogo encerrado. */
    val noShow: GameNoShowUi = GameNoShowUi(),
) {
    /** Quem foi marcado como falta: só o gestor recebe a marca, então para o atleta a lista é vazia. */
    val noShowRoster: List<GameDetailConfirmedUi> get() = confirmedRoster.filter { it.noShow }

    /** "Jogaram": os confirmados sem quem faltou. */
    val playedRoster: List<GameDetailConfirmedUi> get() = confirmedRoster.filterNot { it.noShow }

    /** O gestor marca ou desfaz a falta: jogo encerrado e dentro dos 7 dias depois do fim (PRES-016). */
    val canMarkNoShow: Boolean
        get() = isAdmin && header?.statusTone == GameDetailStatusTone.Completed && noShow.until != null

    val hasPositions: Boolean get() = positions.isNotEmpty()

    /** A posição com mais vagas livres (empate pela ordem do enum): a mesma regra que o servidor usa sem escolha. */
    val mostFreePosition: AthletePosition?
        get() = positions.filter { it.slots > 0 }
            .sortedWith(compareByDescending<GamePositionUi> { it.slots - it.confirmed }.thenBy { it.position.ordinal })
            .firstOrNull()?.position

    /** Dono e admins mexem na presença de outra pessoa enquanto o jogo está publicado, inclusive depois do início. */
    val canManageAttendance: Boolean get() = isAdmin && header?.statusTone == GameDetailStatusTone.Published

    /** Aviso do gestor no jogo cancelado, com caminho até as cobranças para conferir (DEB-053). */
    val canReviewFinance: Boolean
        get() = isAdmin && header?.statusTone == GameDetailStatusTone.Cancelled && header.financeReviewRequired

    /** O servidor põe quem entrar na espera: jogo cheio. */
    val placeGoesToWaitlist: Boolean get() = attendance?.let { it.availableSpots <= 0 } == true

    /** Depois do início a espera não anda mais, então com o jogo cheio não adianta colocar ninguém. */
    val placeBlocked: Boolean get() = placeGoesToWaitlist && header?.gameStarted == true

    /** Número da aba Não vão: o tamanho da lista, ou a contagem do servidor antigo. */
    val declinedCount: Int get() = declinedRoster?.size ?: attendance?.declined ?: 0

    /** A aba abre em Na espera para quem está na espera; para o resto, em Vão. */
    val initialTab: GameDetailTab
        get() = if (response.status == AttendanceStatus.Waitlisted) GameDetailTab.Waiting else GameDetailTab.Going

    /** Dono e admins mexem no jogo pelo ⋯ da barra; o menu sempre tem "Editar jogo". */
    val hasGameMenu: Boolean get() = isAdmin && header != null

    /** "Ajustar vagas": publicado, por começar e sem posições (com posições o total é a soma delas, DEB-102). */
    val canAdjustCapacity: Boolean
        get() = isAdmin && header?.statusTone == GameDetailStatusTone.Published && header.gameStarted.not() && !hasPositions

    /**
     * Painel "N pessoas sem resposta" com o Avisar, do jogo publicado até o início: com grupo do WhatsApp ativo fica
     * sempre (a lista vai para o grupo); sem ele, some sem pendente. Fica depois do envio, para mandar de novo.
     */
    val canNotify: Boolean
        get() = isAdmin && header?.statusTone == GameDetailStatusTone.Published && header.canRespond &&
            ((attendance?.pending ?: 0) > 0 || whatsAppGroupActive || reminder.notifiedCount != null)

    /** A linha verde-limão do que falta (DEB-116). */
    val limeLine: GameLimeLine? get() = resolveLimeLine()

    /** O gestor coloca da espera no jogo: escolha manual e jogo por começar (DEB-018). */
    val canPromote: Boolean
        get() = isAdmin && header?.statusTone == GameDetailStatusTone.Published && header.gameStarted.not() &&
            promotionMode == PromotionMode.MANUAL
}

enum class GameDetailTab { Going, Waiting, NoShow, Declined }

/** PRES-016 · "Faltou" no jogo encerrado (só gestor). */
@Immutable
data class GameNoShowUi(
    /** "27 de outubro": até quando dá para marcar; nulo fora do prazo (ou jogo não encerrado). */
    val until: String? = null,
    /** "terça, 20 de outubro": o jogo, na linha de apoio da folha. */
    val gameDay: String = "",
    /** "R$ 25,00" quando o jogo tem diária; a folha cita o valor só para quem paga diária. */
    val feeLabel: String? = null,
    /** Linha com a marca ou o desfazer em voo: trava o toque duplo (GER-001). */
    val sendingId: String? = null,
    val failed: Boolean = false,
)

/** O que falta no jogo, na linha verde-limão do cabeçalho. */
sealed interface GameLimeLine {
    /** "Restam 2 vagas": jogo sem posições, de 1 a 3 vagas, para quem ainda não respondeu. */
    data class SpotsLeft(val spots: Int) : GameLimeLine

    /** "Tem vaga para central e oposto(a)": o atleta sem resposta, em jogo com posições. */
    data class OpenPositions(val positions: List<AthletePosition>) : GameLimeLine

    /** "Falta 1 central e 2 ponteiros(as)": o que o gestor ainda precisa fechar. */
    data class Missing(val missing: List<Pair<AthletePosition, Int>>) : GameLimeLine
}

@Immutable
data class GameDetailHeroUi(
    /** "Terça, 19h30" */
    val title: String,
    /** "28 de julho" */
    val date: String,
    /** "21h30": início mais a duração, no fuso do jogo. */
    val endTime: String,
    /** Vazio = jogo sem endereço: sem a linha do endereço e sem o "Ver no mapa". */
    val address: String,
    /** "Jogo de terça, 28/07" (título do menu ⋯). */
    val menuTitle: String,
    /** "Jogo de terça, 28/07 · 19h30" (barra do histórico de respostas, JOGO-012). */
    val historySubtitle: String = menuTitle,
    /**
     * "Prioridade dos mensalistas até hoje às 17h30.", a mesma frase da Início e do grupo; vazia quando o jogo não tem
     * janela de prioridade, e a linha some.
     */
    val deadlineLine: String = "",
)

@Immutable
data class GameReminderUi(
    val sending: Boolean = false,
    val failed: Boolean = false,
    val refused: Boolean = false,
    /** Quantos receberam; preenchido depois do envio. */
    val notifiedCount: Int? = null,
)
/** Uma posição do jogo (DEB-102): "2/2 pontas". */
@Immutable
data class GamePositionUi(
    val position: AthletePosition,
    val slots: Int,
    val confirmed: Int,
    val missing: Int,
    val waitlisted: Int,
) {
    val full: Boolean get() = missing <= 0
}

/** Para quê o seletor de posição está aberto. */
sealed interface GamePositionPurpose {
    /** O gestor confirma [memberId] em [position] mesmo com ela cheia. */
    data class Force(val memberId: String, val name: String, val position: AthletePosition) : GamePositionPurpose
}

@Immutable
data class GamePositionPickerUi(
    val purpose: GamePositionPurpose,
    /** A do perfil de quem responde, se o jogo tem vaga nela; só uma sugestão. */
    val suggested: AthletePosition?,
    val selected: AthletePosition?,
    val sending: Boolean = false,
    val failed: Boolean = false,
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
    /**
     * O servidor recusou a resposta por prazo vencido (`ATTENDANCE_DEADLINE_PASSED`, backend v.0.0.17):
     * as respostas fecham com a frase do prazo, e o selo segue "Prazo encerrado" — "Em andamento" é só
     * do jogo que começou.
     */
    val closedByDeadline: Boolean = false,
    val confirmationDeadlineWeekday: GroupWeekday? = null,
    /** Jogo cancelado com cobrança já paga ou dispensada: o gestor confere se devolve (DEB-053). */
    val financeReviewRequired: Boolean = false,
) {
    /** Os botões de resposta valem: o jogo não começou e o servidor não fechou as respostas pelo prazo. */
    val canRespond: Boolean get() = responseOpen && !closedByDeadline

    /**
     * O jogo publicado já começou (ou o servidor o fechou): ninguém sobe da espera e as vagas não mudam,
     * então "Colocar no jogo" e o ajuste de vagas somem.
     */
    val gameStarted: Boolean get() = statusTone == GameDetailStatusTone.Published && !responseOpen
}
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
    /** Falhou por falta de rede ("Sem conexão. Sua resposta não foi enviada."). */
    val offline: Boolean = false,
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
    /** O gestor pode tirar esta pessoa (membro que não é ele mesmo). */
    val canRemove: Boolean = false,
    /** Mensalista não tem diária por jogo. */
    val isMensalista: Boolean = false,
    /** O gestor colocou a pessoa acima das vagas da posição (DEB-102). */
    val forced: Boolean = false,
    /** Foto de perfil (MEM-006); convidado nunca tem. */
    val photo: MemberPhoto? = null,
    /** PRES-016: confirmou e não apareceu; só vem marcado para dono e admin. */
    val noShow: Boolean = false,
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
    /** O gestor pode tirar esta pessoa (membro que não é ele mesmo). */
    val canRemove: Boolean = false,
    /** Quem olha está nesta linha da espera (DEB-116): a linha ganha o fundo lima e o "Você". */
    val isYou: Boolean = false,
    /** Foto de perfil (MEM-006); convidado nunca tem. */
    val photo: MemberPhoto? = null,
    /** PROM-010: a linha do pagamento na espera do gestor (pagamento antecipado); `null` sem diária ou para o atleta. */
    val payment: WaitlistPaymentUi? = null,
)

/**
 * PROM-010 (prancha Antecipado-espera): "Pagou R$ 20" (verde) ou "Falta pagar R$ 20" com Recebi; convidado diz o
 * nome do anfitrião, que é quem paga. [chargeId] e [version] vão no Recebi (rota de status, `If-Match`).
 */
@Immutable
data class WaitlistPaymentUi(
    val amount: String,
    val paid: Boolean,
    val chargeId: String,
    val version: Long,
    val hostName: String? = null,
)

/**
 * PROM-010 (prancha Antecipado-avulso): o avulso na espera de grupo com pagamento antecipado. Pendente mostra
 * "Pague R$ 20 para entrar" com Copiar o Pix do grupo ([pixKey]) ou, com recebimento pelo app, o Pagar.
 */
@Immutable
data class AdvancePaymentUi(
    val amount: String,
    val paid: Boolean,
    val pixKey: String? = null,
    val payInApp: Boolean = false,
)

/** Por que "Tirar"/"Colocar" não deu certo: o jogo fechou para mudanças, ou uma falha qualquer (tentar de novo). */
enum class GameOrganizerFailure { Generic, Closed }

@Immutable
data class GameMemberRemovalUi(
    val memberId: String,
    val name: String,
    val confirmed: Boolean,
    /** A pessoa tem diária neste jogo (avulso confirmado, jogo com valor): tirar cancela a cobrança. */
    val feeCancelled: Boolean = false,
    /** A fila anda de verdade: antes do início, ordem de chegada e alguém na espera. */
    val queueMoves: Boolean = false,
    /** "Outro motivo" aberto: o campo de texto aparece. Fechado, vai o motivo padrão. */
    val reasonOpen: Boolean = false,
    val reason: String = "",
)

@Immutable
data class GameMemberCandidateUi(
    val id: String,
    val name: String,
    val position: AthletePosition?,
    val photo: MemberPhoto? = null,
)

@Immutable
data class GameOrganizerUi(
    val removal: GameMemberRemovalUi? = null,
    val removing: Boolean = false,
    val removeFailure: GameOrganizerFailure? = null,
    val placeOpen: Boolean = false,
    val candidates: List<GameMemberCandidateUi> = emptyList(),
    val selectedId: String? = null,
    val placing: Boolean = false,
    val placeFailure: GameOrganizerFailure? = null,
    /** "Outro motivo" aberto na folha de colocar: o campo de texto aparece. Fechado, vai o motivo padrão. */
    val placeReasonOpen: Boolean = false,
    val placeReason: String = "",
    /** Posição do candidato em jogo com posições (DEB-102). */
    val placePosition: AthletePosition? = null,
) {
    val selected: GameMemberCandidateUi? get() = candidates.firstOrNull { it.id == selectedId }
}

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
    /** Nome do anfitrião quando quem tira é o gestor: ele recebe um aviso (PRES-013). Nulo se o anfitrião é quem tira. */
    val notifiedHostName: String? = null,
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
    /** Posição escolhida para o convidado em jogo com posições (DEB-102); nula em jogo sem posições. */
    val position: AthletePosition? = null,
    /** Nome de quem acabou de entrar (`joined = true`) ou sair; a UI mostra o toast e manda `DismissGuestNotice`. */
    val noticeName: String? = null,
    val noticeJoined: Boolean = true,
) {
    val canSubmit: Boolean get() = name.trim().length in MIN_NAME..MAX_NAME && !adding

    /** Em jogo com posições o convidado também precisa de uma posição escolhida (DEB-102). */
    fun canSubmitWith(needsPosition: Boolean): Boolean = canSubmit && (!needsPosition || position != null)

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

    /** Puxar para atualizar (GER-002): como [Refresh], com a bola na tela e aviso se falhar. */
    data object PullRefresh : GameDetailIntent

    data object DismissRefreshFailed : GameDetailIntent
    /** "Vou" / "Não vou" (e "Sair da lista", que é um "Não vou"): a resposta de quem olha. */
    data class Respond(val intent: AttendanceIntent) : GameDetailIntent

    /** DEB-102: posição escolhida no seletor do "Vou" (ou da entrada forçada). */
    data object DismissPositionPicker : GameDetailIntent
    data class UpdateGuestPosition(val position: AthletePosition) : GameDetailIntent
    data class SelectPlacePosition(val position: AthletePosition) : GameDetailIntent

    /** O gestor confirma uma pessoa da espera numa posição cheia (fica acima das vagas dela). */
    data class RequestForceEntry(val rowId: String) : GameDetailIntent
    data class ConfirmForceEntry(val defaultReason: String) : GameDetailIntent
    data object Edit : GameDetailIntent

    /** "Histórico de respostas" do ⋯ (JOGO-012, só gestor). */
    data object OpenHistory : GameDetailIntent
    data object OpenSettlement : GameDetailIntent
    data object RequestCancel : GameDetailIntent
    data object ConfirmCancel : GameDetailIntent
    data object DismissCancel : GameDetailIntent
    data object Publish : GameDetailIntent
    data class Promote(
        val memberId: String,
        val reason: String,
        val guestSeq: Int = 0,
        /** PROM-010 "Colocar, já pagou": dá baixa na diária na mesma transação. */
        val markPaid: Boolean = false,
    ) : GameDetailIntent

    /** PROM-010: Recebi na linha da espera (pagamento antecipado). */
    data class ReceiveWaitlistPayment(val rowId: String) : GameDetailIntent

    /** PROM-010: Colocar quem não pagou abre a folha; as duas saídas mandam [Promote] com o `markPaid` certo. */
    data class OpenAdvancePlace(val rowId: String) : GameDetailIntent
    data object DismissAdvancePlace : GameDetailIntent
    data class ConfirmAdvancePlace(val markPaid: Boolean, val reason: String) : GameDetailIntent

    /** PROM-010: o avulso na espera copia o Pix do grupo ou abre o Pagar. */
    data object CopyGroupPix : GameDetailIntent
    data object PayAdvance : GameDetailIntent
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

    /** `rowId` = id do membro (linha de confirmado ou de espera). */
    data class RequestRemoveMember(val rowId: String) : GameDetailIntent
    data object ToggleRemoveReason : GameDetailIntent
    data class UpdateRemoveReason(val value: String) : GameDetailIntent

    /** [defaultReason] vai quando "Outro motivo" está fechado, vazio ou com menos de 2 caracteres. */
    data class ConfirmRemoveMember(val defaultReason: String) : GameDetailIntent
    data object DismissRemoveMember : GameDetailIntent
    data object OpenPlaceSheet : GameDetailIntent
    data class SelectPlaceCandidate(val memberId: String) : GameDetailIntent
    data object TogglePlaceReason : GameDetailIntent
    data class UpdatePlaceReason(val value: String) : GameDetailIntent

    /** [defaultReason] vai quando "Outro motivo" está fechado, vazio ou com menos de 2 caracteres. */
    data class ConfirmPlaceMember(val defaultReason: String) : GameDetailIntent
    data object DismissPlaceSheet : GameDetailIntent

    /** "Ver no mapa" do cabeçalho (DEB-116). */
    data object OpenVenueMap : GameDetailIntent
    data object MapOpenFailed : GameDetailIntent

    /** "Avisar" quem não respondeu. */
    data object NotifyPending : GameDetailIntent
    data object OpenGameMenu : GameDetailIntent
    data object DismissGameMenu : GameDetailIntent
    data class OpenPersonActions(val rowId: String) : GameDetailIntent
    data object DismissPersonActions : GameDetailIntent

    /** PRES-016: "Marcar falta" ([marked]) ou "Desfazer" na pessoa [rowId] do jogo encerrado. */
    data class SetNoShow(val rowId: String, val marked: Boolean) : GameDetailIntent
}
sealed interface GameDetailEffect {
    data object OpenEditor : GameDetailEffect
    data object OpenSettlement : GameDetailEffect
    data object Cancelled : GameDetailEffect

    /** "Vou" em jogo com posições: abre a tela de escolher posição (DEB-107). */
    data object PickPosition : GameDetailEffect

    /** "Ver no mapa": o endereço do jogo. */
    data class OpenMap(val address: String) : GameDetailEffect

    /** "Histórico de respostas" (JOGO-012): "Jogo de quinta, 22/10 · 20h00" vai para a barra da tela. */
    data class OpenHistory(val subtitle: String) : GameDetailEffect

    /** PROM-010: Copiar o Pix do grupo (área de transferência, morre na Root). */
    data class CopyPix(val key: String) : GameDetailEffect

    /** PROM-010: o Pagar que já existe (recebimento pelo app ativo). */
    data class OpenPayment(val groupId: String, val groupName: String) : GameDetailEffect
}
