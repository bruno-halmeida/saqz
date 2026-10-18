const ZONA = 'America/Sao_Paulo'

/** `Date` + `minutos` a partir de agora. */
export const daqui = (minutos) => new Date(Date.now() + minutos * 60_000)

/** Data e hora locais (São Paulo) de um instante, no formato que o backend espera. */
export function local(instante) {
  const partes = Object.fromEntries(new Intl.DateTimeFormat('en-CA', {
    timeZone: ZONA, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
  }).formatToParts(instante).map((p) => [p.type, p.value]))
  return { localDate: `${partes.year}-${partes.month}-${partes.day}`, localTime: `${partes.hour}:${partes.minute}:${partes.second}`, zoneId: ZONA }
}

/** Instante com os segundos zerados (o backend compara data/hora local com `startsAt`). */
export const semSegundos = (d) => new Date(Math.floor(d.getTime() / 60_000) * 60_000)
