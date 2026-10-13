package br.com.saqz.groups.adapter.output.crypto

import br.com.saqz.groups.application.invite.InviteCode
import br.com.saqz.groups.application.invite.InviteToken
import br.com.saqz.groups.application.invite.InviteTokenDigest
import br.com.saqz.groups.application.invite.SecureTokenGenerator
import java.security.SecureRandom

/** Gera o código curto de convite (8 caracteres de um alfabeto sem ambiguidade) com SecureRandom. */
class JcaSecureTokenGenerator(
    private val nextBytes: (ByteArray) -> Unit = SecureRandom()::nextBytes,
) : SecureTokenGenerator {
    override fun generate(): InviteToken {
        val alphabet = InviteCode.SHORT_ALPHABET
        val code = StringBuilder(InviteCode.SHORT_LENGTH)
        val entropy = ByteArray(BUFFER_BYTES)
        while (code.length < InviteCode.SHORT_LENGTH) {
            nextBytes(entropy)
            for (byte in entropy) {
                // 5 bits por caractere; 31 símbolos, então 31 é descartado para não enviesar o sorteio.
                val index = byte.toInt() and 0x1F
                if (index < alphabet.length) code.append(alphabet[index])
                if (code.length == InviteCode.SHORT_LENGTH) break
            }
        }
        val invite = InviteCode.from(code.toString())
        return InviteToken(invite, InviteTokenDigest.sha256(invite))
    }

    private companion object {
        const val BUFFER_BYTES = 16
    }
}
