package br.com.saqz.access.adapter.output.media

/** Valor EXIF de foto ja na posicao certa, e o que vale quando nao ha metadado. */
const val UPRIGHT_ORIENTATION: Int = 1

private const val EXIF_MARKER = 0xE1
private const val START_OF_SCAN = 0xDA
private const val END_OF_IMAGE = 0xD9
private const val ORIENTATION_TAG = 0x0112
private const val TIFF_MAGIC = 42
private const val IFD_ENTRY_BYTES = 12

/**
 * Le a orientacao EXIF de um JPEG. O celular grava a foto em retrato como raster
 * deitado mais este metadado; quem redimensiona e recodifica sem aplicar a
 * transformacao entrega um avatar deitado para sempre, porque o metadado original
 * nao sobrevive a recompressao.
 *
 * Vale o mesmo raciocinio do sniff de PNG e WebP no validador do grupo: sao poucos
 * bytes de cabecalho, nao precisam de biblioteca. PNG e WebP caem no valor neutro.
 */
internal fun jpegExifOrientation(bytes: ByteArray): Int {
    if (!bytes.startsWithMarker(0xFF, 0xD8)) return UPRIGHT_ORIENTATION
    var offset = 2
    while (offset + 4 <= bytes.size) {
        if (bytes.byteAt(offset) != 0xFF) return UPRIGHT_ORIENTATION
        val marker = bytes.byteAt(offset + 1)
        if (marker == START_OF_SCAN || marker == END_OF_IMAGE) return UPRIGHT_ORIENTATION
        val length = bytes.bigEndianShort(offset + 2)
        if (length < 2 || offset + 2 + length > bytes.size) return UPRIGHT_ORIENTATION
        val segmentEnd = offset + 2 + length
        if (marker == EXIF_MARKER && length >= 8 && bytes.ascii(offset + 4, 4) == "Exif") {
            return tiffOrientation(bytes, tiffStart = offset + 10, limit = segmentEnd)
        }
        offset = segmentEnd
    }
    return UPRIGHT_ORIENTATION
}

private fun tiffOrientation(bytes: ByteArray, tiffStart: Int, limit: Int): Int {
    if (tiffStart + 8 > limit) return UPRIGHT_ORIENTATION
    val littleEndian = when (bytes.ascii(tiffStart, 2)) {
        "II" -> true
        "MM" -> false
        else -> return UPRIGHT_ORIENTATION
    }
    if (bytes.short(tiffStart + 2, littleEndian) != TIFF_MAGIC) return UPRIGHT_ORIENTATION
    val directory = tiffStart + bytes.int(tiffStart + 4, littleEndian)
    if (directory < tiffStart || directory + 2 > limit) return UPRIGHT_ORIENTATION
    val entries = bytes.short(directory, littleEndian)
    for (index in 0 until entries) {
        val entry = directory + 2 + index * IFD_ENTRY_BYTES
        if (entry + IFD_ENTRY_BYTES > limit) return UPRIGHT_ORIENTATION
        // Orientacao e SHORT de contagem 1, entao o valor mora nos dois primeiros
        // bytes do proprio campo em vez de num deslocamento.
        if (bytes.short(entry, littleEndian) == ORIENTATION_TAG) {
            return bytes.short(entry + 8, littleEndian).takeIf { it in 1..8 } ?: UPRIGHT_ORIENTATION
        }
    }
    return UPRIGHT_ORIENTATION
}

private fun ByteArray.byteAt(offset: Int): Int = this[offset].toInt() and 0xFF

private fun ByteArray.startsWithMarker(vararg expected: Int): Boolean =
    size >= expected.size && expected.indices.all { byteAt(it) == expected[it] }

private fun ByteArray.ascii(offset: Int, length: Int): String =
    if (offset + length > size) "" else copyOfRange(offset, offset + length).toString(Charsets.US_ASCII)

private fun ByteArray.bigEndianShort(offset: Int): Int = (byteAt(offset) shl 8) or byteAt(offset + 1)

private fun ByteArray.short(offset: Int, littleEndian: Boolean): Int =
    if (littleEndian) (byteAt(offset + 1) shl 8) or byteAt(offset) else bigEndianShort(offset)

private fun ByteArray.int(offset: Int, littleEndian: Boolean): Int = if (littleEndian) {
    byteAt(offset) or (byteAt(offset + 1) shl 8) or (byteAt(offset + 2) shl 16) or (byteAt(offset + 3) shl 24)
} else {
    (byteAt(offset) shl 24) or (byteAt(offset + 1) shl 16) or (byteAt(offset + 2) shl 8) or byteAt(offset + 3)
}
