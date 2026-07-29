package br.com.saqz.access.presentation.resetcode

import androidx.lifecycle.viewModelScope
import br.com.saqz.access.domain.passwordreset.PasswordResetError
import br.com.saqz.access.domain.passwordreset.PasswordResetGateway
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.auth_error_network
import br.com.saqz.access.resources.auth_error_unknown
import br.com.saqz.access.ui.SAQZ_CODE_LENGTH
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.designsystem.UiText
import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/**
 * As telas 1e, 1f e 1k do export.
 *
 * **O contador guarda o instante de expiração, não os segundos restantes.** Sair para o
 * app de e-mail e voltar é o percurso normal desta tela, e é o caso que quebra um
 * contador que decrementa: ele reiniciaria do zero (se morresse com o Composable) ou
 * congelaria no valor de quando saiu (se o tique parasse). Aqui cada tique só deriva a
 * diferença até [deadlineMillis], então qualquer buraco no tempo — Doze, app em segundo
 * plano, tique atrasado — se conserta sozinho no tique seguinte.
 *
 * [elapsedMillis] é o relógio monotônico injetável: em produção é o do sistema, no teste
 * é o do `TestScheduler`, que é o que permite exercer 60 segundos sem esperar 60
 * segundos. Monotônico, e não data de parede, porque só a diferença importa e mudança de
 * fuso ou de relógio não pode encurtar a janela.
 *
 * ponytail: sem `SavedStateHandle`. Morte de processo devolve um contador cheio de 60s,
 * que é conservador — atrasa o reenvio, nunca o antecipa —, e o que ela não pode fazer é
 * liberar o reenvio antes do servidor. Persistir o instante entra quando alguém provar
 * que a espera extra incomoda.
 */
class ResetCodeViewModel(
    email: String,
    private val gateway: PasswordResetGateway,
    private val elapsedMillis: () -> Long = monotonicElapsedMillis(),
) : MviViewModel<ResetCodeState, ResetCodeIntent, ResetCodeEffect>(ResetCodeState(email = email)) {

    /**
     * **Dois baldes, dois contadores.** O servidor limita `request` e `verify` em baldes
     * independentes por IP, e misturá-los tira as duas saídas da pessoa de uma vez: um
     * limite de verificação travaria o pedido de código novo por um motivo que não é o
     * dele. Cada janela conta a sua, e a tela mostra o que couber em cada lugar.
     */
    private val resendWindow = Countdown { update { state -> state.copy(resendSeconds = it) } }
    private val verifyWindow = Countdown { update { state -> state.copy(verifyRetrySeconds = it) } }

    init {
        // Chegar aqui é ter acabado de pedir o código na 1d: a janela já está correndo.
        resendWindow.restart(RESET_CODE_RESEND_SECONDS)
    }

    override fun onIntent(intent: ResetCodeIntent) {
        when (intent) {
            // Mexer num dígito apaga a recusa **do código anterior** — sem isso, o valor
            // novo, ainda não verificado, continuaria vermelho com a contagem antiga
            // ("Restam 2 tentativas" sobre um código que a pessoa acabou de mudar). O
            // `expired` não sai junto: quem expirou foi o código que o servidor mandou, e
            // digitar outro dígito não o ressuscita.
            is ResetCodeIntent.UpdateCode ->
                update { it.copy(code = intent.value, remainingAttempts = null) }
            ResetCodeIntent.Verify -> verify()
            ResetCodeIntent.Resend -> resend()
        }
    }

    private fun verify() {
        val current = state.value
        // Intent inválido volta cedo (AGENTS.md §4): código incompleto não vira requisição,
        // e nem tentativa dentro da janela que o servidor pediu para esperar.
        if (!current.canVerify || current.code.length < SAQZ_CODE_LENGTH) return
        update { it.copy(verifying = true, failure = null) }
        viewModelScope.launch {
            when (val result = gateway.verifyCode(current.email, current.code)) {
                is SaqzResult.Success -> {
                    update { it.copy(verifying = false) }
                    emit(ResetCodeEffect.OpenNewPassword(current.email, result.value.token))
                }
                is SaqzResult.Failure -> onVerifyFailure(result.error)
            }
        }
    }

    private fun onVerifyFailure(error: PasswordResetError) {
        update { current ->
            when (error) {
                // Os dígitos ficam: o mockup do 1k mostra "1 3 5 9" nas caixas vermelhas,
                // e limpar o campo obrigaria a redigitar o que talvez esteja quase certo.
                is PasswordResetError.CodeInvalid ->
                    current.copy(verifying = false, remainingAttempts = error.remainingAttempts, expired = false)
                PasswordResetError.CodeExpired ->
                    current.copy(verifying = false, expired = true, remainingAttempts = null)
                // O teto de tentativas mata o código do mesmo jeito que o relógio, e a
                // saída é a mesma: pedir outro. Por isso desenha como o expirado — o
                // fluxo não tem string própria para o teto.
                PasswordResetError.AttemptLimit ->
                    current.copy(verifying = false, expired = true, remainingAttempts = null)
                is PasswordResetError.RateLimited -> current.copy(verifying = false)
                else -> current.copy(verifying = false, failure = error.message())
            }
        }
        // O servidor mandou esperar para **verificar**. Quem espera é o botão de conferir;
        // o reenvio segue liberado quando estiver, porque o balde dele é outro.
        if (error is PasswordResetError.RateLimited) verifyWindow.restart(error.retryAfterSeconds)
    }

    private fun resend() {
        if (!state.value.canResend) return
        val email = state.value.email
        update { it.copy(resending = true, failure = null) }
        viewModelScope.launch {
            when (val result = gateway.requestCode(email)) {
                is SaqzResult.Success -> {
                    // O código velho morreu junto com o envio novo: as caixas voltam
                    // vazias, como o 1f desenha, e as recusas do anterior somem com ele.
                    update {
                        it.copy(
                            resending = false,
                            resent = true,
                            code = "",
                            remainingAttempts = null,
                            expired = false,
                        )
                    }
                    resendWindow.restart(RESET_CODE_RESEND_SECONDS)
                }
                is SaqzResult.Failure -> onResendFailure(result.error)
            }
        }
    }

    private fun onResendFailure(error: PasswordResetError) {
        if (error is PasswordResetError.RateLimited) {
            // Pedido cedo demais: nada de alerta, o contador já é a explicação.
            update { it.copy(resending = false) }
            resendWindow.restart(error.retryAfterSeconds)
            return
        }
        update { it.copy(resending = false, failure = error.message()) }
    }

    /**
     * Uma janela de espera: guarda o **instante** em que ela acaba e publica quantos
     * segundos faltam a cada tique, nunca o contrário. Ver o KDoc da classe para o porquê.
     */
    private inner class Countdown(private val publish: (Int) -> Unit) {
        private var deadlineMillis = 0L
        private var ticker: Job? = null

        fun restart(seconds: Int) {
            val window = seconds.coerceAtLeast(0)
            deadlineMillis = elapsedMillis() + window * MILLIS_PER_SECOND
            publish(window)
            ticker?.cancel()
            if (window == 0) return
            ticker = viewModelScope.launch {
                while (true) {
                    delay(MILLIS_PER_SECOND)
                    val remaining = remaining()
                    publish(remaining)
                    if (remaining == 0) break
                }
            }
        }

        // Arredonda para cima: um segundo que ainda não terminou é um segundo que a pessoa
        // ainda espera, e é assim que o contador do export mostra 0:59 no primeiro tique.
        private fun remaining(): Int {
            val left = deadlineMillis - elapsedMillis()
            if (left <= 0) return 0
            return ((left + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt()
        }
    }
}

private const val MILLIS_PER_SECOND = 1000L

private fun PasswordResetError.message(): UiText = when (this) {
    is PasswordResetError.DataFailure -> when (error) {
        DataError.Connectivity, DataError.Timeout -> UiText.Res(Res.string.auth_error_network)
        else -> UiText.Res(Res.string.auth_error_unknown)
    }
    else -> UiText.Res(Res.string.auth_error_unknown)
}

/**
 * O relógio de produção. Fica fora da classe porque é o valor padrão do construtor, e
 * cada instância precisa da própria origem.
 */
private fun monotonicElapsedMillis(): () -> Long {
    val start = TimeSource.Monotonic.markNow()
    return { start.elapsedNow().inWholeMilliseconds }
}
