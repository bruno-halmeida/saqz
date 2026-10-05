package br.com.saqz.domain

/** Brazil launch capabilities. Re-enabling requires a new release and policy review. */
object StoreLaunchPolicy {
    /** Contratação pelo Asaas dentro do app (e-mail com link de compra, troca de plano com Pix). */
    const val purchases = false

    /**
     * Assinatura pela App Store (StoreKit). Só vale onde existe o port nativo, ou seja, no iOS;
     * desligar aqui volta o iOS ao portão de assinatura sem compra.
     */
    const val appStorePurchases = true

    /**
     * Assinatura pelo Google Play (Play Billing). Só vale onde existe o port nativo, ou seja,
     * no Android; desligar aqui volta o Android ao portão de assinatura sem compra.
     */
    const val googlePlayPurchases = true
    const val chat = false
    const val receivables = false

    /**
     * Vínculo do grupo do WhatsApp (o Saqz no grupo da galera). Desligado no servidor de
     * produção: a tela só mostraria "Não foi possível carregar o vínculo". "Cobrar no
     * WhatsApp" (link wa.me) é outra coisa e continua valendo.
     */
    const val whatsAppGroupBinding = false

    /**
     * Lembrete de cobrança por WhatsApp (DM). O envio por WhatsApp está desligado no servidor
     * de produção, então a aba "WhatsApp" das preferências prometeria algo que não chega.
     */
    const val whatsAppNotifications = false
}
