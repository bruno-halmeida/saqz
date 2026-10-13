@file:OptIn(ExperimentalWasmJsInterop::class)

package br.com.saqz.webapp.interop

import kotlin.coroutines.resume
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Escolha e preparo de imagens no navegador, o equivalente web do que o Android faz com arquivos
 * em `cacheDir` e o iOS com ImageIO.
 *
 * O arquivo escolhido fica do lado JS (um `Blob` em memória, por id) e só atravessa a fronteira
 * como texto: o preview reduzido na seleção e o JPEG recortado no encode, ambos em base64. Copiar
 * byte a byte pela fronteira Wasm/JS seria muito mais lento do que decodificar uma string.
 */
internal sealed interface PickedImage {
    class Selected(val id: String, val width: Int, val height: Int, val preview: ByteArray) : PickedImage
    data object Cancelled : PickedImage
    data object Failed : PickedImage
}

// Maior arquivo aceito. O Android limita a origem a 5 MB porque a copia para o cache; aqui o
// arquivo é decodificado e reencodado, então o teto só protege a memória da aba.
private const val MAX_SOURCE_BYTES = 32.0 * 1024 * 1024

// Lado maior da imagem de trabalho: o mesmo teto do iOS (kCGImageSourceThumbnailMaxPixelSize) e
// do Android (MAX_DIMENSION), que ali rejeitam o que passa disso. No navegador reduzimos, porque
// qualquer foto de 48 MP de celular passa desse limite.
private const val MAX_DIMENSION = 4096

// Lado maior do preview que a tela decodifica com o Skia: bem acima do thumb de 288 px e bem
// abaixo dos 12 MP do original.
private const val PREVIEW_SIDE = 1600
private const val PREVIEW_QUALITY = 0.85

private val runtime: JsAny by lazy {
    createImageRuntime(MAX_SOURCE_BYTES, MAX_DIMENSION, PREVIEW_SIDE, PREVIEW_QUALITY)
}

/**
 * Abre o seletor de arquivos do navegador. `camera` liga `capture="environment"`: no celular abre
 * a câmera traseira, no desktop o seletor normal. Cancelar a coroutine fecha o seletor e solta o
 * que já tiver sido preparado.
 */
internal suspend fun pickImage(camera: Boolean): PickedImage {
    var delivered: PickedImage.Selected? = null
    try {
        return suspendCancellableCoroutine<PickedImage> { continuation ->
            val abort = jsPickImage(runtime, camera) { json ->
                val result = parsePicked(json)
                if (result is PickedImage.Selected) delivered = result
                if (continuation.isActive) continuation.resume(result) else releaseImage(delivered)
            }
            continuation.invokeOnCancellation { jsInvoke(abort) }
        }
    } catch (cancelled: CancellationException) {
        // O resultado chegou, mas a coroutine foi cancelada antes de retomar: ninguém mais conhece o id.
        releaseImage(delivered)
        throw cancelled
    }
}

/**
 * Recorta o quadrado (em pixels da imagem já orientada) e codifica em JPEG, tentando as
 * qualidades em ordem até caber em `maxBytes`. Devolve nulo se nada coube ou se a decodificação falhar.
 */
internal suspend fun encodeImageJpeg(
    id: String,
    left: Int,
    top: Int,
    side: Int,
    qualities: List<Int>,
    maxBytes: Long,
): ByteArray? = suspendCancellableCoroutine { continuation ->
    val abort = jsEncodeImage(runtime, id, left, top, side, qualities.joinToString(","), maxBytes.toDouble()) { base64 ->
        if (continuation.isActive) continuation.resume(decodeBase64(base64))
    }
    continuation.invokeOnCancellation { jsInvoke(abort) }
}

/** Solta o arquivo guardado do lado JS. Idempotente: id desconhecido é ignorado. */
internal fun releaseImage(id: String) = jsReleaseImage(runtime, id)

private fun releaseImage(selected: PickedImage.Selected?) {
    if (selected != null) releaseImage(selected.id)
}

private fun parsePicked(json: String): PickedImage {
    val fields = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return PickedImage.Failed
    return when (fields.text("status")) {
        "cancelled" -> PickedImage.Cancelled
        "selected" -> parseSelected(fields) ?: PickedImage.Failed
        else -> PickedImage.Failed
    }
}

private fun parseSelected(fields: JsonObject): PickedImage.Selected? {
    val id = fields.text("id") ?: return null
    val selected = runCatching {
        PickedImage.Selected(
            id = id,
            width = fields.getValue("width").jsonPrimitive.int,
            height = fields.getValue("height").jsonPrimitive.int,
            preview = Base64.decode(fields.getValue("preview").jsonPrimitive.content),
        )
    }.getOrNull()
    // Preview ilegível: o arquivo guardado do lado JS não serve a ninguém.
    if (selected == null || selected.preview.isEmpty()) {
        releaseImage(id)
        return null
    }
    return selected
}

private fun JsonObject.text(name: String): String? = get(name)?.jsonPrimitive?.content

private fun decodeBase64(base64: String): ByteArray? =
    runCatching { Base64.decode(base64) }.getOrNull()?.takeIf { it.isNotEmpty() }

// O runtime é criado uma vez por aba. Cada função devolve o "abortar" do pedido, que a coroutine
// chama ao ser cancelada. Os resultados voltam como texto (JSON no pick, base64 no encode).
private const val IMAGE_RUNTIME_JS = """(maxSourceBytes, maxDimension, previewSide, previewQuality) => {
  const FOCUS_IGNORE_MS = 500;
  const FOCUS_CANCEL_MS = 1500;
  const sources = new Map();
  let cancelEventSeen = false;

  const newId = () => 'photo-' + (crypto.randomUUID
    ? crypto.randomUUID()
    : Date.now().toString(36) + Math.random().toString(36).slice(2));

  const draw = (source, sx, sy, sw, sh, dw, dh) => {
    const canvas = document.createElement('canvas');
    canvas.width = dw;
    canvas.height = dh;
    const context = canvas.getContext('2d');
    if (!context) throw new Error('canvas indisponivel');
    // JPEG nao tem alfa: PNG e WebP transparentes ficariam pretos sem o fundo branco.
    context.fillStyle = '#ffffff';
    context.fillRect(0, 0, dw, dh);
    context.imageSmoothingEnabled = true;
    context.imageSmoothingQuality = 'high';
    context.drawImage(source, sx, sy, sw, sh, 0, 0, dw, dh);
    return canvas;
  };

  // O Safari acumula a memoria de canvas descartados se eles nao forem zerados.
  const dropCanvas = (canvas) => { canvas.width = 0; canvas.height = 0; };

  const toJpeg = (canvas, quality) => new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', quality));

  const toBase64 = (blob) => new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const url = String(reader.result);
      resolve(url.slice(url.indexOf(',') + 1));
    };
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(blob);
  });

  // Decodifica com a orientacao EXIF aplicada e limita o lado maior, devolvendo algo que o
  // drawImage aceita como origem.
  const decode = async (blob) => {
    let bitmap;
    try {
      bitmap = await createImageBitmap(blob, { imageOrientation: 'from-image' });
    } catch (error) {
      // Navegador sem o valor "from-image": nele o padrao ja respeita o EXIF.
      if (!(error instanceof TypeError)) throw error;
      bitmap = await createImageBitmap(blob);
    }
    const longest = Math.max(bitmap.width, bitmap.height);
    if (longest <= maxDimension) {
      return { source: bitmap, width: bitmap.width, height: bitmap.height, dispose: () => bitmap.close() };
    }
    const scale = maxDimension / longest;
    const width = Math.max(1, Math.round(bitmap.width * scale));
    const height = Math.max(1, Math.round(bitmap.height * scale));
    const canvas = draw(bitmap, 0, 0, bitmap.width, bitmap.height, width, height);
    bitmap.close();
    return { source: canvas, width, height, dispose: () => dropCanvas(canvas) };
  };

  const prepare = async (file) => {
    if (!file || file.size <= 0 || file.size > maxSourceBytes) return { status: 'failed' };
    // Copia o arquivo para a memoria: um File do seletor pode deixar de ser legivel depois
    // (arquivo editado ou apagado na galeria), e o encode so acontece no envio.
    const blob = new Blob([await file.arrayBuffer()], { type: file.type });
    const image = await decode(blob);
    try {
      const scale = Math.min(1, previewSide / Math.max(image.width, image.height));
      const width = Math.max(1, Math.round(image.width * scale));
      const height = Math.max(1, Math.round(image.height * scale));
      const canvas = draw(image.source, 0, 0, image.width, image.height, width, height);
      const preview = await toJpeg(canvas, previewQuality);
      dropCanvas(canvas);
      if (!preview || preview.type !== 'image/jpeg' || preview.size === 0) return { status: 'failed' };
      return { status: 'selected', blob, width: image.width, height: image.height, preview: await toBase64(preview) };
    } finally {
      image.dispose();
    }
  };

  const pick = (capture, done) => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = 'image/*';
    if (capture) input.setAttribute('capture', 'environment');
    input.style.cssText = 'position:fixed;left:-9999px;top:0;width:1px;height:1px;opacity:0;';
    const openedAt = Date.now();
    let finished = false;
    let preparing = false;
    let timer = 0;

    const detach = () => {
      clearTimeout(timer);
      window.removeEventListener('focus', onFocus);
      input.removeEventListener('change', onChange);
      input.removeEventListener('cancel', onCancel);
      input.remove();
    };
    const finish = (result) => {
      if (finished) return;
      finished = true;
      detach();
      done(JSON.stringify(result));
    };
    const abort = () => {
      if (finished) return;
      finished = true;
      detach();
    };

    const onCancel = () => {
      cancelEventSeen = true;
      if (!preparing) finish({ status: 'cancelled' });
    };
    // Reserva para navegador sem o evento "cancel": a janela volta a ter foco e, passado um tempo,
    // continua sem arquivo. Ignora o foco logo apos o clique e vale so enquanto nenhum "cancel" foi visto.
    const onFocus = () => {
      if (cancelEventSeen || preparing || Date.now() - openedAt < FOCUS_IGNORE_MS) return;
      clearTimeout(timer);
      timer = setTimeout(() => {
        if (!preparing && !(input.files && input.files.length)) finish({ status: 'cancelled' });
      }, FOCUS_CANCEL_MS);
    };
    const onChange = async () => {
      const file = input.files && input.files[0];
      if (!file) { finish({ status: 'cancelled' }); return; }
      preparing = true;
      let result;
      try {
        result = await prepare(file);
      } catch (error) {
        result = { status: 'failed' };
      }
      if (finished) return;
      if (result.status !== 'selected') { finish(result); return; }
      const id = newId();
      sources.set(id, result.blob);
      finish({ status: 'selected', id, width: result.width, height: result.height, preview: result.preview });
    };

    window.addEventListener('focus', onFocus);
    input.addEventListener('change', onChange);
    input.addEventListener('cancel', onCancel);
    document.body.appendChild(input);
    // Sem ativacao do usuario o navegador nao abre o seletor e nenhum evento chega: falha na hora
    // em vez de deixar a tela esperando para sempre.
    const activation = navigator.userActivation;
    if (activation && !activation.isActive) {
      finish({ status: 'failed' });
      return abort;
    }
    try {
      input.click();
    } catch (error) {
      finish({ status: 'failed' });
    }
    return abort;
  };

  const encode = (id, left, top, side, qualities, maxBytes, done) => {
    const blob = sources.get(id);
    if (!blob) { done(''); return () => {}; }
    let aborted = false;
    const run = async () => {
      const image = await decode(blob);
      try {
        if (left < 0 || top < 0 || side < 1 || left + side > image.width || top + side > image.height) return '';
        const canvas = draw(image.source, left, top, side, side, side, side);
        try {
          for (const quality of qualities.split(',')) {
            const jpeg = await toJpeg(canvas, Number(quality) / 100);
            if (jpeg && jpeg.type === 'image/jpeg' && jpeg.size > 0 && jpeg.size <= maxBytes) return await toBase64(jpeg);
          }
          return '';
        } finally {
          dropCanvas(canvas);
        }
      } finally {
        image.dispose();
      }
    };
    run().catch(() => '').then((result) => { if (!aborted) done(result); });
    return () => { aborted = true; };
  };

  return { pick, encode, release: (id) => { sources.delete(id); } };
}"""

@JsFun(IMAGE_RUNTIME_JS)
private external fun createImageRuntime(
    maxSourceBytes: Double,
    maxDimension: Int,
    previewSide: Int,
    previewQuality: Double,
): JsAny

@JsFun("(runtime, capture, done) => runtime.pick(capture, done)")
private external fun jsPickImage(runtime: JsAny, capture: Boolean, done: (String) -> Unit): JsAny

// A assinatura espelha a função JS do runtime, um parâmetro por argumento.
@Suppress("LongParameterList")
@JsFun("(runtime, id, left, top, side, qualities, maxBytes, done) => runtime.encode(id, left, top, side, qualities, maxBytes, done)")
private external fun jsEncodeImage(
    runtime: JsAny,
    id: String,
    left: Int,
    top: Int,
    side: Int,
    qualities: String,
    maxBytes: Double,
    done: (String) -> Unit,
): JsAny

@JsFun("(runtime, id) => runtime.release(id)")
private external fun jsReleaseImage(runtime: JsAny, id: String)

@JsFun("(action) => action()")
private external fun jsInvoke(action: JsAny)
