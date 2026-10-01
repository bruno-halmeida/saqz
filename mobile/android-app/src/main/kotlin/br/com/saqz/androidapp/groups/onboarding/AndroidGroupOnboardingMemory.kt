package br.com.saqz.androidapp.groups.onboarding

import android.content.Context
import android.content.SharedPreferences
import br.com.saqz.groups.port.GroupOnboardingMemory
import br.com.saqz.groups.port.GroupOnboardingMemoryPort

/** Memória local do onboarding em SharedPreferences: leitura e escrita síncronas, callback imediato. */
class AndroidGroupOnboardingMemory(context: Context) : GroupOnboardingMemoryPort {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun read(groupId: String, done: (GroupOnboardingMemory) -> Unit) {
        val snooze = preferences.getLong(snoozeKey(groupId), NO_SNOOZE)
        done(
            GroupOnboardingMemory(
                rulesOpened = preferences.getBoolean(rulesKey(groupId), false),
                snoozedUntilEpochMillis = snooze.takeIf { it != NO_SNOOZE },
            ),
        )
    }

    override fun write(groupId: String, memory: GroupOnboardingMemory, done: (Boolean) -> Unit) {
        preferences.edit()
            .putBoolean(rulesKey(groupId), memory.rulesOpened)
            .putLong(snoozeKey(groupId), memory.snoozedUntilEpochMillis ?: NO_SNOOZE)
            .apply()
        done(true)
    }

    override fun isSheetSeen(sheetId: String, done: (Boolean) -> Unit) {
        done(preferences.getBoolean(sheetKey(sheetId), false))
    }

    override fun markSheetSeen(sheetId: String, done: (Boolean) -> Unit) {
        preferences.edit().putBoolean(sheetKey(sheetId), true).apply()
        done(true)
    }

    private fun rulesKey(groupId: String) = "rules:$groupId"
    private fun snoozeKey(groupId: String) = "snooze:$groupId"
    private fun sheetKey(sheetId: String) = "sheet:$sheetId"

    private companion object {
        const val FILE = "saqz-onboarding"
        const val NO_SNOOZE = -1L
    }
}
