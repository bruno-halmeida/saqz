package br.com.saqz.androidapp.groups.draft

import android.content.Context
import br.com.saqz.groups.model.*
import br.com.saqz.groups.port.*
import br.com.saqz.groups.presentation.finance.charges.*
import br.com.saqz.groups.presentation.finance.expenses.*
import br.com.saqz.groups.presentation.games.editor.*
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.domain.game.GameVersionToken
import br.com.saqz.groups.domain.game.SeriesBoundaryScope
import br.com.saqz.groups.domain.game.Weekday
import br.com.saqz.groups.domain.game.WeeklySlot
import br.com.saqz.groups.domain.finance.ExpenseCategory
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
private enum class PersistedGameEditorMode {
    ONE_TIME,
    WEEKLY,
}

@Serializable
private enum class PersistedSeriesBoundaryScope {
    ONLY_THIS,
    THIS_AND_FUTURE,
}

@Serializable
private enum class PersistedWeekday {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY,
}

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
private enum class PersistedGroupModality { COURT_VOLLEYBALL, BEACH_VOLLEYBALL, FOOTVOLLEY }

@Serializable
private enum class PersistedGroupComposition { WOMEN, MEN, MIXED }

@Serializable
private enum class PersistedGroupLevel { BEGINNER, INTERMEDIATE, ADVANCED, MIXED_LEVELS, CUSTOM }

@Serializable
private enum class PersistedGroupPlayStyle { SIX_ZERO, FOUR_TWO, FIVE_ONE, CUSTOM }

@Serializable
private enum class PersistedGroupWeekday { MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY }

@Serializable
private enum class PersistedGroupDraftResource { CREATE_GROUP, UPDATE_GROUP }

@Serializable
private enum class PersistedExpenseCategory { VENUE, EQUIPMENT, REFEREE, OTHER }

@Serializable
private data class PersistedGroupVenueForm(
    val id: String? = null,
    val name: String,
    val address: String,
    val court: String? = null,
)

@Serializable
private data class PersistedGroupRegularSlotForm(
    val id: String? = null,
    val weekday: PersistedGroupWeekday,
    val startTime: String,
    val durationMinutes: Int,
)

@Serializable
private data class PersistedGroupSetupForm(
    val name: String = "",
    val modality: PersistedGroupModality? = null,
    val composition: PersistedGroupComposition? = null,
    val description: String? = null,
    val city: String? = null,
    val level: PersistedGroupLevel? = null,
    val customLevel: String? = null,
    val playStyle: PersistedGroupPlayStyle? = null,
    val customPlayStyle: String? = null,
    val defaultVenue: PersistedGroupVenueForm? = null,
    val regularSlots: List<PersistedGroupRegularSlotForm> = emptyList(),
    val defaultCapacity: Int? = null,
    val defaultConfirmationLeadMinutes: Int? = null,
    val defaultGameFeeCents: Long? = null,
    val monthlyFeeCents: Long? = null,
    val monthlyDueDay: Int? = null,
)

@Serializable
private data class PersistedGroupSetupDraft(
    val schemaVersion: Int = GroupSetupDraft.CURRENT_SCHEMA_VERSION,
    val resource: PersistedGroupDraftResource,
    val groupId: String?,
    val groupVersion: Long?,
    val etag: String?,
    val commandKey: String,
    val form: PersistedGroupSetupForm,
)

@Serializable
private data class PersistedMonthlyChargeDraft(
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
private data class PersistedExpenseForm(
    val description: String = "",
    val amountBrl: String = "",
    val expenseDate: String = "",
    val category: PersistedExpenseCategory? = null,
    val customCategory: String = "",
    val notes: String = "",
)

@Serializable
private data class PersistedExpenseDraft(
    val schemaVersion: Int = ExpenseDraft.CURRENT_SCHEMA,
    val groupId: String,
    val expenseId: String? = null,
    val etag: String? = null,
    val commandKey: String,
    val form: PersistedExpenseForm,
)

internal enum class AndroidDraftType{SETUP,GAME,MONTHLY,EXPENSE}
internal data class AndroidDraftRef(val type:AndroidDraftType,val groupId:String?,val resourceId:String?=null)
internal sealed interface AndroidDraftReadResult<out T>{data class Success<T>(val value:T):AndroidDraftReadResult<T>;data object Missing:AndroidDraftReadResult<Nothing>;data object Corrupt:AndroidDraftReadResult<Nothing>;data object UnsupportedSchema:AndroidDraftReadResult<Nothing>}
internal interface AndroidDraftPreferences{fun read(key:String):String?;fun write(key:String,value:String):Boolean;fun remove(key:String):Boolean;fun entries():Map<String,String>}

internal class SharedPreferencesDraftPreferences(context:Context):AndroidDraftPreferences{
    private val preferences=context.getSharedPreferences(NAME,Context.MODE_PRIVATE)
    override fun read(key:String)=preferences.getString(key,null)
    override fun write(key:String,value:String)=preferences.edit().putString(key,value).commit()
    override fun remove(key:String)=preferences.edit().remove(key).commit()
    override fun entries()=preferences.all.mapNotNull{(key,value)->(value as? String)?.let{key to it}}.toMap()
    private companion object{const val NAME="saqz_group_drafts_v1"}
}

internal class AndroidGroupDraftStore(private val preferences:AndroidDraftPreferences){
    private val json=Json{explicitNulls=false;ignoreUnknownKeys=false}
    fun writeSetup(value:GroupSetupDraft)=write(setupRef(value),PersistedGroupSetupDraft.serializer(),value.toPersisted())
    fun readSetup(key:GroupDraftKey)=read(setupRef(key),PersistedGroupSetupDraft.serializer(),GroupSetupDraft.CURRENT_SCHEMA_VERSION){it.schemaVersion}.map(PersistedGroupSetupDraft::toDomain)
    fun writeGame(value:GameEditorDraft)=write(gameRef(value),PersistedGameEditorDraft.serializer(),value.toPersisted())
    fun readGame(groupId:String,resourceId:String?=null)=read(AndroidDraftRef(AndroidDraftType.GAME,groupId,resourceId),PersistedGameEditorDraft.serializer(),GameEditorDraft.CURRENT_SCHEMA){it.schemaVersion}.map(PersistedGameEditorDraft::toDomain)
    fun writeMonthly(value:MonthlyChargeDraft)=write(AndroidDraftRef(AndroidDraftType.MONTHLY,value.groupId),PersistedMonthlyChargeDraft.serializer(),value.toPersisted())
    fun readMonthly(groupId:String)=read(AndroidDraftRef(AndroidDraftType.MONTHLY,groupId),PersistedMonthlyChargeDraft.serializer(),MonthlyChargeDraft.CURRENT_SCHEMA){it.schemaVersion}.map(PersistedMonthlyChargeDraft::toDomain)
    fun writeExpense(value:ExpenseDraft)=write(AndroidDraftRef(AndroidDraftType.EXPENSE,value.groupId,value.expenseId),PersistedExpenseDraft.serializer(),value.toPersisted())
    fun readExpense(groupId:String,resourceId:String?=null)=read(AndroidDraftRef(AndroidDraftType.EXPENSE,groupId,resourceId),PersistedExpenseDraft.serializer(),ExpenseDraft.CURRENT_SCHEMA){it.schemaVersion}.map(PersistedExpenseDraft::toDomain)
    fun clearSetup(key:GroupDraftKey,commandKey:String)=clear(setupRef(key),PersistedGroupSetupDraft.serializer(),commandKey){it.commandKey}
    fun clearGame(groupId:String,resourceId:String?=null,commandKey:String)=clear(AndroidDraftRef(AndroidDraftType.GAME,groupId,resourceId),PersistedGameEditorDraft.serializer(),commandKey){it.commandKey}
    fun clearMonthly(groupId:String,commandKey:String)=clear(AndroidDraftRef(AndroidDraftType.MONTHLY,groupId),PersistedMonthlyChargeDraft.serializer(),commandKey){it.commandKey}
    fun clearExpense(groupId:String,resourceId:String?=null,commandKey:String)=clear(AndroidDraftRef(AndroidDraftType.EXPENSE,groupId,resourceId),PersistedExpenseDraft.serializer(),commandKey){it.commandKey}
    fun clearGroup(groupId:String):Boolean=preferences.entries().keys.filter{key->parseKey(key)?.groupId==groupId}.all(preferences::remove)
    fun clearAll():Boolean=preferences.entries().keys.filter{it.startsWith(PREFIX)}.all(preferences::remove)

    private fun <T> write(ref:AndroidDraftRef,serializer:KSerializer<T>,value:T):Boolean=runCatching{val payload=json.encodeToJsonElement(serializer,value);require(payload.safeKeys());val envelope=buildJsonObject{put("storageVersion",STORAGE_VERSION);put("type",ref.type.name);put("groupId",ref.groupId?.let(::JsonPrimitive)?:JsonNull);put("resourceId",ref.resourceId?.let(::JsonPrimitive)?:JsonNull);put("payload",payload)};preferences.write(ref.key(),json.encodeToString(JsonObject.serializer(),envelope))}.getOrDefault(false)
    private fun <T> read(ref:AndroidDraftRef,serializer:KSerializer<T>,schema:Int,version:(T)->Int):AndroidDraftReadResult<T>{val raw=preferences.read(ref.key())?:return AndroidDraftReadResult.Missing;return runCatching{val envelope=json.parseToJsonElement(raw).jsonObject;require(envelope.keys==setOf("storageVersion","type","groupId","resourceId","payload"));require(envelope.getValue("storageVersion").jsonPrimitive.int==STORAGE_VERSION);require(envelope.getValue("type").jsonPrimitive.content==ref.type.name);require(envelope["groupId"].nullableContent()==ref.groupId);require(envelope["resourceId"].nullableContent()==ref.resourceId);val payload=envelope.getValue("payload");require(payload.safeKeys());json.decodeFromJsonElement(serializer,payload)}.fold(onSuccess={if(version(it)==schema)AndroidDraftReadResult.Success(it)else AndroidDraftReadResult.UnsupportedSchema},onFailure={AndroidDraftReadResult.Corrupt})}
    private fun <T> clear(ref:AndroidDraftRef,serializer:KSerializer<T>,commandKey:String,key:(T)->String):Boolean=when(val value=read(ref,serializer,Int.MIN_VALUE){Int.MIN_VALUE}){is AndroidDraftReadResult.Success->if(key(value.value)==commandKey)preferences.remove(ref.key())else true;AndroidDraftReadResult.Missing->true;else->false}
    private fun JsonElement?.nullableContent()=if(this==null||this is JsonNull)null else jsonPrimitive.content
    private fun JsonElement.safeKeys():Boolean=when(this){is JsonObject->keys.none{it in FORBIDDEN_KEYS}&&values.all{it.safeKeys()};is JsonArray->all{it.safeKeys()};else->true}
    private fun AndroidDraftRef.key()="$PREFIX${type.name}:${groupId?:"_"}:${resourceId?:"_"}"
    private fun parseKey(key:String):AndroidDraftRef?=runCatching{val parts=key.removePrefix(PREFIX).split(':');require(key.startsWith(PREFIX)&&parts.size==3);AndroidDraftRef(AndroidDraftType.valueOf(parts[0]),parts[1].takeUnless{it=="_"},parts[2].takeUnless{it=="_"})}.getOrNull()
    private fun setupRef(value:GroupSetupDraft)=AndroidDraftRef(AndroidDraftType.SETUP,value.groupId,value.resource.name)
    private fun setupRef(key:GroupDraftKey)=AndroidDraftRef(AndroidDraftType.SETUP,key.groupId,key.resource.name)
    private fun gameRef(value:GameEditorDraft)=AndroidDraftRef(AndroidDraftType.GAME,value.groupId,value.gameId)
    private companion object{const val STORAGE_VERSION=1;const val PREFIX="draft:";val FORBIDDEN_KEYS=setOf("bearerToken","inviteCode","photoBytes","photoHandle","paymentCredential","rawServerError")}
}

private fun <T, R> AndroidDraftReadResult<T>.map(transform: (T) -> R): AndroidDraftReadResult<R> = when (this) {
    is AndroidDraftReadResult.Success -> AndroidDraftReadResult.Success(transform(value))
    AndroidDraftReadResult.Missing -> AndroidDraftReadResult.Missing
    AndroidDraftReadResult.Corrupt -> AndroidDraftReadResult.Corrupt
    AndroidDraftReadResult.UnsupportedSchema -> AndroidDraftReadResult.UnsupportedSchema
}

private fun GroupSetupDraft.toPersisted() = PersistedGroupSetupDraft(
    schemaVersion,
    PersistedGroupDraftResource.valueOf(resource.name),
    groupId,
    groupVersion,
    etag,
    commandKey,
    form.toPersisted(),
)

private fun PersistedGroupSetupDraft.toDomain() = GroupSetupDraft(
    schemaVersion,
    GroupDraftResource.valueOf(resource.name),
    groupId,
    groupVersion,
    etag,
    commandKey,
    form.toDomain(),
)

private fun GroupSetupForm.toPersisted() = PersistedGroupSetupForm(
    name,
    modality?.let { PersistedGroupModality.valueOf(it.name) },
    composition?.let { PersistedGroupComposition.valueOf(it.name) },
    description,
    city,
    level?.let { PersistedGroupLevel.valueOf(it.name) },
    customLevel,
    playStyle?.let { PersistedGroupPlayStyle.valueOf(it.name) },
    customPlayStyle,
    defaultVenue?.let { PersistedGroupVenueForm(it.id, it.name, it.address, it.court) },
    regularSlots.map {
        PersistedGroupRegularSlotForm(
            it.id,
            PersistedGroupWeekday.valueOf(it.weekday.name),
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

private fun PersistedGroupSetupForm.toDomain() = GroupSetupForm(
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

private fun MonthlyChargeDraft.toPersisted() = PersistedMonthlyChargeDraft(
    schemaVersion,
    groupId,
    commandKey,
    month,
    amountBrl,
    dueDate,
    selectedMemberIds,
    reviewed,
)

private fun PersistedMonthlyChargeDraft.toDomain() = MonthlyChargeDraft(
    schemaVersion,
    groupId,
    commandKey,
    month,
    amountBrl,
    dueDate,
    selectedMemberIds,
    reviewed,
)

private fun ExpenseDraft.toPersisted() = PersistedExpenseDraft(
    schemaVersion,
    groupId,
    expenseId,
    etag,
    commandKey,
    form.toPersisted(),
)

private fun PersistedExpenseDraft.toDomain() = ExpenseDraft(
    schemaVersion,
    groupId,
    expenseId,
    etag,
    commandKey,
    form.toDomain(),
)

private fun ExpenseForm.toPersisted() = PersistedExpenseForm(
    description,
    amountBrl,
    expenseDate,
    category?.let { PersistedExpenseCategory.entries[it.ordinal] },
    customCategory,
    notes,
)

private fun PersistedExpenseForm.toDomain() = ExpenseForm(
    description,
    amountBrl,
    expenseDate,
    category?.let { ExpenseCategory.entries[it.ordinal] },
    customCategory,
    notes,
)

private fun GameEditorDraft.toPersisted() = PersistedGameEditorDraft(
    schemaVersion = schemaVersion,
    groupId = groupId,
    gameId = gameId,
    seriesId = seriesId,
    commandKey = commandKey,
    etag = version?.value,
    mode = mode.toPersisted(),
    form = form.toPersisted(),
    scope = scope?.toPersisted(),
)

private fun PersistedGameEditorDraft.toDomain() = GameEditorDraft(
    schemaVersion = schemaVersion,
    groupId = groupId,
    gameId = gameId,
    seriesId = seriesId,
    commandKey = commandKey,
    version = etag?.let(::GameVersionToken),
    mode = mode.toDomain(),
    form = form.toDomain(),
    scope = scope?.toDomain(),
)

private fun GameEditorForm.toPersisted() = PersistedGameEditorForm(
    title = title,
    venue = venue?.toPersisted(),
    localDate = localDate,
    localTime = localTime,
    zoneId = zoneId,
    startsAt = startsAt,
    durationMinutes = durationMinutes,
    capacity = capacity,
    confirmationDeadline = confirmationDeadline,
    gameFeeBrl = gameFeeBrl,
    notes = notes,
    localEndDate = localEndDate,
    slots = slots.map(WeeklySlot::toPersisted),
)

private fun PersistedGameEditorForm.toDomain() = GameEditorForm(
    title = title,
    venue = venue?.toDomain(),
    localDate = localDate,
    localTime = localTime,
    zoneId = zoneId,
    startsAt = startsAt,
    durationMinutes = durationMinutes,
    capacity = capacity,
    confirmationDeadline = confirmationDeadline,
    gameFeeBrl = gameFeeBrl,
    notes = notes,
    localEndDate = localEndDate,
    slots = slots.map(PersistedWeeklySlot::toDomain),
)

private fun GameVenue.toPersisted() = PersistedGameVenue(venueId, name, address, court)
private fun PersistedGameVenue.toDomain() = GameVenue(venueId, name, address, court)

private fun WeeklySlot.toPersisted() = PersistedWeeklySlot(
    slotKey,
    weekday.toPersisted(),
    localTime,
    durationMinutes,
    venue.toPersisted(),
    capacity,
    confirmationLeadMinutes,
    gameFeeCents,
    title,
)

private fun PersistedWeeklySlot.toDomain() = WeeklySlot(
    slotKey,
    weekday.toDomain(),
    localTime,
    durationMinutes,
    venue.toDomain(),
    capacity,
    confirmationLeadMinutes,
    gameFeeCents,
    title,
)

private fun GameEditorMode.toPersisted() = PersistedGameEditorMode.valueOf(name)
private fun PersistedGameEditorMode.toDomain() = GameEditorMode.valueOf(name)

private fun SeriesBoundaryScope.toPersisted() = when (this) {
    SeriesBoundaryScope.OnlyThis -> PersistedSeriesBoundaryScope.ONLY_THIS
    SeriesBoundaryScope.ThisAndFuture -> PersistedSeriesBoundaryScope.THIS_AND_FUTURE
}

private fun PersistedSeriesBoundaryScope.toDomain() = when (this) {
    PersistedSeriesBoundaryScope.ONLY_THIS -> SeriesBoundaryScope.OnlyThis
    PersistedSeriesBoundaryScope.THIS_AND_FUTURE -> SeriesBoundaryScope.ThisAndFuture
}

private fun Weekday.toPersisted() = PersistedWeekday.valueOf(name.uppercase())
private fun PersistedWeekday.toDomain() = Weekday.entries.single { it.name.uppercase() == name }

internal data class AndroidGroupDraftAdapters(val setup:GroupDraftStorePort,val game:GameDraftStorePort,val monthly:MonthlyChargeDraftStorePort,val expense:ExpenseDraftStorePort,val store:AndroidGroupDraftStore){
    companion object{fun create(context:Context):AndroidGroupDraftAdapters{val store=AndroidGroupDraftStore(SharedPreferencesDraftPreferences(context.applicationContext));return AndroidGroupDraftAdapters(SetupAdapter(store),GameAdapter(store),MonthlyAdapter(store),ExpenseAdapter(store),store)}}
}

private class SetupAdapter(private val store:AndroidGroupDraftStore):GroupDraftStorePort{override fun read(key:GroupDraftKey,done:(GroupDraftReadResult)->Unit){done(when(val result=store.readSetup(key)){is AndroidDraftReadResult.Success->GroupDraftReadResult.Success(result.value);AndroidDraftReadResult.Missing->GroupDraftReadResult.Success(null);AndroidDraftReadResult.UnsupportedSchema->GroupDraftReadResult.Failure(GroupDraftFailure.UNSUPPORTED_SCHEMA);AndroidDraftReadResult.Corrupt->GroupDraftReadResult.Failure(GroupDraftFailure.CORRUPT)})};override fun write(draft:GroupSetupDraft,done:(GroupDraftWriteResult)->Unit)=done(if(store.writeSetup(draft))GroupDraftWriteResult.Success else GroupDraftWriteResult.Failure(GroupDraftFailure.UNAVAILABLE));override fun clear(key:GroupDraftKey,commandKey:String,done:(GroupDraftWriteResult)->Unit)=done(if(store.clearSetup(key,commandKey))GroupDraftWriteResult.Success else GroupDraftWriteResult.Failure(GroupDraftFailure.UNAVAILABLE))}
private class GameAdapter(private val store:AndroidGroupDraftStore):GameDraftStorePort{override fun read(groupId:String,resourceId:String?,done:(GameDraftReadResult)->Unit)=done(when(val result=store.readGame(groupId,resourceId)){is AndroidDraftReadResult.Success->GameDraftReadResult.Success(result.value);AndroidDraftReadResult.Missing->GameDraftReadResult.Success(null);else->GameDraftReadResult.Failure});override fun write(draft:GameEditorDraft,done:(GameDraftWriteResult)->Unit)=done(if(store.writeGame(draft))GameDraftWriteResult.Success else GameDraftWriteResult.Failure);override fun clear(groupId:String,resourceId:String?,commandKey:String,done:(GameDraftWriteResult)->Unit)=done(if(store.clearGame(groupId,resourceId,commandKey))GameDraftWriteResult.Success else GameDraftWriteResult.Failure)}
private class MonthlyAdapter(private val store:AndroidGroupDraftStore):MonthlyChargeDraftStorePort{override fun read(groupId:String,done:(MonthlyDraftReadResult)->Unit)=done(when(val result=store.readMonthly(groupId)){is AndroidDraftReadResult.Success->MonthlyDraftReadResult.Success(result.value);AndroidDraftReadResult.Missing->MonthlyDraftReadResult.Success(null);else->MonthlyDraftReadResult.Failure});override fun write(draft:MonthlyChargeDraft,done:(MonthlyDraftWriteResult)->Unit)=done(if(store.writeMonthly(draft))MonthlyDraftWriteResult.Success else MonthlyDraftWriteResult.Failure);override fun clear(groupId:String,commandKey:String,done:(MonthlyDraftWriteResult)->Unit)=done(if(store.clearMonthly(groupId,commandKey))MonthlyDraftWriteResult.Success else MonthlyDraftWriteResult.Failure)}
private class ExpenseAdapter(private val store:AndroidGroupDraftStore):ExpenseDraftStorePort{override fun read(groupId:String,expenseId:String?,done:(ExpenseDraftReadResult)->Unit)=done(when(val result=store.readExpense(groupId,expenseId)){is AndroidDraftReadResult.Success->ExpenseDraftReadResult.Success(result.value);AndroidDraftReadResult.Missing->ExpenseDraftReadResult.Success(null);else->ExpenseDraftReadResult.Failure});override fun write(draft:ExpenseDraft,done:(ExpenseDraftWriteResult)->Unit)=done(if(store.writeExpense(draft))ExpenseDraftWriteResult.Success else ExpenseDraftWriteResult.Failure);override fun clear(groupId:String,expenseId:String?,commandKey:String,done:(ExpenseDraftWriteResult)->Unit)=done(if(store.clearExpense(groupId,expenseId,commandKey))ExpenseDraftWriteResult.Success else ExpenseDraftWriteResult.Failure)}
