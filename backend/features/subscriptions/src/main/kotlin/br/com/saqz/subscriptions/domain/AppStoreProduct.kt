package br.com.saqz.subscriptions.domain

/**
 * Assinaturas cadastradas no App Store Connect, todas no grupo "Saqz". O product ID não pode
 * ser reaproveitado depois de criado na Apple: renomear aqui exige um produto novo lá.
 */
enum class AppStoreProduct(
    val productId: String,
    val plan: Plan,
    val cycle: SubscriptionCycle,
) {
    TITULAR_MENSAL("app.saqz.titular.mensal", Plan.TITULAR, SubscriptionCycle.MONTHLY),
    TITULAR_ANUAL("app.saqz.titular.anual", Plan.TITULAR, SubscriptionCycle.ANNUAL),
    ORGANIZADOR_MENSAL("app.saqz.organizador.mensal", Plan.ORGANIZADOR, SubscriptionCycle.MONTHLY),
    ORGANIZADOR_ANUAL("app.saqz.organizador.anual", Plan.ORGANIZADOR, SubscriptionCycle.ANNUAL),
    ILIMITADO_MENSAL("app.saqz.ilimitado.mensal", Plan.ILIMITADO, SubscriptionCycle.MONTHLY),
    ILIMITADO_ANUAL("app.saqz.ilimitado.anual", Plan.ILIMITADO, SubscriptionCycle.ANNUAL),
    ;

    companion object {
        fun fromProductId(productId: String?): AppStoreProduct? = entries.firstOrNull { it.productId == productId }

        fun of(plan: Plan, cycle: SubscriptionCycle): AppStoreProduct =
            entries.first { it.plan == plan && it.cycle == cycle }
    }
}
