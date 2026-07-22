package br.com.saqz.androidapp.access

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import br.com.saqz.access.domain.port.LocalAccessStatePort
import br.com.saqz.access.domain.port.NativeFailureCode
import br.com.saqz.access.domain.port.NativeSharePort
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.port.ValueCallback
import br.com.saqz.access.domain.port.ValueResult
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal interface AndroidAccessStateStore {
    fun readSelectedGroupId(): String?
    fun writeSelectedGroupId(value: String?)
    fun readPendingInvite(): String?
    fun writePendingInvite(value: String?)
    fun readPendingAttendanceLink(): String?
    fun writePendingAttendanceLink(value: String?)
}

internal class AndroidLocalAccessStateAdapter(
    private val store: AndroidAccessStateStore,
) : LocalAccessStatePort {
    override fun readSelectedGroupId(done: ValueCallback) = read(done, store::readSelectedGroupId)

    override fun writeSelectedGroupId(value: String?, done: ResultCallback) =
        write(done) { store.writeSelectedGroupId(value) }

    override fun readPendingInvite(done: ValueCallback) = read(done, store::readPendingInvite)

    override fun writePendingInvite(value: String?, done: ResultCallback) =
        write(done) { store.writePendingInvite(value) }

    fun readPendingAttendanceLink(done: ValueCallback) = read(done, store::readPendingAttendanceLink)

    fun writePendingAttendanceLink(value: String?, done: ResultCallback) =
        write(done) { store.writePendingAttendanceLink(value) }

    private fun read(done: ValueCallback, block: () -> String?) {
        try {
            done.complete(ValueResult.Success(block()))
        } catch (_: Exception) {
            done.complete(ValueResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE))
        }
    }

    private fun write(done: ResultCallback, block: () -> Unit) {
        try {
            block()
            done.complete(OperationResult.Success)
        } catch (_: Exception) {
            done.complete(OperationResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE))
        }
    }
}

internal class AndroidEncryptedAccessStateStore(
    context: Context,
    private val cipher: AccessStateCipher = AndroidKeystoreAccessStateCipher(),
) : AndroidAccessStateStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun readSelectedGroupId(): String? = preferences.getString(SELECTED_GROUP_KEY, null)

    override fun writeSelectedGroupId(value: String?) {
        commit(SELECTED_GROUP_KEY, value)
    }

    override fun readPendingInvite(): String? =
        preferences.getString(PENDING_INVITE_KEY, null)?.let(cipher::decrypt)

    override fun writePendingInvite(value: String?) {
        commit(PENDING_INVITE_KEY, value?.let(cipher::encrypt))
    }

    override fun readPendingAttendanceLink(): String? =
        preferences.getString(PENDING_ATTENDANCE_LINK_KEY, null)?.let(cipher::decrypt)

    override fun writePendingAttendanceLink(value: String?) {
        commit(PENDING_ATTENDANCE_LINK_KEY, value?.let(cipher::encrypt))
    }

    private fun commit(key: String, value: String?) {
        val editor = preferences.edit()
        if (value == null) editor.remove(key) else editor.putString(key, value)
        check(editor.commit()) { "access state commit failed" }
    }

    internal companion object {
        const val PREFERENCES_NAME = "saqz_access_state"
        private const val SELECTED_GROUP_KEY = "selected_group_id"
        private const val PENDING_INVITE_KEY = "pending_invite_ciphertext"
        private const val PENDING_ATTENDANCE_LINK_KEY = "pending_attendance_link_ciphertext"
    }
}

internal interface AccessStateCipher {
    fun encrypt(plaintext: String): String
    fun decrypt(ciphertext: String): String
}

internal class AndroidKeystoreAccessStateCipher : AccessStateCipher {
    override fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return "${cipher.iv.encodeBase64Url()}.${encrypted.encodeBase64Url()}"
    }

    override fun decrypt(ciphertext: String): String {
        val parts = ciphertext.split('.', limit = 2)
        require(parts.size == 2) { "invalid encrypted access state" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_LENGTH_BITS, parts[0].decodeBase64Url()))
        return cipher.doFinal(parts[1].decodeBase64Url()).toString(Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey = synchronized(KEY_LOCK) {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setKeySize(KEY_SIZE_BITS)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build(),
                )
            }
            .generateKey()
    }

    private fun ByteArray.encodeBase64Url(): String = Base64.encodeToString(
        this,
        Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE,
    )

    private fun String.decodeBase64Url(): ByteArray = Base64.decode(
        this,
        Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE,
    )

    private companion object {
        val KEY_LOCK = Any()
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "saqz_access_pending_invite_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val TAG_LENGTH_BITS = 128
    }
}

internal interface AndroidShareLauncher {
    fun launch(text: String)
}

internal class AndroidShareAdapter(
    private val launcher: AndroidShareLauncher,
) : NativeSharePort {
    override fun share(text: String, done: ResultCallback) {
        try {
            launcher.launch(text)
            done.complete(OperationResult.Success)
        } catch (_: Exception) {
            done.complete(OperationResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE))
        }
    }

    override fun toString() = "AndroidShareAdapter"
}

internal class AndroidSharesheetLauncher(
    private val context: Context,
) : AndroidShareLauncher {
    override fun launch(text: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
        val chooser = Intent.createChooser(send, null)
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
