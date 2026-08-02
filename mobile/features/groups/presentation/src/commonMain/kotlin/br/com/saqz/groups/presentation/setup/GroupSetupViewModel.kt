package br.com.saqz.groups.presentation.setup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.group.CreateGroupProfileCommand
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.group.GroupProfileGateway
import br.com.saqz.groups.domain.group.GroupTimeZone as DomainGroupTimeZone
import br.com.saqz.groups.domain.group.GroupVersionToken
import br.com.saqz.groups.domain.group.UpdateGroupProfileCommand
import br.com.saqz.groups.model.GroupComposition
import br.com.saqz.groups.model.GroupLevel
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.model.GroupPlayStyle
import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.model.GroupSetupForm
import br.com.saqz.groups.model.GroupTimeZone
import br.com.saqz.groups.model.GroupVenueForm
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.port.GroupSystemTimeZonePort
import br.com.saqz.groups.port.GroupSystemTimeZoneResult
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.toUiError
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class GroupSetupViewModel(
    initialState: GroupSetupState,
    private val savedState: SavedStateHandle,
    private val groupGateway: GroupGateway,
    private val profileGateway: GroupProfileGateway,
    private val timeZonePort: GroupSystemTimeZonePort,
) : MviViewModel<GroupSetupState, GroupSetupIntent, GroupSetupEffect>(
    initialState.withSavedText(savedState),
) {

    private var loadGeneration = 0
    private var versionToken: GroupVersionToken? = null
    private var timeZone: DomainGroupTimeZone? = null
    private var retryDelete = false

    init {
        timeZonePort.detect { result ->
            timeZone = (result as? GroupSystemTimeZoneResult.Available)?.value?.toDomain()
        }
        if (state.value.mode is GroupSetupMode.Edit) {
            load()
        } else {
            update { it.copy(isLoading = false) }
        }
    }

    @Suppress("CyclomaticComplexMethod")
    override fun onIntent(intent: GroupSetupIntent) {
        when (intent) {
            is GroupSetupIntent.UpdateName ->
                onTextChange(KeyName, intent.value, GroupTextLimits.NameMax) { copy(name = it) }
            is GroupSetupIntent.UpdateDescription ->
                onTextChange(KeyDescription, intent.value, GroupTextLimits.DescriptionMax) {
                    copy(description = it)
                }
            is GroupSetupIntent.UpdateCustomLevel ->
                onTextChange(KeyCustomLevel, intent.value, GroupTextLimits.CustomLevelMax) {
                    copy(customLevel = it)
                }
            is GroupSetupIntent.UpdateVenueName ->
                onTextChange(KeyVenueName, intent.value, GroupTextLimits.VenueNameMax) { value ->
                    withVenue { copy(name = value) }
                }
            is GroupSetupIntent.UpdateVenueAddress ->
                onTextChange(KeyVenueAddress, intent.value, GroupTextLimits.VenueAddressMax) { value ->
                    withVenue { copy(address = value) }
                }
            is GroupSetupIntent.SelectModality -> onFormChange { copy(modality = intent.value) }
            is GroupSetupIntent.SelectComposition -> onFormChange { copy(composition = intent.value) }
            is GroupSetupIntent.SelectLevel -> onFormChange { copy(level = intent.value) }
            is GroupSetupIntent.SelectPlayStyle -> onFormChange { copy(playStyle = intent.value) }
            is GroupSetupIntent.UpdateCapacity -> onFormChange { copy(defaultCapacity = intent.value) }
            is GroupSetupIntent.SelectConfirmationLead ->
                onFormChange { copy(defaultConfirmationLeadMinutes = intent.minutes) }
            is GroupSetupIntent.SelectDuration -> onDurationChange(intent.minutes)
            is GroupSetupIntent.ToggleRecurring -> onRecurringChange(intent.value)
            is GroupSetupIntent.OpenSheet -> onOpenSheet(intent.sheet)
            GroupSetupIntent.CloseSheet -> update { it.copy(sheet = null) }
            is GroupSetupIntent.PickSlotWeekday ->
                update { it.copy(slotDraft = it.slotDraft.copy(weekday = intent.weekday)) }
            is GroupSetupIntent.PickSlotTime ->
                update { it.copy(slotDraft = it.slotDraft.copy(hour = intent.hour, minute = intent.minute)) }
            GroupSetupIntent.ConfirmSlot -> onConfirmSlot()
            is GroupSetupIntent.RemoveSlot -> onFormChange { copy(regularSlots = regularSlots - intent.slot) }
            GroupSetupIntent.PickPhoto -> emit(GroupSetupEffect.PickPhoto)
            GroupSetupIntent.Submit -> onSubmit()
            GroupSetupIntent.Retry -> onRetry()
            GroupSetupIntent.ConfirmCreate -> onConfirmCreate()
            GroupSetupIntent.BackToForm -> update { it.copy(step = GroupSetupStep.Form) }
            GroupSetupIntent.ConfirmDelete -> onConfirmDelete()
            GroupSetupIntent.SaveDraft -> onSaveDraft()
        }
    }

    private fun onRetry() {
        when {
            retryDelete -> onConfirmDelete()
            versionToken == null && state.value.isEditing -> load()
            else -> onSubmit()
        }
    }

    private fun load() {
        val mode = state.value.mode as? GroupSetupMode.Edit ?: return
        val generation = ++loadGeneration
        update { it.copy(isLoading = true, gatewayError = null, saveFailed = false) }
        viewModelScope.launch {
            when (val result = profileGateway.readProfile(GroupId(mode.groupId))) {
                is SaqzResult.Failure -> showFailure(generation, result.error.toUiError())
                is SaqzResult.Success -> {
                    if (generation != loadGeneration) return@launch
                    val group = result.value.group
                    versionToken = result.value.versionToken
                    update {
                        it.copy(
                            isLoading = false,
                            gatewayError = null,
                            saveFailed = false,
                            form = group.toForm(),
                        )
                    }
                }
            }
        }
    }

    private fun onSubmit() {
        val current = state.value
        if (current.isLoading || current.isSaving || current.isDeleting) return
        val errors = validate(current)
        if (errors.isNotEmpty()) {
            update { it.copy(errors = errors, saveFailed = false, gatewayError = null) }
            return
        }
        if (current.isEditing) {
            update { it.copy(isSaving = true, saveFailed = false, gatewayError = null, errors = emptySet()) }
            retryDelete = false
            saveEdit()
        } else {
            update { it.copy(step = GroupSetupStep.Review, errors = emptySet(), saveFailed = false, gatewayError = null) }
        }
    }

    private fun saveEdit() {
        val mode = state.value.mode as GroupSetupMode.Edit
        val token = versionToken
        if (token == null) {
            showOperationFailure(GroupUiError.Unknown)
            return
        }
        viewModelScope.launch {
            // ponytail: seam de update; o formulário completo usa o endpoint de perfil e
            // converte seus erros tipados em estado visível antes de emitir Saved.
            when (val result = profileGateway.updateProfile(
                UpdateGroupProfileCommand(GroupId(mode.groupId), token, state.value.form.toDomain()),
            )) {
                is SaqzResult.Success -> {
                    versionToken = result.value.versionToken
                    update { it.copy(isSaving = false, saveFailed = false, gatewayError = null) }
                    emit(GroupSetupEffect.Saved)
                }
                is SaqzResult.Failure -> showOperationFailure(result.error.toUiError())
            }
        }
    }

    private fun onConfirmCreate() {
        val form = state.value.form
        val zone = timeZone
        if (state.value.isSaving || zone == null) {
            if (zone == null) showOperationFailure(GroupUiError.Unknown)
            return
        }
        update { it.copy(isSaving = true, saveFailed = false, gatewayError = null) }
        viewModelScope.launch {
            // ponytail: seam de create; createProfile persiste a mesma carga completa que a
            // revisão apresenta e devolve o id autoritativo para o efeito de saída.
            when (val result = profileGateway.createProfile(
                CreateGroupProfileCommand(newCommandKey(), zone, form.toDomain()),
            )) {
                is SaqzResult.Success -> {
                    update { it.copy(isSaving = false, saveFailed = false, gatewayError = null) }
                    emit(GroupSetupEffect.Created(result.value.id.value))
                }
                is SaqzResult.Failure -> showOperationFailure(result.error.toUiError())
            }
        }
    }

    private fun onConfirmDelete() {
        val mode = state.value.mode as? GroupSetupMode.Edit ?: return
        if (state.value.isDeleting) return
        retryDelete = true
        update { it.copy(isDeleting = true, isSaving = false, sheet = null, gatewayError = null, saveFailed = false) }
        viewModelScope.launch {
            // ponytail: seam de delete; 403/404 continuam GroupProfileError até a borda da UI.
            when (val result = groupGateway.delete(GroupId(mode.groupId))) {
                is SaqzResult.Success -> {
                    update { it.copy(isDeleting = false) }
                    emit(GroupSetupEffect.Deleted)
                }
                is SaqzResult.Failure -> showOperationFailure(result.error.toUiError())
            }
        }
    }

    private fun showOperationFailure(error: GroupUiError) {
        update { it.copy(isLoading = false, isSaving = false, isDeleting = false, saveFailed = true, gatewayError = error) }
    }

    private fun showFailure(generation: Int, error: GroupUiError) {
        if (generation != loadGeneration) return
        showOperationFailure(error)
    }

    private fun onSaveDraft() {
        retryDelete = false
        update { it.copy(saveFailed = false, gatewayError = null) }
        emit(GroupSetupEffect.DraftSaved)
    }

    private fun onOpenSheet(sheet: GroupSetupSheet) {
        val index = (sheet as? GroupSetupSheet.Slot)?.index
        val slot = index?.let { state.value.form.regularSlots.getOrNull(it) }
        update { current -> current.copy(sheet = sheet, slotDraft = slot?.toDraft() ?: current.slotDraft) }
    }

    private fun onConfirmSlot() {
        val current = state.value
        val sheet = current.sheet as? GroupSetupSheet.Slot ?: return
        val draft = current.slotDraft
        val slot = GroupRegularSlotForm(
            weekday = draft.weekday,
            startTime = formatTime(draft.hour, draft.minute),
            durationMinutes = current.durationMinutes,
        )
        val slots = current.form.regularSlots
        val updated = when {
            sheet.index == null || sheet.index !in slots.indices -> slots + slot
            else -> slots.toMutableList().also { it[sheet.index] = slot }
        }
        update { it.copy(sheet = null).withForm { copy(regularSlots = updated) }.revalidated() }
    }

    private fun onDurationChange(minutes: Int) = update { current ->
        current.copy(durationMinutes = minutes)
            .withForm { copy(regularSlots = regularSlots.map { it.copy(durationMinutes = minutes) }) }
            .revalidated()
    }

    private fun onRecurringChange(recurring: Boolean) = update { current -> current.copy(recurring = recurring).revalidated() }

    private fun onTextChange(
        key: String,
        value: String,
        maxLength: Int,
        edit: GroupSetupForm.(String) -> GroupSetupForm,
    ) {
        val capped = value.withoutControlChars().takeCodePoints(maxLength)
        savedState[key] = capped
        onFormChange { edit(capped) }
    }

    private fun onFormChange(transform: GroupSetupForm.() -> GroupSetupForm) = update { it.withForm(transform).revalidated() }
}

private fun GroupSetupState.withForm(transform: GroupSetupForm.() -> GroupSetupForm) = copy(form = form.transform())

private fun GroupSetupState.revalidated() = if (errors.isEmpty()) this else copy(errors = validate(this))

private fun GroupSetupState.withSavedText(handle: SavedStateHandle): GroupSetupState {
    val name = handle.get<String>(KeyName)
    val description = handle.get<String>(KeyDescription)
    val customLevel = handle.get<String>(KeyCustomLevel)
    val venueName = handle.get<String>(KeyVenueName)
    val venueAddress = handle.get<String>(KeyVenueAddress)
    val venue = when {
        venueName == null && venueAddress == null -> form.defaultVenue
        else -> (form.defaultVenue ?: EmptyVenue).copy(
            name = venueName ?: form.defaultVenue?.name.orEmpty(),
            address = venueAddress ?: form.defaultVenue?.address.orEmpty(),
        ).orNullWhenCleared()
    }
    return copy(
        form = form.copy(
            name = name ?: form.name,
            description = description ?: form.description,
            customLevel = customLevel ?: form.customLevel,
            defaultVenue = venue,
        ),
    )
}

private fun GroupSetupForm.withVenue(transform: GroupVenueForm.() -> GroupVenueForm) =
    copy(defaultVenue = (defaultVenue ?: EmptyVenue).transform().orNullWhenCleared())

private fun GroupSetupForm.toDomain() = br.com.saqz.groups.domain.group.GroupSetupForm(
    name = name,
    modality = modality?.let { br.com.saqz.groups.domain.group.GroupModality.valueOf(it.name) },
    composition = composition?.let { br.com.saqz.groups.domain.group.GroupComposition.valueOf(it.name) },
    description = description,
    city = city,
    level = level?.let { br.com.saqz.groups.domain.group.GroupLevel.valueOf(it.name) },
    customLevel = customLevel,
    playStyle = playStyle?.let { br.com.saqz.groups.domain.group.GroupPlayStyle.valueOf(it.name) },
    customPlayStyle = customPlayStyle,
    defaultVenue = defaultVenue?.let { br.com.saqz.groups.domain.group.GroupVenue(it.id, it.name, it.address, it.court) },
    regularSlots = regularSlots.map {
        br.com.saqz.groups.domain.group.GroupRegularSlot(
            it.id,
            br.com.saqz.groups.domain.group.GroupWeekday.valueOf(it.weekday.name),
            it.startTime,
            it.durationMinutes,
        )
    },
    defaultCapacity = defaultCapacity,
    defaultConfirmationLeadMinutes = defaultConfirmationLeadMinutes,
    defaultGameFeeCents = defaultGameFeeCents,
    monthlyFeeCents = monthlyFeeCents,
    monthlyDueDay = monthlyDueDay,
).cleaned()

private fun br.com.saqz.groups.domain.group.Group.toForm() = GroupSetupForm(
    name = name,
    modality = profile?.modality?.let { GroupModality.valueOf(it.name) },
    composition = profile?.composition?.let { GroupComposition.valueOf(it.name) },
    description = profile?.description,
    city = profile?.city,
    level = profile?.level?.let { GroupLevel.valueOf(it.name) },
    customLevel = profile?.customLevel,
    playStyle = profile?.playStyle?.let { GroupPlayStyle.valueOf(it.name) },
    customPlayStyle = profile?.customPlayStyle,
    defaultVenue = profile?.defaultVenue?.let { GroupVenueForm(it.id, it.name, it.address, it.court) },
    regularSlots = profile?.regularSlots.orEmpty().map {
        GroupRegularSlotForm(it.id, GroupWeekday.valueOf(it.weekday.name), it.startTime, it.durationMinutes)
    },
    defaultCapacity = profile?.defaultCapacity,
    defaultConfirmationLeadMinutes = profile?.defaultConfirmationLeadMinutes,
    defaultGameFeeCents = financeDefaults?.defaultGameFeeCents,
    monthlyFeeCents = financeDefaults?.monthlyFeeCents,
    monthlyDueDay = financeDefaults?.monthlyDueDay,
)

private fun GroupTimeZone.toDomain() = DomainGroupTimeZone(id)

private fun GroupRegularSlotForm.toDraft(): GroupSlotDraft {
    val parts = startTime.split(':')
    return GroupSlotDraft(weekday, parts.getOrNull(0)?.toIntOrNull() ?: 0, parts.getOrNull(1)?.toIntOrNull() ?: 0)
}

private fun formatTime(hour: Int, minute: Int) = "${hour.pad()}:${minute.pad()}"
private fun Int.pad() = toString().padStart(2, '0')

private val EmptyVenue = GroupVenueForm(name = "", address = "")

@OptIn(ExperimentalUuidApi::class)
private fun newCommandKey(): String = Uuid.random().toString()

private const val KeyName = "group-setup-name"
private const val KeyDescription = "group-setup-description"
private const val KeyCustomLevel = "group-setup-custom-level"
private const val KeyVenueName = "group-setup-venue-name"
private const val KeyVenueAddress = "group-setup-venue-address"
