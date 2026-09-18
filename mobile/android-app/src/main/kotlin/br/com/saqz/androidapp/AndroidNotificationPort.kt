package br.com.saqz.androidapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import br.com.saqz.groups.domain.communication.NativeNotificationPort
import br.com.saqz.groups.domain.communication.NotificationDevice
import br.com.saqz.groups.domain.communication.NotificationSubscription
import com.google.firebase.messaging.FirebaseMessaging
import java.util.UUID

internal class AndroidNotificationPort(private val context: Context) : NativeNotificationPort {
    private val preferences = context.getSharedPreferences("saqz-notifications", Context.MODE_PRIVATE)
    private var permission: ActivityResultLauncher<String>? = null
    private var pending: ((NotificationDevice?) -> Unit)? = null
    private val listeners = mutableSetOf<() -> Unit>()
    fun attach(activity: ComponentActivity) {
        permission = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val callback = pending
            pending = null
            if (callback != null) { if (granted) token(callback) else callback(null) }
        }
    }
    override fun device(done: (NotificationDevice?) -> Unit) {
        if (BuildConfig.FIREBASE_USE_EMULATOR) { done(null); return }
        val permissionMissing = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= 33 && permissionMissing) {
            if (preferences.getBoolean("permissionAsked", false) || permission == null) { done(null); return }
            preferences.edit().putBoolean("permissionAsked", true).apply()
            pending = done
            permission?.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else token(done)
    }
    private fun token(done: (NotificationDevice?) -> Unit) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) { done(null); return@addOnCompleteListener }
            val installation = preferences.getString("installation", null) ?: UUID.randomUUID().toString().also {
                preferences.edit().putString("installation", it).apply()
            }
            done(NotificationDevice(installation, task.result, "ANDROID"))
        }
    }
    override fun clear(done: (Boolean) -> Unit) {
        if (BuildConfig.FIREBASE_USE_EMULATOR) { done(true); return }
        FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener { done(it.isSuccessful) }
    }
    override fun observe(changed: () -> Unit): NotificationSubscription {
        listeners += changed
        return NotificationSubscription { listeners -= changed }
    }
    fun tokenChanged() { listeners.toList().forEach { it() } }
}
