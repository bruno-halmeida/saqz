package br.com.saqz.groups.presentation.attendancelink

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
}

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
)

data class AttendanceLinkState(
    val phase: AttendanceLinkPhase = AttendanceLinkPhase.Loading,
    val destination: AttendanceLinkDestination? = null,
    val game: AttendanceLinkGame? = null,
    /** O "não vou" já foi enviado e a resposta não voltou: o botão da folha não aceita outro toque. */
    val declining: Boolean = false,
)

/** `Undo`: desfaz a resposta que o link acabou de gravar (registra o "não vou" do mesmo jeito que o link de recusa). */
enum class AttendanceLinkIntent { Retry, Decline, Undo, OpenAppDownload }

sealed interface AttendanceLinkEffect { data class Register(val groupId: String) : AttendanceLinkEffect }
