package br.com.saqz.access.application.group.settings

interface GroupSettingsRepository {
    fun update(command: UpdateGroupSettingsCommand): SettingsWriteResult
}
