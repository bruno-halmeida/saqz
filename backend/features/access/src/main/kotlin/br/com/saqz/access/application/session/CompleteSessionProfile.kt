package br.com.saqz.access.application.session

import br.com.saqz.access.domain.AccessName
import br.com.saqz.access.domain.PhoneNumber

class CompleteSessionProfile(
    private val repository: SessionRepository,
) {
    fun execute(subject: String, rawPhone: String, rawDisplayName: String?): CompleteSessionProfileResult {
        val phone = runCatching { PhoneNumber.from(rawPhone) }.getOrNull()
            ?: return CompleteSessionProfileResult.InvalidPhone
        val displayName = rawDisplayName?.let {
            runCatching { AccessName.from(it) }.getOrNull()
                ?: return CompleteSessionProfileResult.InvalidDisplayName
        }
        val session = repository.updateProfile(ProfileCompletion(subject, phone, displayName))
            ?: return CompleteSessionProfileResult.AccountNotFound
        return CompleteSessionProfileResult.Success(session)
    }
}
