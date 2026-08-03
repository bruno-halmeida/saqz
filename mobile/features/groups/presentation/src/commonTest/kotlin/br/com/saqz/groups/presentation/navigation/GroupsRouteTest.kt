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
            GroupsRoute.Invite("group-5"),
            GroupsRoute.InviteMessagePreview("Vôlei do CERET", "https://saqz.app/invite/code"),
            GroupsRoute.InviteQr("Vôlei do CERET", "https://saqz.app/invite/code"),
            GroupsRoute.InviteLanding("invite-code"),
            GroupsRoute.InviteLanding("invite-pending", requestSent = true),
            GroupsRoute.InviteLanding(
                "invite-plan-limit",
                redeemError = InviteLandingRouteError.PlanLimit,
            ),
            GroupsRoute.AthleteRegistration("group-6"),
            GroupsRoute.MemberEditor("group-7", "user-8"),
            GroupsRoute.GameEditor("group-9"),
            GroupsRoute.GameEditor("group-9", "game-1"),
            GroupsRoute.GameDetail("group-9", "game-1"),
        )

        routes.forEach { route ->
            val encoded = Json.encodeToString(GroupsRoute.serializer(), route)
            assertEquals(route, Json.decodeFromString(GroupsRoute.serializer(), encoded))
        }
    }

    @Test
    fun inviteLandingPreservesRedeemErrorAcrossRecreation() {
        val route = GroupsRoute.InviteLanding(
            code = "invite-recreated",
            redeemError = InviteLandingRouteError.RateLimited(23),
        )

        val restored = Json.decodeFromString<GroupsRoute>(
            Json.encodeToString(GroupsRoute.serializer(), route),
        )

        assertEquals(route, restored)
        assertEquals(InviteLandingRouteError.RateLimited(23), (restored as GroupsRoute.InviteLanding).redeemError)
    }
}
