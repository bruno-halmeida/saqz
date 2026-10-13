package br.com.saqz.webapp.groups

import br.com.saqz.composeapp.IOSGroupDraftCodec
import br.com.saqz.groups.model.ExpenseDraft
import br.com.saqz.groups.model.GameEditorDraft
import br.com.saqz.groups.model.GroupDraftKey
import br.com.saqz.groups.model.GroupSetupDraft
import br.com.saqz.groups.model.MonthlyChargeDraft
import br.com.saqz.webapp.interop.WebStorage
import kotlin.io.encoding.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/** Como um rascunho se apresentou na leitura; [Corrupt] e [UnsupportedSchema] viram falhas tipadas no port. */
internal sealed interface DraftRead<out T> {
    class Found<T>(val value: T) : DraftRead<T>
    data object Missing : DraftRead<Nothing>
    data object Corrupt : DraftRead<Nothing>
    data object UnsupportedSchema : DraftRead<Nothing>
}

/**
 * Rascunhos de formulário no `localStorage`: o espelho de `IOSGroupDraftStore` (Swift), com o mesmo codec
 * ([IOSGroupDraftCodec]) e o mesmo envelope. Cada rascunho é uma chave por tipo/grupo/recurso e carrega
 * `storageVersion`; o `schemaVersion` do próprio payload decide se ele ainda é legível (senão
 * `UnsupportedSchema`). `clear*` só apaga quando o `commandKey` guardado é o da operação que terminou:
 * um sucesso atrasado de outra tentativa não pode levar embora o que a pessoa já recomeçou a digitar.
 *
 * [readItem] e [writeItem] vêm do `localStorage` e existem para os testes trocarem por um mapa.
 */
@Suppress("TooManyFunctions") // ler/gravar/limpar de cada um dos quatro tipos, como o IOSGroupDraftStore
internal class WebGroupDraftStore(
    private val readItem: (String) -> String? = WebStorage::read,
    private val writeItem: (String, String?) -> Boolean = WebStorage::write,
    private val codec: IOSGroupDraftCodec = IOSGroupDraftCodec(),
) {
    fun writeSetup(value: GroupSetupDraft): Boolean =
        write(DraftRef(DraftType.SETUP, value.groupId, value.resource.name)) { codec.encodeSetup(value) }

    fun readSetup(key: GroupDraftKey): DraftRead<GroupSetupDraft> =
        decode(read(DraftRef(DraftType.SETUP, key.groupId, key.resource.name)), GroupSetupDraft.CURRENT_SCHEMA_VERSION,
            GroupSetupDraft::schemaVersion, codec::decodeSetup)

    fun clearSetup(key: GroupDraftKey, commandKey: String): Boolean =
        clear(DraftRef(DraftType.SETUP, key.groupId, key.resource.name), commandKey)

    fun writeGame(value: GameEditorDraft): Boolean =
        write(DraftRef(DraftType.GAME, value.groupId, value.gameId)) { codec.encodeGame(value) }

    fun readGame(groupId: String, resourceId: String?): DraftRead<GameEditorDraft> =
        decode(read(DraftRef(DraftType.GAME, groupId, resourceId)), GameEditorDraft.CURRENT_SCHEMA,
            GameEditorDraft::schemaVersion, codec::decodeGame)

    fun clearGame(groupId: String, resourceId: String?, commandKey: String): Boolean =
        clear(DraftRef(DraftType.GAME, groupId, resourceId), commandKey)

    fun writeMonthly(value: MonthlyChargeDraft): Boolean =
        write(DraftRef(DraftType.MONTHLY, value.groupId, null)) { codec.encodeMonthly(value) }

    fun readMonthly(groupId: String): DraftRead<MonthlyChargeDraft> =
        decode(read(DraftRef(DraftType.MONTHLY, groupId, null)), MonthlyChargeDraft.CURRENT_SCHEMA,
            MonthlyChargeDraft::schemaVersion, codec::decodeMonthly)

    fun clearMonthly(groupId: String, commandKey: String): Boolean =
        clear(DraftRef(DraftType.MONTHLY, groupId, null), commandKey)

    fun writeExpense(value: ExpenseDraft): Boolean =
        write(DraftRef(DraftType.EXPENSE, value.groupId, value.expenseId)) { codec.encodeExpense(value) }

    fun readExpense(groupId: String, resourceId: String?): DraftRead<ExpenseDraft> =
        decode(read(DraftRef(DraftType.EXPENSE, groupId, resourceId)), ExpenseDraft.CURRENT_SCHEMA,
            ExpenseDraft::schemaVersion, codec::decodeExpense)

    fun clearExpense(groupId: String, resourceId: String?, commandKey: String): Boolean =
        clear(DraftRef(DraftType.EXPENSE, groupId, resourceId), commandKey)

    private fun write(ref: DraftRef, encode: () -> String): Boolean = runCatching {
        val payload = Json.parseToJsonElement(encode()) as? JsonObject
        if (payload == null || !isSafe(payload)) return false
        val envelope = buildJsonObject {
            put("storageVersion", STORAGE_VERSION)
            put("type", ref.type.wire)
            put("groupId", ref.groupId)
            put("resourceId", ref.resourceId)
            put("payload", payload)
        }
        writeItem(ref.storageKey, envelope.toString())
    }.getOrDefault(false)

    private fun read(ref: DraftRef): DraftRead<String> {
        val raw = readItem(ref.storageKey) ?: return DraftRead.Missing
        return runCatching {
            val envelope = Json.parseToJsonElement(raw) as? JsonObject ?: return@runCatching DraftRead.Corrupt
            val payload = envelope["payload"] as? JsonObject ?: return@runCatching DraftRead.Corrupt
            val valid = envelope.keys == ENVELOPE_KEYS &&
                envelope.int("storageVersion") == STORAGE_VERSION &&
                envelope.string("type") == ref.type.wire &&
                envelope.optionalString("groupId") == ref.groupId &&
                envelope.optionalString("resourceId") == ref.resourceId &&
                isSafe(payload)
            if (valid) DraftRead.Found(payload.toString()) else DraftRead.Corrupt
        }.getOrDefault(DraftRead.Corrupt)
    }

    private fun <T> decode(
        result: DraftRead<String>,
        schema: Int,
        version: (T) -> Int,
        operation: (String) -> T,
    ): DraftRead<T> = when (result) {
        is DraftRead.Found -> try {
            val value = operation(result.value)
            if (version(value) == schema) DraftRead.Found(value) else DraftRead.UnsupportedSchema
        } catch (_: Exception) {
            DraftRead.Corrupt
        }
        DraftRead.Missing -> DraftRead.Missing
        DraftRead.Corrupt -> DraftRead.Corrupt
        DraftRead.UnsupportedSchema -> DraftRead.UnsupportedSchema
    }

    /** Sem rascunho, ou de outro `commandKey`, não há o que apagar e conta como sucesso (como no Swift). */
    private fun clear(ref: DraftRef, commandKey: String): Boolean {
        val raw = readItem(ref.storageKey) ?: return true
        val envelope = runCatching { Json.parseToJsonElement(raw) }.getOrNull() ?: return false
        val stored = ((envelope as? JsonObject)?.get("payload") as? JsonObject)?.optionalString("commandKey")
        return if (stored == commandKey) writeItem(ref.storageKey, null) else true
    }

    /** Segredos, mídia e erro cru do servidor nunca vão para o disco. */
    private fun isSafe(element: JsonElement): Boolean = when (element) {
        is JsonObject -> element.keys.none { it in FORBIDDEN_KEYS } && element.values.all(::isSafe)
        is JsonArray -> element.all(::isSafe)
        else -> true
    }

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** `null` explícito e ausência valem o mesmo; um valor que não é string não casa com nada. */
    private fun JsonObject.optionalString(key: String): String? = when (val value = this[key]) {
        null, JsonNull -> null
        is JsonPrimitive -> if (value.isString) value.content else INVALID
        else -> INVALID
    }

    private enum class DraftType(val wire: String) { SETUP("setup"), GAME("game"), MONTHLY("monthly"), EXPENSE("expense") }

    private class DraftRef(val type: DraftType, val groupId: String?, val resourceId: String?) {
        val storageKey: String get() = "$KEY_PREFIX${type.wire}.${encode(groupId)}.${encode(resourceId)}"
    }

    private companion object {
        const val STORAGE_VERSION = 1
        const val KEY_PREFIX = "saqz.groups.draft."

        /** Marcador impossível de ser um id: força o envelope torto a não casar com a referência. */
        const val INVALID = "\u0000invalid"
        val ENVELOPE_KEYS = setOf("storageVersion", "type", "groupId", "resourceId", "payload")
        val FORBIDDEN_KEYS = setOf("bearerToken", "inviteCode", "photoBytes", "photoHandle", "paymentCredential", "rawServerError")
        val KEY_ENCODING = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

        /** Base64 URL-safe sem padding (igual ao Swift); `_` é o ausente e nenhum base64 de texto é só `_`. */
        fun encode(value: String?): String = value?.let { KEY_ENCODING.encode(it.encodeToByteArray()) } ?: "_"
    }
}
