package br.com.saqz.groups.presentation.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.theme.SaqzTheme

@Composable
internal fun GroupVenueRow(
    name: String,
    address: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SaqzIcon(SaqzIcons.Pin, tint = SaqzTheme.colors.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = SaqzTheme.typography.label,
                color = SaqzTheme.colors.textPrimary,
            )
            Text(
                text = address,
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.textSecondary,
            )
        }
        Box(
            modifier = Modifier.clickable(
                onClickLabel = actionLabel,
                role = Role.Button,
                onClick = onAction,
            )
                .heightIn(min = SaqzTheme.metrics.minimumTouchTarget)
                .padding(horizontal = SaqzTheme.metrics.subGrid),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = actionLabel,
                style = SaqzTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                color = SaqzTheme.colors.primary,
            )
        }
    }
}

@Preview
@Composable
private fun GroupVenueRowPreview() = SaqzTheme {
    SaqzCard(modifier = Modifier.padding(SaqzTheme.metrics.horizontalPadding)) {
        GroupVenueRow(
            name = "CERET — Quadra 2",
            address = "R. Canuto Abreu, s/n · Tatuapé",
            actionLabel = "Ver no mapa",
            onAction = {},
        )
    }
}
