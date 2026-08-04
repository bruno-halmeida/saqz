# VUL-177 — Visão geral financeira multi-grupo

## Problem Statement

A tela 5a precisa de um único agregado para a pessoa que organiza mais de um grupo. Hoje os dados
financeiros existem por grupo e os consumidores teriam de repetir consultas, além de correrem o
risco de somar grupos em que o usuário não administra o caixa.

## Goals

- [ ] Expor um `GET /api/me/finance/overview` com totais e grupos administrados pelo usuário.
- [ ] Derivar saldo com a fórmula financeira oficial e retornar lançamentos recentes estruturados.
- [ ] Permitir mês corrente, mês específico e ano-calendário sem expor dados de grupos de atletas.

## Out of Scope

| Feature | Reason |
| --- | --- |
| Extrato/statement paginado de um grupo | VUL-176 implementa essa superfície em paralelo. |
| Alteração de charges, despesas ou mensalidades | Este endpoint é somente leitura. |
| Texto pronto de status para o mobile | O mobile serializa o texto a partir dos campos estruturados. |
| Fiação de modelos/gateways no mobile | VUL-178 cobre essa integração. |

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Semântica de `year=YYYY` | Ano-calendário completo, de 1º de janeiro a 31 de dezembro. | A tela oferece o segmento de ano, e `month` já cobre a granularidade mensal. | yes — ticket |
| Alcance temporal do saldo | Acumulado desde o início dos registros, sem filtro do período. | É a fórmula de saldo acumulado exigida pelo contrato e pelo extrato. | yes — ticket |
| Alcance de entradas/saídas e recentes | Usar o período selecionado; charges entram pela data do evento que virou `PAID`, despesas pela `expense_date`. | Esses campos representam o movimento ocorrido no período, inclusive quando o mês muda. | implementation decision |
| “A receber” | Charges `PENDING` cujo `due_date` está dentro do período selecionado. | Mantém o total como total do período, sem misturar pendências históricas. | implementation decision |
| Mensalidades pendentes no status | Contar charges `MONTHLY` `PENDING` com vencimento até o fim do período. | “Vencidas ou do mês” inclui vencidas antes do início do período. | yes — ticket |
| Configuração de cobrança | `true` quando o default do grupo tem mensalidade ou existe mensalista ativo com mensalidade efetiva. | Reproduz a regra de mensalidade efetiva usada pelo job. | implementation decision |
| Quantidade de lançamentos recentes | No máximo 5, ordenados do mais novo para o mais antigo; menos de 3 é válido quando não há mais dados. | O ticket pede uma faixa de 3–5, e a API não deve fabricar registros. | implementation decision |
| Validação de parâmetros | `month` exige `YYYY-MM`, `year` exige `YYYY`, ambos juntos retornam 400 com erros de campo. | Evita período ambíguo e segue o padrão de validação HTTP do projeto. | implementation decision |

**Open questions:** none — all resolved or logged above.

## User Stories

### P1: Caixa do organizador ⭐ MVP

**User Story**: Como organizador, quero ver o caixa consolidado dos grupos que administro para
entender entradas, saídas e valores a receber em um só lugar.

**Acceptance Criteria**:

1. WHEN um usuário autenticado consulta o endpoint THEN o sistema SHALL incluir somente grupos em
   que ele é `OWNER` ou `ADMIN`.
2. WHEN há vários grupos administrados THEN `totals.balanceCents` SHALL ser a soma dos saldos
   acumulados e cada saldo SHALL ser `SUM(PAID charges) + SUM(active IN) - SUM(active OUT)`.
3. WHEN o período é mensal ou anual THEN `totals.inCents`, `totals.outCents` e
   `totals.pendingCents` SHALL conter somente os movimentos/charges definidos para aquele período.
4. WHEN não há grupo administrado THEN a resposta SHALL ser HTTP 200 com `groups` e
   `recentTransactions` vazios e os quatro totais iguais a zero.

**Independent Test**: consultar com dois grupos administrados e um grupo em que o usuário é atleta;
confirmar os quatro totais, a lista filtrada e o saldo derivado.

### P1: Período selecionável ⭐ MVP

**User Story**: Como organizador, quero alternar entre mês corrente, mês anterior e ano para
comparar o caixa em diferentes períodos.

**Acceptance Criteria**:

1. WHEN `month` e `year` estão ausentes THEN o sistema SHALL usar o mês corrente no fuso
   `America/Sao_Paulo`.
2. WHEN `month=YYYY-MM` é informado THEN o sistema SHALL usar exatamente aquele mês.
3. WHEN `year=YYYY` é informado THEN o sistema SHALL usar exatamente aquele ano-calendário.
4. WHEN `month` e `year` são informados juntos ou um deles é inválido THEN o sistema SHALL
   responder HTTP 400 com `fieldErrors` para os parâmetros inválidos.

**Independent Test**: repetir a consulta sem filtro, com mês, com ano e com ambos para observar os
períodos e a validação.

### P2: Saúde por grupo e atividade recente

**User Story**: Como organizador, quero identificar rapidamente grupos com pendências e os últimos
movimentos para decidir onde agir.

**Acceptance Criteria**:

1. WHEN um grupo aparece THEN o sistema SHALL retornar `id`, `name`, `balanceCents`,
   `pendingMonthlyCount` e `hasBillingConfigured`; nenhum status textual pronto SHALL ser emitido.
2. WHEN houver atividade no período THEN `recentTransactions` SHALL retornar no máximo cinco itens
   entre mensalidades pagas e lançamentos ativos `IN`/`OUT`, ordenados do mais recente, cada um com
   nome/id do grupo e dados estruturados de tipo, direção, descrição/membro, valor e data.
3. WHEN uma mensalidade, despesa ou grupo não pertence a um grupo administrado THEN ela SHALL ser
   omitida do agregado.

**Independent Test**: criar uma mensalidade paga, uma entrada, uma saída e uma pendência em grupos
administrados e em um grupo de atleta; confirmar a lista recente e os campos de cada grupo.

## Edge Cases

- WHEN somente um admin existe via membership THEN o grupo SHALL ser incluído como administrado.
- WHEN o usuário é apenas `ATHLETE` THEN o grupo SHALL ser omitido sem resposta 403.
- WHEN o grupo é soft-deleted THEN ele e seus dados financeiros SHALL ser omitidos.
- WHEN não existe mensalista configurado e não há pendência THEN `hasBillingConfigured` SHALL ser
  `false` e `pendingMonthlyCount` SHALL ser zero.
- WHEN o período não possui movimentos mas existem grupos THEN os grupos SHALL permanecer na lista,
  os totais do período SHALL ser zero e o saldo acumulado SHALL permanecer calculado.

## Requirement Traceability

| Requirement ID | Story | Phase | Status |
| --- | --- | --- | --- |
| OVERVIEW-01 | P1: Caixa do organizador | Design | Pending |
| OVERVIEW-02 | P1: Caixa do organizador | Design | Pending |
| OVERVIEW-03 | P1: Caixa do organizador | Design | Pending |
| OVERVIEW-04 | P1: Caixa do organizador | Design | Pending |
| OVERVIEW-05 | P1: Período selecionável | Execute | Verified |
| OVERVIEW-06 | P1: Período selecionável | Execute | Verified |
| OVERVIEW-07 | P1: Período selecionável | Execute | Verified |
| OVERVIEW-08 | P2: Saúde por grupo e atividade recente | Design | Pending |
| OVERVIEW-09 | P2: Saúde por grupo e atividade recente | Design | Pending |
| OVERVIEW-10 | P2: Saúde por grupo e atividade recente | Design | Pending |

**Coverage:** 10 total, 10 mapped to tasks, 0 unmapped.

## Success Criteria

- [ ] O endpoint retorna apenas caixa de OWNER/ADMIN e mantém 200 para usuário sem grupo.
- [ ] O saldo acumulado não depende de coluna de saldo e usa a fórmula única do projeto.
- [ ] Os gates locais de unit e integrationTest de `features:groups` passam.
