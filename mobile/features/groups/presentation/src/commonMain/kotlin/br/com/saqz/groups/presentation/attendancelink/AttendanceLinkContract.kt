package br.com.saqz.groups.presentation.attendancelink

import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.attendance.share.AttendanceLinkDestination

enum class AttendanceLinkPhase {
    Loading,
    Confirmed,
    Waitlisted,
    /** O aviso do "não vou" está aberto; nada foi respondido ainda. */
    DeclineSheet,
    Declined,
    Invalid,
    /** Plano do organizador vencido ou pausado: o grupo não recebe atletas novos agora. */
    NotReceiving,
    /** O código existe, mas o jogo já começou. */
    GameStarted,
    GameCancelled,
    /** O organizador tirou o jogo de publicação. */
    GameUnpublished,
    Failed,
    Registration,
    Pending,
    /** O pedido de entrada não existe mais (recusado ou cancelado): só um toque da pessoa pede de novo. */
    RequestClosed,
    /** Jogo com posições (DEB-107): o "Vou" abriu a tela de escolher posição; nada foi respondido ainda. */
    ChoosingPosition,
}

/** A posição de quem respondeu pelo link em jogo com posições, para o cartão do resultado (bolinhas e "4 de 4 vagas"). */
data class AttendanceLinkPosition(val position: AthletePosition, val slots: Int, val confirmed: Int)

/**
 * O jogo do link, já formatado para o cartão (dia, hora e local). Para quem é membro vem do jogo; para quem
 * aguarda aprovação vem da prévia do convite, que é o próximo jogo do grupo e já traz o local escondido.
 */
data class AttendanceLinkGame(
    val weekdayCode: String,
    val date: String,
    val time: String,
    val venueName: String,
    val court: String?,
    val groupNext: Boolean = false,
    /** Vagas e confirmados do jogo; nulos na prévia do convite, que não traz a contagem. */
    val capacity: Int? = null,
    val confirmedCount: Int? = null,
)

data class AttendanceLinkState(
    val phase: AttendanceLinkPhase = AttendanceLinkPhase.Loading,
    val destination: AttendanceLinkDestination? = null,
    val game: AttendanceLinkGame? = null,
    /** O "não vou" já foi enviado e a resposta não voltou: o botão da folha não aceita outro toque. */
    val declining: Boolean = false,
    /** Vez na fila de quem ficou na lista de espera. */
    val waitlistPosition: Long? = null,
    /** Quem aguarda aprovação: o grupo do pedido, o nome dele, quem recebeu o pedido e se o cadastro já está guardado. */
    val pendingGroupId: String? = null,
    val pendingGroupName: String? = null,
    val pendingInviterName: String? = null,
    val pendingRegistrationSaved: Boolean = false,
    /** A atualização silenciosa da fase `Pending` está em voo; o cartão e os textos continuam na tela. */
    val refreshing: Boolean = false,
    /** Jogo com posições: a posição da vaga ou da espera de quem respondeu. */
    val position: AttendanceLinkPosition? = null,
)

/** `Undo`: desfaz a resposta que o link acabou de gravar (registra o "não vou" do mesmo jeito que o link de recusa). */
/** `Refresh`: refaz o pedido de entrada sem sair da fase `Pending` (a tela se atualiza ao reaparecer). */
/** `PickPosition`: reabre a tela de escolher posição (voltou dela sem confirmar). */
enum class AttendanceLinkIntent { Retry, Refresh, Decline, Undo, PickPosition }

sealed interface AttendanceLinkEffect {
    data class Register(val groupId: String) : AttendanceLinkEffect

    /** Jogo com posições (DEB-107): o "Vou" do link abre a tela de escolher posição. */
    data class PickPosition(val destination: AttendanceLinkDestination) : AttendanceLinkEffect
}
