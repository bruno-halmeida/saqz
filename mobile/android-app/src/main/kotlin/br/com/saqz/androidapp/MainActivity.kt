package br.com.saqz.androidapp

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import br.com.saqz.composeapp.SaqzApp
import br.com.saqz.androidapp.access.AndroidLinkAdapter
import br.com.saqz.androidapp.access.BranchSdkSessionClient

class MainActivity : ComponentActivity() {
    internal lateinit var links: AndroidLinkAdapter
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AndroidFirebaseBootstrap.initialize(this)
        links = AndroidLinkAdapter(BranchSdkSessionClient(this))
        setContent { SaqzApp() }
    }

    override fun onStart() {
        super.onStart()
        links.onColdStart(intent?.dataString)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        links.onWarmIntent(intent.dataString)
    }
}
