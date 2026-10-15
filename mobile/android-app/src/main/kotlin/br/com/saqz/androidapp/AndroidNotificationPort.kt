package br.com.saqz.androidapp

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import br.com.saqz.groups.domain.communication.NativeNotificationPort
import br.com.saqz.groups.domain.communication.NotificationDevice
import br.com.saqz.groups.domain.communication.NotificationPermission
import br.com.saqz.groups.domain.communication.NotificationPromptMemory
import br.com.saqz.groups.domain.communication.NotificationSubscription
import com.google.firebase.messaging.FirebaseMessaging
import java.util.UUID

internal class AndroidNotificationPort(private val context: Context) : NativeNotificationPort {
    private val preferences = context.getSharedPreferences("saqz-notifications", Context.MODE_PRIVATE)
    private var permission: ActivityResultLauncher<String>? = null
    private var pending: ((NotificationPermission) -> Unit)? = null
    private val listeners = mutableSetOf<() -> Unit>()
    fun attach(activity: ComponentActivity) {
        permission = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val callback = pending
            pending = null
            callback?.invoke(if (granted) NotificationPermission.Granted else NotificationPermission.Denied)
        }
    }
    /** Só registra o aparelho de quem já permitiu: o pedido do sistema sai por [requestPermission], depois da folha do app. */
    override fun device(done: (NotificationDevice?) -> Unit) {
        if (BuildConfig.FIREBASE_USE_EMULATOR) { done(null); return }
        if (state() == NotificationPermission.Granted) token(done) else done(null)
    }
    private fun state(): NotificationPermission {
        val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val permissionMissing = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        return when {
            enabled && !permissionMissing -> NotificationPermission.Granted
            // O Android 13+ só abre o pedido uma vez por instalação; depois disso só os ajustes.
            permissionMissing && !preferences.getBoolean("permissionAsked", false) -> NotificationPermission.NotAsked
            else -> NotificationPermission.Denied
        }
    }
    override fun permission(done: (NotificationPermission) -> Unit) = done(state())
    override fun requestPermission(done: (NotificationPermission) -> Unit) {
        val launcher = permission
        if (state() != NotificationPermission.NotAsked || launcher == null) { done(state()); return }
        preferences.edit().putBoolean("permissionAsked", true).apply()
        pending = done
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    override fun openSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
    override fun promptMemory(done: (NotificationPromptMemory) -> Unit) = done(
        NotificationPromptMemory(
            preferences.getInt("promptDeclined", 0),
            preferences.getLong("promptDeclinedAt", 0),
        ),
    )
    override fun savePromptMemory(memory: NotificationPromptMemory) {
        preferences.edit()
            .putInt("promptDeclined", memory.declined)
            .putLong("promptDeclinedAt", memory.lastDeclinedAtMillis)
            .apply()
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
    override fun dismissAll() = context.getSystemService(NotificationManager::class.java).cancelAll()
    override fun dismissAttendance(gameId: String) =
        context.getSystemService(NotificationManager::class.java).cancel(attendanceWindowId(gameId))
    override fun observe(changed: () -> Unit): NotificationSubscription {
        listeners += changed
        return NotificationSubscription { listeners -= changed }
    }
    fun tokenChanged() { listeners.toList().forEach { it() } }
}
