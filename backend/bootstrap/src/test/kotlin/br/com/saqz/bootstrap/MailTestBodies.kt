package br.com.saqz.bootstrap

import jakarta.mail.Multipart
import jakarta.mail.Part

/** Jakarta Mail decodes Content-Transfer-Encoding and charset before exposing content. */
internal fun Part.plainTextBody(): String? = when {
    isMimeType("text/plain") -> content as? String
    isMimeType("multipart/*") -> (content as Multipart).let { parts ->
        (0 until parts.count).firstNotNullOfOrNull { parts.getBodyPart(it).plainTextBody() }
    }
    else -> null
}
