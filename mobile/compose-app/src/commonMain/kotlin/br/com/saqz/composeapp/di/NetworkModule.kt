package br.com.saqz.composeapp.di

import br.com.saqz.access.port.NativeAuthPort
import br.com.saqz.access.port.TokenCallback
import br.com.saqz.access.port.TokenResult as NativeTokenResult
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.IdTokenProvider
import br.com.saqz.network.SessionApi
import br.com.saqz.network.SessionGateway
import br.com.saqz.network.SessionInvalidator
import br.com.saqz.network.TokenResult
import br.com.saqz.network.createPlatformNetworkClient
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.onOptions
import org.koin.dsl.module

internal class DelegatingSessionInvalidator : SessionInvalidator {
    lateinit var delegate: SessionInvalidator

    override fun invalidate() = delegate.invalidate()
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

internal val networkModule = module {
    single { createPlatformNetworkClient(get()) }.onOptions { onClose { client -> client?.close() } }
    single { DelegatingSessionInvalidator() }
    single<SessionInvalidator> { get<DelegatingSessionInvalidator>() }
    single<IdTokenProvider> { NativeTokenProvider(get()) }
    single { AuthenticatedNetworkClient(get(), get(), get()) }
    single<SessionGateway> { SessionApi(get()) }
}
