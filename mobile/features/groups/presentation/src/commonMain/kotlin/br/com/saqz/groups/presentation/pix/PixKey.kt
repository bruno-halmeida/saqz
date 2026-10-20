package br.com.saqz.groups.presentation.pix

import br.com.saqz.designsystem.phoneFieldDigits

/**
 * Tipo da chave Pix do grupo. O [label] é o que o backend guarda em `pixLabel` (texto de 2 a 140 caracteres, que não
 * muda): "E-mail", "Aleatória", "CPF" ou "Telefone".
 */
enum class PixKeyType(val label: String) {
    EMAIL("E-mail"),
    RANDOM("Aleatória"),
    CPF("CPF"),
    PHONE("Telefone"),
    ;

    companion object {
        /** O tipo que um `pixLabel` guardado nomeia; texto livre de grupo antigo dá `null`. */
        fun fromLabel(label: String?): PixKeyType? = entries.firstOrNull { it.label == label?.trim() }
    }
}

/** Regras puras da chave Pix por tipo: normalizar o que se digita ou cola, validar, deduzir o tipo e formatar. */
object PixKeys {
    private const val CPF_DIGITS = 11
    private const val UUID_LENGTH = 36
    private const val UUID_HEX_DIGITS = 32
    private const val EMAIL_MAX = 77
    private const val MIN_DDD = 11
    private val UuidGroups = listOf(8, 4, 4, 4, 12)
    private val EmailPattern = Regex("^[a-z0-9._%+\\-]+@[a-z0-9\\-]+(\\.[a-z0-9\\-]+)+$")
    private val PhoneLikePattern = Regex("^[0-9.\\-()+\\s]+$")

    /**
     * O que o campo guarda ao digitar ou colar. E-mail: minúsculas e sem espaços. Aleatória: minúsculas, sem espaços, e
     * 32 hexadecimais seguidos ganham os hífens. CPF: só os dígitos (até 11). Telefone: os 11 dígitos, sem +55.
     */
    fun normalize(type: PixKeyType, raw: String): String = when (type) {
        PixKeyType.EMAIL -> raw.filterNot(Char::isWhitespace).lowercase().take(EMAIL_MAX)
        PixKeyType.RANDOM -> normalizeRandom(raw)
        PixKeyType.CPF -> raw.filter(::isAsciiDigit).take(CPF_DIGITS)
        PixKeyType.PHONE -> phoneFieldDigits(raw)
    }

    fun isValid(type: PixKeyType, stored: String): Boolean = when (type) {
        PixKeyType.EMAIL -> EmailPattern.matches(stored)
        PixKeyType.RANDOM -> isUuid(stored)
        PixKeyType.CPF -> isValidCpf(stored)
        PixKeyType.PHONE -> isMobilePhone(stored)
    }

    /** O tipo de uma chave antiga sem tipo, pelo formato; `null` se não der para deduzir. */
    fun deduce(key: String?): PixKeyType? {
        val text = key?.trim().orEmpty()
        if (text.isEmpty()) return null
        if (text.contains('@')) return PixKeyType.EMAIL.takeIf { isValid(it, normalize(it, text)) }
        if (text.all { it.isLetterOrDigit() || it == '-' } && isUuid(normalizeRandom(text))) return PixKeyType.RANDOM
        if (!PhoneLikePattern.matches(text)) return null
        val digits = text.filter(::isAsciiDigit)
        return when {
            isValidCpf(digits) -> PixKeyType.CPF
            isMobilePhone(phoneFieldDigits(digits)) -> PixKeyType.PHONE
            else -> null
        }
    }

    /** A chave como se mostra: CPF `000.000.000-00` e telefone `(11) 99999-9999`; os demais como guardados. */
    fun format(type: PixKeyType, stored: String): String = when {
        type == PixKeyType.CPF && stored.length == CPF_DIGITS && stored.all(::isAsciiDigit) ->
            "${stored.take(3)}.${stored.substring(3, 6)}.${stored.substring(6, 9)}-${stored.drop(9)}"
        type == PixKeyType.PHONE && stored.length == CPF_DIGITS && stored.all(::isAsciiDigit) ->
            "(${stored.take(2)}) ${stored.substring(2, 7)}-${stored.drop(7)}"
        else -> stored
    }

    private fun normalizeRandom(raw: String): String {
        val text = raw.filterNot(Char::isWhitespace).lowercase().take(UUID_LENGTH)
        val compact = text.replace("-", "")
        return if (compact.length == UUID_HEX_DIGITS && compact.all(::isHex) && text.length == UUID_HEX_DIGITS) {
            hyphenate(compact)
        } else {
            text
        }
    }

    private fun hyphenate(compact: String): String {
        var start = 0
        return UuidGroups.joinToString("-") { size -> compact.substring(start, start + size).also { start += size } }
    }

    private fun isUuid(text: String): Boolean {
        if (text.length != UUID_LENGTH) return false
        val groups = text.split('-')
        return groups.map(String::length) == UuidGroups && groups.all { g -> g.all(::isHex) }
    }

    private fun isValidCpf(digits: String): Boolean {
        if (digits.length != CPF_DIGITS || !digits.all(::isAsciiDigit) || digits.all { it == digits[0] }) return false
        val numbers = digits.map { it - '0' }
        return checkDigit(numbers, 9) == numbers[9] && checkDigit(numbers, 10) == numbers[10]
    }

    private fun checkDigit(numbers: List<Int>, length: Int): Int {
        val sum = (0 until length).sumOf { numbers[it] * (length + 1 - it) }
        val rest = sum * 10 % CPF_DIGITS
        return if (rest == 10) 0 else rest
    }

    /** DDD de 11 a 99 e celular (começa em 9 depois do DDD). */
    private fun isMobilePhone(digits: String): Boolean =
        digits.length == CPF_DIGITS && digits.all(::isAsciiDigit) && digits.take(2).toInt() >= MIN_DDD && digits[2] == '9'

    private fun isAsciiDigit(c: Char) = c in '0'..'9'

    private fun isHex(c: Char) = c in '0'..'9' || c in 'a'..'f'
}
