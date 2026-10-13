package br.com.saqz.webapp.analytics

import br.com.saqz.composeapp.analytics.AnalyticsSink

/** Em dev vai para o console; em prod fica mudo até o Firebase Analytics web entrar no bridge. */
internal class WebAnalyticsSink(private val environment: String) : AnalyticsSink {
    private val verbose = environment != "prod"

    override fun track(name: String, params: Map<String, String>) {
        if (verbose) println("analytics: $name $params")
    }

    override fun setUserId(id: String?) {
        if (verbose) println("analytics: user=$id")
    }

    override fun log(message: String) {
        if (verbose) println("analytics: $message")
    }

    override fun setUserProperty(name: String, value: String?) {
        if (verbose) println("analytics: $name=$value")
    }
}
