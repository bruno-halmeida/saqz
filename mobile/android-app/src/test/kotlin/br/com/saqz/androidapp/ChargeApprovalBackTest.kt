package br.com.saqz.androidapp

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.domain.SaqzResult
import br.com.saqz.receivables.domain.*
import br.com.saqz.receivables.presentation.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ChargeApprovalBackTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun systemBackKeepsUnresolvedAttemptAndUsesReturnCallbackAfterResolution() {
        val marker = """{"actor":"owner","groupId":"group","chargeId":"charge","accountId":"account","requestId":"request","fingerprint":"${"a".repeat(64)}"}"""
        val saved = SavedStateHandle(mapOf("approval.attempt" to marker))
        var returned = 0
        var detail: MemberPaymentDetail? = null
        val gateway = object : ChargeApprovalGateway {
            override suspend fun lookup(target: ChargeApprovalTarget) = SaqzResult.Success(detail)
            override suspend fun preview(target: ChargeApprovalTarget, requestId: String): Nothing = error("No new preview")
            override suspend fun approve(target: ChargeApprovalTarget, command: ChargeApprovalCommand): Nothing = error("No new approval")
            override suspend fun cancel(target: ChargeApprovalTarget, orderId: String, requestId: String): Nothing = error("No new cancellation")
        }
        lateinit var vm: ChargeApprovalViewModel
        compose.runOnUiThread {
            vm = ChargeApprovalViewModel("group", "charge", gateway, UnusedConditions,
                ReceivablesSessionContext { "session" }, ReceivablesRecoveryIdentity { "owner" }, saved)
        }
        compose.setContent { SaqzTheme { ChargeApprovalRoot("group", "charge", { returned++ }, {}, vm) } }
        compose.waitForIdle()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle {
            assertEquals(0, returned); assertEquals(marker, saved.get<String>("approval.attempt"))
            detail = MemberPaymentDetail(MemberPaymentOrder("order", "account", "charge", "group", "payer", "2026-09-20",
                "ISSUED", emptyList(), "a".repeat(64)), emptyList())
            vm.onIntent(ChargeApprovalIntent.Refresh)
        }
        compose.waitForIdle()
        compose.runOnIdle { assertNull(saved.get<String>("approval.attempt")); assertEquals("order", vm.state.value.detail?.order?.id) }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertEquals(1, returned) }
    }
    private object UnusedConditions : GroupReceivablesGateway {
        override suspend fun accounts(): Nothing = error("Restored account")
        override suspend fun status(groupId: String, accountId: String): Nothing = error("Existing command")
        override suspend fun preview(groupId: String, command: ReceiptCommand): Nothing = error("Existing command")
        override suspend fun terms(version: String): Nothing = error("Existing command")
        override suspend fun activate(groupId: String, command: ReceiptCommand): Nothing = error("Existing command")
        override suspend fun deactivate(groupId: String, command: ReceiptCommand): Nothing = error("Existing command")
    }
}
