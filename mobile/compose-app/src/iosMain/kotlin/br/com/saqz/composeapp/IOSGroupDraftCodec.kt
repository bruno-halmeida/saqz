package br.com.saqz.composeapp

import br.com.saqz.groups.domain.finance.ExpenseCategory
import br.com.saqz.groups.model.*
import br.com.saqz.groups.presentation.finance.expenses.ExpenseForm
import br.com.saqz.groups.presentation.finance.charges.MonthlyChargeDraft
import br.com.saqz.groups.presentation.finance.expenses.ExpenseDraft
import br.com.saqz.groups.presentation.games.editor.GameEditorDraft
import br.com.saqz.groups.presentation.games.editor.GameEditorForm
import br.com.saqz.groups.presentation.games.editor.GameEditorMode
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.domain.game.GameVersionToken
import br.com.saqz.groups.domain.game.SeriesBoundaryScope
import br.com.saqz.groups.domain.game.Weekday
import br.com.saqz.groups.domain.game.WeeklySlot
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private enum class PersistedGameEditorMode { ONE_TIME, WEEKLY }

@Serializable
private enum class PersistedSeriesBoundaryScope { ONLY_THIS, THIS_AND_FUTURE }

@Serializable
private enum class PersistedWeekday { MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY }

@Serializable
private data class PersistedGameVenue(
    val venueId: String? = null,
    val name: String,
    val address: String,
    val court: String? = null,
)

@Serializable
private data class PersistedWeeklySlot(
    val slotKey: String,
    val weekday: PersistedWeekday,
    val localTime: String,
    val durationMinutes: Int,
    val venue: PersistedGameVenue,
    val capacity: Int,
    val confirmationLeadMinutes: Int,
    val gameFeeCents: Long? = null,
    val title: String,
)

@Serializable
private data class PersistedGameEditorForm(
    val title: String = "",
    val venue: PersistedGameVenue? = null,
    val localDate: String = "",
    val localTime: String = "",
    val zoneId: String = "",
    val startsAt: String = "",
    val durationMinutes: String = "",
    val capacity: String = "",
    val confirmationDeadline: String = "",
    val gameFeeBrl: String = "",
    val notes: String = "",
    val localEndDate: String = "",
    val slots: List<PersistedWeeklySlot> = emptyList(),
)

@Serializable
private data class PersistedGameEditorDraft(
    val schemaVersion: Int = GameEditorDraft.CURRENT_SCHEMA,
    val groupId: String,
    val gameId: String?,
    val seriesId: String?,
    val commandKey: String,
    val etag: String?,
    val mode: PersistedGameEditorMode,
    val form: PersistedGameEditorForm,
    val scope: PersistedSeriesBoundaryScope? = null,
)

@Serializable
private enum class IOSPersistedGroupModality { COURT_VOLLEYBALL, BEACH_VOLLEYBALL, FOOTVOLLEY }

@Serializable
private enum class IOSPersistedGroupComposition { WOMEN, MEN, MIXED }

@Serializable
private enum class IOSPersistedGroupLevel { BEGINNER, INTERMEDIATE, ADVANCED, MIXED_LEVELS, CUSTOM }

@Serializable
private enum class IOSPersistedGroupPlayStyle { SIX_ZERO, FOUR_TWO, FIVE_ONE, CUSTOM }

@Serializable
private enum class IOSPersistedGroupWeekday { MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY }

@Serializable
private enum class IOSPersistedGroupDraftResource { CREATE_GROUP, UPDATE_GROUP }

@Serializable
private enum class IOSPersistedExpenseCategory { VENUE, EQUIPMENT, REFEREE, OTHER }

@Serializable
private data class IOSPersistedGroupVenueForm(
    val id: String? = null,
    val name: String,
    val address: String,
    val court: String? = null,
)

@Serializable
private data class IOSPersistedGroupRegularSlotForm(
    val id: String? = null,
    val weekday: IOSPersistedGroupWeekday,
    val startTime: String,
    val durationMinutes: Int,
)

@Serializable
private data class IOSPersistedGroupSetupForm(
    val name: String = "",
    val modality: IOSPersistedGroupModality? = null,
    val composition: IOSPersistedGroupComposition? = null,
    val description: String? = null,
    val city: String? = null,
    val level: IOSPersistedGroupLevel? = null,
    val customLevel: String? = null,
    val playStyle: IOSPersistedGroupPlayStyle? = null,
    val customPlayStyle: String? = null,
    val defaultVenue: IOSPersistedGroupVenueForm? = null,
    val regularSlots: List<IOSPersistedGroupRegularSlotForm> = emptyList(),
    val defaultCapacity: Int? = null,
    val defaultConfirmationLeadMinutes: Int? = null,
    val defaultGameFeeCents: Long? = null,
    val monthlyFeeCents: Long? = null,
    val monthlyDueDay: Int? = null,
)

@Serializable
private data class IOSPersistedGroupSetupDraft(
    val schemaVersion: Int = GroupSetupDraft.CURRENT_SCHEMA_VERSION,
    val resource: IOSPersistedGroupDraftResource,
    val groupId: String?,
    val groupVersion: Long?,
    val etag: String?,
    val commandKey: String,
    val form: IOSPersistedGroupSetupForm,
)

@Serializable
private data class IOSPersistedMonthlyChargeDraft(
    val schemaVersion: Int = MonthlyChargeDraft.CURRENT_SCHEMA,
    val groupId: String,
    val commandKey: String,
    val month: String,
    val amountBrl: String,
    val dueDate: String,
    val selectedMemberIds: Set<String>,
    val reviewed: Boolean,
)

@Serializable
private data class IOSPersistedExpenseForm(
    val description: String = "",
    val amountBrl: String = "",
    val expenseDate: String = "",
    val category: IOSPersistedExpenseCategory? = null,
    val customCategory: String = "",
    val notes: String = "",
)

@Serializable
private data class IOSPersistedExpenseDraft(
    val schemaVersion: Int = ExpenseDraft.CURRENT_SCHEMA,
    val groupId: String,
    val expenseId: String? = null,
    val etag: String? = null,
    val commandKey: String,
    val form: IOSPersistedExpenseForm,
)

class IOSGroupDraftCodec {
    private val json = Json { explicitNulls = false; ignoreUnknownKeys = false }

    fun encodeSetup(value: GroupSetupDraft): String = json.encodeToString(value.toIOSPersisted())
    fun encodeGame(value: GameEditorDraft): String = json.encodeToString(value.toPersisted())
    fun encodeMonthly(value: MonthlyChargeDraft): String = json.encodeToString(value.toIOSPersisted())
    fun encodeExpense(value: ExpenseDraft): String = json.encodeToString(value.toIOSPersisted())

    @Throws(Exception::class)
    fun decodeSetup(value: String): GroupSetupDraft =
        json.decodeFromString<IOSPersistedGroupSetupDraft>(value).toDomain()

    @Throws(Exception::class)
    fun decodeGame(value: String): GameEditorDraft = json.decodeFromString<PersistedGameEditorDraft>(value).toDomain()

    @Throws(Exception::class)
    fun decodeMonthly(value: String): MonthlyChargeDraft =
        json.decodeFromString<IOSPersistedMonthlyChargeDraft>(value).toDomain()

    @Throws(Exception::class)
    fun decodeExpense(value: String): ExpenseDraft =
        json.decodeFromString<IOSPersistedExpenseDraft>(value).toDomain()
}

private fun GroupSetupDraft.toIOSPersisted() = IOSPersistedGroupSetupDraft(
    schemaVersion,
    IOSPersistedGroupDraftResource.valueOf(resource.name),
    groupId,
    groupVersion,
    etag,
    commandKey,
    form.toIOSPersisted(),
)

private fun IOSPersistedGroupSetupDraft.toDomain() = GroupSetupDraft(
    schemaVersion,
    GroupDraftResource.valueOf(resource.name),
    groupId,
    groupVersion,
    etag,
    commandKey,
    form.toDomain(),
)

private fun GroupSetupForm.toIOSPersisted() = IOSPersistedGroupSetupForm(
    name,
    modality?.let { IOSPersistedGroupModality.valueOf(it.name) },
    composition?.let { IOSPersistedGroupComposition.valueOf(it.name) },
    description,
    city,
    level?.let { IOSPersistedGroupLevel.valueOf(it.name) },
    customLevel,
    playStyle?.let { IOSPersistedGroupPlayStyle.valueOf(it.name) },
    customPlayStyle,
    defaultVenue?.let { IOSPersistedGroupVenueForm(it.id, it.name, it.address, it.court) },
    regularSlots.map {
        IOSPersistedGroupRegularSlotForm(
            it.id,
            IOSPersistedGroupWeekday.valueOf(it.weekday.name),
            it.startTime,
            it.durationMinutes,
        )
    },
    defaultCapacity,
    defaultConfirmationLeadMinutes,
    defaultGameFeeCents,
    monthlyFeeCents,
    monthlyDueDay,
)

private fun IOSPersistedGroupSetupForm.toDomain() = GroupSetupForm(
    name,
    modality?.let { GroupModality.valueOf(it.name) },
    composition?.let { GroupComposition.valueOf(it.name) },
    description,
    city,
    level?.let { GroupLevel.valueOf(it.name) },
    customLevel,
    playStyle?.let { GroupPlayStyle.valueOf(it.name) },
    customPlayStyle,
    defaultVenue?.let { GroupVenueForm(it.id, it.name, it.address, it.court) },
    regularSlots.map {
        GroupRegularSlotForm(
            it.id,
            GroupWeekday.valueOf(it.weekday.name),
            it.startTime,
            it.durationMinutes,
        )
    },
    defaultCapacity,
    defaultConfirmationLeadMinutes,
    defaultGameFeeCents,
    monthlyFeeCents,
    monthlyDueDay,
)

private fun MonthlyChargeDraft.toIOSPersisted() = IOSPersistedMonthlyChargeDraft(
    schemaVersion,
    groupId,
    commandKey,
    month,
    amountBrl,
    dueDate,
    selectedMemberIds,
    reviewed,
)

private fun IOSPersistedMonthlyChargeDraft.toDomain() = MonthlyChargeDraft(
    schemaVersion,
    groupId,
    commandKey,
    month,
    amountBrl,
    dueDate,
    selectedMemberIds,
    reviewed,
)

private fun ExpenseDraft.toIOSPersisted() = IOSPersistedExpenseDraft(
    schemaVersion,
    groupId,
    expenseId,
    etag,
    commandKey,
    form.toIOSPersisted(),
)

private fun IOSPersistedExpenseDraft.toDomain() = ExpenseDraft(
    schemaVersion,
    groupId,
    expenseId,
    etag,
    commandKey,
    form.toDomain(),
)

private fun ExpenseForm.toIOSPersisted() = IOSPersistedExpenseForm(
    description,
    amountBrl,
    expenseDate,
    category?.let { IOSPersistedExpenseCategory.entries[it.ordinal] },
    customCategory,
    notes,
)

private fun IOSPersistedExpenseForm.toDomain() = ExpenseForm(
    description,
    amountBrl,
    expenseDate,
    category?.let { ExpenseCategory.entries[it.ordinal] },
    customCategory,
    notes,
)

private fun GameEditorDraft.toPersisted() = PersistedGameEditorDraft(
    schemaVersion,
    groupId,
    gameId,
    seriesId,
    commandKey,
    version?.value,
    PersistedGameEditorMode.valueOf(mode.name),
    form.toPersisted(),
    scope?.toPersisted(),
)

private fun PersistedGameEditorDraft.toDomain() = GameEditorDraft(
    schemaVersion,
    groupId,
    gameId,
    seriesId,
    commandKey,
    etag?.let(::GameVersionToken),
    GameEditorMode.valueOf(mode.name),
    form.toDomain(),
    scope?.toDomain(),
)

private fun GameEditorForm.toPersisted() = PersistedGameEditorForm(
    title,
    venue?.let { PersistedGameVenue(it.venueId, it.name, it.address, it.court) },
    localDate,
    localTime,
    zoneId,
    startsAt,
    durationMinutes,
    capacity,
    confirmationDeadline,
    gameFeeBrl,
    notes,
    localEndDate,
    slots.map(WeeklySlot::toPersisted),
)

private fun PersistedGameEditorForm.toDomain() = GameEditorForm(
    title,
    venue?.let { GameVenue(it.venueId, it.name, it.address, it.court) },
    localDate,
    localTime,
    zoneId,
    startsAt,
    durationMinutes,
    capacity,
    confirmationDeadline,
    gameFeeBrl,
    notes,
    localEndDate,
    slots.map(PersistedWeeklySlot::toDomain),
)

private fun WeeklySlot.toPersisted() = PersistedWeeklySlot(
    slotKey,
    PersistedWeekday.valueOf(weekday.name.uppercase()),
    localTime,
    durationMinutes,
    PersistedGameVenue(venue.venueId, venue.name, venue.address, venue.court),
    capacity,
    confirmationLeadMinutes,
    gameFeeCents,
    title,
)

private fun PersistedWeeklySlot.toDomain() = WeeklySlot(
    slotKey,
    Weekday.entries.single { it.name.uppercase() == weekday.name },
    localTime,
    durationMinutes,
    GameVenue(venue.venueId, venue.name, venue.address, venue.court),
    capacity,
    confirmationLeadMinutes,
    gameFeeCents,
    title,
)

private fun SeriesBoundaryScope.toPersisted() = when (this) {
    SeriesBoundaryScope.OnlyThis -> PersistedSeriesBoundaryScope.ONLY_THIS
    SeriesBoundaryScope.ThisAndFuture -> PersistedSeriesBoundaryScope.THIS_AND_FUTURE
}

private fun PersistedSeriesBoundaryScope.toDomain() = when (this) {
    PersistedSeriesBoundaryScope.ONLY_THIS -> SeriesBoundaryScope.OnlyThis
    PersistedSeriesBoundaryScope.THIS_AND_FUTURE -> SeriesBoundaryScope.ThisAndFuture
}
