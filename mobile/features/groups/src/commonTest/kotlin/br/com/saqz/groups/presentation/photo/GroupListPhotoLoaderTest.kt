package br.com.saqz.groups.presentation.photo

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.photo.CachedGroupPhoto
import br.com.saqz.groups.domain.photo.GroupPhotoCachePort
import br.com.saqz.groups.domain.photo.GroupPhotoError
import br.com.saqz.groups.domain.photo.GroupPhotoGateway
import br.com.saqz.groups.domain.photo.GroupPhotoPreviewHandle
import br.com.saqz.groups.domain.photo.GroupPhotoReadResult
import br.com.saqz.groups.domain.photo.GroupPhotoReceipt
import br.com.saqz.groups.domain.photo.GroupPhotoUploadCommand
import br.com.saqz.groups.domain.photo.GroupPhotoVersionToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class GroupListPhotoLoaderTest {
    @Test
    fun `cache hit skips network`() = runTest {
        val cache = FakeCache().apply { entries[GroupId(GROUP)] = CACHED }
        val gateway = FakeGateway()
        val loaded = GroupListPhotoLoader(gateway, cache).load(GROUP)
        assertEquals(ExistingGroupPhoto(CACHED.preview, CACHED.version.value), loaded)
        assertEquals(emptyList(), gateway.reads)
    }

    @Test
    fun `cache miss fetches online and writes cache`() = runTest {
        val cache = FakeCache()
        val gateway = FakeGateway().apply {
            readResult = SaqzResult.Success(
                GroupPhotoReadResult.Available(
                    byteArrayOf(1, 2, 3),
                    "image/jpeg",
                    GroupPhotoVersionToken(PHOTO_ETAG),
                ),
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
            readResult = SaqzResult.Failure(GroupPhotoError.NotFound)
        }
        assertNull(GroupListPhotoLoader(gateway, cache).load(GROUP))
        assertEquals(listOf(GROUP), cache.evicted)
    }

    @Test
    fun `network failure returns null without writing`() = runTest {
        val cache = FakeCache()
        val gateway = FakeGateway().apply {
            readResult = SaqzResult.Failure(
                GroupPhotoError.DataFailure(DataError.Connectivity),
            )
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
        var readResult: SaqzResult<GroupPhotoReadResult, GroupPhotoError> = SaqzResult.Failure(
            GroupPhotoError.DataFailure(DataError.Connectivity),
        )

        override suspend fun upload(command: GroupPhotoUploadCommand) =
            SaqzResult.Success(GroupPhotoReceipt(GroupPhotoVersionToken("\"1\"")))

        override suspend fun read(
            groupId: GroupId,
            version: GroupPhotoVersionToken?,
        ): SaqzResult<GroupPhotoReadResult, GroupPhotoError> {
            reads += groupId.value to version?.value
            return readResult
        }

        override suspend fun remove(groupId: GroupId, groupVersion: GroupPhotoVersionToken) =
            SaqzResult.Success(GroupPhotoReceipt(GroupPhotoVersionToken("\"1\"")))
    }

    private class FakeCache : GroupPhotoCachePort {
        data class Write(val groupId: String, val bytes: ByteArray, val photoEtag: String)
        val entries = mutableMapOf<GroupId, CachedGroupPhoto>()
        val writes = mutableListOf<Write>()
        val evicted = mutableListOf<String>()
        override fun read(groupId: GroupId) = entries[groupId]

        override fun write(
            groupId: GroupId,
            bytes: ByteArray,
            version: GroupPhotoVersionToken,
        ): CachedGroupPhoto {
            writes += Write(groupId.value, bytes, version.value)
            return CachedGroupPhoto(GroupPhotoPreviewHandle("existing-preview"), version).also {
                entries[groupId] = it
            }
        }

        override fun evict(groupId: GroupId) {
            evicted += groupId.value
        }

        override fun clearAll() = Unit
    }

    private companion object {
        const val GROUP = "group-1"
        const val PHOTO_ETAG = "\"photo-2\""
        val CACHED = CachedGroupPhoto(
            GroupPhotoPreviewHandle("existing-preview"),
            GroupPhotoVersionToken(PHOTO_ETAG),
        )
    }
}
