package br.com.saqz.androidapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import br.com.saqz.composeapp.SaqzApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidFirebaseBootstrap.initialize(this)
        setContent { SaqzApp() }
    }
}
