package br.com.saqz.access.presentation.forgotpassword

import androidx.compose.runtime.Immutable
import br.com.saqz.designsystem.UiText

@Immutable
data class ForgotPasswordState(
    val email: String = "",
    val isSubmitting: Boolean = false,
    val error: UiText? = null,
)

sealed interface ForgotPasswordIntent {
    data class UpdateEmail(val value: String) : ForgotPasswordIntent

    data object Submit : ForgotPasswordIntent
}

/**
 * O único efeito da 1d, e ele sai **sempre** que o pedido volta aceito — inclusive para
 * e-mail que não existe. O `POST /api/password-reset/request` responde 202 nos dois casos,
 * e engole falha de SMTP, de propósito: é o que impede o app de virar verificador de quem
 * tem conta no Saqz. Ler a resposta para decidir se navega desfaria a proteção inteira.
 */
sealed interface ForgotPasswordEffect {
    data class CodeRequested(val email: String) : ForgotPasswordEffect
}
