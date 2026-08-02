package br.com.saqz.designsystem

/** Exibe CPF/CNPJ como `XXX.XXX.XXX-XX` sem alterar o valor do campo. */
class CpfVisualTransformation : DigitMaskVisualTransformation(
    maximumDigits = 14,
    format = ::formatCpfCnpj,
)

private fun formatCpfCnpj(digits: String): String = when {
    digits.length <= 3 -> digits
    digits.length <= 6 -> "${digits.take(3)}.${digits.drop(3)}"
    digits.length <= 9 -> "${digits.take(3)}.${digits.drop(3).take(3)}.${digits.drop(6)}"
    else -> "${digits.take(3)}.${digits.drop(3).take(3)}.${digits.drop(6).take(3)}-${digits.drop(9)}"
}
