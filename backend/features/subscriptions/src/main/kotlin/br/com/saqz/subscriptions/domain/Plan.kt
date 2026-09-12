package br.com.saqz.subscriptions.domain

enum class Plan(
    val monthlyPriceCents: Long,
    val maxGroups: Int?,
    val maxAthletes: Int?,
    val multiAdmin: Boolean,
    val reports: Boolean,
    val whatsappSla: Boolean,
) {
    TITULAR(
        monthlyPriceCents = 3_990,
        maxGroups = 1,
        maxAthletes = 25,
        multiAdmin = false,
        reports = false,
        whatsappSla = false,
    ),
    ORGANIZADOR(
        monthlyPriceCents = 5_990,
        maxGroups = 3,
        maxAthletes = null,
        multiAdmin = false,
        reports = false,
        whatsappSla = false,
    ),
    ILIMITADO(
        monthlyPriceCents = 8_990,
        maxGroups = null,
        maxAthletes = null,
        multiAdmin = true,
        reports = true,
        whatsappSla = true,
    );

    /** 25% off twelve monthly payments: exactly nine monthly payments, in cents. */
    val annualPriceCents: Long get() = monthlyPriceCents * 9
}
