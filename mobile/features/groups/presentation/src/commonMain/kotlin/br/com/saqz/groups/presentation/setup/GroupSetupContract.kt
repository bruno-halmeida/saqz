package br.com.saqz.groups.presentation.setup

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.model.GroupComposition
import br.com.saqz.groups.model.GroupLevel
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.model.GroupPlayStyle
import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.model.GroupSetupForm
import br.com.saqz.groups.model.GroupVenueForm
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.model.MonthlyLateJoinPolicy
import br.com.saqz.groups.model.PromotionMode
import br.com.saqz.groups.domain.place.PlaceSuggestion
import br.com.saqz.groups.presentation.GameCapacityLimits
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.place.VenueSearchState

/** `2a` cria e `2i` edita: mesma tela, mesmos doze cards, modos diferentes. */
sealed interface GroupSetupMode {
    data object Create : GroupSetupMode

    data class Edit(val groupId: String) : GroupSetupMode
}

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

object GroupPositionDefaults {
    const val MaxPerPosition = 100
}

object GroupSetupDefaults {
    // O backend recusa fora de `2..100`, mas grava 4 quando um app antigo manda 2 ou 3
    // (GroupProfileDefaults.kt); o piso de hoje é o mesmo das vagas de um jogo.
    const val MinCapacity = GameCapacityLimits.Min
    const val MaxCapacity = 100
    const val Capacity = 12
    const val MaxGameFeeCents = 99_999_999L
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

internal object GroupPixTextLimits {
    const val Min = 2
    const val KeyMax = 140
    const val LabelMax = 80
}

@Immutable
data class GroupSetupState(
    val mode: GroupSetupMode,
    val form: GroupSetupForm = GroupSetupDefaults.Form,
    val pixKey: String? = null,
    val pixLabel: String? = null,
    val photoUrl: String? = null,
    val photo: ImageBitmap? = null,
    // Só o `2j` usa: membros ativos fora o dono, que perdem o acesso. `null` = ainda não chegou ou falhou.
    val memberCount: Int? = null,
    /** Só o OWNER pode excluir; enquanto o snapshot não chega, a ação fica escondida. */
    val canDelete: Boolean = false,
    /** Configurações de jogo são visíveis apenas ao OWNER/ADMIN. */
    val canManageGameConfig: Boolean = false,
    // NÃO se deriva de `form.regularSlots.isEmpty()`: ligado e sem horário é o `2g`.
    // Na criação começa desligado: o primeiro cadastro são só os três campos à vista, e
    // quadra e horários (obrigatórios com a recorrência) ficam para as opções avançadas.
    // Na edição, o `load()` liga quando o grupo já tem horário.
    val recurring: Boolean = false,
    // Duração é do grupo no desenho e por slot no modelo; ver GroupSetupViewModel.
    val durationMinutes: Int = GroupSetupDefaults.DurationMinutes,
    val errors: Set<GroupSetupError> = emptySet(),
    val sheet: GroupSetupSheet? = null,
    val slotDraft: GroupSlotDraft = GroupSlotDraft(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val saveFailed: Boolean = false,
    val gatewayError: GroupUiError? = null,
    /** O backend recusou a gravação por falta de plano: a UI abre o diálogo com a oferta. */
    val subscriptionRequired: Boolean = false,
    /** Distingue "o teste terminou" de "o plano não está ativo" no título do diálogo. */
    val trialEnded: Boolean = false,
    /** Chave de idempotência do payload atual; qualquer edição a descarta antes do retry. */
    val creationCommandKey: String? = null,
    val isOffline: Boolean = false,
    /** Sugestões de endereço do Google; indisponível quando o backend desliga a busca. */
    val venueSearch: VenueSearchState = VenueSearchState(),
    /**
     * Algum mensalista tem mensalidade própria (a mesma regra do "Mensalistas" da checklist da Início).
     * Chega depois do formulário, em uma busca à parte, e só na edição.
     */
    val rosterHasMensalista: Boolean = false,
    /** DEB-102: vagas por posição (só as que têm vaga); vazio = o grupo segue com o total de jogadores. */
    val positionSlots: Map<AthletePosition, Int> = emptyMap(),
    val isSuggestingPositions: Boolean = false,
    /** As vagas por posição do grupo já chegaram do servidor; sem isso o 5-1 não bloqueia o Salvar nem mostra o card. */
    val positionSlotsReady: Boolean = false,
    val positionSuggestionFailed: Boolean = false,
) {
    val isEditing: Boolean = mode is GroupSetupMode.Edit

    /**
     * Vagas por posição na criação (quem cria é o dono) e na edição (para quem gere o grupo), em quadra no 5-1 (6-0 não
     * usa posições: segue o total). No 5-1 elas são obrigatórias e a soma precisa bater com o total de jogadores do
     * grupo (decisão do dono, 20/10).
     */
    val showsPositionSlots: Boolean =
        (!isEditing || canManageGameConfig) && positionSlotsReady && form.modality == GroupModality.COURT_VOLLEYBALL &&
            form.playStyle == GroupPlayStyle.FIVE_ONE
    val positionsTotal: Int = positionSlots.values.sum()

    /** O total de jogadores do grupo, que a soma das posições tem de igualar. */
    val positionsTarget: Int = form.defaultCapacity ?: GroupSetupDefaults.Capacity

    /** Positivo = sobram vagas distribuídas; negativo = faltam. Zero = fecha. */
    val positionsDifference: Int = positionsTotal - positionsTarget

    /** Salvar fica bloqueado enquanto a soma das posições não bate com o total. */
    val positionsBlockSave: Boolean = showsPositionSlots && positionsDifference != 0
    val showsPlayStyle: Boolean = form.modality == GroupModality.COURT_VOLLEYBALL
    val showsCustomLevel: Boolean = form.level == GroupLevel.CUSTOM

    /**
     * O seletor de "quem vira mensalista depois do vencimento" só existe na edição, para quem gere o
     * grupo, se o backend mandou o campo e o grupo cobra mensalidade: tem mensalidade padrão **ou** algum
     * mensalista com valor próprio, como o item "Mensalistas" da checklist da Início.
     */
    val showsMonthlyLateJoinPolicy: Boolean =
        isEditing && canManageGameConfig && form.monthlyLateJoinPolicy != null &&
            (form.monthlyFeeCents != null || rosterHasMensalista)

    /**
     * O que vai para o comando. `GroupSetupForm` não tem campo de recorrência e o
     * gateway serializa `regularSlots` direto, então desligar o switch tem de recortar
     * aqui — os horários **continuam** no formulário, e religar o switch os devolve.
     */
    val slotsForCommand: List<GroupRegularSlotForm> =
        if (recurring) form.regularSlots else emptyList()

    /** Terceiro ponto da mesma regra de [orNullWhenCleared]: o que o comando leva. */
    val venueForCommand: GroupVenueForm? = form.defaultVenue?.orNullWhenCleared()
}

/**
 * **A regra da quadra nula, uma só, para os três pontos que decidem isso**: ao alterar um
 * campo, ao restaurar do `SavedStateHandle` e ao montar o comando. O backend trata
 * `defaultVenue` como opcional-mas-completo — se vier, exige nome e endereço —, então
 * meia quadra não pode existir.
 *
 * A decisão olha **só os campos editáveis nesta tela**, nome e endereço. `court` e `id`
 * são dados do backend sem campo aqui: mantê-los vivos depois que o usuário apagou os
 * dois campos visíveis prenderia a tela numa quadra que ele acha que apagou, acusando
 * nome e endereço em branco sem lhe dar como corrigir. Perder o `court` herdado é o
 * preço, e é menor.
 */
internal fun GroupVenueForm.orNullWhenCleared(): GroupVenueForm? =
    takeUnless { name.isBlank() && address.isBlank() }

sealed interface GroupSetupIntent {
    data class UpdateName(val value: String) : GroupSetupIntent

    data class UpdateDescription(val value: String) : GroupSetupIntent

    data class UpdatePixKey(val value: String) : GroupSetupIntent

    data class UpdatePixLabel(val value: String) : GroupSetupIntent

    data class UpdateCustomLevel(val value: String) : GroupSetupIntent

    data class UpdateVenueName(val value: String) : GroupSetupIntent

    data class UpdateVenueAddress(val value: String) : GroupSetupIntent

    data class SelectPlaceSuggestion(val suggestion: PlaceSuggestion) : GroupSetupIntent

    data object DismissPlaceSuggestions : GroupSetupIntent

    data class SelectModality(val value: GroupModality) : GroupSetupIntent

    data class SelectComposition(val value: GroupComposition) : GroupSetupIntent

    data class SelectLevel(val value: GroupLevel) : GroupSetupIntent

    data class SelectPlayStyle(val value: GroupPlayStyle) : GroupSetupIntent

    data class UpdateCapacity(val value: Int) : GroupSetupIntent

    /** DEB-102: vagas de uma posição; 0 tira a posição. */
    data class UpdatePositionSlots(val position: AthletePosition, val value: Int) : GroupSetupIntent

    /** Preenche as posições com a sugestão do servidor para o total de jogadores do grupo (editável depois). */
    data object SuggestPositionSlots : GroupSetupIntent
    /** Valor por jogo em centavos; `null` = sem cobrança. */
    data class UpdateDefaultGameFee(val cents: Long?) : GroupSetupIntent

    data class SelectDuration(val minutes: Int) : GroupSetupIntent

    data class SelectConfirmationLead(val minutes: Int) : GroupSetupIntent

    data class ToggleMensalistaPriority(val value: Boolean) : GroupSetupIntent

    data class SelectPromotionMode(val value: PromotionMode) : GroupSetupIntent


    data class SelectMonthlyLateJoinPolicy(val value: MonthlyLateJoinPolicy) : GroupSetupIntent

    data class ToggleRecurring(val value: Boolean) : GroupSetupIntent

    data class OpenSheet(val sheet: GroupSetupSheet) : GroupSetupIntent

    data object CloseSheet : GroupSetupIntent

    data class PickSlotWeekday(val weekday: GroupWeekday) : GroupSetupIntent

    data class PickSlotTime(val hour: Int, val minute: Int) : GroupSetupIntent

    data object ConfirmSlot : GroupSetupIntent

    data class RemoveSlot(val slot: GroupRegularSlotForm) : GroupSetupIntent

    data object PickPhoto : GroupSetupIntent

    data object Submit : GroupSetupIntent

    data object ConfirmDelete : GroupSetupIntent

    data object Retry : GroupSetupIntent


    data object DismissSubscriptionRequired : GroupSetupIntent
}

sealed interface GroupSetupEffect {
    data class Created(val groupId: String) : GroupSetupEffect

    data object Saved : GroupSetupEffect

    data object Deleted : GroupSetupEffect


    data object PickPhoto : GroupSetupEffect
}
