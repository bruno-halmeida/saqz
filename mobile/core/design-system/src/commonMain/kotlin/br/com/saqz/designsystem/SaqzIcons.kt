package br.com.saqz.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.theme.SaqzTheme
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.Calendar
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mail
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.Megaphone
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Trash
import com.composables.icons.lucide.User
import com.composables.icons.lucide.Users
import com.composables.icons.lucide.X

/**
 * Mapa de conceito → glifo. O desenho vem da Lucide, que é o que o próprio export
 * indica ("standardizes on Lucide as the closest CDN match"); os paths que moravam
 * aqui eram transcrição dos SVGs inline dos protótipos, não um conjunto de ícones.
 *
 * O que **é** decisão nossa e por isso continua neste arquivo: qual glifo representa
 * cada ideia do Saqz. `Pin` é `map-pin` porque local de jogo é endereço, não alfinete
 * de fixar; `Home` é `house` porque a Lucide renomeou `home` em 1.0.
 */
object SaqzIcons {
    val Home = Lucide.House
    val Calendar = Lucide.Calendar
    val Users = Lucide.Users
    val User = Lucide.User
    val Bell = Lucide.Bell
    val Search = Lucide.Search
    val Megaphone = Lucide.Megaphone
    val Pin = Lucide.MapPin
    val Mail = Lucide.Mail
    val Lock = Lucide.Lock
    val Trash = Lucide.Trash
    val Eye = Lucide.Eye
    val EyeOff = Lucide.EyeOff
    val ChevronRight = Lucide.ChevronRight
    val ChevronLeft = Lucide.ChevronLeft
    val Close = Lucide.X
    val Plus = Lucide.Plus
    val Minus = Lucide.Minus
    val Check = Lucide.Check
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
