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
 * Ícones do export oficial (fluxo 10e): traço 1,8 · sem preenchimento · pontas
 * arredondadas · viewBox de 24. Os `d` abaixo são os glifos do próprio app, não
 * de uma biblioteca — o export marca a Lucide como substituta de CDN, e o desenho
 * real está no HTML dos componentes.
 *
 * `<circle>` e `<rect>` do SVG original viram path aqui: o `ImageVector` só fala
 * path, e converter na origem evita um segundo mecanismo de desenho.
 */
fun saqzStrokeIcon(name: String, pathData: String, strokeWidth: Float = 1.8f): ImageVector =
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
        strokeLineWidth = strokeWidth,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()

object SaqzIcons {
    val Home = saqzStrokeIcon("Home", "m3 11 9-8 9 8v9a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1v-9Z")

    val Calendar = saqzStrokeIcon(
        "Calendar",
        "M7 5h10a3 3 0 0 1 3 3v9a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3z" +
            "M8 3v4M16 3v4M4 10h16",
    )

    val Users = saqzStrokeIcon(
        "Users",
        "M6 8a3 3 0 1 0 6 0a3 3 0 1 0-6 0" +
            "M14.6 9a2.4 2.4 0 1 0 4.8 0a2.4 2.4 0 1 0-4.8 0" +
            "M3.5 19a5.5 5.5 0 0 1 11 0M14 18a4 4 0 0 1 7 0",
    )

    val User = saqzStrokeIcon(
        "User",
        "M8 8a4 4 0 1 0 8 0a4 4 0 1 0-8 0M4.5 21a7.5 7.5 0 0 1 15 0",
    )

    val Bell = saqzStrokeIcon(
        "Bell",
        "M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4",
    )

    val Search = saqzStrokeIcon(
        "Search",
        "M4.5 11a6.5 6.5 0 1 0 13 0a6.5 6.5 0 1 0-13 0m11.5 5 4 4",
    )

    val Megaphone = saqzStrokeIcon("Megaphone", "m3 11 12-5v12L3 13v-2ZM7 14v5h3l1-4")

    val Pin = saqzStrokeIcon(
        "Pin",
        "M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0Z" +
            "M9.5 10a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0",
    )

    val Mail = saqzStrokeIcon(
        "Mail",
        "M6 5h12a3 3 0 0 1 3 3v8a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3z" +
            "m1 2 8 6 8-6",
    )

    val Lock = saqzStrokeIcon(
        "Lock",
        "M7 9h10a3 3 0 0 1 3 3v5a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3v-5a3 3 0 0 1 3-3z" +
            "M8 9V7a4 4 0 1 1 8 0v2",
    )

    val Trash = saqzStrokeIcon(
        "Trash",
        "M4.5 7h15M9.5 7V5.5A1.5 1.5 0 0 1 11 4h2a1.5 1.5 0 0 1 1.5 1.5V7" +
            "m-8 0 .8 11.2A2 2 0 0 0 9.3 20h5.4a2 2 0 0 0 2-1.8L17.5 7",
    )

    val Eye = saqzStrokeIcon(
        "Eye",
        "M3 12s3.5-6 9-6 9 6 9 6-3.5 6-9 6-9-6-9-6Z" +
            "M9.4 12a2.6 2.6 0 1 0 5.2 0a2.6 2.6 0 1 0-5.2 0",
    )

    val EyeOff = saqzStrokeIcon(
        "EyeOff",
        "M3 12s3.5-6 9-6 9 6 9 6-3.5 6-9 6-9-6-9-6Z" +
            "M9.4 12a2.6 2.6 0 1 0 5.2 0a2.6 2.6 0 1 0-5.2 0" +
            "m-5.4-8 16 16",
    )

    val ChevronRight = saqzStrokeIcon("ChevronRight", "m9 6 6 6-6 6")

    // O export só desenha o chevron para a direita; a volta é o mesmo traço espelhado.
    val ChevronLeft = saqzStrokeIcon("ChevronLeft", "m15 6-6 6 6 6")

    val Close = saqzStrokeIcon("Close", "M6 6l12 12M18 6 6 18")

    // O export desenha o "+" de ação com traço 2,2 — é o único glifo dele que
    // foge do 1,8. `Minus` não existe no export: o stepper de lá é HTML solto.
    // Pareamos os dois em 2,2 porque no stepper eles ficam lado a lado, e o
    // render mostra os dois com o mesmo peso — 1,8 no menos quebraria o par.
    val Plus = saqzStrokeIcon("Plus", "M12 5v14M5 12h14", strokeWidth = 2.2f)
    val Minus = saqzStrokeIcon("Minus", "M5 12h14", strokeWidth = 2.2f)

    val Check = saqzStrokeIcon("Check", "M20 6 9 17l-5-5")
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SaqzIcon(SaqzIcons.Home)
            SaqzIcon(SaqzIcons.Calendar)
            SaqzIcon(SaqzIcons.Users)
            SaqzIcon(SaqzIcons.User)
            SaqzIcon(SaqzIcons.Bell)
            SaqzIcon(SaqzIcons.Search)
            SaqzIcon(SaqzIcons.Megaphone)
            SaqzIcon(SaqzIcons.Pin)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SaqzIcon(SaqzIcons.Mail)
            SaqzIcon(SaqzIcons.Lock)
            SaqzIcon(SaqzIcons.Trash)
            SaqzIcon(SaqzIcons.Eye)
            SaqzIcon(SaqzIcons.EyeOff)
            SaqzIcon(SaqzIcons.ChevronLeft)
            SaqzIcon(SaqzIcons.ChevronRight)
            SaqzIcon(SaqzIcons.Close)
            SaqzIcon(SaqzIcons.Plus)
        }
    }
}
