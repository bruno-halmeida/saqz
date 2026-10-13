import { readFileSync } from 'node:fs';

// O wasm importa uma função por nome de './skiko.mjs'; o stub exporta cada uma, lendo a lista do próprio .wasm.
export async function resolve(specifier, context, nextResolve) {
  if (specifier !== './skiko.mjs') return nextResolve(specifier, context);
  const wasm = new URL('saqz-web-test.wasm', new URL('.', context.parentURL));
  const imports = WebAssembly.Module.imports(new WebAssembly.Module(readFileSync(wasm)));
  const exports = imports
    .filter((entry) => entry.module === './skiko.mjs' && entry.kind === 'function')
    .map((entry) => `export const ${entry.name} = () => { throw new Error('skiko indisponível nos testes de Node'); };`);
  const source = `${exports.join('\n')}\nexport const skikoApi = {};\nexport const awaitSkiko = Promise.resolve({});`;
  return { url: `data:text/javascript,${encodeURIComponent(source)}`, shortCircuit: true };
}
