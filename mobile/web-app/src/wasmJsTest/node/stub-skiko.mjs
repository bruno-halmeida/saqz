// Os testes das portas rodam no Node, onde o skiko (Compose, desenho em canvas) não existe nem é usado: o
// executável de teste leva o app inteiro e importa './skiko.mjs', que tentaria carregar o skiko.wasm e abortaria.
// Este hook troca só esse módulo por um vazio.
import { register } from 'node:module';

register('./stub-skiko-hooks.mjs', import.meta.url);
