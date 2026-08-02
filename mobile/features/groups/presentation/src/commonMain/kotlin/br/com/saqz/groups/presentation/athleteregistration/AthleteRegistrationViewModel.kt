package br.com.saqz.groups.presentation.athleteregistration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteGateway
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.athlete.UpdateOwnAthleteProfileCommand
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.group.GroupModality
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.toUiError
import kotlinx.coroutines.launch

class AthleteRegistrationViewModel(
    private val groupId: String,
    private val savedState: SavedStateHandle,
    private val groupGateway: GroupGateway,
    private val athleteGateway: AthleteGateway,
) : MviViewModel<AthleteRegistrationState, AthleteRegistrationIntent, AthleteRegistrationEffect>(
    AthleteRegistrationState().restoreDraft(savedState),
) {
    private var generation = 0L

    init {
        load()
    }

    override fun onIntent(intent: AthleteRegistrationIntent) {
        when (intent) {
            AthleteRegistrationIntent.Retry -> load()
            is AthleteRegistrationIntent.NicknameChanged -> {
                savedState[KEY_NICKNAME] = intent.value
                update { it.copy(nickname = intent.value, error = null) }
            }
            is AthleteRegistrationIntent.PositionSelected -> selectPosition(intent.value)
            is AthleteRegistrationIntent.SecondaryPositionSelected -> selectSecondaryPosition(intent.value)
            is AthleteRegistrationIntent.LevelSelected -> {
                savedState[KEY_LEVEL] = intent.value.name
                update { it.copy(level = intent.value, error = null) }
            }
            is AthleteRegistrationIntent.PreferredSideSelected -> {
                savedState[KEY_SIDE] = intent.value.name
                update { it.copy(preferredSide = intent.value, error = null) }
            }
            is AthleteRegistrationIntent.HeightChanged -> {
                savedState[KEY_HEIGHT] = intent.value
                update {
                    it.copy(
                        heightText = intent.value,
                        error = null,
                    )
                }
            }
            AthleteRegistrationIntent.Save -> save()
        }
    }

    private fun load() {
        val requestGeneration = nextGeneration()
        update { it.copy(isLoading = true, loadFailed = false, error = null, isSaving = false) }
        viewModelScope.launch {
            val group = when (val result = groupGateway.read(GroupId(groupId))) {
                is SaqzResult.Failure -> return@launch showLoadFailure(requestGeneration, result.error.toUiError())
                is SaqzResult.Success -> result.value.group
            }
            if (requestGeneration != generation) return@launch

            val profile = group.profile
            val modality = profile?.modality
            if (modality == null) return@launch showLoadFailure(requestGeneration, GroupUiError.Unknown)

            val ownProfile = when (val result = athleteGateway.ownProfile()) {
                is SaqzResult.Failure -> return@launch showLoadFailure(requestGeneration, result.error.toUiError())
                is SaqzResult.Success -> result.value
            }
            if (requestGeneration != generation) return@launch

            val membership = ownProfile.memberships.firstOrNull { it.groupId == GroupId(groupId) }
                ?: return@launch showLoadFailure(requestGeneration, GroupUiError.NotFound)
            update {
                it.copy(
                    isLoading = false,
                    loadFailed = false,
                    error = null,
                    groupName = group.name,
                    modality = modality,
                    composition = profile.composition,
                    city = profile.city.orEmpty(),
                    displayName = ownProfile.displayName,
                    nickname = membership.nickname.orEmpty(),
                    position = membership.position,
                    secondaryPosition = membership.secondaryPosition,
                    level = membership.level,
                    preferredSide = membership.preferredSide,
                    heightText = membership.heightCm?.toString().orEmpty(),
                ).restoreDraft(savedState)
            }
        }
    }

    private fun selectPosition(value: AthletePosition) {
        savedState[KEY_POSITION] = value.name
        savedState[KEY_SECONDARY_POSITION] = null
        update { it.copy(position = value, secondaryPosition = null, error = null) }
    }

    private fun selectSecondaryPosition(value: AthletePosition) {
        val current = state.value
        if (!current.isCourt || current.position == value) return
        val selected = if (current.secondaryPosition == value) null else value
        savedState[KEY_SECONDARY_POSITION] = selected?.name
        update { it.copy(secondaryPosition = selected, error = null) }
    }

    private fun save() {
        val current = state.value
        if (current.isLoading || current.loadFailed || current.isSaving) return
        val height = current.heightText.trim()
        val heightCm = height.toIntOrNull()
        if (current.isCourt && height.isNotEmpty() && heightCm !in 100..250) {
            update { it.copy(error = GroupUiError.Validation) }
            return
        }

        val requestGeneration = nextGeneration()
        update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = athleteGateway.updateOwnProfile(
                UpdateOwnAthleteProfileCommand(
                    groupId = GroupId(groupId),
                    nickname = current.nickname.trim().takeIf(String::isNotEmpty),
                    position = current.position.takeIf { current.isCourt },
                    secondaryPosition = current.secondaryPosition.takeIf { current.isCourt },
                    level = current.level,
                    preferredSide = current.preferredSide.takeUnless { current.isCourt },
                    heightCm = heightCm.takeIf { current.isCourt },
                ),
            )
            if (requestGeneration != generation) return@launch
            when (result) {
                is SaqzResult.Failure -> update {
                    it.copy(isSaving = false, error = result.error.toUiError())
                }
                is SaqzResult.Success -> {
                    clearDraft()
                    update { it.copy(isSaving = false, error = null) }
                    emit(AthleteRegistrationEffect.Saved)
                }
            }
        }
    }

    private fun showLoadFailure(requestGeneration: Long, error: GroupUiError) {
        if (requestGeneration != generation) return
        update { it.copy(isLoading = false, loadFailed = true, error = error) }
    }

    private fun nextGeneration(): Long {
        generation += 1
        return generation
    }

    private fun clearDraft() {
        DraftKeys.forEach { savedState.remove<Any?>(it) }
    }
}

private fun AthleteRegistrationState.restoreDraft(savedState: SavedStateHandle): AthleteRegistrationState = copy(
    nickname = savedState.get<String>(KEY_NICKNAME) ?: nickname,
    position = savedState.get<String>(KEY_POSITION)?.toEnumOrNull() ?: position,
    secondaryPosition = if (savedState.contains(KEY_SECONDARY_POSITION)) {
        savedState.get<String>(KEY_SECONDARY_POSITION)?.toEnumOrNull()
    } else {
        secondaryPosition
    },
    level = savedState.get<String>(KEY_LEVEL)?.toEnumOrNull() ?: level,
    preferredSide = savedState.get<String>(KEY_SIDE)?.toEnumOrNull() ?: preferredSide,
    heightText = savedState.get<String>(KEY_HEIGHT) ?: heightText,
)

private inline fun <reified T : Enum<T>> String.toEnumOrNull(): T? =
    runCatching { enumValueOf<T>(this) }.getOrNull()

private const val KEY_NICKNAME = "athlete-registration-nickname"
private const val KEY_POSITION = "athlete-registration-position"
private const val KEY_SECONDARY_POSITION = "athlete-registration-secondary-position"
private const val KEY_LEVEL = "athlete-registration-level"
private const val KEY_SIDE = "athlete-registration-side"
private const val KEY_HEIGHT = "athlete-registration-height"

private val DraftKeys = listOf(
    KEY_NICKNAME,
    KEY_POSITION,
    KEY_SECONDARY_POSITION,
    KEY_LEVEL,
    KEY_SIDE,
    KEY_HEIGHT,
)
