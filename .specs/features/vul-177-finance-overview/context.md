# VUL-177 Context

**Gathered:** 2026-08-04
**Spec:** `.specs/features/vul-177-finance-overview/spec.md`
**Status:** Ready for design

## Feature Boundary

Um endpoint de leitura para consolidar financeiramente todos os grupos em que o usuário é
`OWNER`/`ADMIN`, com seleção de mês/ano, saldo derivado, status estruturado por grupo e até cinco
lançamentos recentes. Não altera o extrato do VUL-176.

## Implementation Decisions

- `year=YYYY` representa o ano-calendário.
- O saldo é acumulado; entradas, saídas, pendências do período e lançamentos recentes respeitam o
  período selecionado.
- O contrato retorna campos estruturados (`pendingMonthlyCount`, `hasBillingConfigured`, `kind`,
  `direction`, `memberName`, `description`) e não frases prontas.
- A autorização é aplicada na consulta SQL por `owner_user_id` ou membership `ADMIN`.
- O endpoint usa um controller e um repositório próprios para não conflitar com o trabalho do
  VUL-176.

## Agent's Discretion

- Nomes exatos das classes internas de aplicação e dos campos auxiliares da resposta, desde que os
  campos estruturados da especificação sejam preservados.
- Implementação das queries em uma ou mais consultas JDBC, desde que o resultado seja equivalente e
  a fórmula de saldo permaneça explícita.

## Declined / Undiscussed Gray Areas → Assumptions

As decisões temporais e de validação foram registradas como assumptions na especificação; não há
outras áreas abertas.

## Specific References

Reutilizar `V5__add_group_finance.sql`, `V32__add_finance_direction_and_paid_method.sql`,
`ChargeManagement`, `ExpenseService`, os repositórios JDBC de finance e o padrão de
`VerifiedGroupActorResolver`.

## Deferred Ideas

- Fiação dos modelos/gateways no mobile permanece no VUL-178.
- Endpoint paginado de extrato permanece no VUL-176.
