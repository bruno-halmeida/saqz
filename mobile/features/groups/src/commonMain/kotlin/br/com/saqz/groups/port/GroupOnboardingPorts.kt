package br.com.saqz.groups.port

/** O que o onboarding do organizador lembra fora do servidor, por grupo. */
data class GroupOnboardingMemory(
    /** A pessoa já abriu as regras do grupo pela checklist: conta como passo feito. */
    val rulesOpened: Boolean = false,
    /** "Deixar para depois": a checklist fica escondida até este instante (epoch, ms). */
    val snoozedUntilEpochMillis: Long? = null,
)

/**
 * Memória local do onboarding: SharedPreferences no Android, NSUserDefaults no iOS.
 *
 * Só o que não dá para derivar do próprio grupo mora aqui: "regras abertas uma vez", o
 * "deixar para depois" da checklist e as folhas "Como funciona" já vistas. Fica no aparelho
 * de propósito, sem backend; trocar de aparelho pode repetir uma folha, e isso é aceitável.
 * Callback, não `suspend`, como todo port nativo (AGENTS.md §9).
 */
interface GroupOnboardingMemoryPort {
    fun read(groupId: String, done: (GroupOnboardingMemory) -> Unit)

    fun write(groupId: String, memory: GroupOnboardingMemory, done: (Boolean) -> Unit)

    fun isSheetSeen(sheetId: String, done: (Boolean) -> Unit)

    fun markSheetSeen(sheetId: String, done: (Boolean) -> Unit)
}
