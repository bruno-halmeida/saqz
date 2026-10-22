package br.com.saqz.groups.application.ratelimit

import java.time.Clock

class UserRateLimitedException(val retryAfterSeconds: Int) : RuntimeException()

/**
 * Janela fixa de um minuto por usuário, em memória; entradas de janelas passadas são descartadas.
 * Serve às rotas que gastam cota de um provedor externo por chamada (Google Places, Uazapi).
 * ponytail: vale para uma instância; com mais de uma, trocar por um contador compartilhado.
 */
open class PerUserRateLimiter(private val clock: Clock, private val limitPerMinute: Int) {
    private class Window(val minute: Long, var count: Int)

    private val windows = HashMap<String, Window>()
    private var lastSweepMinute = Long.MIN_VALUE

    @Synchronized
    fun acquire(userKey: String) {
        val nowMillis = clock.millis()
        val minute = Math.floorDiv(nowMillis, 60_000L)
        if (minute != lastSweepMinute) {
            windows.values.removeIf { it.minute < minute }
            lastSweepMinute = minute
        }
        val window = windows[userKey]?.takeIf { it.minute == minute } ?: Window(minute, 0).also { windows[userKey] = it }
        if (window.count >= limitPerMinute) {
            val retry = (((minute + 1) * 60_000L - nowMillis + 999) / 1000).toInt().coerceIn(1, 60)
            throw UserRateLimitedException(retry)
        }
        window.count++
    }

    internal fun trackedUsers(): Int = synchronized(this) { windows.size }
}
