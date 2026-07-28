package br.com.saqz.groups.presentation.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.SaqzSegmented
import br.com.saqz.designsystem.SaqzStepper
import br.com.saqz.designsystem.saqzInitials
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.model.GroupComposition
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.model.GroupPlayStyle
import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.presentation.setup.GroupSetupDefaults
import br.com.saqz.groups.presentation.ui.components.GroupChoiceChipRow
import br.com.saqz.groups.presentation.ui.components.GroupFormCard
import br.com.saqz.groups.presentation.ui.components.GroupRecurrenceSection
import br.com.saqz.groups.presentation.ui.confirmationLeadLabel
import br.com.saqz.groups.presentation.ui.durationLabel
import br.com.saqz.groups.presentation.ui.label
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_setup_add_slot
import br.com.saqz.groups.resources.group_setup_capacity_beach_hint
import br.com.saqz.groups.resources.group_setup_capacity_hint
import br.com.saqz.groups.resources.group_setup_capacity_label
import br.com.saqz.groups.resources.group_setup_composition_hint
import br.com.saqz.groups.resources.group_setup_composition_label
import br.com.saqz.groups.resources.group_setup_confirmation_lead_hint
import br.com.saqz.groups.resources.group_setup_confirmation_lead_label
import br.com.saqz.groups.resources.group_setup_custom_level_hint
import br.com.saqz.groups.resources.group_setup_description_hint
import br.com.saqz.groups.resources.group_setup_description_label
import br.com.saqz.groups.resources.group_setup_duration_label
import br.com.saqz.groups.resources.group_setup_error_capacity_hint
import br.com.saqz.groups.resources.group_setup_error_slot
import br.com.saqz.groups.resources.group_setup_level_hint
import br.com.saqz.groups.resources.group_setup_level_label
import br.com.saqz.groups.resources.group_setup_modality_hint
import br.com.saqz.groups.resources.group_setup_modality_label
import br.com.saqz.groups.resources.group_setup_name_hint
import br.com.saqz.groups.resources.group_setup_name_label
import br.com.saqz.groups.resources.group_setup_photo_added
import br.com.saqz.groups.resources.group_setup_photo_edit_hint
import br.com.saqz.groups.resources.group_setup_photo_hint
import br.com.saqz.groups.resources.group_setup_photo_label
import br.com.saqz.groups.resources.group_setup_play_style_hint
import br.com.saqz.groups.resources.group_setup_play_style_label
import br.com.saqz.groups.resources.group_setup_play_style_not_applicable
import br.com.saqz.groups.resources.group_setup_venue_address_hint
import br.com.saqz.groups.resources.group_setup_venue_address_label
import br.com.saqz.groups.resources.group_setup_venue_name_hint
import br.com.saqz.groups.resources.group_setup_venue_name_label
import org.jetbrains.compose.resources.stringResource

// Público na ordem do export (Masculino, Feminino, Misto), que não é a do enum.
private val CompositionOptions = listOf(
    GroupComposition.MEN,
    GroupComposition.WOMEN,
    GroupComposition.MIXED,
)

// ponytail: o export desenha só 6-0 e 5-1 no segmented; 4-2 e personalizado existem no
// modelo e entram aqui quando o desenho os mostrar.
private val PlayStyleOptions = listOf(GroupPlayStyle.SIX_ZERO, GroupPlayStyle.FIVE_ONE)

/**
 * `SaqzSegmented` não conhece "sem seleção": posiciona o polegar por índice. Com `-1` a
 * tampa arredondada do polegar ainda assoma na borda esquerda do trilho; `-2` o tira
 * inteiro do campo de visão para qualquer número de opções. O certo é um `selected: Int?`
 * no componente — vira ticket do design system, não se conserta aqui dentro.
 */
private const val NoSegmentedSelection = -2

private const val HairlineDivisor = 4
private const val ErrorRingAlpha = 0.1f
private const val ErrorSurfaceAlpha = 0.06f
private const val PhotoSizeFactor = 3

@Composable
internal fun GroupPhotoSection(
    photoUrl: String?,
    groupName: String,
    isEditing: Boolean,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(Res.string.group_setup_photo_label)
    val hint = when {
        isEditing -> stringResource(Res.string.group_setup_photo_edit_hint)
        photoUrl != null -> stringResource(Res.string.group_setup_photo_added)
        else -> stringResource(Res.string.group_setup_photo_hint)
    }
    SaqzCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GroupPhotoThumb(
                photoUrl = photoUrl,
                groupName = groupName,
                actionLabel = label,
                onPick = onPick,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (photoUrl != null && !isEditing && groupName.isNotBlank()) groupName else label,
                    style = SaqzTheme.typography.label,
                    color = SaqzTheme.colors.textPrimary,
                )
                Text(hint, style = SaqzTheme.typography.support, color = SaqzTheme.colors.textSecondary)
            }
        }
    }
}

@Composable
private fun GroupPhotoThumb(
    photoUrl: String?,
    groupName: String,
    actionLabel: String,
    onPick: () -> Unit,
) {
    val metrics = SaqzTheme.metrics
    val colors = SaqzTheme.colors
    // 72 do export: o design system não tem token de miniatura, e 3 × sectionGap dá o número.
    val size = metrics.sectionGap * PhotoSizeFactor
    val badgeSize = metrics.sectionGap + metrics.subGrid
    val shape = RoundedCornerShape(metrics.blockRadius)
    Box(
        modifier = Modifier
            .size(size)
            .clickable(onClickLabel = actionLabel, role = Role.Button, onClick = onPick)
            .testTag(GroupSetupTags.Photo),
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(if (photoUrl == null) colors.surfaceSoft else colors.primary),
            contentAlignment = Alignment.Center,
        ) {
            if (photoUrl == null) {
                SaqzIcon(SaqzIcons.Camera, tint = colors.primary)
            } else {
                // ponytail: sem carregador de imagem no módulo, a foto entra como as
                // iniciais sobre o azul — o mesmo desenho que o `2b` mostra.
                Text(
                    text = saqzInitials(groupName),
                    style = SaqzTheme.typography.title.copy(fontWeight = FontWeight.ExtraBold),
                    color = colors.onPrimary,
                )
            }
        }
        Box(
            modifier = Modifier
                // `right:-4px;bottom:-4px` do export: a insígnia encosta para fora.
                .align(Alignment.BottomEnd)
                .offset(x = metrics.subGrid, y = metrics.subGrid)
                .size(badgeSize)
                .clip(CircleShape)
                .background(if (photoUrl == null) colors.primary else colors.surface),
            contentAlignment = Alignment.Center,
        ) {
            SaqzIcon(
                icon = if (photoUrl == null) SaqzIcons.Plus else SaqzIcons.Camera,
                tint = if (photoUrl == null) colors.onPrimary else colors.primary,
                size = metrics.horizontalPadding,
            )
        }
    }
}

@Composable
internal fun GroupNameSection(
    name: String,
    errorText: String?,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(Res.string.group_setup_name_label)
    GroupFormCard(title = label, modifier = modifier) {
        SaqzInput(
            value = TextFieldValue(name),
            onValueChange = { onChange(it.text) },
            label = label,
            showLabel = false,
            placeholder = stringResource(Res.string.group_setup_name_hint),
            errorText = errorText,
            modifier = Modifier.testTag(GroupSetupTags.Name),
        )
    }
}

@Composable
internal fun GroupModalitySection(
    modality: GroupModality?,
    errorText: String?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GroupFormCard(title = stringResource(Res.string.group_setup_modality_label), modifier = modifier) {
        GroupSelectRow(
            value = modality?.label(),
            placeholder = stringResource(Res.string.group_setup_modality_hint),
            onClick = onOpen,
            isError = errorText != null,
            modifier = Modifier.testTag(GroupSetupTags.Modality),
        )
        GroupFieldError(errorText)
        // `2b`: na areia o card explica por que o sistema de jogo sumiu.
        if (modality == GroupModality.BEACH_VOLLEYBALL) {
            GroupNoteBlock(stringResource(Res.string.group_setup_play_style_not_applicable))
        }
    }
}

@Composable
internal fun GroupCompositionSection(
    composition: GroupComposition?,
    errorText: String?,
    onSelect: (GroupComposition) -> Unit,
    modifier: Modifier = Modifier,
) {
    GroupFormCard(
        title = stringResource(Res.string.group_setup_composition_label),
        hint = stringResource(Res.string.group_setup_composition_hint),
        modifier = modifier,
    ) {
        SaqzSegmented(
            options = CompositionOptions.map { it.label() },
            // Coagir para 0 faria a tela afirmar "Masculino" sobre um valor que não existe.
            selected = CompositionOptions.indexOfOrNone(composition),
            onSelect = { onSelect(CompositionOptions[it]) },
            modifier = Modifier.testTag(GroupSetupTags.Composition),
        )
        GroupFieldError(errorText)
    }
}

@Composable
internal fun GroupLevelSection(
    levelLabel: String?,
    customLevel: String?,
    showsCustomLevel: Boolean,
    customLevelError: String?,
    onOpen: () -> Unit,
    onCustomLevelChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(Res.string.group_setup_level_label)
    GroupFormCard(title = label, modifier = modifier) {
        GroupSelectRow(
            value = levelLabel,
            placeholder = stringResource(Res.string.group_setup_level_hint),
            onClick = onOpen,
            modifier = Modifier.testTag(GroupSetupTags.Level),
        )
        if (showsCustomLevel) {
            SaqzInput(
                value = TextFieldValue(customLevel.orEmpty()),
                onValueChange = { onCustomLevelChange(it.text) },
                label = label,
                showLabel = false,
                placeholder = stringResource(Res.string.group_setup_custom_level_hint),
                errorText = customLevelError,
                modifier = Modifier.testTag(GroupSetupTags.CustomLevel),
            )
        }
    }
}

@Composable
internal fun GroupPlayStyleSection(
    playStyle: GroupPlayStyle?,
    onSelect: (GroupPlayStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    GroupFormCard(
        title = stringResource(Res.string.group_setup_play_style_label),
        hint = stringResource(Res.string.group_setup_play_style_hint),
        modifier = modifier,
    ) {
        SaqzSegmented(
            options = PlayStyleOptions.map { it.label() },
            // 4-2 e personalizado existem no modelo e não no trilho, então o valor salvo
            // pode não estar entre as opções: o trilho fica sem seleção. Coagir para 0
            // pintaria 6-0 sobre um grupo salvo em 4-2 — e o primeiro toque gravaria a
            // mentira por cima do dado real.
            selected = PlayStyleOptions.indexOfOrNone(playStyle),
            onSelect = { onSelect(PlayStyleOptions[it]) },
            modifier = Modifier.testTag(GroupSetupTags.PlayStyle),
        )
    }
}

@Composable
internal fun GroupDescriptionSection(
    description: String?,
    errorText: String?,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(Res.string.group_setup_description_label)
    GroupFormCard(title = label, modifier = modifier) {
        SaqzInput(
            value = TextFieldValue(description.orEmpty()),
            onValueChange = { onChange(it.text) },
            label = label,
            showLabel = false,
            placeholder = stringResource(Res.string.group_setup_description_hint),
            errorText = errorText,
            singleLine = false,
            minLines = DescriptionMinLines,
            modifier = Modifier.testTag(GroupSetupTags.Description),
        )
    }
}

@Composable
internal fun GroupCapacitySection(
    capacity: Int,
    isBeach: Boolean,
    hasError: Boolean,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    SaqzCard(modifier = modifier) {
        SaqzStepper(
            value = capacity,
            onValueChange = onChange,
            // O teto é do backend (`2..100`), como o dos campos de texto: entra como
            // limite de entrada, não como mensagem de erro depois do envio.
            min = GroupSetupDefaults.MinCapacity,
            max = GroupSetupDefaults.MaxCapacity,
            label = stringResource(Res.string.group_setup_capacity_label),
            modifier = Modifier.testTag(GroupSetupTags.Capacity),
        )
        Text(
            // "Mínimo de 2 jogadores." só serve para o piso. O teto (100) não tem
            // mensagem no catálogo e o stepper impede a tela de produzi-lo: estado
            // carregado acima de 100 fica vermelho com a dica normal, até existir string.
            text = when {
                hasError && capacity < GroupSetupDefaults.MinCapacity ->
                    stringResource(Res.string.group_setup_error_capacity_hint)

                isBeach -> stringResource(Res.string.group_setup_capacity_beach_hint)
                else -> stringResource(Res.string.group_setup_capacity_hint)
            },
            style = SaqzTheme.typography.support,
            color = if (hasError) SaqzTheme.colors.errorForeground else SaqzTheme.colors.textSecondary,
        )
    }
}

@Composable
internal fun GroupDurationSection(
    minutes: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    GroupFormCard(title = stringResource(Res.string.group_setup_duration_label), modifier = modifier) {
        GroupChoiceChipRow(
            values = GroupSetupDefaults.DurationOptions,
            selectedValue = minutes,
            label = { durationLabel(it) },
            onSelect = onSelect,
            modifier = Modifier.testTag(GroupSetupTags.Duration),
        )
    }
}

@Composable
internal fun GroupConfirmationLeadSection(
    minutes: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    GroupFormCard(
        title = stringResource(Res.string.group_setup_confirmation_lead_label),
        hint = stringResource(Res.string.group_setup_confirmation_lead_hint),
        modifier = modifier,
    ) {
        GroupChoiceChipRow(
            values = GroupSetupDefaults.ConfirmationLeadOptions,
            selectedValue = minutes,
            label = { confirmationLeadLabel(it) },
            onSelect = onSelect,
            modifier = Modifier.testTag(GroupSetupTags.ConfirmationLead),
        )
    }
}

@Composable
internal fun GroupVenueSection(
    name: String,
    address: String,
    nameError: String?,
    addressError: String?,
    onNameChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nameLabel = stringResource(Res.string.group_setup_venue_name_label)
    val addressLabel = stringResource(Res.string.group_setup_venue_address_label)
    GroupFormCard(title = nameLabel, modifier = modifier) {
        SaqzInput(
            value = TextFieldValue(name),
            onValueChange = { onNameChange(it.text) },
            label = nameLabel,
            showLabel = false,
            placeholder = stringResource(Res.string.group_setup_venue_name_hint),
            errorText = nameError,
            modifier = Modifier.testTag(GroupSetupTags.VenueName),
        )
        Text(
            text = addressLabel,
            style = SaqzTheme.typography.label,
            color = SaqzTheme.colors.textPrimary,
        )
        SaqzInput(
            value = TextFieldValue(address),
            onValueChange = { onAddressChange(it.text) },
            label = addressLabel,
            showLabel = false,
            placeholder = stringResource(Res.string.group_setup_venue_address_hint),
            errorText = addressError,
            leadingContent = { SaqzIcon(SaqzIcons.Pin, tint = SaqzTheme.colors.textSecondary) },
            modifier = Modifier.testTag(GroupSetupTags.VenueAddress),
        )
    }
}

@Composable
internal fun GroupRecurrenceCard(
    recurring: Boolean,
    slots: List<GroupRegularSlotForm>,
    hasError: Boolean,
    onRecurringChange: (Boolean) -> Unit,
    onAddSlot: () -> Unit,
    onRemoveSlot: (GroupRegularSlotForm) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    SaqzCard(
        modifier = modifier.then(
            // O `2g` pinta a borda do card em errorForeground com um anel de 3dp a 10%.
            // Desenhado por cima porque o SaqzCard pinta a própria borda cinza e não
            // aceita tom de erro — ver comentário no VUL-68.
            if (hasError) {
                Modifier.errorOutline(colors.errorForeground, metrics.cardRadius, metrics.subGrid)
            } else {
                Modifier
            },
        ),
    ) {
        GroupRecurrenceSection(
            recurring = recurring,
            slots = slots,
            onRecurringChange = onRecurringChange,
            onAddSlot = onAddSlot,
            onRemoveSlot = onRemoveSlot,
            modifier = Modifier.testTag(GroupSetupTags.Recurrence),
        )
        if (hasError) GroupSlotsErrorStrip()
    }
}

/**
 * Linha de seleção que abre um sheet — modalidade e categoria. Não é componente do
 * fluxo 10 do export, então nasce aqui, dentro da jornada que a usa.
 */
@Composable
private fun GroupSelectRow(
    value: String?,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val shape = RoundedCornerShape(metrics.inputRadius)
    val text = value ?: placeholder
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = metrics.buttonHeight)
            .clip(shape)
            .background(colors.surface, shape)
            .hairline(
                if (isError) colors.errorForeground else colors.border,
                metrics.inputRadius,
                metrics.subGrid / HairlineDivisor,
            )
            .clickable(onClickLabel = text, role = Role.Button, onClick = onClick)
            .padding(horizontal = metrics.horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        Text(
            text = text,
            style = SaqzTheme.typography.label,
            color = if (value == null) colors.textPlaceholder else colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        // O export usa uma seta para baixo; SaqzIcons ainda não tem ChevronDown.
        SaqzIcon(SaqzIcons.ChevronRight, tint = colors.textSecondary)
    }
}

/**
 * A mensagem de erro dos campos que não são `SaqzInput` — o input já traz a sua, no
 * mesmo tom e tamanho (VUL-60), e este é o eco dela para o seletor e o segmented.
 */
@Composable
private fun GroupFieldError(text: String?) {
    if (text == null) return
    Text(text, style = SaqzTheme.typography.support, color = SaqzTheme.colors.errorForeground)
}

/** Bloco ice de nota — o "não se aplica ao vôlei de areia" do `2b`. */
@Composable
private fun GroupNoteBlock(text: String, modifier: Modifier = Modifier) {
    val metrics = SaqzTheme.metrics
    Text(
        text = text,
        style = SaqzTheme.typography.support,
        color = SaqzTheme.colors.textSecondary,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(metrics.cardRadius))
            .background(SaqzTheme.colors.surfaceSoft)
            .padding(horizontal = metrics.blockGap, vertical = metrics.blockGap),
    )
}

/**
 * ponytail: o `GroupAddSlotButton` do VUL-66 só existe em azul, e o `2g` troca o botão
 * por uma faixa tracejada vermelha. Sem tocar em `ui/components/`, a faixa entra
 * **abaixo** do botão; um tom de erro no componente resolveria — está comentado no ticket.
 */
@Composable
private fun GroupSlotsErrorStrip(modifier: Modifier = Modifier) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val dash = metrics.grid
    val shape = RoundedCornerShape(metrics.cardRadius)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = metrics.minimumTouchTarget)
            .clip(shape)
            .background(colors.errorForeground.copy(alpha = ErrorSurfaceAlpha), shape)
            .drawBehind {
                drawRoundRect(
                    color = colors.errorForeground,
                    cornerRadius = CornerRadius(metrics.cardRadius.toPx()),
                    style = Stroke(
                        width = metrics.subGrid.toPx() / HairlineDivisor,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), dash.toPx())),
                    ),
                )
            }
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap)
            .testTag(GroupSetupTags.SlotsError),
        horizontalArrangement = Arrangement.spacedBy(metrics.grid, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SaqzIcon(SaqzIcons.Plus, tint = colors.errorForeground)
        Text(
            text = stringResource(Res.string.group_setup_error_slot),
            style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.Bold),
            color = colors.errorForeground,
        )
    }
}

private fun <T> List<T>.indexOfOrNone(value: T): Int =
    indexOf(value).takeIf { it >= 0 } ?: NoSegmentedSelection

/** Linha de 1px desenhada por trás — `border` cru gastaria um dp fora dos tokens. */
private fun Modifier.hairline(color: Color, radius: Dp, width: Dp) = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = width.toPx()),
    )
}

/** Borda de erro + anel de 3dp a 10%, desenhados por cima do card já pintado. */
private fun Modifier.errorOutline(color: Color, radius: Dp, ringWidth: Dp) = drawWithContent {
    drawContent()
    val corner = CornerRadius(radius.toPx())
    val line = ringWidth.toPx() / HairlineDivisor
    drawRoundRect(color = color, cornerRadius = corner, style = Stroke(width = line))
    val ring = ringWidth.toPx()
    drawRoundRect(
        color = color.copy(alpha = ErrorRingAlpha),
        topLeft = Offset(-ring / 2, -ring / 2),
        size = Size(size.width + ring, size.height + ring),
        cornerRadius = CornerRadius(radius.toPx() + ring / 2),
        style = Stroke(width = ring),
    )
}

private const val DescriptionMinLines = 3
