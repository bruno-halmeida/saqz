package br.com.saqz.groups.presentation.invite

import qrcode.QRCode
import kotlin.io.encoding.Base64

private const val DATA_URL_PREFIX = "data:"
private const val BASE64_MARKER = ";base64,"

/**
 * No Kotlin/Wasm o qrcode-kotlin desenha num `<canvas>` e o `renderToBytes()` devolve os bytes do
 * texto `data:image/png;base64,...` (o `toDataURL`), não o PNG. A tela, o salvar e o compartilhar
 * esperam PNG: aqui o data URL vira PNG de novo. Se um dia vier PNG puro, passa direto.
 */
internal actual fun QRCode.renderToPngBytes(): ByteArray {
    val raw = renderToBytes()
    val prefix = DATA_URL_PREFIX.encodeToByteArray()
    if (raw.size < prefix.size || !raw.copyOfRange(0, prefix.size).contentEquals(prefix)) return raw
    val text = raw.decodeToString()
    val payload = text.substringAfter(BASE64_MARKER, missingDelimiterValue = "")
    return if (payload.isEmpty()) raw else Base64.decode(payload)
}
