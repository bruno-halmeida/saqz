package br.com.saqz.core.common.analytics

/**
 * Funil único do app: `screen_view` por rota/aba e `ui_action` por intent de ViewModel, mais o
 * `user_id`. Sem PII em parâmetro — só nomes de classe e ids. O host instala os dois lambdas em
 * `loadSaqzPlatformDependencies`; até lá (testes, previews) tudo é no-op.
 *
 * ponytail: global mutável em vez de injeção nas 42 ViewModels. Upgrade: construtor, no dia em que
 * um teste precisar de sinks distintos por ViewModel.
 */
object SaqzAnalytics {
    var track: (name: String, params: Map<String, String>) -> Unit = { _, _ -> }
    var setUser: (id: String?) -> Unit = { }

    /** Breadcrumb do Crashlytics. Só recebe linha já segura (o `NetworkCallLogger` não loga token nem corpo). */
    var log: (message: String) -> Unit = { }

    var setProperty: (name: String, value: String?) -> Unit = { _, _ -> }

    fun screen(name: String) = track("screen_view", mapOf("screen_name" to name))

    /** Evento curado: fato de sucesso que `ui_action` não distingue (submit não é sucesso). */
    fun event(name: String, vararg params: Pair<String, String>) = track(name, params.toMap())

    /** User property do GA4 (nome até 24 caracteres, valor até 36). `null` apaga. */
    fun userProperty(name: String, value: String?) = setProperty(name, value)

    fun action(screen: String, action: String) {
        // ponytail: intents de digitação (*Changed) são ruído por tecla; filtro por sufixo.
        if (action.substringAfterLast('.').endsWith("Changed")) return
        track("ui_action", mapOf("screen" to screen, "action" to action))
    }

    fun reset() {
        track = { _, _ -> }
        setUser = { }
        log = { }
        setProperty = { _, _ -> }
    }
}

/** "AccessRoute.Login", "SaqzShellDestination", "LoginViewModel": só os segmentos de classe, sem pacote. */
fun analyticsName(value: Any?): String {
    if (value == null) return "null"
    val qualified = value::class.qualifiedName ?: return value::class.simpleName ?: "Unknown"
    return qualified.split('.').takeLastWhile { it.first().isUpperCase() }.joinToString(".")
}
