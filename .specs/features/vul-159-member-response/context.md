# VUL-159 Context

**Gathered:** 2026-08-03
**Spec:** `.specs/features/vul-159-member-response/spec.md`
**Status:** Ready for design

## Feature Boundary

Adicionar ao detalhe do jogo uma seção de resposta do próprio membro, feedback baseado no backend,
posição de reserva pelo roster, trava pós-prazo, transparência de cobrança para AVULSO e opt-in de
auto-confirmação para MENSALISTA.

## Implementation Decisions

- A seção vive em `ui/gamedetail/GameResponseSection.kt` para reduzir conflito com VUL-158.
- A tela usa dois botões próprios com strings de `strings_game_response.xml`; o componente
  compartilhado `SaqzAttendanceSelector` não é reutilizado porque ele inclui Talvez e usa strings
  do design system compartilhado.
- O switch fica junto da seção de resposta por ser o menor diff e manter a configuração contextual
  ao jogo.
- Respostas e switch usam atualizações otimistas, rollback e contadores de geração monotônicos.
- O backend permanece a fonte da verdade para status, cobrança e política de fila.

## Agent's Discretion

- Hierarquia visual interna da seção, respeitando componentes e tokens existentes.
- Mensagem genérica para falhas não classificadas pelo domínio.

## Declined / Undiscussed Gray Areas → Assumptions

- O contrato de VUL-156 não oferece leitura do opt-in: o switch começa desligado e sincroniza o
  valor retornado após cada atualização.
- Falha no perfil próprio não impede resposta de attendance; apenas omite o switch.

## Specific References

- Endpoint de opt-in: `PUT /api/groups/{groupId}/athletes/me/auto-confirmation`.
- Roster: `GET /api/groups/{groupId}/games/{gameId}/attendance/roster`.

## Deferred Ideas

- Expor o estado persistido de auto-confirmação em um endpoint de leitura ou no perfil próprio.
