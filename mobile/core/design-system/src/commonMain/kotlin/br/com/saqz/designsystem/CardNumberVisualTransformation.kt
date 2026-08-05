package br.com.saqz.designsystem

/** Exibe o número do cartão como `XXXX XXXX XXXX XXXX` sem alterar o valor do campo. */
class CardNumberVisualTransformation : DigitMaskVisualTransformation(
    maximumDigits = 19,
    format = ::formatCardNumber,
)

private fun formatCardNumber(digits: String): String = digits.chunked(4).joinToString(" ")
