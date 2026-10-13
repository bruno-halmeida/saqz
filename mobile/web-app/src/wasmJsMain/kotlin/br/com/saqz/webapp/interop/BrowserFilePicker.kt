@file:OptIn(ExperimentalWasmJsInterop::class)

package br.com.saqz.webapp.interop

import kotlin.io.encoding.Base64
import kotlin.js.ExperimentalWasmJsInterop

// Abre o seletor de arquivo do navegador (<input type="file">) e responde por callback, uma única vez:
//   "cancelled"               a pessoa fechou o seletor;
//   "invalid"                 tipo fora de `accept`, vazio, acima de `maxBytes` ou ilegível;
//   "ok|<mime>|<base64>"      o arquivo (FileReader já entrega em base64, sem copiar byte a byte).
// `click()` exige ativação do usuário: a chamada precisa vir síncrona do toque. O evento `cancel` do input
// existe nos navegadores atuais (Safari desde 16.4); sem ele o seletor fica sem resposta até a próxima escolha.
@JsFun(
    """(accept, maxBytes, done) => {
  let finished = false;
  const finish = (result) => {
    if (finished) return;
    finished = true;
    done(result);
  };
  try {
    const allowed = accept.split(',');
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = accept;
    input.multiple = false;
    input.style.display = 'none';
    input.addEventListener('cancel', () => {
      input.remove();
      finish('cancelled');
    });
    input.addEventListener('change', () => {
      const file = input.files && input.files[0];
      input.remove();
      if (!file) {
        finish('cancelled');
        return;
      }
      if (file.size === 0 || file.size > maxBytes || allowed.indexOf(file.type) < 0) {
        finish('invalid');
        return;
      }
      const reader = new FileReader();
      reader.onload = () => {
        const url = String(reader.result);
        finish('ok|' + file.type + '|' + url.slice(url.indexOf(',') + 1));
      };
      reader.onerror = () => finish('invalid');
      reader.readAsDataURL(file);
    });
    document.body.appendChild(input);
    input.click();
  } catch (error) {
    finish('invalid');
  }
}""",
)
private external fun jsPickFile(accept: String, maxBytes: Int, done: (String) -> Unit)

internal sealed interface BrowserFilePick {
    class Picked(val mimeType: String, val bytes: ByteArray) : BrowserFilePick
    data object Cancelled : BrowserFilePick
    data object Invalid : BrowserFilePick
}

/** Fronteira Kotlin do seletor de arquivo do navegador. */
internal object BrowserFilePicker {
    fun pick(mimeTypes: Collection<String>, maxBytes: Int, done: (BrowserFilePick) -> Unit) {
        var finished = false
        val finish = { result: BrowserFilePick ->
            if (!finished) {
                finished = true
                done(result)
            }
        }
        try {
            jsPickFile(mimeTypes.joinToString(","), maxBytes) { finish(parse(it)) }
        } catch (error: Throwable) {
            if (finished) throw error
            finish(BrowserFilePick.Invalid)
        }
    }

    private fun parse(code: String): BrowserFilePick = when {
        code == "cancelled" -> BrowserFilePick.Cancelled
        code.startsWith("ok|") -> {
            val mimeType = code.substringAfter('|').substringBefore('|')
            val encoded = code.substringAfter('|').substringAfter('|')
            runCatching { BrowserFilePick.Picked(mimeType, Base64.decode(encoded)) }.getOrDefault(BrowserFilePick.Invalid)
        }
        else -> BrowserFilePick.Invalid
    }
}
