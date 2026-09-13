package br.com.saqz.receivables.presentation

import br.com.saqz.receivables.domain.ReceiptLegalAddress
import br.com.saqz.receivables.domain.ReceiptLegalRegistration
import kotlinx.datetime.LocalDate

internal fun OnboardingForm.registration(): ReceiptLegalRegistration? {
    val amount = incomeCents(this[OnboardingField.INCOME]) ?: return null
    val birth = if (company) null else {
        val input = this[OnboardingField.BIRTH_DATE]
        if (!input.matches(Regex("[0-9]{2}/[0-9]{2}/[0-9]{4}"))) return null
        val iso = "${input.substring(6)}-${input.substring(3, 5)}-${input.take(2)}"
        runCatching { LocalDate.parse(iso).toString() }.getOrNull() ?: return null
    }
    val registration = ReceiptLegalRegistration(this[OnboardingField.NAME].trim(), this[OnboardingField.EMAIL].trim(),
        this[OnboardingField.DOCUMENT], this[OnboardingField.PHONE], amount,
        ReceiptLegalAddress(this[OnboardingField.STREET].trim(), this[OnboardingField.NUMBER].trim(),
            this[OnboardingField.PROVINCE].trim(), this[OnboardingField.POSTAL_CODE]), birth,
        if (company) this[OnboardingField.COMPANY_TYPE] else null)
    return registration.takeIf { it.valid() && (it.cpfCnpj.length == 14) == company }
}
internal fun incomeCents(input: String): Long? {
    if (!input.matches(Regex("[0-9]{1,15}([,.][0-9]{1,2})?"))) return null
    val parts = input.replace(',', '.').split('.')
    val units = parts[0].toLongOrNull() ?: return null
    val cents = parts.getOrNull(1)?.padEnd(2, '0')?.toLongOrNull() ?: 0
    if (units > (Long.MAX_VALUE - cents) / 100) return null
    return units * 100 + cents
}
internal fun safeOnboardingUrl(url: String): Boolean =
    Regex("https://([A-Za-z0-9-]+\\.)*asaas\\.com(?::443)?/[^\\s]*").matches(url)
