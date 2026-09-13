# Backend — controles de recebimentos

Contrato `docs/receivables/rollout-contract.md` implementado sem alteracoes de contrato, exclusivamente em backend.

## Resultado

- V53 aditiva cria modo global OFF por padrao, versao por usuario, excecoes removidas em INHERIT e auditoria imutavel.
- API rollout global, usuario, historico e availability autenticada; sem envelope de sucesso, no-store, identidade da sessao e admin consultado a cada requisicao.
- Escritas serializadas na linha de controle; replay verifica ator/conteudo/alvo antes de expectedVersion e devolve snapshot original. Alteracao da flag operacional trava a conta financeira e grava auditoria na mesma transacao.
- Availability e estado do usuario usam uma SELECT conjunta para snapshot consistente; nenhum lock adicional em leituras que possa inverter a ordem rollout/conta.
- Cadastro de conta nova e provisionamento READY respeitam BACKEND do titular; retomada de conta existente e recuperacao UNKNOWN continuam disponiveis.
- Ativacao verifica rollout do titular, inclusive se delegado possuir ALLOW proprio; cadastro aprovado, plano e flag operacional permanecem obrigatorios.
- Default do contexto financeiro falha fechado. Desativacao nao consulta rollout; documentos, leitura e manutencao continuam disponiveis.

## Gates

JDK21: `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`.

1. `./gradlew :features:receivables:check :bootstrap:test :architecture-tests:test`: BUILD SUCCESSFUL; 15 dominio, 28 integracao PostgreSQL, 393 bootstrap e 20 arquitetura, zero falhas/erros/skips.
2. Apos reforcos finais de validacao HTTP e teste do titular/delegado, `./gradlew :features:receivables:check :bootstrap:test --tests '*ReceivablesRolloutEndpointIntegrationTest' --tests '*GroupReceivablesIntegrationTest' --tests '*BearerSecurityIntegrationTest' --tests '*PlatformAdminEndpointIntegrationTest' :architecture-tests:test`: BUILD SUCCESSFUL.
3. `git diff --check -- backend`: limpo.

Cobertura adicionada: matriz completa de modos/excecoes, OFF absoluto, dependencia mobile/backend, manutencao, replay original, conflitos de ator/conteudo/versao, concorrencia, historico imutavel, flag sem conta rejeitada, INHERIT remove registro, HTTP real 401/403/404/400/409, admin revogado na mesma sessao, valores fracionarios/duplicados/nulos, provision READY bloqueado sem POST externo, UNKNOWN recuperavel e documentos apos OFF, ativacao direta apos revogacao e cancelamento mesmo sem tabela rollout.

Log completo: `/tmp/receivables-full-gates.log`; rodada final: `/tmp/receivables-final-gates.log`.

Contagens da rodada final:

```json
{
  "features/receivables/build/test-results/test": {
    "tests": 15,
    "failures": 0,
    "errors": 0,
    "skipped": 0
  },
  "features/receivables/build/test-results/integrationTest": {
    "tests": 28,
    "failures": 0,
    "errors": 0,
    "skipped": 0
  },
  "bootstrap/build/test-results/test": {
    "tests": 22,
    "failures": 0,
    "errors": 0,
    "skipped": 0
  },
  "architecture-tests/build/test-results/test": {
    "tests": 20,
    "failures": 0,
    "errors": 0,
    "skipped": 0
  }
}
```

## Pendencias

Nenhuma pendencia identificada nesta tarefa de toggles. Pagamentos permanecem follow-up separado, conforme instrucao; nenhuma API de dinheiro inexistente foi anunciada. Sem acesso a servidor/credenciais externas, branch/staging/commit/push.

## Arquivos alterados

- `backend/bootstrap/src/main/kotlin/br/com/saqz/adminweb/http/AdminReceivablesRolloutController.kt`
- `backend/bootstrap/src/main/kotlin/br/com/saqz/bootstrap/configuration/ReceivablesRolloutConfiguration.kt`
- `backend/bootstrap/src/test/kotlin/br/com/saqz/bootstrap/ReceivablesRolloutEndpointIntegrationTest.kt`
- `backend/features/receivables/src/integrationTest/kotlin/br/com/saqz/receivables/ReceivablesRolloutIntegrationTest.kt`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/jdbc/JdbcReceivablesRollout.kt`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/application/ReceivablesRollout.kt`
- `backend/features/receivables/src/main/resources/db/migration/V53__receivable_rollout_controls.sql`
- `backend/features/receivables/src/test/kotlin/br/com/saqz/receivables/domain/RolloutPolicyTest.kt`
- `backend/bootstrap/src/main/kotlin/br/com/saqz/bootstrap/configuration/GroupReceivablesConfiguration.kt`
- `backend/bootstrap/src/main/kotlin/br/com/saqz/bootstrap/configuration/ReceivablesConfiguration.kt`
- `backend/bootstrap/src/main/kotlin/br/com/saqz/bootstrap/configuration/http/ApiProblemWriter.kt`
- `backend/bootstrap/src/test/kotlin/br/com/saqz/bootstrap/GroupReceivablesIntegrationTest.kt`
- `backend/features/receivables/src/integrationTest/kotlin/br/com/saqz/receivables/FinancialAccountsHttpIntegrationTest.kt`
- `backend/features/receivables/src/integrationTest/kotlin/br/com/saqz/receivables/FinancialOnboardingIntegrationTest.kt`
- `backend/features/receivables/src/integrationTest/kotlin/br/com/saqz/receivables/ReceivablesSchemaIntegrationTest.kt`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/application/FinancialOnboarding.kt`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/application/ManageGroupReceivables.kt`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/domain/FinancialAccess.kt`
- `backend/features/receivables/src/test/kotlin/br/com/saqz/receivables/domain/FinancialAccessTest.kt`
