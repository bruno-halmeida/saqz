package br.com.saqz.access.domain.port

/**
 * O que o pop-up de atualização guarda no aparelho: SharedPreferences no Android, NSUserDefaults no iOS.
 *
 * A última mínima recebida (sem rede, ela continua valendo; FLG-007) e as recomendadas já dispensadas.
 * Callback, não `suspend`, como todo port nativo (AGENTS.md §9). Os nomes de método carregam "Update"
 * de propósito: seletor igual a outro port com tipos diferentes faz o Kotlin/Native renomear um deles
 * no header ObjC (ver `GroupOnboardingMemoryPort`).
 */
interface AppUpdateMemoryPort {
    fun readUpdateMinimum(done: (String?) -> Unit)

    /** `null` apaga: o adm tirou a mínima. */
    fun writeUpdateMinimum(value: String?, done: (Boolean) -> Unit)

    fun isRecommendedUpdateDismissed(version: String, done: (Boolean) -> Unit)

    fun markRecommendedUpdateDismissed(version: String, done: (Boolean) -> Unit)
}

/** Memória que some com o processo: para os testes que montam o app inteiro sem aparelho. */
class InMemoryAppUpdateMemory : AppUpdateMemoryPort {
    private var minimum: String? = null
    private val dismissed = mutableSetOf<String>()

    override fun readUpdateMinimum(done: (String?) -> Unit) = done(minimum)

    override fun writeUpdateMinimum(value: String?, done: (Boolean) -> Unit) {
        minimum = value
        done(true)
    }

    override fun isRecommendedUpdateDismissed(version: String, done: (Boolean) -> Unit) = done(version in dismissed)

    override fun markRecommendedUpdateDismissed(version: String, done: (Boolean) -> Unit) {
        dismissed += version
        done(true)
    }
}
