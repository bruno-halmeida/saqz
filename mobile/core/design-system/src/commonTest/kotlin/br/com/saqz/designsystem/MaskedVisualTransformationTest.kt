package br.com.saqz.designsystem

import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals

class MaskedVisualTransformationTest {
    @Test
    fun phone_formats_digits_without_changing_the_raw_value() {
        val transformed = PhoneVisualTransformation().filter(AnnotatedString("11999990000"))

        assertEquals("(11) 99999-0000", transformed.text.text)
        assertEquals(11, transformed.offsetMapping.transformedToOriginal(transformed.text.length))
        assertEquals(11, transformed.offsetMapping.originalToTransformed(11).let {
            transformed.offsetMapping.transformedToOriginal(it)
        })
    }

    @Test
    fun cpf_formats_digits_without_changing_the_raw_value() {
        val transformed = CpfVisualTransformation().filter(AnnotatedString("12345678901"))

        assertEquals("123.456.789-01", transformed.text.text)
        assertEquals(11, transformed.offsetMapping.transformedToOriginal(transformed.text.length))
    }

    @Test
    fun cpf_does_not_show_digits_beyond_the_mask() {
        val transformed = CpfVisualTransformation().filter(AnnotatedString("123456789012"))

        assertEquals("123.456.789-01", transformed.text.text)
    }

    @Test
    fun phone_offset_mapping_skips_mask_separators_in_both_directions() {
        val transformed = PhoneVisualTransformation().filter(AnnotatedString("11999990000"))
        val mapping = transformed.offsetMapping

        assertEquals(5, mapping.originalToTransformed(2))
        assertEquals(2, mapping.transformedToOriginal(4))
        assertEquals(2, mapping.transformedToOriginal(5))
        assertEquals(11, mapping.originalToTransformed(7))
        assertEquals(7, mapping.transformedToOriginal(11))
    }

    @Test
    fun deleting_in_the_middle_uses_raw_offsets_and_keeps_the_mask_consistent() {
        val raw = "11999990000"
        val transformed = PhoneVisualTransformation().filter(AnnotatedString(raw))
        val mapping = transformed.offsetMapping
        val cursorInTransformedText = mapping.originalToTransformed(7)
        val cursorInRawText = mapping.transformedToOriginal(cursorInTransformedText)
        val editedRaw = raw.removeRange(cursorInRawText - 1, cursorInRawText)

        assertEquals(7, cursorInRawText)
        assertEquals("1199990000", editedRaw)
        assertEquals(
            "(11) 99990-000",
            PhoneVisualTransformation().filter(AnnotatedString(editedRaw)).text.text,
        )
    }

    @Test
    fun inserting_in_the_middle_uses_raw_offsets_and_keeps_the_mask_consistent() {
        val raw = "1199990000"
        val transformed = PhoneVisualTransformation().filter(AnnotatedString(raw))
        val mapping = transformed.offsetMapping
        val cursorInTransformedText = mapping.originalToTransformed(6)
        val cursorInRawText = mapping.transformedToOriginal(cursorInTransformedText)
        val editedRaw = raw.substring(0, cursorInRawText) + "9" + raw.substring(cursorInRawText)

        assertEquals(6, cursorInRawText)
        assertEquals("11999990000", editedRaw)
        assertEquals(
            "(11) 99999-0000",
            PhoneVisualTransformation().filter(AnnotatedString(editedRaw)).text.text,
        )
    }

    @Test
    fun cpf_offset_mapping_stays_before_separators() {
        val mapping = CpfVisualTransformation()
            .filter(AnnotatedString("12345678901"))
            .offsetMapping

        assertEquals(3, mapping.transformedToOriginal(3))
        assertEquals(3, mapping.transformedToOriginal(4))
        assertEquals(6, mapping.transformedToOriginal(7))
        assertEquals(9, mapping.transformedToOriginal(11))
    }

    @Test
    fun cep_formats_digits_without_changing_the_raw_value() {
        val transformed = CepVisualTransformation().filter(AnnotatedString("12345678"))

        assertEquals("12345-678", transformed.text.text)
        assertEquals(8, transformed.offsetMapping.transformedToOriginal(transformed.text.length))
    }

    @Test
    fun cep_does_not_show_digits_beyond_the_mask() {
        val transformed = CepVisualTransformation().filter(AnnotatedString("123456789012"))

        assertEquals("12345-678", transformed.text.text)
    }
}
