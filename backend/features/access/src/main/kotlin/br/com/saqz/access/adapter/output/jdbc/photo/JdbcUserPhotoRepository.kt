package br.com.saqz.access.adapter.output.jdbc.photo

import br.com.saqz.access.application.photo.StoredUserPhoto
import br.com.saqz.access.application.photo.UserPhotoImage
import br.com.saqz.access.application.photo.UserPhotoRepository
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import javax.sql.DataSource

class JdbcUserPhotoRepository(
    dataSource: DataSource,
) : UserPhotoRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun replace(userId: UUID, photo: UserPhotoImage) {
        jdbc.sql(
            """
            INSERT INTO access_user_photos (
                user_id, photo_bytes, byte_size, width, height, sha256_digest, created_at, updated_at
            ) VALUES (
                :userId, :bytes, :byteSize, :width, :height, :digest, now(), now()
            )
            ON CONFLICT (user_id) DO UPDATE SET
                photo_bytes = EXCLUDED.photo_bytes,
                byte_size = EXCLUDED.byte_size,
                width = EXCLUDED.width,
                height = EXCLUDED.height,
                sha256_digest = EXCLUDED.sha256_digest,
                updated_at = now()
            """.trimIndent(),
        )
            .param("userId", userId)
            .param("bytes", photo.bytes)
            .param("byteSize", photo.byteSize)
            .param("width", photo.width)
            .param("height", photo.height)
            .param("digest", photo.sha256Digest)
            .update()
    }

    override fun remove(userId: UUID) {
        jdbc.sql("DELETE FROM access_user_photos WHERE user_id = :userId")
            .param("userId", userId)
            .update()
    }

    override fun read(userId: UUID): StoredUserPhoto? = jdbc.sql(
        """
        SELECT photo_bytes, byte_size, encode(sha256_digest, 'hex') AS digest
        FROM access_user_photos
        WHERE user_id = :userId
        """.trimIndent(),
    )
        .param("userId", userId)
        .query { result, _ ->
            StoredUserPhoto(
                bytes = result.getBytes("photo_bytes"),
                byteSize = result.getLong("byte_size"),
                digest = result.getString("digest"),
            )
        }
        .optional()
        .orElse(null)
}
