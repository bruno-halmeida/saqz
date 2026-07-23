package br.com.saqz.composeapp.di

import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.TokenCallback
import br.com.saqz.access.domain.port.TokenResult as NativeTokenResult
import br.com.saqz.access.data.session.KtorSessionGateway
import br.com.saqz.access.domain.session.SessionGateway
import br.com.saqz.access.domain.session.SessionInvalidator as AccessSessionInvalidator
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.IdTokenProvider
import br.com.saqz.network.SessionInvalidator as NetworkSessionInvalidator
import br.com.saqz.network.TokenResult
import br.com.saqz.network.createPlatformNetworkClient
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.onOptions
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal class DelegatingSessionInvalidator : AccessSessionInvalidator, NetworkSessionInvalidator {
    var delegate: AccessSessionInvalidator? = null

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
}

internal val accessDataModule = module {
    singleOf(::NativeTokenProvider) { bind<IdTokenProvider>() }
    single<SessionGateway> { KtorSessionGateway(get()) }
}

internal val accessInvalidationModule = module {
    single { DelegatingSessionInvalidator() }
    single<AccessSessionInvalidator> { get<DelegatingSessionInvalidator>() }
    single<NetworkSessionInvalidator> { get<DelegatingSessionInvalidator>() }
}
