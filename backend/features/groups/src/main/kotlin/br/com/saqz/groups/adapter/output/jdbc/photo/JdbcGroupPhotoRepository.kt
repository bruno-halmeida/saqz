package br.com.saqz.groups.adapter.output.jdbc.photo

import br.com.saqz.groups.application.photo.GroupPhotoMediaType
import br.com.saqz.groups.application.photo.GroupPhotoMetadata
import br.com.saqz.groups.application.photo.GroupPhotoRepository
import br.com.saqz.groups.application.photo.GroupPhotoWriteResult
import br.com.saqz.groups.application.photo.ReplaceGroupPhotoCommand
import br.com.saqz.groups.application.photo.StoredGroupPhoto
import java.sql.ResultSet
import java.util.UUID
import javax.sql.DataSource
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate

class JdbcGroupPhotoRepository(dataSource: DataSource) : GroupPhotoRepository {
    private val jdbc = JdbcClient.create(dataSource)
    private val transaction = TransactionTemplate(DataSourceTransactionManager(dataSource))

    override fun replace(command: ReplaceGroupPhotoCommand): GroupPhotoWriteResult = transaction.execute {
        val groupVersion = incrementGroupVersion(command.groupId, command.expectedGroupVersion)
            ?: return@execute GroupPhotoWriteResult.VersionConflict
        val photo = jdbc.sql(
            """
            INSERT INTO group_photos (
                group_id, photo_bytes, media_type, byte_size, width, height,
                sha256_digest, version, updated_by, created_at, updated_at
            ) VALUES (
                :groupId, :bytes, :mediaType, :byteSize, :width, :height,
                :digest, 1, :actorId, now(), now()
            )
            ON CONFLICT (group_id) DO UPDATE SET
                photo_bytes = EXCLUDED.photo_bytes,
                media_type = EXCLUDED.media_type,
                byte_size = EXCLUDED.byte_size,
                width = EXCLUDED.width,
                height = EXCLUDED.height,
                sha256_digest = EXCLUDED.sha256_digest,
                version = group_photos.version + 1,
                updated_by = EXCLUDED.updated_by,
                updated_at = now()
            RETURNING group_id, photo_bytes, media_type, byte_size, width, height,
                sha256_digest, version, updated_by
            """.trimIndent(),
        )
            .param("groupId", command.groupId)
            .param("bytes", command.photo.bytes)
            .param("mediaType", command.photo.mediaType.value)
            .param("byteSize", command.photo.byteSize)
            .param("width", command.photo.width)
            .param("height", command.photo.height)
            .param("digest", command.photo.sha256Digest)
            .param("actorId", command.actorId)
            .query { result, _ -> result.toStoredPhoto() }
            .single()
        GroupPhotoWriteResult.Replaced(photo, groupVersion)
    }

    override fun remove(groupId: UUID, expectedGroupVersion: Long): GroupPhotoWriteResult = transaction.execute {
        val currentVersion = jdbc.sql("SELECT version FROM access_groups WHERE id = :groupId FOR UPDATE")
            .param("groupId", groupId)
            .query(Long::class.java)
            .optional()
            .orElse(null)
            ?: return@execute GroupPhotoWriteResult.VersionConflict
        if (currentVersion != expectedGroupVersion) return@execute GroupPhotoWriteResult.VersionConflict
        val deleted = jdbc.sql("DELETE FROM group_photos WHERE group_id = :groupId")
            .param("groupId", groupId)
            .update()
        if (deleted == 0) return@execute GroupPhotoWriteResult.AlreadyAbsent(currentVersion)
        val groupVersion = incrementGroupVersion(groupId, expectedGroupVersion)
            ?: error("locked group version changed during photo removal")
        GroupPhotoWriteResult.Removed(groupVersion)
    }

    override fun read(groupId: UUID): StoredGroupPhoto? = jdbc.sql(
        """
        SELECT group_id, photo_bytes, media_type, byte_size, width, height,
            sha256_digest, version, updated_by
        FROM group_photos
        WHERE group_id = :groupId
        """.trimIndent(),
    )
        .param("groupId", groupId)
        .query { result, _ -> result.toStoredPhoto() }
        .optional()
        .orElse(null)

    override fun readMetadata(groupId: UUID): GroupPhotoMetadata? = jdbc.sql(
        """
        SELECT group_id, media_type, byte_size, width, height, version
        FROM group_photos
        WHERE group_id = :groupId
        """.trimIndent(),
    )
        .param("groupId", groupId)
        .query { result, _ ->
            GroupPhotoMetadata(
                groupId = result.getObject("group_id", UUID::class.java),
                mediaType = GroupPhotoMediaType.entries.first { it.value == result.getString("media_type") },
                byteSize = result.getLong("byte_size"),
                width = result.getInt("width"),
                height = result.getInt("height"),
                version = result.getLong("version"),
            )
        }
        .optional()
        .orElse(null)

    private fun incrementGroupVersion(groupId: UUID, expectedVersion: Long): Long? = jdbc.sql(
        """
        UPDATE access_groups
        SET version = version + 1, updated_at = now()
        WHERE id = :groupId AND version = :expectedVersion
        RETURNING version
        """.trimIndent(),
    )
        .param("groupId", groupId)
        .param("expectedVersion", expectedVersion)
        .query(Long::class.java)
        .optional()
        .orElse(null)

    private fun ResultSet.toStoredPhoto() = StoredGroupPhoto(
        groupId = getObject("group_id", UUID::class.java),
        bytes = getBytes("photo_bytes"),
        mediaType = GroupPhotoMediaType.entries.first { it.value == getString("media_type") },
        byteSize = getLong("byte_size"),
        width = getInt("width"),
        height = getInt("height"),
        sha256Digest = getBytes("sha256_digest"),
        version = getLong("version"),
        updatedBy = getObject("updated_by", UUID::class.java),
    )
}
