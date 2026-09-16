package br.com.saqz.groups.presentation.communication

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.designsystem.SaqzAvatar
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzSkeleton
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.SaqzSwitch
import br.com.saqz.designsystem.SaqzSegmented
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzMotionPolicy
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.communication.CommunicationChannel
import br.com.saqz.groups.presentation.ui.GroupLoadFailure
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.connected_load_failure_title
import br.com.saqz.groups.resources.communication_notifications
import br.com.saqz.groups.resources.communication_settings
import br.com.saqz.groups.resources.communication_settings_note
import br.com.saqz.groups.resources.communication_channel_app
import br.com.saqz.groups.resources.communication_channel_push
import br.com.saqz.groups.resources.communication_channel_whatsapp
import br.com.saqz.groups.resources.communication_push_note
import br.com.saqz.groups.resources.communication_whatsapp_note
import br.com.saqz.groups.resources.communication_pref_charges
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
            state.loading && !settings -> NotificationInboxSkeleton()
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
                        SaqzSegmented(
                            listOf(
                                stringResource(Res.string.communication_channel_app),
                                stringResource(Res.string.communication_channel_push),
                                stringResource(Res.string.communication_channel_whatsapp)),
                            state.settingsChannel.ordinal,
                            { onIntent(NotificationCenterIntent.SelectChannel(NotificationSettingsChannel.entries[it])) },
                        )
                        if (state.settingsChannel == NotificationSettingsChannel.APP) {
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
                        } else DeliveryChannelSettings(state, onIntent)
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
                        NotificationCard(item, state.busy, onIntent)
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

/**
 * Card de notificação com a cara do design system: avatar com iniciais do autor
 * (10k), chip "Não lida" no tom brand com ponto, horário em caption e ação "Abrir"
 * como botão ghost pequeno — a pílula azul cheia ficava pesada demais por item.
 */
@Composable
private fun NotificationCard(
    item: NotificationUi,
    busy: Boolean,
    onIntent: (NotificationCenterIntent) -> Unit,
) {
    val colors = SaqzTheme.colors
    val typography = SaqzTheme.typography
    SaqzCard(modifier = Modifier.testTag(NotificationCenterTags.notification(item.sequence))) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SaqzAvatar(name = item.content.author, initialsColor = colors.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = item.content.author,
                    style = typography.body.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(item.content.time, style = typography.caption, color = colors.textSecondary)
            }
            if (!item.read) {
                SaqzStatusChip(
                    stringResource(Res.string.communication_unread),
                    tone = SaqzChipTone.Brand, dot = true,
                )
            }
        }
        Text(item.content.body, style = typography.support, color = colors.textPrimary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            SaqzButton(
                stringResource(Res.string.communication_open),
                { onIntent(NotificationCenterIntent.Open(item.sequence)) },
                variant = SaqzButtonVariant.Ghost, size = SaqzButtonSize.Sm, enabled = !busy,
            )
        }
    }
}

/**
 * Loading da caixa de entrada: quatro cards de placeholder no formato real da
 * notificação, pulsando juntos. Substitui a bolinha no canto — quem espera vê o
 * esqueleto do que vai aparecer. Movimento reduzido congela o pulso em 1f.
 */
@Composable
private fun NotificationInboxSkeleton() {
    val pulse = if (SaqzTheme.motion == SaqzMotionPolicy.Reduced) {
        1f
    } else {
        rememberInfiniteTransition(label = "notificationInboxLoading").animateFloat(
            initialValue = 0.45f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
            label = "notificationInboxPulse",
        ).value
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(SaqzTheme.metrics.horizontalPadding)
            .graphicsLayer { alpha = pulse },
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        repeat(SkeletonCardCount) {
            SaqzCard {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SaqzSkeleton(width = 40.dp, height = 40.dp, circle = true)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SaqzSkeleton(width = 140.dp, height = 14.dp)
                        SaqzSkeleton(width = 88.dp, height = 12.dp)
                    }
                }
                SaqzSkeleton(height = 14.dp)
                SaqzSkeleton(height = 14.dp)
            }
        }
    }
}

private const val SkeletonCardCount = 4

@Composable
private fun DeliveryChannelSettings(state: NotificationCenterState, onIntent: (NotificationCenterIntent) -> Unit) {
    val preferences = state.preferences
    val push = preferences.pushSettings
    val whatsapp = preferences.whatsappSettings
    if (state.settingsChannel == NotificationSettingsChannel.PUSH) {
    Text(stringResource(Res.string.communication_push_note), style = SaqzTheme.typography.support)
    PreferenceSwitch("push-notices", stringResource(Res.string.communication_pref_notices), push.notices, state.busy) {
        onIntent(NotificationCenterIntent.Preferences(preferences.copy(push = push.copy(notices = it))))
    }
    PreferenceSwitch("push-messages", stringResource(Res.string.communication_pref_messages), push.messages, state.busy) {
        onIntent(NotificationCenterIntent.Preferences(preferences.copy(push = push.copy(messages = it))))
    }
    PreferenceSwitch("push-reminders", stringResource(Res.string.communication_pref_reminders), push.reminders, state.busy) {
        onIntent(NotificationCenterIntent.Preferences(preferences.copy(push = push.copy(reminders = it))))
    }
    PreferenceSwitch("push-charges", stringResource(Res.string.communication_pref_charges), push.charges, state.busy) {
        onIntent(NotificationCenterIntent.Preferences(preferences.copy(push = push.copy(charges = it))))
    }
    } else {
    Text(stringResource(Res.string.communication_whatsapp_note), style = SaqzTheme.typography.support)
    PreferenceSwitch("whatsapp-charges", stringResource(Res.string.communication_pref_charges), whatsapp.charges, state.busy) {
        onIntent(NotificationCenterIntent.Preferences(preferences.copy(whatsapp = whatsapp.copy(charges = it))))
    }
    }
}

@Composable
private fun PreferenceSwitch(tag: String, label: String, checked: Boolean, busy: Boolean, onChange: (Boolean) -> Unit) {
    SaqzSwitch(checked, onChange, label = label, enabled = !busy, modifier = Modifier.testTag("preferences-$tag"))
}

@Preview
@Composable
private fun NotificationCenterPreview() = SaqzTheme {
    NotificationCenterScreen(NotificationCenterState(loading = false), true, {}, {})
}

@Preview
@Composable
private fun NotificationInboxPreview() = SaqzTheme {
    NotificationCenterScreen(
        NotificationCenterState(
            loading = false,
            items = listOf(
                NotificationUi(
                    sequence = 1, groupId = "g1", channel = CommunicationChannel.NOTICE, gameId = null,
                    content = ThreadMessageUi("m1", "Bruna Silva", "Jogo de sábado confirmado, lista aberta!", "há 2 h"),
                    read = false,
                ),
                NotificationUi(
                    sequence = 2, groupId = "g1", channel = CommunicationChannel.REMINDER, gameId = null,
                    content = ThreadMessageUi("m2", "Lucas Pereira", "Lembrete: mensalidade vence amanhã.", "há 5 h"),
                    read = true,
                ),
            ),
        ),
        settings = false, onBack = {}, onIntent = {},
    )
}

@Preview
@Composable
private fun NotificationInboxSkeletonPreview() = SaqzTheme {
    NotificationCenterScreen(NotificationCenterState(loading = true), settings = false, onBack = {}, onIntent = {})
}
