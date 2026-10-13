package br.com.saqz.groups.application.report

import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportCsvTest {
    private val zone = ZoneId.of("America/Sao_Paulo")

    @Test
    fun `attendance csv has Excel friendly header, local date and one row per game`() {
        val csv = ReportCsv.attendance(
            listOf(ReportGameRow(UUID.randomUUID(), "Racha de quinta", Instant.parse("2026-10-08T22:30:00Z"), 12, "COMPLETED", 10, 9, 2, 1, 3)),
            zone,
        )

        assertEquals(
            "﻿Jogo;Data;Horário;Vagas;Confirmados;Recusaram;Lista de espera;Sem resposta;Situação\r\n" +
                "Racha de quinta;08/10/2026;19:30;12;10;2;1;3;Encerrado\r\n",
            csv,
        )
    }

    @Test
    fun `ledger csv signs exits and uses decimal comma`() {
        val csv = ReportCsv.ledger(
            listOf(
                ReportLedgerRow(Instant.parse("2026-10-02T15:00:00Z"), "IN", "CHARGE", "Mensalidade · Marina", "MONTHLY", "PIX", 12_050),
                ReportLedgerRow(Instant.parse("2026-10-03T15:00:00Z"), "OUT", "EXPENSE", "Aluguel", "VENUE", null, 30_000),
            ),
            zone,
        )

        assertTrue(csv.contains("02/10/2026;Entrada;Mensalidade · Marina;MONTHLY;PIX;120,50\r\n"))
        assertTrue(csv.contains("03/10/2026;Saída;Aluguel;VENUE;;-300,00\r\n"))
    }

    @Test
    fun `cells with separators or quotes are quoted and formulas are neutralised`() {
        val csv = ReportCsv.ledger(
            listOf(ReportLedgerRow(Instant.parse("2026-10-02T15:00:00Z"), "OUT", "EXPENSE", "=HYPERLINK(\"x\";\"y\")", "OTHER", null, 100)),
            zone,
        )

        assertTrue(csv.contains("\"'=HYPERLINK(\"\"x\"\";\"\"y\"\")\""), csv)
    }

    @Test
    fun `money formats cents`() {
        assertEquals("0,05", ReportCsv.money(5))
        assertEquals("-1,00", ReportCsv.money(-100))
    }
}
