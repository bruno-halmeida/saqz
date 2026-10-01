package br.com.saqz.sharedkernel.moderation

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ObjectionableTextTest {
    @Test
    fun `flags strong profanity and slurs as whole words`() {
        listOf(
            "Seu filho da puta",
            "CARALHO, que jogo",
            "vai tomar no cu",
            "tnc",
            "você é um viado",
            "fuck this",
        ).forEach { assertTrue(ObjectionableText.contains(it), it) }
    }

    @Test
    fun `sees through accents repeated letters punctuation and leetspeak`() {
        listOf("caralhooo", "p0rr4", "Porra!!!", "cuzão", "foda-se", "Sapatão", "FILHO   DA  PUTA").forEach {
            assertTrue(ObjectionableText.contains(it), it)
        }
    }

    @Test
    fun `keeps everyday sports talk and words that merely contain a term`() {
        listOf(
            "Puta jogo ontem, galera!",
            "O cara é pica no saque",
            "Jogo foda demais",
            "Treino na quadra da Escola Bicharia",
            "Cuidado com a rede",
            "Turma do vôlei de sábado",
            "Chegar às 19h, levar bola",
            "Recife",
            "Cacete, choveu",
            "",
        ).forEach { assertFalse(ObjectionableText.contains(it), it) }
    }
}
