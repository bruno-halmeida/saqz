package br.com.saqz.groups.presentation.navigation

import kotlinx.serialization.json.Json
import br.com.saqz.groups.presentation.newentry.NewEntryPrefill
import kotlin.test.Test
import kotlin.test.assertEquals

class FinanceRouteTest {
    @Test
    fun routesRoundTripWithScalarArguments() {
        val routes = listOf(
            FinanceRoute.GroupCashbox("group-1"),
            FinanceRoute.Statement("group-2"),
            FinanceRoute.NewEntry("group-2"),
            FinanceRoute.NewEntry("group-2", NewEntryPrefill.GameCourt("2026-08-12")),
            FinanceRoute.GameSettlement("group-3", "game-1"),
        )

        routes.forEach { route ->
            val encoded = Json.encodeToString(FinanceRoute.serializer(), route)
            assertEquals(route, Json.decodeFromString(FinanceRoute.serializer(), encoded))
        }
    }
}
