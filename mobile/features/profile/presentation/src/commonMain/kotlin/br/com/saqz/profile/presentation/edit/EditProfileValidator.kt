package br.com.saqz.profile.presentation.edit

import br.com.saqz.profile.domain.ProfileError

internal fun EditProfileForm.validate(): Set<EditProfileFieldError> = buildSet {
    if (displayName.isBlank()) add(EditProfileFieldError.NameRequired)
    if (phone.isBlank()) add(EditProfileFieldError.PhoneRequired)
}

internal fun ProfileError.Validation.toEditProfileFieldErrors(): Set<EditProfileFieldError> =
    details.fieldMessages.keys.mapNotNull { field ->
        when (field) {
            "displayName", "name" -> EditProfileFieldError.NameInvalid
            "phone" -> EditProfileFieldError.PhoneInvalid
            "nickname" -> EditProfileFieldError.NicknameInvalid
            "city" -> EditProfileFieldError.CityInvalid
            "phoneVisibility" -> EditProfileFieldError.PhoneVisibilityInvalid
            else -> null
        }
    }.toSet()

internal fun Set<EditProfileFieldError>.withoutField(
    field: EditProfileFieldError,
): Set<EditProfileFieldError> = filterNot { it.sameFieldAs(field) }.toSet()

private fun EditProfileFieldError.sameFieldAs(other: EditProfileFieldError): Boolean = when (this) {
    EditProfileFieldError.NameRequired,
    EditProfileFieldError.NameInvalid,
    -> other == EditProfileFieldError.NameRequired || other == EditProfileFieldError.NameInvalid

    EditProfileFieldError.PhoneRequired,
    EditProfileFieldError.PhoneInvalid,
    -> other == EditProfileFieldError.PhoneRequired || other == EditProfileFieldError.PhoneInvalid

    EditProfileFieldError.NicknameInvalid -> other == EditProfileFieldError.NicknameInvalid
    EditProfileFieldError.CityInvalid -> other == EditProfileFieldError.CityInvalid
    EditProfileFieldError.PhoneVisibilityInvalid -> other == EditProfileFieldError.PhoneVisibilityInvalid
}
