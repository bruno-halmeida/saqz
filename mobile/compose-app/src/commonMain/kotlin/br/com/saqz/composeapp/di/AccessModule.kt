package br.com.saqz.composeapp.di

import br.com.saqz.access.presentation.AuthenticationStateMachine
import br.com.saqz.access.presentation.SessionAccessStateMachine
import br.com.saqz.access.presentation.SessionIntent
import br.com.saqz.access.presentation.forgotpassword.ForgotPasswordViewModel
import br.com.saqz.access.presentation.identitycompletion.IdentityCompletionViewModel
import br.com.saqz.access.presentation.login.LoginViewModel
import br.com.saqz.access.presentation.verification.VerificationViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.onOptions
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * O grafo de apresentação do acesso: as duas máquinas compartilhadas e **toda** ViewModel
 * de `:features:access`.
 *
 * As ViewModels das telas moram aqui e não no `composePresentationModule` desde o VUL-84,
 * que é dono deste arquivo: os sete tickets de tela do fluxo 1 rodam em paralelo e cada um
 * acrescenta uma linha ao bloco abaixo. Um arquivo só, uma lista só, um lugar só para
 * procurar. O `composePresentationModule` fica com o que é do app-shell — o orquestrador e
 * a ViewModel do portão.
 */
internal val accessPresentationModule = module {
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
        .onOptions { onClose { scope -> scope?.cancel() } }
    single {
        SessionAccessStateMachine(get(), get(), get(), get()).also { machine ->
            get<DelegatingSessionInvalidator>().delegate = machine
        }
    }
    single {
        AuthenticationStateMachine(get()) { transition ->
            get<SessionAccessStateMachine>().onIntent(SessionIntent.Accept(transition))
        }
    }

    viewModelOf(::LoginViewModel)
    viewModelOf(::ForgotPasswordViewModel)
    viewModelOf(::IdentityCompletionViewModel)
    // Órfã: sem rota desde o VUL-84, apagada pelo VUL-91 junto com a tela.
    viewModelOf(::VerificationViewModel)
}
