package br.com.saqz.androidapp

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

// As duas escalas que "Remover animações" (Acessibilidade) zera. As opções do
// desenvolvedor zeram uma de cada vez, então qualquer uma em 0 já é pedido de menos
// movimento — o Compose não lê nenhuma delas sozinho, quem anda é o frame clock.
private val AnimationScaleKeys = listOf(
    Settings.Global.ANIMATOR_DURATION_SCALE,
    Settings.Global.TRANSITION_ANIMATION_SCALE,
)

/**
 * Contraparte Android do `AccessibilityPreferencesObserver` do iOS: lê o sinal nativo de
 * "remover animações" e reentrega a cada mudança, enquanto a composição estiver viva —
 * o usuário pode virar o ajuste com o app aberto.
 *
 * Só o Reduce Motion. O Android não publica equivalente ao Reduce Transparency da Apple.
 */
@Composable
internal fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var reduceMotion by remember(resolver) { mutableStateOf(resolver.animationsDisabled()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduceMotion = resolver.animationsDisabled()
            }
        }
        AnimationScaleKeys.forEach {
            resolver.registerContentObserver(Settings.Global.getUriFor(it), false, observer)
        }
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduceMotion
}

private fun ContentResolver.animationsDisabled() = AnimationScaleKeys.any {
    Settings.Global.getFloat(this, it, 1f) == 0f
}
