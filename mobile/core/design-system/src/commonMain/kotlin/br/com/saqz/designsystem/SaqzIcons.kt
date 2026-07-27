package br.com.saqz.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.theme.SaqzTheme

/**
 * Base de ícones do fluxo 10e: traço 1,8 · sem preenchimento · pontas arredondadas ·
 * viewBox de 24, no desenho da Lucide. Um ícone novo é uma linha — o `d` do SVG
 * entra em [saqzStrokeIcon] e nada mais precisa ser gerado.
 */
fun saqzStrokeIcon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(pathData),
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()

object SaqzIcons {
    val ChevronLeft = saqzStrokeIcon("ChevronLeft", "M15 18l-6-6 6-6")
    val ChevronRight = saqzStrokeIcon("ChevronRight", "M9 18l6-6-6-6")
    val Close = saqzStrokeIcon("Close", "M18 6 6 18M6 6l12 12")
    val Check = saqzStrokeIcon("Check", "M20 6 9 17l-5-5")
    val Plus = saqzStrokeIcon("Plus", "M5 12h14M12 5v14")
    val Minus = saqzStrokeIcon("Minus", "M5 12h14")
    val Search = saqzStrokeIcon("Search", "M19 11a8 8 0 1 1-16 0 8 8 0 0 1 16 0M21 21l-4.3-4.3")
    val Bell = saqzStrokeIcon(
        "Bell",
        "M10.27 21a2 2 0 0 0 3.46 0" +
            "M3.26 15.33A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.67C19.41 13.96 18 12.5 18 8A6 6 0 0 0 6 8" +
            "c0 4.5-1.41 5.96-2.74 7.33",
    )
}

@Composable
fun SaqzIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = SaqzTheme.colors.textPrimary,
    size: Dp = 22.dp,
) = Icon(
    imageVector = icon,
    contentDescription = null,
    tint = tint,
    modifier = modifier.size(size),
)

@Preview
@Composable
private fun SaqzIconsPreview() = SaqzTheme {
    SaqzPreviewGrid {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SaqzIcon(SaqzIcons.ChevronLeft)
            SaqzIcon(SaqzIcons.ChevronRight)
            SaqzIcon(SaqzIcons.Close)
            SaqzIcon(SaqzIcons.Check)
            SaqzIcon(SaqzIcons.Plus)
            SaqzIcon(SaqzIcons.Minus)
            SaqzIcon(SaqzIcons.Search)
            SaqzIcon(SaqzIcons.Bell)
        }
    }
}
