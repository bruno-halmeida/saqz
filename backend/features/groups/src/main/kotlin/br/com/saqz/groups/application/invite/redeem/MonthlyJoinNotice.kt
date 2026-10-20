package br.com.saqz.groups.application.invite.redeem

import java.util.UUID

/** O que mudou para quem entrou pelo link de mensalistas; define o texto do aviso a dono e admins (NOT-004). */
enum class MonthlyJoinOutcome {
    /** Entrou no grupo como mensalista. */
    JOINED_MENSALISTA,

    /** Entrou como avulso porque o grupo está sem mensalidade padrão. */
    JOINED_AVULSO_NO_DEFAULT_FEE,

    /** Já era avulso do grupo e virou mensalista. */
    BECAME_MENSALISTA,
}

/**
 * Aviso a dono e admins (menos quem entrou) quando alguém entra ou vira mensalista pelo link de mensalistas.
 * Roda na transação do resgate e nunca pode desfazer a entrada.
 */
fun interface MonthlyJoinNotice {
    fun joined(groupId: UUID, userId: UUID, outcome: MonthlyJoinOutcome)
}
