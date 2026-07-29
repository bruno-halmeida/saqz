package br.com.saqz.access.presentation.newpassword

import androidx.lifecycle.viewModelScope
import br.com.saqz.access.domain.passwordreset.PasswordResetError
import br.com.saqz.access.domain.passwordreset.PasswordResetGateway
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.auth_error_network
import br.com.saqz.access.resources.auth_error_unknown
import br.com.saqz.access.resources.login_error_password
import br.com.saqz.access.resources.register_error_password
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.designsystem.UiText
import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import kotlinx.coroutines.launch

/** O mínimo que o 1b e o 1g anunciam ("Mínimo de 8 caracteres."). */
const val MINIMUM_PASSWORD_LENGTH = 8

/**
 * O 1g. O [token] é o do `PasswordResetTicket` que a 1e trocou pelo código, e chega pela
 * rota — nasce no construtor e não muda enquanto a tela vive.
 *
 * As duas regras do desenho são conferidas **antes** da chamada: senha curta e senha que
 * não confere não gastam viagem ao servidor nem entram no histórico de tentativas dele.
 */
class NewPasswordViewModel(
    private val token: String,
    private val gateway: PasswordResetGateway,
) : MviViewModel<NewPasswordState, NewPasswordIntent, NewPasswordEffect>(NewPasswordState()) {

    override fun onIntent(intent: NewPasswordIntent) {
        when (intent) {
            // Digitar limpa a recusa do campo: a linha vermelha fala do que foi enviado,
            // não do que está sendo escrito agora.
            is NewPasswordIntent.UpdatePassword -> update {
                it.copy(password = intent.value, passwordError = null, alert = null)
            }
            is NewPasswordIntent.UpdateConfirmation -> update {
                it.copy(confirmation = intent.value, confirmationError = null, alert = null)
            }
            NewPasswordIntent.Submit -> submit()
        }
    }

    private fun submit() {
        val current = state.value
        if (current.isSaving) return

        val passwordError = UiText.Res(Res.string.register_error_password)
            .takeIf { current.password.length < MINIMUM_PASSWORD_LENGTH }
        val confirmationError = UiText.Res(Res.string.login_error_password)
            .takeIf { current.password != current.confirmation }
        if (passwordError != null || confirmationError != null) {
            update {
                it.copy(passwordError = passwordError, confirmationError = confirmationError, alert = null)
            }
            return
        }

        update { it.copy(isSaving = true, passwordError = null, confirmationError = null, alert = null) }
        viewModelScope.launch {
            when (val result = gateway.confirm(token, current.password)) {
                is SaqzResult.Success -> {
                    update { it.copy(isSaving = false) }
                    emit(NewPasswordEffect.Saved)
                }
                is SaqzResult.Failure -> onRefusal(result.error)
            }
        }
    }

    private fun onRefusal(error: PasswordResetError) {
        update { it.copy(isSaving = false) }
        when (error) {
            // Todo estado do ticket que não seja "válido" tem a mesma saída: o código de
            // novo. `CodeInvalid`, `CodeExpired` e `AttemptLimit` não deveriam chegar do
            // `confirm`, mas se chegarem dizem a mesma coisa que o `TokenInvalid` — o
            // ticket acabou —, e é a única resposta que não deixa a pessoa sem botão.
            PasswordResetError.TokenInvalid,
            PasswordResetError.CodeExpired,
            PasswordResetError.AttemptLimit,
            is PasswordResetError.CodeInvalid,
            -> emit(NewPasswordEffect.CodeRestartNeeded)

            // A recusa do servidor à senha em si vai para o campo dela, e não para o
            // alerta: é ali que a pessoa vai corrigir.
            is PasswordResetError.Validation -> update {
                it.copy(passwordError = UiText.Res(Res.string.register_error_password))
            }

            is PasswordResetError.RateLimited -> update { it.copy(alert = UNKNOWN) }
            is PasswordResetError.DataFailure -> update { it.copy(alert = error.error.alert()) }
        }
    }
}

// O 1g não tem texto próprio para recusa de servidor — o export não desenha alerta nesta
// tela —, então as duas mensagens genéricas do acesso servem: conexão e "tente de novo".
private val UNKNOWN = UiText.Res(Res.string.auth_error_unknown)

private fun DataError.alert(): UiText = when (this) {
    DataError.Connectivity, DataError.Timeout -> UiText.Res(Res.string.auth_error_network)
    else -> UNKNOWN
}
