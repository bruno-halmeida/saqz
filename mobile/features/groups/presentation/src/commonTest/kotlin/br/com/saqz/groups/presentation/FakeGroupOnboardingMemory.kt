package br.com.saqz.groups.presentation

import br.com.saqz.groups.port.GroupOnboardingMemory
import br.com.saqz.groups.port.GroupOnboardingMemoryPort

/** Memória do onboarding em mapa: o que foi escrito volta na leitura seguinte. */
internal class FakeGroupOnboardingMemory(
    initial: Map<String, GroupOnboardingMemory> = emptyMap(),
    seen: Set<String> = emptySet(),
) : GroupOnboardingMemoryPort {
    val memories = initial.toMutableMap()
    val seenSheets = seen.toMutableSet()
    val writes = mutableListOf<Pair<String, GroupOnboardingMemory>>()

    override fun read(groupId: String, done: (GroupOnboardingMemory) -> Unit) {
        done(memories[groupId] ?: GroupOnboardingMemory())
    }

    override fun write(groupId: String, memory: GroupOnboardingMemory, done: (Boolean) -> Unit) {
        memories[groupId] = memory
        writes += groupId to memory
        done(true)
    }

    override fun isSheetSeen(sheetId: String, done: (Boolean) -> Unit) {
        done(sheetId in seenSheets)
    }

    override fun markSheetSeen(sheetId: String, done: (Boolean) -> Unit) {
        seenSheets += sheetId
        done(true)
    }
}
