# Teste de carga

Responde a uma pergunta: **quantas pessoas ao mesmo tempo este servidor aguenta, e o que quebra primeiro.**

| Arquivo | O que é |
|---|---|
| `session.js` | Roteiro k6 que repete o que o app faz: abre (PUT session + Home), entra no grupo (as mesmas chamadas em série da tela), às vezes abre membros, opcionalmente responde presença. Tem pausas de leitura, então **1 VU ≈ 1 pessoa usando o app**. |
| `analise.mjs` | Lê o `--out json` e diz, **degrau por degrau**, quem passou. O resumo do k6 é acumulado: um degrau ruim no fim fica diluído. É este que responde "qual foi o último degrau bom". |
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

No Supabase do Server Dev, a mesma conexão do `seed-exploracao.sh server`:

```sh
docker run --rm -i -e PGPASSWORD="$SAQZ_DB_PASSWORD" -e PGSSLMODE=require postgres:16-alpine \
  psql -h aws-0-sa-east-1.pooler.supabase.com -p 5432 \
       -U postgres.jrwpmobttggeturyekot -d postgres \
       -v ON_ERROR_STOP=1 -f - < tests/load/seed-volume.sql
```

### O volume aplica uma vez, e é de mão única

`group_charge_events` tem trigger `BEFORE UPDATE OR DELETE` que recusa qualquer mutação
(`V5__add_group_finance.sql`). O volume escreve ~1.300 desses eventos, e o `seed-exploracao.sql`
começa com `DELETE FROM group_charge_events` — ou seja, **depois do volume o seed de exploração não
roda mais nesse banco**. O próprio volume é no-op na segunda vez (checa o prefixo `10ad`).

Para voltar atrás sem recriar o banco, desligar o trigger é o único caminho, e só o dono da tabela
consegue:

```sql
ALTER TABLE group_charge_events DISABLE TRIGGER group_charge_events_append_only;
DELETE FROM group_charge_events WHERE group_id = '9a000000-0000-4000-8000-000000000001';
ALTER TABLE group_charge_events ENABLE TRIGGER group_charge_events_append_only;
```

Reabilitar não é opcional: é a trava que garante que histórico financeiro não se reescreve.

## Rodar

```sh
k6 run -e BASE_URL=http://localhost:8080 -e PROFILE=smoke tests/load/session.js   # o roteiro funciona?
k6 run -e BASE_URL=... -e PROFILE=ramp  -e MAX_VUS=300 tests/load/session.js      # capacidade
k6 run -e BASE_URL=... -e PROFILE=spike -e MAX_VUS=<capacidade> tests/load/session.js
k6 run -e BASE_URL=... -e PROFILE=soak  -e MAX_VUS=<capacidade> tests/load/session.js
```

`WRITES=1` liga a resposta de presença (só em ambiente isolado).

- **ramp** sobe em degraus (10 → 1200, limitado por `MAX_VUS`). A capacidade sai do `analise.mjs`, não
  do abort: o threshold aqui é rede de segurança (10% de erro, p95 3 s), porque 1% julgado cedo mata o
  teste por ruído. Guarde a saída com `--out json=/tmp/ramp.json` ou o degrau se perde.
- **concorrencia** mede **requisição em voo**, não usuário: sem pausa de leitura, 1 VU = 1 requisição
  simultânea. No `ramp`, 300 usuários com pausa realista são só ~4 requisições em voo.
- **spike** simula a hora do jogo: todos chegam em 1 minuto, cada iteração com **token novo**, o que
  fura o cache de 3 minutos e bate no Firebase a cada abertura.
- **soak** segura metade da capacidade por 1 hora: vazamento, conexão presa, cache crescendo.

## Resultado medido (2026-09-17, Server Dev)

`PROFILE=ramp MAX_VUS=900`, grupo com 2 anos de histórico (volume aplicado), 30 min:

| | |
|---|---|
| **Capacidade útil** | **676 VUs** (~11.300 req/min) — último degrau com p95 < 1 s |
| Primeiro degrau ruim | 826 VUs (p95 1.091 ms) — **sem erro nenhum** |
| Vazão máxima | 14.998 req/min (250 req/s) em 900 VUs, mas p95 1,3 s |
| Joelho da latência | p95 dobra de 97 ms (150 VUs) para 198 ms (450 VUs) |
| Erro | **2 em 133.931 requisições** (0,00%), em todos os degraus |

A vazão **não tem platô** dentro do que medimos: ela sobe até o fim. Quem estoura é a latência. O
servidor degrada, não cai — não houve 5xx, timeout nem 429 da borda em nenhum degrau.

Para comparar, o mesmo ramp no grupo **sem** volume (`MAX_VUS=300`): p95 plano de 93 a 119 ms, mediana
*caindo* de 57 para 53 ms conforme o cache de token esquenta, 0 erro em 40.371 requisições. Nenhum
sinal de saturação — por isso o volume não é opcional para medir capacidade.

### Quem limita: CPU, 2 cores

No pico (`docker stats` no servidor, load average 8.37 em 2 cores):

| Container | CPU (de 200%) | Memória |
|---|---|---|
| `saqz-backend-1` | 88,3% | 503 MB / 7,76 GB |
| `cloudflare-tunnel` | 51,1% | 88 MB |
| `traefik` | 29,3% | 168 MB |
| `saqz-database-1` | 0,0% | 32 MB (ocioso — o app usa Supabase) |

Não é o pool do Hikari (5), não é memória, não é o Supabase: são os 2 cores, e **47% do pico é
caminho de entrada** (túnel + proxy), não aplicação. O k6 do lado do cliente ficou em 21% de um core
de 10 e 46 Mbps — o gargalo não era o teste.

Ordem de alavanca, da melhor para a pior: **mais cores** (ganho quase linear, zero código) → **os 51%
do túnel** → **janela em `/charges`**, que devolve o histórico inteiro (831 KB, 1.642 itens no grupo
de 2 anos) e cresce ~500 itens/ano **por grupo, independente de quantos usuários existam** — um grupo
de 3 anos com 20 membros é mais lento que um grupo novo com 300.

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

Capacidade = último degrau com p95 < 1 s e erro < 1%, e isso sai do `analise.mjs`, não do resumo do
k6 (que é acumulado e dilui o degrau ruim). Isso é gente **usando ao mesmo tempo**; a base total que
isso sustenta depende de quanta gente abre o app na mesma hora — a hora do jogo é o pior caso, e é o
que o `spike` mede. Anote junto: qual suspeito encheu primeiro, e as rotas mais lentas do último degrau.

Dois cuidados que custaram ramps abortados:

- **Erro em bloco não é saturação.** 403 é papel (`/memberships` exige `MANAGE_ATHLETES`, atleta leva
  403 por definição) e 404 é conta fora do grupo — o `setup()` descobre o rateio justamente por isso.
  Saturação aqui apareceu como latência, nunca como erro.
- **O primeiro degrau é lento por aquecimento, não por fila.** Todo VU acabou de logar e paga o
  `checkRevoked` (~270 ms) antes do cache de 3 min pegar: 327 ms em 10 VUs contra 84 ms em 300.
