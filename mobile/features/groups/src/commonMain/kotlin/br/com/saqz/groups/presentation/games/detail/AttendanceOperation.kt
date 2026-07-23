package br.com.saqz.groups.presentation.games.detail

import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceVersionToken

internal sealed interface AttendanceOperation {
    val key: String

    data class Self(
        val action: AttendanceAction,
        override val key: String,
    ) : AttendanceOperation

    data class Override(
        val memberId: String,
        val intent: AttendanceIntent,
        val reason: String,
        override val key: String,
    ) : AttendanceOperation

    data class Capacity(
        val capacity: Int,
        val version: AttendanceVersionToken,
        override val key: String,
    ) : AttendanceOperation
}
