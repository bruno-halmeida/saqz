package br.com.saqz.groups.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzEmptyState
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_load_access_denied
import br.com.saqz.groups.resources.group_load_failure_body
import br.com.saqz.groups.resources.group_load_failure_title
import br.com.saqz.groups.resources.group_load_not_found
import br.com.saqz.groups.resources.group_system_retry
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun GroupLoadFailure(
    error: GroupUiError?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    val title = when (error) {
        GroupUiError.AccessDenied -> stringResource(Res.string.group_load_access_denied)
        GroupUiError.NotFound -> stringResource(Res.string.group_load_not_found)
        else -> stringResource(Res.string.group_load_failure_title)
    }
    SaqzEmptyState(
        title = title,
        description = stringResource(Res.string.group_load_failure_body),
        icon = SaqzIcons.CircleAlert,
        action = stringResource(Res.string.group_system_retry),
        onAction = onRetry,
        modifier = modifier.then(if (testTag == null) Modifier else Modifier.testTag(testTag)),
    )
}
