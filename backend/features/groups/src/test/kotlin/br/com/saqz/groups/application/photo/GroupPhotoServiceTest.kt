package br.com.saqz.groups.application.photo

import br.com.saqz.groups.application.read.GetGroup
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.application.read.GroupReadSnapshot
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Foto do grupo: só dono e admin trocam ou tiram; quem é do grupo vê; versão velha não sobrescreve. */
class GroupPhotoServiceTest {
    private val actor = UUID.randomUUID()
    private val group = UUID.randomUUID()
    private val validPhoto = ValidatedGroupPhoto(byteArrayOf(1, 2, 3), GroupPhotoMediaType.PNG, 400, 300, byteArrayOf(9))
    private var role: GroupRole? = GroupRole.OWNER
    private var validation: GroupPhotoValidationResult = GroupPhotoValidationResult.Valid(validPhoto)
    private val seen = mutableListOf<Pair<String, String>>()
    private val repository = FakeRepository()
    private val service = GroupPhotoService(
        GetGroup(
            object : GroupReadRepository {
                override fun find(key: GroupReadKey) = if (key.groupId == group) {
                    GroupReadSnapshot(group, AccessName.from("Grupo"), IanaTimeZone.from("UTC"), role, 5)
                } else null
            },
            GroupAccessPolicy(),
        ),
        { type, input -> seen += type to input.readBytes().decodeToString(); validation },
        repository,
    )

    private fun upload(version: Long = 5, input: InputStream = ByteArrayInputStream("bytes".toByteArray())) =
        service.upload(actor, group, version, "image/png", input)

    @Test fun `dono e admin trocam a foto e recebem a versão da foto e a nova versão do grupo`() {
        listOf(GroupRole.OWNER, GroupRole.ADMIN).forEach {
            role = it
            repository.next = GroupPhotoWriteResult.Replaced(stored(version = 3), groupVersion = 6)
            assertEquals(UploadGroupPhotoResult.Success(photoVersion = 3, groupVersion = 6), upload())
        }
        val command = repository.replaced.last()
        assertEquals(ReplaceGroupPhotoCommand(group, 5, actor, validPhoto), command)
        assertEquals(listOf("image/png" to "bytes", "image/png" to "bytes"), seen)
    }

    @Test fun `atleta é barrado antes de a imagem ser lida`() {
        role = GroupRole.ATHLETE
        assertEquals(UploadGroupPhotoResult.AccessForbidden, upload())
        assertTrue(seen.isEmpty())
        assertTrue(repository.replaced.isEmpty())
    }

    @Test fun `quem não é do grupo, ou grupo que não existe, recebe não encontrado`() {
        role = null
        assertEquals(UploadGroupPhotoResult.GroupNotFound, upload())
        role = GroupRole.OWNER
        assertEquals(UploadGroupPhotoResult.GroupNotFound, service.upload(actor, UUID.randomUUID(), 5, "image/png", ByteArrayInputStream(ByteArray(0))))
        assertTrue(seen.isEmpty())
    }

    @Test fun `imagem recusada pelo validador volta com o motivo e nada é gravado`() {
        GroupPhotoRejection.entries.forEach {
            validation = GroupPhotoValidationResult.Rejected(it)
            assertEquals(UploadGroupPhotoResult.Invalid(it), upload())
        }
        assertTrue(repository.replaced.isEmpty())
    }

    @Test fun `versão do grupo desatualizada é conflito`() {
        repository.next = GroupPhotoWriteResult.VersionConflict
        assertEquals(UploadGroupPhotoResult.VersionConflict, upload(version = 2))
        assertEquals(2L, repository.replaced.single().expectedGroupVersion)
    }

    @Test fun `trocar a foto não aceita resposta de remoção do repositório`() {
        repository.next = GroupPhotoWriteResult.Removed(7)
        assertFailsWith<IllegalStateException> { upload() }
        repository.next = GroupPhotoWriteResult.AlreadyAbsent(7)
        assertFailsWith<IllegalStateException> { upload() }
    }

    @Test fun `qualquer membro lê a foto, quem é de fora ou grupo sem foto não`() {
        val saved = stored(version = 4)
        repository.photo = saved
        listOf(GroupRole.OWNER, GroupRole.ADMIN, GroupRole.ATHLETE).forEach {
            role = it
            assertEquals(ReadGroupPhotoResult.Success(saved), service.read(actor, group))
        }
        role = null
        assertEquals(ReadGroupPhotoResult.GroupNotFound, service.read(actor, group))
        role = GroupRole.ATHLETE
        repository.photo = null
        assertEquals(ReadGroupPhotoResult.GroupNotFound, service.read(actor, group))
    }

    @Test fun `dono e admin tiram a foto, e tirar de novo continua sucesso com a versão atual`() {
        listOf(GroupRole.OWNER, GroupRole.ADMIN).forEach {
            role = it
            repository.next = GroupPhotoWriteResult.Removed(8)
            assertEquals(RemoveGroupPhotoResult.Success(8), service.remove(actor, group, 7))
        }
        repository.next = GroupPhotoWriteResult.AlreadyAbsent(9)
        assertEquals(RemoveGroupPhotoResult.Success(9), service.remove(actor, group, 7))
        assertEquals(listOf(group to 7L, group to 7L, group to 7L), repository.removed)
    }

    @Test fun `atleta não tira a foto, e quem é de fora não encontra o grupo`() {
        role = GroupRole.ATHLETE
        assertEquals(RemoveGroupPhotoResult.AccessForbidden, service.remove(actor, group, 7))
        role = null
        assertEquals(RemoveGroupPhotoResult.GroupNotFound, service.remove(actor, group, 7))
        assertTrue(repository.removed.isEmpty())
    }

    @Test fun `tirar a foto com versão velha é conflito, e resposta de troca é erro do repositório`() {
        repository.next = GroupPhotoWriteResult.VersionConflict
        assertEquals(RemoveGroupPhotoResult.VersionConflict, service.remove(actor, group, 1))
        repository.next = GroupPhotoWriteResult.Replaced(stored(1), 2)
        assertFailsWith<IllegalStateException> { service.remove(actor, group, 1) }
    }

    private fun stored(version: Long) = StoredGroupPhoto(group, byteArrayOf(1, 2, 3), GroupPhotoMediaType.PNG, 3, 400, 300, byteArrayOf(9), version, actor)

    private class FakeRepository : GroupPhotoRepository {
        var next: GroupPhotoWriteResult = GroupPhotoWriteResult.VersionConflict
        var photo: StoredGroupPhoto? = null
        val replaced = mutableListOf<ReplaceGroupPhotoCommand>()
        val removed = mutableListOf<Pair<UUID, Long>>()
        override fun replace(command: ReplaceGroupPhotoCommand): GroupPhotoWriteResult { replaced += command; return next }
        override fun remove(groupId: UUID, expectedGroupVersion: Long): GroupPhotoWriteResult { removed += groupId to expectedGroupVersion; return next }
        override fun read(groupId: UUID) = photo
        override fun readMetadata(groupId: UUID) = error("unused")
    }
}
