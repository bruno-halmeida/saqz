package br.com.saqz.access.domain

enum class PersistedMembershipRole {
    ADMIN,
    ATHLETE,
}

enum class GroupRole {
    OWNER,
    ADMIN,
    ATHLETE,
    ;

    companion object {
        fun resolve(
            isOwner: Boolean,
            persistedRole: PersistedMembershipRole?,
        ): GroupRole? = when {
            isOwner -> OWNER
            persistedRole == PersistedMembershipRole.ADMIN -> ADMIN
            persistedRole == PersistedMembershipRole.ATHLETE -> ATHLETE
            else -> null
        }
    }
}
