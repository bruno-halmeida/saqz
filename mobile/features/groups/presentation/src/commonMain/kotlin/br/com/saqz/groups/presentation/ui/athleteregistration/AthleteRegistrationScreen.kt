package br.com.saqz.groups.presentation.ui.athleteregistration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzChoiceChip
import br.com.saqz.designsystem.SaqzEmptyState
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.SaqzInputKind
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.athlete.AthleteLevel
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.athlete.AthletePreferredSide
import br.com.saqz.groups.domain.group.GroupComposition
import br.com.saqz.groups.domain.group.GroupModality
import br.com.saqz.groups.presentation.athleteregistration.AthleteRegistrationIntent
import br.com.saqz.groups.presentation.athleteregistration.AthleteRegistrationState
import br.com.saqz.groups.presentation.ui.components.GroupFormCard
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.athlete_registration_beach_lead
import br.com.saqz.groups.resources.athlete_registration_city_unknown
import br.com.saqz.groups.resources.athlete_registration_court_position
import br.com.saqz.groups.resources.athlete_registration_height
import br.com.saqz.groups.resources.athlete_registration_height_hint
import br.com.saqz.groups.resources.athlete_registration_identity_title
import br.com.saqz.groups.resources.athlete_registration_joined_subtitle
import br.com.saqz.groups.resources.athlete_registration_joined_title
import br.com.saqz.groups.resources.athlete_registration_lead
import br.com.saqz.groups.resources.athlete_registration_level
import br.com.saqz.groups.resources.athlete_registration_level_advanced
import br.com.saqz.groups.resources.athlete_registration_level_beginner
import br.com.saqz.groups.resources.athlete_registration_level_intermediate
import br.com.saqz.groups.resources.athlete_registration_load_error_body
import br.com.saqz.groups.resources.athlete_registration_load_error_title
import br.com.saqz.groups.resources.athlete_registration_modality_beach
import br.com.saqz.groups.resources.athlete_registration_modality_court
import br.com.saqz.groups.resources.athlete_registration_modality_footvolley
import br.com.saqz.groups.resources.athlete_registration_nickname_hint
import br.com.saqz.groups.resources.athlete_registration_position_libero
import br.com.saqz.groups.resources.athlete_registration_position_middle
import br.com.saqz.groups.resources.athlete_registration_position_opposite_female
import br.com.saqz.groups.resources.athlete_registration_position_opposite_male
import br.com.saqz.groups.resources.athlete_registration_position_outside_female
import br.com.saqz.groups.resources.athlete_registration_position_outside_male
import br.com.saqz.groups.resources.athlete_registration_position_setter_female
import br.com.saqz.groups.resources.athlete_registration_position_setter_male
import br.com.saqz.groups.resources.athlete_registration_retry
import br.com.saqz.groups.resources.athlete_registration_save
import br.com.saqz.groups.resources.athlete_registration_save_error
import br.com.saqz.groups.resources.athlete_registration_secondary_hint
import br.com.saqz.groups.resources.athlete_registration_secondary_position
import br.com.saqz.groups.resources.athlete_registration_side
import br.com.saqz.groups.resources.athlete_registration_side_both
import br.com.saqz.groups.resources.athlete_registration_side_hint
import br.com.saqz.groups.resources.athlete_registration_side_left
import br.com.saqz.groups.resources.athlete_registration_side_right
import br.com.saqz.groups.resources.athlete_registration_summary
import br.com.saqz.groups.resources.athlete_registration_summary_secondary
import br.com.saqz.groups.resources.athlete_registration_title
import br.com.saqz.groups.resources.athlete_registration_undefined_level
import br.com.saqz.groups.resources.athlete_registration_undefined_position
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

object AthleteRegistrationTags {
    const val Screen = "athlete-registration-screen"
    const val Nickname = "athlete-registration-nickname"
    const val Position = "athlete-registration-position"
    const val SecondaryPosition = "athlete-registration-secondary-position"
    const val Level = "athlete-registration-level"
    const val Height = "athlete-registration-height"
    const val Side = "athlete-registration-side"
    const val Summary = "athlete-registration-summary"
    const val Error = "athlete-registration-error"
    const val LoadFailure = "athlete-registration-load-failure"
    const val Save = "athlete-registration-save"

    fun position(value: AthletePosition) = "$Position-${value.name}"
    fun secondary(value: AthletePosition) = "$SecondaryPosition-${value.name}"
    fun level(value: AthleteLevel) = "$Level-${value.name}"
    fun side(value: AthletePreferredSide) = "$Side-${value.name}"
}

@Composable
fun AthleteRegistrationScreen(
    state: AthleteRegistrationState,
    onIntent: (AthleteRegistrationIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SaqzTheme.colors.background)
            .imePadding(),
    ) {
        SaqzTopAppBar(title = stringResource(Res.string.athlete_registration_title), onBack = onBack)
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SaqzSpinner()
            }
            state.loadFailed -> AthleteRegistrationLoadFailure(
                onRetry = { onIntent(AthleteRegistrationIntent.Retry) },
                modifier = Modifier.testTag(AthleteRegistrationTags.LoadFailure),
            )
            else -> Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap)
                    .testTag(AthleteRegistrationTags.Screen),
                verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
            ) {
                AthleteRegistrationHeader(state)
                if (state.error != null) {
                    AthleteRegistrationError(modifier = Modifier.testTag(AthleteRegistrationTags.Error))
                }
                AthleteIdentitySection(state, onIntent)
                if (state.isCourt) {
                    AthleteCourtSections(state, onIntent)
                } else {
                    AthleteBeachSection(state, onIntent)
                }
                if (state.isCourt) {
                    AthleteSummary(state)
                }
                SaqzButton(
                    label = stringResource(Res.string.athlete_registration_save),
                    onClick = { onIntent(AthleteRegistrationIntent.Save) },
                    fullWidth = true,
                    enabled = !state.isSaving,
                    loading = state.isSaving,
                    modifier = Modifier.testTag(AthleteRegistrationTags.Save),
                )
                Spacer(Modifier.size(metrics.blockGap))
            }
        }
    }
}

@Composable
private fun AthleteRegistrationHeader(state: AthleteRegistrationState) {
    val modality = when (state.modality) {
        GroupModality.COURT_VOLLEYBALL -> stringResource(Res.string.athlete_registration_modality_court)
        GroupModality.BEACH_VOLLEYBALL -> stringResource(Res.string.athlete_registration_modality_beach)
        GroupModality.FOOTVOLLEY -> stringResource(Res.string.athlete_registration_modality_footvolley)
        null -> ""
    }
    val city = state.city.ifBlank { stringResource(Res.string.athlete_registration_city_unknown) }
    Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
        Text(
            text = stringResource(Res.string.athlete_registration_joined_title, state.groupName),
            style = SaqzTheme.typography.title,
            color = SaqzTheme.colors.textPrimary,
        )
        Text(
            text = stringResource(Res.string.athlete_registration_joined_subtitle, modality, city),
            style = SaqzTheme.typography.label,
            color = SaqzTheme.colors.primary,
        )
        Text(
            text = stringResource(Res.string.athlete_registration_lead),
            style = SaqzTheme.typography.body,
            color = SaqzTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun AthleteIdentitySection(
    state: AthleteRegistrationState,
    onIntent: (AthleteRegistrationIntent) -> Unit,
) {
    GroupFormCard(title = stringResource(Res.string.athlete_registration_identity_title)) {
        SaqzInput(
            value = state.nickname,
            onValueChange = { onIntent(AthleteRegistrationIntent.NicknameChanged(it)) },
            label = stringResource(Res.string.athlete_registration_identity_title),
            placeholder = state.displayName,
            showLabel = false,
            helperText = stringResource(Res.string.athlete_registration_nickname_hint),
            modifier = Modifier.testTag(AthleteRegistrationTags.Nickname),
        )
    }
}

@Composable
private fun AthleteCourtSections(
    state: AthleteRegistrationState,
    onIntent: (AthleteRegistrationIntent) -> Unit,
) {
    GroupFormCard(title = stringResource(Res.string.athlete_registration_court_position)) {
        PositionChips(
            state = state,
            onSelect = { onIntent(AthleteRegistrationIntent.PositionSelected(it)) },
            modifier = Modifier.testTag(AthleteRegistrationTags.Position),
        )
    }
    GroupFormCard(
        title = stringResource(Res.string.athlete_registration_secondary_position),
        hint = stringResource(Res.string.athlete_registration_secondary_hint),
    ) {
        PositionChips(
            state = state,
            secondary = true,
            onSelect = { onIntent(AthleteRegistrationIntent.SecondaryPositionSelected(it)) },
            modifier = Modifier.testTag(AthleteRegistrationTags.SecondaryPosition),
        )
    }
    GroupFormCard(title = stringResource(Res.string.athlete_registration_level)) {
        LevelChips(
            selected = state.level,
            onSelect = { onIntent(AthleteRegistrationIntent.LevelSelected(it)) },
            modifier = Modifier.testTag(AthleteRegistrationTags.Level),
        )
    }
    GroupFormCard(
        title = stringResource(Res.string.athlete_registration_height),
        hint = stringResource(Res.string.athlete_registration_height_hint),
    ) {
        SaqzInput(
            value = state.heightText,
            onValueChange = { onIntent(AthleteRegistrationIntent.HeightChanged(it)) },
            label = stringResource(Res.string.athlete_registration_height),
            showLabel = false,
            kind = SaqzInputKind.Text,
            keyboardType = KeyboardType.Number,
            modifier = Modifier.testTag(AthleteRegistrationTags.Height),
        )
    }
}

@Composable
private fun AthleteBeachSection(
    state: AthleteRegistrationState,
    onIntent: (AthleteRegistrationIntent) -> Unit,
) {
    Text(
        text = stringResource(Res.string.athlete_registration_beach_lead),
        style = SaqzTheme.typography.body,
        color = SaqzTheme.colors.textSecondary,
    )
    GroupFormCard(
        title = stringResource(Res.string.athlete_registration_side),
        hint = stringResource(Res.string.athlete_registration_side_hint),
    ) {
        SideChips(
            selected = state.preferredSide,
            onSelect = { onIntent(AthleteRegistrationIntent.PreferredSideSelected(it)) },
            modifier = Modifier.testTag(AthleteRegistrationTags.Side),
        )
    }
    GroupFormCard(title = stringResource(Res.string.athlete_registration_level)) {
        LevelChips(
            selected = state.level,
            onSelect = { onIntent(AthleteRegistrationIntent.LevelSelected(it)) },
            modifier = Modifier.testTag(AthleteRegistrationTags.Level),
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PositionChips(
    state: AthleteRegistrationState,
    secondary: Boolean = false,
    onSelect: (AthletePosition) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = AthletePosition.entries
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
    ) {
        options.forEach { position ->
            val selected = if (secondary) state.secondaryPosition == position else state.position == position
            val disabled = secondary && state.position == position
            if (!disabled) {
                SaqzChoiceChip(
                    label = stringResource(positionLabel(position, state.composition)),
                    selected = selected,
                    onClick = { onSelect(position) },
                    modifier = Modifier.testTag(
                        if (secondary) AthleteRegistrationTags.secondary(position)
                        else AthleteRegistrationTags.position(position),
                    ),
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun LevelChips(
    selected: AthleteLevel?,
    onSelect: (AthleteLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
    ) {
        AthleteLevel.entries.forEach { level ->
            SaqzChoiceChip(
                label = stringResource(levelLabel(level)),
                selected = selected == level,
                onClick = { onSelect(level) },
                modifier = Modifier.testTag(AthleteRegistrationTags.level(level)),
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SideChips(
    selected: AthletePreferredSide?,
    onSelect: (AthletePreferredSide) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
    ) {
        AthletePreferredSide.entries.forEach { side ->
            SaqzChoiceChip(
                label = stringResource(sideLabel(side)),
                selected = selected == side,
                onClick = { onSelect(side) },
                modifier = Modifier.testTag(AthleteRegistrationTags.side(side)),
            )
        }
    }
}

@Composable
private fun AthleteSummary(state: AthleteRegistrationState) {
    val position = state.position?.let { stringResource(positionLabel(it, state.composition)) }
        ?: stringResource(Res.string.athlete_registration_undefined_position)
    val level = state.level?.let { stringResource(levelLabel(it)).lowercase() }
        ?: stringResource(Res.string.athlete_registration_undefined_level)
    val summary = if (state.secondaryPosition != null) {
        stringResource(
            Res.string.athlete_registration_summary_secondary,
            position,
            stringResource(positionLabel(state.secondaryPosition, state.composition)).lowercase(),
            level,
        )
    } else {
        stringResource(Res.string.athlete_registration_summary, position, level)
    }
    SaqzCard(
        modifier = Modifier.testTag(AthleteRegistrationTags.Summary),
        tone = br.com.saqz.designsystem.SaqzCardTone.Soft,
    ) {
        Text(
            text = summary,
            style = SaqzTheme.typography.body.copy(fontWeight = FontWeight(600)),
            color = SaqzTheme.colors.textPrimary,
        )
    }
}

@Composable
private fun AthleteRegistrationError(modifier: Modifier = Modifier) {
    SaqzCard(modifier = modifier) {
        Text(
            text = stringResource(Res.string.athlete_registration_save_error),
            style = SaqzTheme.typography.support,
            color = SaqzTheme.colors.errorForeground,
        )
    }
}

@Composable
private fun AthleteRegistrationLoadFailure(modifier: Modifier = Modifier, onRetry: () -> Unit) {
    SaqzEmptyState(
        title = stringResource(Res.string.athlete_registration_load_error_title),
        description = stringResource(Res.string.athlete_registration_load_error_body),
        icon = SaqzIcons.CircleAlert,
        action = stringResource(Res.string.athlete_registration_retry),
        onAction = onRetry,
        modifier = modifier.fillMaxSize(),
    )
}

private fun positionLabel(
    position: AthletePosition,
    composition: GroupComposition?,
): StringResource = when (position) {
    AthletePosition.LEVANTADOR -> if (composition == GroupComposition.WOMEN) {
        Res.string.athlete_registration_position_setter_female
    } else {
        Res.string.athlete_registration_position_setter_male
    }
    AthletePosition.PONTA -> if (composition == GroupComposition.WOMEN) {
        Res.string.athlete_registration_position_outside_female
    } else {
        Res.string.athlete_registration_position_outside_male
    }
    AthletePosition.CENTRAL -> Res.string.athlete_registration_position_middle
    AthletePosition.OPOSTO -> if (composition == GroupComposition.WOMEN) {
        Res.string.athlete_registration_position_opposite_female
    } else {
        Res.string.athlete_registration_position_opposite_male
    }
    AthletePosition.LIBERO -> Res.string.athlete_registration_position_libero
}

private fun levelLabel(level: AthleteLevel): StringResource = when (level) {
    AthleteLevel.INICIANTE -> Res.string.athlete_registration_level_beginner
    AthleteLevel.INTERMEDIARIO -> Res.string.athlete_registration_level_intermediate
    AthleteLevel.AVANCADO -> Res.string.athlete_registration_level_advanced
}

private fun sideLabel(side: AthletePreferredSide): StringResource = when (side) {
    AthletePreferredSide.DIREITA -> Res.string.athlete_registration_side_right
    AthletePreferredSide.ESQUERDA -> Res.string.athlete_registration_side_left
    AthletePreferredSide.TANTO_FAZ -> Res.string.athlete_registration_side_both
}

internal object AthleteRegistrationSamples {
    val womenCourt = AthleteRegistrationState(
        isLoading = false,
        groupName = "Vôlei do CERET",
        modality = GroupModality.COURT_VOLLEYBALL,
        composition = GroupComposition.WOMEN,
        city = "São Paulo",
        displayName = "Bruno Almeida",
        nickname = "Bruninho",
        position = AthletePosition.LEVANTADOR,
        secondaryPosition = AthletePosition.PONTA,
        level = AthleteLevel.INTERMEDIARIO,
        heightText = "184",
    )

    val menCourt = womenCourt.copy(
        composition = GroupComposition.MEN,
        position = AthletePosition.PONTA,
        secondaryPosition = AthletePosition.OPOSTO,
    )

    val mixedCourt = womenCourt.copy(
        composition = GroupComposition.MIXED,
        position = AthletePosition.CENTRAL,
        secondaryPosition = AthletePosition.LIBERO,
    )

    val beach = AthleteRegistrationState(
        isLoading = false,
        groupName = "Areia do Ibira",
        modality = GroupModality.BEACH_VOLLEYBALL,
        composition = GroupComposition.MIXED,
        city = "São Paulo",
        displayName = "Bruno Almeida",
        nickname = "Bruninho",
        level = AthleteLevel.INTERMEDIARIO,
        preferredSide = AthletePreferredSide.DIREITA,
    )
}

@Preview(name = "3j — feminino", widthDp = 390, heightDp = 844)
@Composable
private fun AthleteRegistrationWomenPreview() = SaqzTheme {
    AthleteRegistrationScreen(AthleteRegistrationSamples.womenCourt, {}, {})
}

@Preview(name = "3j — masculino", widthDp = 390, heightDp = 844)
@Composable
private fun AthleteRegistrationMenPreview() = SaqzTheme {
    AthleteRegistrationScreen(AthleteRegistrationSamples.menCourt, {}, {})
}

@Preview(name = "3j — misto", widthDp = 390, heightDp = 844)
@Composable
private fun AthleteRegistrationMixedPreview() = SaqzTheme {
    AthleteRegistrationScreen(AthleteRegistrationSamples.mixedCourt, {}, {})
}

@Preview(name = "3k — areia", widthDp = 390, heightDp = 844)
@Composable
private fun AthleteRegistrationBeachPreview() = SaqzTheme {
    AthleteRegistrationScreen(AthleteRegistrationSamples.beach, {}, {})
}
