package br.com.saqz.groups.presentation.setup

import br.com.saqz.groups.data.VersionedGroupDto

data class GroupSetupInput(
    val existing: VersionedGroupDto? = null,
)
