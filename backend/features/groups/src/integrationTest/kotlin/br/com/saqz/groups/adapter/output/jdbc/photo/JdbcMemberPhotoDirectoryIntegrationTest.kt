package br.com.saqz.groups.adapter.output.jdbc.photo

import br.com.saqz.groups.application.photo.ReadMemberPhotoResult
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import java.security.MessageDigest
import java.util.UUID
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient

/** MEM-006: quem vê a foto de perfil de quem dentro do grupo. */
class JdbcMemberPhotoDirectoryIntegrationTest {
    private val source = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    private val jdbc = JdbcClient.create(source)
    private val directory = JdbcMemberPhotoDirectory(source)
    private val owner = user("Dona")
    private val athlete = user("Atleta")
    private val noPhoto = user("Sem Foto")
    private val outsider = user("De Fora")
    private val group = group(owner)

    init {
        membership(group, owner, "ADMIN")
        membership(group, athlete)
        membership(group, noPhoto)
        // Gravadas direto na tabela, como as fotos cadastradas antes da mudança.
        photo(owner, "foto-dona")
        photo(athlete, "foto-atleta")
        photo(outsider, "foto-de-fora")
    }

    @Test fun `a member sees another member's photo saved before the change`() {
        val found = assertIs<ReadMemberPhotoResult.Found>(directory.read(owner, group, athlete))
        assertContentEquals("foto-atleta".encodeToByteArray(), found.photo.bytes)
        assertEquals(hex("foto-atleta"), found.photo.digest)
        assertEquals(mapOf(athlete to hex("foto-atleta"), owner to hex("foto-dona")), directory.tags(owner, group, listOf(owner, athlete, noPhoto)))
    }

    @Test fun `someone outside the group is forbidden`() {
        assertEquals(ReadMemberPhotoResult.Forbidden, directory.read(outsider, group, athlete))
        assertEquals(emptyMap(), directory.tags(outsider, group, listOf(athlete)))
    }

    @Test fun `a former member is forbidden and a former member's photo is not found`() {
        jdbc.sql("DELETE FROM group_memberships WHERE group_id = :group AND user_id = :user")
            .param("group", group).param("user", athlete).update()

        assertEquals(ReadMemberPhotoResult.Forbidden, directory.read(athlete, group, owner))
        assertEquals(ReadMemberPhotoResult.NotFound, directory.read(owner, group, athlete))
        assertEquals(emptyMap(), directory.tags(owner, group, listOf(athlete)))
    }

    @Test fun `a non member target is not found even with a photo`() {
        assertEquals(ReadMemberPhotoResult.NotFound, directory.read(owner, group, outsider))
        assertEquals(emptyMap(), directory.tags(owner, group, listOf(outsider)))
    }

    @Test fun `the blocker sees initials and the blocked still sees the blocker's photo`() {
        jdbc.sql("INSERT INTO user_blocks (blocker_id, blocked_id, created_at) VALUES (:blocker, :blocked, now())")
            .param("blocker", owner).param("blocked", athlete).update()

        assertEquals(ReadMemberPhotoResult.NotFound, directory.read(owner, group, athlete))
        assertEquals(emptyMap(), directory.tags(owner, group, listOf(athlete)))
        assertIs<ReadMemberPhotoResult.Found>(directory.read(athlete, group, owner))
        assertEquals(mapOf(owner to hex("foto-dona")), directory.tags(athlete, group, listOf(owner)))
    }

    @Test fun `a member without photo is not found and has no tag`() {
        assertEquals(ReadMemberPhotoResult.NotFound, directory.read(athlete, group, noPhoto))
        assertEquals(emptyMap(), directory.tags(athlete, group, listOf(noPhoto)))
    }

    @Test fun `a deleted group shows no photo`() {
        jdbc.sql("UPDATE access_groups SET deleted_at = now() WHERE id = :group").param("group", group).update()

        assertEquals(ReadMemberPhotoResult.Forbidden, directory.read(owner, group, athlete))
        assertEquals(emptyMap(), directory.tags(owner, group, listOf(athlete)))
    }

    private fun hex(content: String) =
        MessageDigest.getInstance("SHA-256").digest(content.encodeToByteArray()).joinToString("") { "%02x".format(it) }

    private fun photo(user: UUID, content: String) {
        val bytes = content.encodeToByteArray()
        jdbc.sql(
            "INSERT INTO access_user_photos (user_id, photo_bytes, byte_size, width, height, sha256_digest, created_at, updated_at) " +
                "VALUES (:user, :bytes, :size, 1, 1, :digest, now(), now())",
        )
            .param("user", user).param("bytes", bytes).param("size", bytes.size.toLong())
            .param("digest", MessageDigest.getInstance("SHA-256").digest(bytes)).update()
    }

    private fun user(name: String): UUID {
        val id = UUID.randomUUID()
        jdbc.sql("INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) VALUES (:id, :subject, true, :name, now(), now())")
            .param("id", id).param("subject", id.toString()).param("name", name).update()
        return id
    }

    private fun group(owner: UUID): UUID {
        val id = UUID.randomUUID()
        jdbc.sql("INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at) VALUES (:id, :owner, :key, 'Vôlei', 'UTC', now(), now())")
            .param("id", id).param("owner", owner).param("key", UUID.randomUUID()).update()
        return id
    }

    private fun membership(group: UUID, user: UUID, role: String = "ATHLETE") {
        jdbc.sql("INSERT INTO group_memberships (group_id, user_id, role, membership_type, active, created_at, updated_at) VALUES (:group, :user, :role, 'AVULSO', true, now(), now())")
            .param("group", group).param("user", user).param("role", role, java.sql.Types.OTHER).update()
    }
}
