package br.com.saqz.groups.adapter.output.crypto

import br.com.saqz.groups.application.invite.InviteCode
import br.com.saqz.groups.application.invite.InviteToken
import br.com.saqz.groups.application.invite.InviteTokenDigest
import br.com.saqz.groups.application.invite.SecureTokenGenerator
import java.security.SecureRandom
import java.util.Base64

class JcaSecureTokenGenerator(
    private val nextBytes: (ByteArray) -> Unit = SecureRandom()::nextBytes,
) : SecureTokenGenerator {
    override fun generate(): InviteToken {
        val entropy = ByteArray(TOKEN_BYTES)
        nextBytes(entropy)
        val code = InviteCode.from(ENCODER.encodeToString(entropy))
        return InviteToken(code, InviteTokenDigest.sha256(code))
    }

    private companion object {
        const val TOKEN_BYTES = 32
        val ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    }
}
