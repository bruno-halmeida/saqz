package br.com.saqz.groups.ui.setup

import androidx.compose.runtime.Composable
import br.com.saqz.groups.model.GroupComposition
import br.com.saqz.groups.model.GroupLevel
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.model.GroupPlayStyle
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_setup_composition_men
import br.com.saqz.groups.resources.group_setup_composition_mixed
import br.com.saqz.groups.resources.group_setup_composition_women
import br.com.saqz.groups.resources.group_setup_custom
import br.com.saqz.groups.resources.group_setup_level_advanced
import br.com.saqz.groups.resources.group_setup_level_beginner
import br.com.saqz.groups.resources.group_setup_level_intermediate
import br.com.saqz.groups.resources.group_setup_level_mixed
import br.com.saqz.groups.resources.group_setup_modality_beach
import br.com.saqz.groups.resources.group_setup_modality_court
import br.com.saqz.groups.resources.group_setup_modality_footvolley
import br.com.saqz.groups.resources.group_setup_style_five_one
import br.com.saqz.groups.resources.group_setup_style_four_two
import br.com.saqz.groups.resources.group_setup_style_six_zero
import br.com.saqz.groups.resources.group_setup_friday
import br.com.saqz.groups.resources.group_setup_monday
import br.com.saqz.groups.resources.group_setup_saturday
import br.com.saqz.groups.resources.group_setup_sunday
import br.com.saqz.groups.resources.group_setup_thursday
import br.com.saqz.groups.resources.group_setup_tuesday
import br.com.saqz.groups.resources.group_setup_wednesday
import br.com.saqz.groups.resources.groups_weekday_friday
import br.com.saqz.groups.resources.groups_weekday_monday
import br.com.saqz.groups.resources.groups_weekday_saturday
import br.com.saqz.groups.resources.groups_weekday_sunday
import br.com.saqz.groups.resources.groups_weekday_thursday
import br.com.saqz.groups.resources.groups_weekday_tuesday
import br.com.saqz.groups.resources.groups_weekday_wednesday
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun modalityLabel(value: GroupModality): String = stringResource(when (value) {
    GroupModality.COURT_VOLLEYBALL -> Res.string.group_setup_modality_court
    GroupModality.BEACH_VOLLEYBALL -> Res.string.group_setup_modality_beach
    GroupModality.FOOTVOLLEY -> Res.string.group_setup_modality_footvolley
})

@Composable
internal fun compositionLabel(value: GroupComposition): String = stringResource(when (value) {
    GroupComposition.WOMEN -> Res.string.group_setup_composition_women
    GroupComposition.MEN -> Res.string.group_setup_composition_men
    GroupComposition.MIXED -> Res.string.group_setup_composition_mixed
})

@Composable
internal fun levelLabel(value: GroupLevel): String = stringResource(when (value) {
    GroupLevel.BEGINNER -> Res.string.group_setup_level_beginner
    GroupLevel.INTERMEDIATE -> Res.string.group_setup_level_intermediate
    GroupLevel.ADVANCED -> Res.string.group_setup_level_advanced
    GroupLevel.MIXED_LEVELS -> Res.string.group_setup_level_mixed
    GroupLevel.CUSTOM -> Res.string.group_setup_custom
})

@Composable
internal fun playStyleLabel(value: GroupPlayStyle): String = stringResource(when (value) {
    GroupPlayStyle.SIX_ZERO -> Res.string.group_setup_style_six_zero
    GroupPlayStyle.FOUR_TWO -> Res.string.group_setup_style_four_two
    GroupPlayStyle.FIVE_ONE -> Res.string.group_setup_style_five_one
    GroupPlayStyle.CUSTOM -> Res.string.group_setup_custom
})

@Composable
internal fun weekdayLabel(name: String, compact: Boolean = false): String = stringResource(
    when (name) {
        "MONDAY" -> if (compact) Res.string.groups_weekday_monday else Res.string.group_setup_monday
        "TUESDAY" -> if (compact) Res.string.groups_weekday_tuesday else Res.string.group_setup_tuesday
        "WEDNESDAY" -> if (compact) Res.string.groups_weekday_wednesday else Res.string.group_setup_wednesday
        "THURSDAY" -> if (compact) Res.string.groups_weekday_thursday else Res.string.group_setup_thursday
        "FRIDAY" -> if (compact) Res.string.groups_weekday_friday else Res.string.group_setup_friday
        "SATURDAY" -> if (compact) Res.string.groups_weekday_saturday else Res.string.group_setup_saturday
        "SUNDAY" -> if (compact) Res.string.groups_weekday_sunday else Res.string.group_setup_sunday
        else -> error("Unknown weekday: $name")
    },
)
