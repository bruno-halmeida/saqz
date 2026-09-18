package br.com.saqz.composeapp.analytics

/** Porta nativa do analytics (Firebase Analytics no Android e no iOS). Ligada ao `SaqzAnalytics` no bootstrap. */
interface AnalyticsSink {
    fun track(name: String, params: Map<String, String>)
    fun setUserId(id: String?)

    /** Breadcrumb (Crashlytics `log`). Recebe só linhas já seguras. */
    fun log(message: String)

    fun setUserProperty(name: String, value: String?)
}
