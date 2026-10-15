package br.com.saqz.groups.application.entryrequest

import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import java.util.UUID

class GetOwnEntryRequest(
    private val groupReadRepository: GroupReadRepository,
    private val entryRequestRepository: EntryRequestRepository,
) {
    fun execute(actor: UUID, groupId: UUID): GetOwnEntryRequestResult {
        val group = groupReadRepository.find(GroupReadKey(actor, groupId))
            ?: return GetOwnEntryRequestResult.GroupNotFound
        val request = entryRequestRepository.find(groupId, actor)
            ?: return GetOwnEntryRequestResult.RequestNotFound
        return GetOwnEntryRequestResult.Success(
            OwnEntryRequest(
                groupId = group.id,
                groupName = group.name.value,
                modality = group.profile?.modality,
                composition = group.profile?.composition,
                city = group.profile?.city,
                requestedAt = request.requestedAt,
                athleteProfile = entryRequestRepository.findAthleteProfile(groupId, actor),
            ),
        )
    }
}
