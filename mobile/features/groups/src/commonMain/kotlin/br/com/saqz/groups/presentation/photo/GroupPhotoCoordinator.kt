package br.com.saqz.groups.presentation.photo

import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.photo.CachedGroupPhoto
import br.com.saqz.groups.domain.photo.GroupPhotoCachePort
import br.com.saqz.groups.domain.photo.GroupPhotoCrop
import br.com.saqz.groups.domain.photo.GroupPhotoEncoderPort
import br.com.saqz.groups.domain.photo.GroupPhotoEncodingResult
import br.com.saqz.groups.domain.photo.GroupPhotoError as DomainPhotoError
import br.com.saqz.groups.domain.photo.GroupPhotoGateway
import br.com.saqz.groups.domain.photo.GroupPhotoPreviewHandle
import br.com.saqz.groups.domain.photo.GroupPhotoReadResult
import br.com.saqz.groups.domain.photo.GroupPhotoReceipt
import br.com.saqz.groups.domain.photo.GroupPhotoSelection
import br.com.saqz.groups.domain.photo.GroupPhotoSelectionPort
import br.com.saqz.groups.domain.photo.GroupPhotoSelectionResult
import br.com.saqz.groups.domain.photo.GroupPhotoUploadCommand
import br.com.saqz.groups.domain.photo.GroupPhotoVersionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class GroupPhotoStage { IDLE, LOADING, SELECTING, CROPPING, ENCODING, UPLOADING, REMOVING }
enum class GroupPhotoError {
    READ_FAILED,
    SELECTION_FAILED,
    ENCODING_FAILED,
    UPLOAD_FAILED,
    REMOVE_FAILED,
    STALE_VERSION,
    TARGET_UNAVAILABLE,
}

data class ExistingGroupPhoto(
    val preview: GroupPhotoPreviewHandle,
    val photoEtag: String? = null,
)

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
    data class Load(val groupId: String, val groupEtag: String) : GroupPhotoIntent
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
    private var loadGeneration = 0L

    fun onIntent(intent: GroupPhotoIntent) {
        when (intent) {
            is GroupPhotoIntent.BindTarget -> mutableState.update {
                it.copy(groupId = intent.groupId, groupEtag = intent.groupEtag, error = null)
            }
            is GroupPhotoIntent.Load -> load(intent.groupId, intent.groupEtag)
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

    private fun load(groupId: String, groupEtag: String) {
        operation?.cancel()
        operation = null
        loadGeneration += 1
        val generation = loadGeneration
        val domainGroupId = GroupId(groupId)
        val cached = runCatching { cache.read(domainGroupId) }.getOrNull()
        mutableState.value = GroupPhotoState(
            groupId = groupId,
            groupEtag = groupEtag,
            stage = GroupPhotoStage.LOADING,
        )
        operation = scope.launch {
            val result = gateway.read(domainGroupId, cached?.version)
            if (!isCurrentLoad(groupId, generation)) return@launch
            when (result) {
                is SaqzResult.Failure -> finishReadFailure(domainGroupId, result.error)
                is SaqzResult.Success -> finishReadSuccess(domainGroupId, cached, result.value)
            }
        }
    }

    private fun finishReadSuccess(
        groupId: GroupId,
        cached: CachedGroupPhoto?,
        result: GroupPhotoReadResult,
    ) {
        val photo = when (result) {
            GroupPhotoReadResult.NotModified -> cached
            is GroupPhotoReadResult.Available -> runCatching {
                cache.write(groupId, result.bytes, result.version)
            }.getOrNull()
        }
        mutableState.update {
            it.copy(
                existing = photo?.let { value -> ExistingGroupPhoto(value.preview, value.version.value) },
                stage = GroupPhotoStage.IDLE,
                error = if (photo == null) GroupPhotoError.READ_FAILED else null,
            )
        }
    }

    private fun finishReadFailure(groupId: GroupId, error: DomainPhotoError) {
        if (error == DomainPhotoError.NotFound) cache.evict(groupId)
        mutableState.update {
            it.copy(
                existing = null,
                stage = GroupPhotoStage.IDLE,
                error = if (error == DomainPhotoError.NotFound) null else GroupPhotoError.READ_FAILED,
            )
        }
    }

    private fun isCurrentLoad(groupId: String, generation: Long): Boolean =
        generation == loadGeneration && mutableState.value.groupId == groupId

    private fun select(block: suspend () -> GroupPhotoSelectionResult) {
        if (mutableState.value.stage !in setOf(GroupPhotoStage.IDLE, GroupPhotoStage.CROPPING)) return
        mutableState.update { it.copy(stage = GroupPhotoStage.SELECTING, error = null) }
        operation = scope.launch {
            when (val result = block()) {
                is GroupPhotoSelectionResult.Selected -> {
                    mutableState.value.selection?.source?.value?.let(selections::cleanup)
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
            when (val encoded = encoder.encode(selection.source.value, snapshot.crop)) {
                GroupPhotoEncodingResult.Failed -> mutableState.update {
                    it.copy(stage = GroupPhotoStage.CROPPING, retryUpload = true, error = GroupPhotoError.ENCODING_FAILED)
                }
                is GroupPhotoEncodingResult.Encoded -> {
                    mutableState.update { it.copy(stage = GroupPhotoStage.UPLOADING) }
                    val command = GroupPhotoUploadCommand(
                        GroupId(groupId),
                        GroupPhotoVersionToken(groupEtag),
                        encoded.value,
                    )
                    when (val result = gateway.upload(command)) {
                        is SaqzResult.Failure -> mutableState.update {
                            val stale = result.error == DomainPhotoError.StaleVersion
                            it.copy(
                                stage = GroupPhotoStage.CROPPING,
                                retryUpload = !stale,
                                error = if (stale) GroupPhotoError.STALE_VERSION else GroupPhotoError.UPLOAD_FAILED,
                            )
                        }
                        is SaqzResult.Success -> uploadSucceeded(groupId, selection, result.value)
                    }
                }
            }
        }
    }

    private fun uploadSucceeded(groupId: String, selection: GroupPhotoSelection, receipt: GroupPhotoReceipt) {
        selections.cleanup(selection.source.value)
        cache.evict(GroupId(groupId))
        mutableState.update {
            it.copy(
                existing = ExistingGroupPhoto(selection.preview),
                selection = null,
                groupEtag = receipt.version.value,
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
            when (val result = gateway.remove(GroupId(groupId), GroupPhotoVersionToken(groupEtag))) {
                is SaqzResult.Failure -> mutableState.update {
                    it.copy(stage = GroupPhotoStage.IDLE, error = GroupPhotoError.REMOVE_FAILED)
                }
                is SaqzResult.Success -> {
                    cache.evict(GroupId(groupId))
                    mutableState.update {
                        it.copy(
                            existing = null,
                            groupEtag = result.value.version.value,
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
            encoder.cancel(source.value)
            selections.cleanup(source.value)
        }
        mutableState.update {
            it.copy(selection = null, crop = GroupPhotoCrop(), stage = GroupPhotoStage.IDLE, retryUpload = false, error = null)
        }
    }

    private fun purge(clearAll: Boolean) {
        loadGeneration += 1
        val groupId = mutableState.value.groupId
        cleanupSelection()
        if (clearAll) cache.clearAll() else groupId?.let { cache.evict(GroupId(it)) }
        mutableState.value = GroupPhotoState()
    }

    private fun targetUnavailable() {
        mutableState.update { it.copy(error = GroupPhotoError.TARGET_UNAVAILABLE) }
    }

}
