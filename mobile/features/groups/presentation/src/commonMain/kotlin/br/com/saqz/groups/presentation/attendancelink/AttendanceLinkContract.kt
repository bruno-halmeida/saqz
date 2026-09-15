package br.com.saqz.groups.presentation.attendancelink

import br.com.saqz.groups.domain.attendance.share.AttendanceLinkDestination

enum class AttendanceLinkPhase { Loading, Confirmed, Waitlisted, Invalid, Failed, Registration, Pending }
data class AttendanceLinkState(
    val phase: AttendanceLinkPhase = AttendanceLinkPhase.Loading,
    val destination: AttendanceLinkDestination? = null,
)

enum class AttendanceLinkIntent { Retry }

sealed interface AttendanceLinkEffect { data class Register(val groupId: String) : AttendanceLinkEffect }
