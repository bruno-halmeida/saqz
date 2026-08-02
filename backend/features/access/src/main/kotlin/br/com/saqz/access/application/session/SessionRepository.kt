package br.com.saqz.access.application.session

interface SessionRepository {
    fun upsertAndLoad(command: SessionUpsert): SessionView

    fun isDeleted(subject: String): Boolean = false

    fun updateProfile(command: ProfileCompletion): SessionView? =
        throw UnsupportedOperationException("updateProfile not supported by ${this::class.simpleName}")
}
