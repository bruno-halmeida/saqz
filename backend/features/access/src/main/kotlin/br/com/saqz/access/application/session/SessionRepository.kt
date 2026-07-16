package br.com.saqz.access.application.session

interface SessionRepository {
    fun upsertAndLoad(command: SessionUpsert): SessionView
}
