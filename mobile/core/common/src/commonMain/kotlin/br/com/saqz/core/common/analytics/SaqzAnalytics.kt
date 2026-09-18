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

    fun screen(name: String) = track("screen_view", mapOf("screen_name" to name))

    fun action(screen: String, action: String) {
        // ponytail: intents de digitação (*Changed) são ruído por tecla; filtro por sufixo.
        if (action.substringAfterLast('.').endsWith("Changed")) return
        track("ui_action", mapOf("screen" to screen, "action" to action))
    }

    fun reset() {
        track = { _, _ -> }
        setUser = { }
    }
}

/** "AccessRoute.Login", "SaqzShellDestination", "LoginViewModel": só os segmentos de classe, sem pacote. */
fun analyticsName(value: Any?): String {
    if (value == null) return "null"
    val qualified = value::class.qualifiedName ?: return value::class.simpleName ?: "Unknown"
    return qualified.split('.').takeLastWhile { it.first().isUpperCase() }.joinToString(".")
}
