package br.com.saqz.groups.presentation.games.detail

import androidx.lifecycle.SavedStateHandle
import br.com.saqz.groups.domain.attendance.AttendanceGateway
import br.com.saqz.groups.domain.attendance.share.AttendanceSharingGateway
import br.com.saqz.groups.domain.game.GameGateway
import br.com.saqz.groups.domain.group.GroupRole

/**
 * Entry-compatible factory binding for the existing [GameDetailViewModel] (T14):
 * accepts the route identity (`groupId`, `gameId`, `role`) and an explicit
 * [SavedStateHandle] rather than resolving one from a singleton Koin binding, so
 * a future Navigation Compose 3 entry decorator (T16/T21) can supply its own
 * entry-scoped handle -- restoration then keys off the entry, not a shared
 * instance. No ViewModel behavior changes (LIFE-01..05, GROUPNAV-01, REG-04).
 */
class GameDetailViewModelFactory(
    private val gateway: GameGateway,
    private val attendanceGateway: AttendanceGateway?,
    private val attendanceShareGateway: AttendanceSharingGateway?,
) {
    fun create(
        groupId: String,
        gameId: String,
        role: GroupRole,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): GameDetailViewModel = GameDetailViewModel(
        gateway = gateway,
        groupId = groupId,
        gameId = gameId,
        role = role,
        attendanceGateway = attendanceGateway,
        attendanceShareGateway = attendanceShareGateway,
        savedStateHandle = savedStateHandle,
    )
}
