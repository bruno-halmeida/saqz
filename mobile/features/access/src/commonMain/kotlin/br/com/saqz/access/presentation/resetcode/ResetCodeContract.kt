package br.com.saqz.access.presentation.resetcode

import androidx.compose.runtime.Immutable
import br.com.saqz.designsystem.UiText

/**
 * A janela de reenvio, em segundos, **do servidor** (VUL-80). O número não é escolha de
 * desenho: contador que zera antes do backend liberar o reenvio entrega um 429 que a
 * pessoa não tem como entender. Se a janela do servidor mudar, este número muda junto.
 */
const val RESET_CODE_RESEND_SECONDS = 60

/**
 * Os três estados do export sobre a mesma rota `ResetCode` — 1e (esperando), 1f (acabou
 * de reenviar) e 1k (recusado) — são combinações deste estado só, e não três telas:
 *
 * * 1e é o repouso, com [resendSeconds] correndo;
 * * 1f é [resent] ligado, que troca o texto do contador e apaga o "Entrar ›";
 * * 1k é [remainingAttempts] e [expired] — que o mockup mostra juntos por ser o pior
 *   caso, mas que o `PasswordResetGateway` distingue e a tela desenha separados.
 */
@Immutable
data class ResetCodeState(
    val email: String,
    val code: String = "",
    val verifying: Boolean = false,
    val resending: Boolean = false,
    /** Segundos até o servidor aceitar um novo envio; 0 libera o reenvio. */
    val resendSeconds: Int = RESET_CODE_RESEND_SECONDS,
    /** 1f: o alerta verde está no ar e o rodapé vira "Reenviar novamente em {m:ss}". */
    val resent: Boolean = false,
    /** 1k, linha vermelha. `null` enquanto o servidor não recusou o código. */
    val remainingAttempts: Int? = null,
    /** 1k, alerta âmbar: o código morreu e só resta pedir outro. */
    val expired: Boolean = false,
    /** Recusa sem desenho próprio no export (rede, servidor): alerta de erro. */
    val failure: UiText? = null,
) {
    val busy: Boolean get() = verifying || resending

    val canResend: Boolean get() = resendSeconds <= 0 && !busy

    /**
     * O "Lembrou a senha? Entrar ›" do rodapé cede o lugar assim que um alerta ocupa a
     * tela: nem o 1f nem o 1k o desenham. Só o 1e, que é a tela em repouso, o tem.
     */
    val hasAlert: Boolean get() = resent || expired || failure != null
}

sealed interface ResetCodeIntent {
    data class UpdateCode(val value: String) : ResetCodeIntent

    data object Verify : ResetCodeIntent

    data object Resend : ResetCodeIntent
}

sealed interface ResetCodeEffect {
    /** Código trocado pelo ticket: a 1g assume, e o e-mail vai junto para o voltar. */
    data class OpenNewPassword(val email: String, val token: String) : ResetCodeEffect
}

/** `m:ss`, como o export escreve o contador ("0:42"). */
internal fun formatResendCountdown(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return "${safe / SECONDS_PER_MINUTE}:${(safe % SECONDS_PER_MINUTE).toString().padStart(2, '0')}"
}

private const val SECONDS_PER_MINUTE = 60
