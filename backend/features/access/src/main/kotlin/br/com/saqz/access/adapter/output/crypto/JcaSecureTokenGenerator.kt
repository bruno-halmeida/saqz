package br.com.saqz.access.adapter.output.crypto

import br.com.saqz.access.application.invite.InviteCode
import br.com.saqz.access.application.invite.InviteToken
import br.com.saqz.access.application.invite.InviteTokenDigest
import br.com.saqz.access.application.invite.SecureTokenGenerator
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

class JcaSecureTokenGenerator(
    private val nextBytes: (ByteArray) -> Unit = SecureRandom()::nextBytes,
) : SecureTokenGenerator {
    override fun generate(): InviteToken {
        val entropy = ByteArray(TOKEN_BYTES)
        nextBytes(entropy)
        val code = InviteCode.from(ENCODER.encodeToString(entropy))
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(code.value.toByteArray(StandardCharsets.US_ASCII))
        return InviteToken(code, InviteTokenDigest.from(digest))
    }

    private companion object {
        const val TOKEN_BYTES = 32
        val ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    }
}
