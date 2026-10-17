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
    Failed,
    Registration,
    Pending,
}

data class AttendanceLinkState(
    val phase: AttendanceLinkPhase = AttendanceLinkPhase.Loading,
    val destination: AttendanceLinkDestination? = null,
    /** O "não vou" já foi enviado e a resposta não voltou: o botão da folha não aceita outro toque. */
    val declining: Boolean = false,
)

enum class AttendanceLinkIntent { Retry, Decline, OpenAppDownload }

sealed interface AttendanceLinkEffect { data class Register(val groupId: String) : AttendanceLinkEffect }
