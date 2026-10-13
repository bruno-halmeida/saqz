package br.com.saqz.webapp.groups

import br.com.saqz.groups.port.GroupInviteUrlCache
import br.com.saqz.groups.port.GroupInviteUrlReadCallback
import br.com.saqz.groups.port.GroupInviteUrlReadResult
import br.com.saqz.groups.port.GroupInviteUrlStorePort
import br.com.saqz.groups.port.GroupInviteUrlWriteCallback
import br.com.saqz.groups.port.GroupInviteUrlWriteResult
import br.com.saqz.groups.port.InviteNativeFailureCode
import br.com.saqz.groups.port.InviteNativeOperationResult
import br.com.saqz.groups.port.InviteShareImage
import br.com.saqz.groups.port.NativeInviteClipboardPort
import br.com.saqz.groups.port.NativeInviteSharePort
import br.com.saqz.webapp.interop.WebStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class PersistedInviteUrl(val inviteUrl: String, val expiresAt: String? = null, val revision: String? = null)

/** Cache do link de convite por grupo (localStorage) e compartilhamento/cópia pelo navegador. */
internal class WebInviteAdapters(private val scope: CoroutineScope) {
    private val json = Json { ignoreUnknownKeys = true }

    val urlStore: GroupInviteUrlStorePort = object : GroupInviteUrlStorePort {
        override fun read(groupId: String, done: GroupInviteUrlReadCallback) {
            val raw = WebStorage.read(key(groupId))
            val cache = raw?.let { value ->
                runCatching { json.decodeFromString(PersistedInviteUrl.serializer(), value) }.getOrNull()
            }
            if (raw != null && cache == null) {
                done.complete(GroupInviteUrlReadResult.Failure)
                return
            }
            done.complete(
                GroupInviteUrlReadResult.Success(cache?.let { GroupInviteUrlCache(it.inviteUrl, it.expiresAt, it.revision) }),
            )
        }

        override fun write(groupId: String, cache: GroupInviteUrlCache?, done: GroupInviteUrlWriteCallback) {
            val value = cache?.let { json.encodeToString(PersistedInviteUrl.serializer(), PersistedInviteUrl(it.inviteUrl, it.expiresAt, it.revision)) }
            done.complete(if (WebStorage.write(key(groupId), value)) GroupInviteUrlWriteResult.Success else GroupInviteUrlWriteResult.Failure)
        }

        private fun key(groupId: String) = "saqz.groups.inviteUrl.$groupId"
    }

    // STUB: substituir por navigator.share / navigator.clipboard (imagem como File; saveImage = download).
    val share: NativeInviteSharePort = object : NativeInviteSharePort {
        override fun shareText(text: String, done: (InviteNativeOperationResult) -> Unit) = unavailable(done)
        override fun shareImage(image: InviteShareImage, done: (InviteNativeOperationResult) -> Unit) = unavailable(done)
        override fun saveImage(image: InviteShareImage, done: (InviteNativeOperationResult) -> Unit) = unavailable(done)
    }

    // STUB: substituir por navigator.clipboard.writeText.
    val clipboard: NativeInviteClipboardPort = object : NativeInviteClipboardPort {
        override fun copyText(text: String, done: (InviteNativeOperationResult) -> Unit) = unavailable(done)
    }

    private fun unavailable(done: (InviteNativeOperationResult) -> Unit) =
        done(InviteNativeOperationResult.Failure(InviteNativeFailureCode.PROVIDER_UNAVAILABLE))
}
