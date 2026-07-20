package br.com.saqz.groups.presentation.photo

import br.com.saqz.groups.data.GroupPhotoGateway
import br.com.saqz.groups.data.GroupPhotoReceipt
import br.com.saqz.groups.data.GroupPhotoUploadCommand
import br.com.saqz.groups.port.GroupPhotoCachePort
import br.com.saqz.groups.port.GroupPhotoCrop
import br.com.saqz.groups.port.GroupPhotoEncoderPort
import br.com.saqz.groups.port.GroupPhotoEncodingResult
import br.com.saqz.groups.port.GroupPhotoPreviewHandle
import br.com.saqz.groups.port.GroupPhotoSelection
import br.com.saqz.groups.port.GroupPhotoSelectionPort
import br.com.saqz.groups.port.GroupPhotoSelectionResult
import br.com.saqz.network.NetworkResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class GroupPhotoStage { IDLE, SELECTING, CROPPING, ENCODING, UPLOADING, REMOVING }
enum class GroupPhotoError { SELECTION_FAILED, ENCODING_FAILED, UPLOAD_FAILED, REMOVE_FAILED, STALE_VERSION, TARGET_UNAVAILABLE }

data class ExistingGroupPhoto(val preview: GroupPhotoPreviewHandle, val etag: String)

data class GroupPhotoState(
    val groupId: String? = null,
    val groupEtag: String? = null,
    val existing: ExistingGroupPhoto? = null,
    val selection: GroupPhotoSelection? = null,
    val crop: GroupPhotoCrop = GroupPhotoCrop(),
    val stage: GroupPhotoStage = GroupPhotoStage.IDLE,
    val retryUpload: Boolean = false,
    val error: GroupPhotoError? = null,
)

sealed interface GroupPhotoIntent {
    data class BindTarget(val groupId: String, val groupEtag: String) : GroupPhotoIntent
    data class SetExisting(val photo: ExistingGroupPhoto?) : GroupPhotoIntent
    data object ChooseCamera : GroupPhotoIntent
    data object ChooseLibrary : GroupPhotoIntent
    data class ChangeCrop(val crop: GroupPhotoCrop) : GroupPhotoIntent
    data object Upload : GroupPhotoIntent
    data object RetryUpload : GroupPhotoIntent
    data object Remove : GroupPhotoIntent
    data object Cancel : GroupPhotoIntent
    data object MembershipLost : GroupPhotoIntent
    data object Logout : GroupPhotoIntent
}

class GroupPhotoCoordinator(
    private val gateway: GroupPhotoGateway,
    private val selections: GroupPhotoSelectionPort,
    private val encoder: GroupPhotoEncoderPort,
    private val cache: GroupPhotoCachePort,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(GroupPhotoState())
    val state: StateFlow<GroupPhotoState> = mutableState.asStateFlow()
    private var operation: Job? = null

    fun onIntent(intent: GroupPhotoIntent) {
        when (intent) {
            is GroupPhotoIntent.BindTarget -> mutableState.update {
                it.copy(groupId = intent.groupId, groupEtag = intent.groupEtag, error = null)
            }
            is GroupPhotoIntent.SetExisting -> mutableState.update { it.copy(existing = intent.photo, error = null) }
            GroupPhotoIntent.ChooseCamera -> select(selections::chooseCamera)
            GroupPhotoIntent.ChooseLibrary -> select(selections::chooseLibrary)
            is GroupPhotoIntent.ChangeCrop -> mutableState.update { it.copy(crop = intent.crop, error = null) }
            GroupPhotoIntent.Upload, GroupPhotoIntent.RetryUpload -> upload()
            GroupPhotoIntent.Remove -> remove()
            GroupPhotoIntent.Cancel -> cleanupSelection()
            GroupPhotoIntent.MembershipLost -> purge(clearAll = false)
            GroupPhotoIntent.Logout -> purge(clearAll = true)
        }
    }

    private fun select(block: suspend () -> GroupPhotoSelectionResult) {
        if (mutableState.value.stage !in setOf(GroupPhotoStage.IDLE, GroupPhotoStage.CROPPING)) return
        mutableState.update { it.copy(stage = GroupPhotoStage.SELECTING, error = null) }
        operation = scope.launch {
            when (val result = block()) {
                is GroupPhotoSelectionResult.Selected -> {
                    mutableState.value.selection?.source?.let(selections::cleanup)
                    mutableState.update {
                        it.copy(selection = result.value, crop = GroupPhotoCrop(), stage = GroupPhotoStage.CROPPING)
                    }
                }
                GroupPhotoSelectionResult.Cancelled -> mutableState.update { it.copy(stage = GroupPhotoStage.IDLE) }
                GroupPhotoSelectionResult.Failed -> mutableState.update {
                    it.copy(stage = GroupPhotoStage.IDLE, error = GroupPhotoError.SELECTION_FAILED)
                }
            }
        }
    }

    private fun upload() {
        val snapshot = mutableState.value
        val selection = snapshot.selection ?: return targetUnavailable()
        val groupId = snapshot.groupId ?: return targetUnavailable()
        val groupEtag = snapshot.groupEtag ?: return targetUnavailable()
        if (snapshot.stage !in setOf(GroupPhotoStage.IDLE, GroupPhotoStage.CROPPING)) return
        mutableState.update { it.copy(stage = GroupPhotoStage.ENCODING, retryUpload = false, error = null) }
        operation = scope.launch {
            when (val encoded = encoder.encode(selection.source, snapshot.crop)) {
                GroupPhotoEncodingResult.Failed -> mutableState.update {
                    it.copy(stage = GroupPhotoStage.CROPPING, retryUpload = true, error = GroupPhotoError.ENCODING_FAILED)
                }
                is GroupPhotoEncodingResult.Encoded -> {
                    mutableState.update { it.copy(stage = GroupPhotoStage.UPLOADING) }
                    when (val result = gateway.upload(GroupPhotoUploadCommand(groupId, groupEtag, encoded.value))) {
                        is NetworkResult.Failure -> mutableState.update {
                            val error = result.error as? br.com.saqz.network.NetworkError.ApiProblemError
                            val stale = error?.problem?.status == 409 && error.problem.code == "VERSION_CONFLICT"
                            it.copy(
                                stage = GroupPhotoStage.CROPPING,
                                retryUpload = !stale,
                                error = if (stale) GroupPhotoError.STALE_VERSION else GroupPhotoError.UPLOAD_FAILED,
                            )
                        }
                        is NetworkResult.Success -> uploadSucceeded(groupId, selection, result.value)
                    }
                }
            }
        }
    }

    private fun uploadSucceeded(groupId: String, selection: GroupPhotoSelection, receipt: GroupPhotoReceipt) {
        selections.cleanup(selection.source)
        cache.evict(groupId)
        mutableState.update {
            it.copy(
                existing = ExistingGroupPhoto(selection.preview, receipt.etag),
                selection = null,
                groupEtag = receipt.etag,
                stage = GroupPhotoStage.IDLE,
                retryUpload = false,
                error = null,
            )
        }
    }

    private fun remove() {
        val snapshot = mutableState.value
        val groupId = snapshot.groupId ?: return targetUnavailable()
        val groupEtag = snapshot.groupEtag ?: return targetUnavailable()
        if (snapshot.stage != GroupPhotoStage.IDLE || snapshot.existing == null) return
        mutableState.update { it.copy(stage = GroupPhotoStage.REMOVING, error = null) }
        operation = scope.launch {
            when (val result = gateway.remove(groupId, groupEtag)) {
                is NetworkResult.Failure -> mutableState.update {
                    it.copy(stage = GroupPhotoStage.IDLE, error = GroupPhotoError.REMOVE_FAILED)
                }
                is NetworkResult.Success -> {
                    cache.evict(groupId)
                    mutableState.update {
                        it.copy(
                            existing = null,
                            groupEtag = result.value.etag,
                            stage = GroupPhotoStage.IDLE,
                            error = null,
                        )
                    }
                }
            }
        }
    }

    private fun cleanupSelection() {
        operation?.cancel()
        operation = null
        val source = mutableState.value.selection?.source
        if (source != null) {
            encoder.cancel(source)
            selections.cleanup(source)
        }
        mutableState.update {
            it.copy(selection = null, crop = GroupPhotoCrop(), stage = GroupPhotoStage.IDLE, retryUpload = false, error = null)
        }
    }

    private fun purge(clearAll: Boolean) {
        val groupId = mutableState.value.groupId
        cleanupSelection()
        if (clearAll) cache.clearAll() else groupId?.let(cache::evict)
        mutableState.value = GroupPhotoState()
    }

    private fun targetUnavailable() {
        mutableState.update { it.copy(error = GroupPhotoError.TARGET_UNAVAILABLE) }
    }

}
