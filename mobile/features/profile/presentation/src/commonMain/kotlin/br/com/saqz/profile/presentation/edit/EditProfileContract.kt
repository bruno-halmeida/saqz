package br.com.saqz.profile.presentation.edit

import androidx.compose.runtime.Immutable
import br.com.saqz.profile.domain.PhoneVisibility
import br.com.saqz.profile.domain.Profile

@Immutable
data class EditProfileForm(
    val displayName: String = "",
    val nickname: String = "",
    val phone: String = "",
    val email: String = "",
    val city: String = "",
    val phoneVisibility: PhoneVisibility = PhoneVisibility.ADMINS,
)

@Immutable
data class EditProfileState(
    val form: EditProfileForm = EditProfileForm(),
    val originalForm: EditProfileForm = EditProfileForm(),
    val photoUrl: String? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val loadFailed: Boolean = false,
    val saveFailed: Boolean = false,
    val fieldErrors: Set<EditProfileFieldError> = emptySet(),
) {
    val hasChanges: Boolean = form != originalForm
    val canSave: Boolean = hasChanges && !isLoading && !isSaving

    companion object {
        fun loaded(profile: Profile): EditProfileState {
            val form = profile.toEditProfileForm()
            return EditProfileState(
                form = form,
                originalForm = form,
                photoUrl = profile.user.photoUrl,
                isLoading = false,
            )
        }
    }
}

enum class EditProfileFieldError {
    NameRequired,
    NameInvalid,
    PhoneRequired,
    PhoneInvalid,
    NicknameInvalid,
    CityInvalid,
    PhoneVisibilityInvalid,
}

sealed interface EditProfileIntent {
    data class UpdateDisplayName(val value: String) : EditProfileIntent

    data class UpdateNickname(val value: String) : EditProfileIntent

    data class UpdatePhone(val value: String) : EditProfileIntent

    data class UpdateCity(val value: String) : EditProfileIntent

    data class SelectPhoneVisibility(val value: PhoneVisibility) : EditProfileIntent

    data object Submit : EditProfileIntent

    data object Retry : EditProfileIntent
}

sealed interface EditProfileEffect {
    data object Saved : EditProfileEffect
}

internal fun Profile.toEditProfileForm(): EditProfileForm = EditProfileForm(
    displayName = user.displayName,
    nickname = user.nickname.orEmpty(),
    phone = user.phone?.let(::formatPhoneForDisplay).orEmpty(),
    email = user.email.orEmpty(),
    city = user.city.orEmpty(),
    phoneVisibility = user.phoneVisibility,
)

internal fun formatPhoneForDisplay(phone: String): String {
    val digits = phone.filter(Char::isDigit).removePrefix("55")
    return when {
        digits.length == 11 -> "(${digits.take(2)}) ${digits.drop(2).take(5)}-${digits.takeLast(4)}"
        else -> phone
    }
}
