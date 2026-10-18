package br.com.saqz.network

import android.os.Looper
import android.os.StrictMode
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * O noturno de 16/10 caiu com `NetworkOnMainThreadException`: a tela fechava com a resposta ainda chegando,
 * o cancelamento rodava na thread principal e o `HttpURLConnection` lia o resto do corpo do socket ali.
 * Com o OkHttp, fechar a resposta só derruba a conexão, sem drenar.
 */
@RunWith(RobolectricTestRunner::class)
class OkHttpCancelOnMainThreadTest {
    @Test
    fun cancelarNaThreadPrincipalComCorpoChegandoNaoLeNemLanca() {
        // No host (JVM) não existe o BlockGuard do Android, então o StrictMode não pega leitura de rede na
        // principal e o `HttpURLConnection` da JVM também não drena como o do aparelho. O teste garante que o
        // cancelamento não lança nem espera o corpo; a prova contra o motor antigo fica para o E2E no aparelho.
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectNetwork().penaltyDeath().build())
        assertEquals(Looper.getMainLooper().thread, Thread.currentThread())

        val firstBytesSent = CountDownLatch(1)
        val server = ServerSocket(0)
        val serverThread = thread(isDaemon = true) {
            runCatching {
                server.accept().use { socket ->
                    val input = socket.getInputStream()
                    val headersEnd = "\r\n\r\n".toByteArray()
                    var matched = 0
                    while (matched < headersEnd.size) {
                        val b = input.read()
                        if (b < 0) return@use
                        matched = if (b.toByte() == headersEnd[matched]) matched + 1 else 0
                    }
                    val out = socket.getOutputStream()
                    out.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 60\r\n\r\n[".toByteArray())
                    out.flush()
                    firstBytesSent.countDown()
                    // 59 bytes restantes, um a cada 200 ms: ~12 s para o corpo terminar.
                    repeat(59) {
                        Thread.sleep(200)
                        out.write(' '.code)
                        out.flush()
                    }
                }
            }
        }

        val client = createPlatformNetworkClient(
            NetworkConfig(NetworkEnvironment.Test, "http://127.0.0.1:${server.localPort}", requestTimeoutMillis = 30_000),
        )
        try {
            runBlocking {
                val request = async(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                    client.execute(HttpMethod.Get, "/lento", String.serializer())
                }
                assertTrue(firstBytesSent.await(5, TimeUnit.SECONDS), "servidor não chegou a mandar o corpo")
                Thread.sleep(300)

                val started = System.nanoTime()
                request.cancel()
                val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)

                assertTrue(elapsedMillis < 1_000, "cancelar na principal levou ${elapsedMillis}ms: leu o corpo restante?")
            }
        } finally {
            server.close()
            serverThread.interrupt()
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX)
        }
    }
}
