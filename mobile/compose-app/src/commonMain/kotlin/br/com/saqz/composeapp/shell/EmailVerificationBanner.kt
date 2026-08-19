package br.com.saqz.composeapp.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.LifecycleResumeEffect
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeFailureCode
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.composeapp.resources.Res
import br.com.saqz.composeapp.resources.shell_email_dismiss
import br.com.saqz.composeapp.resources.shell_email_resend
import br.com.saqz.composeapp.resources.shell_email_resend_failed
import br.com.saqz.composeapp.resources.shell_email_resent
import br.com.saqz.composeapp.resources.shell_email_unverified
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIconButton
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzToastText
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.time.Clock
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

internal const val SaqzEmailBannerTag = "shell-email-banner"
internal const val SaqzEmailBannerResendTag = "shell-email-banner-resend"
internal const val SaqzEmailBannerDismissTag = "shell-email-banner-dismiss"

/**
 * Trava entre envios: o reenvio existe para quem não recebeu, não para quem quer insistir.
 * Um minuto é o suficiente para o e-mail chegar antes de a ação voltar.
 *
 * O relógio é de parede (e injetável pelo parâmetro `now`) e não monotônico: a trava é
 * salva e precisa continuar valendo depois de a tela ser recriada, e uma marca monotônica
 * não sobrevive a isso — nem ao processo, onde ela viraria um número sem referência.
 */
private const val ResendCooldownMillis = 60_000L

/**
 * A faixa de e-mail não confirmado (VUL-91), o contrapeso da trava que o VUL-76 tirou do
 * backend: quem não confirmou **entra**, e o que sobra é este aviso.
 *
 * Ela informa e não bloqueia — nenhuma aba, nenhum botão, nenhuma rota passa por aqui. Se
 * um dia impedir alguma ação, virou a trava de volta, só que fora do backend, onde ela ao
 * menos era confiável.
 *
 * Não está no export (nenhuma das 11 telas previa este estado), então herda o formato do
 * `SaqzOfflineBanner`: faixa navy no topo do conteúdo, texto curto, uma ação. Sem o
 * spinner de lá — ali há trabalho na fila, aqui há um aviso parado.
 *
 * [auth] e [now] são parâmetros com padrão, e não resoluções escondidas no corpo, para o
 * teste montar a faixa com uma porta falsa e um relógio seu sem subir um grafo inteiro.
 *
 * Pública, ao contrário do resto do shell, porque a recriação da tela só é testável onde o
 * `StateRestorationTester` funciona — no host Android do `:android-app`. No Kotlin/Native,
 * que é onde o `commonTest` deste módulo roda, ele é `TODO()`.
 */
@Composable
fun EmailVerificationBanner(
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    auth: NativeAuthPort = koinInject(),
    now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    // Volta do plano de fundo: a pessoa saiu para o app de e-mail, tocou no link e voltou.
    // O provedor só reflete isso ao recarregar o usuário, então é aqui que se pergunta —
    // e a faixa some sozinha quando a resposta chega ao estado da sessão. A primeira
    // resumida também dispara, o que é de graça: quem já confirmou não tem faixa.
    // As duas lambdas passam por `rememberUpdatedState` porque são lidas de dentro de
    // efeitos que reiniciam por conta própria, e ler o parâmetro direto ali prende a versão
    // da composição em que o efeito começou.
    val refresh by rememberUpdatedState(onRefresh)
    val clock by rememberUpdatedState(now)
    LifecycleResumeEffect(Unit) {
        refresh()
        onPauseOrDispose {}
    }

    // A trava é o **instante** em que ela termina, guardado em `rememberSaveable`, e não um
    // "está esperando" que renasce falso: girar o aparelho não pode devolver o botão, e o
    // que sobra do minuto se mede pelo relógio, não recomeça. `sending` e `failed` seguem
    // em `remember` porque são da volta que está acontecendo — quem os perde na recriação
    // já encontra a trava acesa.
    var lockedUntil by rememberSaveable { mutableLongStateOf(0L) }
    var sending by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val cooling = lockedUntil > 0L

    LaunchedEffect(lockedUntil) {
        if (lockedUntil == 0L) return@LaunchedEffect
        val remaining = lockedUntil - clock()
        // Menor ou igual a zero é a trava que venceu enquanto a tela não existia.
        if (remaining > 0) delay(remaining)
        lockedUntil = 0L
    }

    EmailVerificationBannerContent(
        message = when {
            failed -> stringResource(Res.string.shell_email_resend_failed)
            // Enquanto o envio está no ar a faixa segue no aviso, com o botão ocupado: a
            // confirmação é o que já saiu, não o que está saindo.
            cooling && !sending -> stringResource(Res.string.shell_email_resent)
            else -> stringResource(Res.string.shell_email_unverified)
        },
        loading = sending,
        onDismiss = onDismiss,
        onResend = if (cooling) {
            null
        } else {
            {
                sending = true
                failed = false
                // A trava acende **antes** da chamada, não no retorno dela. Se a tela for
                // recriada com o envio em voo, o callback cai numa composição morta e não
                // teria como acendê-la — e a faixa nova liberaria outro reenvio na hora,
                // que é exatamente o spam que a trava existe para impedir.
                lockedUntil = now() + ResendCooldownMillis
                auth.sendVerification(object : ResultCallback {
                    override fun complete(result: OperationResult) {
                        sending = false
                        when (result) {
                            OperationResult.Success -> Unit
                            // O backend já mandou o primeiro e-mail (cadastro) e recusa
                            // reenvio antes de um minuto. Isso não é falha: é a mesma
                            // trava da faixa. Destravar aqui devolvia o botão na hora e
                            // o toque seguinte também não mandava nada.
                            is OperationResult.Failure -> if (result.code == NativeFailureCode.TOO_MANY_REQUESTS) {
                                failed = false
                            } else {
                                failed = true
                                lockedUntil = 0L
                            }
                        }
                    }
                })
            }
        },
        modifier = modifier,
    )
}

/**
 * O desenho da faixa, sem porta nem estado — é o que a preview e o print montam.
 *
 * Público, ao contrário do resto do shell, porque o print mora no `:android-app`, que é
 * onde o Roborazzi roda (o `:compose-app` não tem source set de host Android).
 */
@Composable
fun EmailVerificationBannerContent(
    message: String,
    onResend: (() -> Unit)?,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    onDismiss: (() -> Unit)? = null,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.grid)
            .background(colors.textPrimary, RoundedCornerShape(metrics.cardRadius))
            // 12 na vertical é o do `SaqzOfflineBanner`; o fim é menor porque o botão traz
            // o próprio respiro dentro da pílula.
            .padding(start = metrics.horizontalPadding, end = metrics.grid, top = metrics.blockGap, bottom = metrics.blockGap)
            .testTag(SaqzEmailBannerTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.grid),
    ) {
        // O envelope no lime que o VUL-47 pôs sobre o navy, nos mesmos 16 do spinner da
        // faixa offline: é o par de cores de lá, sem o giro que aqui não faria sentido.
        SaqzIcon(SaqzIcons.Mail, tint = colors.accent, size = metrics.grid * 2)
        SaqzToastText(message, modifier = Modifier.weight(1f))
        // A ação some enquanto a trava dura, em vez de ficar desabilitada: o desabilitado
        // do design system pinta a pílula de cinza claro, e um retângulo cinza no meio do
        // navy lê como peça quebrada. Sem ela o aviso já diz o que aconteceu.
        //
        // Ghost com rótulo lime porque nenhuma variante serve de fábrica sobre o navy —
        // Secondary desenharia um bloco branco. O tamanho Sm guarda os 44 do alvo de toque.
        if (onResend != null) {
            SaqzButton(
                label = stringResource(Res.string.shell_email_resend),
                onClick = onResend,
                variant = SaqzButtonVariant.Ghost,
                size = SaqzButtonSize.Sm,
                loading = loading,
                contentColor = colors.accent,
                modifier = Modifier.testTag(SaqzEmailBannerResendTag),
            )
        }
        // Dispensar é da pessoa, não do sistema. A faixa não acaba sozinha — o VUL-76 tirou
        // a trava do backend de propósito —, então quem já sabe que deve confirmar precisa
        // de um jeito de tirar o aviso da frente. Quem guarda o dispensado é o shell, e o
        // estado morre com a sessão: o próximo login lembra de novo, e quem confirmou não
        // vê a faixa de jeito nenhum. O rótulo mora neste módulo: no iOS o `stringResource`
        // do Compose não aceita o `Res` de outro source set (receiver type mismatch).
        if (onDismiss != null) {
            SaqzIconButton(
                onClick = onDismiss,
                contentDescription = stringResource(Res.string.shell_email_dismiss),
                modifier = Modifier.testTag(SaqzEmailBannerDismissTag),
            ) {
                SaqzIcon(SaqzIcons.Close, tint = colors.accent, size = metrics.grid * 2)
            }
        }
    }
}

@Preview
@Composable
private fun EmailVerificationBannerPreview() = SaqzTheme {
    EmailVerificationBannerContent(
        message = "Confirme seu e-mail para não perder o acesso à sua conta.",
        onResend = {},
        onDismiss = {},
    )
}

@Preview
@Composable
private fun EmailVerificationBannerSentPreview() = SaqzTheme {
    EmailVerificationBannerContent(
        message = "E-mail reenviado. Confira sua caixa de entrada.",
        onResend = null,
        onDismiss = {},
    )
}
