package br.com.saqz.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.saqz.designsystem.resources.Res
import br.com.saqz.designsystem.resources.material_arrow_back
import br.com.saqz.designsystem.resources.topbar_back
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

const val SaqzTopBarTag = "saqz-top-bar"
const val SaqzTopBarTitleTag = "saqz-top-bar-title"
const val SaqzTopBarBackTag = "saqz-top-bar-back"

@Composable
fun SaqzTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(SaqzTheme.colors.surface)
            .testTag(SaqzTopBarTag),
    ) {
        Box(
            Modifier.fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(48.dp)
                        .testTag(SaqzTopBarBackTag),
                ) {
                    Image(
                        painter = painterResource(Res.drawable.material_arrow_back),
                        contentDescription = stringResource(Res.string.topbar_back),
                        colorFilter = ColorFilter.tint(SaqzTheme.colors.primary),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Text(
                title,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp)
                    .testTag(SaqzTopBarTitleTag),
                style = SaqzTheme.typography.lead.copy(
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = SaqzTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            if (trailingContent != null) {
                Box(Modifier.align(Alignment.CenterEnd)) { trailingContent() }
            }
        }
        Box(
            Modifier.fillMaxWidth()
                .height(1.dp)
                .background(SaqzTheme.colors.hairline),
        )
    }
}

@Preview
@Composable
private fun SaqzTopBarPreview() = SaqzTheme {
    SaqzTopBar(title = "Título")
}

@Preview
@Composable
private fun SaqzTopBarWithBackPreview() = SaqzTheme {
    SaqzTopBar(
        title = "Título",
        onBack = {},
        trailingContent = { Text("⋯") },
    )
}