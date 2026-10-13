package br.com.saqz.groups.domain.group

import br.com.saqz.domain.GroupId

/** Read-only trial facts supplied by the app composition root without a feature dependency. */
data class GroupTrialAccessInfo(
    val endsAt: String?,
    val readOnly: Boolean,
    val isOwner: Boolean,
    /** Há plano pago ativo: o fim do teste deixou de importar. */
    val planActive: Boolean = false,
    /** O teste do organizador acabou e não há plano: distingue "teste terminou" de "plano inativo". */
    val trialEnded: Boolean = false,
)

fun interface GroupTrialAccessPort {
    suspend fun read(groupId: GroupId): GroupTrialAccessInfo?
}
