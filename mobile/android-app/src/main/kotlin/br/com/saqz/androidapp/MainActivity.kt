package br.com.saqz.androidapp

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.saqz.composeapp.SaqzApp
import br.com.saqz.composeapp.di.loadSaqzPlatformDependencies
import java.lang.ref.WeakReference
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {
    private val model: MainActivityModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model.attach(this)
        // model init loads the platform dependencies into Koin; SaqzApp resolves from there.
        //
        // ponytail: `reduceTransparency` fica no padrão `false`. O Android não publica
        // equivalente ao Reduce Transparency da Apple — `HIGH_TEXT_CONTRAST_ENABLED` é
        // sobre texto, não sobre translucidez, e inventar sinal é pior que não ter.
        // Teto: no dia em que existir um ajuste de translucidez, ele entra aqui do lado.
        setContent { SaqzApp(reduceMotion = rememberReduceMotion()) }
    }

    override fun onStart() {
        super.onStart()
        model.onStart(intent?.dataString, intent?.getStringExtra(EXTRA_NOTIFICATION_GROUP_ID))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        model.onWarmIntent(intent.dataString, intent.getStringExtra(EXTRA_NOTIFICATION_GROUP_ID))
    }
}

internal class MainActivityModel(
    context: android.content.Context,
    factory: AndroidAppCompositionFactory,
) : ViewModel() {
    private val activity = CurrentActivity()
    private val composition = factory.create(context, viewModelScope, activity::require)
    init {
        loadSaqzPlatformDependencies(composition.dependencies, context.applicationContext)
    }
    private var coldStarted = false

    fun attach(value: Activity) {
        activity.attach(value)
        (value as? ComponentActivity)?.let { (composition.dependencies.notifications as? AndroidNotificationPort)?.attach(it) }
        (value as? ComponentActivity)?.let { composition.photos?.attach(it); composition.documents?.attach(it) }
    }

    fun onStart(url: String?, notificationGroupId: String?) {
        if (coldStarted) return
        coldStarted = true
        composition.links.onColdStart(url)
        notificationGroupId?.let(composition.links::onNotificationOpen)
    }

    fun onWarmIntent(url: String?, notificationGroupId: String?) {
        composition.links.onWarmIntent(url)
        notificationGroupId?.let(composition.links::onNotificationOpen)
    }

}

private class CurrentActivity {
    private var reference = WeakReference<Activity>(null)

    fun attach(activity: Activity) {
        reference = WeakReference(activity)
    }

    fun require(): Activity = checkNotNull(reference.get()) { "activity is not attached" }
}
