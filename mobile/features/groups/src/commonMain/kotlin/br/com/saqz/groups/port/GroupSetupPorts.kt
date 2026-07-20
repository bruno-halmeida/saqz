package br.com.saqz.groups.port

import br.com.saqz.groups.model.GroupDraftKey
import br.com.saqz.groups.model.GroupSetupDraft
import br.com.saqz.groups.model.GroupTimeZone
import kotlinx.datetime.TimeZone

sealed interface GroupSystemTimeZoneResult {
    data class Available(val value: GroupTimeZone) : GroupSystemTimeZoneResult
    data object Unavailable : GroupSystemTimeZoneResult
}

fun interface GroupSystemTimeZonePort {
    fun detect(done: (GroupSystemTimeZoneResult) -> Unit)
}

class DefaultGroupSystemTimeZonePort : GroupSystemTimeZonePort {
    override fun detect(done: (GroupSystemTimeZoneResult) -> Unit) {
        val result = runCatching { TimeZone.currentSystemDefault().id }
            .fold(
                onSuccess = { raw ->
                    when (val parsed = GroupTimeZone.parse(raw)) {
                        is GroupTimeZone.ParseResult.Valid -> GroupSystemTimeZoneResult.Available(parsed.value)
                        GroupTimeZone.ParseResult.Invalid -> GroupSystemTimeZoneResult.Unavailable
                    }
                },
                onFailure = { GroupSystemTimeZoneResult.Unavailable },
            )
        done(result)
    }
}

enum class GroupDraftFailure { UNAVAILABLE, CORRUPT, UNSUPPORTED_SCHEMA }

sealed interface GroupDraftReadResult {
    data class Success(val draft: GroupSetupDraft?) : GroupDraftReadResult
    data class Failure(val reason: GroupDraftFailure) : GroupDraftReadResult
}

sealed interface GroupDraftWriteResult {
    data object Success : GroupDraftWriteResult
    data class Failure(val reason: GroupDraftFailure) : GroupDraftWriteResult
}

interface GroupDraftStorePort {
    fun read(key: GroupDraftKey, done: (GroupDraftReadResult) -> Unit)
    fun write(draft: GroupSetupDraft, done: (GroupDraftWriteResult) -> Unit)
    fun clear(key: GroupDraftKey, commandKey: String, done: (GroupDraftWriteResult) -> Unit)
}
