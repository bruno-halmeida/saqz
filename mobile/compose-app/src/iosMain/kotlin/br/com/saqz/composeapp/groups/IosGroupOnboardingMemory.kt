package br.com.saqz.composeapp.groups

import br.com.saqz.groups.port.GroupOnboardingMemory
import br.com.saqz.groups.port.GroupOnboardingMemoryPort
import platform.Foundation.NSUserDefaults

/** Memória local do onboarding em NSUserDefaults: leitura e escrita síncronas, callback imediato. */
class IosGroupOnboardingMemory(private val defaults: NSUserDefaults) : GroupOnboardingMemoryPort {
    constructor() : this(NSUserDefaults.standardUserDefaults)

    override fun read(groupId: String, done: (GroupOnboardingMemory) -> Unit) {
        val snoozeKey = snoozeKey(groupId)
        val snooze = if (defaults.objectForKey(snoozeKey) != null) defaults.doubleForKey(snoozeKey).toLong() else null
        done(
            GroupOnboardingMemory(
                rulesOpened = defaults.boolForKey(rulesKey(groupId)),
                snoozedUntilEpochMillis = snooze,
            ),
        )
    }

    override fun write(groupId: String, memory: GroupOnboardingMemory, done: (Boolean) -> Unit) {
        defaults.setBool(memory.rulesOpened, forKey = rulesKey(groupId))
        val snooze = memory.snoozedUntilEpochMillis
        if (snooze == null) {
            defaults.removeObjectForKey(snoozeKey(groupId))
        } else {
            defaults.setDouble(snooze.toDouble(), forKey = snoozeKey(groupId))
        }
        done(true)
    }

    override fun isSheetSeen(sheetId: String, done: (Boolean) -> Unit) {
        done(defaults.boolForKey(sheetKey(sheetId)))
    }

    override fun markSheetSeen(sheetId: String, done: (Boolean) -> Unit) {
        defaults.setBool(true, forKey = sheetKey(sheetId))
        done(true)
    }

    private fun rulesKey(groupId: String) = "$PREFIX.rules.$groupId"
    private fun snoozeKey(groupId: String) = "$PREFIX.snooze.$groupId"
    private fun sheetKey(sheetId: String) = "$PREFIX.sheet.$sheetId"

    private companion object {
        const val PREFIX = "saqz.onboarding"
    }
}
