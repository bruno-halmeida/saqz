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
