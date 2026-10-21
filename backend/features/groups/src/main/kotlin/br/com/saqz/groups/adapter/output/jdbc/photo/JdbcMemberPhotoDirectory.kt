package br.com.saqz.groups.adapter.output.jdbc.photo

import br.com.saqz.groups.application.photo.MemberPhoto
import br.com.saqz.groups.application.photo.MemberPhotoDirectory
import br.com.saqz.groups.application.photo.ReadMemberPhotoResult
import java.util.UUID
import javax.sql.DataSource
import org.springframework.jdbc.core.simple.JdbcClient

class JdbcMemberPhotoDirectory(dataSource: DataSource) : MemberPhotoDirectory {
    private val jdbc = JdbcClient.create(dataSource)

    override fun tags(viewerId: UUID, groupId: UUID, userIds: Collection<UUID>): Map<UUID, String> {
        if (userIds.isEmpty()) return emptyMap()
        return jdbc.sql(
            """
            SELECT photos.user_id, encode(photos.sha256_digest, 'hex') AS digest
            FROM access_user_photos photos
            JOIN group_memberships memberships
              ON memberships.user_id = photos.user_id AND memberships.group_id = :groupId
            JOIN access_groups groups ON groups.id = memberships.group_id AND groups.deleted_at IS NULL
            WHERE photos.user_id IN (:userIds)
              AND EXISTS (SELECT 1 FROM group_memberships viewer
                          WHERE viewer.group_id = :groupId AND viewer.user_id = :viewerId)
              AND NOT EXISTS (SELECT 1 FROM user_blocks blocks
                              WHERE blocks.blocker_id = :viewerId AND blocks.blocked_id = photos.user_id)
            """.trimIndent(),
        )
            .param("groupId", groupId)
            .param("viewerId", viewerId)
            .param("userIds", userIds)
            .query { rs, _ -> rs.getObject("user_id", UUID::class.java) to rs.getString("digest") }
            .list()
            .toMap()
    }

    override fun read(viewerId: UUID, groupId: UUID, userId: UUID): ReadMemberPhotoResult {
        val viewerIsMember = jdbc.sql(
            """
            SELECT EXISTS (
                SELECT 1 FROM group_memberships memberships
                JOIN access_groups groups ON groups.id = memberships.group_id AND groups.deleted_at IS NULL
                WHERE memberships.group_id = :groupId AND memberships.user_id = :viewerId
            )
            """.trimIndent(),
        )
            .param("groupId", groupId)
            .param("viewerId", viewerId)
            .query(Boolean::class.java)
            .single()
        if (!viewerIsMember) return ReadMemberPhotoResult.Forbidden
        return jdbc.sql(
            """
            SELECT photos.photo_bytes, photos.byte_size, encode(photos.sha256_digest, 'hex') AS digest
            FROM access_user_photos photos
            JOIN group_memberships memberships
              ON memberships.user_id = photos.user_id AND memberships.group_id = :groupId
            WHERE photos.user_id = :userId
              AND NOT EXISTS (SELECT 1 FROM user_blocks blocks
                              WHERE blocks.blocker_id = :viewerId AND blocks.blocked_id = photos.user_id)
            """.trimIndent(),
        )
            .param("groupId", groupId)
            .param("viewerId", viewerId)
            .param("userId", userId)
            .query { rs, _ ->
                MemberPhoto(rs.getBytes("photo_bytes"), rs.getLong("byte_size"), rs.getString("digest"))
            }
            .optional()
            .map<ReadMemberPhotoResult>(ReadMemberPhotoResult::Found)
            .orElse(ReadMemberPhotoResult.NotFound)
    }
}
