package br.com.saqz.composeapp.shell

import androidx.compose.material.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthStateListener
import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeFailureCode
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.port.TokenCallback
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class EmailVerificationBannerTest {

    @Test
    fun resendCallsTheProviderAndConfirms() = runComposeUiTest {
        val auth = FakeAuthPort()
        setContent { SaqzTheme { EmailVerificationBanner(onRefresh = {}, auth = auth) } }

        onNodeWithText(Unverified).assertIsDisplayed()
        onNodeWithTag(SaqzEmailBannerResendTag).performClick()
        waitForIdle()

        assertEquals(1, auth.verificationCalls)
        onNodeWithText(Resent).assertIsDisplayed()
    }

    /**
     * A trava entre envios: sem ela o "Reenviar" vira botão de spam, e quem paga é a caixa
     * de entrada da pessoa. O relógio do teste não avança sozinho, então o minuto não passa
     * e a ação continua fora da faixa.
     */
    @Test
    fun resendLocksAfterASuccessfulSend() = runComposeUiTest {
        val auth = FakeAuthPort()
        setContent { SaqzTheme { EmailVerificationBanner(onRefresh = {}, auth = auth) } }

        onNodeWithTag(SaqzEmailBannerResendTag).assertExists()
        onNodeWithTag(SaqzEmailBannerResendTag).performClick()
        waitForIdle()

        onNodeWithTag(SaqzEmailBannerResendTag).assertDoesNotExist()
        assertEquals(1, auth.verificationCalls)
        // A faixa continua lá: o que sumiu foi a ação, não o aviso.
        onNodeWithTag(SaqzEmailBannerTag).assertIsDisplayed()
    }

    // A trava acende antes de a chamada voltar: o envio em voo já basta para tirar a ação
    // da faixa, e é o que a mantém travada quando a tela é recriada no meio do envio. A
    // restauração em si é medida no `ShellEmailBannerRestorationTest`, no `:android-app` —
    // o `StateRestorationTester` do Compose é `TODO()` no Kotlin/Native, que é onde esta
    // suíte roda.
    @Test
    fun anInFlightResendAlreadyLocksTheAction() = runComposeUiTest {
        val auth = FakeAuthPort(result = null)
        setContent { SaqzTheme { EmailVerificationBanner(onRefresh = {}, auth = auth, now = { 0L }) } }

        onNodeWithTag(SaqzEmailBannerResendTag).performClick()
        waitForIdle()

        onNodeWithTag(SaqzEmailBannerResendTag).assertDoesNotExist()
        onNodeWithTag(SaqzEmailBannerTag).assertIsDisplayed()
        assertEquals(1, auth.verificationCalls)
    }

    // Falha não trava: quem não conseguiu reenviar precisa poder tentar de novo.
    @Test
    fun aFailedResendSaysSoAndStaysAvailable() = runComposeUiTest {
        val auth = FakeAuthPort(result = OperationResult.Failure(NativeFailureCode.NETWORK_UNAVAILABLE))
        setContent { SaqzTheme { EmailVerificationBanner(onRefresh = {}, auth = auth) } }

        onNodeWithTag(SaqzEmailBannerResendTag).performClick()
        waitForIdle()

        onNodeWithText(Failed).assertIsDisplayed()
        onNodeWithTag(SaqzEmailBannerResendTag).performClick()
        waitForIdle()
        assertEquals(2, auth.verificationCalls)
    }

    // `reloadUser` sai pelo [onRefresh]; quem o dispara é a resumida do ciclo de vida, e a
    // primeira delas acontece quando a faixa entra na composição.
    @Test
    fun composingTheBannerAsksForARefresh() = runComposeUiTest {
        var refreshes = 0
        setContent {
            SaqzTheme { EmailVerificationBanner(onRefresh = { refreshes++ }, auth = FakeAuthPort()) }
        }
        waitForIdle()

        assertEquals(1, refreshes)
    }

    // A faixa informa, não bloqueia: o conteúdo do shell continua alcançável com ela na tela.
    @Test
    fun theBannerDoesNotBlockTheShellContent() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzAppShell(
                    banner = { EmailVerificationBanner(onRefresh = {}, auth = FakeAuthPort()) },
                    homeTab = { Text(HomeTab) },
                    groupsTab = { Text(GroupsTab) },
                    profileTab = { Text(ProfileTab) },
                )
            }
        }

        onNodeWithTag(SaqzEmailBannerTag).assertIsDisplayed()
        // VUL-193: Início é a aba inicial; a faixa não a cobre.
        onNodeWithText(HomeTab).assertIsDisplayed()
        onNodeWithText("Perfil").performClick()
        waitForIdle()
        onNodeWithText(ProfileTab).assertIsDisplayed()
        onNodeWithTag(SaqzEmailBannerTag).assertIsDisplayed()
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
        const val GroupsTab = "conteudo-grupos"
        const val HomeTab = "conteudo-inicio"
        const val ProfileTab = "conteudo-perfil"
        const val Unverified = "Confirme seu e-mail para não perder o acesso à sua conta."
        const val Resent = "E-mail reenviado. Confira sua caixa de entrada."
        const val Failed = "Não foi possível reenviar agora. Tente de novo em instantes."
    }
}
