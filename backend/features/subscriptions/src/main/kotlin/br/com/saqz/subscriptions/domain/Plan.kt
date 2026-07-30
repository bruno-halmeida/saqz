package br.com.saqz.subscriptions.domain

enum class Plan(
    val monthlyPriceCents: Long,
    val annualPriceCents: Long,
    val maxGroups: Int?,
    val maxAthletes: Int?,
    val multiAdmin: Boolean,
    val reports: Boolean,
    val whatsappSla: Boolean,
) {
    TITULAR(
        monthlyPriceCents = 3_990,
        annualPriceCents = 39_900,
        maxGroups = 1,
        maxAthletes = 25,
        multiAdmin = false,
        reports = false,
        whatsappSla = false,
    ),
    ORGANIZADOR(
        monthlyPriceCents = 5_990,
        annualPriceCents = 59_900,
        maxGroups = 3,
        maxAthletes = null,
        multiAdmin = false,
        reports = false,
        whatsappSla = false,
    ),
    ILIMITADO(
        monthlyPriceCents = 8_990,
        annualPriceCents = 89_900,
        maxGroups = null,
        maxAthletes = null,
        multiAdmin = true,
        reports = true,
        whatsappSla = true,
    ),
}
