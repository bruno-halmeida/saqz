package br.com.saqz.access.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.access_brand
import br.com.saqz.access.resources.saqz_lettering
import br.com.saqz.access.resources.saqz_symbol_foreground
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * O chrome comum das 11 telas do fluxo 1: a onda do rodapé, a marca e o cabeçalho.
 *
 * Mora em `:features:access` e não no design system porque nada disso aparece fora do
 * fluxo 1 — AD-031 e seção 5 do `mobile/AGENTS.md`: o que não está no fluxo 10 do export
 * nasce dentro da jornada que o usa.
 *
 * Todo número aqui sai do bloco `fluxo1` do `ui-contract.json`
 * (`core/design-system/src/commonTest/composeResources/files/`), que é a cópia versionada
 * das medidas do export. Divergir dele é regressão, não ajuste.
 */
internal object AccessChromeTags {
    const val Wave = "access-wave"
    const val Brand = "access-brand"
    const val Lettering = "access-lettering"
    const val Content = "access-content"
    const val Title = "access-title"
    const val Subtitle = "access-subtitle"
}

/** Uma curva do rodapé, nas coordenadas do viewBox de 390×130 do export. */
internal data class AccessWaveCurve(
    val startY: Float,
    val control1X: Float,
    val control1Y: Float,
    val control2X: Float,
    val control2Y: Float,
    val endY: Float,
)

// SPEC_DEVIATION: `dp` e `sp` crus em `features/*/src/commonMain`, que a seção 5 do
// mobile/AGENTS.md proíbe por convenção.
// Reason: estes números são medidas das telas do fluxo 1, não do inventário do fluxo 10,
// e o AD-031 mantém no `:core:design-system` só o que o fluxo 10 lista — subir isto para
// o `SaqzMetrics` compartilhado seria alimentar o design system com desenho que não é
// dele, que foi exatamente o que o reset apagou. Sem token compartilhado, o literal é a
// única forma de escrever a medida; o que este objeto acrescenta é um lugar único para
// os seis tickets de tela que vêm depois e o `AccessMetricsTest`, que amarra cada valor
// à chave `fluxo1` do ui-contract.json — a mesma amarra que o `SaqzFluxo1ContractTest`
// faz do lado do design system. Mexer no número sem mexer no contrato reprova.
//
// Os literais que o VUL-77 (`SaqzCodeInput`) e o VUL-78 (`SaqzInlineAlert`) já
// mergearam seguem soltos nos arquivos deles; mudá-los agora sairia do escopo deste
// ticket e conflitaria com PRs em voo. Eles migram para cá quando alguém os tocar.
internal object AccessMetrics {
    val waveHeight = 130.dp
    val horizontalPadding = 26.dp
    val topPadding = 20.dp
    val spaciousTopPadding = 36.dp
    val brandLargeSize = 86.dp
    val brandLargeRadius = 22.dp
    val brandLargeSymbol = 64.dp
    val brandSmallSize = 68.dp
    val brandSmallRadius = 18.dp
    val brandSmallSymbol = 50.dp
    val letteringHeight = 30.dp
    val letteringGap = 10.dp

    // O export varia o teto do subtítulo entre 290 e 300 conforme a tela; 300 é o mais
    // largo, e a quebra real vem do texto, não do teto.
    val subtitleMaxWidth = 300.dp

    // O único número deste objeto que o contrato não versiona: o bloco `subtitulo` tem
    // tamanho, entrelinha, cor e largura, mas não o afastamento do título. Vem da
    // descrição do export ("10 abaixo do título") e por isso não entra no teste.
    val subtitleGap = 10.dp

    const val WAVE_VIEWPORT_WIDTH = 390f
    const val WAVE_VIEWPORT_HEIGHT = 130f
    const val WAVE_BACK_LAYER_ALPHA = 0.16f
    val waveBack = AccessWaveCurve(62f, 90f, 14f, 230f, 108f, 34f)
    val waveFront = AccessWaveCurve(96f, 120f, 54f, 260f, 128f, 72f)

    const val TITLE_SIZE = 28f
    const val SPACIOUS_TITLE_SIZE = 29f
    const val TITLE_LINE_HEIGHT_RATIO = 1.12f
    const val TITLE_TRACKING = -0.035f

    // A Inter é variável (wght 100–900) e o export pede 750. As estáticas empacotadas em
    // androidMain param em 700, então hoje isto renderiza como bold — é troca de asset,
    // não de token. Está anotado em `_pesoSetecentosECinquenta` no contrato.
    const val TITLE_WEIGHT = 750

    const val SUBTITLE_SIZE = 14f
    const val SUBTITLE_LINE_HEIGHT_RATIO = 1.5f
}

/**
 * A coluna que as 11 telas compartilham: fundo branco (não o canvas cinza), a onda atrás,
 * padding lateral de 26, rolagem vertical e `imePadding`.
 *
 * `spacious` é o par 1a/1i: topo de 36 em vez de 20 (e, no cabeçalho, título de 29).
 *
 * O topo de 20/36 do export é medido a partir do começo da área útil, não da borda
 * física: a `MainActivity` chama `enableEdgeToEdge()` e o `SaqzNavHost` não repassa
 * inset nenhum, então sem o recuo o voltar de 44 das telas compactas nasceria debaixo da
 * barra de status ou do recorte da câmera. O inset vai na coluna e **não** na onda, que
 * continua sangrando até a borda.
 *
 * O 1h — que centraliza o conteúdo com folga embaixo — monta a própria coluna sobre
 * [AccessWave]; não há slot aqui para uma tela só.
 */
@Composable
fun AccessScaffold(
    modifier: Modifier = Modifier,
    spacious: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().background(SaqzTheme.colors.surface)) {
        AccessWave(Modifier.align(Alignment.BottomCenter))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AccessMetrics.horizontalPadding)
                .padding(
                    top = if (spacious) AccessMetrics.spaciousTopPadding else AccessMetrics.topPadding,
                )
                .testTag(AccessChromeTags.Content),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

/**
 * As duas curvas do rodapé, com os paths do export.
 *
 * O export declara `preserveAspectRatio="none"`: a onda **estica** na largura em vez de
 * manter proporção, senão numa tela larga ela subiria junto. Por isso x e y escalam
 * independentes, do viewBox de 390×130 para a largura real por 130dp.
 */
@Composable
fun AccessWave(modifier: Modifier = Modifier) {
    val primary = SaqzTheme.colors.primary
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(AccessMetrics.waveHeight)
            .testTag(AccessChromeTags.Wave),
    ) {
        // M0 62 C 90 14, 230 108, 390 34 L390 130 0 130Z — o azul da marca a 16%.
        drawPath(
            wavePath(AccessMetrics.waveBack),
            color = primary.copy(alpha = AccessMetrics.WAVE_BACK_LAYER_ALPHA),
        )
        // M0 96 C 120 54, 260 128, 390 72 L390 130 0 130Z — o azul sólido, na frente.
        drawPath(wavePath(AccessMetrics.waveFront), color = primary)
    }
}

private fun DrawScope.wavePath(curve: AccessWaveCurve): Path {
    val scaleX = size.width / AccessMetrics.WAVE_VIEWPORT_WIDTH
    val scaleY = size.height / AccessMetrics.WAVE_VIEWPORT_HEIGHT
    return Path().apply {
        moveTo(0f, curve.startY * scaleY)
        cubicTo(
            curve.control1X * scaleX,
            curve.control1Y * scaleY,
            curve.control2X * scaleX,
            curve.control2Y * scaleY,
            size.width,
            curve.endY * scaleY,
        )
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
}

/**
 * A marca em dois tamanhos, sem meio-termo: `large` é o 86 com lettering das telas 1a e
 * 1i; o padrão é o 68 sem lettering das outras oito. O 1h não tem marca nenhuma.
 *
 * O fundo é o azul **chapado** da marca — o gradiente de três paradas que a tela de login
 * usava não existe no export.
 */
@Composable
fun AccessBrandMark(modifier: Modifier = Modifier, large: Boolean = false) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(if (large) AccessMetrics.brandLargeSize else AccessMetrics.brandSmallSize)
                .background(
                    color = SaqzTheme.colors.primary,
                    shape = RoundedCornerShape(
                        if (large) AccessMetrics.brandLargeRadius else AccessMetrics.brandSmallRadius,
                    ),
                )
                .testTag(AccessChromeTags.Brand),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(Res.drawable.saqz_symbol_foreground),
                contentDescription = stringResource(Res.string.access_brand),
                modifier = Modifier.size(if (large) AccessMetrics.brandLargeSymbol else AccessMetrics.brandSmallSymbol),
            )
        }
        if (large) {
            Spacer(Modifier.height(AccessMetrics.letteringGap))
            Image(
                painter = painterResource(Res.drawable.saqz_lettering),
                contentDescription = null,
                modifier = Modifier.height(AccessMetrics.letteringHeight).testTag(AccessChromeTags.Lettering),
            )
        }
    }
}

/**
 * Título centralizado com a última palavra em azul, e o subtítulo opcional abaixo.
 *
 * A quebra de linha é **conteúdo**, não consequência de largura: quem chama passa o `\n`
 * onde o desenho quebra ("Organize seu grupo.\nJogue" + "junto."). Título terminado em
 * quebra recebe o destaque sem espaço à frente ("Esqueceu a senha?\n" + "Sem stress.").
 *
 * Nas telas de erro (1i, 1j) o subtítulo some e o alerta ocupa o lugar — por isso ele é
 * nulável, e não um texto vazio.
 */
@Composable
fun AccessHeader(
    title: String,
    emphasis: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    spacious: Boolean = false,
) {
    val colors = SaqzTheme.colors
    val titleSize = (if (spacious) AccessMetrics.SPACIOUS_TITLE_SIZE else AccessMetrics.TITLE_SIZE).sp
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = buildAnnotatedString {
                append(title)
                if (title.lastOrNull()?.isWhitespace() == false) append(' ')
                withStyle(SpanStyle(color = colors.primary)) { append(emphasis) }
            },
            style = SaqzTheme.typography.title.copy(
                fontSize = titleSize,
                lineHeight = titleSize * AccessMetrics.TITLE_LINE_HEIGHT_RATIO,
                fontWeight = FontWeight(AccessMetrics.TITLE_WEIGHT),
                letterSpacing = AccessMetrics.TITLE_TRACKING.em,
                textAlign = TextAlign.Center,
            ),
            color = colors.textPrimary,
            modifier = Modifier.testTag(AccessChromeTags.Title),
        )
        if (subtitle != null) {
            Spacer(Modifier.height(AccessMetrics.subtitleGap))
            Text(
                text = subtitle,
                style = SaqzTheme.typography.support.copy(
                    fontSize = AccessMetrics.SUBTITLE_SIZE.sp,
                    lineHeight = AccessMetrics.SUBTITLE_SIZE.sp * AccessMetrics.SUBTITLE_LINE_HEIGHT_RATIO,
                    textAlign = TextAlign.Center,
                ),
                color = colors.textSecondary,
                modifier = Modifier.widthIn(max = AccessMetrics.subtitleMaxWidth).testTag(AccessChromeTags.Subtitle),
            )
        }
    }
}

@Preview(name = "Chrome — marca grande (1a)", widthDp = 390, heightDp = 400)
@Composable
private fun AccessChromeSpaciousPreview() = SaqzTheme {
    AccessScaffold(spacious = true) {
        AccessBrandMark(large = true)
        Spacer(Modifier.height(24.dp))
        AccessHeader(
            title = "Organize seu grupo.\nJogue",
            emphasis = "junto.",
            subtitle = "Entre na sua conta e mantenha sua galera sempre alinhada.",
            spacious = true,
        )
    }
}

@Preview(name = "Chrome — marca pequena (1d)", widthDp = 390, heightDp = 400)
@Composable
private fun AccessChromeCompactPreview() = SaqzTheme {
    AccessScaffold {
        AccessBrandMark()
        Spacer(Modifier.height(24.dp))
        AccessHeader(
            title = "Esqueceu a senha?\n",
            emphasis = "Sem stress.",
            subtitle = "Digite seu e-mail e enviamos um código para você criar uma nova senha.",
        )
    }
}
