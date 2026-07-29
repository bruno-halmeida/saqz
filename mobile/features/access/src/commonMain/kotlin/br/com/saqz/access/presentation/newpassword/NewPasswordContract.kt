package br.com.saqz.access.presentation.newpassword

import androidx.compose.runtime.Immutable
import br.com.saqz.designsystem.UiText

/**
 * O 1g. Três lugares de erro porque o desenho tem três: a linha do campo de cima, a do
 * campo de baixo (que também é onde mora o "Mínimo de 8 caracteres.") e o alerta acima
 * dos campos, para o que o servidor recusa.
 */
@Immutable
data class NewPasswordState(
    val password: String = "",
    val confirmation: String = "",
    val isSaving: Boolean = false,
    val passwordError: UiText? = null,
    val confirmationError: UiText? = null,
    val alert: UiText? = null,
)

sealed interface NewPasswordIntent {
    data class UpdatePassword(val value: String) : NewPasswordIntent

    data class UpdateConfirmation(val value: String) : NewPasswordIntent

    data object Submit : NewPasswordIntent
}

sealed interface NewPasswordEffect {
    /** Senha trocada: segue para o 1h. */
    data object Saved : NewPasswordEffect

    /**
     * O ticket morreu (expirou, já foi usado, ou o servidor o recusou). A pessoa **não**
     * fica presa num erro sem botão: o 1e reaparece pedindo um código novo.
     */
    data object CodeRestartNeeded : NewPasswordEffect
}
