package br.com.saqz.sharedkernel.group

import java.util.UUID

data class GroupFinancialSetup(val groupId: UUID, val ownerUserId: UUID,
    val gameFeeCents: Long?, val monthlyFeeCents: Long?)

fun interface GroupFinancialSetupLookup {
    /** Called inside a transaction; locks current ownership and prices against concurrent edits. */
    fun lockActive(groupId: UUID): GroupFinancialSetup?
}
