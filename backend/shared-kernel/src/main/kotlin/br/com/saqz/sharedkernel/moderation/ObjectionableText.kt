package br.com.saqz.sharedkernel.moderation

import java.text.Normalizer

/**
 * Filtro de texto ofensivo para o que uma pessoa escreve e outras leem: avisos, nomes, títulos
 * (Apple 1.2 pede um filtro antes da publicação). É deliberadamente conservador — xingamento
 * forte, termo sexual explícito e ofensa a grupos de pessoas. Gíria que no esporte é elogio
 * ("pica", "foda", "puta jogo") fica de fora: barrar aviso legítimo custa mais do que deixar
 * passar um palavrão leve, e o resto cai na denúncia.
 *
 * A comparação é por palavra inteira, sem acento, em minúsculas, com letras repetidas
 * colapsadas ("caralhooo") e as trocas mais comuns de número por letra ("p0rr4").
 */
object ObjectionableText {
    fun contains(text: String): Boolean {
        val normalized = normalize(text)
        if (normalized.isBlank()) return false
        return PATTERNS.any { it.containsMatchIn(normalized) }
    }

    private fun normalize(text: String): String {
        val lower = text.lowercase()
            .map { LEET[it] ?: it }
            .joinToString("")
        val unaccented = Normalizer.normalize(lower, Normalizer.Form.NFD).replace(DIACRITICS, "")
        return " " + unaccented.replace(NON_LETTER, " ") + " "
    }

    private val DIACRITICS = Regex("\\p{M}+")
    private val NON_LETTER = Regex("[^a-z]+")
    private val LEET = mapOf('0' to 'o', '1' to 'i', '3' to 'e', '4' to 'a', '5' to 's', '7' to 't', '@' to 'a', '$' to 's')

    /** Cada termo vira um padrão em que cada letra pode se repetir e as palavras se separam por espaço. */
    private fun pattern(term: String): Regex {
        val words = term.split(' ').filter(String::isNotEmpty).joinToString(" +") { word ->
            word.toList().fold(mutableListOf<Char>()) { acc, c -> if (acc.lastOrNull() != c) acc += c; acc }
                .joinToString("") { "$it+" }
        }
        return Regex(" $words ")
    }

    private val TERMS = listOf(
        // xingamentos e termos sexuais explícitos (pt-BR)
        "filho da puta", "filha da puta", "fdp", "puta que pariu", "puta que te pariu",
        "vai se foder", "vai se fuder", "vsf", "foda se", "fodase", "foder", "fuder", "fodido", "fudido",
        "tomar no cu", "toma no cu", "vai tomar no cu", "tnc", "vtnc", "pau no cu", "cuzao", "cusao",
        "arrombado", "arrombada", "caralho", "porra", "buceta", "boceta", "xoxota", "xereca", "piroca",
        "punheta", "punheteiro", "vadia", "vagabunda", "rapariga", "chupa meu pau", "chupa minha rola",
        "estuprar", "estuprador", "estupro",
        // ofensas a grupos de pessoas (pt-BR)
        "viado", "viadinho", "bicha", "boiola", "baitola", "traveco", "sapatao", "crioulo", "macaca",
        "retardado", "retardada", "mongoloide",
        // inglês
        "fuck", "fucking", "fucker", "motherfucker", "cunt", "bitch", "whore", "slut", "asshole",
        "nigger", "nigga", "faggot", "fag", "retard",
    )

    private val PATTERNS = TERMS.map(::pattern)
}
