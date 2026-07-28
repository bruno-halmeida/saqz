package br.com.saqz.access.presentation.forgotpassword

import androidx.lifecycle.viewModelScope
import br.com.saqz.access.domain.passwordreset.PasswordResetGateway
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.auth_error_network
import br.com.saqz.access.resources.login_error_email_invalid
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.designsystem.UiText
import br.com.saqz.domain.SaqzResult
import kotlinx.coroutines.launch

class ForgotPasswordViewModel(
    private val gateway: PasswordResetGateway,
) : MviViewModel<ForgotPasswordState, ForgotPasswordIntent, ForgotPasswordEffect>(ForgotPasswordState()) {

    override fun onIntent(intent: ForgotPasswordIntent) {
        when (intent) {
            is ForgotPasswordIntent.UpdateEmail -> update { it.copy(email = intent.value, error = null) }
            ForgotPasswordIntent.Submit -> submit()
        }
    }

    private fun submit() {
        val current = state.value
        if (current.isSubmitting) return
        val email = current.email.trim()
        if (!email.looksLikeEmail()) {
            update { it.copy(error = UiText.Res(Res.string.login_error_email_invalid)) }
            return
        }
        update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            val result = gateway.requestCode(email)
            update { it.copy(isSubmitting = false) }
            when (result) {
                // Aceito é aceito, e não se pergunta de quem: navega com o e-mail digitado.
                is SaqzResult.Success -> emit(ForgotPasswordEffect.CodeRequested(email))
                // O que fica na tela é a recusa de **transporte** — a pessoa continua na 1d
                // e tenta de novo. Nenhuma recusa do servidor distingue conta existente de
                // inexistente aqui, então uma mensagem só dá conta de todas.
                is SaqzResult.Failure -> update { it.copy(error = UiText.Res(Res.string.auth_error_network)) }
            }
        }
    }
}

// ponytail: forma mínima, e é tudo que cabe aqui. Quem valida e-mail de verdade é o
// servidor, e ele responde igual exista a conta ou não — o que esta guarda evita é o
// campo em branco virar um `ResetCode("")` sem destinatário na 1e.
private fun String.looksLikeEmail(): Boolean {
    val at = indexOf('@')
    return at > 0 && at < lastIndex && none(Char::isWhitespace)
}
