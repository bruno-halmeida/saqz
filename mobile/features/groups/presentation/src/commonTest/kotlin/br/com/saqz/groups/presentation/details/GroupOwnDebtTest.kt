package br.com.saqz.groups.presentation.details

import br.com.saqz.domain.GroupId
import br.com.saqz.groups.domain.finance.Charge
import br.com.saqz.groups.domain.finance.ChargeKind
import br.com.saqz.groups.domain.finance.ChargeStatus
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GroupOwnDebtTest {
    private val today = LocalDate(2026, 8, 1)

    @Test
    fun `no pending charge means no debt`() = runTest {
        assertNull(groupOwnDebt(emptyList(), emptyList(), today, "Lucas Prado", hasPixKey = true))
    }

    @Test
    fun `debt takes competence and due label from the oldest and sums the rest`() = runTest {
        val debt = groupOwnDebt(
            pending = listOf(charge("mensal", 7_000L, "2026-07-10"), charge("avulso", 2_500L, "2026-08-28")),
            pendingUi = listOf(ui("mensal", "Mensalidade · Julho", "Venceu em 10/07"), ui("avulso", "Jogo avulso", "Vence em 28/08")),
            today = today,
            pixLabel = " Lucas Prado ",
            hasPixKey = true,
        )

        assertEquals(
            GroupOwnDebtUi(
                eyebrow = "Mensalidade · Julho",
                totalLabel = "R$ 95,00",
                dueLabel = "Venceu em 10/07",
                overdue = true,
                countLabel = "2 cobranças em aberto",
                receiverLabel = "Pix de Lucas Prado",
            ),
            debt,
        )
    }

    @Test
    fun `single pending charge not yet due has no count and is not overdue`() = runTest {
        val debt = groupOwnDebt(
            pending = listOf(charge("avulso", 2_500L, "2026-08-28")),
            pendingUi = listOf(ui("avulso", "Jogo avulso", "Vence em 28/08")),
            today = today,
            pixLabel = null,
            hasPixKey = true,
        )

        assertEquals(false, debt?.overdue)
        assertNull(debt?.countLabel)
        assertNull(debt?.receiverLabel)
    }

    @Test
    fun `receiver is hidden when the group has no pix key`() = runTest {
        val debt = groupOwnDebt(
            pending = listOf(charge("mensal", 7_000L, "2026-07-10")),
            pendingUi = listOf(ui("mensal", "Mensalidade · Julho", "Venceu em 10/07")),
            today = today,
            pixLabel = "Lucas Prado",
            hasPixKey = false,
        )

        assertNull(debt?.receiverLabel)
    }

    private fun charge(id: String, cents: Long, dueDate: String) = Charge(
        id = id,
        groupId = GroupId("group-1"),
        memberId = "me",
        kind = ChargeKind.Monthly,
        amountCents = cents,
        dueDate = dueDate,
        status = ChargeStatus.Pending,
        version = 1,
        audit = emptyList(),
    )

    private fun ui(id: String, title: String, due: String) =
        OwnChargeUi(id = id, title = title, dueLabel = due, amountLabel = "", status = OwnChargeStatusUi.Pending)
}
