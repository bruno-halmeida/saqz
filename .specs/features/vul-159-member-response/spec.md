# VUL-159 — Resposta do membro no jogo

## Problem Statement

O membro precisa informar sua presença no jogo e entender imediatamente se ficou na vaga ou na
reserva. A tela também precisa tornar explícita a cobrança do diarista e permitir o opt-in de
auto-confirmação para mensalistas quando o grupo oferecer esse recurso.

## Goals

- [ ] Permitir responder Vou/Não vou até o prazo e refletir o resultado autoritativo do backend.
- [ ] Mostrar a posição da reserva pela ordem do roster e bloquear respostas após o prazo.
- [ ] Permitir o opt-in de auto-confirmação somente para mensalistas em grupos habilitados.

## Out of Scope

| Feature | Reason |
| --- | --- |
| Opção Talvez | Decisão de produto deste ticket é somente Vou/Não vou. |
| Reimplementação da política de confirmação | A política é do backend; a tela apenas exibe o status retornado. |
| Leitura do opt-in salvo | O contrato disponível no backend de VUL-156 expõe apenas o PUT de atualização. |
| Alteração do backend | Os endpoints necessários já estão em `origin/main`. |

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Como descobrir se o membro é mensalista | Usar o `GET /api/athletes/me` existente e a membership do grupo | O `GET /api/groups/{id}` expõe configuração do grupo, mas não o tipo do membro. | y |
| Estado inicial do switch | Desligado até o usuário alterná-lo; o PUT retorna o estado aplicado | VUL-156 não expõe GET do opt-in no contrato atual. | y |
| Fonte da posição na reserva | Índice do membro na lista `waitlisted` do roster | O backend já ordena a lista pela posição e a devolve nominalmente. | y |
| Erro de carregamento do perfil próprio | Ocultar somente o opt-in; manter resposta do jogo disponível | O perfil é metadado opcional para a resposta. | y |

**Open questions:** none — all resolved or logged above.

## User Stories

### P1: Responder presença ⭐ MVP

**User Story**: Como membro, quero escolher Vou ou Não vou no detalhe do jogo para registrar minha
presença.

**Acceptance Criteria**:

1. WHEN o jogo está publicado e antes do prazo THEN a tela SHALL exibir exatamente as ações Vou e Não vou.
2. WHEN o membro escolhe uma ação THEN a tela SHALL exibir o status retornado pelo backend como confirmado, reserva ou não vou.
3. WHEN a resposta retornada tem status WAITLISTED THEN a tela SHALL exibir a posição calculada pela ordem do roster.
4. WHEN a resposta falha THEN a tela SHALL restaurar a resposta anterior e exibir erro de salvamento.
5. WHEN o membro escolhe outra resposta antes do prazo THEN a tela SHALL enviar a nova intenção, sem reimplementar a política do backend.

**Independent Test**: abrir um jogo publicado, alternar Vou/Não vou e observar o status da mutação,
incluindo uma resposta WAITLISTED ordenada no roster.

### P1: Encerramento e transparência ⭐ MVP

**User Story**: Como membro, quero saber quando as confirmações encerraram e quando Vou gera cobrança.

**Acceptance Criteria**:

1. WHEN o prazo passou THEN a tela SHALL manter o resultado visível, desabilitar Vou/Não vou e exibir que as confirmações estão encerradas.
2. WHEN o membro é AVULSO THEN a tela SHALL exibir que confirmar gera a cobrança do jogo.

**Independent Test**: abrir estados publicado antes/depois do prazo e com membro AVULSO.

### P1: Auto-confirmação ⭐ MVP

**User Story**: Como mensalista, quero ativar ou desativar a confirmação automática quando o grupo
habilitar o recurso.

**Acceptance Criteria**:

1. WHEN o membro é MENSALISTA e `autoConfirmEnabled` do grupo é true THEN a tela SHALL exibir o switch com o texto definido pelo produto.
2. WHEN o membro não é MENSALISTA ou o grupo não habilita o recurso THEN a tela SHALL ocultar o switch.
3. WHEN o switch muda THEN a tela SHALL aplicar a mudança otimisticamente e enviar o PUT de opt-in.
4. WHEN o PUT de opt-in falha THEN a tela SHALL restaurar o valor anterior e exibir erro de salvamento.

**Independent Test**: montar a tela para mensalista habilitado, AVULSO e grupo desabilitado; alternar o
switch e simular sucesso/falha do gateway.

## Edge Cases

- Respostas de presença concorrentes não podem sobrescrever o estado da geração mais nova.
- Alternâncias ABA do switch são protegidas por contador de geração, não por igualdade do valor.
- Roster sem o membro aguardado não deve inventar uma posição.
- Resposta sem `ETag` continua sendo `InvalidResponse` para mutações de attendance.

## Requirement Traceability

| Requirement ID | Story | Status |
| --- | --- | --- |
| VUL159-01 | Responder presença | Pending |
| VUL159-02 | Responder presença | Pending |
| VUL159-03 | Encerramento e transparência | Pending |
| VUL159-04 | Encerramento e transparência | Pending |
| VUL159-05 | Auto-confirmação | Pending |
| VUL159-06 | Auto-confirmação | Pending |
| VUL159-07 | Auto-confirmação | Pending |
| VUL159-08 | All | Pending |

## Success Criteria

- [ ] Gate de presentation e gates completos do ticket passam localmente.
- [ ] Testes de transporte cobrem roster, response e opt-in.
- [ ] Screenshots cobrem resposta aberta, reserva/erro, prazo encerrado e switch mensalista.
