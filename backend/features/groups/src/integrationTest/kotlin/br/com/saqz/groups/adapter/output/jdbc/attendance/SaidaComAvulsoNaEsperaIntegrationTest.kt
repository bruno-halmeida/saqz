package br.com.saqz.groups.adapter.output.jdbc.attendance

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID
import kotlin.test.assertEquals

/**
 * DEB-006 (PRES-006 B, PROM-002): vaga liberada antes do prazo por exclusão de conta ou remoção pelo organizador.
 * Sem mensalista na espera sobe o avulso mais antigo; com mensalista na espera, sobe o mensalista (mesmo que chegado depois).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SaidaComAvulsoNaEsperaIntegrationTest {
    private lateinit var h: FilaHarness

    @BeforeEach fun reset() { h = FilaHarness(this); h.cenario(capacity = 4, priority = true) }

    private class Cena(val saindo: UUID, val avulsos: List<UUID>)

    /** Jogo lotado (o [Cena.saindo] e mais três confirmados) e dois avulsos na espera, em ordem de chegada. */
    private fun cena(): Cena {
        val saindo = h.atleta(); h.presenca(saindo, "CONFIRMED")
        repeat(3) { h.presenca(h.atleta(), "CONFIRMED") }
        val avulsos = listOf(h.atleta(), h.atleta())
        avulsos.forEachIndexed { i, id -> h.presenca(id, "WAITLISTED", i + 1L) }
        return Cena(saindo, avulsos)
    }

    private fun excluirConta(saindo: UUID) = h.dataSource.let {
        br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner(it).inTransaction {
            h.liberar().release(h.group, saindo, saindo)
            br.com.saqz.groups.adapter.output.jdbc.athlete.JdbcAthleteRepository(it).remove(h.group, saindo)
        }
    }

    @Test fun `exclusao de conta sem mensalista na espera sobe o avulso mais antigo, com diaria e aviso`() {
        val c = cena()
        excluirConta(c.saindo)
        assertEquals("CONFIRMED", h.status(c.avulsos[0])); assertEquals("WAITLISTED", h.status(c.avulsos[1]))
        assertEquals(1, h.diarias(c.avulsos[0])); assertEquals(1, h.avisos(c.avulsos[0]))
        assertEquals(0, h.avisos(c.avulsos[1])); assertEquals(0, h.avisos(c.saindo))
    }

    @Test fun `remocao pelo organizador sem mensalista na espera sobe o avulso mais antigo`() {
        val c = cena()
        h.remover().execute(h.dono, h.group, c.saindo)
        assertEquals("DECLINED", h.status(c.saindo))
        assertEquals("CONFIRMED", h.status(c.avulsos[0])); assertEquals("WAITLISTED", h.status(c.avulsos[1]))
        assertEquals(1, h.avisos(c.avulsos[0]))
    }

    @Test fun `com mensalista na espera a vaga vai para o mensalista, mesmo chegado depois dos avulsos`() {
        val c = cena()
        val mensalista = h.atleta("MENSALISTA"); h.presenca(mensalista, "WAITLISTED", 3)
        excluirConta(c.saindo)
        assertEquals("CONFIRMED", h.status(mensalista))
        c.avulsos.forEach { assertEquals("WAITLISTED", h.status(it)) }
        assertEquals(0, h.diarias(mensalista)); assertEquals(1, h.avisos(mensalista))
    }

    @Test fun `remocao pelo organizador com mensalista na espera tambem sobe o mensalista`() {
        val c = cena()
        val mensalista = h.atleta("MENSALISTA"); h.presenca(mensalista, "WAITLISTED", 3)
        h.remover().execute(h.dono, h.group, c.saindo)
        assertEquals("CONFIRMED", h.status(mensalista))
        c.avulsos.forEach { assertEquals("WAITLISTED", h.status(it)) }
    }

    @Test fun `depois do prazo a vaga liberada vai para o mais antigo, mensalista ou nao`() {
        val c = cena()
        val mensalista = h.atleta("MENSALISTA"); h.presenca(mensalista, "WAITLISTED", 3)
        h.encerrarPrazo()
        excluirConta(c.saindo)
        assertEquals("CONFIRMED", h.status(c.avulsos[0]))
        assertEquals("WAITLISTED", h.status(mensalista))
    }
}
