package br.com.saqz.groups.presentation.games.detail

import br.com.saqz.groups.domain.attendance.AttendanceIntent

sealed interface GameDetailIntent {
    data object Refresh : GameDetailIntent

    data object RefreshAttendance : GameDetailIntent

    data object OpenEdit : GameDetailIntent

    data class RequestLifecycle(
        val action: GameLifecycleAction,
    ) : GameDetailIntent

    data object DismissConfirmation : GameDetailIntent

    data object ConfirmLifecycle : GameDetailIntent

    data object Reload : GameDetailIntent

    data class RequestAttendance(
        val action: AttendanceAction,
    ) : GameDetailIntent

    data object DismissAttendance : GameDetailIntent

    data object ConfirmAttendance : GameDetailIntent

    data class OverrideAttendance(
        val memberId: String,
        val intent: AttendanceIntent,
        val reason: String,
    ) : GameDetailIntent

    data class ChangeCapacity(
        val capacity: Int,
    ) : GameDetailIntent

    data class UpdateOverrideMember(
        val value: String,
    ) : GameDetailIntent

    data class UpdateOverrideReason(
        val value: String,
    ) : GameDetailIntent

    data class UpdateCapacityInput(
        val value: String,
    ) : GameDetailIntent

    data object RetryAttendance : GameDetailIntent

    data object RequestAttendanceLinkShare : GameDetailIntent

    data object RetryAttendanceLinkShare : GameDetailIntent

    data object RequestAttendanceImageShare : GameDetailIntent

    data object ConfirmAttendanceImageShare : GameDetailIntent

    data object CancelAttendanceImageShare : GameDetailIntent

    data class ReportAttendanceShareResult(
        val successful: Boolean,
    ) : GameDetailIntent
}
