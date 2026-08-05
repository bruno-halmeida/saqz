package br.com.saqz.designsystem

/** Exibe a validade do cartão como `MM/AA` sem alterar o valor do campo. */
class CardExpiryVisualTransformation : DigitMaskVisualTransformation(
    maximumDigits = 4,
    format = ::formatCardExpiry,
)

private fun formatCardExpiry(digits: String): String = when {
    digits.length <= 2 -> digits
    else -> "${digits.take(2)}/${digits.drop(2)}"
}
