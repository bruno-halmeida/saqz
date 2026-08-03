# VUL-159 Design

## Ownership

`KtorAttendanceGateway` traduz roster, resposta e opt-in para o domínio. `GameDetailViewModel`
coordena as leituras do jogo/grupo/attendance/roster e do perfil próprio, e mantém somente estado
de apresentação. `GameResponseSection` é uma unidade visual isolada; `GameDetailScreen` apenas a
insere entre o resumo e a lista existente.

## Data flow

```text
GameDetailViewModel
  ├─ GameGateway.read + GroupGateway.read
  ├─ AttendanceGateway.read + roster
  ├─ AthleteGateway.ownProfile ──┐
  └─ AttendanceGateway.respond/updateAutoConfirmation
                                  ↓
                         GameResponseSection
```

O status da resposta vem de `AttendanceMutation.attendance.status`; a posição é o índice do membro
em `AttendanceRoster.waitlisted`. A política de prioridade, cobrança e promoção não aparece na
presentation.

## Components

| Component | Location | Responsibility |
| --- | --- | --- |
| Attendance contracts | `groups/domain/attendance/Attendance.kt` | Tipos de roster, opt-in e portas do gateway. |
| Attendance transport | `groups/data/attendance/KtorAttendanceGateway.kt` | DTOs, rotas, mapeamento, erro e retry conforme o contrato existente. |
| Response state | `groups/presentation/gamedetail/*` | Estado, intents, carregamento, otimista/rollback e generation guards. |
| Response UI | `groups/presentation/ui/gamedetail/GameResponseSection.kt` | Dois botões, feedback, aviso AVULSO, switch e estados bloqueado/erro. |
| Visual evidence | `groups/presentation/src/androidHostTest/.../GameDetailScreenshotTest.kt` | Capturas das variantes exigidas pelo guia móvel. |

## Risks & Concerns

- **Contrato de opt-in sem leitura**: o switch não consegue refletir um valor persistido antes da
  primeira mudança. Mitigação: documentar a limitação no PR e manter rollback/valor retornado;
  follow-up fica fora do ticket.
- **Resposta fora de ordem**: múltiplas intenções podem completar em ordem diferente. Mitigação:
  contador separado para response e auto-confirmation; callbacks antigos são descartados.
- **Prazo avançar enquanto a tela está aberta**: o bloqueio é calculado no carregamento/render a
  partir do deadline autoritativo. O backend ainda rejeita a escrita após o prazo e a falha também
  trava a seção.
- **Rosters vazios ou ocultos**: ausência do membro não deve criar posição. Mitigação: posição nula,
  e o feedback usa a mensagem genérica de reserva sem ordinal somente se necessário.

## Reuse

- `SaqzCard`, `SaqzButton`, `SaqzSwitch`, `SaqzTheme` e `testTag` existente.
- `AthleteGateway.ownProfile` para tipo do membro.
- `retryTransport`, `NetworkResult` e o mapeamento de `AttendanceError` já existentes.
