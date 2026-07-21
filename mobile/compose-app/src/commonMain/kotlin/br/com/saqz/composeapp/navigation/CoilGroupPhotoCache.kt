package br.com.saqz.composeapp.navigation

import br.com.saqz.groups.port.CachedGroupPhoto
import br.com.saqz.groups.port.EncodedGroupPhoto
import br.com.saqz.groups.port.GroupPhotoCachePort
import br.com.saqz.groups.port.GroupPhotoPreviewHandle
import br.com.saqz.groups.port.GroupPhotoPreviewPort
import coil3.disk.DiskCache
import okio.FileSystem
import okio.Path

internal class CoilGroupPhotoCache(
    directory: Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / DEFAULT_DIRECTORY,
) : GroupPhotoCachePort, GroupPhotoPreviewPort {
    private val diskCache = DiskCache.Builder()
        .directory(directory)
        .maxSizeBytes(MAX_CACHE_BYTES)
        .build()

    init {
        clearAll()
    }

    override fun read(groupId: String): CachedGroupPhoto? {
        if (groupId.isBlank()) return null
        val key = groupKey(groupId)
        val snapshot = diskCache.openSnapshot(key) ?: return null
        return snapshot.use {
            val metadata = readBounded(snapshot.metadata, MAX_METADATA_BYTES) ?: return@use null
            val separator = metadata.indexOf(0)
            if (separator <= 0 || separator == metadata.lastIndex) return@use null
            val storedGroupId = metadata.copyOfRange(0, separator).decodeToString()
            val photoEtag = metadata.copyOfRange(separator + 1, metadata.size).decodeToString()
            if (storedGroupId != groupId || photoEtag.isBlank()) return@use null
            if (!hasBoundedPhoto(snapshot.data)) return@use null
            CachedGroupPhoto(previewHandle(key, photoEtag), photoEtag)
        }
    }

    override fun write(groupId: String, bytes: ByteArray, photoEtag: String): CachedGroupPhoto? {
        if (groupId.isBlank() || photoEtag.isBlank()) return null
        if (bytes.size.toLong() !in 1..EncodedGroupPhoto.MAX_GROUP_PHOTO_BYTES) return null
        val metadata = groupId.encodeToByteArray() + byteArrayOf(0) + photoEtag.encodeToByteArray()
        if (metadata.size > MAX_METADATA_BYTES) return null
        val key = groupKey(groupId)
        val editor = diskCache.openEditor(key) ?: return null
        return try {
            diskCache.fileSystem.write(editor.data) { write(bytes) }
            diskCache.fileSystem.write(editor.metadata) { write(metadata) }
            editor.commit()
            CachedGroupPhoto(previewHandle(key, photoEtag), photoEtag)
        } catch (_: Throwable) {
            runCatching { editor.abort() }
            null
        }
    }

    override fun read(preview: GroupPhotoPreviewHandle): ByteArray? {
        val match = HANDLE_PATTERN.matchEntire(preview.value) ?: return null
        val key = match.groupValues[1]
        val expectedVersion = match.groupValues[2]
        val snapshot = diskCache.openSnapshot(key) ?: return null
        return snapshot.use {
            val metadata = readBounded(snapshot.metadata, MAX_METADATA_BYTES) ?: return@use null
            val separator = metadata.indexOf(0)
            if (separator <= 0 || separator == metadata.lastIndex) return@use null
            val photoEtag = metadata.copyOfRange(separator + 1, metadata.size).decodeToString()
            if (hash(photoEtag) != expectedVersion) return@use null
            readBounded(snapshot.data, EncodedGroupPhoto.MAX_GROUP_PHOTO_BYTES.toInt())
        }
    }

    override fun evict(groupId: String) {
        if (groupId.isNotBlank()) diskCache.remove(groupKey(groupId))
    }

    override fun clearAll() {
        diskCache.clear()
    }

    fun shutdown() {
        diskCache.shutdown()
    }

    private fun hasBoundedPhoto(path: Path): Boolean =
        diskCache.fileSystem.metadata(path).size in 1..EncodedGroupPhoto.MAX_GROUP_PHOTO_BYTES

    private fun readBounded(path: Path, maximumBytes: Int): ByteArray? {
        val size = diskCache.fileSystem.metadata(path).size ?: return null
        if (size !in 1..maximumBytes.toLong()) return null
        return runCatching { diskCache.fileSystem.read(path) { readByteArray() } }.getOrNull()
    }

    private fun previewHandle(key: String, photoEtag: String) =
        GroupPhotoPreviewHandle("$HANDLE_PREFIX$key:${hash(photoEtag)}")

    private fun groupKey(groupId: String): String = "group-photo-${hash(groupId)}"

    private fun hash(value: String): String {
        var hash = FNV_OFFSET_BASIS
        value.encodeToByteArray().forEach { byte ->
            hash = (hash xor byte.toUByte().toULong()) * FNV_PRIME
        }
        return hash.toString(16)
    }

    private companion object {
        const val DEFAULT_DIRECTORY = "saqz-private-group-photos"
        const val HANDLE_PREFIX = "coil-group-photo:"
        const val MAX_CACHE_BYTES = 25L * 1024L * 1024L
        const val MAX_METADATA_BYTES = 8 * 1024
        const val FNV_OFFSET_BASIS = 14695981039346656037UL
        const val FNV_PRIME = 1099511628211UL
        val HANDLE_PATTERN = Regex("${HANDLE_PREFIX}(group-photo-[0-9a-f]+):([0-9a-f]+)")
    }
}
