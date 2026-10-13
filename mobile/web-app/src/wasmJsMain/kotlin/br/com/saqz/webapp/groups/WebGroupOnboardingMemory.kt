package br.com.saqz.webapp.groups

import br.com.saqz.groups.port.GroupOnboardingMemory
import br.com.saqz.groups.port.GroupOnboardingMemoryPort
import br.com.saqz.webapp.interop.WebStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class PersistedOnboardingMemory(val rulesOpened: Boolean = false, val snoozedUntilEpochMillis: Long? = null)

/** Memória do onboarding do organizador no `localStorage` (SharedPreferences no Android, NSUserDefaults no iOS). */
internal class WebGroupOnboardingMemory : GroupOnboardingMemoryPort {
    private val json = Json { ignoreUnknownKeys = true }

    override fun readMemory(groupId: String, done: (GroupOnboardingMemory) -> Unit) {
        val persisted = WebStorage.read(memoryKey(groupId))
            ?.let { runCatching { json.decodeFromString(PersistedOnboardingMemory.serializer(), it) }.getOrNull() }
            ?: PersistedOnboardingMemory()
        done(GroupOnboardingMemory(persisted.rulesOpened, persisted.snoozedUntilEpochMillis))
    }

    override fun writeMemory(groupId: String, memory: GroupOnboardingMemory, done: (Boolean) -> Unit) {
        val value = json.encodeToString(
            PersistedOnboardingMemory.serializer(),
            PersistedOnboardingMemory(memory.rulesOpened, memory.snoozedUntilEpochMillis),
        )
        done(WebStorage.write(memoryKey(groupId), value))
    }

    override fun isSheetSeen(sheetId: String, done: (Boolean) -> Unit) = done(WebStorage.read(sheetKey(sheetId)) != null)

    override fun markSheetSeen(sheetId: String, done: (Boolean) -> Unit) = done(WebStorage.write(sheetKey(sheetId), "1"))

    private fun memoryKey(groupId: String) = "saqz.groups.onboarding.$groupId"
    private fun sheetKey(sheetId: String) = "saqz.groups.sheetSeen.$sheetId"
}
