package br.com.saqz.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.resources.Res
import br.com.saqz.designsystem.resources.action_hide_password
import br.com.saqz.designsystem.resources.action_show_password
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource

enum class SaqzInputKind { Text, Email, Password, Phone }

internal fun keyboardTypeFor(kind: SaqzInputKind): KeyboardType =
    when (kind) {
        SaqzInputKind.Text -> KeyboardType.Text
        SaqzInputKind.Email -> KeyboardType.Email
        SaqzInputKind.Password -> KeyboardType.Password
        SaqzInputKind.Phone -> KeyboardType.Phone
    }

// Password masks until revealed; the toggle only flips `revealed`, so the visual
// transformation is the ONLY thing it changes — never the value/selection/focus.
internal fun visualTransformationFor(kind: SaqzInputKind, revealed: Boolean): VisualTransformation =
    if (kind == SaqzInputKind.Password && !revealed) PasswordVisualTransformation() else VisualTransformation.None

// A borda de 3dp existe sempre (transparente em repouso) para o glow de foco não
// empurrar o layout ao aparecer.
private val FocusRingWidth = 3.dp

@Composable
fun SaqzInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    kind: SaqzInputKind = SaqzInputKind.Text,
    helperText: String? = null,
    errorText: String? = null,
    invalid: Boolean = false,
    enabled: Boolean = true,
    inlineLabel: Boolean = false,
    borderColor: Color? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    placeholder: String? = null,
    keyboardType: KeyboardType = keyboardTypeFor(kind),
    singleLine: Boolean = true,
    minLines: Int = 1,
    showLabel: Boolean = true,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val shape = RoundedCornerShape(metrics.inputRadius)
    // `revealed` governs only the visual transformation; the field value is never
    // copied here, it stays owned by the caller-provided TextFieldValue.
    var revealed by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val message = errorText ?: helperText
    val wrong = errorText != null || invalid

    val accent = when {
        wrong -> colors.errorForeground
        focused -> colors.primary
        else -> null
    }
    val ring = accent?.copy(alpha = 0.11f) ?: Color.Transparent
    val line = accent ?: borderColor ?: colors.border

    Column(
        modifier = modifier
            .semantics(mergeDescendants = true) {}
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(metrics.subGrid),
    ) {
        if (showLabel && !inlineLabel) {
            Text(text = label, style = SaqzTheme.typography.support, color = colors.textSecondary)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(FocusRingWidth, ring, shape)
                .padding(FocusRingWidth)
                .heightIn(min = if (inlineLabel) 56.dp else metrics.minimumTouchTarget)
                .background(colors.surface, shape)
                .border(1.dp, line, shape)
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
                if (value.text.isEmpty() && (placeholder != null || inlineLabel)) {
                    Text(
                        text = placeholder ?: label,
                        style = SaqzTheme.typography.body,
                        color = if (enabled) colors.textPlaceholder else colors.disabledForeground,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = singleLine,
                    minLines = minLines,
                    visualTransformation = visualTransformationFor(kind, revealed),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    textStyle = SaqzTheme.typography.body.copy(color = colors.textPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused }
                        .semantics {
                            contentDescription = label
                            if (errorText != null) error(errorText)
                        },
                )
            }
            if (kind == SaqzInputKind.Password) {
                PasswordToggle(revealed = revealed, onToggle = { revealed = !revealed })
            }
            trailingContent?.let { trailing ->
                Box(modifier = Modifier.padding(start = 12.dp), contentAlignment = Alignment.Center) {
                    trailing()
                }
            }
        }
        if (message != null) {
            Text(
                text = message,
                style = SaqzTheme.typography.support,
                color = if (errorText != null) colors.errorForeground else colors.textSecondary,
            )
        }
    }
}

@Composable
private fun PasswordToggle(revealed: Boolean, onToggle: () -> Unit) {
    val metrics = SaqzTheme.metrics
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
                onClick = onToggle,
            )
            .semantics { contentDescription = toggleLabel },
        contentAlignment = Alignment.Center,
    ) {
        SaqzIcon(
            icon = if (revealed) SaqzIcons.EyeOff else SaqzIcons.Eye,
            tint = SaqzTheme.colors.textSecondary,
            size = 20.dp,
        )
    }
}

@Preview
@Composable
private fun SaqzInputPreview() = SaqzTheme {
    SaqzPreviewGrid {
        SaqzInput(TextFieldValue("nome@exemplo.com"), {}, label = "E-mail", kind = SaqzInputKind.Email)
        SaqzInput(TextFieldValue(""), {}, label = "Local", placeholder = "CERET — Quadra 2")
        SaqzInput(TextFieldValue("123"), {}, label = "Senha", kind = SaqzInputKind.Password)
        SaqzInput(TextFieldValue("ana"), {}, label = "E-mail", errorText = "Informe um e-mail válido")
        SaqzInput(TextFieldValue("Fixo"), {}, label = "Grupo", enabled = false, helperText = "Não editável")
    }
}
