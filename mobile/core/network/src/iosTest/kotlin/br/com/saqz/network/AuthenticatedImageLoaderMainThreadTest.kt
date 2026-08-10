package br.com.saqz.network

import coil3.PlatformContext
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertSame

/**
 * Par do `ViewModelScopeThreadTest`: lá o contrato é o dispatcher das ViewModels, aqui o do
 * Coil. O fetcher autenticado pede o token ao `NativeAuthPort`, adapter `@MainActor`, e em
 * Swift 6 chamada fora da main é trap de runtime — a tela de perfil congela no debugger, sem
 * crash e sem erro. O padrão do Coil é `Dispatchers.IO`, então a configuração é o que segura
 * a regra: se ela sumir, quebra aqui, e não no aparelho.
 */
class AuthenticatedImageLoaderMainThreadTest {
    @Test
    fun `o loader autenticado busca na main`() {
        val loader = authenticatedImageLoader(PlatformContext.INSTANCE, client())

        assertSame(Dispatchers.Main, loader.defaults.fetcherCoroutineContext)
    }

    private fun client() = AuthenticatedNetworkClient(
        network = NetworkClient(
            engine = MockEngine { respondOk() },
            config = NetworkConfig(NetworkEnvironment.Test, "https://api.example.test/"),
        ),
        tokenProvider = UnavailableTokenProvider,
        sessionInvalidator = NoOpInvalidator,
    )
}

private object UnavailableTokenProvider : IdTokenProvider {
    override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) =
        completion(TokenResult.Unavailable)
}

private object NoOpInvalidator : SessionInvalidator {
    override fun invalidate() = Unit
}
