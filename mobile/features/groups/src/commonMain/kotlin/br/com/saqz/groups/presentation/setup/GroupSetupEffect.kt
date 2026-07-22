package br.com.saqz.groups.presentation.setup

sealed interface GroupSetupEffect {
    data class SelectGroup(val groupId: String) : GroupSetupEffect
    data class OpenGroup(val groupId: String) : GroupSetupEffect
    data class UploadPhoto(val groupId: String, val groupEtag: String) : GroupSetupEffect
}
