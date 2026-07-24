package br.com.saqz.access.domain

@JvmInline
value class PhoneNumber private constructor(
    val value: String,
) {
    override fun toString(): String = "PhoneNumber(+55***${value.takeLast(2)})"

    companion object {
        private const val MOBILE_NATIONAL_LENGTH = 11
        private val DDD_RANGE = 11..99

        fun from(raw: String): PhoneNumber {
            val trimmed = raw.trim()
            val digits = trimmed.filter(Char::isDigit)
            require(digits.isNotEmpty()) { "phone must contain digits" }

            val hasCountryCode = trimmed.startsWith("+") || digits.length >= 12
            val national = if (hasCountryCode) {
                require(digits.startsWith("55")) { "phone must use Brazil country code +55" }
                digits.removePrefix("55")
            } else {
                digits
            }

            require(national.length == MOBILE_NATIONAL_LENGTH) { "phone must contain 11 digits (mobile) after the country code" }
            require(national.take(2).toInt() in DDD_RANGE) { "phone area code must be between 11 and 99" }
            require(national[2] == '9') { "phone must be a mobile number with a leading 9 after the area code" }
            return PhoneNumber("+55$national")
        }
    }
}
