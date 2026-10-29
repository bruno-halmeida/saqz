package br.com.saqz.groups.presentation.ui.setup

import androidx.compose.foundation.Image
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.saveable.rememberSaveable
import br.com.saqz.groups.presentation.GameFeeProblem
import br.com.saqz.groups.presentation.setup.GroupSetupMode
import br.com.saqz.groups.presentation.ui.components.GameFeeBlock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import br.com.saqz.core.common.formatting.formatBrlPlain
import br.com.saqz.core.common.formatting.parseBrlToCents
import br.com.saqz.core.common.formatting.sanitizeBrlInput
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzDashedAddButton
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzSegmented
import br.com.saqz.designsystem.SaqzStepper
import br.com.saqz.designsystem.SaqzTextLink
import br.com.saqz.designsystem.rememberSaqzDismissKeyboard
import br.com.saqz.designsystem.saqzInitials
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.model.GroupPlayStyle
import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.presentation.photo.GroupRemotePhotoRoot
import br.com.saqz.groups.presentation.setup.GroupSetupDefaults
import br.com.saqz.groups.presentation.setup.GroupSetupError
import br.com.saqz.groups.presentation.setup.GroupSetupIntent
import br.com.saqz.groups.presentation.setup.GroupSetupSheet
import br.com.saqz.groups.presentation.setup.GroupSetupState
import br.com.saqz.groups.presentation.ui.components.DurationStepper
import br.com.saqz.groups.presentation.ui.components.GroupSlotPill
import br.com.saqz.groups.presentation.ui.components.VenueSearchField
import br.com.saqz.groups.presentation.ui.label
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_setup_about_title
import br.com.saqz.groups.resources.group_setup_add_another_slot
import br.com.saqz.groups.resources.group_setup_add_slot
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
import br.com.saqz.groups.resources.group_setup_error_composition
import br.com.saqz.groups.resources.group_setup_error_custom_level
import br.com.saqz.groups.resources.group_setup_error_description
import br.com.saqz.groups.resources.group_setup_error_modality
import br.com.saqz.groups.resources.group_setup_error_name
import br.com.saqz.groups.resources.group_setup_error_venue_address
import br.com.saqz.groups.resources.group_setup_error_venue_name
import br.com.saqz.groups.resources.group_setup_games_no
import br.com.saqz.groups.resources.group_setup_games_title
import br.com.saqz.groups.resources.group_setup_games_yes
import br.com.saqz.groups.resources.group_setup_games_yes_hint
import br.com.saqz.groups.resources.group_setup_level_hint
import br.com.saqz.groups.resources.group_setup_level_label
import br.com.saqz.groups.resources.group_setup_modality_label
import br.com.saqz.groups.resources.group_setup_more_details
import br.com.saqz.groups.resources.group_setup_more_details_hint
import br.com.saqz.groups.resources.group_setup_name_hint
import br.com.saqz.groups.resources.group_setup_name_label
import br.com.saqz.groups.resources.group_setup_photo_add
import br.com.saqz.groups.resources.group_setup_photo_added
import br.com.saqz.groups.resources.group_setup_photo_change
import br.com.saqz.groups.resources.group_setup_photo_hint
import br.com.saqz.groups.resources.group_setup_photo_label
import br.com.saqz.groups.resources.group_setup_play_style_five_one_hint
import br.com.saqz.groups.resources.group_setup_play_style_label
import br.com.saqz.groups.resources.group_setup_games_no_hint
import br.com.saqz.groups.resources.group_setup_rules_title
import br.com.saqz.groups.resources.group_setup_slots_label
import br.com.saqz.groups.resources.group_setup_venue_address_hint
import br.com.saqz.groups.resources.group_setup_venue_address_label
import br.com.saqz.groups.resources.group_setup_venue_default_label
import br.com.saqz.groups.resources.group_setup_venue_name_hint
import br.com.saqz.groups.resources.group_setup_venue_name_label
import org.jetbrains.compose.resources.stringResource

private val ModalityOptions = GroupModality.entries

// Público na ordem do desenho (Masculino, Feminino, Misto), que não é a do enum.
private val CompositionOptions = listOf(
    br.com.saqz.groups.model.GroupComposition.MEN,
    br.com.saqz.groups.model.GroupComposition.WOMEN,
    br.com.saqz.groups.model.GroupComposition.MIXED,
)

// ponytail: o desenho tem só 6-0 e 5-1 no segmentado; 4-2 e personalizado existem no modelo.
private val PlayStyleOptions = listOf(GroupPlayStyle.SIX_ZERO, GroupPlayStyle.FIVE_ONE)

private const val DescriptionMinLines = 3
private const val PhotoCircleFactor = 2

/** Rótulo de campo: texto de rótulo com o ícone cinza à esquerda, só onde o desenho tem. */
@Composable
internal fun GroupFieldLabel(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
    ) {
        if (icon != null) SaqzIcon(icon, tint = SaqzTheme.colors.textSecondary)
        Text(text = text, style = SaqzTheme.typography.label, color = SaqzTheme.colors.textPrimary)
    }
}

/** Texto de apoio de uma linha sob o campo. */
@Composable
internal fun GroupFieldHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = SaqzTheme.typography.support,
        color = SaqzTheme.colors.textSecondary,
        modifier = modifier,
    )
}

@Composable
private fun GroupSetupState.errorText(error: GroupSetupError, text: String): String? =
    if (error in errors) text else null

/** Cartão "O grupo": nome, foto em linha, esporte (segmentado em pé) e público. */
@Composable
internal fun GroupBasicsCard(state: GroupSetupState, onIntent: (GroupSetupIntent) -> Unit, modifier: Modifier = Modifier) {
    val form = state.form
    val nameLabel = stringResource(Res.string.group_setup_name_label)
    SaqzCard(modifier = modifier) {
        GroupFieldLabel(nameLabel)
        SaqzInput(
            value = form.name,
            onValueChange = { onIntent(GroupSetupIntent.UpdateName(it)) },
            label = nameLabel,
            showLabel = false,
            placeholder = stringResource(Res.string.group_setup_name_hint),
            errorText = state.errorText(GroupSetupError.NameRequired, stringResource(Res.string.group_setup_error_name)),
            modifier = Modifier.testTag(GroupSetupTags.Name),
        )
        GroupPhotoRow(
            photoUrl = state.photoUrl,
            photo = state.photo,
            groupName = form.name,
            onPick = { onIntent(GroupSetupIntent.PickPhoto) },
        )
        SaqzDivider()
        GroupFieldLabel(stringResource(Res.string.group_setup_modality_label))
        SaqzSegmented(
            options = ModalityOptions.map { it.label() },
            selected = ModalityOptions.indexOfOrNone(form.modality),
            onSelect = { onIntent(GroupSetupIntent.SelectModality(ModalityOptions[it])) },
            vertical = true,
            optionTags = ModalityOptions.map(GroupSetupTags::modalityOption),
            modifier = Modifier.testTag(GroupSetupTags.Modality),
        )
        GroupFieldError(state.errorText(GroupSetupError.ModalityRequired, stringResource(Res.string.group_setup_error_modality)))
        Column {
            GroupFieldLabel(stringResource(Res.string.group_setup_composition_label))
            GroupFieldHint(stringResource(Res.string.group_setup_composition_hint))
        }
        SaqzSegmented(
            options = CompositionOptions.map { it.label() },
            // Coagir para 0 faria a tela afirmar "Masculino" sobre um valor que não existe.
            selected = CompositionOptions.indexOfOrNone(form.composition),
            onSelect = { onIntent(GroupSetupIntent.SelectComposition(CompositionOptions[it])) },
            modifier = Modifier.testTag(GroupSetupTags.Composition),
        )
        GroupFieldError(
            state.errorText(GroupSetupError.CompositionRequired, stringResource(Res.string.group_setup_error_composition)),
        )
    }
}

/** A foto como linha: círculo com a câmera (ou a miniatura), título, apoio e a ação de texto. */
@Composable
private fun GroupPhotoRow(
    photoUrl: String?,
    photo: ImageBitmap?,
    groupName: String,
    onPick: () -> Unit,
) {
    val metrics = SaqzTheme.metrics
    val colors = SaqzTheme.colors
    val label = stringResource(Res.string.group_setup_photo_label)
    val hasPhoto = photoUrl != null || photo != null
    val dismissKeyboard = rememberSaqzDismissKeyboard()
    val pick = {
        dismissKeyboard()
        onPick()
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(metrics.sectionGap * PhotoCircleFactor)
                .clip(CircleShape)
                .background(if (hasPhoto && photo == null) colors.primary else colors.surfaceSoft)
                .clickable(onClickLabel = label, role = Role.Button, onClick = pick)
                .testTag(GroupSetupTags.Photo),
            contentAlignment = Alignment.Center,
        ) {
            when {
                photo != null -> Image(
                    bitmap = photo,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> GroupRemotePhotoRoot(photoUrl = photoUrl, modifier = Modifier.fillMaxSize()) {
                    if (photoUrl == null) {
                        SaqzIcon(SaqzIcons.Camera, tint = colors.primary)
                    } else {
                        Text(
                            text = saqzInitials(groupName),
                            style = SaqzTheme.typography.label.copy(fontWeight = FontWeight.ExtraBold),
                            color = colors.onPrimary,
                        )
                    }
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = SaqzTheme.typography.label, color = colors.textPrimary)
            GroupFieldHint(
                stringResource(if (hasPhoto) Res.string.group_setup_photo_added else Res.string.group_setup_photo_hint),
            )
        }
        SaqzTextLink(
            label = stringResource(if (hasPhoto) Res.string.group_setup_photo_change else Res.string.group_setup_photo_add),
            onClick = pick,
            touchTarget = true,
        )
    }
}

/** Cartão "Jogos": a pergunta do dia fixo e, com "Sim", dias, horários e quadra. */
@Composable
internal fun GroupGamesCard(state: GroupSetupState, onIntent: (GroupSetupIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val hasSlotsError = GroupSetupError.SlotsRequired in state.errors
    SaqzCard(modifier = modifier) {
        Text(
            text = stringResource(Res.string.group_setup_games_title),
            style = SaqzTheme.typography.label,
            color = colors.textPrimary,
        )
        SaqzSegmented(
            options = listOf(stringResource(Res.string.group_setup_games_yes), stringResource(Res.string.group_setup_games_no)),
            selected = if (state.recurring) 0 else 1,
            onSelect = { onIntent(GroupSetupIntent.ToggleRecurring(it == 0)) },
            modifier = Modifier.testTag(GroupSetupTags.Recurrence),
        )
        GroupFieldHint(
            stringResource(if (state.recurring) Res.string.group_setup_games_yes_hint else Res.string.group_setup_games_no_hint),
        )
        if (state.recurring) {
            Column(
                modifier = Modifier.fillMaxWidth().then(
                    if (hasSlotsError) Modifier.errorOutline(colors.errorForeground, metrics.cardRadius, metrics.subGrid) else Modifier,
                ),
                verticalArrangement = Arrangement.spacedBy(metrics.grid),
            ) {
                GroupFieldLabel(stringResource(Res.string.group_setup_slots_label), icon = SaqzIcons.Calendar)
                state.form.regularSlots.forEachIndexed { index, slot ->
                    GroupSlotPill(
                        slot = slot,
                        onEdit = { onIntent(GroupSetupIntent.OpenSheet(GroupSetupSheet.Slot(index = index))) },
                        onRemove = { onIntent(GroupSetupIntent.RemoveSlot(slot)) },
                    )
                }
                SaqzDashedAddButton(
                    label = stringResource(
                        if (state.form.regularSlots.isEmpty()) Res.string.group_setup_add_slot else Res.string.group_setup_add_another_slot,
                    ),
                    onClick = { onIntent(GroupSetupIntent.OpenSheet(GroupSetupSheet.Slot(index = null))) },
                )
                if (hasSlotsError) GroupSlotsErrorStrip()
            }
            GroupVenueFields(state, onIntent, nameLabel = stringResource(Res.string.group_setup_venue_name_label), pinIcon = true)
        }
    }
}

/** Quadra e endereço (opcional): nos "Jogos" com dia fixo, ou em "Regras dos jogos" como quadra padrão. */
@Composable
internal fun GroupVenueFields(
    state: GroupSetupState,
    onIntent: (GroupSetupIntent) -> Unit,
    nameLabel: String,
    pinIcon: Boolean,
    modifier: Modifier = Modifier,
) {
    val addressLabel = stringResource(Res.string.group_setup_venue_address_label)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
        GroupFieldLabel(nameLabel, icon = if (pinIcon) SaqzIcons.Pin else null)
        VenueSearchField(
            suggestions = state.venueSearch.suggestions,
            onSelect = { onIntent(GroupSetupIntent.SelectPlaceSuggestion(it)) },
            onDismiss = { onIntent(GroupSetupIntent.DismissPlaceSuggestions) },
        ) {
            SaqzInput(
                value = state.form.defaultVenue?.name.orEmpty(),
                onValueChange = { onIntent(GroupSetupIntent.UpdateVenueName(it)) },
                label = nameLabel,
                showLabel = false,
                placeholder = stringResource(Res.string.group_setup_venue_name_hint),
                errorText = state.errorText(GroupSetupError.VenueNameRequired, stringResource(Res.string.group_setup_error_venue_name)),
                modifier = Modifier.testTag(GroupSetupTags.VenueName),
            )
        }
        GroupFieldLabel(addressLabel)
        SaqzInput(
            value = state.form.defaultVenue?.address.orEmpty(),
            onValueChange = { onIntent(GroupSetupIntent.UpdateVenueAddress(it)) },
            label = addressLabel,
            showLabel = false,
            placeholder = stringResource(Res.string.group_setup_venue_address_hint),
            errorText = state.errorText(
                GroupSetupError.VenueAddressNotFound,
                stringResource(Res.string.group_setup_error_venue_address),
            ),
            modifier = Modifier.testTag(GroupSetupTags.VenueAddress),
        )
    }
}

/** A linha "Mais detalhes (opcional)": título e apoio fechados; título e seta para cima abertos. */
@Composable
internal fun GroupMoreDetailsRow(expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val metrics = SaqzTheme.metrics
    val title = stringResource(Res.string.group_setup_more_details)
    SaqzCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = metrics.minimumTouchTarget)
                .clickable(onClickLabel = title, role = Role.Button, onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = SaqzTheme.typography.label, color = SaqzTheme.colors.textPrimary)
                if (!expanded) GroupFieldHint(stringResource(Res.string.group_setup_more_details_hint))
            }
            SaqzIcon(if (expanded) SaqzIcons.ChevronUp else SaqzIcons.ChevronDown, tint = SaqzTheme.colors.textSecondary)
        }
    }
}

/** "Sobre o grupo": nível, sistema de jogo (só quadra) e descrição. */
@Composable
internal fun GroupAboutSection(state: GroupSetupState, onIntent: (GroupSetupIntent) -> Unit, modifier: Modifier = Modifier) {
    val form = state.form
    val levelLabel = stringResource(Res.string.group_setup_level_label)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
        SaqzSectionHeader(title = stringResource(Res.string.group_setup_about_title))
        SaqzCard {
            GroupFieldLabel(levelLabel)
            GroupSelectRow(
                value = form.level?.label(),
                placeholder = stringResource(Res.string.group_setup_level_hint),
                onClick = { onIntent(GroupSetupIntent.OpenSheet(GroupSetupSheet.Level)) },
                modifier = Modifier.testTag(GroupSetupTags.Level),
            )
            if (state.showsCustomLevel) {
                SaqzInput(
                    value = form.customLevel.orEmpty(),
                    onValueChange = { onIntent(GroupSetupIntent.UpdateCustomLevel(it)) },
                    label = levelLabel,
                    showLabel = false,
                    placeholder = stringResource(Res.string.group_setup_custom_level_hint),
                    errorText = state.errorText(
                        GroupSetupError.CustomLevelRequired,
                        stringResource(Res.string.group_setup_error_custom_level),
                    ),
                    modifier = Modifier.testTag(GroupSetupTags.CustomLevel),
                )
            }
            if (state.showsPlayStyle) {
                GroupFieldLabel(stringResource(Res.string.group_setup_play_style_label))
                SaqzSegmented(
                    options = PlayStyleOptions.map { it.label() },
                    // 4-2 e personalizado existem no modelo e não no trilho: sem marca, para não gravar mentira.
                    selected = PlayStyleOptions.indexOfOrNone(form.playStyle),
                    onSelect = { onIntent(GroupSetupIntent.SelectPlayStyle(PlayStyleOptions[it])) },
                    modifier = Modifier.testTag(GroupSetupTags.PlayStyle),
                )
                if (form.playStyle == GroupPlayStyle.FIVE_ONE) {
                    GroupFieldHint(stringResource(Res.string.group_setup_play_style_five_one_hint))
                }
            }
            val descriptionLabel = stringResource(Res.string.group_setup_description_label)
            GroupFieldLabel(descriptionLabel)
            SaqzInput(
                value = form.description.orEmpty(),
                onValueChange = { onIntent(GroupSetupIntent.UpdateDescription(it)) },
                label = descriptionLabel,
                showLabel = false,
                placeholder = stringResource(Res.string.group_setup_description_hint),
                errorText = state.errorText(
                    GroupSetupError.DescriptionTooShort,
                    stringResource(Res.string.group_setup_error_description),
                ),
                singleLine = false,
                minLines = DescriptionMinLines,
                modifier = Modifier.testTag(GroupSetupTags.Description),
            )
        }
    }
}

/**
 * "Regras dos jogos": vagas, painel do 5-1, valor, duração e prioridade dos mensalistas (esta só na criação: na edição o
 * mesmo valor vive em "Como preencher as vagas"). Sem dia fixo, a quadra padrão fica aqui.
 */
@Composable
internal fun GroupRulesSection(
    state: GroupSetupState,
    onIntent: (GroupSetupIntent) -> Unit,
    modifier: Modifier = Modifier,
    feePreview: (@Composable (Long?) -> Unit)? = null,
) {
    val form = state.form
    val capacity = form.defaultCapacity ?: GroupSetupDefaults.Capacity
    val capacityError = GroupSetupError.CapacityOutOfRange in state.errors
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
        SaqzSectionHeader(title = stringResource(Res.string.group_setup_rules_title))
        SaqzCard {
            Column {
                SaqzStepper(
                    value = capacity,
                    onValueChange = { onIntent(GroupSetupIntent.UpdateCapacity(it)) },
                    // O teto é do backend (`4..100`): entra como limite de entrada, não como erro depois do envio.
                    min = GroupSetupDefaults.MinCapacity,
                    max = GroupSetupDefaults.MaxCapacity,
                    label = stringResource(Res.string.group_setup_capacity_label),
                    icon = SaqzIcons.Users,
                    labelStyle = SaqzTheme.typography.label,
                    modifier = Modifier.testTag(GroupSetupTags.Capacity),
                )
                Text(
                    text = if (capacityError && capacity < GroupSetupDefaults.MinCapacity) {
                        stringResource(Res.string.group_setup_error_capacity_hint)
                    } else {
                        stringResource(Res.string.group_setup_capacity_hint)
                    },
                    style = SaqzTheme.typography.support,
                    color = if (capacityError) SaqzTheme.colors.errorForeground else SaqzTheme.colors.textSecondary,
                )
            }
            if (state.showsPositionSlots) GroupPositionSlotsSection(state = state, onIntent = onIntent)
            GroupGameFeeField(state = state, onIntent = onIntent)
            feePreview?.invoke(form.defaultGameFeeCents)
            DurationStepper(
                minutes = state.durationMinutes,
                onChange = { onIntent(GroupSetupIntent.SelectDuration(it)) },
                label = stringResource(Res.string.group_setup_duration_label),
                icon = SaqzIcons.Clock,
                labelStyle = SaqzTheme.typography.label,
                hasError = GroupSetupError.DurationOutOfRange in state.errors,
                modifier = Modifier.testTag(GroupSetupTags.Duration),
            )
            if (!state.isEditing) {
                GroupConfirmationLeadField(
                    minutes = form.defaultConfirmationLeadMinutes,
                    onSelect = { onIntent(GroupSetupIntent.SelectConfirmationLead(it)) },
                    title = stringResource(Res.string.group_setup_confirmation_lead_label),
                    hint = stringResource(Res.string.group_setup_confirmation_lead_hint),
                    tag = GroupSetupTags.ConfirmationLead,
                    icon = SaqzIcons.Star,
                )
            }
            if (!state.recurring) {
                GroupVenueFields(
                    state = state,
                    onIntent = onIntent,
                    nameLabel = stringResource(Res.string.group_setup_venue_default_label),
                    pinIcon = false,
                )
            }
        }
    }
}

/**
 * DEB-146: o bloco "Valor por jogo" (Cobrar diária · Sem diária) de Criar e Editar grupo. Reais digitados com "R$"
 * dentro do campo; o formulário recebe centavos. Aberto pelo cartão ou pelo aviso do grupo sem valor, a tela rola
 * até aqui uma vez.
 */
@Composable
internal fun GroupGameFeeField(state: GroupSetupState, onIntent: (GroupSetupIntent) -> Unit, modifier: Modifier = Modifier) {
    val cents = state.form.defaultGameFeeCents
    var text by remember { mutableStateOf(cents?.let(::formatBrlPlain).orEmpty()) }
    // Valor vindo de fora (carregamento, restauração, sugestão) substitui o texto; a digitação em curso não.
    LaunchedEffect(cents) {
        if (parseBrlToCents(text) != cents) text = cents?.let(::formatBrlPlain).orEmpty()
    }
    val requester = remember { BringIntoViewRequester() }
    var focused by rememberSaveable { mutableStateOf(false) }
    val focus = (state.mode as? GroupSetupMode.Edit)?.focusGameFee == true
    LaunchedEffect(focus, state.isLoading) {
        if (focus && !state.isLoading && !focused) {
            focused = true
            requester.bringIntoView()
        }
    }
    GameFeeBlock(
        choice = state.gameFeeChoice,
        feeText = text,
        onChoice = { onIntent(GroupSetupIntent.SelectGameFeeChoice(it)) },
        onFeeTextChange = { raw ->
            text = sanitizeBrlInput(raw)
            onIntent(GroupSetupIntent.UpdateDefaultGameFee(parseBrlToCents(text)))
        },
        problem = when {
            GroupSetupError.GameFeeInvalid in state.errors -> GameFeeProblem.Invalid
            else -> null
        },
        // Em Criar grupo o valor já vem preenchido com a conta; a sugestão no apoio é das outras telas.
        monthlyFeeCents = state.form.monthlyFeeCents.takeIf { state.isEditing },
        modifier = modifier.bringIntoViewRequester(requester).testTag(GroupSetupTags.GameFee),
    )
}

/** Prioridade dos mensalistas: segmentado 3h · 6h · 12h · 24h. Valor salvo fora dessas quatro fica sem marca. */
@Composable
internal fun GroupConfirmationLeadField(
    minutes: Int?,
    onSelect: (Int) -> Unit,
    title: String,
    hint: String,
    tag: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val options = GroupSetupDefaults.ConfirmationLeadOptions
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid)) {
        Column {
            GroupFieldLabel(title, icon = icon)
            GroupFieldHint(hint)
        }
        SaqzSegmented(
            options = options.map { "${it / MinutesPerHour}h" },
            selected = options.indexOfOrNone(minutes),
            onSelect = { onSelect(options[it]) },
            optionTags = options.map { "$tag-$it" },
            modifier = Modifier.testTag(tag),
        )
    }
}

private const val MinutesPerHour = 60
