package br.com.saqz.groups.presentation.details

import br.com.saqz.groups.domain.communication.GroupWhatsAppStatus
import br.com.saqz.groups.domain.group.GroupFinanceDefaults
import br.com.saqz.groups.port.GroupOnboardingMemory
import br.com.saqz.groups.presentation.sampleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupChecklistTest {
    private val now = 1_000_000L

    @Test
    fun `a fresh group has only the recurrence done and whatsapp as the current step`() {
        val checklist = groupChecklist(sampleGroup(), rosterHasMensalista = false, whatsApp = GroupWhatsAppStatus.NONE, memory = GroupOnboardingMemory(), nowEpochMillis = now, whatsAppBinding = true)

        assertEquals(GroupChecklistItem.WhatsApp, checklist?.current)
        assertEquals(
            mapOf(
                GroupChecklistItem.WhatsApp to false,
                GroupChecklistItem.Mensalistas to false,
                GroupChecklistItem.Pix to false,
                GroupChecklistItem.Rules to false,
                GroupChecklistItem.Recurrence to true,
            ),
            checklist?.rows?.associate { it.item to it.done },
        )
    }

    @Test
    fun `each item is derived from the group the roster the binding and the local memory`() {
        val group = sampleGroup().let {
            it.copy(
                profile = it.profile?.copy(pixKey = "ceret@volei.com.br"),
                financeDefaults = GroupFinanceDefaults(defaultGameFeeCents = null, monthlyFeeCents = 8500, monthlyDueDay = 10),
            )
        }
        val checklist = groupChecklist(group, rosterHasMensalista = false, whatsApp = GroupWhatsAppStatus.DISABLED, memory = GroupOnboardingMemory(rulesOpened = true), nowEpochMillis = now, whatsAppBinding = true)

        assertEquals(GroupChecklistItem.WhatsApp, checklist?.current)
        assertTrue(checklist!!.rows.filter { it.item != GroupChecklistItem.WhatsApp }.all { it.done })
    }

    @Test
    fun `a mensalista in the roster also completes the mensalistas item`() {
        val checklist = groupChecklist(sampleGroup(), rosterHasMensalista = true, whatsApp = null, memory = GroupOnboardingMemory(), nowEpochMillis = now, whatsAppBinding = true)

        assertTrue(checklist!!.rows.single { it.item == GroupChecklistItem.Mensalistas }.done)
    }

    @Test
    fun `all five done means no checklist at all`() {
        val group = sampleGroup().let {
            it.copy(
                profile = it.profile?.copy(pixKey = "chave"),
                financeDefaults = GroupFinanceDefaults(null, 8500, 10),
            )
        }

        assertNull(groupChecklist(group, rosterHasMensalista = true, whatsApp = GroupWhatsAppStatus.ACTIVE, memory = GroupOnboardingMemory(rulesOpened = true), nowEpochMillis = now, whatsAppBinding = true))
    }

    @Test
    fun `without the whatsapp binding the item is gone and does not block completion`() {
        val fresh = groupChecklist(
            sampleGroup(), rosterHasMensalista = false, whatsApp = null, memory = GroupOnboardingMemory(),
            nowEpochMillis = now, whatsAppBinding = false,
        )
        assertEquals(GroupChecklistItem.Mensalistas, fresh?.current)
        assertTrue(fresh!!.rows.none { it.item == GroupChecklistItem.WhatsApp })

        val group = sampleGroup().let {
            it.copy(profile = it.profile?.copy(pixKey = "chave"), financeDefaults = GroupFinanceDefaults(null, 8500, 10))
        }
        assertNull(
            groupChecklist(
                group, rosterHasMensalista = true, whatsApp = GroupWhatsAppStatus.NONE,
                memory = GroupOnboardingMemory(rulesOpened = true), nowEpochMillis = now, whatsAppBinding = false,
            ),
        )
    }

    @Test
    fun `snoozed memory hides the checklist until the deadline passes`() {
        val memory = GroupOnboardingMemory(snoozedUntilEpochMillis = now + 1)

        assertNull(groupChecklist(sampleGroup(), false, null, memory, nowEpochMillis = now, whatsAppBinding = true))
        assertEquals(GroupChecklistItem.WhatsApp, groupChecklist(sampleGroup(), false, null, memory, nowEpochMillis = now + 1, whatsAppBinding = true)?.current)
    }
}
