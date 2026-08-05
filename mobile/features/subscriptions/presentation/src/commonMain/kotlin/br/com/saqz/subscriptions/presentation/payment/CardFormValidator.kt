package br.com.saqz.subscriptions.presentation.payment

/**
 * Função pura, fora da ViewModel (AGENTS.md §4) — mesmo padrão do `GroupSetupValidator`.
 * Erro no campo, não card genérico (VUL-196): cada campo tem seu próprio [CardFormError].
 */
internal fun validateCardForm(form: CardFormState): Set<CardFormError> = buildSet {
    val numberDigits = form.number.filter(Char::isDigit)
    if (numberDigits.length !in 12..19 || !isLuhnValid(numberDigits)) add(CardFormError.NumberInvalid)
    if (!isValidCardExpiry(form.expiry)) add(CardFormError.ExpiryInvalid)
    val cvvDigits = form.cvv.filter(Char::isDigit)
    if (cvvDigits.length !in 3..4) add(CardFormError.CvvInvalid)
    if (form.holderName.trim().length < 2) add(CardFormError.HolderNameRequired)
    val postalDigits = form.postalCode.filter(Char::isDigit)
    if (postalDigits.length != 8) add(CardFormError.PostalCodeInvalid)
    if (form.addressNumber.isBlank()) add(CardFormError.AddressNumberRequired)
    val phoneDigits = form.phone.filter(Char::isDigit)
    if (phoneDigits.length !in 10..11) add(CardFormError.PhoneInvalid)
}

/** Algoritmo padrão (mod 10) — mesmo checado por toda bandeira aceita pelo Asaas. */
internal fun isLuhnValid(digits: String): Boolean {
    if (digits.isEmpty()) return false
    var sum = 0
    var doubleDigit = false
    for (index in digits.length - 1 downTo 0) {
        var value = digits[index] - '0'
        if (doubleDigit) {
            value *= 2
            if (value > 9) value -= 9
        }
        sum += value
        doubleDigit = !doubleDigit
    }
    return sum % 10 == 0
}

// ponytail: valida só o formato (mês 01-12, 4 dígitos). Expiração real é o Asaas quem
// recusa no create() — mesmo padrão do cupom expirado (PaymentViewModel.loadSummary).
internal fun isValidCardExpiry(expiry: String): Boolean {
    val digits = expiry.filter(Char::isDigit)
    if (digits.length != 4) return false
    val month = digits.take(2).toIntOrNull() ?: return false
    return month in 1..12
}
