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

    // O WhatsApp (vínculo com o grupo da galera e a aba das preferências) não mora aqui: quem
    // decide é o servidor, em `GET /api/whatsapp/availability`. "Cobrar no WhatsApp" (wa.me)
    // é outra coisa e vale sempre.
}
