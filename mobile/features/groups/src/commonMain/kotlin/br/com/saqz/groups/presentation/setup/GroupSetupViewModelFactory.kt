package br.com.saqz.groups.presentation.setup

import br.com.saqz.groups.domain.group.GroupProfileGateway
import br.com.saqz.groups.port.GroupDraftStorePort
import br.com.saqz.groups.port.GroupSystemTimeZonePort

/**
 * Entry-compatible factory binding for the existing [GroupSetupViewModel] (T14):
 * accepts the route identity ([GroupSetupInput], distinguishing the Setup and
 * CreateGroup routes by whether a group already exists) and the shared
 * dependencies, so each Navigation Compose 3 entry can request its own
 * lifecycle-scoped instance. No ViewModel behavior changes (LIFE-01..05,
 * GROUPNAV-01, REG-04).
 */
class GroupSetupViewModelFactory(
    private val gateway: GroupProfileGateway,
    private val timeZones: GroupSystemTimeZonePort,
    private val drafts: GroupDraftStorePort,
) {
    fun create(
        input: GroupSetupInput,
        commandKeys: GroupCommandKeyFactory,
    ): GroupSetupViewModel = GroupSetupViewModel(
        input = input,
        gateway = gateway,
        timeZones = timeZones,
        drafts = drafts,
        commandKeys = commandKeys,
    )
}
