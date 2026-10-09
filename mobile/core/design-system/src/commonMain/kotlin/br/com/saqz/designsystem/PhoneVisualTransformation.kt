package br.com.saqz.designsystem

/** Exibe telefones brasileiros como `(XX) XXXXX-XXXX` sem alterar o valor do campo. */
class PhoneVisualTransformation : DigitMaskVisualTransformation(
    maximumDigits = PHONE_DIGITS,
    format = ::formatPhone,
) {
    override fun accept(input: String): String = phoneFieldDigits(input)
}

/**
 * O que o campo de telefone mostra e guarda: os 11 dígitos de DDD + 9 + 8, nada além.
 *
 * O +55 não aparece nem se digita: é de quem envia, porque o WhatsApp precisa dele. Número
 * colado ou guardado com o código do país (`+55 11 98765-4321`, 13 dígitos) chega ao campo
 * sem ele.
 */
fun phoneFieldDigits(input: String): String {
    val digits = input.filter(::isAsciiDigit)
    val national = if (digits.length == COUNTRY_CODE.length + PHONE_DIGITS && digits.startsWith(COUNTRY_CODE)) {
        digits.drop(COUNTRY_CODE.length)
    } else {
        digits
    }
    return national.take(PHONE_DIGITS)
}

private const val PHONE_DIGITS = 11
private const val COUNTRY_CODE = "55"

private fun formatPhone(digits: String): String = when {
    digits.isEmpty() -> ""
    digits.length <= 2 -> "(${digits}"
    digits.length <= 7 -> "(${digits.take(2)}) ${digits.drop(2)}"
    else -> "(${digits.take(2)}) ${digits.drop(2).take(5)}-${digits.drop(7)}"
}
