package br.com.saqz.groups.presentation.setup

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.group.CreateGroupProfileCommand
import br.com.saqz.groups.domain.group.GroupProfileError
import br.com.saqz.groups.domain.group.GroupProfileGateway
import br.com.saqz.groups.domain.group.GroupVersionToken
import br.com.saqz.groups.domain.group.UpdateGroupProfileCommand
import br.com.saqz.groups.model.GroupDraftKey
import br.com.saqz.groups.model.GroupDraftResource
import br.com.saqz.groups.domain.group.GroupLevel
import br.com.saqz.groups.domain.group.GroupModality
import br.com.saqz.groups.domain.group.GroupPlayStyle
import br.com.saqz.groups.model.GroupSetupDraft
import br.com.saqz.groups.domain.group.GroupSetupForm
import br.com.saqz.groups.domain.group.GroupTimeZone
import br.com.saqz.groups.port.GroupDraftReadResult
import br.com.saqz.groups.port.GroupDraftStorePort
import br.com.saqz.groups.port.GroupDraftWriteResult
import br.com.saqz.groups.port.GroupSystemTimeZonePort
import br.com.saqz.groups.port.GroupSystemTimeZoneResult
import kotlinx.coroutines.launch

class GroupSetupViewModel(
    input: GroupSetupInput,
    private val gateway: GroupProfileGateway,
    private val timeZones: GroupSystemTimeZonePort,
    private val drafts: GroupDraftStorePort,
    commandKeys: GroupCommandKeyFactory,
) : MviViewModel<GroupSetupState, GroupSetupIntent, GroupSetupEffect>(
    initialGroupSetupState(input, commandKeys),
) {
    private val existing = input.existing
    private val draftKey = GroupDraftKey(
        if (existing == null) GroupDraftResource.CREATE_GROUP else GroupDraftResource.UPDATE_GROUP,
        existing?.group?.id?.value,
    )

    init {
        restoreDraft()
        if (existing == null) detectTimeZone()
    }

    override fun onIntent(intent: GroupSetupIntent) {
        when (intent) {
            is GroupSetupIntent.UpdateName -> updateForm { copy(name = intent.value) }
            is GroupSetupIntent.UpdateModality -> updateForm {
                copy(
                    modality = intent.value,
                    playStyle = if (intent.value == GroupModality.COURT_VOLLEYBALL) playStyle else null,
                    customPlayStyle = if (intent.value == GroupModality.COURT_VOLLEYBALL) customPlayStyle else null,
                )
            }
            is GroupSetupIntent.UpdateComposition -> updateForm { copy(composition = intent.value) }
            is GroupSetupIntent.UpdateDescription -> updateForm { copy(description = intent.value) }
            is GroupSetupIntent.UpdateCity -> updateForm { copy(city = intent.value) }
            is GroupSetupIntent.UpdateLevel -> updateForm {
                copy(level = intent.value, customLevel = if (intent.value == GroupLevel.CUSTOM) customLevel else null)
            }
            is GroupSetupIntent.UpdateCustomLevel -> updateForm { copy(customLevel = intent.value) }
            is GroupSetupIntent.UpdatePlayStyle -> updateForm {
                copy(playStyle = intent.value, customPlayStyle = if (intent.value == GroupPlayStyle.CUSTOM) customPlayStyle else null)
            }
            is GroupSetupIntent.UpdateCustomPlayStyle -> updateForm { copy(customPlayStyle = intent.value) }
            is GroupSetupIntent.UpdateVenue -> updateForm { copy(defaultVenue = intent.value) }
            is GroupSetupIntent.UpdateSlots -> updateForm { copy(regularSlots = intent.value) }
            is GroupSetupIntent.UpdateDefaultCapacity -> updateForm { copy(defaultCapacity = intent.value) }
            is GroupSetupIntent.UpdateConfirmationLeadMinutes -> updateForm {
                copy(defaultConfirmationLeadMinutes = intent.value)
            }
            is GroupSetupIntent.UpdateDefaultGameFeeCents -> updateForm { copy(defaultGameFeeCents = intent.value) }
            is GroupSetupIntent.UpdateMonthlyFee -> updateForm {
                copy(monthlyFeeCents = intent.cents, monthlyDueDay = if (intent.cents == null) null else intent.dueDay)
            }
            is GroupSetupIntent.SelectFallbackTimeZone -> selectFallbackTimeZone(intent.identifier)
            is GroupSetupIntent.SetPhotoPending -> {
                val shouldOpen = !intent.value && state.value.photoPending
                val groupId = state.value.successGroupId
                update { it.copy(photoPending = intent.value) }
                if (shouldOpen && groupId != null) openConfirmedGroup(groupId)
            }
            GroupSetupIntent.Submit -> submit()
            GroupSetupIntent.ReloadConflict -> reloadConflict()
            GroupSetupIntent.RetryPhotoUpload -> retryPhoto()
            GroupSetupIntent.PhotoUploadFailed -> update { it.copy(photoRetryAvailable = true) }
            GroupSetupIntent.PhotoUploadSucceeded -> {
                val shouldOpen = state.value.photoPending
                val groupId = state.value.successGroupId
                update {
                    it.copy(photoPending = false, photoRetryAvailable = false)
                }
                if (shouldOpen && groupId != null) openConfirmedGroup(groupId)
            }
        }
    }

    private fun detectTimeZone() {
        timeZones.detect { result ->
            update { current ->
                when (result) {
                    is GroupSystemTimeZoneResult.Available -> current.copy(
                        timeZone = GroupTimeZone(result.value.id),
                        timezoneSelectionRequired = false,
                    )
                    GroupSystemTimeZoneResult.Unavailable -> current.copy(timezoneSelectionRequired = true)
                }
            }
        }
    }

    private fun selectFallbackTimeZone(identifier: String) {
        val parsed = identifier.toGroupTimeZone()
        if (parsed != null) {
            update {
                it.copy(timeZone = parsed, timezoneSelectionRequired = false, fieldErrors = it.fieldErrors - "timeZone")
            }
        } else {
            update {
                it.copy(fieldErrors = it.fieldErrors + ("timeZone" to listOf("must be a valid timezone")))
            }
        }
    }

    private fun restoreDraft() {
        drafts.read(draftKey) { result ->
            when (result) {
                is GroupDraftReadResult.Success -> result.draft?.let { draft ->
                    if (draft.schemaVersion == GroupSetupDraft.CURRENT_SCHEMA_VERSION &&
                        draft.resource == draftKey.resource && draft.groupId == draftKey.groupId
                    ) {
                        update {
                            it.copy(
                                form = draft.form.toDomainForm(),
                                commandKey = draft.commandKey,
                                groupVersion = draft.groupVersion,
                                etag = draft.etag,
                            )
                        }
                    }
                }
                is GroupDraftReadResult.Failure -> update { it.copy(error = GroupSetupError.DRAFT_UNAVAILABLE) }
            }
        }
    }

    private fun updateForm(transform: GroupSetupForm.() -> GroupSetupForm) {
        update {
            it.copy(
                form = it.form.transform(),
                fieldErrors = emptyMap(),
                validationMessages = emptyList(),
                error = null,
            )
        }
        persistDraft()
    }

    private fun persistDraft() {
        val current = state.value
        drafts.write(current.toDraft(draftKey)) { result ->
            if (result is GroupDraftWriteResult.Failure) {
                update { it.copy(error = GroupSetupError.DRAFT_UNAVAILABLE) }
            }
        }
    }

    private fun submit() {
        val current = state.value
        if (current.isLoading) return
        val errors = validateGroupSetup(current)
        if (errors.isNotEmpty()) {
            update { it.copy(validationAttempted = true, fieldErrors = errors) }
            return
        }
        persistDraft()
        update {
            it.copy(
                isLoading = true,
                validationAttempted = true,
                fieldErrors = emptyMap(),
                validationMessages = emptyList(),
                error = null,
            )
        }
        viewModelScope.launch {
            if (current.mode == GroupSetupMode.CREATE) create(current) else update(current)
        }
    }

    private suspend fun create(snapshot: GroupSetupState) {
        val command = CreateGroupProfileCommand(snapshot.commandKey, requireNotNull(snapshot.timeZone), snapshot.form)
        when (val result = gateway.createProfile(command)) {
            is SaqzResult.Success -> confirmedSuccess(result.value.id.value, result.value.version, null)
            is SaqzResult.Failure -> fail(result.error)
        }
    }

    private suspend fun update(snapshot: GroupSetupState) {
        val command = UpdateGroupProfileCommand(
            GroupId(requireNotNull(snapshot.groupId)),
            GroupVersionToken(requireNotNull(snapshot.etag)),
            snapshot.form,
        )
        when (val result = gateway.updateProfile(command)) {
            is SaqzResult.Success -> confirmedSuccess(
                result.value.group.id.value,
                result.value.group.version,
                result.value.versionToken.value,
            )
            is SaqzResult.Failure -> {
                if (result.error is GroupProfileError.Conflict) {
                    update { it.copy(isLoading = false, conflict = true) }
                } else fail(result.error)
            }
        }
    }

    private fun confirmedSuccess(groupId: String, version: Long, etag: String?) {
        val commandKey = state.value.commandKey
        val confirmedEtag = etag ?: "\"$version\""
        drafts.clear(draftKey, commandKey) { result ->
            update {
                it.copy(
                    isLoading = false,
                    conflict = false,
                    successGroupId = groupId,
                    groupId = groupId,
                    groupVersion = version,
                    etag = confirmedEtag,
                    error = if (result is GroupDraftWriteResult.Failure) GroupSetupError.DRAFT_UNAVAILABLE else null,
                )
            }
            if (state.value.photoPending) {
                emit(GroupSetupEffect.UploadPhoto(groupId, confirmedEtag))
            } else {
                openConfirmedGroup(groupId)
            }
        }
    }

    private fun openConfirmedGroup(groupId: String) {
        if (existing == null) emit(GroupSetupEffect.SelectGroup(groupId))
        emit(GroupSetupEffect.OpenGroup(groupId))
    }

    private fun reloadConflict() {
        val groupId = state.value.groupId ?: return
        if (state.value.isLoading) return
        update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = gateway.readProfile(GroupId(groupId))) {
                is SaqzResult.Success -> {
                    update {
                        it.copy(
                            form = result.value.group.toForm(),
                            groupVersion = result.value.group.version,
                            etag = result.value.versionToken.value,
                            isLoading = false,
                            conflict = false,
                            fieldErrors = emptyMap(),
                        )
                    }
                    persistDraft()
                }
                is SaqzResult.Failure -> fail(result.error)
            }
        }
    }

    private fun retryPhoto() {
        val groupId = state.value.successGroupId ?: return
        val groupEtag = state.value.etag ?: return
        update { it.copy(photoRetryAvailable = false) }
        emit(GroupSetupEffect.UploadPhoto(groupId, groupEtag))
    }

    private fun fail(error: GroupProfileError) {
        when (error) {
            is GroupProfileError.Validation -> update {
                it.copy(
                    isLoading = false,
                    fieldErrors = error.details.fieldMessages,
                    validationMessages = error.details.globalMessages
                        .map(GroupSetupValidationMessage::Safe)
                        .ifEmpty { listOf(GroupSetupValidationMessage.Generic) },
                    error = null,
                )
            }
            is GroupProfileError.Conflict -> update {
                it.copy(isLoading = false, conflict = true)
            }
            is GroupProfileError.DataFailure -> update {
                it.copy(
                    isLoading = false,
                    fieldErrors = emptyMap(),
                    error = when (error.error) {
                        DataError.Forbidden -> GroupSetupError.FORBIDDEN
                        DataError.NotFound -> GroupSetupError.NOT_FOUND
                        else -> GroupSetupError.UNAVAILABLE
                    },
                )
            }
        }
    }

}

private fun initialGroupSetupState(
    input: GroupSetupInput,
    commandKeys: GroupCommandKeyFactory,
): GroupSetupState {
    val existing = input.existing
    return GroupSetupState(
        mode = if (existing == null) GroupSetupMode.CREATE else GroupSetupMode.EDIT,
        form = existing?.group?.toForm() ?: newGroupDefaults(),
        commandKey = commandKeys.create(),
        groupId = existing?.group?.id?.value,
        groupVersion = existing?.group?.version,
        etag = existing?.versionToken?.value,
        timeZone = existing?.group?.timeZone,
    )
}
