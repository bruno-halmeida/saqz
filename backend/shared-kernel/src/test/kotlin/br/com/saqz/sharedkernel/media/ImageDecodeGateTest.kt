package br.com.saqz.sharedkernel.media

import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ImageDecodeGateTest {
    @Test
    fun `quem nao consegue vaga no prazo recebe ocupado em vez de esperar para sempre`() {
        val gate = ImageDecodeGate(permits = 1, maxWait = Duration.ofMillis(50))
        val holding = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            executor.submit { gate.withPermit { holding.countDown(); release.await(5, TimeUnit.SECONDS) } }
            holding.await(5, TimeUnit.SECONDS)

            val failure = assertFailsWith<ImageDecodeBusyException> { gate.withPermit { "nunca" } }

            assertEquals(5, failure.retryAfterSeconds)
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `a vaga volta depois do bloco, inclusive quando ele falha`() {
        val gate = ImageDecodeGate(permits = 1, maxWait = Duration.ofMillis(50))

        assertFailsWith<IllegalStateException> { gate.withPermit { error("decodificacao falhou") } }

        assertEquals("ok", gate.withPermit { "ok" })
    }
}
