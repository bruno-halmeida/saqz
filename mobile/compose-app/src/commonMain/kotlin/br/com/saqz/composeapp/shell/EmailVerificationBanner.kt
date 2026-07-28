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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.LifecycleResumeEffect
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.composeapp.resources.Res
import br.com.saqz.composeapp.resources.shell_email_resend
import br.com.saqz.composeapp.resources.shell_email_resend_failed
import br.com.saqz.composeapp.resources.shell_email_resent
import br.com.saqz.composeapp.resources.shell_email_unverified
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzToastText
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

internal const val SaqzEmailBannerTag = "shell-email-banner"
internal const val SaqzEmailBannerResendTag = "shell-email-banner-resend"

/**
 * Trava entre envios: o reenvio existe para quem não recebeu, não para quem quer insistir.
 * Um minuto é o suficiente para o e-mail chegar antes de a ação voltar.
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
 * [auth] é parâmetro com padrão do Koin, e não uma resolução escondida no corpo, para o
 * teste montar a faixa com uma porta falsa sem subir um grafo inteiro.
 */
@Composable
internal fun EmailVerificationBanner(
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    auth: NativeAuthPort = koinInject(),
) {
    // Volta do plano de fundo: a pessoa saiu para o app de e-mail, tocou no link e voltou.
    // O provedor só reflete isso ao recarregar o usuário, então é aqui que se pergunta —
    // e a faixa some sozinha quando a resposta chega ao estado da sessão. A primeira
    // resumida também dispara, o que é de graça: quem já confirmou não tem faixa.
    LifecycleResumeEffect(onRefresh) {
        onRefresh()
        onPauseOrDispose {}
    }

    // `rememberSaveable` na trava, `remember` no resto: o envio em voo morre com a
    // recomposição de qualquer jeito, mas a trava é o que impede o segundo toque — girar
    // o aparelho não pode devolver o botão. Recriar reinicia o minuto do zero, o que erra
    // para o lado seguro.
    var cooling by rememberSaveable { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(cooling) {
        if (!cooling) return@LaunchedEffect
        delay(ResendCooldownMillis)
        cooling = false
    }

    EmailVerificationBannerContent(
        message = when {
            failed -> stringResource(Res.string.shell_email_resend_failed)
            cooling -> stringResource(Res.string.shell_email_resent)
            else -> stringResource(Res.string.shell_email_unverified)
        },
        loading = sending,
        onResend = if (cooling) {
            null
        } else {
            {
                sending = true
                failed = false
                auth.sendVerification(object : ResultCallback {
                    override fun complete(result: OperationResult) {
                        sending = false
                        if (result is OperationResult.Success) cooling = true else failed = true
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
    }
}

@Preview
@Composable
private fun EmailVerificationBannerPreview() = SaqzTheme {
    EmailVerificationBannerContent(
        message = "Confirme seu e-mail para não perder o acesso à sua conta.",
        onResend = {},
    )
}

@Preview
@Composable
private fun EmailVerificationBannerSentPreview() = SaqzTheme {
    EmailVerificationBannerContent(
        message = "E-mail reenviado. Confira sua caixa de entrada.",
        onResend = null,
    )
}
