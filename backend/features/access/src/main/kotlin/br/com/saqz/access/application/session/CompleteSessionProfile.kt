package br.com.saqz.access.application.session

import br.com.saqz.access.domain.AccessName
import br.com.saqz.access.domain.PhoneNumber

class CompleteSessionProfile(
    private val repository: SessionRepository,
) {
    fun execute(
        subject: String,
        rawPhone: String?,
        rawDisplayName: String?,
        rawNickname: String? = null,
        rawCity: String? = null,
        rawPhoneVisibility: String? = null,
        phoneProvided: Boolean = true,
        displayNameProvided: Boolean = rawDisplayName != null,
        nicknameProvided: Boolean = false,
        cityProvided: Boolean = false,
        phoneVisibilityProvided: Boolean = false,
    ): CompleteSessionProfileResult {
        val phone = if (phoneProvided) {
            runCatching { PhoneNumber.from(rawPhone.orEmpty()) }.getOrNull()
                ?: return CompleteSessionProfileResult.InvalidPhone
        } else {
            null
        }
        val displayName = if (displayNameProvided) {
            rawDisplayName?.let {
                runCatching { AccessName.from(it) }.getOrNull()
                    ?: return CompleteSessionProfileResult.InvalidDisplayName
            } ?: return CompleteSessionProfileResult.InvalidDisplayName
        } else {
            null
        }
        val nickname = rawNickname?.takeUnless(String::isBlank)
        val city = rawCity?.takeUnless(String::isBlank)
        val session = repository.updateProfile(
            ProfileCompletion(
                subject = subject,
                phone = phone,
                displayName = displayName,
                nickname = nickname,
                city = city,
                phoneVisibility = rawPhoneVisibility,
                phoneProvided = phoneProvided,
                displayNameProvided = displayNameProvided,
                nicknameProvided = nicknameProvided,
                cityProvided = cityProvided,
                phoneVisibilityProvided = phoneVisibilityProvided,
            ),
        )
            ?: return CompleteSessionProfileResult.AccountNotFound
        return CompleteSessionProfileResult.Success(session)
    }
}
