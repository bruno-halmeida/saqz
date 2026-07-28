package br.com.saqz.access.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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

// `fluxo1` do ui-contract.json, literal — nenhum destes é token do design system, e
// arredondar qualquer um "para a grade de 4" é o erro que o contrato tranca.
private val WaveHeight = 130.dp
private val HorizontalPadding = 26.dp
private val TopPadding = 20.dp
private val SpaciousTopPadding = 36.dp
private val BrandLargeSize = 86.dp
private val BrandLargeRadius = 22.dp
private val BrandLargeSymbol = 64.dp
private val BrandSmallSize = 68.dp
private val BrandSmallRadius = 18.dp
private val BrandSmallSymbol = 50.dp
private val LetteringHeight = 30.dp
private val LetteringGap = 10.dp
private val SubtitleGap = 10.dp

// O export varia o teto do subtítulo entre 290 e 300 conforme a tela; 300 é o mais
// largo, e a quebra real vem do texto, não do teto.
private val SubtitleMaxWidth = 300.dp

private const val WaveViewportWidth = 390f
private const val WaveViewportHeight = 130f
private const val WaveBackLayerAlpha = 0.16f
private const val TitleSize = 28f
private const val SpaciousTitleSize = 29f
private const val TitleLineHeightRatio = 1.12f
private const val TitleTracking = -0.035f

// A Inter é variável (wght 100–900) e o export pede 750. As estáticas empacotadas em
// androidMain param em 700, então hoje isto renderiza como bold — é troca de asset, não
// de token. Está anotado em `_pesoSetecentosECinquenta` no contrato.
private const val TitleWeight = 750

private const val SubtitleSize = 14f
private const val SubtitleLineHeightRatio = 1.5f

/**
 * A coluna que as 11 telas compartilham: fundo branco (não o canvas cinza), a onda atrás,
 * padding lateral de 26, rolagem vertical e `imePadding`.
 *
 * `spacious` é o par 1a/1i: topo de 36 em vez de 20 (e, no cabeçalho, título de 29).
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HorizontalPadding)
                .padding(top = if (spacious) SpaciousTopPadding else TopPadding)
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
            .height(WaveHeight)
            .testTag(AccessChromeTags.Wave),
    ) {
        // M0 62 C 90 14, 230 108, 390 34 L390 130 0 130Z — o azul da marca a 16%.
        drawPath(
            wavePath(startY = 62f, c1x = 90f, c1y = 14f, c2x = 230f, c2y = 108f, endY = 34f),
            color = primary.copy(alpha = WaveBackLayerAlpha),
        )
        // M0 96 C 120 54, 260 128, 390 72 L390 130 0 130Z — o azul sólido, na frente.
        drawPath(
            wavePath(startY = 96f, c1x = 120f, c1y = 54f, c2x = 260f, c2y = 128f, endY = 72f),
            color = primary,
        )
    }
}

private fun DrawScope.wavePath(
    startY: Float,
    c1x: Float,
    c1y: Float,
    c2x: Float,
    c2y: Float,
    endY: Float,
): Path {
    val scaleX = size.width / WaveViewportWidth
    val scaleY = size.height / WaveViewportHeight
    return Path().apply {
        moveTo(0f, startY * scaleY)
        cubicTo(c1x * scaleX, c1y * scaleY, c2x * scaleX, c2y * scaleY, size.width, endY * scaleY)
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
                .size(if (large) BrandLargeSize else BrandSmallSize)
                .background(
                    color = SaqzTheme.colors.primary,
                    shape = RoundedCornerShape(if (large) BrandLargeRadius else BrandSmallRadius),
                )
                .testTag(AccessChromeTags.Brand),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(Res.drawable.saqz_symbol_foreground),
                contentDescription = stringResource(Res.string.access_brand),
                modifier = Modifier.size(if (large) BrandLargeSymbol else BrandSmallSymbol),
            )
        }
        if (large) {
            Spacer(Modifier.height(LetteringGap))
            Image(
                painter = painterResource(Res.drawable.saqz_lettering),
                contentDescription = null,
                modifier = Modifier.height(LetteringHeight).testTag(AccessChromeTags.Lettering),
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
    val titleSize = (if (spacious) SpaciousTitleSize else TitleSize).sp
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = buildAnnotatedString {
                append(title)
                if (title.lastOrNull()?.isWhitespace() == false) append(' ')
                withStyle(SpanStyle(color = colors.primary)) { append(emphasis) }
            },
            style = SaqzTheme.typography.title.copy(
                fontSize = titleSize,
                lineHeight = titleSize * TitleLineHeightRatio,
                fontWeight = FontWeight(TitleWeight),
                letterSpacing = TitleTracking.em,
                textAlign = TextAlign.Center,
            ),
            color = colors.textPrimary,
            modifier = Modifier.testTag(AccessChromeTags.Title),
        )
        if (subtitle != null) {
            Spacer(Modifier.height(SubtitleGap))
            Text(
                text = subtitle,
                style = SaqzTheme.typography.support.copy(
                    fontSize = SubtitleSize.sp,
                    lineHeight = SubtitleSize.sp * SubtitleLineHeightRatio,
                    textAlign = TextAlign.Center,
                ),
                color = colors.textSecondary,
                modifier = Modifier.widthIn(max = SubtitleMaxWidth).testTag(AccessChromeTags.Subtitle),
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
