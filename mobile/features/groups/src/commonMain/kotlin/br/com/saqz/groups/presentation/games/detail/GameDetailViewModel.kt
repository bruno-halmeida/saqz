package br.com.saqz.groups.presentation.games.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.serialization.saved
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import kotlinx.serialization.Serializable
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.attendance.*
import br.com.saqz.groups.domain.attendance.share.AttendanceLinkUrl
import br.com.saqz.groups.domain.attendance.share.AttendanceSharingGateway
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameError
import br.com.saqz.groups.domain.game.GameGateway
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.GameVersionToken
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.presentation.attendance.share.AttendanceShareImage
import br.com.saqz.groups.presentation.attendance.share.AttendanceShareImageModel
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlin.random.Random
import kotlin.time.Clock

class GameDetailViewModel(
    private val gateway: GameGateway,
    groupId: String,
    gameId: String,
    role: GroupRole,
    private val attendanceGateway: AttendanceGateway? = null,
    private val attendanceShareGateway: AttendanceSharingGateway? = null,
    private val keys: AttendanceCommandKeyFactory = AttendanceCommandKeyFactory { "attendance-${Random.nextLong()}" },
    private val now: () -> Instant = { Clock.System.now() },
    savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : MviViewModel<GameDetailState, GameDetailIntent, GameDetailEffect>(GameDetailState(groupId, gameId, role)) {
    private var retryOperation: AttendanceOperation? = null

    // No durable draft exists for this screen, so the SavedStateHandle snapshot is the
    // authoritative restore for in-progress organizer input after process death (PMVI-018).
    private var inputSnapshot by savedStateHandle.saved { GameDetailInputSnapshot() }

    init {
        update {
            it.copy(
                overrideMemberId = inputSnapshot.memberId,
                overrideReason = inputSnapshot.reason,
                capacityInput = inputSnapshot.capacityInput,
            )
        }
        load()
    }

    override fun onIntent(intent: GameDetailIntent) {
        when (intent) {
            GameDetailIntent.Refresh, GameDetailIntent.Reload -> {
                load()
            }

            GameDetailIntent.RefreshAttendance -> {
                refreshAttendance()
            }

            GameDetailIntent.OpenEdit -> {
                openEdit()
            }

            is GameDetailIntent.RequestLifecycle -> {
                request(intent.action)
            }

            GameDetailIntent.DismissConfirmation -> {
                if (!state.value.isMutating) update { state.value.copy(pendingAction = null) }
            }

            GameDetailIntent.ConfirmLifecycle -> {
                confirm()
            }

            is GameDetailIntent.RequestAttendance -> {
                requestAttendance(intent.action)
            }

            GameDetailIntent.DismissAttendance -> {
                if (!state.value.isAttendanceMutating) {
                    update {
                        state.value.copy(pendingAttendanceAction = null, attendanceCommandKey = null)
                    }
                }
            }

            GameDetailIntent.ConfirmAttendance -> {
                confirmAttendance()
            }

            is GameDetailIntent.OverrideAttendance -> {
                override(intent)
            }

            is GameDetailIntent.ChangeCapacity -> {
                capacity(intent.capacity)
            }

            is GameDetailIntent.UpdateOverrideMember -> {
                updateInput { it.copy(overrideMemberId = intent.value) }
            }

            is GameDetailIntent.UpdateOverrideReason -> {
                updateInput { it.copy(overrideReason = intent.value) }
            }

            is GameDetailIntent.UpdateCapacityInput -> {
                updateInput { it.copy(capacityInput = intent.value) }
            }

            GameDetailIntent.RetryAttendance -> {
                retryOperation?.let(::execute)
            }

            GameDetailIntent.RequestAttendanceLinkShare -> {
                shareAttendanceLink(false)
            }

            GameDetailIntent.RetryAttendanceLinkShare -> {
                shareAttendanceLink(true)
            }

            GameDetailIntent.RequestAttendanceImageShare -> {
                requestAttendanceImageShare()
            }

            GameDetailIntent.ConfirmAttendanceImageShare -> {
                confirmAttendanceImageShare()
            }

            GameDetailIntent.CancelAttendanceImageShare -> {
                update { state.value.copy(showAttendanceSharePrivacy = false) }
            }

            is GameDetailIntent.ReportAttendanceShareResult -> {
                reportAttendanceShareResult(intent.successful)
            }
        }
    }

    private fun load() {
        if (state.value.isMutating || state.value.isAttendanceMutating) return
        val current = state.value
        update { current.copy(isLoading = true, error = null, attendanceError = null, reloadAvailable = false, pendingAction = null) }
        viewModelScope.launch {
            when (val result = gateway.read(GroupId(current.groupId), current.gameId)) {
                is SaqzResult.Success -> {
                    update {
                        state.value.copy(
                            game = result.value.game,
                            version = result.value.version,
                            attendanceOpen = result.value.game.attendanceOpen(),
                            error = null,
                        )
                    }
                    loadAttendance(current.groupId, current.gameId)
                }

                is SaqzResult.Failure -> {
                    update {
                        state.value.copy(
                            game = null,
                            version = null,
                            isLoading = false,
                            error =
                                if (result.error ==
                                    GameError.HiddenResource
                                ) {
                                    GameDetailError.HIDDEN
                                } else {
                                    GameDetailError.UNAVAILABLE
                                },
                        )
                    }
                }
            }
        }
    }

    private suspend fun loadAttendance(
        groupId: String,
        gameId: String,
    ) {
        val attendance = attendanceGateway
        if (attendance == null) {
            update { state.value.copy(isLoading = false) }
            return
        }
        when (val result = attendance.read(GroupId(groupId), gameId)) {
            is SaqzResult.Success -> {
                update {
                    state.value.copy(
                        attendance = result.value,
                        isLoading = false,
                        attendanceError = null,
                        attendanceErrorMessage = null,
                    )
                }
            }

            is SaqzResult.Failure -> {
                val failure = result.error.toPresentation()
                update { state.value.copy(isLoading = false, attendanceError = failure.error, attendanceErrorMessage = failure.message) }
            }
        }
    }

    private fun refreshAttendance() {
        val current = state.value
        if (current.isAttendanceMutating ||
            attendanceGateway == null
        ) {
            return
        }
        viewModelScope.launch { loadAttendance(current.groupId, current.gameId) }
    }

    private fun openEdit() {
        val current = state.value
        if (current.canEdit &&
            !current.isMutating
        ) {
            emit(GameDetailEffect.OpenEdit(current.groupId, current.gameId))
        }
    }

    private fun request(action: GameLifecycleAction) {
        val current = state.value
        if (!current.isMutating &&
            action in current.actions
        ) {
            update { current.copy(pendingAction = action, error = null) }
        }
    }

    private fun confirm() {
        val current = state.value
        val version =
            current.version ?: return
        val action =
            current.pendingAction ?: return
        if (current.isMutating ||
            action !in current.actions
        ) {
            return
        }
        update { current.copy(isMutating = true, error = null) }
        viewModelScope.launch {
            when (val result = gateway.lifecycle(GroupId(current.groupId), current.gameId, version, action.toDomain())) {
                is SaqzResult.Success -> {
                    update {
                        state.value.copy(
                            game = result.value.game,
                            version = result.value.version,
                            isMutating = false,
                            pendingAction = null,
                            reloadAvailable = false,
                        )
                    }
                    emit(GameDetailEffect.LifecycleApplied(action))
                }

                is SaqzResult.Failure -> {
                    fail(result.error)
                }
            }
        }
    }

    private fun requestAttendance(action: AttendanceAction) {
        val current = state.value
        if (current.isAttendanceMutating || action !in current.attendanceActions) return
        val key = if (current.pendingAttendanceAction == action) current.attendanceCommandKey ?: keys.create() else keys.create()
        update { current.copy(pendingAttendanceAction = action, attendanceCommandKey = key, attendanceError = null) }
    }

    private fun confirmAttendance() {
        val current = state.value
        val action =
            current.pendingAttendanceAction ?: return
        val key = current.attendanceCommandKey ?: return
        execute(AttendanceOperation.Self(action, key))
    }

    private fun override(intent: GameDetailIntent.OverrideAttendance) {
        val current = state.value
        if (!current.organizer ||
            current.isAttendanceMutating
        ) {
            return
        }
        if (intent.memberId.isBlank() ||
            intent.reason.trim().length < 2
        ) {
            update { current.copy(attendanceError = GameDetailError.VALIDATION, attendanceErrorMessage = null) }
            return
        }
        execute(AttendanceOperation.Override(intent.memberId, intent.intent, intent.reason.trim(), keys.create()))
    }

    private fun capacity(value: Int) {
        val current = state.value
        val version =
            current.version ?: return
        if (!current.organizer ||
            current.isAttendanceMutating
        ) {
            return
        }
        if (value !in
            2..100
        ) {
            update { current.copy(attendanceError = GameDetailError.VALIDATION, attendanceErrorMessage = null) }
            return
        }
        execute(AttendanceOperation.Capacity(value, AttendanceVersionToken(version.value), keys.create()))
    }

    private fun execute(operation: AttendanceOperation) {
        val attendance = attendanceGateway ?: return
        val current = state.value
        if (current.isAttendanceMutating) return
        retryOperation = operation
        update {
            current.copy(
                isAttendanceMutating = true,
                attendanceError = null,
                attendanceErrorMessage = null,
                retryAttendanceAvailable = false,
            )
        }
        viewModelScope.launch {
            when (operation) {
                is AttendanceOperation.Self -> {
                    when (
                        val result =
                            attendance.respond(
                                GroupId(current.groupId),
                                current.gameId,
                                SelfAttendanceCommand(operation.key, operation.action.intent()),
                            )
                    ) {
                        is SaqzResult.Success -> applied(result.value.value, result.value.version, true)
                        is SaqzResult.Failure -> attendanceFailed(result.error)
                    }
                }

                is AttendanceOperation.Override -> {
                    when (
                        val result =
                            attendance.override(
                                GroupId(current.groupId),
                                current.gameId,
                                OverrideAttendanceCommand(operation.key, operation.memberId, operation.intent, operation.reason),
                            )
                    ) {
                        is SaqzResult.Success -> applied(result.value.value, result.value.version, false)
                        is SaqzResult.Failure -> attendanceFailed(result.error)
                    }
                }

                is AttendanceOperation.Capacity -> {
                    when (
                        val result =
                            attendance.capacity(
                                GroupId(current.groupId),
                                current.gameId,
                                operation.version,
                                AttendanceCapacityCommand(operation.key, operation.capacity),
                            )
                    ) {
                        is SaqzResult.Success -> capacityApplied(result.value)
                        is SaqzResult.Failure -> attendanceFailed(result.error)
                    }
                }
            }
        }
    }

    private fun applied(
        result: AttendanceMutation,
        version: AttendanceVersionToken,
        updateOwn: Boolean,
    ) {
        retryOperation = null
        val detail = if (updateOwn) result.detail.copy(ownAttendance = result.attendance) else result.detail
        update {
            state.value
                .copy(
                    attendance = detail,
                    attendanceVersion = version,
                    isAttendanceMutating = false,
                    pendingAttendanceAction = null,
                    attendanceCommandKey = null,
                    attendanceError = null,
                    attendanceErrorMessage = null,
                    retryAttendanceAvailable = false,
                ).syncGame(detail)
        }
        emit(GameDetailEffect.AttendanceApplied(result.attendance.status, result.promotedCount, true))
    }

    private fun capacityApplied(result: VersionedAttendanceCapacity) {
        retryOperation = null
        update {
            state.value
                .copy(
                    attendance = result.value.detail,
                    version = GameVersionToken(result.version.value),
                    isAttendanceMutating = false,
                    attendanceError = null,
                    attendanceErrorMessage = null,
                    retryAttendanceAvailable = false,
                ).syncGame(result.value.detail)
        }
        emit(GameDetailEffect.CapacityApplied(result.value.capacity, result.value.promotedCount))
    }

    private fun attendanceFailed(failure: AttendanceError) {
        val mapped = failure.toPresentation()
        update {
            state.value.copy(
                isAttendanceMutating = false,
                attendanceError = mapped.error,
                attendanceErrorMessage = mapped.message,
                retryAttendanceAvailable =
                    mapped.error in setOf(GameDetailError.CONFLICT, GameDetailError.UNAVAILABLE),
                reloadAvailable = mapped.error == GameDetailError.CONFLICT,
            )
        }
    }

    private fun fail(failure: GameError) {
        val error =
            when (failure) {
                GameError.Conflict -> GameDetailError.CONFLICT
                GameError.HiddenResource -> GameDetailError.HIDDEN
                GameError.InvalidLifecycle -> GameDetailError.INVALID_LIFECYCLE
                else -> GameDetailError.UNAVAILABLE
            }
        update {
            state.value.copy(
                isMutating = false,
                pendingAction = null,
                error = error,
                reloadAvailable =
                    error == GameDetailError.CONFLICT || error == GameDetailError.INVALID_LIFECYCLE,
            )
        }
    }

    private fun shareAttendanceLink(retry: Boolean) {
        val current = state.value
        val url = current.attendanceLinkUrl
        if (retry && url != null) {
            emit(GameDetailEffect.ShareAttendanceLink(AttendanceLinkUrl(url)))
            return
        }
        if (current.isAttendanceLinkLoading || !current.organizer || current.game?.status != GameStatus.Published ||
            !current.attendanceOpen
        ) {
            return
        }
        val share = attendanceShareGateway ?: return
        update { current.copy(isAttendanceLinkLoading = true, attendanceLinkError = null) }
        viewModelScope.launch {
            when (val result = share.rotateLink(GroupId(current.groupId), current.gameId)) {
                is SaqzResult.Success -> {
                    update {
                        state.value.copy(
                            isAttendanceLinkLoading = false,
                            attendanceLinkUrl = result.value.value,
                            attendanceLinkError = null,
                        )
                    }
                    emit(GameDetailEffect.ShareAttendanceLink(result.value))
                }

                is SaqzResult.Failure -> {
                    update { state.value.copy(isAttendanceLinkLoading = false, attendanceLinkError = GameDetailError.UNAVAILABLE) }
                }
            }
        }
    }

    private fun requestAttendanceImageShare() {
        val current = state.value
        if (current.isAttendanceShareSnapshotLoading || !current.organizer) return
        if (current.attendanceShareSnapshot != null) {
            update { current.copy(showAttendanceSharePrivacy = true, attendanceShareError = null) }
            return
        }
        val share = attendanceShareGateway ?: return
        update { current.copy(isAttendanceShareSnapshotLoading = true, attendanceShareError = null) }
        viewModelScope.launch {
            when (val result = share.readSnapshot(GroupId(current.groupId), current.gameId)) {
                is SaqzResult.Success -> {
                    val image = AttendanceShareImage.from(result.value)
                    update {
                        state.value.copy(
                            isAttendanceShareSnapshotLoading = false,
                            attendanceShareSnapshot = image,
                            attendanceShareError = null,
                            showAttendanceSharePrivacy = true,
                        )
                    }
                }

                is SaqzResult.Failure -> {
                    update {
                        state.value.copy(
                            isAttendanceShareSnapshotLoading = false,
                            attendanceShareError = GameDetailError.UNAVAILABLE,
                        )
                    }
                }
            }
        }
    }

    private fun confirmAttendanceImageShare() {
        val image =
            state.value.attendanceShareSnapshot ?: return
        update { state.value.copy(showAttendanceSharePrivacy = false) }
        emit(GameDetailEffect.ShareAttendanceImage(image))
    }

    private fun reportAttendanceShareResult(successful: Boolean) {
        if (!successful &&
            state.value.attendanceShareSnapshot != null
        ) {
            update { state.value.copy(attendanceShareError = GameDetailError.UNAVAILABLE) }
        }
    }

    private fun Game.attendanceOpen() =
        status == GameStatus.Published && runCatching { now() <= Instant.parse(confirmationDeadline) }.getOrDefault(false)

    private fun AttendanceAction.intent() = if (this == AttendanceAction.CONFIRM) AttendanceIntent.Confirm else AttendanceIntent.Decline

    private fun AttendanceError.toPresentation() =
        when (this) {
            AttendanceError.HiddenResource -> {
                AttendanceFailure(GameDetailError.HIDDEN)
            }

            AttendanceError.DeadlinePassed -> {
                AttendanceFailure(GameDetailError.DEADLINE)
            }

            AttendanceError.Frozen -> {
                AttendanceFailure(GameDetailError.FROZEN)
            }

            AttendanceError.Conflict -> {
                AttendanceFailure(GameDetailError.CONFLICT)
            }

            is AttendanceError.Validation -> {
                AttendanceFailure(
                    GameDetailError.VALIDATION,
                    error.details.globalMessages.firstOrNull(String::isNotBlank),
                )
            }

            else -> {
                AttendanceFailure(GameDetailError.UNAVAILABLE)
            }
        }

    private fun GameDetailState.syncGame(detail: AttendanceDetail) =
        copy(
            game =
                game?.copy(
                    capacity = detail.capacity,
                    confirmedCount = detail.confirmedCount,
                    availableSpots = detail.availableSpots,
                    waitlistCount = detail.waitlistCount,
                ),
        )

    private fun updateInput(transform: (GameDetailState) -> GameDetailState) {
        update(transform)
        val current = state.value
        inputSnapshot = GameDetailInputSnapshot(current.overrideMemberId, current.overrideReason, current.capacityInput)
    }

    private data class AttendanceFailure(
        val error: GameDetailError,
        val message: String? = null,
    )
}

@Serializable
private data class GameDetailInputSnapshot(
    val memberId: String = "",
    val reason: String = "",
    val capacityInput: String? = null,
)
