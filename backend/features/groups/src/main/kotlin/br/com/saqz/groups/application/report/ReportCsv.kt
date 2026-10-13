package br.com.saqz.groups.application.report

import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * CSV pensado para abrir no Excel/Planilhas em português: separador `;`, vírgula decimal,
 * BOM UTF-8 e células que começam com `= + - @` neutralizadas contra injeção de fórmula.
 */
object ReportCsv {
    private const val BOM = "﻿"
    private val date = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val time = DateTimeFormatter.ofPattern("HH:mm")

    fun attendance(games: List<ReportGameRow>, zone: ZoneId): String = build(
        listOf("Jogo", "Data", "Horário", "Vagas", "Confirmados", "Recusaram", "Lista de espera", "Sem resposta", "Situação"),
        games.map {
            val local = it.startsAt.atZone(zone)
            listOf(
                it.title, date.format(local), time.format(local), it.capacity.toString(), it.confirmed.toString(),
                it.declined.toString(), it.waitlisted.toString(), it.noResponse.toString(),
                if (it.status == "COMPLETED") "Encerrado" else "Publicado",
            )
        },
    )

    fun ledger(rows: List<ReportLedgerRow>, zone: ZoneId): String = build(
        listOf("Data", "Tipo", "Descrição", "Categoria", "Forma de pagamento", "Valor (R$)"),
        rows.map {
            listOf(
                date.format(it.occurredAt.atZone(zone)),
                if (it.direction == "IN") "Entrada" else "Saída",
                it.title,
                it.category,
                it.paidMethod.orEmpty(),
                money(if (it.direction == "IN") it.amountCents else -it.amountCents),
            )
        },
    )

    fun money(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val abs = Math.abs(cents)
        return "$sign${abs / 100},${(abs % 100).toString().padStart(2, '0')}"
    }

    private fun build(header: List<String>, rows: List<List<String>>): String =
        (listOf(header) + rows).joinToString(separator = "\r\n", prefix = BOM, postfix = "\r\n") { row ->
            row.joinToString(";") { cell(it) }
        }

    private fun cell(raw: String): String {
        val safe = if (raw.firstOrNull() in setOf('=', '+', '@') || (raw.startsWith("-") && !raw.matches(Regex("-[0-9]+(,[0-9]+)?")))) {
            "'$raw"
        } else {
            raw
        }
        return if (safe.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + safe.replace("\"", "\"\"") + "\""
        } else {
            safe
        }
    }
}
