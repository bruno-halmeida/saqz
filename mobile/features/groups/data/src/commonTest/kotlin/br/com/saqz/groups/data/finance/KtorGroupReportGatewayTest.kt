package br.com.saqz.groups.data.finance

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.finance.FinanceError
import br.com.saqz.groups.domain.finance.ReportExportKind
import br.com.saqz.groups.domain.finance.ReportPeriod
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.IdTokenProvider
import br.com.saqz.network.NetworkClient
import br.com.saqz.network.NetworkConfig
import br.com.saqz.network.NetworkEnvironment
import br.com.saqz.network.SessionInvalidator
import br.com.saqz.network.TokenResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KtorGroupReportGatewayTest {
    private val group = GroupId("group-1")

    @Test
    fun `availability reads the flag from the backend`() = runTest {
        val gateway = gateway { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/groups/group-1/reports/availability", request.url.encodedPath)
            json("""{"enabled":true}""")
        }

        assertEquals(true, assertIs<SaqzResult.Success<Boolean>>(gateway.availability(group)).value)
    }

    @Test
    fun `report sends the period and maps attendance and finance`() = runTest {
        val gateway = gateway { request ->
            assertEquals("/api/groups/group-1/reports", request.url.encodedPath)
            assertEquals("PREVIOUS_MONTH", request.url.parameters["period"])
            json(REPORT)
        }

        val report = assertIs<SaqzResult.Success<br.com.saqz.groups.domain.finance.GroupReport>>(
            gateway.report(group, ReportPeriod.PreviousMonth),
        ).value

        assertEquals(ReportPeriod.PreviousMonth, report.period)
        assertEquals("2026-09-01", report.from)
        assertEquals(4, report.attendance.games)
        assertEquals(8.5, report.attendance.averageConfirmed)
        assertEquals(80, report.attendance.confirmationRatePercent)
        assertEquals("Ana", report.attendance.mostPresent.single().name)
        assertEquals(7, report.attendance.mostPresent.single().count)
        assertEquals("Edu", report.attendance.neverResponded.single().name)
        assertEquals(7_500L, report.finance.inCents)
        assertEquals(4_500L, report.finance.balanceCents)
        assertEquals(2, report.finance.overdueMonthly.single().months)
        assertEquals(6_000L, report.finance.overdueMonthly.single().amountCents)
    }

    @Test
    fun `export sends kind and period and returns file name and csv`() = runTest {
        val gateway = gateway { request ->
            assertEquals("/api/groups/group-1/reports/export", request.url.encodedPath)
            assertEquals("LEDGER", request.url.parameters["kind"])
            assertEquals("YEAR", request.url.parameters["period"])
            json("""{"fileName":"saqz-lancamentos.csv","csv":"a;b\r\n"}""")
        }

        val export = assertIs<SaqzResult.Success<br.com.saqz.groups.domain.finance.ReportExport>>(
            gateway.export(group, ReportPeriod.Year, ReportExportKind.Ledger),
        ).value

        assertEquals("saqz-lancamentos.csv", export.fileName)
        assertEquals("a;b\r\n", export.csv)
    }

    @Test
    fun `plan without reports surfaces as subscription required`() = runTest {
        val gateway = gateway {
            respond(
                """{"status":403,"code":"SUBSCRIPTION_REQUIRED","correlationId":"safe"}""",
                HttpStatusCode.Forbidden,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val failure = assertIs<SaqzResult.Failure<FinanceError>>(gateway.report(group, ReportPeriod.CurrentMonth))

        assertEquals(FinanceError.Data(DataError.SubscriptionRequired), failure.error)
    }

    private fun gateway(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        KtorGroupReportGateway(
            AuthenticatedNetworkClient(
                NetworkClient(
                    MockEngine { request -> handler(request) },
                    NetworkConfig(NetworkEnvironment.Test, "https://api.example.test/"),
                ),
                object : IdTokenProvider {
                    override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) =
                        completion(TokenResult.Available("obviously-fake-token"))
                },
                object : SessionInvalidator {
                    override fun invalidate() = Unit
                },
            ),
            retryDelay = {},
        )

    private fun MockRequestHandleScope.json(body: String) =
        respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))

    private companion object {
        const val REPORT = """{
            "period":"PREVIOUS_MONTH","from":"2026-09-01","toExclusive":"2026-10-01",
            "attendance":{"games":4,"averageConfirmed":8.5,"averageCapacity":11.0,"confirmationRatePercent":80,
              "mostPresent":[{"userId":"u1","name":"Ana","count":7}],
              "mostDeclined":[],
              "neverResponded":[{"userId":"u2","name":"Edu","count":4}]},
            "finance":{"inCents":7500,"outCents":3000,"balanceCents":4500,"receivableCents":4000,
              "overdueMonthly":[{"userId":"u3","name":"Marina","amountCents":6000,"months":2}]}
        }"""
    }
}
