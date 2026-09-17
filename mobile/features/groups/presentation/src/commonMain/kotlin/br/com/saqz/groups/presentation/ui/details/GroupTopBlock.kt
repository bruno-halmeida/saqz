package br.com.saqz.groups.presentation.ui.details

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_details_created_photo_failed
import br.com.saqz.groups.resources.group_details_created_photo_failed_title
import org.jetbrains.compose.resources.stringResource

/** Andaime (T): hoje só o título; o C2 põe "Editar" no slot de ações e passa a usar [onIntent]. */
@Suppress("UnusedParameter")
@Composable
internal fun GroupTopBar(
    state: GroupDetailsState,
    onBack: () -> Unit,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    SaqzTopAppBar(modifier = modifier, title = state.header?.name, onBack = onBack)
}

/** Andaime (T): o spinner de hoje; o C2 troca pelo skeleton que espelha o layout. */
@Composable
internal fun GroupDetailsLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        SaqzSpinner()
    }
}

/**
 * Andaime (T): o que vem no topo da coluna rolável — o banner da foto (só no pós-criação) e o
 * card de cabeçalho antigo. O C2 apaga o card: o nome fica só na barra. Emite os filhos direto
 * na coluna da tela (sem contêiner), para o respiro entre eles ser o mesmo dos outros blocos —
 * por isso é extensão de `ColumnScope`: é o que o compose-rules exige de quem emite mais de um nó.
 */
@Composable
internal fun ColumnScope.GroupTopContent(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    photoFailed: Boolean,
) {
    if (photoFailed) GroupPhotoFailedBanner()
    state.header?.let { GroupHeaderCard(header = it, isAdmin = state.isAdmin, onIntent = onIntent) }
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
