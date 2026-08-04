package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.finance.overview.FinanceOverviewGroup
import br.com.saqz.groups.application.finance.overview.FinanceOverviewQuery
import br.com.saqz.groups.application.finance.overview.FinanceOverviewReadModel
import br.com.saqz.groups.application.finance.overview.FinanceOverviewRepository
import br.com.saqz.groups.application.finance.overview.FinanceOverviewTotals
import br.com.saqz.groups.application.finance.overview.FinanceOverviewTransaction
import br.com.saqz.groups.application.finance.overview.FinanceOverviewTransactionKind
import br.com.saqz.groups.application.finance.overview.FinanceOverviewPeriod
import br.com.saqz.groups.domain.finance.expense.ExpenseDirection
import br.com.saqz.sharedkernel.RequestIdentity
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import org.junit.jupiter.api.Test

class MyFinanceOverviewControllerTest {
    private val actor = UUID.randomUUID()
    private val group = UUID.randomUUID()
    private val recentTransactionId = UUID.fromString("10000000-0000-0000-0000-000000000001")
    private val identity = RequestIdentity("subject", emailVerified = true)
    private val clock = Clock.fixed(Instant.parse("2026-08-01T12:00:00Z"), ZoneId.of("UTC"))
    private val zone = ZoneId.of("America/Sao_Paulo")

    @Test
    fun `returns structured overview with period totals groups and recent transactions`() {
        var requestedActor: UUID? = null
        val transaction = FinanceOverviewTransaction(
            id = recentTransactionId,
            groupId = group,
            groupName = "Grupo Praia",
            kind = FinanceOverviewTransactionKind.LAUNCH,
            direction = ExpenseDirection.OUT,
            memberName = null,
            description = "Aluguel da quadra",
            amountCents = 2500,
            occurredAt = Instant.parse("2026-08-02T10:00:00Z"),
        )
        val controller = controller(onFind = { requestedActor = it })

        val response = controller.overview(identity, "2026-08", null)

        assertEquals(actor, requestedActor)
        assertEquals(FinanceOverviewPeriodResponse("2026-08", null), response.period)
        assertEquals(FinanceOverviewTotalsResponse(12000, 18000, 6000, 4000), response.totals)
        assertEquals(
            listOf(FinanceOverviewGroupResponse(group, "Grupo Praia", 12000, 2, true)),
            response.groups,
        )
        assertEquals(
            listOf(
                FinanceOverviewTransactionResponse(
                    id = transaction.id,
                    groupId = group,
                    groupName = "Grupo Praia",
                    kind = "LAUNCH",
                    direction = "OUT",
                    memberName = null,
                    description = "Aluguel da quadra",
                    amountCents = 2500,
                    occurredAt = transaction.occurredAt,
                ),
            ),
            response.recentTransactions,
        )
    }

    @Test
    fun `returns empty zeroed overview when actor administers no groups`() {
        val controller = controller(read = { _, period ->
            FinanceOverviewReadModel(
                period = period,
                totals = FinanceOverviewTotals(0, 0, 0, 0),
                groups = emptyList(),
                recentTransactions = emptyList(),
            )
        })

        val response = controller.overview(identity, null, null)

        assertEquals(FinanceOverviewTotalsResponse(0, 0, 0, 0), response.totals)
        assertEquals(emptyList(), response.groups)
        assertEquals(emptyList(), response.recentTransactions)
    }

    @Test
    fun `returns calendar year in response when year filter is selected`() {
        val controller = controller()

        val response = controller.overview(identity, null, "2025")

        assertEquals(FinanceOverviewPeriodResponse(null, 2025), response.period)
    }

    @Test
    fun `exposes validation errors for mutually exclusive filters`() {
        val controller = controller()

        val failure = assertFailsWith<InvalidGroupRequestException> {
            controller.overview(identity, "2026-08", "2026")
        }

        assertEquals(
            mapOf(
                "month" to listOf("não pode ser informado junto com year"),
                "year" to listOf("não pode ser informado junto com month"),
            ),
            failure.fieldErrors,
        )
    }

    private fun controller(
        onFind: (UUID) -> Unit = {},
        read: (UUID, FinanceOverviewPeriod) -> FinanceOverviewReadModel = { _, period ->
            FinanceOverviewReadModel(
                period = period,
                totals = FinanceOverviewTotals(12000, 18000, 6000, 4000),
                groups = listOf(FinanceOverviewGroup(group, "Grupo Praia", 12000, 2, true)),
                recentTransactions = listOf(
                    FinanceOverviewTransaction(
                        recentTransactionId,
                        group,
                        "Grupo Praia",
                        FinanceOverviewTransactionKind.LAUNCH,
                        ExpenseDirection.OUT,
                        null,
                        "Aluguel da quadra",
                        2500,
                        Instant.parse("2026-08-02T10:00:00Z"),
                    ),
                ),
            )
        },
    ): MyFinanceOverviewController {
        val repository = object : FinanceOverviewRepository {
            override fun find(actorId: UUID, period: FinanceOverviewPeriod): FinanceOverviewReadModel {
                onFind(actorId)
                return read(actorId, period)
            }
        }
        return MyFinanceOverviewController(
            actorResolver = VerifiedGroupActorResolver { actor },
            query = FinanceOverviewQuery(repository, clock, zone),
        )
    }
}
