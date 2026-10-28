package br.com.saqz.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.saqz.designsystem.theme.SaqzTheme

/**
 * Etiqueta sobre foto (lista de grupos, GER-003): pílula branca a 70% com o texto sólido, sem sombra; a foto aparece por
 * trás. [dot] põe o ponto da cor do estado antes do texto ("Você vai", "Na espera"); sem ponto é o selo pequeno ("Admin").
 *
 * SPEC_DEVIATION: componente fora do export oficial.
 * Reason: lista de grupos redesenhada e aprovada pelo dono em 2026-10-28 (DEB-145).
 */
@Composable
fun SaqzPhotoTag(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    dot: Color? = null,
) {
    val compact = dot == null
    Row(
        modifier = modifier
            .background(SaqzTheme.colors.surface.copy(alpha = PHOTO_TAG_ALPHA), CircleShape)
            .then(if (compact) Modifier else Modifier.heightIn(min = PHOTO_STATUS_HEIGHT))
            .padding(horizontal = if (compact) PHOTO_TAG_PADDING_X else SaqzTheme.metrics.grid, vertical = PHOTO_TAG_PADDING_Y),
        horizontalArrangement = Arrangement.spacedBy(PHOTO_DOT_GAP, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) Box(Modifier.size(PHOTO_DOT_SIZE).background(dot, CircleShape))
        Text(
            text = text,
            style = SaqzTheme.typography.caption.copy(
                fontSize = if (compact) PHOTO_TAG_SMALL_TEXT else PHOTO_TAG_TEXT,
                lineHeight = if (compact) PHOTO_TAG_SMALL_LINE else PHOTO_TAG_LINE,
                fontWeight = FontWeight.Bold,
            ),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Botão translúcido sobre foto ("Confirmar" na lista de grupos): azul da marca a 70%, texto branco. A pílula visível tem
 * 28 dp, mas o alvo de toque é de 44 dp (o mesmo do `SaqzIconButton`): o respiro em volta é transparente.
 */
@Composable
fun SaqzPhotoButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = SaqzTheme.colors
    val dismissKeyboard = rememberSaqzDismissKeyboard()
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = SaqzTheme.metrics.iconButtonSize)
            .clickable(enabled = enabled, onClickLabel = label, role = Role.Button) {
                dismissKeyboard()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PHOTO_BUTTON_HEIGHT)
                .background(colors.primary.copy(alpha = PHOTO_TAG_ALPHA), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = SaqzTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                color = colors.onPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// Medidas do desenho da lista de grupos (gerar.py, h_admin_tag e h_photo_status): fora da escala do fluxo 10.
private const val PHOTO_TAG_ALPHA = 0.7f
private val PHOTO_TAG_PADDING_X = 6.dp
private val PHOTO_TAG_PADDING_Y = 2.dp
private val PHOTO_STATUS_HEIGHT = 24.dp
private val PHOTO_BUTTON_HEIGHT = 28.dp
private val PHOTO_DOT_SIZE = 6.dp
private val PHOTO_DOT_GAP = 6.dp
private val PHOTO_TAG_SMALL_TEXT = 10.sp
private val PHOTO_TAG_SMALL_LINE = 12.sp
private val PHOTO_TAG_TEXT = 11.sp
private val PHOTO_TAG_LINE = 14.sp

@Preview
@Composable
private fun SaqzPhotoTagPreview() = SaqzTheme {
    SaqzPreviewGrid {
        Box(Modifier.size(112.dp, 120.dp).background(SaqzTheme.colors.textSecondary)) {
            SaqzPhotoTag(
                text = "Admin",
                color = SaqzTheme.colors.textPrimary,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            )
            SaqzPhotoTag(
                text = "Você vai",
                color = SaqzTheme.colors.successForeground,
                dot = SaqzTheme.colors.success,
                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp).fillMaxWidth(),
            )
        }
        Box(Modifier.size(112.dp, 120.dp).background(SaqzTheme.colors.textSecondary)) {
            SaqzPhotoButton(
                label = "Confirmar",
                onClick = {},
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 8.dp).fillMaxWidth(),
            )
        }
    }
}
