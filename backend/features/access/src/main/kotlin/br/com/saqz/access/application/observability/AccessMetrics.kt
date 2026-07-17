package br.com.saqz.access.application.observability

private val OPERATIONS = setOf("bootstrap", "http", "invite", "provider")
private val RESULTS = setOf("success", "failure", "rejected", "generated", "expired", "redeemed")
private val STATUSES = setOf("200", "201", "204", "400", "401", "403", "404", "429", "503")

data class AccessMetricEvent(
    val operation: String,
    val result: String,
    val status: String,
) {
    init {
        require(operation in OPERATIONS)
        require(result in RESULTS)
        require(status in STATUSES)
    }
}

fun interface AccessMetrics {
    fun record(event: AccessMetricEvent)

    companion object {
        val NONE = AccessMetrics { }
    }
}
