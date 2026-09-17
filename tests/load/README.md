# Teste de carga

Responde a uma pergunta: **quantas pessoas ao mesmo tempo este servidor aguenta, e o que quebra primeiro.**

| Arquivo | O que é |
|---|---|
| `session.js` | Roteiro k6 que repete o que o app faz: abre (PUT session + Home), entra no grupo (as mesmas chamadas em série da tela), às vezes abre membros, opcionalmente responde presença. Tem pausas de leitura, então **1 VU ≈ 1 pessoa usando o app**. |
| `seed-volume.sql` | Envelhece o grupo do seed em 2 anos (~210 jogos, ~3.000 presenças, ~1.350 cobranças). Sem isso `/games` e `/charges`, que devolvem o histórico inteiro, parecem rápidos e o número sai otimista. |

## Onde rodar

**Nunca contra o Server Dev**: é compartilhado, tem conta de gente real, fica atrás do Cloudflare (que
bloqueia o tráfego como ataque) e gasta cota do Firebase e do Supabase. O script recusa esse host.
Exceção: `PROFILE=smoke` com `ALLOW_SHARED=1`, que é 1 usuário fazendo 1 sessão; a única escrita é o `PUT /api/session`, idempotente, o mesmo que o app faz ao abrir.

O alvo certo é um ambiente isolado de mesmo porte do servidor real, batendo direto na origem:

1. Subir backend + Postgres próprios (`compose.yaml`), com projeto Firebase de teste.
2. `./seed-usuarios.sh local` → `./seed-exploracao.sh local` → aplicar `tests/load/seed-volume.sql`
   (`docker compose exec -T database psql -U saqz -d saqz -f - < tests/load/seed-volume.sql`).
3. Expor as métricas só nesse ambiente, por variável (nada muda em produção):
   `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics`
4. Pagamento fica de fora: o roteiro não toca `/subscriptions` nem `/api/receivables`.

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
