package br.com.saqz.androidapp

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.domain.SaqzResult
import br.com.saqz.receivables.domain.*
import br.com.saqz.receivables.domain.port.*
import br.com.saqz.receivables.presentation.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FinancialOnboardingBackTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun systemBackKeepsPendingCreationAndResolutionRefreshesDirectoryBeforeReturn() {
        val marker = """{"actor":"owner","requestId":"request","kind":"CREATE"}"""
        val saved = SavedStateHandle(mapOf("onboarding.attempt" to marker))
        var returned = 0; var changed = 0
        val gateway = object : FinancialOnboardingGateway {
            var account: OwnedReceiptAccount? = null
            override suspend fun mine(ownerId: String) = SaqzResult.Success(account)
            override suspend fun documents() = SaqzResult.Success(emptyList<ReceiptDocument>())
            override suspend fun currentTerms(): Nothing = error("No new terms during pending creation")
            override suspend fun create(ownerId: String, command: ReceiptRegistrationCommand): Nothing = error("No recreation")
            override suspend fun recover(ownerId: String, requestId: String): Nothing = error("Read before recovery")
            override suspend fun upload(document: ReceiptDocument, requestId: String, file: ReceiptDocumentFile): Nothing =
                error("No upload")
        }
        val picker = object : ReceiptDocumentPicker {
            override fun choose(done: ReceiptFileCallback): Nothing = error("No document requested")
        }
        lateinit var vm: FinancialOnboardingViewModel
        compose.runOnUiThread {
            vm = FinancialOnboardingViewModel(gateway, object : ReceivablesAvailabilityGateway {
                override suspend fun get(): Nothing = error("Maintenance does not require new business availability")
            }, ReceivablesSessionContext { "session" }, ReceivablesRecoveryIdentity { "owner" }, picker, saved)
        }
        compose.setContent { SaqzTheme { FinancialOnboardingRoot({ returned++ }, { changed++ }, onOpenWallet = {}, viewModel = vm) } }
        compose.waitForIdle()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle {
            assertEquals(0, returned); assertEquals(marker, saved.get<String>("onboarding.attempt"))
            gateway.account = OwnedReceiptAccount("account", "owner", AccountRegistration.UNDER_REVIEW, false)
            vm.onIntent(FinancialOnboardingIntent.Refresh)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertNull(saved.get<String>("onboarding.attempt")); assertEquals("account", vm.state.value.account?.id)
            assertEquals(1, changed)
        }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertEquals(1, returned) }
    }
}
