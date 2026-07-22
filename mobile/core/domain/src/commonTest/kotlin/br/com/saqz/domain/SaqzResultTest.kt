package br.com.saqz.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class SaqzResultTest {
    private data object TestError : SaqzError
    private data object MappedError : SaqzError

    @Test
    fun successPreservesValue() {
        assertEquals("ready", SaqzResult.Success("ready").value)
    }

    @Test
    fun failurePreservesError() {
        assertSame(TestError, SaqzResult.Failure(TestError).error)
    }

    @Test
    fun mapTransformsSuccessValue() {
        assertEquals(SaqzResult.Success(4), SaqzResult.Success("saqz").map(String::length))
    }

    @Test
    fun mapPreservesFailure() {
        val result: SaqzResult<Int, TestError> = SaqzResult.Failure(TestError)
        assertEquals(SaqzResult.Failure(TestError), result.map { it.toString() })
    }

    @Test
    fun mapErrorPreservesSuccess() {
        val result: SaqzResult<String, TestError> = SaqzResult.Success("ready")
        assertEquals(SaqzResult.Success("ready"), result.mapError { MappedError })
    }

    @Test
    fun mapErrorTransformsFailure() {
        val result: SaqzResult<String, TestError> = SaqzResult.Failure(TestError)
        assertEquals(SaqzResult.Failure(MappedError), result.mapError { MappedError })
    }

    @Test
    fun onSuccessRunsActionForSuccess() {
        var observed = ""
        val result = SaqzResult.Success("ready").onSuccess { observed = it }
        assertEquals("ready", observed)
        assertEquals(SaqzResult.Success("ready"), result)
    }

    @Test
    fun onSuccessDoesNotRunActionForFailure() {
        var invoked = false
        val result: SaqzResult<String, TestError> = SaqzResult.Failure(TestError)
        assertEquals(SaqzResult.Failure(TestError), result.onSuccess { invoked = true })
        assertEquals(false, invoked)
    }

    @Test
    fun onFailureDoesNotRunActionForSuccess() {
        var invoked = false
        val result: SaqzResult<String, TestError> = SaqzResult.Success("ready")
        assertEquals(SaqzResult.Success("ready"), result.onFailure { invoked = true })
        assertEquals(false, invoked)
    }

    @Test
    fun onFailureRunsActionForFailure() {
        var observed: SaqzError? = null
        val result: SaqzResult<String, TestError> = SaqzResult.Failure(TestError)
        assertEquals(SaqzResult.Failure(TestError), result.onFailure { observed = it })
        assertSame(TestError, observed)
    }

    @Test
    fun asEmptyResultTransformsSuccessToUnit() {
        val result: SaqzResult<String, TestError> = SaqzResult.Success("ready")
        assertEquals(SaqzResult.Success(Unit), result.asEmptyResult())
    }

    @Test
    fun asEmptyResultPreservesFailure() {
        val result: SaqzResult<String, TestError> = SaqzResult.Failure(TestError)
        assertEquals(SaqzResult.Failure(TestError), result.asEmptyResult())
    }

    @Test
    fun emptyResultRepresentsUnitSuccess() {
        val result: EmptyResult<TestError> = SaqzResult.Success(Unit)
        assertEquals(Unit, assertIs<SaqzResult.Success<Unit>>(result).value)
    }
}
