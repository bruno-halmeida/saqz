package br.com.saqz.composeapp

import br.com.saqz.groups.model.GroupSetupDraft
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

class IOSGroupDraftCodec {
    private val json = Json { explicitNulls = false; ignoreUnknownKeys = false }

    fun encodeSetup(value: GroupSetupDraft): String = json.encodeToString(value)
    fun encodeGame(value: GameEditorDraft): String = json.encodeToString(value.toPersisted())
    fun encodeMonthly(value: MonthlyChargeDraft): String = json.encodeToString(value)
    fun encodeExpense(value: ExpenseDraft): String = json.encodeToString(value)

    @Throws(Exception::class)
    fun decodeSetup(value: String): GroupSetupDraft = json.decodeFromString(value)

    @Throws(Exception::class)
    fun decodeGame(value: String): GameEditorDraft = json.decodeFromString<PersistedGameEditorDraft>(value).toDomain()

    @Throws(Exception::class)
    fun decodeMonthly(value: String): MonthlyChargeDraft = json.decodeFromString(value)

    @Throws(Exception::class)
    fun decodeExpense(value: String): ExpenseDraft = json.decodeFromString(value)
}

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
