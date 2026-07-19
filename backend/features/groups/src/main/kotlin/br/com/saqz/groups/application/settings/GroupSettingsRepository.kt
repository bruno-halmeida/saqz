package br.com.saqz.groups.application.settings

interface GroupSettingsRepository {
    fun update(command: UpdateGroupSettingsCommand): SettingsWriteResult
}
