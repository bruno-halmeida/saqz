# Atores por API: cenários multi-pessoa sem LLM e sem aparelho

Cada cenário é uma regra do cofre (`Saqz/Regras/<feature>/<ID>.md`) executada por **várias pessoas ao mesmo tempo** (dono,
admins, atletas) direto na API do backend local. Não usa tela, aparelho nem modelo de linguagem: cria as contas no Auth
Emulator, chama `/api/**` com o token de cada uma, confere resposta, banco e central de avisos, e diz **passou** ou
**falhou** por regra. É a camada 1 da automação das regras que dependem de mais de uma pessoa; a camada 2 (Maestro, telas)
vem depois e cobre o que só a tela mostra.

## Como rodar

```sh
tests/multi-aparelhos/stack.sh up                 # Postgres, Auth Emulator, Mailpit e backend em 127.0.0.1:18080
node tests/atores/run.mjs --criticos              # só as 21 regras críticas
node tests/atores/run.mjs --todos                 # todas (inclui 2 cenários lentos, ~6 min)
node tests/atores/run.mjs --todos --sem-lentos    # todas menos as que esperam um job de minutos
node tests/atores/run.mjs --regra PRES-005        # uma regra (ou várias: --regra PRES-005,PRES-009)
node tests/atores/run.mjs --lista                 # lista os cenários, com críticos e lentos
node tests/atores/run.mjs --criticos --subir --derrubar   # sobe o stack, roda e derruba o que ele mesmo subiu
```

- Node 22 ou mais novo, **sem dependências** (só `fetch`, `FormData` e módulos nativos). O SQL usa o `psql` do container do
  stack (`docker exec saqz-multidev-db psql`), o mesmo banco que o `stack.sh` sobe.
- Sem `--subir`, se o backend não responder em `http://127.0.0.1:18080/actuator/health` o comando para com a mensagem de
  como subir. O comando **nunca derruba um stack que não subiu**.
- Endereços podem ser trocados por variável: `SAQZ_ATORES_API`, `SAQZ_ATORES_AUTH`, `SAQZ_ATORES_MAILPIT`,
  `SAQZ_ATORES_DB`. `SAQZ_ATORES_TETO_MS` muda o teto de um cenário (padrão 240 s; os lentos têm teto próprio) e
  `SAQZ_ATORES_PILHA=1` imprime a pilha de erros de código (não de conferência).
- Código de saída: `0` tudo passou; `1` algum cenário falhou ou ficou bloqueado; `2` uso errado ou stack fora do ar.
- Relatório: terminal e `tests/atores/ultimo-resultado.md` (regra, passou/falhou, tempo, passo que falhou, resposta do servidor).

## Banco limpo por cenário: não precisa de `stack.sh reset`

Cada cenário gera uma `<tag>` única (`Date.now` em base 36 + 4 caracteres) e a usa nos e-mails (`qa-org+<tag>@saqz.local`),
nos nomes (`QA ORG <tag>`, `QA Vôlei <tag>`) e nos grupos. As contas e os grupos de um cenário nunca se cruzam com os de
outro, então dá para rodar em cima de banco sujo, repetir, e rodar ao lado de outra sessão que use o mesmo stack. Os poucos
estados globais que alguns cenários mexem (travas de reenvio de e-mail/senha, modo da oferta de teste) são zerados ou
**restaurados no fim do próprio cenário**.

## Como o cenário é escrito

`cenarios/<feature>.mjs` exporta um array; cada item é uma regra:

```js
{
  id: 'PRES-005', feature: 'presenca', critico: true,
  titulo: '"Vou" depois do prazo vai sempre para a espera',
  async rodar(c) {
    const { ORG, ATL1, ATL2 } = await c.atores('ORG', 'ATL1', 'ATL2')          // contas + token + telefone
    const g = await c.passo('ORG cria o grupo; ATL1 e ATL2 entram pelo convite', () => c.grupo(ORG, [ATL1, ATL2]))
    const j = await c.passo('ORG publica jogo de 2 vagas; o prazo vence', async () => {
      const jogo = await c.jogo(ORG, g.id, { vagas: 2 })
      await c.prazoPara(jogo.id, new Date(Date.now() - 60_000))                 // relógio por banco, como o roteiro 01
      return jogo
    })
    await c.passo('ATL1 diz Vou: entra na espera e sobe na hora', async () => {
      c.status(await ATL1.vou(g.id, j.id), 200)
      c.igual(await c.statusDe(j.id, ATL1), 'CONFIRMED', 'ATL1')
      c.igual((await c.eventos(j.id, ATL1))[0], 'null>WAITLISTED:SELF', 'passou pela espera')
    })
  },
}
```

- **Passos nomeados** no formato "ORG faz X; ATL1 faz Y; confere Z" (espelham as tabelas do cofre e os roteiros de
  `tests/multi-aparelhos/roteiros`). O relatório diz qual passo falhou.
- **`lib/ator.mjs`** é uma pessoa: um método por rota (`criarGrupo`, `convite`, `entrarPorConvite`, `publicarJogo`,
  `vou`/`naoVou`, `porOutro`, `colocarNoJogo`, `cobrancas`, `baixa`, `cobrar`, `avisosDoJogo`, `excluirConta`…). Todo método
  devolve a resposta crua (`status`, `corpo`, `etag`, `codigo`); quem confere é o cenário.
- **`lib/cenario.mjs`** (`c`): `atores`, `grupo`, `jogo`, `configurar`, `tipo` (mensalista/avulso), `assinar` (assinatura
  simulada no banco), `encerrarTeste`, `prazoPara`, `inicioPara`, `presencas`, `eventos`, `statusDe`, `sql`, `valor`,
  `esperar`; conferências `status`, `igual`, `afirmar`; `divergir` registra uma divergência **sem parar** o cenário (para os
  passos seguintes ainda rodarem) e o cenário falha no fim; `bloqueado(motivo)` marca o cenário como BLOQUEADO.
- **`lib/auth.mjs`**: REST do Auth Emulator (criar conta, entrar, confirmar e-mail, token com `auth_time` antigo para a
  exclusão de conta). **`lib/db.mjs`**: `psql` do container. **`lib/espera.mjs`**: `esperar(descricao, fn, { teto })`, poll
  com teto que lança erro dizendo o que esperava.
- Campo `achado: 'ID'` no cenário só rotula a falha no relatório (a regra falha de verdade; ver `achados.md`).
- Campos `lento: true` e `teto` (ms) marcam cenários que esperam um job (`--sem-lentos` pula).

### Concorrência, relógio e prazo

- **Toque duplo e disputa de vaga**: as chamadas saem **em paralelo** (`Promise.all`) e o cenário confere que um efeito só
  aconteceu (`PRES-009`: 5 "Vou" juntos = 1 linha, 1 evento, 1 cobrança; última vaga entre 3 atletas; `FIN-002`: duas baixas
  na mesma versão = 200 e 409; `JOGO-009`: dois jogos no mesmo horário = 201 e 409).
- **Relógio**: mesmas atualizações de banco do roteiro 01 (`update games set confirmation_deadline=…`, `starts_at=…`), que o
  cenário faz por `c.prazoPara` e `c.inicioPara`. Os jobs que o backend roda sozinho são esperados por poll com teto
  (`PROM-005`: job do prazo a cada minuto; `JOGO-006`: encerramento a cada 5 minutos).

## Regras cobertas (81 cenários, 21 críticos)

| Arquivo | Regras |
| --- | --- |
| `acesso.mjs` | ACE-001\*, 002\*, 003, 004, 005, 006, SAI-009\* |
| `assinatura.mjs` | ASS-001, 002\*, 003\*, 005, 006 |
| `avisos.mjs` | NOT-001, 002, 003\*, 004, 005, 006 |
| `convites.mjs` | CONV-001\*, 002\*, 003, 004, 005, 006, 007\*, 008 |
| `financeiro.mjs` | FIN-001\*, 002\*, 004\*, 005, 006, 007, 008 |
| `jogos.mjs` | JOGO-001, 002, 003, 004\*, 005\*, 006, 007, 008, 009, 010 |
| `link-presenca.mjs` | LINK-001, 002, 003, 004, 006 |
| `membros.mjs` | MEM-001, 002, 003, 004, 005 |
| `presenca.mjs` | PRES-001, 002\*, 003, 004, 005\*, 006\*, 007, 008, 009\*, 010, 011, 012, 013 |
| `promocao.mjs` | PROM-001\*, 002\*, 003, 004\*, 005, 006, 007, 008, 009 |
| `saida.mjs` | SAI-001, 002, 003, 006, 007, 008 |

\* crítica.

## Limites: o que este executor não prova

- **Telas e textos do app** (textos de toast, folhas, "Voltar", teclado): é a camada 2. Aqui se confere o contrato da API.
- **Push** (FCM/APNs não existem no stack local): confere-se só a **central de avisos** (`GET /api/me/notifications`) e as
  tabelas `group_messages`/`group_notifications`. Aviso de grupo excluído, por exemplo, só se vê no banco (a central
  esconde aviso de grupo que já não existe). Que o aviso saia *por push* ficou para a rodada em produção.
- **WhatsApp** (grupo vinculado, botões do lembrete, mensagem particular) e **pagamento** (Asaas, Apple, Google): sem
  chave no stack local. `NOT-007`, `ASS-004` e a parte de pagamento de `ASS-005` não têm cenário; a assinatura é
  **simulada no banco** (`c.assinar`) para provar o que o backend faz com ela (limites, carência, congelamento).
- **Horário dos jobs diários**: lembrete de presença às 9h/12h30/18h, "jogo liberado" às 14h, mensalidade automática às 3h10,
  avisos do fim do teste (3, 1 e 0 dias) e segundo aviso de pedido parado (2 dias) disparam por cron em horário fixo de
  São Paulo e não há como chamá-los por API. Ficam sem cenário (`NOT-003` prova o lembrete **manual** e a publicação;
  `FIN-003`, `ASS-002` marcos de 3/1/0 dias e o pedido parado dependem de teste de integração do backend ou de rodar o job).
- **Regras que moram no app**, não na API: "a primeira quadra informada vira a do grupo" (`JOGO-007`), preenchimento da
  duração do jogo pelo padrão (`JOGO-002`), travas de toque duplo da tela (`GER-001`).
- **Limites por IP** (30 prévias de convite anônimas por 10 minutos, 20 e-mails por IP) não são exercitados até o teto para
  não travar outras sessões no mesmo `127.0.0.1`; o teto **por conta** é.
- `SAI-004` (delegação financeira) depende da conta de recebimentos (Asaas) e não tem cenário.
- `CONV-010` a `CONV-012` (convite de mensalistas, DEB-106) foram decididas em 20/10 e ainda não têm rota: os cenários de API entram junto da entrega (cadastro por dono, admin e atleta, resgate como mensalista ou avulso, vagas do plano, aviso a dono e admins, 403 e 422). A fila de envio, as falhas e a pausa dependem do Uazapi falso (`tests/multi-aparelhos/ferramentas/uazapi-falso.mjs`), que o stack destes cenários não sobe; ficam no roteiro multi-aparelho `tests/multi-aparelhos/roteiros/10-convite-de-mensalistas.md`.

## Operação

- O stack é compartilhado: outras sessões podem estar usando 18080/9099/15432/11025/18025. Confira com
  `tests/multi-aparelhos/stack.sh status` antes de subir ou derrubar.
- Falha de cenário que for **bug real** não se corrige aqui: registre em [`achados.md`](achados.md) com regra, passos e
  resposta do servidor.
- Para a rodada noturna da CI: `node tests/atores/run.mjs --todos --subir --derrubar` em `ubuntu-latest` (Docker já vem;
  precisa de JDK 21 e do Firebase CLI na versão do `stack.sh`, como no job `e2e-android` de `mobile-nightly.yml`) e
  guardar `tests/atores/ultimo-resultado.md` como artefato.
