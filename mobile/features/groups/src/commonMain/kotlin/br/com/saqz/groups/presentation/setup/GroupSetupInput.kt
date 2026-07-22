package br.com.saqz.groups.presentation.setup

import br.com.saqz.groups.domain.group.VersionedGroup

data class GroupSetupInput(
    val existing: VersionedGroup? = null,
)
