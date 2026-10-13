@file:OptIn(ExperimentalWasmJsInterop::class)

package br.com.saqz.webapp.interop

import kotlin.js.ExperimentalWasmJsInterop

// Um "navegador" mínimo no Node para os testes das portas: troca navigator/document/FileReader globais por
// dublês que registram o que foi chamado. As funções JS de verdade (BrowserShare, BrowserFilePicker,
// BrowserImageRender) rodam sem alteração contra eles.
//   shareMode: "none" (sem navigator.share) | "ok" | "abort" (AbortError) | "denied" (NotAllowedError)
@JsFun(
    """(shareMode, canShareFiles, hasClipboard) => {
  const g = globalThis;
  const log = { shared: [], copied: [], downloads: [], drawn: [], canvas: null, picker: [] };
  g.__saqzFake = { log, pick: null };
  const define = (name, value) => Object.defineProperty(g, name, { value, configurable: true, writable: true });
  const settle = () => {
    if (shareMode === 'abort') return Promise.reject(Object.assign(new Error('abort'), { name: 'AbortError' }));
    if (shareMode === 'denied') return Promise.reject(Object.assign(new Error('denied'), { name: 'NotAllowedError' }));
    return Promise.resolve();
  };
  const fakeNavigator = {};
  if (shareMode !== 'none') {
    fakeNavigator.share = (data) => {
      if (data.files) log.shared.push('files:' + data.files.map((f) => f.name + '|' + f.type + '|' + f.size).join(','));
      else log.shared.push('text:' + data.text);
      return settle();
    };
    fakeNavigator.canShare = () => canShareFiles;
  }
  if (hasClipboard) {
    fakeNavigator.clipboard = { writeText: (text) => { log.copied.push(text); return Promise.resolve(); } };
  }
  define('navigator', fakeNavigator);
  define('FileReader', class {
    readAsDataURL(file) {
      this.result = 'data:' + file.type + ';base64,' + file.base64;
      Promise.resolve().then(() => this.onload());
    }
  });
  define('document', {
    body: { appendChild() {} },
    createElement(tag) {
      if (tag === 'a') {
        return { style: {}, remove() {}, click() { log.downloads.push(this.download); } };
      }
      if (tag === 'canvas') {
        const canvas = {
          width: 0,
          height: 0,
          getContext: () => ({
            fillRect() {},
            measureText: (text) => ({ width: text.length * 20 }),
            fillText(text) { log.drawn.push(text); },
          }),
          toDataURL: () => { log.canvas = canvas.width + 'x' + canvas.height; return 'data:image/png;base64,UE5H'; },
        };
        return canvas;
      }
      const listeners = {};
      return {
        style: {},
        files: null,
        addEventListener(name, listener) { listeners[name] = listener; },
        remove() {},
        click() {
          log.picker.push(this.accept);
          const script = g.__saqzFake.pick;
          Promise.resolve().then(() => {
            if (script === null || script === 'cancel') { listeners.cancel(); return; }
            if (script === 'empty') { this.files = []; listeners.change(); return; }
            this.files = [script];
            listeners.change();
          });
        },
      };
    },
  });
}""",
)
internal external fun installFakeBrowser(shareMode: String, canShareFiles: Boolean, hasClipboard: Boolean)

/** Próxima escolha do seletor de arquivo: "cancel", "empty" ou um arquivo (tipo, tamanho, conteúdo em base64). */
@JsFun("(type, size, base64) => { globalThis.__saqzFake.pick = { type, size, base64 }; }")
internal external fun fakePickFile(type: String, size: Int, base64: String)

@JsFun("(script) => { globalThis.__saqzFake.pick = script; }")
internal external fun fakePickScript(script: String)

@JsFun("() => JSON.stringify(globalThis.__saqzFake.log)")
internal external fun fakeBrowserLog(): String
