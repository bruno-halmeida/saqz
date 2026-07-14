package br.com.saqz.composeapp

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
fun SaqzApp() {
    MaterialTheme {
        Text(
            text = "Saqz",
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Saqz" },
        )
    }
}
