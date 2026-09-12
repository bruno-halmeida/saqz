package br.com.saqz.bootstrap.configuration

import br.com.saqz.access.application.session.AppOnboardingCode
import org.springframework.web.util.UriComponentsBuilder
import org.springframework.web.util.UriUtils
import java.net.URI
import java.nio.charset.StandardCharsets

class AppOnboardingLinkFactory(private val branchDomain: URI) {
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

    fun create(code: AppOnboardingCode): URI {
        val parameters = linkedMapOf(
            "\$deeplink_path" to "onboarding",
            "saqz_onboarding" to code.value,
            "\$ios_nativelink" to "true",
        )
        val query = parameters.entries.joinToString("&") { (name, value) ->
            "${encode(name)}=${encode(value)}"
        }
        return UriComponentsBuilder.fromUri(branchDomain)
            .replacePath("/")
            .replaceQuery(query)
            .build(true)
            .toUri()
    }

    private fun encode(value: String): String = UriUtils.encode(value, StandardCharsets.UTF_8)
}
