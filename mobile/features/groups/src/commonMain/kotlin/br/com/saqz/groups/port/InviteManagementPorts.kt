package br.com.saqz.groups.port

sealed interface GroupInviteUrlReadResult {
    data class Success(val inviteUrl: String?) : GroupInviteUrlReadResult
    data object Failure : GroupInviteUrlReadResult
}

sealed interface GroupInviteUrlWriteResult {
    data object Success : GroupInviteUrlWriteResult
    data object Failure : GroupInviteUrlWriteResult
}

fun interface GroupInviteUrlReadCallback {
    fun complete(result: GroupInviteUrlReadResult)
}

fun interface GroupInviteUrlWriteCallback {
    fun complete(result: GroupInviteUrlWriteResult)
}

interface GroupInviteUrlStorePort {
    fun read(groupId: String, done: GroupInviteUrlReadCallback)
    fun write(groupId: String, inviteUrl: String?, done: GroupInviteUrlWriteCallback)
}

enum class InviteNativeFailureCode { PROVIDER_UNAVAILABLE }

sealed interface InviteNativeOperationResult {
    data object Success : InviteNativeOperationResult
    data object Cancelled : InviteNativeOperationResult
    data class Failure(val code: InviteNativeFailureCode) : InviteNativeOperationResult
}

data class InviteShareImage(val pngBytes: ByteArray) {
    override fun equals(other: Any?): Boolean = other is InviteShareImage && pngBytes.contentEquals(other.pngBytes)

    override fun hashCode(): Int = pngBytes.contentHashCode()
}

interface NativeInviteSharePort {
    fun shareText(text: String, done: (InviteNativeOperationResult) -> Unit)
    fun shareImage(image: InviteShareImage, done: (InviteNativeOperationResult) -> Unit)
    fun saveImage(image: InviteShareImage, done: (InviteNativeOperationResult) -> Unit)
}

interface NativeInviteClipboardPort {
    fun copyText(text: String, done: (InviteNativeOperationResult) -> Unit)
}
