package br.com.saqz.profile.presentation.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.profile.domain.PhoneVisibility
import br.com.saqz.profile.domain.ProfileError
import br.com.saqz.profile.domain.ProfileGateway
import br.com.saqz.profile.domain.UpdateField
import br.com.saqz.profile.domain.UpdateSessionProfileRequest
import kotlinx.coroutines.launch

class EditProfileViewModel(
    private val gateway: ProfileGateway,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : MviViewModel<EditProfileState, EditProfileIntent, EditProfileEffect>(EditProfileState()) {
    private var loadGeneration = 0L

    init {
        load()
    }

    override fun onIntent(intent: EditProfileIntent) {
        when (intent) {
            is EditProfileIntent.UpdateDisplayName -> updateDraft(KeyDisplayName, intent.value) {
                updateForm(EditProfileFieldError.NameRequired) { copy(displayName = intent.value) }
            }
            is EditProfileIntent.UpdateNickname -> updateDraft(KeyNickname, intent.value) {
                updateForm(EditProfileFieldError.NicknameInvalid) { copy(nickname = intent.value) }
            }
            is EditProfileIntent.UpdatePhone -> updateDraft(KeyPhone, intent.value) {
                updateForm(EditProfileFieldError.PhoneRequired) { copy(phone = intent.value) }
            }
            is EditProfileIntent.UpdateCity -> updateDraft(KeyCity, intent.value) {
                updateForm(EditProfileFieldError.CityInvalid) { copy(city = intent.value) }
            }
            is EditProfileIntent.SelectPhoneVisibility -> updateDraft(KeyPhoneVisibility, intent.value.name) {
                updateForm(EditProfileFieldError.PhoneVisibilityInvalid) { copy(phoneVisibility = intent.value) }
            }
            EditProfileIntent.Submit -> save()
            EditProfileIntent.Retry -> load()
        }
    }

    private fun load() {
        val generation = ++loadGeneration
        update { it.copy(isLoading = true, loadFailed = false, saveFailed = false) }
        viewModelScope.launch {
            when (val result = gateway.bootstrap()) {
                is SaqzResult.Success -> if (generation == loadGeneration) {
                    update { EditProfileState.loaded(result.value).restoreDraft(savedState) }
                }
                is SaqzResult.Failure -> if (generation == loadGeneration) {
                    update { it.copy(isLoading = false, loadFailed = true) }
                }
            }
        }
    }

    private fun updateForm(
        field: EditProfileFieldError,
        transform: EditProfileForm.() -> EditProfileForm,
    ) {
        update { current ->
            val form = current.form.transform()
            current.copy(
                form = form,
                fieldErrors = current.fieldErrors.withoutField(field),
                saveFailed = false,
            )
        }
    }

    private fun updateDraft(key: String, value: String, update: () -> Unit) {
        savedState[key] = value
        update()
    }

    private fun clearDraft() {
        DraftKeys.forEach { savedState.remove<Any?>(it) }
    }

    private fun save() {
        val current = state.value
        if (!current.canSave) return

        val validationErrors = current.form.validate()
        if (validationErrors.isNotEmpty()) {
            update { it.copy(fieldErrors = validationErrors, saveFailed = false) }
            return
        }

        update { it.copy(isSaving = true, saveFailed = false, fieldErrors = emptySet()) }
        viewModelScope.launch {
            when (val result = gateway.updateProfile(current.form.toRequest())) {
                is SaqzResult.Success -> {
                    clearDraft()
                    update { EditProfileState.loaded(result.value) }
                    emit(EditProfileEffect.Saved)
                }
                is SaqzResult.Failure -> when (val error = result.error) {
                    is ProfileError.Validation -> update {
                        it.copy(
                            isSaving = false,
                            fieldErrors = error.toEditProfileFieldErrors(),
                        )
                    }
                    is ProfileError.DataFailure -> update {
                        it.copy(isSaving = false, saveFailed = true)
                    }
                }
            }
        }
    }
}

private fun EditProfileForm.toRequest(): UpdateSessionProfileRequest = UpdateSessionProfileRequest(
    displayName = UpdateField.Set(displayName),
    nickname = UpdateField.Set(nickname.trim().ifEmpty { null }),
    phone = UpdateField.Set(phone.toBackendPhone()),
    city = UpdateField.Set(city.trim().ifEmpty { null }),
    phoneVisibility = UpdateField.Set(phoneVisibility),
)

private fun String.toBackendPhone(): String {
    val digits = filter(Char::isDigit)
    if (digits.startsWith("55") && digits.length > 11) return "+$digits"
    if (digits.length == 11) return "+55$digits"
    return trim()
}

private fun EditProfileState.restoreDraft(savedState: SavedStateHandle): EditProfileState {
    val restoredForm = form.copy(
        displayName = savedState.get<String>(KeyDisplayName) ?: form.displayName,
        nickname = savedState.get<String>(KeyNickname) ?: form.nickname,
        phone = savedState.get<String>(KeyPhone) ?: form.phone,
        city = savedState.get<String>(KeyCity) ?: form.city,
        phoneVisibility = savedState.get<String>(KeyPhoneVisibility)
            ?.let { value -> runCatching { PhoneVisibility.valueOf(value) }.getOrNull() }
            ?: form.phoneVisibility,
    )
    return copy(form = restoredForm)
}

private const val KeyDisplayName = "edit-profile-display-name"
private const val KeyNickname = "edit-profile-nickname"
private const val KeyPhone = "edit-profile-phone"
private const val KeyCity = "edit-profile-city"
private const val KeyPhoneVisibility = "edit-profile-phone-visibility"

private val DraftKeys = listOf(KeyDisplayName, KeyNickname, KeyPhone, KeyCity, KeyPhoneVisibility)
