package br.com.saqz.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class DomainValuesTest {
    @Test
    fun validationDetailsPreserveOrderedGlobalAndFieldMessages() {
        val details = ValidationDetails(
            globalMessages = listOf("first", "second"),
            fieldMessages = mapOf("email" to listOf("required", "invalid")),
        )

        assertEquals(
            ValidationDetails(
                globalMessages = listOf("first", "second"),
                fieldMessages = mapOf("email" to listOf("required", "invalid")),
            ),
            details,
        )
    }

    @Test
    fun dataErrorFixtureCoversEveryApprovedOutcome() {
        val errors = listOf(
            DataError.Connectivity,
            DataError.Timeout,
            DataError.Unauthenticated,
            DataError.Forbidden,
            DataError.Validation(ValidationDetails(emptyList(), emptyMap())),
            DataError.Conflict,
            DataError.NotFound,
            DataError.InvalidResponse,
            DataError.PayloadTooLarge,
            DataError.Server,
            DataError.Unknown,
        )

        assertEquals(11, errors.distinct().size)
    }

    @Test
    fun groupIdUsesValueEquality() {
        assertEquals(GroupId("group-123"), GroupId("group-123"))
    }
}
