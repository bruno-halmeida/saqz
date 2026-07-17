package br.com.saqz.androidapp

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.com.saqz.composeapp.SaqzApp
import br.com.saqz.composeapp.SaqzAppRuntime
import java.lang.ref.WeakReference

class MainActivity : ComponentActivity() {
    private lateinit var model: MainActivityModel

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model = ViewModelProvider(
            this,
            MainActivityModel.factory(applicationContext, MainActivityComposition.factory()),
        )[MainActivityModel::class.java]
        model.attach(this)
        setContent { SaqzApp(model.runtime) }
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

internal class MainActivityModel private constructor(
    context: android.content.Context,
    factory: AndroidAppCompositionFactory,
) : ViewModel() {
    private val activity = CurrentActivity()
    private val composition = factory.create(context, viewModelScope, activity::require)
    val runtime = SaqzAppRuntime(composition.dependencies)
    private var coldStarted = false

    fun attach(value: Activity) {
        activity.attach(value)
    }

    fun onStart(url: String?) {
        if (coldStarted) return
        coldStarted = true
        composition.links.onColdStart(url)
    }

    fun onWarmIntent(url: String?) {
        composition.links.onWarmIntent(url)
    }

    override fun onCleared() {
        runtime.close()
    }

    companion object {
        fun factory(
            context: android.content.Context,
            compositionFactory: AndroidAppCompositionFactory,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == MainActivityModel::class.java)
                return MainActivityModel(context.applicationContext, compositionFactory) as T
            }
        }
    }
}

private class CurrentActivity {
    private var reference = WeakReference<Activity>(null)

    fun attach(activity: Activity) {
        reference = WeakReference(activity)
    }

    fun require(): Activity = checkNotNull(reference.get()) { "activity is not attached" }
}
