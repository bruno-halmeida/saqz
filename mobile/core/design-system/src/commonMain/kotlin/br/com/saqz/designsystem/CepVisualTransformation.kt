package br.com.saqz.designsystem

/** Exibe CEP como `XXXXX-XXX` sem alterar o valor do campo. */
class CepVisualTransformation : DigitMaskVisualTransformation(
    maximumDigits = 8,
    format = ::formatCep,
)

private fun formatCep(digits: String): String = when {
    digits.length <= 5 -> digits
    else -> "${digits.take(5)}-${digits.drop(5)}"
}
