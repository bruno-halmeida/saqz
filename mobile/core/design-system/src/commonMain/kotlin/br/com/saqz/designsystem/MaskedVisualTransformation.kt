package br.com.saqz.designsystem

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

open class DigitMaskVisualTransformation(
    private val maximumDigits: Int,
    private val format: (String) -> String,
) : VisualTransformation {
    /**
     * O que o campo pode guardar: só os dígitos que a máscara mostra. O [SaqzInput] de
     * `String` passa cada edição por aqui. Sem isso o dígito além do limite entrava no valor
     * sem aparecer — a tela mostrava um número completo e a validação o recusava (revisão da
     * Apple de 09/10/2026: "(11) 99999-0000" na tela, 12 dígitos no valor).
     */
    open fun accept(input: String): String = input.filter(::isAsciiDigit).take(maximumDigits)

    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter(Char::isDigit).take(maximumDigits)
        val transformed = format(digits)
        return TransformedText(
            text = AnnotatedString(transformed),
            offsetMapping = DigitMaskOffsetMapping(text.text, transformed, maximumDigits),
        )
    }
}

// Só 0–9: as validações comparam com '9' ASCII, e um dígito de outro alfabeto passaria
// pela máscara (`Char.isDigit`) sem passar por elas.
internal fun isAsciiDigit(character: Char): Boolean = character in '0'..'9'

private class DigitMaskOffsetMapping(
    private val original: String,
    private val transformed: String,
    private val maximumDigits: Int,
) : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int {
        val bounded = offset.coerceIn(0, original.length)
        val digits = original.take(bounded).count(Char::isDigit).coerceAtMost(maximumDigits)
        return transformedBoundary(digits)
    }

    override fun transformedToOriginal(offset: Int): Int {
        val bounded = offset.coerceIn(0, transformed.length)
        val digits = transformed.take(bounded).count(Char::isDigit)
        return originalBoundary(digits)
    }

    private fun transformedBoundary(digits: Int): Int {
        if (digits == 0) return 0
        var seen = 0
        transformed.forEachIndexed { index, character ->
            if (character.isDigit()) {
                seen++
                if (seen == digits) {
                    var boundary = index + 1
                    while (boundary < transformed.length && !transformed[boundary].isDigit()) boundary++
                    return boundary
                }
            }
        }
        return transformed.length
    }

    private fun originalBoundary(digits: Int): Int {
        if (digits == 0) return 0
        var seen = 0
        original.forEachIndexed { index, character ->
            if (character.isDigit()) {
                seen++
                if (seen == digits) return index + 1
            }
        }
        return original.length
    }
}
