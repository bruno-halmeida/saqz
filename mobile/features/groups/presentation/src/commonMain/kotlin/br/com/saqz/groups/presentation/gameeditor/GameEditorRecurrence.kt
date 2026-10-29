package br.com.saqz.groups.presentation.gameeditor

import br.com.saqz.groups.presentation.GameFeeChoice
import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.map
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.presentation.GameCapacityLimits
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
 * E, ao marcar o primeiro jogo de um grupo sem quadra, a quadra do jogo vira a quadra padrão
 * do grupo. Regra densa fora do ViewModel (AGENTS.md §4).
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
 * O perfil do grupo com o horário novo (se houver) no fim da lista e, se o grupo ainda não tem
 * quadra, a quadra deste jogo como padrão — sem ela o backend não gera os jogos da semana e o
 * próximo jogo pediria a quadra de novo. `null` quando o perfil está incompleto (sem modalidade
 * ou composição), que o `PUT` do grupo recusaria.
 */
fun recurrenceProfileForm(
    group: Group,
    venue: GameVenue?,
    slot: GroupRegularSlot?,
    fee: RecurrenceGameFee? = null,
): GroupSetupForm? {
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
        regularSlots = profile.regularSlots + listOfNotNull(slot),
        defaultCapacity = profile.defaultCapacity?.coerceAtLeast(GameCapacityLimits.Min),
        defaultConfirmationLeadMinutes = profile.defaultConfirmationLeadMinutes,
        defaultGameFeeCents = if (fee != null) fee.cents else group.financeDefaults?.defaultGameFeeCents,
        // Só manda a marca quando a escolha deste jogo vira a do grupo; sem ela o servidor mantém a que tem.
        defaultNoGameFee = fee?.noGameFee,
        monthlyFeeCents = group.financeDefaults?.monthlyFeeCents,
        monthlyDueDay = group.financeDefaults?.monthlyDueDay,
        mensalistaPriority = group.gameConfig.mensalistaPriority,
        promotionMode = group.gameConfig.promotionMode,
        pixKey = profile.pixKey,
        pixLabel = profile.pixLabel,
    ).cleaned()
}

/**
 * Grava no grupo o que o jogo recém-marcado pede: o dia e a hora como horário regular (com
 * "Repetir toda semana") e a quadra como padrão, quando o grupo não tem uma e o jogo é novo. Lê
 * o grupo de novo porque o token de versão pode ter envelhecido desde a abertura da tela e porque
 * é ele, não a tela, que sabe se já há quadra. Nada a gravar, nada feito.
 *
 * Só a repetição é falha para quem chama: a quadra padrão é cortesia para o próximo jogo, e o
 * jogo já está marcado — se ela não gravar, a próxima criação pede a quadra de novo.
 */
/**
 * DEB-146: grupo sem valor por jogo que ganha "Repetir toda semana" recebe a escolha deste jogo, para os jogos da
 * semana não nascerem "não definidos".
 */
data class RecurrenceGameFee(val cents: Long?, val noGameFee: Boolean)

internal suspend fun saveGroupDefaults(
    groupId: String,
    state: GameEditorState,
    creating: Boolean,
    groupGateway: GroupGateway,
    profileGateway: GroupProfileGateway,
): SaqzResult<Unit, GroupProfileError> {
    val slot = recurrenceSlotFor(state.form).takeIf { state.recurrenceOffered && state.form.recurring }
    val venue = state.form.venue?.takeIf { creating && it.name.isNotBlank() }
    if (slot == null && venue == null) return SaqzResult.Success(Unit)
    val read = groupGateway.read(GroupId(groupId))
    val saved = when (read) {
        is SaqzResult.Failure -> SaqzResult.Failure(read.error)
        is SaqzResult.Success -> writeGroupDefaults(groupId, read.value, venue, slot, profileGateway, state.recurrenceFee(slot))
    }
    // Sem repetição pedida, a quadra padrão é cortesia: falhar não falha o jogo.
    return if (saved is SaqzResult.Failure && slot == null) SaqzResult.Success(Unit) else saved
}

private suspend fun writeGroupDefaults(
    groupId: String,
    versioned: VersionedGroup,
    venue: GameVenue?,
    slot: GroupRegularSlot?,
    profileGateway: GroupProfileGateway,
    fee: RecurrenceGameFee? = null,
): SaqzResult<Unit, GroupProfileError> {
    val profile = versioned.group.profile
    val newSlot = slot?.takeUnless { profile?.regularSlots.orEmpty().hasSlotAt(it) }
    val newVenue = venue?.takeIf { profile?.defaultVenue == null }
    val groupFee = fee?.takeIf { versioned.group.financeDefaults?.gameFeeUndefined == true }
    // O horário já existe, mas o grupo ainda sem valor ganha a escolha deste jogo mesmo assim (DEB-146).
    if (newSlot == null && newVenue == null && groupFee == null) return SaqzResult.Success(Unit)
    val form = recurrenceProfileForm(versioned.group, newVenue, newSlot, groupFee)
        ?: return SaqzResult.Failure(GroupProfileError.DataFailure(DataError.InvalidResponse))
    return profileGateway.updateProfile(
        UpdateGroupProfileCommand(GroupId(groupId), versioned.versionToken, form),
    ).map { }
}

private fun GameEditorState.recurrenceFee(slot: GroupRegularSlot?): RecurrenceGameFee? = when {
    slot == null || !groupFeeUndefined -> null
    form.gameFeeChoice == GameFeeChoice.NoFee -> RecurrenceGameFee(null, noGameFee = true)
    form.gameFeeChoice == GameFeeChoice.Charge -> form.gameFeeCents?.let { RecurrenceGameFee(it, noGameFee = false) }
    else -> null
}

private fun Int.pad(): String = toString().padStart(2, '0')
