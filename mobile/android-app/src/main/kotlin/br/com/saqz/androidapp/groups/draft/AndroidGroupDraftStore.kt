package br.com.saqz.androidapp.groups.draft

import android.content.Context
import br.com.saqz.groups.model.*
import br.com.saqz.groups.port.*
import br.com.saqz.groups.presentation.finance.charges.*
import br.com.saqz.groups.presentation.finance.expenses.*
import br.com.saqz.groups.presentation.games.editor.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.*

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
    fun writeSetup(value:GroupSetupDraft)=write(setupRef(value),GroupSetupDraft.serializer(),value)
    fun readSetup(key:GroupDraftKey)=read(setupRef(key),GroupSetupDraft.serializer(),GroupSetupDraft.CURRENT_SCHEMA_VERSION){it.schemaVersion}
    fun writeGame(value:GameEditorDraft)=write(gameRef(value),GameEditorDraft.serializer(),value)
    fun readGame(groupId:String,resourceId:String?)=read(AndroidDraftRef(AndroidDraftType.GAME,groupId,resourceId),GameEditorDraft.serializer(),GameEditorDraft.CURRENT_SCHEMA){it.schemaVersion}
    fun writeMonthly(value:MonthlyChargeDraft)=write(AndroidDraftRef(AndroidDraftType.MONTHLY,value.groupId),MonthlyChargeDraft.serializer(),value)
    fun readMonthly(groupId:String)=read(AndroidDraftRef(AndroidDraftType.MONTHLY,groupId),MonthlyChargeDraft.serializer(),MonthlyChargeDraft.CURRENT_SCHEMA){it.schemaVersion}
    fun writeExpense(value:ExpenseDraft)=write(AndroidDraftRef(AndroidDraftType.EXPENSE,value.groupId),ExpenseDraft.serializer(),value)
    fun readExpense(groupId:String)=read(AndroidDraftRef(AndroidDraftType.EXPENSE,groupId),ExpenseDraft.serializer(),ExpenseDraft.CURRENT_SCHEMA){it.schemaVersion}
    fun clearSetup(key:GroupDraftKey,commandKey:String)=clear(setupRef(key),GroupSetupDraft.serializer(),commandKey){it.commandKey}
    fun clearGame(groupId:String,resourceId:String?,commandKey:String)=clear(AndroidDraftRef(AndroidDraftType.GAME,groupId,resourceId),GameEditorDraft.serializer(),commandKey){it.commandKey}
    fun clearMonthly(groupId:String,commandKey:String)=clear(AndroidDraftRef(AndroidDraftType.MONTHLY,groupId),MonthlyChargeDraft.serializer(),commandKey){it.commandKey}
    fun clearExpense(groupId:String,commandKey:String)=clear(AndroidDraftRef(AndroidDraftType.EXPENSE,groupId),ExpenseDraft.serializer(),commandKey){it.commandKey}
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

internal data class AndroidGroupDraftAdapters(val setup:GroupDraftStorePort,val game:GameDraftStorePort,val monthly:MonthlyChargeDraftStorePort,val expense:ExpenseDraftStorePort,val store:AndroidGroupDraftStore){
    companion object{fun create(context:Context):AndroidGroupDraftAdapters{val store=AndroidGroupDraftStore(SharedPreferencesDraftPreferences(context.applicationContext));return AndroidGroupDraftAdapters(SetupAdapter(store),GameAdapter(store),MonthlyAdapter(store),ExpenseAdapter(store),store)}}
}

private class SetupAdapter(private val store:AndroidGroupDraftStore):GroupDraftStorePort{override fun read(key:GroupDraftKey,done:(GroupDraftReadResult)->Unit){done(when(val result=store.readSetup(key)){is AndroidDraftReadResult.Success->GroupDraftReadResult.Success(result.value);AndroidDraftReadResult.Missing->GroupDraftReadResult.Success(null);AndroidDraftReadResult.UnsupportedSchema->GroupDraftReadResult.Failure(GroupDraftFailure.UNSUPPORTED_SCHEMA);AndroidDraftReadResult.Corrupt->GroupDraftReadResult.Failure(GroupDraftFailure.CORRUPT)})};override fun write(draft:GroupSetupDraft,done:(GroupDraftWriteResult)->Unit)=done(if(store.writeSetup(draft))GroupDraftWriteResult.Success else GroupDraftWriteResult.Failure(GroupDraftFailure.UNAVAILABLE));override fun clear(key:GroupDraftKey,commandKey:String,done:(GroupDraftWriteResult)->Unit)=done(if(store.clearSetup(key,commandKey))GroupDraftWriteResult.Success else GroupDraftWriteResult.Failure(GroupDraftFailure.UNAVAILABLE))}
private class GameAdapter(private val store:AndroidGroupDraftStore):GameDraftStorePort{override fun read(groupId:String,resourceId:String?,done:(GameDraftReadResult)->Unit)=done(when(val result=store.readGame(groupId,resourceId)){is AndroidDraftReadResult.Success->GameDraftReadResult.Success(result.value);AndroidDraftReadResult.Missing->GameDraftReadResult.Success(null);else->GameDraftReadResult.Failure});override fun write(draft:GameEditorDraft,done:(GameDraftWriteResult)->Unit)=done(if(store.writeGame(draft))GameDraftWriteResult.Success else GameDraftWriteResult.Failure);override fun clear(groupId:String,resourceId:String?,commandKey:String,done:(GameDraftWriteResult)->Unit)=done(if(store.clearGame(groupId,resourceId,commandKey))GameDraftWriteResult.Success else GameDraftWriteResult.Failure)}
private class MonthlyAdapter(private val store:AndroidGroupDraftStore):MonthlyChargeDraftStorePort{override fun read(groupId:String,done:(MonthlyDraftReadResult)->Unit)=done(when(val result=store.readMonthly(groupId)){is AndroidDraftReadResult.Success->MonthlyDraftReadResult.Success(result.value);AndroidDraftReadResult.Missing->MonthlyDraftReadResult.Success(null);else->MonthlyDraftReadResult.Failure});override fun write(draft:MonthlyChargeDraft,done:(MonthlyDraftWriteResult)->Unit)=done(if(store.writeMonthly(draft))MonthlyDraftWriteResult.Success else MonthlyDraftWriteResult.Failure);override fun clear(groupId:String,commandKey:String,done:(MonthlyDraftWriteResult)->Unit)=done(if(store.clearMonthly(groupId,commandKey))MonthlyDraftWriteResult.Success else MonthlyDraftWriteResult.Failure)}
private class ExpenseAdapter(private val store:AndroidGroupDraftStore):ExpenseDraftStorePort{override fun read(groupId:String,done:(ExpenseDraftReadResult)->Unit)=done(when(val result=store.readExpense(groupId)){is AndroidDraftReadResult.Success->ExpenseDraftReadResult.Success(result.value);AndroidDraftReadResult.Missing->ExpenseDraftReadResult.Success(null);else->ExpenseDraftReadResult.Failure});override fun write(draft:ExpenseDraft,done:(ExpenseDraftWriteResult)->Unit)=done(if(store.writeExpense(draft))ExpenseDraftWriteResult.Success else ExpenseDraftWriteResult.Failure);override fun clear(groupId:String,expenseId:String?,commandKey:String,done:(ExpenseDraftWriteResult)->Unit)=done(if(store.clearExpense(groupId,commandKey))ExpenseDraftWriteResult.Success else ExpenseDraftWriteResult.Failure)}
