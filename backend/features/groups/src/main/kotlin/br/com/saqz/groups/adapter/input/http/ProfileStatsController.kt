package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.profile.GetProfileStats
import br.com.saqz.groups.application.profile.ProfileStats
import br.com.saqz.sharedkernel.RequestIdentity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

data class ProfileStatsResponse(
    val games: Int,
    val attendanceRate: Int?,
    val groups: Int,
)

@RestController
class ProfileStatsController(
    private val actorResolver: VerifiedGroupActorResolver,
    private val getProfileStats: GetProfileStats,
) {
    @GetMapping("/api/profile/stats")
    fun get(@AuthenticationPrincipal identity: RequestIdentity): ProfileStatsResponse =
        getProfileStats.execute(actorResolver.resolve(identity)).toResponse()
}

private fun ProfileStats.toResponse() = ProfileStatsResponse(games, attendanceRate, groups)
