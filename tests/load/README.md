# Teste de carga

Responde a uma pergunta: **quantas pessoas ao mesmo tempo este servidor aguenta, e o que quebra primeiro.**

| Arquivo | O que é |
|---|---|
| `session.js` | Roteiro k6 que repete o que o app faz: abre (PUT session + Home), entra no grupo (as mesmas chamadas em série da tela), às vezes abre membros, opcionalmente responde presença. Tem pausas de leitura, então **1 VU ≈ 1 pessoa usando o app**. |
| `seed-volume.sql` | Envelhece o grupo do seed em 2 anos (~210 jogos, ~3.000 presenças, ~1.350 cobranças). Sem isso `/games` e `/charges`, que devolvem o histórico inteiro, parecem rápidos e o número sai otimista. |

## Onde rodar

**O alvo é o Server Dev** (`https://saqz-api.brunoalmeida.dev`, com `ALLOW_SHARED=1`). Todo dado lá é
de teste e a máquina é do mesmo porte da que vai rodar de verdade — é justamente o teste que vale.
O `ALLOW_SHARED` continua obrigatório para o alvo nunca ser implícito.

O que a medição carrega por bater nesse host, e que é bom saber ao ler o resultado:

- **Cloudflare na frente.** O número inclui a borda. Se aparecer 403/429 em bloco, é a borda vendo
  ataque, não o servidor saturando — dá para separar pelo corpo da resposta.
- **Banco remoto** (Supabase, pooler em modo sessão). Cada conexão do Hikari ocupa um slot lá, por
  isso o pool é pequeno; `saqz.db.max-pool-size` ajusta sem recompilar.
- **Cota do Firebase**: cada `signIn` é uma chamada real. O `ramp` reusa o token por 50 min, como o
  app; só o `spike` pega token novo a cada iteração.
- **Pagamento fica de fora**: o roteiro não toca `/subscriptions` nem `/api/receivables`.
- As métricas do Hikari **não estão expostas** (401). Para enxergar o pool em vez de inferir pela
  curva de latência: `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics`.

Em ambiente isolado (`compose.yaml`, Firebase de teste) o caminho é o mesmo, trocando a `BASE_URL`:
`./seed-usuarios.sh local` → `./seed-exploracao.sh local` → `seed-volume.sql`
(`docker compose exec -T database psql -U saqz -d saqz -f - < tests/load/seed-volume.sql`).

## Rodar

```sh
k6 run -e BASE_URL=http://localhost:8080 -e PROFILE=smoke tests/load/session.js   # o roteiro funciona?
k6 run -e BASE_URL=... -e PROFILE=ramp  -e MAX_VUS=300 tests/load/session.js      # capacidade
k6 run -e BASE_URL=... -e PROFILE=spike -e MAX_VUS=<capacidade> tests/load/session.js
k6 run -e BASE_URL=... -e PROFILE=soak  -e MAX_VUS=<capacidade> tests/load/session.js
```

`WRITES=1` liga a resposta de presença (só em ambiente isolado).

- **ramp** sobe em degraus (10, 25, 50, 100, 150, 200, 300) e **aborta** quando p95 > 1 s ou erro > 1%.
  O último degrau que passou é a capacidade em usuários simultâneos.
- **spike** simula a hora do jogo: todos chegam em 1 minuto, cada iteração com **token novo**, o que
  fura o cache de 3 minutos e bate no Firebase a cada abertura.
- **soak** segura metade da capacidade por 1 hora: vazamento, conexão presa, cache crescendo.

## O que olhar enquanto roda

| Suspeito | Como ver | Sinal de que é ele |
|---|---|---|
| Pool de conexões (5 por padrão) | `GET /actuator/metrics/hikaricp.connections.pending` | `pending` > 0 sustentado enquanto a CPU está folgada |
| Limite do pooler do Supabase | painel do Supabase, conexões | erros 5xx com `remaining connection slots` no log |
| Firebase (token novo ≈ 320 ms) | tag `firebase signIn` e o 1º request de cada VU no spike | p95 sobe só no spike, não no ramp |
| CPU / memória da máquina | `docker stats`, `/actuator/metrics/jvm.memory.used`, `process.cpu.usage` | CPU > 85% antes de o pool encher |

O tamanho do pool é ajustável no ambiente de teste por `SAQZ_DB_MAX_POOL_SIZE` (padrão 5). Se o ramp
apontar o pool, repita com 10 e 20 **conferindo o teto de conexões do Supabase** antes: em session
mode cada conexão do pool segura um slot do pooler.

## Como ler o resultado

Capacidade = último degrau do `ramp` sem abortar. Isso é gente **usando ao mesmo tempo**; a base total
que isso sustenta depende de quanta gente abre o app na mesma hora (a hora do jogo é o pior caso, e é
o que o `spike` mede). Anote junto: qual suspeito encheu primeiro, e o p95 por endpoint do resumo do k6.
