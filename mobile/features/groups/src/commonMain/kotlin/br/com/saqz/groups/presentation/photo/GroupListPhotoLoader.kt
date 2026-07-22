package br.com.saqz.groups.presentation.photo

import br.com.saqz.groups.data.GroupPhotoGateway
import br.com.saqz.groups.data.GroupPhotoReadResult
import br.com.saqz.groups.data.PhotoFailure
import br.com.saqz.groups.data.toPhotoFailure
import br.com.saqz.groups.port.CachedGroupPhoto
import br.com.saqz.groups.port.GroupPhotoCachePort
import br.com.saqz.network.NetworkResult
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
        cache.read(groupId)?.toExisting()?.let { return it }
        return lockFor(groupId).withLock {
            cache.read(groupId)?.toExisting() ?: fetch(groupId)
        }
    }

    private suspend fun fetch(groupId: String): ExistingGroupPhoto? =
        when (val result = gateway.read(groupId)) {
            is NetworkResult.Failure -> {
                if (result.error.toPhotoFailure() == PhotoFailure.NotFound) {
                    cache.evict(groupId)
                }
                null
            }
            is NetworkResult.Success -> when (val value = result.value) {
                GroupPhotoReadResult.NotModified -> cache.read(groupId)?.toExisting()
                is GroupPhotoReadResult.Available -> runCatching {
                    cache.write(groupId, value.bytes, value.etag)
                }.getOrNull()?.toExisting()
            }
        }

    private suspend fun lockFor(groupId: String): Mutex = registry.withLock {
        locks.getOrPut(groupId) { Mutex() }
    }

    private fun CachedGroupPhoto.toExisting(): ExistingGroupPhoto =
        ExistingGroupPhoto(preview = preview, photoEtag = photoEtag)
}
