# Re-verificação independente — fix do re-vínculo de grupo de WhatsApp (gap 1 da `validation.md`)

- **Range verificado:** `dbef6bb5..82d0ad82` (HEAD `82d0ad82`, autor do fix ≠ verificador).
- **Objeto:** resolver o gap 1 (média) de `validation.md:119` — re-vínculo não cancelava jobs
  pendentes do vínculo anterior (`spec.md:16-18`).
- **Método:** leitura de `spec.md`, seção 4 de `validation.md` e `git diff dbef6bb5..82d0ad82`;
  gate real forçado no HEAD; sensor de mutação M1–M3 em cópia isolada (`git archive`), mutações
  descartadas uma a uma.

## Veredito

**PASS.** O fix entrega o desfecho exigido pela spec: re-vínculo com JID **diferente** cancela os
jobs `PENDING` do grupo **na mesma transação** do upsert; re-vínculo do **mesmo JID** preserva os
pendentes. Nenhum `SPEC_DEVIATION`. Na Rodada 1, M3 (filtro de status) sobreviveu como lacuna de
**teste**; o teste de `2b903208` a matou na Rodada 2 — **3/3 mutações mortas** (ver seção final).

## A) Resultado ancorado na spec

### A1. Caiu o gate no HEAD (`82d0ad82`)

Comando (re-execução forçada, sem cache):

```
cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :features:groups:test :features:groups:integrationTest --rerun-tasks
```

| Tarefa | Suítes | Testes | Falhas | Erros | Resultado |
|---|---|---|---|---|---|
| `:features:groups:test` | 59 | **690** | 0 | 0 | verde |
| `:features:groups:integrationTest` | 48 | **573** | 0 | 0 | verde |

`BUILD SUCCESSFUL in 4m 11s`, `15 actionable tasks: 15 executed` (sem `FROM-CACHE`/`UP-TO-DATE`
nas tarefas de teste). Testes direcionados: `NotificationWhatsAppGroupIntegrationTest` = **15/15**
(inclui os 2 novos), `LinkGroupWhatsAppTest` = **15/15**; no XML, os novos casos passam:
`relinking with a different jid cancels the pending jobs of the previous binding` e
`relinking the same jid preserves the pending jobs`.

### A2. Cancelamento no JID diferente, atômico com o upsert

Implementação no HEAD: `backend/features/groups/src/main/kotlin/br/com/saqz/groups/application/whatsapp/LinkGroupWhatsApp.kt:123-127`

```kotlin
transactionRunner.inTransaction {
    val replaced = bindings.find(groupId)?.whatsappJid?.let { it != invite.jid } ?: false
    bindings.upsert(binding)
    if (replaced) bindings.cancelPendingByGroup(groupId)
}
```

- `find` + `upsert` + `cancel` rodam dentro de `TransactionTemplate(DataSourceTransactionManager)`
  com `ISOLATION_READ_COMMITTED` (`.../adapter/output/jdbc/transaction/JdbcTransactionRunner.kt:12-16`),
  no mesmo `DataSource` — mesma conexão/transação, com rollback conjunto. Atomicidade estrutural
  confirmada.
- Escopo do cancelamento é **do grupo** (não global):
  `JdbcGroupWhatsAppBindingRepository.kt:103-107` — `q.message_id IN (SELECT id FROM group_messages
  WHERE group_id = :groupId)`.
- Somente `PENDING` (fila de retry usa `PENDING` + `next_attempt_at`,
  `JdbcNotificationWhatsAppGroup.kt:36-37,129-130`), com `completed_at = now()` e **sem** tocar
  `attempts` (`JdbcGroupWhatsAppBindingRepository.kt:41-43,104`), coerente com o worker
  (`JdbcNotificationWhatsAppGroup.kt:134-139`, `CANCELLED` não incrementa). O teste novo cobre
  isso: `assertEquals(0, attempts())` em `NotificationWhatsAppGroupIntegrationTest.kt:205`.
- Comparação com a spec (`spec.md:16-18`): "colar um novo link substitui o vínculo existente … e
  cancela jobs de grupo pendentes do vínculo anterior" — exatamente o comportamento; o guard
  `replaced` é o que distingue substituição real de re-upsert do mesmo JID.

### A3. JID igual preserva pendentes

Guard `it != invite.jid` (`LinkGroupWhatsApp.kt:124`); com mesmo JID `replaced = false` e nenhum
cancel é emitido. Evidência verde: `NotificationWhatsAppGroupIntegrationTest.kt:210-220`
(`assertEquals("PENDING", status())` na linha 218, `bindingJid()` na 219).

### A4. Checagens de desvio pedidas

| Ponto | Situação | Evidência |
|---|---|---|
| Tentativas dos cancelados | Não incrementa (`attempts` intocado); compatível com worker e documentado na porta | `JdbcGroupWhatsAppBindingRepository.kt:104` + teste `:205` |
| Transacionalidade | Real: `TransactionTemplate` + `DataSourceTransactionManager` (READ_COMMITTED), upsert e cancel na mesma transação | `LinkGroupWhatsApp.kt:123-127`, `JdbcTransactionRunner.kt:12-16` |
| Escopo do cancelamento | Por grupo Saqz (subquery por `group_id`), não global | `JdbcGroupWhatsAppBindingRepository.kt:106` |

**Nenhum `SPEC_DEVIATION`.** Ressalva não-bloqueante: não há teste que force falha para provar o
rollback conjunto (estruturalmente correto); ver gaps.

## B) Sensor de discriminação em cópia isolada (Rodada 1)

Cópia: `git archive 82d0ad82 backend > /tmp/reverify-fix.tar`, extraída em `/tmp/reverify-fix`.
Todas as mutações aplicadas **somente na cópia**; repo intacto (`git status --porcelain` vazio
antes e depois; cópia descartada ao fim). Cada rodada com `--rerun-tasks` e filtro `--tests`.

| Mutação | Injeção (na cópia) | Teste(s) alvo | Resultado |
|---|---|---|---|
| **M1** | `LinkGroupWhatsApp.kt:126`: remover o guard `if (replaced)` (cancelar sempre) | `relinking the same jid preserves the pending jobs` | **MORTA** — `EXIT=1`; `AssertionFailedError: expected: <PENDING> but was: <CANCELLED>` em `NotificationWhatsAppGroupIntegrationTest.kt:218` |
| **M2** | `LinkGroupWhatsApp.kt:126`: remover a chamada `bindings.cancelPendingByGroup(groupId)` | `relinking with a different jid cancels the pending jobs of the previous binding` | **MORTA** — `EXIT=1`; `expected: <CANCELLED> but was: <PENDING>` em `NotificationWhatsAppGroupIntegrationTest.kt:204` |
| **M3** | `JdbcGroupWhatsAppBindingRepository.kt:105`: remover `AND q.status = 'PENDING'` do `CANCEL_PENDING_BY_GROUP` | ambos os relinks + `JdbcGroupWhatsAppBindingRepositoryIntegrationTest` (24 testes) e suíte completa `:features:groups:integrationTest` | **SOBREVIVENTE** — 24/24 e **573/573 verdes**, `BUILD SUCCESSFUL`; classe mutante compilada confirmada sem a string do filtro (nenhum `q.status = 'PENDING'` no `.class`), logo a mutação esteve ativa nas execuções |

Cobertura de quem exercita `cancelPendingByGroup`: apenas os 2 testes novos de relink (grep em
`backend/features/groups/src` e `backend/bootstrap/src`); o endpoint de bootstrap não inspeciona a
fila, e os unitários usam fakes (`LinkGroupWhatsAppTest.kt:238`, `ManageGroupWhatsAppBindingTest.kt:136`).

> M3 foi re-checado em cópia nova de `origin/main` (`2b903208`) e está **MORTA** na Rodada 2 —
> detalhes na seção final.

## Gaps ranqueados

1. **[Média — lacuna de teste, não do fix; RESOLVIDO na Rodada 2 — `2b903208`] M3 sobreviveu na
   Rodada 1: nada distinguia cancelar só `PENDING` de
   sobrescrever status terminal.** Cenário sem cobertura: job já `ACCEPTED`/`FAILED`/`CANCELLED`
   do grupo + re-vínculo com JID diferente. No código entregue ele fica intacto; um refactor que
   remova o filtro sobrescreveria `status`/`completed_at` (corrupção de histórico) sem nenhum teste
   vermelho. **Recomendação:** teste que drena um job até `ACCEPTED`, faz relink com JID diferente
   e assere que o job terminal permaneceu `ACCEPTED` com `completed_at` inalterado. Adjacente:
   o **escopo por grupo** da subquery também não é discriminado (os cenários atuais têm um único
   grupo; um cancelamento global passaria) — vale assert com 2 grupos e job `PENDING` no outro.
   **Status:** resolvido em `2b903208` — o teste `relinking keeps terminal jobs of the previous
   binding intact` mata M3 (ver "Rodada 2"). A lacuna adjacente de **escopo** segue sem teste
   dedicado (não foi alvo desta rodada; ver item 2 sobre evidência dinâmica).
2. **[Baixa] Sem teste de atomicidade/rollback.** A estrutura usa `TransactionTemplate`
   (correta), mas nenhum teste força falha no upsert/cancel para provar que ambos revertem juntos.
   Não contradiz a spec; é lacuna de evidência dinâmica.
3. **[Baixa/informativo] `attempts` dos cancelados.** A spec não fixa o valor; o fix mantém 0,
   documentado na porta (`GroupWhatsAppBindingRepository.kt:19-24`) e coberto em teste. Sem ação.

## Resumo

| Item | Veredito | Evidência principal |
|---|---|---|
| A) Desfecho da spec (`spec.md:16-18`) | **PASS** | `LinkGroupWhatsApp.kt:123-127` + 2 testes novos verdes |
| B) M1 | MORTA | `NotificationWhatsAppGroupIntegrationTest.kt:218` |
| B) M2 | MORTA | `NotificationWhatsAppGroupIntegrationTest.kt:204` |
| B) M3 | **MORTA** (Rodada 2, `2b903208`) | `NotificationWhatsAppGroupIntegrationTest.kt:233` — `expected: <1> but was: <0>` |

Gate na Rodada 1 (HEAD `82d0ad82`): **1263 testes, 0 falhas** (`test` 690 + `integrationTest` 573).
Fix verificado: o gap 1 de `validation.md` está resolvido no produto e a lacuna de teste (M3) foi
fechada em `2b903208`; sensor final **3/3 mortas**.

---

## Rodada 2 — M3 re-checado (2b903208)

- **Base:** `origin/main` = `2b903208` (`test(groups): cover terminal group jobs preserved across
  rebind`); commit desta verificação rebaseado por cima; árvore limpa antes/depois.
- **Cópia isolada nova:** `git archive origin/main backend > /tmp/reverify-round2.tar`, extraída em
  `/tmp/reverify-round2`; mutação aplicada **somente na cópia**; cópia descartada ao fim; repo
  intacto (`git status --porcelain` vazio).
- **Mutação M3 (re-aplicada):** remover `AND q.status = 'PENDING'` do SQL de
  `cancelPendingByGroup` (`JdbcGroupWhatsAppBindingRepository.kt:105` na cópia).
- **Comando:**
  ```
  cd /tmp/reverify-round2/backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
    ./gradlew :features:groups:integrationTest --tests '*NotificationWhatsAppGroupIntegrationTest' --rerun-tasks
  ```
- **Resultado: M3 MORTA.** `BUILD FAILED` (`EXIT=1`); `NotificationWhatsAppGroupIntegrationTest`
  com **16 testes / 1 falha**:
  - Teste que matou: `relinking keeps terminal jobs of the previous binding intact()`
  - Asserção/linha: `NotificationWhatsAppGroupIntegrationTest.kt:233` —
    `AssertionFailedError: expected: <1> but was: <0>` (`assertEquals(1, countStatus("ACCEPTED"))`
    após o re-vínculo: sem o filtro, o job terminal `ACCEPTED` é sobrescrito para `CANCELLED`).
  - Classe mutante compilada confirmada sem a string do filtro (0 ocorrências de
    `q.status = 'PENDING'` no `.class`), logo a mutação esteve ativa na execução.
- **Baseline (sem mutação, repo em `2b903208`):** o mesmo teste passa — `tests=1 failures=0`,
  `BUILD SUCCESSFUL`.
- **Sensor final: 3/3 mutações mortas** (M1 e M2 na Rodada 1; M3 na Rodada 2). Veredito global:
  **PASS**, sem gaps abertos de discriminação.
