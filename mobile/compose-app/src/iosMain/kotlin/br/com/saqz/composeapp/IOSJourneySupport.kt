package br.com.saqz.composeapp

import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteRosterEntry
import br.com.saqz.groups.domain.group.Group
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.group.GroupVersionToken
import br.com.saqz.groups.domain.group.VersionedGroup
import br.com.saqz.groups.domain.membership.RedeemedMembership
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

// ponytail: Swift-facing seam for the native journey suites only — CoroutineScope,
// SaqzResult, and value classes (GroupId, GroupVersionToken) cross the ObjC bridge
// as opaque types Swift cannot instantiate (same precedent as ResourcePreflight).

/** Main-dispatcher scope for driving shared state machines from XCTest. */
class IOSJourneyScope {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun cancel() = scope.cancel()
}

fun journeySuccess(value: Any?): SaqzResult<Any?, Nothing> = SaqzResult.Success(value)

fun journeyRosterSuccess(entries: List<AthleteRosterEntry>): SaqzResult<List<AthleteRosterEntry>, Nothing> =
    SaqzResult.Success(entries)

fun journeyRedeemedMembership(groupId: String, role: GroupRole): RedeemedMembership =
    RedeemedMembership(GroupId(groupId), role)

fun journeyVersionedGroup(group: Group, versionToken: String): VersionedGroup =
    VersionedGroup(group, GroupVersionToken(versionToken))
