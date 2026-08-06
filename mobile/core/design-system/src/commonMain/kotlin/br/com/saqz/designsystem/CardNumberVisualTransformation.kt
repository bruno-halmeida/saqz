package br.com.saqz.designsystem

/**
 * Teto absoluto de dígitos de um número de cartão de crédito. Cobertura: Visa/Mastercard/
 * Elo/Hipercard = 16, Amex = 15, Diners = 14, e algumas Visa = 19. Não existe bandeira
 * válida acima de 19. Fonte única — a máscara e a validação no ViewModel referenciam esta
 * constante para não divergirem.
 */
const val MAX_CARD_NUMBER_DIGITS = 19

/** Exibe o número do cartão como `XXXX XXXX XXXX XXXX` sem alterar o valor do campo. */
class CardNumberVisualTransformation : DigitMaskVisualTransformation(
    maximumDigits = MAX_CARD_NUMBER_DIGITS,
    format = ::formatCardNumber,
)

private fun formatCardNumber(digits: String): String = digits.chunked(4).joinToString(" ")
