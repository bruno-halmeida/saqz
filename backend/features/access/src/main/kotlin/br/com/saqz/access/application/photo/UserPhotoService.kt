package br.com.saqz.access.application.photo

import java.io.InputStream
import java.util.UUID

sealed interface UploadUserPhotoResult {
    data class Success(val version: Long) : UploadUserPhotoResult

    data class Rejected(val reason: UserPhotoRejection) : UploadUserPhotoResult
}

/**
 * A foto e do proprio dono da sessao: nao ha papel para conferir nem versao de
 * agregado para travar, entao o ultimo envio vence.
 */
class UserPhotoService(
    private val converter: UserPhotoConversionPort,
    private val repository: UserPhotoRepository,
) {
    fun upload(userId: UUID, declaredContentType: String, input: InputStream): UploadUserPhotoResult =
        when (val conversion = converter.convert(declaredContentType, input)) {
            is UserPhotoConversion.Rejected -> UploadUserPhotoResult.Rejected(conversion.reason)
            is UserPhotoConversion.Converted ->
                UploadUserPhotoResult.Success(repository.replace(userId, conversion.photo))
        }

    fun read(userId: UUID): StoredUserPhoto? = repository.read(userId)

    fun remove(userId: UUID) = repository.remove(userId)
}
