package br.com.saqz.androidapp

import android.content.ContentResolver
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import br.com.saqz.designsystem.theme.SaqzAccessibilityPreferences
import br.com.saqz.designsystem.theme.SaqzMotionPolicy
import br.com.saqz.designsystem.theme.SaqzTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A ponta Android do Reduce Motion: o ajuste do sistema chegando à composição.
 *
 * O `SaqzAppEnvironmentTest` (compose-app) já cobre `booleano → SaqzTheme`. O que falta é
 * daqui para trás — `Settings.Global` → booleano —, e o teste fecha o caminho inteiro
 * afirmando sobre `SaqzTheme.motion`, que é o que os componentes leem.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class AndroidReduceMotionTest {

    @get:Rule
    val compose = createComposeRule()

    private val resolver: ContentResolver
        get() = ApplicationProvider.getApplicationContext<android.app.Application>().contentResolver

    @Test
    fun semAjusteAsDuracoesSaoAsNormais() {
        assertEquals(SaqzMotionPolicy.Normal, motionUnderSetting())
    }

    @Test
    fun animatorDurationScaleZeradaReduzOMovimento() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)

        assertEquals(SaqzMotionPolicy.Reduced, motionUnderSetting())
    }

    @Test
    fun transitionAnimationScaleZeradaReduzOMovimento() {
        // As opções do desenvolvedor zeram uma escala de cada vez; "Remover animações"
        // zera as duas. Qualquer uma sozinha já conta.
        Settings.Global.putFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 0f)

        assertEquals(SaqzMotionPolicy.Reduced, motionUnderSetting())
    }

    @Test
    fun ajusteLigadoComOAppAbertoDerrubaAsDuracoesNaMesmaSessao() {
        lateinit var motion: SaqzMotionPolicy
        compose.setContent { ThemeUnderSystemSetting { motion = SaqzTheme.motion } }
        assertEquals(320, motion.sheetDurationMillis)

        // Sem recompor a árvore: o usuário virou o ajuste com o app aberto e o sistema
        // avisa pelo ContentObserver. (`putFloat` sozinho não notifica no Robolectric.)
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        resolver.notifyChange(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), null)
        compose.waitForIdle()

        assertEquals(0, motion.sheetDurationMillis)
        assertEquals(0, motion.thumbDurationMillis)
        assertEquals(0, motion.switchDurationMillis)
    }

    @Test
    fun ajusteLigadoNaFrestaEntreALeituraInicialEOObserverEhRecuperado() {
        // Achado do Codex no PR #51. O `DisposableEffect` só é aplicado quando a composição
        // termina, então o `remember` abaixo roda **depois** da leitura inicial e **antes**
        // do registro do observer: é a fresta. A notificação do sistema sai sem ninguém do
        // outro lado e se perde — só a releitura pós-registro recupera o ajuste.
        lateinit var motion: SaqzMotionPolicy
        compose.setContent {
            val reduceMotion = rememberReduceMotion()
            remember {
                val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
                Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
                resolver.notifyChange(uri, null)
            }
            SaqzTheme(SaqzAccessibilityPreferences(reduceMotion = reduceMotion)) {
                motion = SaqzTheme.motion
            }
        }
        compose.waitForIdle()

        assertEquals(SaqzMotionPolicy.Reduced, motion)
    }

    private fun motionUnderSetting(): SaqzMotionPolicy {
        lateinit var motion: SaqzMotionPolicy
        compose.setContent { ThemeUnderSystemSetting { motion = SaqzTheme.motion } }
        compose.waitForIdle()
        return motion
    }

    // A mesma ligação que o MainActivity faz: o sinal do sistema entra nas preferências
    // do tema, e é o tema que os componentes consultam.
    @Composable
    private fun ThemeUnderSystemSetting(content: @Composable () -> Unit) = SaqzTheme(
        preferences = SaqzAccessibilityPreferences(reduceMotion = rememberReduceMotion()),
        content = content,
    )
}
