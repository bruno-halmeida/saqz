package br.com.saqz.subscriptions.presentation.payment

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardFormValidatorTest {
    @Test
    fun `luhn accepts a known valid card number`() {
        assertTrue(isLuhnValid("4111111111111111"))
    }

    @Test
    fun `luhn rejects a number with a wrong check digit`() {
        assertFalse(isLuhnValid("4111111111111112"))
    }

    @Test
    fun `luhn rejects blank input`() {
        assertFalse(isLuhnValid(""))
    }

    @Test
    fun `card expiry accepts a valid month and year`() {
        assertTrue(isValidCardExpiry("1228"))
    }

    @Test
    fun `card expiry rejects month zero or above twelve`() {
        assertFalse(isValidCardExpiry("0028"))
        assertFalse(isValidCardExpiry("1328"))
    }

    @Test
    fun `card expiry rejects anything but four digits`() {
        assertFalse(isValidCardExpiry("128"))
    }

    @Test
    fun `valid form has no errors`() = assertEquals(emptySet(), validateCardForm(validForm()))

    @Test
    fun `invalid number is flagged without touching other fields`() {
        val errors = validateCardForm(validForm().copy(number = "1234"))
        assertEquals(setOf(CardFormError.NumberInvalid), errors)
    }

    @Test
    fun `invalid expiry is flagged`() {
        val errors = validateCardForm(validForm().copy(expiry = "1399"))
        assertEquals(setOf(CardFormError.ExpiryInvalid), errors)
    }

    @Test
    fun `short cvv is flagged`() {
        val errors = validateCardForm(validForm().copy(cvv = "12"))
        assertEquals(setOf(CardFormError.CvvInvalid), errors)
    }

    @Test
    fun `blank holder name is flagged`() {
        val errors = validateCardForm(validForm().copy(holderName = "A"))
        assertEquals(setOf(CardFormError.HolderNameRequired), errors)
    }

    @Test
    fun `postal code with less than eight digits is flagged`() {
        val errors = validateCardForm(validForm().copy(postalCode = "0131010"))
        assertEquals(setOf(CardFormError.PostalCodeInvalid), errors)
    }

    @Test
    fun `blank address number is flagged`() {
        val errors = validateCardForm(validForm().copy(addressNumber = "  "))
        assertEquals(setOf(CardFormError.AddressNumberRequired), errors)
    }

    @Test
    fun `short phone is flagged`() {
        val errors = validateCardForm(validForm().copy(phone = "119999"))
        assertEquals(setOf(CardFormError.PhoneInvalid), errors)
    }

    @Test
    fun `every field wrong reports every error`() {
        val errors = validateCardForm(CardFormState())
        assertEquals(
            setOf(
                CardFormError.NumberInvalid,
                CardFormError.ExpiryInvalid,
                CardFormError.CvvInvalid,
                CardFormError.HolderNameRequired,
                CardFormError.PostalCodeInvalid,
                CardFormError.AddressNumberRequired,
                CardFormError.PhoneInvalid,
            ),
            errors,
        )
    }

    private fun validForm() = CardFormState(
        number = "4111111111111111",
        expiry = "1228",
        cvv = "123",
        holderName = "Ana Silva",
        postalCode = "01310100",
        addressNumber = "1000",
        phone = "11999990000",
    )
}
