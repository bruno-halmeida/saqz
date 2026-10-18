import { deflateSync } from 'node:zlib'

const crcTabela = Array.from({ length: 256 }, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0 })
const crc32 = (buf) => { let c = 0xffffffff; for (const b of buf) c = crcTabela[(c ^ b) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0 }
const bloco = (tipo, dados) => {
  const t = Buffer.from(tipo); const len = Buffer.alloc(4); len.writeUInt32BE(dados.length)
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(Buffer.concat([t, dados])))
  return Buffer.concat([len, t, dados, crc])
}

/** PNG RGB de cor lisa (pequeno em disco mesmo com muitos pixels): serve para testar o redimensionamento da foto. */
export function png(largura, altura, [r, g, b] = [200, 60, 60]) {
  const linha = Buffer.alloc(1 + largura * 3)
  for (let x = 0; x < largura; x++) { linha[1 + x * 3] = r; linha[2 + x * 3] = g; linha[3 + x * 3] = b }
  const cru = Buffer.concat(Array.from({ length: altura }, () => linha))
  const cab = Buffer.alloc(13); cab.writeUInt32BE(largura, 0); cab.writeUInt32BE(altura, 4); cab[8] = 8; cab[9] = 2
  return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), bloco('IHDR', cab), bloco('IDAT', deflateSync(cru)), bloco('IEND', Buffer.alloc(0))])
}

/** Largura e altura de um JPEG (marcador SOF). */
export function dimensoesJpeg(bytes) {
  let i = 2
  while (i < bytes.length) {
    if (bytes[i] !== 0xff) { i++; continue }
    const marcador = bytes[i + 1]
    if (marcador >= 0xc0 && marcador <= 0xcf && ![0xc4, 0xc8, 0xcc].includes(marcador)) return { altura: bytes.readUInt16BE(i + 5), largura: bytes.readUInt16BE(i + 7) }
    i += 2 + bytes.readUInt16BE(i + 2)
  }
  return null
}
