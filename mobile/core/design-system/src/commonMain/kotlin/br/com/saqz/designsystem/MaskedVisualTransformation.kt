package br.com.saqz.designsystem

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

open class DigitMaskVisualTransformation(
    private val maximumDigits: Int,
    private val format: (String) -> String,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter(Char::isDigit).take(maximumDigits)
        val transformed = format(digits)
        return TransformedText(
            text = AnnotatedString(transformed),
            offsetMapping = DigitMaskOffsetMapping(text.text, transformed, maximumDigits),
        )
    }
}

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
