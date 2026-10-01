package br.com.saqz.groups.presentation.gameeditor

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.map
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.domain.group.Group
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.group.GroupProfileError
import br.com.saqz.groups.domain.group.GroupProfileGateway
import br.com.saqz.groups.domain.group.GroupRegularSlot
import br.com.saqz.groups.domain.group.GroupSetupForm
import br.com.saqz.groups.domain.group.GroupVenue
import br.com.saqz.groups.domain.group.GroupWeekday
import br.com.saqz.groups.domain.group.UpdateGroupProfileCommand
import br.com.saqz.groups.domain.group.VersionedGroup
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * "Repetir toda semana" no editor (4a): o dia e a hora do jogo viram horário regular do grupo.
 * Regra densa fora do ViewModel (AGENTS.md §4).
 */

/** O horário regular que o formulário pede; `null` sem data ou hora válidas. */
fun recurrenceSlotFor(form: GameEditorFields): GroupRegularSlot? {
    val date = runCatching { LocalDate.parse(form.localDate) }.getOrNull() ?: return null
    val time = runCatching { LocalTime.parse(form.localTime) }.getOrNull() ?: return null
    return GroupRegularSlot(
        weekday = GroupWeekday.entries[date.dayOfWeek.ordinal],
        startTime = "${time.hour.pad()}:${time.minute.pad()}",
        durationMinutes = form.durationMinutes,
    )
}

/** `true` quando o grupo já se repete nesse dia e hora: não há o que gravar. */
fun List<GroupRegularSlot>.hasSlotAt(slot: GroupRegularSlot): Boolean =
    any { it.weekday == slot.weekday && it.startTime == slot.startTime }

/**
 * O perfil do grupo com o horário novo no fim da lista e, se o grupo ainda não tem quadra, a
 * quadra deste jogo como padrão — sem ela o backend não gera os jogos da semana. `null` quando o
 * perfil está incompleto (sem modalidade ou composição), que o `PUT` do grupo recusaria.
 */
fun recurrenceProfileForm(group: Group, venue: GameVenue?, slot: GroupRegularSlot): GroupSetupForm? {
    val profile = group.profile ?: return null
    val modality = profile.modality ?: return null
    val composition = profile.composition ?: return null
    return GroupSetupForm(
        name = group.name,
        modality = modality,
        composition = composition,
        description = profile.description,
        city = profile.city,
        level = profile.level,
        customLevel = profile.customLevel,
        playStyle = profile.playStyle,
        customPlayStyle = profile.customPlayStyle,
        defaultVenue = profile.defaultVenue ?: venue?.let { GroupVenue(it.venueId, it.name, it.address, it.court) },
        regularSlots = profile.regularSlots + slot,
        defaultCapacity = profile.defaultCapacity,
        defaultConfirmationLeadMinutes = profile.defaultConfirmationLeadMinutes,
        defaultGameFeeCents = group.financeDefaults?.defaultGameFeeCents,
        monthlyFeeCents = group.financeDefaults?.monthlyFeeCents,
        monthlyDueDay = group.financeDefaults?.monthlyDueDay,
        mensalistaPriority = group.gameConfig.mensalistaPriority,
        promotionMode = group.gameConfig.promotionMode,
        autoConfirmEnabled = group.gameConfig.autoConfirmEnabled,
        pixKey = profile.pixKey,
        pixLabel = profile.pixLabel,
    ).cleaned()
}

/**
 * Grava o dia e a hora do jogo como horário regular do grupo. Lê o grupo de novo porque o token de
 * versão pode ter envelhecido desde a abertura da tela. Sem repetição pedida, não faz nada.
 */
internal suspend fun saveRecurrence(
    groupId: String,
    state: GameEditorState,
    groupGateway: GroupGateway,
    profileGateway: GroupProfileGateway,
): SaqzResult<Unit, GroupProfileError> {
    val slot = recurrenceSlotFor(state.form)
    if (!state.recurrenceOffered || !state.form.recurring || slot == null) return SaqzResult.Success(Unit)
    return when (val read = groupGateway.read(GroupId(groupId))) {
        is SaqzResult.Failure -> SaqzResult.Failure(read.error)
        is SaqzResult.Success -> addRegularSlot(groupId, read.value, state.form.venue, slot, profileGateway)
    }
}

private suspend fun addRegularSlot(
    groupId: String,
    versioned: VersionedGroup,
    venue: GameVenue?,
    slot: GroupRegularSlot,
    profileGateway: GroupProfileGateway,
): SaqzResult<Unit, GroupProfileError> {
    if (versioned.group.profile?.regularSlots.orEmpty().hasSlotAt(slot)) return SaqzResult.Success(Unit)
    val form = recurrenceProfileForm(versioned.group, venue, slot)
        ?: return SaqzResult.Failure(GroupProfileError.DataFailure(DataError.InvalidResponse))
    return profileGateway.updateProfile(
        UpdateGroupProfileCommand(GroupId(groupId), versioned.versionToken, form),
    ).map { }
}

private fun Int.pad(): String = toString().padStart(2, '0')
