package br.com.saqz.access.presentation.forgotpassword

import androidx.lifecycle.viewModelScope
import br.com.saqz.access.domain.passwordreset.PasswordResetError
import br.com.saqz.access.domain.passwordreset.PasswordResetGateway
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.auth_error_network
import br.com.saqz.access.resources.invite_rate_limit
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
            // O campo fica travado durante o envio, mas travar é recomposição e texto já
            // enfileirado chega depois: se o e-mail mudou no meio, esta resposta é de um
            // endereço que não está mais na tela. Navegar com ele mandaria o código para
            // um lugar e mostraria outro. A resposta velha morre aqui — a pessoa reenvia.
            if (state.value.email.trim() != email) return@launch
            when (result) {
                // Aceito é aceito, e não se pergunta de quem: navega com o e-mail digitado.
                is SaqzResult.Success -> emit(ForgotPasswordEffect.CodeRequested(email))
                is SaqzResult.Failure -> update { it.copy(error = result.error.message()) }
            }
        }
    }
}

/**
 * O 429 do backend não é falta de conexão: ele vem do `TooSoon` (pediu código de novo cedo
 * demais) e do limite por IP, e os dois trazem a espera em segundos, que o gateway preserva.
 * Dizer "verifique sua conexão" aí manda a pessoa mexer no wi-fi por um problema de relógio.
 *
 * O resto continua numa mensagem só, e de propósito: nenhuma outra recusa desta chamada
 * distingue conta existente de inexistente, e inventar texto por código de erro só daria
 * material para adivinhar quem tem conta no Saqz.
 */
private fun PasswordResetError.message(): UiText = when {
    this is PasswordResetError.RateLimited && retryAfterSeconds > 0 ->
        UiText.Res(Res.string.invite_rate_limit, listOf(retryAfterSeconds))
    else -> UiText.Res(Res.string.auth_error_network)
}

// ponytail: forma mínima, e é tudo que cabe aqui. Quem valida e-mail de verdade é o
// servidor, e ele responde igual exista a conta ou não — o que esta guarda evita é o
// campo em branco virar um `ResetCode("")` sem destinatário na 1e.
private fun String.looksLikeEmail(): Boolean {
    val at = indexOf('@')
    return at > 0 && at < lastIndex && none(Char::isWhitespace)
}
