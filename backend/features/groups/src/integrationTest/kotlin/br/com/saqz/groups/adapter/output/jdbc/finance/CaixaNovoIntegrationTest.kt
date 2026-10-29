package br.com.saqz.groups.adapter.output.jdbc.finance

import br.com.saqz.groups.adapter.output.jdbc.attendance.FilaHarness
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.finance.expense.ExpenseResult
import br.com.saqz.groups.application.finance.expense.ExpenseService
import br.com.saqz.groups.application.finance.openingbalance.OpeningBalance
import br.com.saqz.groups.application.finance.openingbalance.OpeningBalanceResult
import br.com.saqz.groups.application.finance.openingbalance.OpeningBalanceService
import br.com.saqz.groups.application.finance.overview.FinanceOverviewPeriod
import br.com.saqz.groups.application.finance.statement.FinanceStatementQuery
import br.com.saqz.groups.domain.finance.expense.ExpenseCategory
import br.com.saqz.groups.domain.finance.expense.ExpenseDirection
import br.com.saqz.groups.domain.finance.expense.ExpenseDraft
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** DEB-148 (FIN-006): saldo inicial com versão e histórico, somado no extrato e no caixa geral; Patrocínio e Venda só na entrada. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CaixaNovoIntegrationTest {
    private lateinit var h: FilaHarness
    private val tx get() = JdbcTransactionRunner(h.dataSource)
    private val saldo get() = OpeningBalanceService(tx, JdbcOpeningBalanceRepository(h.dataSource), Instant::now)
    private val lancamentos get() = ExpenseService(tx, JdbcExpenseRepository(h.dataSource), UUID::randomUUID, Instant::now)
    private val hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"))

    @BeforeEach fun reset() { h = FilaHarness(this); h.cenario(capacity = 8) }

    private fun extrato() = JdbcFinanceStatementRepository(h.dataSource)
        .page(FinanceStatementQuery(h.group, YearMonth.from(hoje), null, 50, 0)).summary
    private fun caixaGeral() = JdbcFinanceOverviewRepository(h.dataSource, ZoneId.of("America/Sao_Paulo"))
        .find(h.dono, FinanceOverviewPeriod.Month(YearMonth.from(hoje))).groups.single { it.id == h.group }.balanceCents

    private fun lancar(category: ExpenseCategory, direction: ExpenseDirection, cents: Long = 1000) =
        lancamentos.create(h.dono, h.group, ExpenseDraft("Lançamento", cents, hoje, category, if (category == ExpenseCategory.OTHER) "Outro" else null, null, direction))

    @Test fun `saldo inicial nasce nulo, aceita zero e positivo, recusa negativo e versao velha, e guarda o historico`() {
        assertEquals(OpeningBalance(null, 1), assertIs<OpeningBalanceResult.Success>(saldo.read(h.dono, h.group)).value)
        assertNull(extrato().openingBalanceCents)

        assertEquals(OpeningBalance(0, 2), assertIs<OpeningBalanceResult.Success>(saldo.update(h.dono, h.group, 1, 0)).value)
        assertEquals(OpeningBalanceResult.Conflict, saldo.update(h.dono, h.group, 1, 25000))
        assertEquals(OpeningBalanceResult.InvalidAmount, saldo.update(h.dono, h.group, 2, -1))
        assertEquals(OpeningBalanceResult.InvalidAmount, saldo.update(h.dono, h.group, 2, 100_000_000))
        assertEquals(OpeningBalance(25000, 3), assertIs<OpeningBalanceResult.Success>(saldo.update(h.dono, h.group, 2, 25000)).value)
        assertEquals(OpeningBalance(30000, 4), assertIs<OpeningBalanceResult.Success>(saldo.update(h.dono, h.group, 3, 30000)).value)
        assertEquals(
            "null>0,0>25000,25000>30000",
            h.string("SELECT string_agg(coalesce(old_amount_cents::text,'null')||'>'||new_amount_cents, ',' ORDER BY occurred_at, new_amount_cents) FROM group_opening_balance_events WHERE group_id='${h.group}'"),
        )
    }

    @Test fun `o saldo inicial soma no extrato e no caixa geral`() {
        assertIs<ExpenseResult.Saved>(lancar(ExpenseCategory.VENUE, ExpenseDirection.OUT, 10000))
        assertEquals(-10000, extrato().accumulatedBalanceCents); assertEquals(-10000, caixaGeral())
        saldo.update(h.dono, h.group, 1, 25000)
        val resumo = extrato()
        assertEquals(25000, resumo.openingBalanceCents); assertEquals(15000, resumo.accumulatedBalanceCents)
        assertEquals(15000, caixaGeral())
        assertEquals(-10000, resumo.periodBalanceCents, "o mês não leva o saldo inicial")
    }

    @Test fun `atleta recebe 403 e quem e de fora 404`() {
        val atleta = h.atleta()
        assertEquals(OpeningBalanceResult.Forbidden, saldo.read(atleta, h.group))
        assertEquals(OpeningBalanceResult.Forbidden, saldo.update(atleta, h.group, 1, 100))
        assertEquals(OpeningBalanceResult.Hidden, saldo.read(UUID.randomUUID(), h.group))
    }

    @Test fun `Patrocinio e Venda so entram como entrada, e as categorias antigas seguem nos dois sentidos`() {
        assertIs<ExpenseResult.Saved>(lancar(ExpenseCategory.SPONSORSHIP, ExpenseDirection.IN))
        assertIs<ExpenseResult.Saved>(lancar(ExpenseCategory.SALE, ExpenseDirection.IN))
        assertEquals(setOf("category"), assertIs<ExpenseResult.Invalid>(lancar(ExpenseCategory.SPONSORSHIP, ExpenseDirection.OUT)).fields)
        assertEquals(setOf("category"), assertIs<ExpenseResult.Invalid>(lancar(ExpenseCategory.SALE, ExpenseDirection.OUT)).fields)
        ExpenseCategory.entries.filterNot { it.incomeOnly }.forEach { category ->
            assertIs<ExpenseResult.Saved>(lancar(category, ExpenseDirection.IN), "$category IN")
            assertIs<ExpenseResult.Saved>(lancar(category, ExpenseDirection.OUT), "$category OUT")
        }
        assertEquals(2000, extrato().totalInCents - ExpenseCategory.entries.filterNot { it.incomeOnly }.size * 1000L)
    }
}
