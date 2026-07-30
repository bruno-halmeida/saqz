package br.com.saqz.subscriptions.application

import java.time.Instant

/**
 * Reserva atômica de chaves de idempotência para operações que criam recursos no Asaas.
 * Garante que duas corridas com a mesma chave não façam POST duplicado.
 */
interface AsaasIdempotencyStore {
    /** @return true se esta chamada ganhou a reserva e deve chamar o Asaas. */
    fun tryBegin(key: String, now: Instant): Boolean

    fun find(key: String): AsaasIdempotencyReservation?

    fun complete(key: String, resourceId: String)

    /** Libera a reserva se o resource_id ainda for null (falha antes do complete). */
    fun release(key: String)
}
