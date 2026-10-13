@file:OptIn(ExperimentalWasmJsInterop::class)

package br.com.saqz.webapp.interop

import kotlin.io.encoding.Base64
import kotlin.js.ExperimentalWasmJsInterop

/** Como terminou uma operação do navegador (compartilhar, copiar, baixar). */
internal enum class BrowserOutcome { DONE, CANCELLED, FAILED }

// As funções abaixo respondem por callback com "ok" | "cancelled" | "failed" (só String cruza a
// fronteira sem custo) e nunca lançam: toda exceção do navegador vira "failed". São chamadas de forma
// síncrona a partir do toque, para não perder a ativação do usuário que share/clipboard/click exigem.

@JsFun("() => typeof navigator !== 'undefined' && typeof navigator.share === 'function'")
private external fun jsCanShare(): Boolean

// AbortError é a pessoa fechando a folha de compartilhamento; o resto (NotAllowedError, etc.) é falha.
@JsFun(
    """(text, done) => {
  try {
    navigator.share({ text }).then(
      () => done('ok'),
      (error) => done(error && error.name === 'AbortError' ? 'cancelled' : 'failed'),
    );
  } catch (error) {
    done('failed');
  }
}""",
)
private external fun jsShareText(text: String, done: (String) -> Unit)

@JsFun(
    """(text, done) => {
  const clipboard = typeof navigator !== 'undefined' ? navigator.clipboard : undefined;
  if (!clipboard || typeof clipboard.writeText !== 'function') {
    done('failed');
    return;
  }
  try {
    clipboard.writeText(text).then(() => done('ok'), () => done('failed'));
  } catch (error) {
    done('failed');
  }
}""",
)
private external fun jsCopyText(text: String, done: (String) -> Unit)

// Compartilha o arquivo quando o navegador aceita (canShare com files); senão, ou com `download`,
// baixa por <a download>. O File nasce síncrono para a chamada ao share ainda estar na janela de
// ativação do toque.
@JsFun(
    """(base64, fileName, mimeType, download, done) => {
  let bytes;
  try {
    const binary = atob(base64);
    bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  } catch (error) {
    done('failed');
    return;
  }
  if (!download && typeof File === 'function' && typeof navigator !== 'undefined'
      && typeof navigator.share === 'function' && typeof navigator.canShare === 'function') {
    try {
      const data = { files: [new File([bytes], fileName, { type: mimeType })] };
      if (navigator.canShare(data)) {
        navigator.share(data).then(
          () => done('ok'),
          (error) => done(error && error.name === 'AbortError' ? 'cancelled' : 'failed'),
        );
        return;
      }
    } catch (error) {
      done('failed');
      return;
    }
  }
  let result = 'ok';
  try {
    const url = URL.createObjectURL(new Blob([bytes], { type: mimeType }));
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    link.style.display = 'none';
    document.body.appendChild(link);
    link.click();
    link.remove();
    const timer = setTimeout(() => URL.revokeObjectURL(url), 60000);
    if (typeof timer === 'object' && timer.unref) timer.unref();
  } catch (error) {
    result = 'failed';
  }
  done(result);
}""",
)
private external fun jsShareFile(base64: String, fileName: String, mimeType: String, download: Boolean, done: (String) -> Unit)

private fun outcomeOf(code: String) = when (code) {
    "ok" -> BrowserOutcome.DONE
    "cancelled" -> BrowserOutcome.CANCELLED
    else -> BrowserOutcome.FAILED
}

/** Fronteira Kotlin das APIs de compartilhamento do navegador. */
internal object BrowserShare {
    /** Só existe `navigator.share` em contexto seguro e em parte dos navegadores (ausente no Firefox desktop). */
    val canShare: Boolean get() = runCatching { jsCanShare() }.getOrDefault(false)

    fun shareText(text: String, done: (BrowserOutcome) -> Unit) = start(done) { jsShareText(text, it) }

    fun copyText(text: String, done: (BrowserOutcome) -> Unit) = start(done) { jsCopyText(text, it) }

    /** Compartilha o arquivo; sem suporte a compartilhar arquivos, baixa. */
    fun shareFile(base64: String, fileName: String, mimeType: String, done: (BrowserOutcome) -> Unit) =
        start(done) { jsShareFile(base64, fileName, mimeType, false, it) }

    fun shareFile(bytes: ByteArray, fileName: String, mimeType: String, done: (BrowserOutcome) -> Unit) =
        shareFile(Base64.encode(bytes), fileName, mimeType, done)

    fun downloadFile(bytes: ByteArray, fileName: String, mimeType: String, done: (BrowserOutcome) -> Unit) =
        start(done) { jsShareFile(Base64.encode(bytes), fileName, mimeType, true, it) }

    /** Garante um único `done`, mesmo se a chamada ao JS falhar depois de ele já ter sido invocado. */
    private fun start(done: (BrowserOutcome) -> Unit, call: ((String) -> Unit) -> Unit) {
        var finished = false
        val finish = { outcome: BrowserOutcome ->
            if (!finished) {
                finished = true
                done(outcome)
            }
        }
        try {
            call { finish(outcomeOf(it)) }
        } catch (error: Throwable) {
            // Erro do próprio `done` (já entregue) não é falha do navegador: não mascarar.
            if (finished) throw error
            finish(BrowserOutcome.FAILED)
        }
    }
}
