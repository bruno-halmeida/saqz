package br.com.saqz.groups.presentation.setup

import androidx.lifecycle.SavedStateHandle
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.model.GroupSetupForm
import br.com.saqz.groups.model.GroupVenueForm

/**
 * Uma ViewModel para as duas telas e os dois passos. Ela **não carrega dado**: recebe o
 * estado inicial pronto.
 *
 * ponytail: sem gateway, `Submit`/`ConfirmCreate`/`ConfirmDelete` marcam a flag, emitem o
 * efeito e param. Quando o `GroupGateway` entrar, cada um desses três pontos vira uma
 * chamada em `viewModelScope` que resolve a flag e o `saveFailed`; nenhuma tela muda.
 */
class GroupSetupViewModel(
    initialState: GroupSetupState,
    private val savedState: SavedStateHandle,
) : MviViewModel<GroupSetupState, GroupSetupIntent, GroupSetupEffect>(
    initialState.withSavedText(savedState),
) {

    @Suppress("CyclomaticComplexMethod")
    override fun onIntent(intent: GroupSetupIntent) {
        when (intent) {
            is GroupSetupIntent.UpdateName ->
                onTextChange(KeyName, intent.value) { copy(name = it) }

            is GroupSetupIntent.UpdateDescription ->
                onTextChange(KeyDescription, intent.value) { copy(description = it) }

            is GroupSetupIntent.UpdateCustomLevel ->
                onTextChange(KeyCustomLevel, intent.value) { copy(customLevel = it) }

            is GroupSetupIntent.UpdateVenueName ->
                onTextChange(KeyVenueName, intent.value) { value -> withVenue { copy(name = value) } }

            is GroupSetupIntent.UpdateVenueAddress ->
                onTextChange(KeyVenueAddress, intent.value) { value -> withVenue { copy(address = value) } }

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
            is GroupSetupIntent.RemoveSlot ->
                onFormChange { copy(regularSlots = regularSlots - intent.slot) }

            GroupSetupIntent.PickPhoto -> emit(GroupSetupEffect.PickPhoto)
            GroupSetupIntent.Submit -> onSubmit()
            GroupSetupIntent.Retry -> onSubmit()
            GroupSetupIntent.ConfirmCreate -> onConfirmCreate()
            GroupSetupIntent.BackToForm -> update { it.copy(step = GroupSetupStep.Form) }
            GroupSetupIntent.ConfirmDelete -> onConfirmDelete()
            GroupSetupIntent.SaveDraft -> onSaveDraft()
        }
    }

    private fun onSubmit() {
        val errors = validate(state.value)
        when {
            errors.isNotEmpty() -> update { it.copy(errors = errors, saveFailed = false) }
            state.value.isEditing -> {
                update { it.copy(isSaving = true, saveFailed = false, errors = emptySet()) }
                emit(GroupSetupEffect.Saved)
            }
            // Criar passa pela revisão (`2d`); editar salva direto (`2i`).
            else -> update { it.copy(step = GroupSetupStep.Review, errors = emptySet(), saveFailed = false) }
        }
    }

    private fun onConfirmCreate() {
        update { it.copy(isSaving = true, saveFailed = false) }
        // ponytail: o id sai da resposta do gateway; até lá vai vazio e quem escuta só
        // fecha a tela. `GroupCreateCommand` já existe para quando isso ligar.
        emit(GroupSetupEffect.Created(groupId = ""))
    }

    private fun onConfirmDelete() {
        val mode = state.value.mode
        if (mode !is GroupSetupMode.Edit) return
        update { it.copy(isDeleting = true, sheet = null) }
        emit(GroupSetupEffect.Deleted)
    }

    private fun onSaveDraft() {
        // ponytail: `GroupDraftStorePort` (features/groups/port) guarda o rascunho; hoje
        // só o efeito sai, e quem o escuta decide o que fazer.
        update { it.copy(saveFailed = false) }
        emit(GroupSetupEffect.DraftSaved)
    }

    private fun onOpenSheet(sheet: GroupSetupSheet) {
        val index = (sheet as? GroupSetupSheet.Slot)?.index
        val slot = index?.let { state.value.form.regularSlots.getOrNull(it) }
        update { current ->
            current.copy(
                sheet = sheet,
                slotDraft = slot?.toDraft() ?: current.slotDraft,
            )
        }
    }

    private fun onConfirmSlot() {
        val current = state.value
        val draft = current.slotDraft
        val slot = GroupRegularSlotForm(
            weekday = draft.weekday,
            startTime = formatTime(draft.hour, draft.minute),
            // ponytail: o desenho tem UMA duração para o grupo e o modelo tem uma por
            // slot, então o valor do grupo é espelhado em todo slot. Se o produto pedir
            // duração por horário, o campo do estado sai e o picker ganha a escolha.
            durationMinutes = current.durationMinutes,
        )
        val index = (current.sheet as? GroupSetupSheet.Slot)?.index
        val slots = current.form.regularSlots
        val updated = when {
            index == null || index !in slots.indices -> slots + slot
            else -> slots.toMutableList().also { it[index] = slot }
        }
        update { it.copy(sheet = null).withForm { copy(regularSlots = updated) }.revalidated() }
    }

    private fun onDurationChange(minutes: Int) = update { current ->
        current
            .copy(durationMinutes = minutes)
            .withForm { copy(regularSlots = regularSlots.map { it.copy(durationMinutes = minutes) }) }
            .revalidated()
    }

    private fun onRecurringChange(recurring: Boolean) = update { current ->
        current.copy(recurring = recurring).revalidated()
    }

    private fun onTextChange(key: String, value: String, edit: GroupSetupForm.(String) -> GroupSetupForm) {
        savedState[key] = value
        onFormChange { edit(value) }
    }

    private fun onFormChange(transform: GroupSetupForm.() -> GroupSetupForm) = update { current ->
        current.withForm(transform).revalidated()
    }
}

private fun GroupSetupState.withForm(transform: GroupSetupForm.() -> GroupSetupForm) =
    copy(form = form.transform())

/** Erro já mostrado some assim que o campo é corrigido; antes do primeiro submit, nada valida. */
private fun GroupSetupState.revalidated() = if (errors.isEmpty()) this else copy(errors = validate(this))

private fun GroupSetupForm.withVenue(transform: GroupVenueForm.() -> GroupVenueForm) =
    copy(defaultVenue = (defaultVenue ?: EmptyVenue).transform())

private fun GroupRegularSlotForm.toDraft(): GroupSlotDraft {
    val parts = startTime.split(':')
    return GroupSlotDraft(
        weekday = weekday,
        hour = parts.getOrNull(0)?.toIntOrNull() ?: 0,
        minute = parts.getOrNull(1)?.toIntOrNull() ?: 0,
    )
}

private fun formatTime(hour: Int, minute: Int): String =
    "${hour.pad()}:${minute.pad()}"

private fun Int.pad(): String = toString().padStart(2, '0')

private val EmptyVenue = GroupVenueForm(name = "", address = "")

// Process death: só o texto digitado. Enum, pílula e switch são baratos de repicar.
private const val KeyName = "group-setup-name"
private const val KeyDescription = "group-setup-description"
private const val KeyCustomLevel = "group-setup-custom-level"
private const val KeyVenueName = "group-setup-venue-name"
private const val KeyVenueAddress = "group-setup-venue-address"

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
        )
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
