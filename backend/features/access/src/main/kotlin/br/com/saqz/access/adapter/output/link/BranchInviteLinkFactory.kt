package br.com.saqz.access.adapter.output.link

import br.com.saqz.access.application.invite.InviteCode
import br.com.saqz.access.application.invite.InviteLinkFactory
import org.springframework.web.util.UriComponentsBuilder
import org.springframework.web.util.UriUtils
import java.net.URI
import java.nio.charset.StandardCharsets

class BranchInviteLinkFactory(private val branchDomain: URI) : InviteLinkFactory {
    init {
        require(branchDomain.scheme.equals("https", ignoreCase = true)) { "Branch domain must use HTTPS" }
        require(!branchDomain.host.isNullOrBlank()) { "Branch domain must have a host" }
        require(branchDomain.port == -1) { "Branch domain must not specify a port" }
        require(branchDomain.userInfo == null) { "Branch domain must not contain user info" }
        require(branchDomain.path.isNullOrEmpty() || branchDomain.path == "/") {
            "Branch domain must not contain a path"
        }
        require(branchDomain.query == null) { "Branch domain must not contain a query" }
        require(branchDomain.fragment == null) { "Branch domain must not contain a fragment" }
    }

    override fun create(code: InviteCode): URI {
        val parameters = linkedMapOf(
            "\$deeplink_path" to "invite/${code.value}",
            "saqz_invite" to code.value,
            "\$ios_nativelink" to "true",
        )
        val encodedQuery = parameters.entries.joinToString("&") { (name, value) ->
            "${encode(name)}=${encode(value)}"
        }
        return UriComponentsBuilder
            .fromUri(branchDomain)
            .replacePath("/")
            .replaceQuery(encodedQuery)
            .build(true)
            .toUri()
    }

    private fun encode(value: String): String = UriUtils.encode(value, StandardCharsets.UTF_8)
}
