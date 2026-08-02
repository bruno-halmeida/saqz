package br.com.saqz.profile.domain

/**
 * Resultado da escolha nativa de uma foto de usuário.
 *
 * A plataforma devolve bytes já prontos para o multipart. O domínio não conhece URI,
 * arquivo temporário ou a API de permissões de nenhuma das plataformas.
 */
sealed interface ProfilePhotoSelectionResult {
    data class Selected(
        val bytes: ByteArray,
        val mediaType: String,
    ) : ProfilePhotoSelectionResult {
        init {
            require(bytes.isNotEmpty())
            require(mediaType.isNotBlank())
        }

        override fun equals(other: Any?): Boolean = other is Selected &&
            bytes.contentEquals(other.bytes) && mediaType == other.mediaType

        override fun hashCode(): Int = 31 * bytes.contentHashCode() + mediaType.hashCode()
    }

    data object Cancelled : ProfilePhotoSelectionResult
    data object CameraPermissionDenied : ProfilePhotoSelectionResult
    data object LibraryPermissionDenied : ProfilePhotoSelectionResult
    data object Failed : ProfilePhotoSelectionResult
}

/** Capacidade nativa de escolher e preparar uma foto para o upload do perfil. */
interface ProfilePhotoSelectionPort {
    suspend fun chooseCamera(): ProfilePhotoSelectionResult
    suspend fun chooseLibrary(): ProfilePhotoSelectionResult
}
