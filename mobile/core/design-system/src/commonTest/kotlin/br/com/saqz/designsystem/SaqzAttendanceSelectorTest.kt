package br.com.saqz.designsystem

import androidx.compose.ui.graphics.Color
import br.com.saqz.designsystem.theme.SaqzColorTokens
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SaqzAttendanceSelectorTest {
    private val tokens = SaqzColorTokens.Light

    @Test
    fun eachIntentFillsWithItsExportColor() {
        assertEquals(tokens.primary, tokens.attendanceFill(SaqzAttendance.Going))
        assertEquals(tokens.accent, tokens.attendanceFill(SaqzAttendance.Maybe))
        assertEquals(tokens.errorForeground, tokens.attendanceFill(SaqzAttendance.Out))
    }

    @Test
    fun onlyMaybeKeepsNavyOverTheSolid() {
        assertEquals(tokens.onPrimary, tokens.attendanceOnFill(SaqzAttendance.Going))
        assertEquals(tokens.textPrimary, tokens.attendanceOnFill(SaqzAttendance.Maybe))
        assertEquals(tokens.onPrimary, tokens.attendanceOnFill(SaqzAttendance.Out))
    }

    @Test
    fun unselectedLabelIsNavyAndFadesWhenDisabled() {
        SaqzAttendance.entries.forEach { intent ->
            assertEquals(
                tokens.textPrimary,
                tokens.attendanceLabel(intent, selected = false, enabled = true),
            )
            assertEquals(
                tokens.disabledForeground,
                tokens.attendanceLabel(intent, selected = false, enabled = false),
                "opção não escolhida precisa recuar quando o seletor trava ($intent)",
            )
        }
    }

    // A regressão que o review pegou: o rótulo olhava `enabled` mas o fundo não, então
    // desabilitado + selecionado punha cinza sobre o sólido — 1.01:1 no "Não vou".
    @Test
    fun disabledKeepsTheChosenAnswerOnItsSolid() {
        SaqzAttendance.entries.forEach { intent ->
            assertEquals(
                tokens.attendanceOnFill(intent),
                tokens.attendanceLabel(intent, selected = true, enabled = false),
                "a resposta registrada não pode perder a cor de conteúdo ao travar ($intent)",
            )
        }
    }

    @Test
    fun selectedLabelClearsThreeToOneOnItsFillEvenWhenDisabled() {
        listOf(true, false).forEach { enabled ->
            SaqzAttendance.entries.forEach { intent ->
                val ratio = contrast(
                    tokens.attendanceLabel(intent, selected = true, enabled = enabled),
                    tokens.attendanceFill(intent),
                )
                assertTrue(
                    ratio >= 3.0,
                    "$intent com enabled=$enabled ficou em ${ratio.format()}:1 sobre o próprio fill",
                )
            }
        }
    }

    // ponytail: piso de 3:1 (WCAG AA para texto grande e para componentes de UI), não
    // 4.5:1 — branco sobre o #E5484D do export dá 3.91:1 no estado habilitado, e a
    // paleta é do VUL-44. O teto está anotado no PR; subir o piso pede outra cor de
    // erro, não uma mudança aqui.
    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun luminance(color: Color) =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun channel(value: Float): Double {
        val c = value.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun Double.format() = (this * 100).toInt() / 100.0
}
