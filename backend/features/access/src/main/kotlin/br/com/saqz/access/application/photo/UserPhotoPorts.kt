package br.com.saqz.access.application.photo

import java.io.InputStream
import java.util.UUID

/**
 * O envio e sempre recomprimido antes de ser gravado, entao a foto guardada tem
 * um tipo so. O grupo guarda o original e por isso carrega um tipo por linha.
 */
const val USER_PHOTO_MEDIA_TYPE: String = "image/jpeg"

enum class UserPhotoRejection {
    EMPTY,
    TOO_LARGE,
    UNSUPPORTED_TYPE,
    INVALID_IMAGE,
    DIMENSIONS_TOO_LARGE,
}

data class UserPhotoImage(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val sha256Digest: ByteArray,
) {
    val byteSize: Long get() = bytes.size.toLong()
}

sealed interface UserPhotoConversion {
    data class Converted(val photo: UserPhotoImage) : UserPhotoConversion

    data class Rejected(val reason: UserPhotoRejection) : UserPhotoConversion
}

fun interface UserPhotoConversionPort {
    fun convert(declaredContentType: String, input: InputStream): UserPhotoConversion
}

/**
 * [digest] e o SHA-256 dos bytes guardados em hexadecimal. Ele identifica o
 * conteudo, nao a linha: bytes iguais dao validador igual e bytes diferentes dao
 * validador diferente, em qualquer conta e depois de qualquer remocao.
 */
data class StoredUserPhoto(
    val bytes: ByteArray,
    val byteSize: Long,
    val digest: String,
)

interface UserPhotoRepository {
    fun replace(userId: UUID, photo: UserPhotoImage)

    fun remove(userId: UUID)

    fun read(userId: UUID): StoredUserPhoto?
}
