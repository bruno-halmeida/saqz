package br.com.saqz.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class TransportRetryTest {
    private val connectivity = NetworkResult.Failure(NetworkError.Connectivity)
    private val success = NetworkResult.Success("ready")

    @Test fun `persistent eligible failure makes four calls with exact delays`() = runTest {
        val fixture = execute(listOf(connectivity, connectivity, connectivity, connectivity))

        assertEquals(4, fixture.calls)
        assertEquals(listOf(500L, 1_000L, 2_000L), fixture.delays)
    }

    @Test fun `exhaustion returns the exact last failure`() = runTest {
        val final = NetworkResult.Failure(NetworkError.Timeout)
        val fixture = execute(listOf(connectivity, connectivity, connectivity, final))

        assertEquals(final, fixture.result)
    }

    @Test fun `success on first attempt stops without backoff`() = runTest {
        assertStopsAt(successAttempt = 1, expectedDelays = emptyList())
    }

    @Test fun `success on second attempt consumes first backoff only`() = runTest {
        assertStopsAt(successAttempt = 2, expectedDelays = listOf(500L))
    }

    @Test fun `success on third attempt consumes two backoffs`() = runTest {
        assertStopsAt(successAttempt = 3, expectedDelays = listOf(500L, 1_000L))
    }

    @Test fun `success on fourth attempt consumes every backoff`() = runTest {
        assertStopsAt(successAttempt = 4, expectedDelays = listOf(500L, 1_000L, 2_000L))
    }

    @Test fun `connectivity is retryable`() = runTest {
        assertRetried(NetworkError.Connectivity)
    }

    @Test fun `timeout is retryable`() = runTest {
        assertRetried(NetworkError.Timeout)
    }

    @Test fun `structured server failure is retryable`() = runTest {
        assertRetried(NetworkError.ApiProblemError(problem(status = 503)))
    }

    @Test fun `raw server failure is retryable`() = runTest {
        assertRetried(NetworkError.HttpStatus(502))
    }

    @Test fun `unauthenticated failure is not retried`() = runTest {
        assertNotRetried(NetworkError.ApiProblemError(problem(status = 401)))
    }

    @Test fun `forbidden failure is not retried`() = runTest {
        assertNotRetried(NetworkError.ApiProblemError(problem(status = 403)))
    }

    @Test fun `validation failure is not retried`() = runTest {
        assertNotRetried(NetworkError.ApiProblemError(problem(status = 400, code = "VALIDATION_FAILED")))
    }

    @Test fun `conflict failure is not retried`() = runTest {
        assertNotRetried(NetworkError.HttpStatus(409))
    }

    @Test fun `not found failure is not retried`() = runTest {
        assertNotRetried(NetworkError.HttpStatus(404))
    }

    @Test fun `rate limit failure is not retried`() = runTest {
        assertNotRetried(NetworkError.ApiProblemError(problem(status = 429)))
    }

    @Test fun `invalid response is not retried`() = runTest {
        assertNotRetried(NetworkError.InvalidResponse)
    }

    @Test fun `payload too large is not retried`() = runTest {
        assertNotRetried(NetworkError.PayloadTooLarge)
    }

    @Test fun `unknown failure is not retried`() = runTest {
        assertNotRetried(NetworkError.Unknown)
    }

    @Test fun `unavailable token outcome is not retried`() = runTest {
        assertNotRetried(NetworkError.Unavailable)
    }

    @Test fun `non server raw status is not retried`() = runTest {
        assertNotRetried(NetworkError.HttpStatus(413))
    }

    @Test fun `unsafe write selected as retry safety never makes one call`() = runTest {
        val fixture = execute(listOf(connectivity, success), safety = RetrySafety.Never)

        assertEquals(1, fixture.calls)
        assertEquals(emptyList(), fixture.delays)
        assertEquals(connectivity, fixture.result)
    }

    @Test fun `idempotent write is eligible when caller selects it explicitly`() = runTest {
        val fixture = execute(listOf(connectivity, success), safety = RetrySafety.IdempotentWrite)

        assertEquals(2, fixture.calls)
        assertEquals(listOf(500L), fixture.delays)
        assertEquals(success, fixture.result)
    }

    @Test fun `cancellation from call propagates without delay`() = runTest {
        var calls = 0
        val delays = mutableListOf<Long>()

        assertFailsWith<CancellationException> {
            retryTransport<String>(RetrySafety.Read, delayMillis = delays::add) {
                calls += 1
                throw CancellationException("call cancelled")
            }
        }
        assertEquals(1, calls)
        assertEquals(emptyList(), delays)
    }

    @Test fun `cancellation from first backoff propagates before second call`() = runTest {
        assertBackoffCancellation(backoffIndex = 0, expectedCalls = 1)
    }

    @Test fun `cancellation from second backoff propagates before third call`() = runTest {
        assertBackoffCancellation(backoffIndex = 1, expectedCalls = 2)
    }

    @Test fun `cancellation from third backoff propagates before fourth call`() = runTest {
        assertBackoffCancellation(backoffIndex = 2, expectedCalls = 3)
    }

    private suspend fun assertStopsAt(successAttempt: Int, expectedDelays: List<Long>) {
        val outcomes = List(successAttempt - 1) { connectivity } + success
        val fixture = execute(outcomes)

        assertEquals(successAttempt, fixture.calls)
        assertEquals(expectedDelays, fixture.delays)
        assertEquals(success, fixture.result)
    }

    private suspend fun assertRetried(error: NetworkError) {
        val fixture = execute(listOf(NetworkResult.Failure(error), success))

        assertEquals(2, fixture.calls)
        assertEquals(listOf(500L), fixture.delays)
        assertEquals(success, fixture.result)
    }

    private suspend fun assertNotRetried(error: NetworkError) {
        val failure = NetworkResult.Failure(error)
        val fixture = execute(listOf(failure, success))

        assertEquals(1, fixture.calls)
        assertEquals(emptyList(), fixture.delays)
        assertEquals(failure, fixture.result)
    }

    private suspend fun assertBackoffCancellation(backoffIndex: Int, expectedCalls: Int) {
        var calls = 0
        var delays = 0

        assertFailsWith<CancellationException> {
            retryTransport(RetrySafety.Read, delayMillis = {
                if (delays == backoffIndex) throw CancellationException("backoff cancelled")
                delays += 1
            }) {
                calls += 1
                connectivity
            }
        }
        assertEquals(expectedCalls, calls)
        assertEquals(backoffIndex, delays)
    }

    private suspend fun execute(
        outcomes: List<NetworkResult<String>>,
        safety: RetrySafety = RetrySafety.Read,
    ): Fixture {
        var calls = 0
        val delays = mutableListOf<Long>()
        val result = retryTransport(safety, delayMillis = delays::add) {
            outcomes.getOrElse(calls++) { error("unexpected call") }
        }
        return Fixture(calls, delays, result)
    }

    private fun problem(status: Int, code: String? = null) = ApiProblem(
        status = status,
        code = code,
        correlationId = "safe-fixture",
    )

    private data class Fixture(
        val calls: Int,
        val delays: List<Long>,
        val result: NetworkResult<String>,
    )
}
