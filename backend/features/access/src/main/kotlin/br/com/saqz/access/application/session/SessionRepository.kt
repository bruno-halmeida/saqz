package br.com.saqz.access.application.session

interface SessionRepository {
    fun upsertAndLoad(command: SessionUpsert): SessionView

    fun updateProfile(command: ProfileCompletion): SessionView? =
        throw UnsupportedOperationException("updateProfile not supported by ${this::class.simpleName}")

    /** Instante da suspensão de plataforma, ou null quando a conta pode entrar. */
    fun suspendedAt(subject: String): java.time.Instant? = null
}
