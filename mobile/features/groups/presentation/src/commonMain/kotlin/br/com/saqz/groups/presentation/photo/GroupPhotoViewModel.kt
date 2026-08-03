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

internal class GroupPhotoViewModel(
    private val profileGateway: GroupProfileGateway,
    private val photoGateway: GroupPhotoGateway,
    private val selection: GroupPhotoSelectionPort,
    private val encoder: GroupPhotoEncoderPort,
    private val previews: GroupPhotoPreviewPort,
) : MviViewModel<GroupPhotoState, GroupPhotoIntent, Nothing>(GroupPhotoState()) {
    private var groupId: GroupId? = null
    private var groupVersion: GroupPhotoVersionToken? = null

    override fun onIntent(intent: GroupPhotoIntent) {
        when (intent) {
            is GroupPhotoIntent.BindGroup -> bind(intent.groupId)
            GroupPhotoIntent.ChooseCamera -> choose(selection::chooseCamera)
            GroupPhotoIntent.ChooseLibrary -> choose(selection::chooseLibrary)
            GroupPhotoIntent.Remove -> remove()
            GroupPhotoIntent.ClearError -> update { it.copy(error = null) }
        }
    }

    private fun bind(id: String?) {
        val nextId = id?.takeIf(String::isNotBlank)?.let(::GroupId)
        if (nextId == groupId && (nextId == null || state.value.isLoading)) return
        groupId = nextId
        groupVersion = null
        if (nextId == null) {
            update { it.copy(photoUrl = null, isLoading = false, error = null) }
        } else {
            load(nextId)
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
                            is GroupPhotoReadResult.Available -> update {
                                it.copy(
                                    photoUrl = photoUrl(id, photo.version),
                                    isLoading = false,
                                    error = null,
                                )
                            }
                            GroupPhotoReadResult.NotModified -> finish()
                        }
                        is SaqzResult.Failure -> if (result.error == br.com.saqz.groups.domain.photo.GroupPhotoError.NotFound) {
                            update { it.copy(photoUrl = null, isLoading = false, error = null) }
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
        if (groupId == null) {
            finish(GroupPhotoUiError.TargetUnavailable)
            return
        }
        update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = open()) {
                is GroupPhotoSelectionResult.Selected -> upload(result.value)
                GroupPhotoSelectionResult.Cancelled -> finish()
                GroupPhotoSelectionResult.CameraPermissionDenied -> finish(GroupPhotoUiError.CameraPermissionDenied)
                GroupPhotoSelectionResult.LibraryPermissionDenied -> finish(GroupPhotoUiError.LibraryPermissionDenied)
                GroupPhotoSelectionResult.Failed -> finish(GroupPhotoUiError.SelectionFailed)
            }
        }
    }

    private suspend fun upload(selected: GroupPhotoSelection) {
        val id = groupId ?: return finish(GroupPhotoUiError.TargetUnavailable)
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
                is GroupPhotoReadResult.Available -> update {
                    it.copy(
                        photoUrl = photoUrl(id, photo.version),
                        isLoading = false,
                        error = null,
                        changeVersion = it.changeVersion + 1,
                    )
                }
                GroupPhotoReadResult.NotModified -> finish(GroupPhotoUiError.UploadFailed)
            }
        }
    }

    private fun remove() {
        val id = groupId
        val version = groupVersion
        if (state.value.isLoading) return
        if (id == null || version == null) {
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
                            isLoading = false,
                            error = null,
                            changeVersion = it.changeVersion + 1,
                        )
                    }
                }
            }
        }
    }

    private fun finish(error: GroupPhotoUiError? = null) {
        update { it.copy(isLoading = false, error = error) }
    }

    private fun photoUrl(id: GroupId, version: GroupPhotoVersionToken): String =
        "/api/groups/${id.value}/photo?v=${version.value.removeSurrounding("\"")}"
}
