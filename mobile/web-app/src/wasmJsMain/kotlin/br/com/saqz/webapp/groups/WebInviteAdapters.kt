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
import br.com.saqz.webapp.interop.BrowserOutcome
import br.com.saqz.webapp.interop.BrowserShare
import br.com.saqz.webapp.interop.WebStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class PersistedInviteUrl(val inviteUrl: String, val expiresAt: String? = null, val revision: String? = null)

/**
 * Cache do link de convite por grupo (localStorage) e compartilhamento/cópia pelo navegador.
 * `scope` não é usado: a chamada ao navegador precisa ser síncrona com o toque (ativação do usuário).
 */
internal class WebInviteAdapters(@Suppress("UnusedPrivateProperty") scope: CoroutineScope) {
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
            val value = cache?.let {
                json.encodeToString(PersistedInviteUrl.serializer(), PersistedInviteUrl(it.inviteUrl, it.expiresAt, it.revision))
            }
            val written = WebStorage.write(key(groupId), value)
            done.complete(if (written) GroupInviteUrlWriteResult.Success else GroupInviteUrlWriteResult.Failure)
        }

        private fun key(groupId: String) = "saqz.groups.inviteUrl.$groupId"
    }

    /**
     * Compartilhar pelo navegador. Fechar a folha conta como sucesso: a folha nativa do Android e do iOS
     * nunca informa desistência, e as telas tratam `Cancelled` como erro. Sem `navigator.share` o texto falha
     * (a tela já tem "Copiar link"); a imagem cai no download, que é o que o navegador consegue fazer.
     */
    val share: NativeInviteSharePort = object : NativeInviteSharePort {
        override fun shareText(text: String, done: (InviteNativeOperationResult) -> Unit) {
            if (BrowserShare.canShare) BrowserShare.shareText(text) { done(it.toInviteResult()) } else unavailable(done)
        }

        override fun shareImage(image: InviteShareImage, done: (InviteNativeOperationResult) -> Unit) {
            if (image.pngBytes.isEmpty()) return unavailable(done)
            BrowserShare.shareFile(image.pngBytes, "saqz-convite.png", PNG) { done(it.toInviteResult()) }
        }

        override fun saveImage(image: InviteShareImage, done: (InviteNativeOperationResult) -> Unit) {
            if (image.pngBytes.isEmpty()) return unavailable(done)
            BrowserShare.downloadFile(image.pngBytes, "saqz-convite.png", PNG) { done(it.toInviteResult()) }
        }
    }

    val clipboard: NativeInviteClipboardPort = object : NativeInviteClipboardPort {
        override fun copyText(text: String, done: (InviteNativeOperationResult) -> Unit) =
            BrowserShare.copyText(text) { done(it.toInviteResult()) }
    }

    private fun BrowserOutcome.toInviteResult(): InviteNativeOperationResult = when (this) {
        BrowserOutcome.DONE -> InviteNativeOperationResult.Success
        BrowserOutcome.CANCELLED -> InviteNativeOperationResult.Success
        BrowserOutcome.FAILED -> InviteNativeOperationResult.Failure(InviteNativeFailureCode.PROVIDER_UNAVAILABLE)
    }

    private fun unavailable(done: (InviteNativeOperationResult) -> Unit) =
        done(InviteNativeOperationResult.Failure(InviteNativeFailureCode.PROVIDER_UNAVAILABLE))

    private companion object {
        const val PNG = "image/png"
    }
}
