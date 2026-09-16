# T0 — Homologação Uazapi (grupo de teste real)

Executado em 2026-09-16 contra a instância `saqz-principal` (`551153040175` / Saqz.app, conectada),
token de desenvolvimento. Método: a própria instância criou o grupo de teste via `POST /group/create`
("Saqz T0 Test", sem participantes), exerceu o ciclo e se auto-removeu (`/group/leave` → grupo
deletado, conferido com `/group/list` force). Nenhuma pessoa real recebeu mensagem — os envios
foram do próprio número da instância para o grupo dela. Payloads brutos nos comandos do ciclo
(mostrados abaixo em resumo).

## Respostas às incógnitas

| # | Incógnita | Resposta |
|---|---|---|
| U1 | Semântica de `Owner*` | **Parcial.** Grupo criado pela instância: `OwnerJID: 123227628130444@lid`, `OwnerPN: 551153040175@s.whatsapp.net`. Não distingue "criador" de "conta conectada" (aqui são o mesmo). `OwnerIsAdmin`/`OwnerCanSendMessage` = true (instância admin). **Decisão: não depender de `Owner*`** — anti-sequestro usa `Participants[].IsAdmin` + `PhoneNumber`, como o design já determinava. |
| U2 | Payload do evento `groups` | **Não testado — fora da v1** (sem webhook/SSE no escopo). |
| U3 | `/group/info` fora do grupo/inexistente | **HTTP 500** com corpo `{"error":"that group does not exist"}` — NÃO é 404. Risco crítico mapeado: a política de DM trata 5xx como retry; o adapter `UazapiGroupDirectory` deve traduzir esse 500 específico para `NotInGroup` (vínculo quebrado), jamais retry. |
| U4 | `join` com aprovação pendente | **Parcial.** Sem segundo telefone não dá para criar cenário com `IsJoinApprovalRequired=true` + aprovação pendente. Cobertura defensiva: após `join`, `LinkGroupWhatsApp` sempre reconfirma com `/group/info` (force) — entrada não confirmada vira `409 join_pending`, independente do formato do pending. |
| U5 | Envio em grupo `IsAnnounce` sem admin | **Parcial.** `DEMOTE` em si mesmo (único admin) é no-op (`groupUpdated: []`, HTTP 200) — WhatsApp protege o último admin. Cobertura: erro de envio segue a classificação do sender (4xx → FAILED permanente da mensagem; vínculo só quebra pela revalidação de membership). |

## Contratos de parser (para T2 — `UazapiGroupDirectory`)

1. **Wrapper `{"group": ...}`**: `group/create`, `group/inviteInfo`, `group/join`,
   `group/updateAnnounce` e `group/updateParticipants` devolvem o objeto `Group` **embrulhado na
   chave `group`** (com `response` textual e, no join, `needs_refresh`). O parser desembrulha antes
   de ler campos.
2. **`Participants[].PhoneNumber` é JID completo**: vem como `"551153040175@s.whatsapp.net"`
   (e há `LID` separado, `AddressingMode: "lid"`). Normalização = remover sufixo `@s.whatsapp.net`
   e tudo que não é dígito antes de comparar com o telefone do membro Saqz.
3. **`group/info` é a fonte da verdade pós-join**: `group/join` retorna **objeto Group vazio**
   (`OwnerJID: ""`, `Participants: null`, `needs_refresh: true`) mesmo com sucesso — nunca confiar
   no retorno do join; sempre reconfirmar com `group/info` (`force: true`).
4. **`group/info` com `getInviteLink: true`** devolve `invite_link` completo
   (`https://chat.whatsapp.com/<code>`) e `getRequestsParticipants` a lista de pedidos.
5. **`group/list` tem cache**: resposta pode estar defasada sem `force: true` (observado: grupo
   removido continuava na lista até o force). Membership check em produção deve usar `group/info`
   com `force`, não `group/list`.
6. **`inviteInfo` aceita código puro ou URL completa** — ambos funcionam; resposta traz
   `Participants` com `IsAdmin` (suficiente para o anti-sequestro sem depender de `Owner*`).
7. **`send/text` para `@g.us`**: HTTP 200 com `status: "Pending"`, `isGroup: true`, `messageid`
   próprio. Aceitação ≠ leitura (como no DM).
8. **`IsEphemeral: true` com `DisappearingTimer: 0`** apareceu no grupo criado — campo ignorado
   pelo parser do Saqz.

## Envio de teste (evidência de entrega)

Dois envios `send/text` ao JID do grupo retornaram 200/Pending (um com `IsAnnounce: true` como
admin). Grupo de teste **deletado** após o ciclo (`/group/list` force = 0 grupos). Custo total do
ciclo: mensagens apenas da própria instância para o próprio grupo.