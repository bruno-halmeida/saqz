package br.com.saqz.designsystem

/** Exibe telefones brasileiros como `(XX) XXXXX-XXXX` sem alterar o valor do campo. */
class PhoneVisualTransformation : DigitMaskVisualTransformation(
    maximumDigits = 11,
    format = ::formatPhone,
)

private fun formatPhone(digits: String): String = when {
    digits.isEmpty() -> ""
    digits.length <= 2 -> "(${digits}"
    digits.length <= 7 -> "(${digits.take(2)}) ${digits.drop(2)}"
    else -> "(${digits.take(2)}) ${digits.drop(2).take(5)}-${digits.drop(7)}"
}
