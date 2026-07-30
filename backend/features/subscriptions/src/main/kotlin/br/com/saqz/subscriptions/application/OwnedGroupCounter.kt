package br.com.saqz.subscriptions.application

import java.util.UUID

fun interface OwnedGroupCounter {
    fun countOwnedGroups(ownerUserId: UUID): Int
}
