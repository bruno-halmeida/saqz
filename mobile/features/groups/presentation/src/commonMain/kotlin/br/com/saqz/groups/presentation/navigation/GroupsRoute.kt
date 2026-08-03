package br.com.saqz.groups.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface GroupsRoute : NavKey {
    @Serializable
    data object List : GroupsRoute

    @Serializable
    data object Create : GroupsRoute

    @Serializable
    data class Details(val groupId: String) : GroupsRoute

    @Serializable
    data class Edit(val groupId: String) : GroupsRoute

    @Serializable
    data class Members(val groupId: String) : GroupsRoute

    @Serializable
    data class Schedule(val groupId: String) : GroupsRoute

    /** 3a, with 3b/3c stacked as child destinations. */
    @Serializable
    data class Invite(val groupId: String) : GroupsRoute

    /** 3b: WhatsApp/message preview. */
    @Serializable
    data class InviteMessagePreview(val groupName: String, val inviteUrl: String) : GroupsRoute

    /** 3c: QR preview. */
    @Serializable
    data class InviteQr(val groupName: String, val inviteUrl: String) : GroupsRoute

    /** 3d/3e/3f/3l: the landing ViewModel owns the visible state. */
    @Serializable
    data class InviteLanding(
        val code: String,
        val requestSent: Boolean = false,
        val redeemError: InviteLandingRouteError? = null,
    ) : GroupsRoute

    /** 3j/3k: first athlete profile after a successful invite redemption. */
    @Serializable
    data class AthleteRegistration(val groupId: String) : GroupsRoute

    /** 3g: member editor. */
    @Serializable
    data class MemberEditor(val groupId: String, val userId: String) : GroupsRoute
}

@Serializable
sealed interface InviteLandingRouteError {
    @Serializable
    data object Invalid : InviteLandingRouteError

    @Serializable
    data class Expired(val expiredAt: String) : InviteLandingRouteError

    @Serializable
    data class RateLimited(val retryAfterSeconds: Int?) : InviteLandingRouteError

    @Serializable
    data object PlanLimit : InviteLandingRouteError

    @Serializable
    data object Network : InviteLandingRouteError
}
