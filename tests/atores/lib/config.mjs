// Endereços do stack local (tests/multi-aparelhos/stack.sh). Podem ser trocados por variável de ambiente.
export const API = process.env.SAQZ_ATORES_API ?? 'http://127.0.0.1:18080'
export const AUTH = process.env.SAQZ_ATORES_AUTH ?? 'http://127.0.0.1:9099'
export const MAILPIT = process.env.SAQZ_ATORES_MAILPIT ?? 'http://127.0.0.1:18025'
export const DB_CONTAINER = process.env.SAQZ_ATORES_DB ?? 'saqz-multidev-db'
export const SENHA = 'SenhaQA2026'
