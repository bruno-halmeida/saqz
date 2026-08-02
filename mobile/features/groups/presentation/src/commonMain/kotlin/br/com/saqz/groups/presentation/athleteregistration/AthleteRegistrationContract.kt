package br.com.saqz.groups.presentation.athleteregistration

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.domain.athlete.AthleteLevel
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.athlete.AthletePreferredSide
import br.com.saqz.groups.domain.group.GroupComposition
import br.com.saqz.groups.domain.group.GroupModality
import br.com.saqz.groups.presentation.GroupUiError

@Immutable
data class AthleteRegistrationState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val error: GroupUiError? = null,
    val groupName: String = "",
    val modality: GroupModality? = null,
    val composition: GroupComposition? = null,
    val city: String = "",
    val displayName: String = "",
    val nickname: String = "",
    val position: AthletePosition? = null,
    val secondaryPosition: AthletePosition? = null,
    val level: AthleteLevel? = null,
    val preferredSide: AthletePreferredSide? = null,
    val heightText: String = "",
    val isSaving: Boolean = false,
) {
    val isCourt: Boolean get() = modality == GroupModality.COURT_VOLLEYBALL
}

sealed interface AthleteRegistrationIntent {
    data object Retry : AthleteRegistrationIntent
    data class NicknameChanged(val value: String) : AthleteRegistrationIntent
    data class PositionSelected(val value: AthletePosition) : AthleteRegistrationIntent
    data class SecondaryPositionSelected(val value: AthletePosition) : AthleteRegistrationIntent
    data class LevelSelected(val value: AthleteLevel) : AthleteRegistrationIntent
    data class PreferredSideSelected(val value: AthletePreferredSide) : AthleteRegistrationIntent
    data class HeightChanged(val value: String) : AthleteRegistrationIntent
    data object Save : AthleteRegistrationIntent
}

sealed interface AthleteRegistrationEffect {
    data object Saved : AthleteRegistrationEffect
}
