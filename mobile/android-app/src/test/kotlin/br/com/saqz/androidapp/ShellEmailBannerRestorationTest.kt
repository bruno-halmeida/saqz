package br.com.saqz.androidapp

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthStateListener
import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.port.TokenCallback
import br.com.saqz.composeapp.shell.EmailVerificationBanner
import br.com.saqz.designsystem.theme.SaqzTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A trava do reenvio atravessando a recriação da tela (VUL-91).
 *
 * Mora aqui e não no `commonTest` do `:compose-app` porque o `StateRestorationTester` do
 * Compose é `TODO()` no Kotlin/Native, que é onde aquela suíte roda (AGENTS.md §10). O
 * resto do comportamento da faixa é medido lá, onde o gate dela está.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = Application::class,
)
class ShellEmailBannerRestorationTest {
    @get:Rule
    val compose = createComposeRule()

    /**
     * O caso que o review pegou: girar o aparelho com o `sendVerification` no ar. O callback
     * original volta para uma composição morta e não tem como acender trava nenhuma — se ela
     * dependesse do retorno, a faixa recriada liberaria outro reenvio na hora.
     */
    @Test
    fun theLockSurvivesRecreationWithTheSendStillInFlight() {
        val auth = FakeAuthPort(result = null)
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            SaqzTheme { EmailVerificationBanner(onRefresh = {}, auth = auth, now = { CLOCK_START }) }
        }

        compose.onNodeWithTag(RESEND).performClick()
        compose.waitForIdle()
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        compose.onNodeWithTag(RESEND).assertDoesNotExist()
        // O que sumiu foi a ação, não o aviso.
        compose.onNodeWithTag(BANNER).assertIsDisplayed()
        assertEquals(1, auth.verificationCalls)
    }

    // A trava é um instante, não um "está esperando": o minuto não recomeça na recriação, e
    // o que venceu enquanto a tela não existia devolve a ação em vez de esperar de novo.
    @Test
    fun aLockThatExpiredWhileAwayComesBackUnlocked() {
        val auth = FakeAuthPort()
        var clock = CLOCK_START
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            SaqzTheme { EmailVerificationBanner(onRefresh = {}, auth = auth, now = { clock }) }
        }

        compose.onNodeWithTag(RESEND).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(RESEND).assertDoesNotExist()

        clock = CLOCK_START + COOLDOWN_MILLIS + 1
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        compose.onNodeWithTag(RESEND).assertExists()
    }

    /** [result] nulo é o envio que fica no ar: o callback nunca volta. */
    private class FakeAuthPort(
        private val result: OperationResult? = OperationResult.Success,
    ) : NativeAuthPort {
        var verificationCalls = 0

        override fun sendVerification(done: ResultCallback) {
            verificationCalls += 1
            result?.let(done::complete)
        }

        override fun observe(listener: AuthStateListener): Cancelable =
            object : Cancelable { override fun cancel() = Unit }

        override fun createAccount(name: String, email: String, password: String, done: AuthCallback) = Unit
        override fun signInWithPassword(email: String, password: String, done: AuthCallback) = Unit
        override fun signInWithGoogle(done: AuthCallback) = Unit
        override fun reloadUser(done: AuthCallback) = Unit
        override fun updateDisplayName(name: String, done: AuthCallback) = Unit
        override fun idToken(forceRefresh: Boolean, done: TokenCallback) = Unit
        override fun signOut(done: ResultCallback) = Unit
    }

    private companion object {
        // As etiquetas e a janela são internas à faixa; aqui elas são o contrato observado.
        const val BANNER = "shell-email-banner"
        const val RESEND = "shell-email-banner-resend"
        const val COOLDOWN_MILLIS = 60_000L

        // Um instante qualquer, longe de zero: com o relógio parado em 0 o "venceu enquanto
        // a tela não existia" não se distinguiria de "nunca travou".
        const val CLOCK_START = 1_700_000_000_000L
    }
}
