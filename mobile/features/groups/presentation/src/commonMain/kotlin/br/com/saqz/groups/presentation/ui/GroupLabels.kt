package br.com.saqz.groups.presentation.ui

import androidx.compose.runtime.Composable
import br.com.saqz.groups.model.GroupComposition
import br.com.saqz.groups.model.GroupLevel
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.model.GroupPlayStyle
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_composition_men
import br.com.saqz.groups.resources.group_composition_mixed
import br.com.saqz.groups.resources.group_composition_women
import br.com.saqz.groups.resources.group_duration_minutes
import br.com.saqz.groups.resources.group_duration_one_hour
import br.com.saqz.groups.resources.group_duration_one_hour_thirty
import br.com.saqz.groups.resources.group_duration_two_hours
import br.com.saqz.groups.resources.group_duration_two_hours_thirty
import br.com.saqz.groups.resources.group_lead_minutes
import br.com.saqz.groups.resources.group_lead_six_hours
import br.com.saqz.groups.resources.group_lead_three_hours
import br.com.saqz.groups.resources.group_lead_twelve_hours
import br.com.saqz.groups.resources.group_lead_twenty_four_hours
import br.com.saqz.groups.resources.group_level_advanced
import br.com.saqz.groups.resources.group_level_beginner
import br.com.saqz.groups.resources.group_level_custom
import br.com.saqz.groups.resources.group_level_intermediate
import br.com.saqz.groups.resources.group_level_mixed_levels
import br.com.saqz.groups.resources.group_modality_beach_volleyball
import br.com.saqz.groups.resources.group_modality_court_volleyball
import br.com.saqz.groups.resources.group_modality_footvolley
import br.com.saqz.groups.resources.group_play_style_custom
import br.com.saqz.groups.resources.group_play_style_five_one
import br.com.saqz.groups.resources.group_play_style_four_two
import br.com.saqz.groups.resources.group_play_style_six_zero
import br.com.saqz.groups.resources.group_weekday_friday
import br.com.saqz.groups.resources.group_weekday_monday
import br.com.saqz.groups.resources.group_weekday_saturday
import br.com.saqz.groups.resources.group_weekday_short_friday
import br.com.saqz.groups.resources.group_weekday_short_monday
import br.com.saqz.groups.resources.group_weekday_short_saturday
import br.com.saqz.groups.resources.group_weekday_short_sunday
import br.com.saqz.groups.resources.group_weekday_short_thursday
import br.com.saqz.groups.resources.group_weekday_short_tuesday
import br.com.saqz.groups.resources.group_weekday_short_wednesday
import br.com.saqz.groups.resources.group_weekday_sunday
import br.com.saqz.groups.resources.group_weekday_thursday
import br.com.saqz.groups.resources.group_weekday_tuesday
import br.com.saqz.groups.resources.group_weekday_wednesday
import org.jetbrains.compose.resources.stringResource

@Composable
fun GroupModality.label(): String = stringResource(
    when (this) {
        GroupModality.COURT_VOLLEYBALL -> Res.string.group_modality_court_volleyball
        GroupModality.BEACH_VOLLEYBALL -> Res.string.group_modality_beach_volleyball
        GroupModality.FOOTVOLLEY -> Res.string.group_modality_footvolley
    },
)

@Composable
fun GroupComposition.label(): String = stringResource(
    when (this) {
        GroupComposition.WOMEN -> Res.string.group_composition_women
        GroupComposition.MEN -> Res.string.group_composition_men
        GroupComposition.MIXED -> Res.string.group_composition_mixed
    },
)

@Composable
fun GroupLevel.label(): String = stringResource(
    when (this) {
        GroupLevel.BEGINNER -> Res.string.group_level_beginner
        GroupLevel.INTERMEDIATE -> Res.string.group_level_intermediate
        GroupLevel.ADVANCED -> Res.string.group_level_advanced
        GroupLevel.MIXED_LEVELS -> Res.string.group_level_mixed_levels
        GroupLevel.CUSTOM -> Res.string.group_level_custom
    },
)

@Composable
fun GroupPlayStyle.label(): String = stringResource(
    when (this) {
        GroupPlayStyle.SIX_ZERO -> Res.string.group_play_style_six_zero
        GroupPlayStyle.FOUR_TWO -> Res.string.group_play_style_four_two
        GroupPlayStyle.FIVE_ONE -> Res.string.group_play_style_five_one
        GroupPlayStyle.CUSTOM -> Res.string.group_play_style_custom
    },
)

@Composable
fun GroupWeekday.label(): String = stringResource(
    when (this) {
        GroupWeekday.SUNDAY -> Res.string.group_weekday_sunday
        GroupWeekday.MONDAY -> Res.string.group_weekday_monday
        GroupWeekday.TUESDAY -> Res.string.group_weekday_tuesday
        GroupWeekday.WEDNESDAY -> Res.string.group_weekday_wednesday
        GroupWeekday.THURSDAY -> Res.string.group_weekday_thursday
        GroupWeekday.FRIDAY -> Res.string.group_weekday_friday
        GroupWeekday.SATURDAY -> Res.string.group_weekday_saturday
    },
)

@Composable
fun GroupWeekday.shortLabel(): String = stringResource(
    when (this) {
        GroupWeekday.SUNDAY -> Res.string.group_weekday_short_sunday
        GroupWeekday.MONDAY -> Res.string.group_weekday_short_monday
        GroupWeekday.TUESDAY -> Res.string.group_weekday_short_tuesday
        GroupWeekday.WEDNESDAY -> Res.string.group_weekday_short_wednesday
        GroupWeekday.THURSDAY -> Res.string.group_weekday_short_thursday
        GroupWeekday.FRIDAY -> Res.string.group_weekday_short_friday
        GroupWeekday.SATURDAY -> Res.string.group_weekday_short_saturday
    },
)

@Composable
fun durationLabel(minutes: Int): String = when (minutes) {
    60 -> stringResource(Res.string.group_duration_one_hour)
    90 -> stringResource(Res.string.group_duration_one_hour_thirty)
    120 -> stringResource(Res.string.group_duration_two_hours)
    150 -> stringResource(Res.string.group_duration_two_hours_thirty)
    else -> stringResource(Res.string.group_duration_minutes, minutes)
}

@Composable
fun confirmationLeadLabel(minutes: Int): String = when (minutes) {
    180 -> stringResource(Res.string.group_lead_three_hours)
    360 -> stringResource(Res.string.group_lead_six_hours)
    720 -> stringResource(Res.string.group_lead_twelve_hours)
    1_440 -> stringResource(Res.string.group_lead_twenty_four_hours)
    else -> stringResource(Res.string.group_lead_minutes, minutes)
}
