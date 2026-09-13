# Gestão financeira final — tarefas

## Test Coverage Matrix

Guidelines: `mobile/AGENTS.md`, TLC e padrões existentes Kotlin/JUnit/MockWebServer/MockEngine.

| Camada | Tipo | Expectativa | Gate |
|---|---|---|---|
| backend application/provider | unit/integration | todos MGT-01..07; payload exato, auth, timeout/recovery | `backend/gradlew -p backend :features:receivables:test :features:receivables:integrationTest` |
| backend HTTP/JDBC/config | integration/build | happy/edge/error de cada rota, persistência cifrada | mesmos + `:bootstrap:test :architecture-tests:test` |
| mobile domain/data/VM/UI | common/ios | MGT-01..10, request/result/sessão/generation/persistência | Gradle somente com lease; sem lease, inspeção/compilação pelo coordenador |

## Plano atômico

1. T1 contrato/spec/design/tasks → docs; sem código; revisão textual.
2. T2 backend domínio/provider/store/controller/config/V62 + testes colocalizados → MGT-01..07.
3. T3 mobile domínio/data/VM/UI/strings + testes colocalizados → MGT-01..10.
4. T4 evidência, inspeção e diffs de wiring → relatório; gates permitidos.

Dependências: `T1 → T2 → T3 → T4`. Cada tarefa inclui os testes da camada que altera. A dispatch
proíbe commits e novos workers, portanto os passos são entregues sem Git mutante e a verificação
autor != verificador fica explicitamente pendente ao coordenador.

## Resultado final

- [x] T1: contrato, critérios e desenho publicados.
- [x] T2: backend com correção permitida, preservação de seis campos de identidade e recuperação cifrada; V62 e testes concluídos em a401eb7b.
- [x] T3: gateways/VM/UI compartilhados, seleção por nome/grupo e invalidação por revogação; commits 6f700201/af0a043a/777abef4.
- [x] T4: DI/navegação, Android/iOS, capturas e revisão independente final aprovados; relatório final-wallet-management-review.md.

A lacuna de sensor de autenticação recente do destino bancário foi corrigida e revalidada por um
segundo verificador: 10 testes restaurados, 7/7 falhas detectadas no conjunto. Sem reembolso no produto.
