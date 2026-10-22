package br.com.saqz.sharedkernel.media

import java.time.Duration
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/** O servidor está decodificando o máximo de imagens que cabe na memória; tente de novo em [retryAfterSeconds]. */
class ImageDecodeBusyException(val retryAfterSeconds: Int) : RuntimeException("image decoding at capacity")

/**
 * Porteiro das decodificações de imagem do processo inteiro. Uma foto de 4096 × 4096 px vira
 * ~67 MB de pixels ao abrir, mais a cópia rotacionada e a reduzida; com o pod em 1 GiB e
 * `ExitOnOutOfMemoryError`, meia dúzia de uploads simultâneos derrubava a API. Quem não
 * consegue uma vaga em [maxWait] recebe [ImageDecodeBusyException] em vez de ocupar memória.
 */
class ImageDecodeGate(
    permits: Int = DEFAULT_PERMITS,
    private val maxWait: Duration = DEFAULT_MAX_WAIT,
) {
    init {
        require(permits > 0) { "Image decode gate needs at least one permit" }
    }

    private val permits = Semaphore(permits, true)

    fun <T> withPermit(block: () -> T): T {
        if (!permits.tryAcquire(maxWait.toMillis(), TimeUnit.MILLISECONDS)) {
            throw ImageDecodeBusyException(RETRY_AFTER_SECONDS)
        }
        try {
            return block()
        } finally {
            permits.release()
        }
    }

    companion object {
        const val DEFAULT_PERMITS = 2
        val DEFAULT_MAX_WAIT: Duration = Duration.ofSeconds(10)
        private const val RETRY_AFTER_SECONDS = 5

        /** Um porteiro para o processo: foto de perfil e foto de grupo disputam a mesma memória. */
        val shared: ImageDecodeGate = ImageDecodeGate()
    }
}
