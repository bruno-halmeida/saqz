package br.com.saqz.groups.presentation.setup

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.model.GroupComposition
import br.com.saqz.groups.model.GroupLevel
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.model.GroupPlayStyle
import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.model.GroupSetupForm
import br.com.saqz.groups.model.GroupWeekday

/** `2a` cria e `2i` edita: mesma tela, mesmos doze cards, modos diferentes. */
sealed interface GroupSetupMode {
    data object Create : GroupSetupMode

    data class Edit(val groupId: String) : GroupSetupMode
}

/** `2d` é passo, não rota: a revisão lê o mesmo formulário já preenchido. */
enum class GroupSetupStep { Form, Review }

sealed interface GroupSetupSheet {
    data object Modality : GroupSetupSheet

    data object Level : GroupSetupSheet

    /** `index == null` abre o `2c` para um horário novo. */
    data class Slot(val index: Int?) : GroupSetupSheet

    data object ConfirmDelete : GroupSetupSheet
}

/**
 * Espelha o `SlotDraft` do VUL-66, que é `internal` e por isso não atravessa a
 * API pública deste módulo — e o `:compose-app` precisa registrar a ViewModel no
 * Koin. A conversão é uma linha, no ponto onde o `GroupSlotPicker` é chamado.
 */
data class GroupSlotDraft(
    val weekday: GroupWeekday = GroupWeekday.TUESDAY,
    val hour: Int = 19,
    val minute: Int = 30,
)

object GroupSetupDefaults {
    const val MinCapacity = 2
    const val Capacity = 12
    const val DurationMinutes = 120
    const val ConfirmationLeadMinutes = 360

    val DurationOptions = listOf(60, 90, 120, 150)
    val ConfirmationLeadOptions = listOf(180, 360, 720, 1_440)

    /**
     * O `2a` abre com público, sistema, capacidade e antecedência já escolhidos — o
     * export nunca desenha esses quatro sem seleção, e `GroupSetupError` não tem erro
     * para nenhum deles. Modalidade e categoria continuam vazias de propósito.
     */
    val Form = GroupSetupForm(
        composition = GroupComposition.MIXED,
        playStyle = GroupPlayStyle.SIX_ZERO,
        defaultCapacity = Capacity,
        defaultConfirmationLeadMinutes = ConfirmationLeadMinutes,
    )
}

@Immutable
data class GroupSetupState(
    val mode: GroupSetupMode,
    val step: GroupSetupStep = GroupSetupStep.Form,
    val form: GroupSetupForm = GroupSetupDefaults.Form,
    val photoUrl: String? = null,
    // Só o `2j` usa: "Os %d membros perdem o acesso…". Não sai do formulário.
    val memberCount: Int = 0,
    // NÃO se deriva de `form.regularSlots.isEmpty()`: ligado e sem horário é o `2g`.
    val recurring: Boolean = true,
    // Duração é do grupo no desenho e por slot no modelo; ver GroupSetupViewModel.
    val durationMinutes: Int = GroupSetupDefaults.DurationMinutes,
    val errors: Set<GroupSetupError> = emptySet(),
    val sheet: GroupSetupSheet? = null,
    val slotDraft: GroupSlotDraft = GroupSlotDraft(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val saveFailed: Boolean = false,
    val isOffline: Boolean = false,
) {
    val isEditing: Boolean = mode is GroupSetupMode.Edit
    val showsPlayStyle: Boolean = form.modality == GroupModality.COURT_VOLLEYBALL
    val showsCustomLevel: Boolean = form.level == GroupLevel.CUSTOM
}

sealed interface GroupSetupIntent {
    data class UpdateName(val value: String) : GroupSetupIntent

    data class UpdateDescription(val value: String) : GroupSetupIntent

    data class UpdateCustomLevel(val value: String) : GroupSetupIntent

    data class UpdateVenueName(val value: String) : GroupSetupIntent

    data class UpdateVenueAddress(val value: String) : GroupSetupIntent

    data class SelectModality(val value: GroupModality) : GroupSetupIntent

    data class SelectComposition(val value: GroupComposition) : GroupSetupIntent

    data class SelectLevel(val value: GroupLevel) : GroupSetupIntent

    data class SelectPlayStyle(val value: GroupPlayStyle) : GroupSetupIntent

    data class UpdateCapacity(val value: Int) : GroupSetupIntent

    data class SelectDuration(val minutes: Int) : GroupSetupIntent

    data class SelectConfirmationLead(val minutes: Int) : GroupSetupIntent

    data class ToggleRecurring(val value: Boolean) : GroupSetupIntent

    data class OpenSheet(val sheet: GroupSetupSheet) : GroupSetupIntent

    data object CloseSheet : GroupSetupIntent

    data class PickSlotWeekday(val weekday: GroupWeekday) : GroupSetupIntent

    data class PickSlotTime(val hour: Int, val minute: Int) : GroupSetupIntent

    data object ConfirmSlot : GroupSetupIntent

    data class RemoveSlot(val slot: GroupRegularSlotForm) : GroupSetupIntent

    data object PickPhoto : GroupSetupIntent

    data object Submit : GroupSetupIntent

    data object ConfirmCreate : GroupSetupIntent

    data object BackToForm : GroupSetupIntent

    data object ConfirmDelete : GroupSetupIntent

    data object Retry : GroupSetupIntent

    data object SaveDraft : GroupSetupIntent
}

sealed interface GroupSetupEffect {
    data class Created(val groupId: String) : GroupSetupEffect

    data object Saved : GroupSetupEffect

    data object Deleted : GroupSetupEffect

    data object DraftSaved : GroupSetupEffect

    data object PickPhoto : GroupSetupEffect
}
