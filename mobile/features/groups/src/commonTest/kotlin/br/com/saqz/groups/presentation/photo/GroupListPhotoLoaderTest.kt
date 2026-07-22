package br.com.saqz.groups.presentation.photo

import br.com.saqz.groups.data.GroupPhotoGateway
import br.com.saqz.groups.data.GroupPhotoReadResult
import br.com.saqz.groups.data.GroupPhotoReceipt
import br.com.saqz.groups.data.GroupPhotoUploadCommand
import br.com.saqz.groups.port.CachedGroupPhoto
import br.com.saqz.groups.port.GroupPhotoCachePort
import br.com.saqz.groups.port.GroupPhotoPreviewHandle
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class GroupListPhotoLoaderTest {
    @Test
    fun `cache hit skips network`() = runTest {
        val cache = FakeCache().apply { entries[GROUP] = CACHED }
        val gateway = FakeGateway()
        val loaded = GroupListPhotoLoader(gateway, cache).load(GROUP)
        assertEquals(ExistingGroupPhoto(CACHED.preview, CACHED.photoEtag), loaded)
        assertEquals(emptyList(), gateway.reads)
    }

    @Test
    fun `cache miss fetches online and writes cache`() = runTest {
        val cache = FakeCache()
        val gateway = FakeGateway().apply {
            readResult = NetworkResult.Success(
                GroupPhotoReadResult.Available(byteArrayOf(1, 2, 3), "image/jpeg", PHOTO_ETAG),
            )
        }
        val loaded = GroupListPhotoLoader(gateway, cache).load(GROUP)
        assertEquals(ExistingGroupPhoto(CACHED.preview, PHOTO_ETAG), loaded)
        assertEquals(listOf<Pair<String, String?>>(GROUP to null), gateway.reads)
        assertEquals(listOf<Byte>(1, 2, 3), cache.writes.single().bytes.toList())
        assertEquals(PHOTO_ETAG, cache.writes.single().photoEtag)
    }

    @Test
    fun `not found returns null and evicts stale cache entry`() = runTest {
        val cache = FakeCache()
        val gateway = FakeGateway().apply {
            readResult = NetworkResult.Failure(NetworkError.HttpStatus(404))
        }
        assertNull(GroupListPhotoLoader(gateway, cache).load(GROUP))
        assertEquals(listOf(GROUP), cache.evicted)
    }

    @Test
    fun `network failure returns null without writing`() = runTest {
        val cache = FakeCache()
        val gateway = FakeGateway().apply {
            readResult = NetworkResult.Failure(NetworkError.Unavailable)
        }
        assertNull(GroupListPhotoLoader(gateway, cache).load(GROUP))
        assertEquals(emptyList(), cache.writes)
        assertEquals(emptyList(), cache.evicted)
    }

    @Test
    fun `blank group id returns null`() = runTest {
        val gateway = FakeGateway()
        assertNull(GroupListPhotoLoader(gateway, FakeCache()).load("  "))
        assertEquals(emptyList(), gateway.reads)
    }

    private class FakeGateway : GroupPhotoGateway {
        val reads = mutableListOf<Pair<String, String?>>()
        var readResult: NetworkResult<GroupPhotoReadResult> = NetworkResult.Failure(NetworkError.Unavailable)
        override suspend fun upload(command: GroupPhotoUploadCommand) =
            NetworkResult.Success(GroupPhotoReceipt("\"1\""))
        override suspend fun read(groupId: String, etag: String?): NetworkResult<GroupPhotoReadResult> {
            reads += groupId to etag
            return readResult
        }
        override suspend fun remove(groupId: String, groupEtag: String) =
            NetworkResult.Success(GroupPhotoReceipt("\"1\""))
    }

    private class FakeCache : GroupPhotoCachePort {
        data class Write(val groupId: String, val bytes: ByteArray, val photoEtag: String)
        val entries = mutableMapOf<String, CachedGroupPhoto>()
        val writes = mutableListOf<Write>()
        val evicted = mutableListOf<String>()
        override fun read(groupId: String) = entries[groupId]
        override fun write(groupId: String, bytes: ByteArray, photoEtag: String): CachedGroupPhoto {
            writes += Write(groupId, bytes, photoEtag)
            return CachedGroupPhoto(GroupPhotoPreviewHandle("existing-preview"), photoEtag).also {
                entries[groupId] = it
            }
        }
        override fun evict(groupId: String) { evicted += groupId }
        override fun clearAll() = Unit
    }

    private companion object {
        const val GROUP = "group-1"
        const val PHOTO_ETAG = "\"photo-2\""
        val CACHED = CachedGroupPhoto(GroupPhotoPreviewHandle("existing-preview"), PHOTO_ETAG)
    }
}
