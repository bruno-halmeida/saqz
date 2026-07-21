package br.com.saqz.groups.adapter.output.link

import br.com.saqz.groups.application.attendance.share.AttendanceLinkCode
import br.com.saqz.groups.application.attendance.share.AttendanceLinkFactory
import org.springframework.web.util.UriComponentsBuilder
import org.springframework.web.util.UriUtils
import java.net.URI
import java.nio.charset.StandardCharsets

class BranchAttendanceLinkFactory(private val branchDomain: URI) : AttendanceLinkFactory {
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

    override fun create(code: AttendanceLinkCode): URI {
        val parameters = linkedMapOf(
            "\$deeplink_path" to "attendance/${code.value}",
            "saqz_attendance" to code.value,
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
