package br.com.saqz.domain

/** Brazil launch capabilities. Re-enabling requires a new release and policy review. */
object StoreLaunchPolicy {
    const val purchases = false
    const val chat = false
    const val receivables = false

    /**
     * Vínculo do grupo do WhatsApp (o Saqz no grupo da galera). Desligado no servidor de
     * produção: a tela só mostraria "Não foi possível carregar o vínculo". "Cobrar no
     * WhatsApp" (link wa.me) é outra coisa e continua valendo.
     */
    const val whatsAppGroupBinding = false
}
