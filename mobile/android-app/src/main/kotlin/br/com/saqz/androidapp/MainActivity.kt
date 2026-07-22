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
        setContent { SaqzApp(model.dependencies) }
    }

    override fun onStart() {
        super.onStart()
        model.onStart(intent?.dataString)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        model.onWarmIntent(intent.dataString)
    }
}

internal class MainActivityModel(
    context: android.content.Context,
    factory: AndroidAppCompositionFactory,
) : ViewModel() {
    private val activity = CurrentActivity()
    private val composition = factory.create(context, viewModelScope, activity::require)
    init {
        loadSaqzPlatformDependencies(composition.dependencies)
    }
    val dependencies = composition.dependencies
    private var coldStarted = false

    fun attach(value: Activity) {
        activity.attach(value)
        (value as? ComponentActivity)?.let { composition.photos?.attach(it) }
    }

    fun onStart(url: String?) {
        if (coldStarted) return
        coldStarted = true
        composition.links.onColdStart(url)
    }

    fun onWarmIntent(url: String?) {
        composition.links.onWarmIntent(url)
    }

}

private class CurrentActivity {
    private var reference = WeakReference<Activity>(null)

    fun attach(activity: Activity) {
        reference = WeakReference(activity)
    }

    fun require(): Activity = checkNotNull(reference.get()) { "activity is not attached" }
}
