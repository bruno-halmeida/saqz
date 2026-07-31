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

    /**
     * Libera a reserva por compare-and-delete: só apaga a linha cujo `created_at` seja
     * exatamente [expectedCreatedAt], evitando apagar uma reserva nova (ABA) criada por
     * outro worker entre a inspeção e esta chamada.
     * @return true se esta chamada liberou a reserva; false se outro worker já tratou dela.
     */
    fun release(key: String, expectedCreatedAt: Instant): Boolean
}
