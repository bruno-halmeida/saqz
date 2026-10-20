package br.com.saqz.groups.presentation.monthlyinvite

/** Erro do campo WhatsApp na folha "Nova pessoa" (CONV-010). */
enum class MonthlyInvitePhoneError { Empty, Incomplete, NotMobile, Duplicate, AlreadyInvited }

/**
 * Regras do cadastro de convidado, fora da ViewModel. O campo guarda só os 11 dígitos (o +55 nunca aparece); o
 * celular do Brasil tem DDD de 11 a 99 e começa com 9, as mesmas regras do backend.
 */
object MonthlyInviteRules {
    const val PHONE_DIGITS = 11
    private const val MIN_NAME_LETTERS = 2
    private val DDD_RANGE = 11..99

    fun phoneDigits(raw: String): String = raw.filter(Char::isDigit).take(PHONE_DIGITS)

    fun isValidName(name: String): Boolean = name.trim().count(Char::isLetter) >= MIN_NAME_LETTERS

    fun phoneError(digits: String): MonthlyInvitePhoneError? = when {
        digits.isEmpty() -> MonthlyInvitePhoneError.Empty
        digits.length < PHONE_DIGITS -> MonthlyInvitePhoneError.Incomplete
        digits.take(2).toInt() !in DDD_RANGE || digits[2] != '9' -> MonthlyInvitePhoneError.NotMobile
        else -> null
    }

    /** "(11) 98765-4321". */
    fun formatPhone(digits: String): String =
        if (digits.length == PHONE_DIGITS) "(${digits.take(2)}) ${digits.substring(2, 7)}-${digits.substring(7)}" else digits

    fun firstName(name: String): String = name.trim().substringBefore(' ')
}
