package br.com.saqz.composeapp.di

import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.TokenCallback
import br.com.saqz.access.domain.port.TokenResult as NativeTokenResult
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.IdTokenProvider
import br.com.saqz.network.SessionApi
import br.com.saqz.network.SessionGateway
import br.com.saqz.network.SessionInvalidator
import br.com.saqz.network.TokenResult
import br.com.saqz.network.createPlatformNetworkClient
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.onOptions
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal class DelegatingSessionInvalidator : SessionInvalidator {
    var delegate: SessionInvalidator? = null

    override fun invalidate() {
        delegate?.invalidate()
    }
}

internal class NativeTokenProvider(private val auth: NativeAuthPort) : IdTokenProvider {
    override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) {
        auth.idToken(forceRefresh, object : TokenCallback {
            override fun complete(result: NativeTokenResult) {
                completion(
                    when (result) {
                        is NativeTokenResult.Success -> TokenResult.Available(result.token)
                        is NativeTokenResult.Failure -> TokenResult.Unavailable
                    },
                )
            }
        })
    }
}

internal val coreNetworkModule = module {
    single { createPlatformNetworkClient(get()) }.onOptions { onClose { client -> client?.close() } }
    singleOf(::AuthenticatedNetworkClient)
    singleOf(::SessionApi) { bind<SessionGateway>() }
}

internal val accessDataModule = module {
    singleOf(::NativeTokenProvider) { bind<IdTokenProvider>() }
}

internal val accessInvalidationModule = module {
    single { DelegatingSessionInvalidator() }
    single<SessionInvalidator> { get<DelegatingSessionInvalidator>() }
}
