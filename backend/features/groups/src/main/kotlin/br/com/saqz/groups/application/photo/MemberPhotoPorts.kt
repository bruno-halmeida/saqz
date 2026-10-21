package br.com.saqz.groups.application.photo

import java.util.UUID

/** Foto de perfil de um membro vista por outro membro do mesmo grupo (MEM-006). */
data class MemberPhoto(val bytes: ByteArray, val byteSize: Long, val digest: String)

sealed interface ReadMemberPhotoResult {
    data class Found(val photo: MemberPhoto) : ReadMemberPhotoResult

    /** Quem pede não é membro do grupo. */
    data object Forbidden : ReadMemberPhotoResult

    /** Alvo fora do grupo, bloqueado por quem pede ou sem foto: a resposta não diz qual. */
    data object NotFound : ReadMemberPhotoResult
}

/**
 * Leitura da foto de perfil no contexto do grupo. A foto mora em `access`; esta porta é
 * o que `groups` enxerga dela.
 */
interface MemberPhotoDirectory {
    /**
     * `photoTag` de cada pessoa de [userIds] que é membro de [groupId] e tem foto. Fica de
     * fora quem [viewerId] bloqueou e quem não é mais do grupo (registro antigo = iniciais).
     */
    fun tags(viewerId: UUID, groupId: UUID, userIds: Collection<UUID>): Map<UUID, String>

    fun read(viewerId: UUID, groupId: UUID, userId: UUID): ReadMemberPhotoResult
}

fun MemberPhotoDirectory.tagsOf(viewerId: UUID, groupId: UUID, userIds: Iterable<UUID?>): Map<UUID, String> =
    userIds.filterNotNull().toSet().let { if (it.isEmpty()) emptyMap() else tags(viewerId, groupId, it) }

/** Ninguém tem foto: padrão dos controllers montados sem a porta (testes antigos). */
object NoMemberPhotos : MemberPhotoDirectory {
    override fun tags(viewerId: UUID, groupId: UUID, userIds: Collection<UUID>): Map<UUID, String> = emptyMap()

    override fun read(viewerId: UUID, groupId: UUID, userId: UUID): ReadMemberPhotoResult = ReadMemberPhotoResult.NotFound
}
