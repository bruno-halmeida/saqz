package br.com.saqz.androidapp.groups.invite

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import br.com.saqz.groups.port.GroupInviteUrlCache
import br.com.saqz.groups.port.GroupInviteUrlReadCallback
import br.com.saqz.groups.port.GroupInviteUrlReadResult
import br.com.saqz.groups.port.GroupInviteUrlStorePort
import br.com.saqz.groups.port.GroupInviteUrlWriteCallback
import br.com.saqz.groups.port.GroupInviteUrlWriteResult
import br.com.saqz.groups.port.InviteNativeFailureCode
import br.com.saqz.groups.port.InviteNativeOperationResult
import br.com.saqz.groups.port.InviteShareImage
import br.com.saqz.groups.port.NativeInviteSharePort
import br.com.saqz.groups.port.NativeInviteClipboardPort
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

internal class AndroidInviteUrlStore(context: Context) : GroupInviteUrlStorePort {
    private val preferences = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    override fun read(groupId: String, done: GroupInviteUrlReadCallback) {
        runCatching {
            preferences.getString(urlKey(groupId), null)?.let { inviteUrl ->
                GroupInviteUrlCache(inviteUrl, preferences.getString(expiresAtKey(groupId), null))
            }
        }
            .fold(
                onSuccess = { done.complete(GroupInviteUrlReadResult.Success(it)) },
                onFailure = { done.complete(GroupInviteUrlReadResult.Failure) },
            )
    }

    override fun write(groupId: String, cache: GroupInviteUrlCache?, done: GroupInviteUrlWriteCallback) {
        val committed = runCatching {
            val editor = preferences.edit()
            if (cache == null) {
                editor.remove(urlKey(groupId)).remove(expiresAtKey(groupId))
            } else {
                editor.putString(urlKey(groupId), cache.inviteUrl)
                cache.expiresAt?.let { editor.putString(expiresAtKey(groupId), it) }
                    ?: editor.remove(expiresAtKey(groupId))
            }
            editor.commit()
        }.getOrDefault(false)
        done.complete(if (committed) GroupInviteUrlWriteResult.Success else GroupInviteUrlWriteResult.Failure)
    }

    private fun urlKey(groupId: String) = "$KEY_PREFIX$groupId"
    private fun expiresAtKey(groupId: String) = "$EXPIRES_AT_PREFIX$groupId"

    private companion object {
        const val NAME = "saqz_group_invites_v1"
        const val KEY_PREFIX = "invite-url:"
        const val EXPIRES_AT_PREFIX = "invite-expires-at:"
    }
}

internal class AndroidInviteShareAdapter(
    private val context: Context,
) : NativeInviteSharePort, NativeInviteClipboardPort {
    private val directory = File(context.cacheDir, "attendance-share")

    override fun shareText(text: String, done: (InviteNativeOperationResult) -> Unit) {
        runCatching {
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text)
            val chooser = Intent.createChooser(send, null)
            if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }.fold(
            onSuccess = { done(InviteNativeOperationResult.Success) },
            onFailure = { done(InviteNativeOperationResult.Failure(InviteNativeFailureCode.PROVIDER_UNAVAILABLE)) },
        )
    }

    override fun shareImage(image: InviteShareImage, done: (InviteNativeOperationResult) -> Unit) {
        runCatching {
            val file = writeCacheImage(image)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.group-photo-files", file)
            val send = Intent(Intent.ACTION_SEND)
                .setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val chooser = Intent.createChooser(send, null)
            if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }.fold(
            onSuccess = { done(InviteNativeOperationResult.Success) },
            onFailure = { done(InviteNativeOperationResult.Failure(InviteNativeFailureCode.PROVIDER_UNAVAILABLE)) },
        )
    }

    override fun saveImage(image: InviteShareImage, done: (InviteNativeOperationResult) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Android 6–9 exige WRITE_EXTERNAL_STORAGE em runtime para MediaStore.
            // Este port não pede permissão; o share sheet genérico oferece o salvamento.
            shareImage(image, done)
            return
        }
        runCatching {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "saqz-convite-${UUID.randomUUID()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Saqz")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
            try {
                resolver.openOutputStream(uri).use { output ->
                    requireNotNull(output).write(image.pngBytes)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    resolver.update(uri, ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    }, null, null)
                }
            } catch (error: Throwable) {
                resolver.delete(uri, null, null)
                throw error
            }
        }.fold(
            onSuccess = { done(InviteNativeOperationResult.Success) },
            onFailure = { done(InviteNativeOperationResult.Failure(InviteNativeFailureCode.PROVIDER_UNAVAILABLE)) },
        )
    }

    override fun copyText(text: String, done: (InviteNativeOperationResult) -> Unit) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) {
            done(InviteNativeOperationResult.Failure(InviteNativeFailureCode.PROVIDER_UNAVAILABLE))
            return
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("Convite do grupo", text))
        done(InviteNativeOperationResult.Success)
    }

    private fun writeCacheImage(image: InviteShareImage): File {
        require(image.pngBytes.isNotEmpty())
        directory.mkdirs()
        return File(directory, "invite-share-${UUID.randomUUID()}.png").also { file ->
            FileOutputStream(file).use { output -> output.write(image.pngBytes) }
            require(BitmapFactory.decodeFile(file.path) != null)
        }
    }
}
