package br.com.saqz.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.resources.Res
import br.com.saqz.designsystem.resources.action_close
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource

// Test tags let the iosSimulatorArm64Test suite (no screenshot capture) observe the
// modal structure: scrim barrier, header title, scrollable body and fixed footer.
internal const val SaqzModalScrimTag = "saqz-modal-scrim"
internal const val SaqzModalTitleTag = "saqz-modal-title"
internal const val SaqzModalFooterTag = "saqz-modal-footer"

// Pure resolver: the two dismiss channels are independent flags, never collapsed into
// one "dismissible". Back-press is owned by the platform through these properties;
// outside-click is driven by our own scrim so it stays observable without a real window.
internal fun saqzDialogProperties(
    dismissOnBackPress: Boolean,
    dismissOnClickOutside: Boolean,
): DialogProperties = DialogProperties(
    dismissOnBackPress = dismissOnBackPress,
    dismissOnClickOutside = dismissOnClickOutside,
    usePlatformDefaultWidth = false,
)

@Composable
fun SaqzDialog(
    title: String,
    onCloseRequest: () -> Unit,
    primaryAction: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissOnBackPress: Boolean = false,
    dismissOnClickOutside: Boolean = false,
    showCloseAction: Boolean = true,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onCloseRequest,
        properties = saqzDialogProperties(dismissOnBackPress, dismissOnClickOutside),
    ) {
        SaqzModalScaffold(
            title = title,
            onCloseRequest = onCloseRequest,
            primaryAction = primaryAction,
            showCloseAction = showCloseAction,
            dismissOnClickOutside = dismissOnClickOutside,
            alignment = Alignment.Center,
            cardModifier = modifier.fillMaxWidth(0.92f),
            content = content,
        )
    }
}

// Shared modal body for dialog (centred) and bottom sheet (bottom-anchored). The scrim
// blocks the background; the card taps are consumed so they never reach the scrim.
// Title sits first, body scrolls, footer stays fixed regardless of content length.
@Composable
internal fun SaqzModalScaffold(
    title: String,
    onCloseRequest: () -> Unit,
    primaryAction: @Composable () -> Unit,
    showCloseAction: Boolean,
    dismissOnClickOutside: Boolean,
    alignment: Alignment,
    cardModifier: Modifier,
    content: @Composable () -> Unit,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val scrimInteraction = remember { MutableInteractionSource() }
    // ponytail: standard modal scrim, not a registry token — overlays need a dimming
    // barrier the token table never defines.
    val scrim = Color.Black.copy(alpha = 0.32f)
    val shape = RoundedCornerShape(metrics.cardRadius)

    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(metrics.horizontalPadding)) {
        // Cap the card at the available height so a tall body scrolls while a short one
        // wraps; alignment (not arrangement) anchors it centred or bottom.
        val maxCardHeight = maxHeight

        // Scrim under the card, click-consuming so the background is unavailable; only
        // forwards a close when outside-dismiss is enabled.
        Box(
            modifier = Modifier
                .testTag(SaqzModalScrimTag)
                .matchParentSize()
                .background(scrim)
                .clickable(
                    interactionSource = scrimInteraction,
                    indication = null,
                ) { if (dismissOnClickOutside) onCloseRequest() },
        )

        Column(
            modifier = cardModifier
                .align(alignment)
                .heightIn(max = maxCardHeight)
                // Consume taps so a press inside the card never reaches the scrim below.
                .pointerInput(Unit) { detectTapGestures { } }
                .clip(shape)
                .background(colors.surface, shape)
                .padding(metrics.utilityCardPadding),
            verticalArrangement = Arrangement.spacedBy(metrics.grid),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = SaqzTheme.typography.displayMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.testTag(SaqzModalTitleTag),
                )
                if (showCloseAction) {
                    val closeLabel = stringResource(Res.string.action_close)
                    val closeInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .sizeIn(
                                minWidth = metrics.minimumTouchTarget,
                                minHeight = metrics.minimumTouchTarget,
                            )
                            .clickable(
                                interactionSource = closeInteraction,
                                indication = null,
                                onClickLabel = closeLabel,
                                role = Role.Button,
                            ) { onCloseRequest() }
                            .semantics { contentDescription = closeLabel },
                        contentAlignment = Alignment.Center,
                    ) {
                        // Decorative glyph; the accessible name lives on the box itself.
                        Text(text = "×", style = SaqzTheme.typography.lead, color = colors.textSecondary)
    }
}

            }
            // Scrollable body: takes the remaining bounded height so the footer stays fixed.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                content()
            }
                // Fixed footer: never scrolls out of view.
                Box(modifier = Modifier.fillMaxWidth().testTag(SaqzModalFooterTag)) {
                    primaryAction()
            }
        }
    }
}

@Preview
@Composable
private fun SaqzDialogPreview() = SaqzTheme {
    SaqzDialog("Confirmar ação", {}, primaryAction = { SaqzButton("Confirmar", {}) }) {
        Text("Esta ação pode ser confirmada nesta prévia.")
    }
}
