package br.com.saqz.groups.presentation.photo

import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.photo.CachedGroupPhoto
import br.com.saqz.groups.domain.photo.GroupPhotoCachePort
import br.com.saqz.groups.domain.photo.GroupPhotoError
import br.com.saqz.groups.domain.photo.GroupPhotoGateway
import br.com.saqz.groups.domain.photo.GroupPhotoReadResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GroupListPhotoLoader(
    private val gateway: GroupPhotoGateway,
    private val cache: GroupPhotoCachePort,
) {
    private val registry = Mutex()
    private val locks = mutableMapOf<String, Mutex>()

    suspend fun load(groupId: String): ExistingGroupPhoto? {
        if (groupId.isBlank()) return null
        val domainGroupId = GroupId(groupId)
        cache.read(domainGroupId)?.toExisting()?.let { return it }
        return lockFor(groupId).withLock {
            cache.read(domainGroupId)?.toExisting() ?: fetch(domainGroupId)
        }
    }

    private suspend fun fetch(groupId: GroupId): ExistingGroupPhoto? =
        when (val result = gateway.read(groupId)) {
            is SaqzResult.Failure -> {
                if (result.error == GroupPhotoError.NotFound) {
                    cache.evict(groupId)
                }
                null
            }
            is SaqzResult.Success -> when (val value = result.value) {
                GroupPhotoReadResult.NotModified -> cache.read(groupId)?.toExisting()
                is GroupPhotoReadResult.Available -> runCatching {
                    cache.write(groupId, value.bytes, value.version)
                }.getOrNull()?.toExisting()
            }
        }

    private suspend fun lockFor(groupId: String): Mutex = registry.withLock {
        locks.getOrPut(groupId) { Mutex() }
    }

    private fun CachedGroupPhoto.toExisting(): ExistingGroupPhoto =
        ExistingGroupPhoto(preview = preview, photoEtag = version.value)
}
