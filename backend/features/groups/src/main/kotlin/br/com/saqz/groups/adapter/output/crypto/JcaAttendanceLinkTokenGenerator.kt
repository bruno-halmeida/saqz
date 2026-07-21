package br.com.saqz.groups.adapter.output.crypto

import br.com.saqz.groups.application.attendance.share.AttendanceLinkCode
import br.com.saqz.groups.application.attendance.share.AttendanceLinkToken
import br.com.saqz.groups.application.attendance.share.AttendanceLinkTokenDigest
import br.com.saqz.groups.application.attendance.share.AttendanceLinkTokenGenerator
import java.security.SecureRandom
import java.util.Base64

class JcaAttendanceLinkTokenGenerator(
    private val nextBytes: (ByteArray) -> Unit = SecureRandom()::nextBytes,
) : AttendanceLinkTokenGenerator {
    override fun generate(): AttendanceLinkToken {
        val entropy = ByteArray(TOKEN_BYTES)
        nextBytes(entropy)
        val code = AttendanceLinkCode.from(ENCODER.encodeToString(entropy))
        return AttendanceLinkToken(code, AttendanceLinkTokenDigest.sha256(code))
    }

    private companion object {
        const val TOKEN_BYTES = 32
        val ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    }
}
