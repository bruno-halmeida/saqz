package br.com.saqz.groups.presentation.communication

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzSwitch
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.ui.GroupLoadFailure
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.connected_load_failure_title
import br.com.saqz.groups.resources.communication_notifications
import br.com.saqz.groups.resources.communication_settings
import br.com.saqz.groups.resources.communication_settings_note
import br.com.saqz.groups.resources.communication_pref_notices
import br.com.saqz.groups.resources.communication_pref_messages
import br.com.saqz.groups.resources.communication_pref_reminders
import br.com.saqz.groups.resources.communication_save
import br.com.saqz.groups.resources.communication_saved
import br.com.saqz.groups.resources.communication_failure
import br.com.saqz.groups.resources.communication_open
import br.com.saqz.groups.resources.communication_unread
import br.com.saqz.groups.resources.communication_inbox_empty
import br.com.saqz.groups.resources.communication_more
import br.com.saqz.groups.resources.communication_refresh
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.compose.ui.tooling.preview.Preview

object NotificationCenterTags {
    const val Screen = "notification-center"
    const val Notices = "preferences-notices"
    const val Messages = "preferences-messages"
    const val Reminders = "preferences-reminders"
    fun notification(sequence: Long) = "notification-$sequence"
}

@Composable
fun NotificationCenterRoot(settings: Boolean, onBack: () -> Unit, onOpen: (NotificationCenterEffect.Open) -> Unit) {
    val vm: NotificationCenterViewModel = koinViewModel(
        key = "notification-center/$settings", parameters = { parametersOf(settings) },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    ObserveAsEvents(vm.effects) { effect -> when (effect) { is NotificationCenterEffect.Open -> onOpen(effect) } }
    NotificationCenterScreen(state, settings, onBack, vm::onIntent)
}

@Composable
internal fun NotificationCenterScreen(
    state: NotificationCenterState,
    settings: Boolean,
    onBack: () -> Unit,
    onIntent: (NotificationCenterIntent) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(SaqzTheme.colors.background).testTag(NotificationCenterTags.Screen)) {
        SaqzTopAppBar(
            title = stringResource(if (settings) Res.string.communication_settings else Res.string.communication_notifications),
            onBack = onBack,
        )
        when {
            state.loading -> SaqzSpinner()
            state.error != null -> GroupLoadFailure(
                state.error, { onIntent(NotificationCenterIntent.Refresh) },
                failureTitle = stringResource(Res.string.connected_load_failure_title),
            )
            else -> LazyColumn(
                modifier = Modifier.padding(SaqzTheme.metrics.horizontalPadding),
                verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
            ) {
                if (state.actionFailed) item { Text(stringResource(Res.string.communication_failure)) }
                if (settings) item {
                    Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
                        Text(stringResource(Res.string.communication_settings_note))
                        SaqzSwitch(
                            state.preferences.notices,
                            { onIntent(NotificationCenterIntent.Preferences(state.preferences.copy(notices = it))) },
                            label = stringResource(Res.string.communication_pref_notices), enabled = !state.busy,
                            modifier = Modifier.testTag(NotificationCenterTags.Notices),
                        )
                        SaqzSwitch(
                            state.preferences.messages,
                            { onIntent(NotificationCenterIntent.Preferences(state.preferences.copy(messages = it))) },
                            label = stringResource(Res.string.communication_pref_messages), enabled = !state.busy,
                            modifier = Modifier.testTag(NotificationCenterTags.Messages),
                        )
                        SaqzSwitch(
                            state.preferences.reminders,
                            { onIntent(NotificationCenterIntent.Preferences(state.preferences.copy(reminders = it))) },
                            label = stringResource(Res.string.communication_pref_reminders), enabled = !state.busy,
                            modifier = Modifier.testTag(NotificationCenterTags.Reminders),
                        )
                        SaqzButton(
                            stringResource(Res.string.communication_save),
                            { onIntent(NotificationCenterIntent.Save) }, loading = state.busy,
                        )
                        if (state.saved) Text(stringResource(Res.string.communication_saved))
                    }
                } else {
                    item {
                        SaqzButton(
                            stringResource(Res.string.communication_refresh),
                            { onIntent(NotificationCenterIntent.Refresh) }, enabled = !state.busy,
                        )
                    }
                    if (state.items.isEmpty()) item { Text(stringResource(Res.string.communication_inbox_empty)) }
                    items(state.items, key = { it.sequence }) { item ->
                        SaqzCard(modifier = Modifier.testTag(NotificationCenterTags.notification(item.sequence))) {
                            if (!item.read) Text(stringResource(Res.string.communication_unread), color = SaqzTheme.colors.primary)
                            Text(item.content.author, style = SaqzTheme.typography.body)
                            Text(item.content.body)
                            Text(item.content.time, style = SaqzTheme.typography.support)
                            SaqzButton(
                                stringResource(Res.string.communication_open),
                                { onIntent(NotificationCenterIntent.Open(item.sequence)) }, enabled = !state.busy,
                            )
                        }
                    }
                    if (state.nextCursor != null) item {
                        SaqzButton(
                            stringResource(Res.string.communication_more),
                            { onIntent(NotificationCenterIntent.More) }, loading = state.busy,
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun NotificationCenterPreview() = SaqzTheme {
    NotificationCenterScreen(NotificationCenterState(loading = false), true, {}, {})
}
