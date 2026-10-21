package br.com.saqz.subscriptions.application

import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration

/**
 * Valor dos pedidos do Google Play pela Orders API. Nunca bloqueia a compra: falhou, o pedido fica
 * sem valor e [backfill] (job agendado) tenta de novo depois de [retryAfter]. A receita nunca
 * infere o valor pelo preço do plano.
 */
class GooglePlayOrderAmounts(
    private val api: GooglePlayOrdersApi,
    private val store: GooglePlayOrderAmountStore,
    private val clock: Clock = Clock.systemUTC(),
    private val retryAfter: Duration = Duration.ofHours(1),
) {
    private val log = LoggerFactory.getLogger(GooglePlayOrderAmounts::class.java)

    /** Logo depois de gravar o pedido. Só busca o que ainda não tem valor. */
    fun fetch(orderId: String) {
        if (!store.orderAmountMissing(orderId)) return
        try {
            fetchBatch(listOf(orderId))
        } catch (failure: GooglePlayUnavailableException) {
            log.warn("Valor do pedido {} do Google Play fica para o job: {}", orderId, failure.message)
            store.markOrderAmountChecked(listOf(orderId), clock.instant())
        } catch (failure: GooglePlayOrdersRejectedException) {
            log.warn("Orders API recusou o pedido {}: {}", orderId, failure.message)
            store.markOrderAmountChecked(listOf(orderId), clock.instant())
        }
    }

    /** Um lote de pedidos sem valor. @return quantos ganharam valor. */
    fun backfill(batchSize: Int = DEFAULT_BATCH_SIZE): Int {
        val now = clock.instant()
        val ids = store.ordersWithoutAmount(now.minus(retryAfter), batchSize)
        if (ids.isEmpty()) return 0
        return try {
            fetchBatch(ids)
        } catch (failure: GooglePlayUnavailableException) {
            log.warn("Orders API fora do ar; {} pedidos ficam para a próxima rodada: {}", ids.size, failure.message)
            store.markOrderAmountChecked(ids, now)
            0
        } catch (_: GooglePlayOrdersRejectedException) {
            // Um id ruim derruba o lote inteiro: um a um, os bons ganham valor e o ruim espera a próxima volta.
            ids.sumOf(::fetchOne)
        }
    }

    private fun fetchOne(orderId: String): Int = try {
        fetchBatch(listOf(orderId))
    } catch (failure: RuntimeException) {
        if (failure !is GooglePlayOrdersRejectedException && failure !is GooglePlayUnavailableException) throw failure
        log.warn("Pedido {} do Google Play sem valor: {}", orderId, failure.message)
        store.markOrderAmountChecked(listOf(orderId), clock.instant())
        0
    }

    private fun fetchBatch(ids: List<String>): Int {
        val now = clock.instant()
        val amounts = api.orders(ids)
        amounts.forEach { store.saveOrderAmount(it, now) }
        val missing = ids - amounts.map { it.orderId }.toSet()
        if (missing.isNotEmpty()) store.markOrderAmountChecked(missing, now)
        return amounts.size
    }

    companion object {
        const val DEFAULT_BATCH_SIZE = 100
    }
}
