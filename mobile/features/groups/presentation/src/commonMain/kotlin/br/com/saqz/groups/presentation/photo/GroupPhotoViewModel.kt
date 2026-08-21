package br.com.saqz.groups.presentation.photo

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.group.GroupProfileGateway
import br.com.saqz.groups.domain.photo.GroupPhotoCrop
import br.com.saqz.groups.domain.photo.EncodedGroupPhoto
import br.com.saqz.groups.domain.photo.GroupPhotoEncodingResult
import br.com.saqz.groups.domain.photo.GroupPhotoGateway
import br.com.saqz.groups.domain.photo.GroupPhotoEncoderPort
import br.com.saqz.groups.domain.photo.GroupPhotoReadResult
import br.com.saqz.groups.domain.photo.GroupPhotoSelection
import br.com.saqz.groups.domain.photo.GroupPhotoSelectionPort
import br.com.saqz.groups.domain.photo.GroupPhotoSelectionResult
import br.com.saqz.groups.domain.photo.GroupPhotoUploadCommand
import br.com.saqz.groups.domain.photo.GroupPhotoVersionToken
import br.com.saqz.groups.domain.photo.GroupPhotoPreviewPort
import kotlinx.coroutines.launch

class GroupPhotoViewModel(
    private val profileGateway: GroupProfileGateway,
    private val photoGateway: GroupPhotoGateway,
    private val selection: GroupPhotoSelectionPort,
    private val encoder: GroupPhotoEncoderPort,
    private val previews: GroupPhotoPreviewPort,
) : MviViewModel<GroupPhotoState, GroupPhotoIntent, Nothing>(GroupPhotoState()) {
    private var groupId: GroupId? = null
    private var groupVersion: GroupPhotoVersionToken? = null
    private var pending: GroupPhotoSelection? = null

    override fun onIntent(intent: GroupPhotoIntent) {
        when (intent) {
            is GroupPhotoIntent.BindGroup -> bind(intent.groupId)
            GroupPhotoIntent.ChooseCamera -> choose(selection::chooseCamera)
            GroupPhotoIntent.ChooseLibrary -> choose(selection::chooseLibrary)
            GroupPhotoIntent.Remove -> remove()
            GroupPhotoIntent.ClearError -> update { it.copy(error = null) }
            GroupPhotoIntent.Commit -> commit()
        }
    }

    override fun onCleared() {
        discardPending()
        super.onCleared()
    }

    private fun bind(id: String?) {
        val nextId = id?.takeIf(String::isNotBlank)?.let(::GroupId)
        // BindGroup no mesmo id não envia a foto retida: a câmera recria a
        // composition e o save do perfil receberia 409.
        if (nextId == groupId) return
        val held = pending
        groupId = nextId
        groupVersion = null
        when {
            nextId == null -> if (held == null) {
                update { it.copy(photoUrl = null, preview = null, isLoading = false, error = null, hasPending = false) }
            }
            held != null -> commitHeld(nextId, held)
            else -> load(nextId)
        }
    }

    private fun load(id: GroupId) {
        update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val profile = profileGateway.readProfile(id)) {
                is SaqzResult.Failure -> finish(GroupPhotoUiError.LoadFailed)
                is SaqzResult.Success -> {
                    groupVersion = GroupPhotoVersionToken(profile.value.versionToken.value)
                    when (val result = photoGateway.read(id)) {
                        is SaqzResult.Success -> when (val photo = result.value) {
                            is GroupPhotoReadResult.Available -> show(id, photo)
                            GroupPhotoReadResult.NotModified -> finish()
                        }
                        is SaqzResult.Failure -> if (result.error == br.com.saqz.groups.domain.photo.GroupPhotoError.NotFound) {
                            update { it.copy(photoUrl = null, preview = null, isLoading = false, error = null, hasPending = false) }
                        } else {
                            finish(GroupPhotoUiError.LoadFailed)
                        }
                    }
                }
            }
        }
    }

    private fun choose(
        open: suspend () -> GroupPhotoSelectionResult,
    ) {
        if (state.value.isLoading) return
        update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = open()) {
                is GroupPhotoSelectionResult.Selected -> hold(result.value)
                GroupPhotoSelectionResult.Cancelled -> finish()
                GroupPhotoSelectionResult.CameraPermissionDenied -> finish(GroupPhotoUiError.CameraPermissionDenied)
                GroupPhotoSelectionResult.LibraryPermissionDenied -> finish(GroupPhotoUiError.LibraryPermissionDenied)
                GroupPhotoSelectionResult.Failed -> finish(GroupPhotoUiError.SelectionFailed)
            }
        }
    }

    private suspend fun hold(selected: GroupPhotoSelection) {
        val bytes = previews.read(selected.preview.value)
        if (bytes == null) {
            selection.cleanup(selected.source.value)
            finish(GroupPhotoUiError.SelectionFailed)
            return
        }
        discardPending()
        pending = selected
        val preview = decodeGroupPhoto(bytes, GROUP_PHOTO_THUMB_PX)
        update {
            it.copy(
                photoUrl = pendingPhotoUrl(selected.preview.value),
                preview = preview,
                isLoading = false,
                error = null,
                changeVersion = it.changeVersion + 1,
                hasPending = true,
            )
        }
    }

    private fun commit() {
        val id = groupId
        val held = pending
        if (id == null || held == null || state.value.isLoading) return
        commitHeld(id, held)
    }

    private fun commitHeld(id: GroupId, selected: GroupPhotoSelection) {
        update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val profile = profileGateway.readProfile(id)) {
                is SaqzResult.Failure -> finish(GroupPhotoUiError.UploadFailed)
                is SaqzResult.Success -> {
                    groupVersion = GroupPhotoVersionToken(profile.value.versionToken.value)
                    upload(selected)
                }
            }
        }
    }

    private suspend fun upload(selected: GroupPhotoSelection) {
        val id = groupId ?: return finish(GroupPhotoUiError.TargetUnavailable)
        pending = null
        try {
            if (previews.read(selected.preview.value) == null) {
                finish(GroupPhotoUiError.SelectionFailed)
                return
            }
            val version = groupVersion ?: run {
                finish(GroupPhotoUiError.UploadFailed)
                return
            }
            val encoded = encode(selected.source.value) ?: return
            when (val uploaded = photoGateway.upload(GroupPhotoUploadCommand(id, version, encoded))) {
                is SaqzResult.Failure -> finish(GroupPhotoUiError.UploadFailed)
                is SaqzResult.Success -> refresh(id, uploaded.value.version)
            }
        } finally {
            selection.cleanup(selected.source.value)
        }
    }

    private suspend fun encode(source: String): EncodedGroupPhoto? = when (val result = encoder.encode(source, GroupPhotoCrop())) {
        is GroupPhotoEncodingResult.Encoded -> result.value
        GroupPhotoEncodingResult.Failed -> {
            finish(GroupPhotoUiError.EncodingFailed)
            null
        }
    }

    private suspend fun refresh(id: GroupId, version: GroupPhotoVersionToken) {
        groupVersion = version
        when (val refreshed = photoGateway.read(id)) {
            is SaqzResult.Failure -> finish(GroupPhotoUiError.UploadFailed)
            is SaqzResult.Success -> when (val photo = refreshed.value) {
                is GroupPhotoReadResult.Available -> show(id, photo, bumpChange = true)
                GroupPhotoReadResult.NotModified -> finish(GroupPhotoUiError.UploadFailed)
            }
        }
    }

    private fun remove() {
        if (state.value.isLoading) return
        val id = groupId
        val version = groupVersion
        if (pending != null || id == null) {
            discardPending()
            if (id == null) {
                update {
                    it.copy(
                        photoUrl = null,
                        preview = null,
                        isLoading = false,
                        error = null,
                        changeVersion = it.changeVersion + 1,
                        hasPending = false,
                    )
                }
            } else {
                load(id)
            }
            return
        }
        if (version == null) {
            finish(GroupPhotoUiError.TargetUnavailable)
            return
        }
        update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val removed = photoGateway.remove(id, version)) {
                is SaqzResult.Failure -> finish(GroupPhotoUiError.RemovalFailed)
                is SaqzResult.Success -> {
                    groupVersion = removed.value.version
                    update {
                        it.copy(
                            photoUrl = null,
                            preview = null,
                            isLoading = false,
                            error = null,
                            changeVersion = it.changeVersion + 1,
                            hasPending = false,
                        )
                    }
                }
            }
        }
    }

    private suspend fun show(
        id: GroupId,
        photo: GroupPhotoReadResult.Available,
        bumpChange: Boolean = false,
    ) {
        val preview = decodeGroupPhoto(photo.bytes, GROUP_PHOTO_THUMB_PX)
        update {
            it.copy(
                photoUrl = photoUrl(id, photo.version),
                preview = preview,
                isLoading = false,
                error = null,
                changeVersion = if (bumpChange) it.changeVersion + 1 else it.changeVersion,
                hasPending = false,
            )
        }
    }

    private fun discardPending() {
        pending?.let { selection.cleanup(it.source.value) }
        pending = null
    }

    private fun finish(error: GroupPhotoUiError? = null) {
        update { it.copy(isLoading = false, error = error, hasPending = pending != null) }
    }

    private fun photoUrl(id: GroupId, version: GroupPhotoVersionToken): String =
        "/api/groups/${id.value}/photo?v=${version.value.removeSurrounding("\"")}"

    private fun pendingPhotoUrl(preview: String) = "pending:$preview"
}
