package br.com.saqz.groups.presentation.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzSkeleton
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_details_created_photo_failed
import br.com.saqz.groups.resources.group_details_created_photo_failed_title
import br.com.saqz.groups.resources.group_details_venue_edit
import org.jetbrains.compose.resources.stringResource

// Medidas do skeleton que a grade não nomeia (mock `skeleton()` do _mock-grupo/build.mjs).
private val SkeletonHeroHeight = 256.dp
private val SkeletonHeaderHeight = 20.dp
private val SkeletonTitleHeight = 14.dp
private val SkeletonMetaHeight = 12.dp
private val SkeletonLineGap = 6.dp

/**
 * A barra: o nome do grupo (uma vez só na tela) e "Editar" para o gestor. A ação some enquanto
 * carrega e na falha de carga — não há grupo para editar.
 */
@Composable
internal fun GroupTopBar(
    state: GroupDetailsState,
    onBack: () -> Unit,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val canEdit = state.isAdmin && !state.isLoading && !state.loadFailed
    SaqzTopAppBar(
        modifier = modifier,
        title = state.header?.name,
        onBack = onBack,
        actions = {
            if (canEdit) {
                SaqzButton(
                    label = stringResource(Res.string.group_details_venue_edit),
                    onClick = { onIntent(GroupDetailsIntent.EditGroup) },
                    modifier = Modifier.testTag(GroupDetailsTags.EditGroup),
                    variant = SaqzButtonVariant.Ghost,
                    size = SaqzButtonSize.Sm,
                    labelStyle = SaqzTheme.typography.label,
                )
            }
        },
    )
}

/** O skeleton espelha o layout real — bloco do jogo e duas listas — para a troca não saltar. */
@Composable
internal fun GroupDetailsLoading(modifier: Modifier = Modifier) {
    val metrics = SaqzTheme.metrics
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap)
            .testTag(GroupDetailsTags.Skeleton),
        verticalArrangement = Arrangement.spacedBy(metrics.sectionGap),
    ) {
        SaqzSkeleton(height = SkeletonHeroHeight, radius = metrics.blockRadius)
        GroupSkeletonList(
            headerWidth = metrics.grid * 16,
            titleWidth = metrics.grid * 19,
            metaWidth = metrics.grid * 25,
            circle = false,
        )
        GroupSkeletonList(
            headerWidth = metrics.grid * 10,
            titleWidth = metrics.grid * 11,
            metaWidth = metrics.grid * 27,
            circle = true,
        )
    }
}

@Composable
private fun GroupSkeletonList(headerWidth: Dp, titleWidth: Dp, metaWidth: Dp, circle: Boolean) {
    val metrics = SaqzTheme.metrics
    Column(verticalArrangement = Arrangement.spacedBy(metrics.blockGap)) {
        SaqzSkeleton(width = headerWidth, height = SkeletonHeaderHeight)
        SaqzCard(padded = false) {
            repeat(2) { index ->
                if (index > 0) SaqzDivider()
                Row(
                    modifier = Modifier.padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
                ) {
                    if (circle) {
                        SaqzSkeleton(width = metrics.grid * 5, height = metrics.grid * 5, circle = true)
                    } else {
                        SaqzSkeleton(
                            width = metrics.iconButtonSize,
                            height = metrics.iconButtonSize,
                            radius = metrics.inputRadius,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(SkeletonLineGap)) {
                        SaqzSkeleton(width = titleWidth, height = SkeletonTitleHeight)
                        SaqzSkeleton(width = metaWidth, height = SkeletonMetaHeight)
                    }
                }
            }
        }
    }
}

/** O topo da coluna rolável: só o banner da foto (pós-criação). */
@Composable
internal fun ColumnScope.GroupTopContent(photoFailed: Boolean) {
    if (photoFailed) GroupPhotoFailedBanner()
}

@Composable
private fun GroupPhotoFailedBanner(modifier: Modifier = Modifier) {
    val colors = SaqzTheme.colors
    SaqzCard(modifier = modifier.testTag(GroupDetailsTags.PhotoFailed)) {
        Text(
            text = stringResource(Res.string.group_details_created_photo_failed_title),
            color = colors.textPrimary,
            style = SaqzTheme.typography.body,
        )
        Text(
            text = stringResource(Res.string.group_details_created_photo_failed),
            color = colors.textSecondary,
            style = SaqzTheme.typography.support,
        )
    }
}
