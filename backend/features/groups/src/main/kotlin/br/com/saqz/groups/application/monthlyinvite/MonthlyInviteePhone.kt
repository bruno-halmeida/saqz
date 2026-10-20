package br.com.saqz.groups.application.monthlyinvite

/**
 * Celular brasileiro do convidado, nas mesmas regras do `PhoneNumber` de `features/access` (módulos de feature não
 * dependem uns dos outros): 11 dígitos, DDD de 11 a 99 e 9 inicial. [value] é `+55DDD9XXXXXXXX`, o formato de
 * `access_users.phone`, para casar com a conta de quem entra.
 */
@JvmInline
value class MonthlyInviteePhone private constructor(val value: String) {
    /** Os 11 dígitos que o app mostra (sem +55). */
    val national: String get() = value.removePrefix("+55")

    override fun toString(): String = "MonthlyInviteePhone(+55***${value.takeLast(2)})"

    companion object {
        private const val MOBILE_NATIONAL_LENGTH = 11
        private val DDD_RANGE = 11..99

        fun parse(raw: String): MonthlyInviteePhone? {
            val trimmed = raw.trim()
            val digits = trimmed.filter(Char::isDigit)
            if (digits.isEmpty()) return null
            val hasCountryCode = trimmed.startsWith("+") || digits.length >= 12
            val national = if (hasCountryCode) {
                if (!digits.startsWith("55")) return null
                digits.removePrefix("55")
            } else {
                digits
            }
            if (national.length != MOBILE_NATIONAL_LENGTH) return null
            if (national.take(2).toInt() !in DDD_RANGE || national[2] != '9') return null
            return MonthlyInviteePhone("+55$national")
        }

        fun fromStored(value: String): MonthlyInviteePhone = MonthlyInviteePhone(value)
    }
}
