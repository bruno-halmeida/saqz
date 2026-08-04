package br.com.saqz.groups.presentation.navigation

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class FinanceRouteTest {
    @Test
    fun routesRoundTripWithScalarArguments() {
        val routes = listOf(
            FinanceRoute.Overview,
            FinanceRoute.GroupCashbox("group-1"),
            FinanceRoute.Statement("group-2"),
            FinanceRoute.GameSettlement("group-3", "game-1"),
        )

        routes.forEach { route ->
            val encoded = Json.encodeToString(FinanceRoute.serializer(), route)
            assertEquals(route, Json.decodeFromString(FinanceRoute.serializer(), encoded))
        }
    }
}
