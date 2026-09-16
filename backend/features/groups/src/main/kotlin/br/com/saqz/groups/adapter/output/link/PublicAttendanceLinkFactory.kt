package br.com.saqz.groups.adapter.output.link

import br.com.saqz.groups.application.attendance.share.AttendanceLinkCode
import br.com.saqz.groups.application.attendance.share.AttendanceLinkFactory
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

class PublicAttendanceLinkFactory(private val linksDomain: URI) : AttendanceLinkFactory {
    init {
        require(linksDomain.scheme.equals("https", ignoreCase = true)) { "Links domain must use HTTPS" }
        require(!linksDomain.host.isNullOrBlank()) { "Links domain must have a host" }
        require(linksDomain.port == -1) { "Links domain must not specify a port" }
        require(linksDomain.userInfo == null) { "Links domain must not contain user info" }
        require(linksDomain.path.isNullOrEmpty() || linksDomain.path == "/") {
            "Links domain must not contain a path"
        }
        require(linksDomain.query == null) { "Links domain must not contain a query" }
        require(linksDomain.fragment == null) { "Links domain must not contain a fragment" }
    }

    override fun create(code: AttendanceLinkCode): URI =
        UriComponentsBuilder
            .fromUri(linksDomain)
            .pathSegment("attendance", code.value)
            .build()
            .toUri()
}
