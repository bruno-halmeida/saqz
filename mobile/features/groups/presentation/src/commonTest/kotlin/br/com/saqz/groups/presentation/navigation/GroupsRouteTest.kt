package br.com.saqz.groups.presentation.navigation

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class GroupsRouteTest {
    @Test
    fun routesRoundTripWithScalarArguments() {
        val routes = listOf(
            GroupsRoute.List,
            GroupsRoute.Create,
            GroupsRoute.Details("group-1"),
            GroupsRoute.Edit("group-2"),
            GroupsRoute.Members("group-3"),
            GroupsRoute.Schedule("group-4"),
        )

        routes.forEach { route ->
            val encoded = Json.encodeToString(GroupsRoute.serializer(), route)
            assertEquals(route, Json.decodeFromString(GroupsRoute.serializer(), encoded))
        }
    }
}
