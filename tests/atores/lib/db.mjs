import { execFile } from 'node:child_process'
import { promisify } from 'node:util'
import { DB_CONTAINER } from './config.mjs'

const exec = promisify(execFile)

/** Roda SQL no Postgres do stack (psql do container) e devolve as linhas como arrays de texto. */
export async function sql(consulta) {
  const { stdout } = await exec('docker', ['exec', DB_CONTAINER, 'psql', '-U', 'saqz', '-d', 'saqz', '-At', '-F', '|', '-c', consulta],
    { maxBuffer: 16 * 1024 * 1024 })
  return stdout.split('\n').filter((l) => l !== '').map((l) => l.split('|'))
}

/** Primeira coluna da primeira linha, ou null. */
export async function valor(consulta) {
  const linhas = await sql(consulta)
  return linhas.length ? linhas[0][0] : null
}

/** Escapa literal de texto para SQL. */
export const lit = (texto) => `'${String(texto).replaceAll("'", "''")}'`
