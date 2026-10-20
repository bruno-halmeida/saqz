package br.com.saqz.subscriptions.application

/** Uma linha da receita: mês (America/Sao_Paulo, `AAAA-MM`) × origem × moeda. Valores nulos = a origem não informa. */
data class AdminRevenueRow(
    val month: String,
    /** `SITE`, `APP_STORE` ou `GOOGLE_PLAY`. */
    val source: String,
    /** Nula quando a origem não informa valor (Google Play hoje). */
    val currency: String?,
    val payments: Long,
    val grossCents: Long?,
    /** Só quando todos os pagamentos do grupo têm líquido; senão nulo ("a definir"). */
    val netCents: Long?,
)

/** `real` = pagamentos reais não estornados (a receita); `test` = pagamentos de teste, à parte, nunca somados à receita. */
data class AdminRevenueReport(
    val real: List<AdminRevenueRow>,
    val test: List<AdminRevenueRow>,
)

/** Receita real x teste por mês e origem, a partir dos pagamentos confirmados (view `subscription_payments`). */
fun interface AdminRevenueReader {
    fun monthly(months: Int): AdminRevenueReport
}
