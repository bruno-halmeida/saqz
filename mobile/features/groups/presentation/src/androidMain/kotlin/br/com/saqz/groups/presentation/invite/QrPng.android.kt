package br.com.saqz.groups.presentation.invite

import qrcode.QRCode

internal actual fun QRCode.renderToPngBytes(): ByteArray = renderToBytes()
