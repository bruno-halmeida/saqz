package br.com.saqz.designsystem.component

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.resources.Res
import br.com.saqz.designsystem.resources.action_hide_password
import br.com.saqz.designsystem.resources.action_show_password
import br.com.saqz.designsystem.resources.material_visibility
import br.com.saqz.designsystem.resources.material_visibility_off
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource

enum class SaqzInputKind { Text, Email, Password }

internal fun keyboardTypeFor(kind: SaqzInputKind): KeyboardType =
    when (kind) {
        SaqzInputKind.Text -> KeyboardType.Text
        SaqzInputKind.Email -> KeyboardType.Email
        SaqzInputKind.Password -> KeyboardType.Password
    }

// Password masks until revealed; the toggle only flips `revealed`, so the visual
// transformation is the ONLY thing it changes — never the value/selection/focus.
internal fun visualTransformationFor(kind: SaqzInputKind, revealed: Boolean): VisualTransformation =
    if (kind == SaqzInputKind.Password && !revealed) PasswordVisualTransformation() else VisualTransformation.None

@Composable
fun SaqzInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    kind: SaqzInputKind = SaqzInputKind.Text,
    helperText: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
    inlineLabel: Boolean = false,
    leadingContent: (@Composable () -> Unit)? = null,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val shape = RoundedCornerShape(metrics.compactControlRadius)
    // `revealed` governs only the visual transformation; the field value is never
    // copied here, it stays owned by the caller-provided TextFieldValue.
    var revealed by remember { mutableStateOf(false) }
    val message = errorText ?: helperText

    Column(
        modifier = modifier
            .semantics(mergeDescendants = true) {}
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(metrics.subGrid),
    ) {
        if (!inlineLabel) {
            Text(text = label, style = SaqzTheme.typography.caption, color = colors.textSecondary)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (inlineLabel) 56.dp else metrics.minimumTouchTarget)
                .background(colors.surface, shape)
                .border(
                    1.dp,
                    when {
                        errorText != null -> colors.errorForeground
                        inlineLabel -> colors.border
                        else -> colors.controlBorder
                    },
                    shape,
                )
                .padding(horizontal = metrics.horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingContent?.let { leading ->
                Box(modifier = Modifier.padding(end = 12.dp), contentAlignment = Alignment.Center) {
                    leading()
                }
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (inlineLabel && value.text.isEmpty()) {
                    Text(
                        text = label,
                        style = SaqzTheme.typography.body,
                        color = if (enabled) colors.textMuted else colors.disabledForeground,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = true,
                    visualTransformation = visualTransformationFor(kind, revealed),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardTypeFor(kind)),
                    textStyle = SaqzTheme.typography.body.copy(color = colors.textPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = label
                            if (errorText != null) error(errorText)
                        },
                )
            }
            if (kind == SaqzInputKind.Password) {
                val toggleLabel = stringResource(
                    if (revealed) Res.string.action_hide_password else Res.string.action_show_password,
                )
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .sizeIn(minWidth = metrics.minimumTouchTarget, minHeight = metrics.minimumTouchTarget)
                        // The toggle never steals focus, so the field keeps it across the flip.
                        .focusProperties { canFocus = false }
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClickLabel = toggleLabel,
                            role = Role.Button,
                        ) { revealed = !revealed }
                        .semantics { contentDescription = toggleLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    PasswordVisibilityGlyph(revealed = revealed, color = colors.textSecondary)
                }
            }
        }
        if (message != null) {
            Text(
                text = message,
                style = SaqzTheme.typography.caption,
                color = if (errorText != null) colors.errorForeground else colors.textMuted,
            )
        }
    }
}

@Composable
private fun PasswordVisibilityGlyph(revealed: Boolean, color: Color) {
    Image(
        painter = painterResource(
            if (revealed) Res.drawable.material_visibility_off else Res.drawable.material_visibility,
        ),
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = Modifier.size(20.dp).clearAndSetSemantics {},
    )
}

@Preview
@Composable
private fun SaqzInputPreview() = SaqzTheme {
    SaqzInput(TextFieldValue("nome@exemplo.com"), {}, label = "E-mail", kind = SaqzInputKind.Email)
}
