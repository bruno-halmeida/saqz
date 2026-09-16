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
    Failed,
    Registration,
    Pending,
}

data class AttendanceLinkState(
    val phase: AttendanceLinkPhase = AttendanceLinkPhase.Loading,
    val destination: AttendanceLinkDestination? = null,
)

enum class AttendanceLinkIntent { Retry, Decline }

sealed interface AttendanceLinkEffect { data class Register(val groupId: String) : AttendanceLinkEffect }
