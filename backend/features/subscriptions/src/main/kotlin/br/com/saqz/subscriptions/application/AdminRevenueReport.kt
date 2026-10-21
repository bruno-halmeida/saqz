package br.com.saqz.subscriptions.application

import java.util.UUID

/** Uma linha da receita: mês (America/Sao_Paulo, `AAAA-MM`) × origem × moeda. Valores nulos = a origem não informa. */
data class AdminRevenueRow(
    val month: String,
    /** `SITE`, `APP_STORE` ou `GOOGLE_PLAY`. */
    val source: String,
    /** Nula quando a origem não informa valor (pedido do Google Play ainda sem valor da Orders API). */
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
    val courtesy: List<AdminRevenueCourtesyRow> = emptyList(),
)

/**
 * Cortesia no mês (America/Sao_Paulo, `AAAA-MM`): cobrado é sempre zero; `cededCents` é o valor de tabela (preço mensal do plano,
 * mês cheio) das cortesias ativas em algum momento do mês. Nunca entra em `real`, no bruto, no líquido nem no total do panorama.
 */
data class AdminRevenueCourtesyRow(
    val month: String,
    val active: Long,
    val cededCents: Long,
)

/**
 * Receita real por mês × origem do cliente × plataforma × moeda. Origem = o primeiro cupom da pessoa (view `customer_acquisition`);
 * nulos em `couponType`/`couponId` = "Sem cupom" (nunca usou cupom, ou pagou antes do primeiro uso).
 */
data class AdminRevenueOriginRow(
    val month: String,
    /** `TRIAL` ou `DISCOUNT`; nulo = sem cupom. */
    val couponType: String?,
    val couponId: UUID?,
    val couponCode: String?,
    val campaign: String?,
    /** `SITE`, `APP_STORE` ou `GOOGLE_PLAY`. */
    val source: String,
    val currency: String?,
    val payments: Long,
    /** Nulo quando nenhum pagamento do grupo tem valor. */
    val grossCents: Long?,
    /** Pagamentos do grupo sem valor (loja que ainda não informou). */
    val paymentsWithoutValue: Long,
)

/** Receita real x teste por mês e origem, a partir dos pagamentos confirmados (view `subscription_payments`). */
interface AdminRevenueReader {
    fun monthly(months: Int): AdminRevenueReport

    /** Só pagamentos reais e não estornados, como `real` em [monthly]. */
    fun byOrigin(months: Int): List<AdminRevenueOriginRow>
}
