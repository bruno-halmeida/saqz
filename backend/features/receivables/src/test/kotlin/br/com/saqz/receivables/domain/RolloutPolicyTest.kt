package br.com.saqz.receivables.domain

import br.com.saqz.receivables.application.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class RolloutPolicyTest {
    @Test fun `all mode and override combinations obey absolute off and deny`() {
        val expected = mapOf(RolloutMode.OFF to listOf(false,false,false),
            RolloutMode.SELECTED_USERS to listOf(false,true,false), RolloutMode.ALL_USERS to listOf(true,true,false))
        for (mode in RolloutMode.entries) for ((index, decision) in RolloutDecision.entries.withIndex())
            assertEquals(expected.getValue(mode)[index], RolloutPolicy.enabled(mode, decision), "$mode $decision")
    }
    @Test fun `mobile is always bounded by backend and maintenance survives every combination`() {
        for (backend in RolloutMode.entries) for (mobile in RolloutMode.entries)
            for (backendDecision in RolloutDecision.entries) for (mobileDecision in RolloutDecision.entries) {
                val result = RolloutPolicy.availability(listOf(SystemRollout(ReceivableSystem.BACKEND,backend),SystemRollout(ReceivableSystem.MOBILE,mobile)),
                    listOf(UserRolloutOverride(ReceivableSystem.BACKEND,backendDecision),UserRolloutOverride(ReceivableSystem.MOBILE,mobileDecision)))
                assertEquals(RolloutPolicy.enabled(backend,backendDecision),result.backendEnabled)
                assertEquals(result.backendEnabled && RolloutPolicy.enabled(mobile,mobileDecision),result.mobileEnabled)
                assertTrue(result.maintenanceAvailable)
            }
    }
}
