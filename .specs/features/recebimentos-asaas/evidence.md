# Evidências incrementais

## T01 — domínio e fronteiras

Gate: JDK 21, `backend/gradlew -p backend :features:subscriptions:test :features:receivables:test :architecture-tests:test`, exit 0.
Cinco testes novos; vinte testes de arquitetura. Testes existentes preservados.

Arquivo das asserções: `backend/features/receivables/src/test/kotlin/br/com/saqz/receivables/domain/FinancialAccessTest.kt`.

| Critério do plano | Linha e asserção | Resultado esperado / mapeamento reverso |
|---|---|---|
| B1/B6 manutenção após corte | 22 `assertEquals(ActionPermission(true), ...)` | acesso a dinheiro e instrumentos existentes; FIN-ACCESS-01 |
| B5 bloqueio de novas operações | 26 `assertEquals(ActionPermission(false, INELIGIBLE_PLAN), ...)` | corte comercial sem bloqueio global |
| B2 aprovação e ativação | 33–40 `assertTrue` / `assertEquals(...reason)` | aprovação, liberação e grupo ativado obrigatórios |
| B2 delegação sem redelegação | 47–49 `assertTrue` / `assertEquals(UNAUTHORIZED, ...)` | operação permitida, identidade e concessão exclusivas do titular |
| B2 revogação | 51–54 `assertEquals(UNAUTHORIZED, ...)` | revogado ou administrador removido perde autorização |
| B2 isolamento | 60–62 `assertEquals(UNAUTHORIZED, ...)` | novo dono de grupo e delegado de outra conta sem acesso |
| B2 autenticação recente | 68–71 `assertEquals(RECENT_AUTHENTICATION_REQUIRED, ...)` | saque e mudança bancária exigem autenticação recente; leitura preservada |

Todas as asserções novas correspondem a critérios acima; sem testes especulativos.
A política recebe fatos atualizados: verificação HTTP/JDBC de autenticação e revogação pertence às tarefas seguintes.
A matriz trial/planos depende da composição T06; T01 fornece apenas a porta e não afirma implementar o trial.

## T02 — schema financeiro aditivo

Gate: JDK 21, `:features:receivables:check :architecture-tests:test :bootstrap:test --tests '*SubscriptionsMigrationOnBootstrapClasspathIntegrationTest'`, exit 0.
6 testes PostgreSQL reais, 5 de domínio, 20 de arquitetura e migração integrada do bootstrap passam.

Asserções em `ReceivablesSchemaIntegrationTest.kt`; cada linha abaixo também é o mapeamento reverso para B1/B2/B4/B5/B6:

| Critério | Linha / asserção | Resultado |
|---|---|---|
| B1 migração aditiva | 25–29 `assertEquals(1/0/2300/17, ...)` | migra uma vez; preserva cobrança manual; cria 17 tabelas financeiras |
| B2 identidade única | 37–39 `assertEquals("23505", ...sqlState)` / contagem 1 | titular e CPF/CNPJ não duplicam conta |
| B4 deduplicação por conta | 51–53 SQLSTATE 23505 / contagem 2 | evento duplicado rejeitado só na mesma conta |
| B5 competência única | 69–71 SQLSTATE 23505 / contagem 1 | mesma competência não reaparece após troca de conta |
| B4/B6 ledger imutável | 82–85 SQLSTATE 23505/P0001 / soma 10000 | recebimento e histórico não duplicam nem são apagados |
| B2 isolamento de saque | 101–107 SQLSTATE 23503 / contagem 0 | destino de outra conta rejeitado pelo banco |

Sem alterações em testes existentes. Valores comerciais não populados. Testes de concorrência de operações pertencem a T03.
